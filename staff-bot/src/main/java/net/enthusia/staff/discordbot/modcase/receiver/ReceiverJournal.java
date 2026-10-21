package net.enthusia.staff.discordbot.modcase.receiver;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/** One-process, synthetic, durable-by-atomic-replace projection and two independent outboxes. */
public final class ReceiverJournal {
    private static final ObjectMapper JSON = new ObjectMapper()
            .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
    private static final int MAX_EVENTS = 4096;
    private static final int MAX_JOBS = 8192;
    private static final int MAX_FILE_BYTES = 3_000_000;
    private static final Pattern RECEIPT = Pattern.compile("[A-Za-z0-9_:/.-]{1,160}");
    private final Path file;
    private State state;

    public enum Outcome { ACCEPTED, DUPLICATE, REJECTED }
    public enum Surface { DISCORD, PAPER }
    public enum Delivery { READY, POSSIBLY_SENT, CONFIRMED, FAILED }

    public record Cursor(String caseId, String origin, String scope, long revision,
                         String caseState, boolean needsResync, boolean sawBirth,
                         String operationId) { }
    public record Job(String id, String caseId, String operationId, long revision,
                      Surface surface, Delivery delivery, int attempts,
                      long nextAtMillis, String receipt) { }
    private record State(Map<String, CaseEvent> operations, Map<String, Cursor> cases,
                         Map<String, Job> jobs) { }

    public ReceiverJournal(Path file) throws IOException {
        this.file = Objects.requireNonNull(file).toAbsolutePath();
        if (!Files.exists(file)) {
            state = new State(Map.of(), Map.of(), Map.of());
        } else {
            if (Files.size(file) > MAX_FILE_BYTES) {
                throw new IOException("receiver journal exceeds size cap");
            }
            state = validateLoaded(JSON.readValue(Files.readAllBytes(file), State.class));
        }
    }

    private static State validateLoaded(State loaded) throws IOException {
        if (!validHeader(loaded) || !validOperations(loaded)
                || !validCursors(loaded) || !validJobs(loaded)) {
            throw new IOException("corrupt receiver journal");
        }
        return new State(Map.copyOf(loaded.operations()), Map.copyOf(loaded.cases()),
                Map.copyOf(loaded.jobs()));
    }

    private static boolean validHeader(State loaded) {
        return loaded != null && loaded.operations() != null && loaded.cases() != null
                && loaded.jobs() != null && loaded.operations().size() <= MAX_EVENTS
                && loaded.jobs().size() <= MAX_JOBS
                && loaded.cases().size() <= loaded.operations().size();
    }

    private static boolean validOperations(State loaded) {
        boolean validKeys = loaded.operations().entrySet().stream()
                .allMatch(entry -> entry.getValue() != null
                        && Objects.equals(entry.getValue().operationId(), entry.getKey()));
        return validKeys && loaded.operations().values().stream()
                .map(event -> event.caseId() + "/" + event.revision())
                .distinct().count() == loaded.operations().size();
    }

    private static boolean validCursors(State loaded) {
        Map<String, Long> latest = new HashMap<>();
        for (CaseEvent event : loaded.operations().values()) {
            latest.merge(event.caseId(), event.revision(), Math::max);
        }
        if (!latest.keySet().equals(loaded.cases().keySet())) {
            return false;
        }
        for (Map.Entry<String, Cursor> entry : loaded.cases().entrySet()) {
            Cursor cursor = entry.getValue();
            if (cursor == null || !Objects.equals(cursor.caseId(), entry.getKey())) {
                return false;
            }
            CaseEvent event = loaded.operations().get(cursor.operationId());
            if (event == null || !matchesCursor(cursor, event)
                    || cursor.revision() != latest.get(cursor.caseId())) {
                return false;
            }
        }
        return true;
    }

    private static boolean matchesCursor(Cursor cursor, CaseEvent event) {
        return Objects.equals(cursor.caseId(), event.caseId())
                && Objects.equals(cursor.origin(), event.originPlatform())
                && Objects.equals(cursor.scope(), event.scopeId())
                && cursor.revision() == event.revision()
                && Objects.equals(cursor.caseState(), event.caseState());
    }

    private static boolean validJobs(State loaded) {
        for (Map.Entry<String, Job> entry : loaded.jobs().entrySet()) {
            Job job = entry.getValue();
            if (job == null || !Objects.equals(entry.getKey(), job.id())) {
                return false;
            }
            CaseEvent event = loaded.operations().get(job.operationId());
            if (event == null || !matchesJob(job, event, loaded.cases())) {
                return false;
            }
        }
        return true;
    }

    private static boolean matchesJob(Job job, CaseEvent event, Map<String, Cursor> cases) {
        return Objects.equals(job.caseId(), event.caseId())
                && job.revision() == event.revision()
                && cases.containsKey(job.caseId())
                && validJobDelivery(job)
                && job.id().equals(event.identity() + "/" + event.revision() + "/" + job.surface());
    }

    private static boolean validJobDelivery(Job job) {
        return job.surface() != null && job.delivery() != null && job.attempts() >= 0
                && job.nextAtMillis() >= 0
                && (job.delivery() != Delivery.CONFIRMED
                    || (job.receipt() != null
                        && RECEIPT.matcher(job.receipt()).matches()));
    }

    public synchronized Outcome accept(CaseEvent event) throws IOException {
        CaseEvent previous = state.operations().get(event.operationId());
        if (previous != null) {
            return previous.equals(event) ? Outcome.DUPLICATE : Outcome.REJECTED;
        }
        Cursor cursor = state.cases().get(event.caseId());
        if (!mayAdvance(cursor, event) || state.operations().size() >= MAX_EVENTS) {
            return Outcome.REJECTED;
        }
        boolean sawBirth = "CASE_CREATED".equals(event.eventType())
                || (cursor != null && cursor.sawBirth());
        boolean needsResync = !sawBirth || (cursor != null
                && (cursor.needsResync() || event.revision() != cursor.revision() + 1));
        Cursor next = new Cursor(event.caseId(), event.originPlatform(), event.scopeId(),
                event.revision(), event.caseState(), needsResync, sawBirth, event.operationId());
        Map<String, CaseEvent> operations = new HashMap<>(state.operations());
        Map<String, Cursor> cases = new HashMap<>(state.cases());
        Map<String, Job> jobs = new HashMap<>(state.jobs());
        operations.put(event.operationId(), event);
        cases.put(event.caseId(), next);
        if (!needsResync) {
            enqueue(jobs, event);
        }
        commit(new State(operations, cases, jobs));
        return Outcome.ACCEPTED;
    }

    public synchronized boolean reconcile(CaseEvent authoritative) throws IOException {
        Cursor current = state.cases().get(authoritative.caseId());
        if (current == null || !current.needsResync()
                || !current.operationId().equals(authoritative.operationId())
                || !state.operations().get(current.operationId()).equals(authoritative)
                || current.revision() != authoritative.revision()) {
            return false;
        }
        Map<String, Cursor> cases = new HashMap<>(state.cases());
        Map<String, Job> jobs = new HashMap<>(state.jobs());
        cases.put(current.caseId(), new Cursor(current.caseId(), current.origin(),
                current.scope(), current.revision(), current.caseState(),
                false, current.sawBirth(), current.operationId()));
        enqueue(jobs, authoritative);
        commit(new State(state.operations(), cases, jobs));
        return true;
    }

    private static boolean mayAdvance(Cursor cursor, CaseEvent event) {
        if (cursor == null) {
            return true; // Missing CASE_CREATED: persist, but fence notices until resync.
        }
        return cursor.origin().equals(event.originPlatform())
                && cursor.scope().equals(event.scopeId())
                && event.revision() > cursor.revision()
                && !"CLOSED".equals(cursor.caseState())
                && !("CLAIMED".equals(cursor.caseState())
                     && "OPEN".equals(event.caseState()))
                && (cursor.sawBirth() || !"CASE_CREATED".equals(event.eventType()));
    }

    private static void enqueue(Map<String, Job> jobs, CaseEvent event) {
        for (Surface surface : Surface.values()) {
            String key = event.identity() + "/" + event.revision() + "/" + surface;
            jobs.putIfAbsent(key, new Job(key, event.caseId(), event.operationId(),
                    event.revision(), surface, Delivery.READY, 0, 0, null));
        }
        if (jobs.size() > MAX_JOBS) {
            throw new IllegalStateException("bounded receiver outbox exhausted");
        }
    }

    public synchronized Cursor cursor(String caseId) {
        return state.cases().get(caseId);
    }

    public synchronized List<Job> ready(String origin, String scope, int limit, Instant now) {
        if (limit < 1 || limit > 100) {
            throw new IllegalArgumentException("invalid scoped batch limit");
        }
        return state.jobs().values().stream()
                .filter(job -> due(job, origin, scope, now))
                .sorted(java.util.Comparator.comparing(Job::id)).limit(limit).toList();
    }

    private boolean due(Job job, String origin, String scope, Instant now) {
        Cursor current = state.cases().get(job.caseId());
        CaseEvent event = state.operations().get(job.operationId());
        return current != null && event != null && !current.needsResync()
                && current.revision() == job.revision()
                && current.origin().equals(origin) && current.scope().equals(scope)
                && job.delivery() == Delivery.READY && job.nextAtMillis() <= now.toEpochMilli()
                && ("CLOSED".equals(current.caseState())
                    || Instant.parse(event.expiresAt()).isAfter(now));
    }

    /** MUST be persisted before invoking even a fake destination. */
    public synchronized boolean markPossiblySent(String id, Instant now) throws IOException {
        Job job = state.jobs().get(id);
        CaseEvent event = job == null ? null : state.operations().get(job.operationId());
        if (event == null || !due(job, event.originPlatform(), event.scopeId(), now)) {
            return false;
        }
        replace(job, Delivery.POSSIBLY_SENT, job.attempts() + 1,
                job.nextAtMillis(), null);
        return true;
    }

    public synchronized boolean confirm(String id, String operationId, String receipt)
            throws IOException {
        Job job = state.jobs().get(id);
        if (job == null || !job.operationId().equals(operationId)
                || receipt == null || !receipt.matches("[A-Za-z0-9_:/.-]{1,160}")) {
            return false;
        }
        if (job.delivery() == Delivery.CONFIRMED) {
            return receipt.equals(job.receipt());
        }
        if (job.delivery() != Delivery.POSSIBLY_SENT) {
            return false;
        }
        replace(job, Delivery.CONFIRMED, job.attempts(), job.nextAtMillis(), receipt);
        return true;
    }

    /** Only for a fake verified NOT_SENT response; uncertainty is never automatically retried. */
    public synchronized boolean verifiedNotSent(String id, Instant now) throws IOException {
        Job job = state.jobs().get(id);
        if (job == null || job.delivery() != Delivery.POSSIBLY_SENT) {
            return false;
        }
        Delivery result = job.attempts() >= 4 ? Delivery.FAILED : Delivery.READY;
        long delayMillis = 1_000L << Math.min(job.attempts(), 4);
        replace(job, result, job.attempts(), now.toEpochMilli() + delayMillis, null);
        return true;
    }

    private void replace(Job job, Delivery delivery, int attempts,
                         long nextAt, String receipt) throws IOException {
        Map<String, Job> jobs = new HashMap<>(state.jobs());
        jobs.put(job.id(), new Job(job.id(), job.caseId(), job.operationId(),
                job.revision(), job.surface(), delivery, attempts, nextAt, receipt));
        commit(new State(state.operations(), state.cases(), jobs));
    }

    private void commit(State proposed) throws IOException {
        byte[] bytes = JSON.writeValueAsBytes(proposed);
        if (bytes.length > MAX_FILE_BYTES) {
            throw new IOException("receiver journal capacity exceeded");
        }
        Path parent = file.getParent();
        Files.createDirectories(parent);
        Path temp = Files.createTempFile(parent, ".case-receiver-", ".tmp");
        try {
            try (FileChannel channel = FileChannel.open(temp, StandardOpenOption.WRITE)) {
                ByteBuffer data = ByteBuffer.wrap(bytes);
                while (data.hasRemaining()) {
                    channel.write(data);
                }
                channel.force(true);
            }
            Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
            state = proposed;
        } finally {
            Files.deleteIfExists(temp);
        }
    }
}
