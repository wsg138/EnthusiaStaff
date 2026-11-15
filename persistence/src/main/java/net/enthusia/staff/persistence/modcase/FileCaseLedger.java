package net.enthusia.staff.persistence.modcase;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import net.enthusia.staff.domain.modcase.CaseDelivery;
import net.enthusia.staff.domain.modcase.CaseLedger;
import net.enthusia.staff.domain.modcase.CaseNotice;

/**
 * Atomic local case/outbox snapshots; NOT a distributed production database adapter.
 *
 * <p>Every public read-modify-write operation deliberately locks on this instance.
 * The monitor covers revision checks, delivery leases, durable snapshot replacement,
 * and publication of the new in-memory maps as one linearizable operation. Replacing
 * method-level synchronization with unguarded block locks would introduce races.
 * Only one ledger instance/process may own a journal directory at a time.
 */
public final class FileCaseLedger implements CaseLedger {
    private static final Pattern RECEIPT = Pattern.compile("[a-zA-Z0-9:_./-]{1,160}");
    private static final Pattern ERROR = Pattern.compile("[A-Z0-9_]{1,64}");

    private final CaseSnapshotFile storage;
    private Map<String, CaseNotice> cases = new HashMap<>();
    private Map<CaseDelivery.Key, CaseDelivery> deliveries = new HashMap<>();

    public FileCaseLedger(Path directory) {
        storage = new CaseSnapshotFile(directory);
        CaseSnapshotFile.Snapshot snapshot = storage.load();
        for (CaseNotice notice : snapshot.cases()) {
            if (cases.putIfAbsent(notice.identity(), notice) != null) {
                throw new IllegalStateException("duplicate case in snapshot");
            }
        }
        for (CaseDelivery delivery : snapshot.deliveries()) {
            if (deliveries.putIfAbsent(delivery.key(), delivery) != null) {
                throw new IllegalStateException("duplicate outbox job in snapshot");
            }
        }
        Map<CaseDelivery.Key, CaseDelivery> recovered = new HashMap<>(deliveries);
        recovered.replaceAll((key, value) -> value.state() == CaseDelivery.State.IN_FLIGHT
                ? value.uncertain("RESTART_SEND_UNCONFIRMED") : value);
        if (!recovered.equals(deliveries)) {
            commit(cases, recovered);
        }
    }

    private void commit(Map<String, CaseNotice> changed, Map<CaseDelivery.Key, CaseDelivery> outbox) {
        storage.save(new CaseSnapshotFile.Snapshot(new ArrayList<>(changed.values()),
                new ArrayList<>(outbox.values())));
        cases = new HashMap<>(changed);
        deliveries = new HashMap<>(outbox);
    }

    private static String identity(CaseDelivery.Key key) {
        return key.origin() + "/" + key.scope() + "/" + key.caseId();
    }

    private static void enqueue(Map<CaseDelivery.Key, CaseDelivery> outbox, CaseNotice event) {
        for (CaseDelivery.Destination destination : CaseDelivery.Destination.values()) {
            CaseDelivery.Key key = new CaseDelivery.Key(
                    event.origin(), event.scope(), event.caseId(), event.revision(), destination);
            outbox.putIfAbsent(key, CaseDelivery.pending(key, event.createdAt()));
        }
    }

    @Override
    public synchronized Ingest accept(CaseNotice event) {
        if (event == null) {
            throw new IllegalArgumentException("trusted case event required");
        }
        CaseNotice old = cases.get(event.identity());
        if (old != null && old.revision() > event.revision()) {
            return Ingest.STALE;
        }
        if (old != null && old.revision() == event.revision()) {
            return old.equals(event) ? Ingest.REPLAY : Ingest.CONFLICT;
        }
        Map<String, CaseNotice> changed = new HashMap<>(cases);
        Map<CaseDelivery.Key, CaseDelivery> outbox = new HashMap<>(deliveries);
        changed.put(event.identity(), event);
        enqueue(outbox, event);
        commit(changed, outbox);
        return old == null ? Ingest.CREATED : Ingest.ADVANCED;
    }

    @Override
    public synchronized Optional<CaseNotice> find(CaseDelivery.Key key) {
        return Optional.ofNullable(cases.get(identity(key)));
    }

    @Override
    public synchronized List<CaseDelivery> due(Instant now, int limit) {
        if (now == null || limit < 1 || limit > 1000) {
            throw new IllegalArgumentException("invalid outbox poll");
        }
        return deliveries.values().stream()
                .filter(job -> job.due(now))
                .filter(job -> isCurrentAndActive(job.key(), now))
                .filter(job -> !ambiguousEarlier(job.key()))
                .sorted(Comparator.comparing(CaseDelivery::nextAttempt))
                .limit(limit).toList();
    }

    private boolean isCurrentAndActive(CaseDelivery.Key key, Instant now) {
        CaseNotice notice = cases.get(identity(key));
        return notice != null && notice.revision() == key.revision()
                && notice.expiresAt().isAfter(now);
    }

    private boolean ambiguousEarlier(CaseDelivery.Key key) {
        return deliveries.values().stream().anyMatch(job -> job.key().origin() == key.origin()
                && job.key().scope().equals(key.scope()) && job.key().caseId().equals(key.caseId())
                && job.key().destination() == key.destination() && job.key().revision() < key.revision()
                && (job.state() == CaseDelivery.State.UNCERTAIN
                || job.state() == CaseDelivery.State.IN_FLIGHT));
    }

    @Override
    public synchronized Optional<CaseDelivery> begin(CaseDelivery.Key key, Instant now) {
        if (key == null || now == null) {
            throw new IllegalArgumentException("delivery key and attempt time required");
        }
        CaseDelivery previous = deliveries.get(key);
        if (previous == null || !previous.due(now) || !isCurrentAndActive(key, now)
                || ambiguousEarlier(key)) {
            return Optional.empty();
        }
        CaseDelivery started = previous.start(UUID.randomUUID().toString());
        replace(started);
        return Optional.of(started);
    }

    private void replace(CaseDelivery value) {
        Map<CaseDelivery.Key, CaseDelivery> outbox = new HashMap<>(deliveries);
        outbox.put(value.key(), value);
        commit(cases, outbox);
    }

    private CaseDelivery leased(CaseDelivery.Key key, String token) {
        CaseDelivery current = deliveries.get(key);
        return current != null && current.state() == CaseDelivery.State.IN_FLIGHT
                && current.attemptToken().equals(token) ? current : null;
    }

    @Override
    public synchronized boolean acknowledge(CaseDelivery.Key key, String token, String remoteReceipt) {
        require(RECEIPT, remoteReceipt);
        CaseDelivery current = leased(key, token);
        if (current == null) {
            return false;
        }
        replace(current.sent(remoteReceipt));
        return true;
    }

    @Override
    public synchronized boolean retry(CaseDelivery.Key key, String token, String code, Instant retryAt) {
        require(ERROR, code);
        if (retryAt == null) {
            throw new IllegalArgumentException("retry time required");
        }
        CaseDelivery current = leased(key, token);
        if (current == null) {
            return false;
        }
        replace(current.retry(code, retryAt));
        return true;
    }

    @Override
    public synchronized boolean uncertain(CaseDelivery.Key key, String token, String code) {
        require(ERROR, code);
        CaseDelivery current = leased(key, token);
        if (current == null) {
            return false;
        }
        replace(current.uncertain(code));
        return true;
    }

    @Override
    public synchronized boolean reconcile(CaseDelivery.Key key, String receipt,
                                          boolean confirmedAbsent, Instant now) {
        CaseDelivery current = deliveries.get(key);
        if (current == null || current.state() != CaseDelivery.State.UNCERTAIN) {
            return false;
        }
        if (receipt != null) {
            require(RECEIPT, receipt);
            replace(current.sent(receipt));
        } else if (confirmedAbsent && now != null) {
            replace(current.retry("VERIFIED_NOT_SENT", now));
        } else {
            return false;
        }
        return true;
    }

    @Override
    public synchronized List<CaseDelivery> unresolved() {
        return deliveries.values().stream().filter(value -> value.state() != CaseDelivery.State.DELIVERED)
                .sorted(Comparator.comparing(value -> value.key().idempotencyKey())).toList();
    }

    @Override
    public synchronized List<CaseNotice> active(Instant now, int limit) {
        if (now == null || limit < 1 || limit > 1000) {
            throw new IllegalArgumentException("invalid inbox poll");
        }
        return cases.values().stream().filter(c -> c.state() != CaseNotice.State.RESOLVED)
                .filter(c -> c.expiresAt().isAfter(now))
                .sorted(Comparator.comparing(CaseNotice::createdAt).reversed()).limit(limit).toList();
    }

    @Override
    public synchronized Optional<String> previousReceipt(CaseDelivery.Key key) {
        return deliveries.values().stream().filter(job -> job.key().origin() == key.origin()
                && job.key().scope().equals(key.scope())
                && job.key().caseId().equals(key.caseId())
                && job.key().destination() == key.destination()
                && job.key().revision() < key.revision()
                && job.state() == CaseDelivery.State.DELIVERED)
                .max(Comparator.comparingLong(job -> job.key().revision()))
                .map(CaseDelivery::remoteReceipt);
    }

    @Override
    public synchronized Change action(CaseDelivery.Key key, long expectedRevision, String actor,
                                      CaseNotice.Action action, Instant now, StaffGate gate) {
        CaseNotice current = cases.get(identity(key));
        if (current == null) {
            return Change.NOT_FOUND;
        }
        if (current.revision() != expectedRevision || key.revision() != expectedRevision) {
            return Change.STALE;
        }
        if (now == null || !current.expiresAt().isAfter(now)) {
            return Change.EXPIRED;
        }
        if (gate == null || !gate.permits(actor, current, action)) {
            return Change.DENIED;
        }
        // A custom gate may reenter the monitor and accept a newer canonical event.
        // Never let the synthetic-only action overwrite that newer projection.
        if (!current.equals(cases.get(identity(key)))) {
            return Change.STALE;
        }
        CaseNotice changed;
        try {
            changed = current.apply(action, actor);
        } catch (IllegalStateException error) {
            return Change.NOT_APPLICABLE;
        }
        Map<String, CaseNotice> updates = new HashMap<>(cases);
        Map<CaseDelivery.Key, CaseDelivery> outbox = new HashMap<>(deliveries);
        updates.put(changed.identity(), changed);
        enqueue(outbox, changed);
        commit(updates, outbox);
        return Change.APPLIED;
    }

    private static void require(Pattern pattern, String value) {
        if (value == null || !pattern.matcher(value).matches()) {
            throw new IllegalArgumentException("invalid delivery metadata");
        }
    }
}
