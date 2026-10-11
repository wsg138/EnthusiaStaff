package net.enthusia.staff.paper.audit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Replays staff-action JSONL records into the durable Discord database outbox.
 * Runs only on Paper's asynchronous scheduler, never on a region/game thread.
 *
 * <p>The checkpoint always follows database acceptance. After a crash, a row
 * may be offered again; the database's unique event ID must make that a no-op.
 * This class does not promise exactly-once Discord HTTP delivery.
 */
final class StaffActionOutboxRecovery {
    private static final int MAX_PER_TICK = 200;
    private static final int MAX_PRUNE_TRACKED_JOURNALS = 256;
    private static final int MAX_RECORD_BYTES = 16_384;
    private static final int EMPTY_RECORD_LENGTH = 0;
    private static final int END_OF_FILE = -1;
    private static final int LINE_FEED_BYTE = '\n';
    private static final int RETENTION_DAYS = 30;
    private static final String PREFIX = "staff-actions-";
    private static final String EXTENSION = ".jsonl";
    private static final String CHECKPOINT_EXTENSION = ".cursor";
    private static final String EVENT_ID_FIELD = "eventId";
    private static final String TIMESTAMP_FIELD = "ts";
    private static final ObjectMapper JSON = new ObjectMapper();

    @FunctionalInterface
    interface OutboxWriter {
        void accept(UUID eventId, String json, Instant timestamp) throws Exception;
    }

    private final Path directory;
    private final Logger log;
    private final OutboxWriter writer;
    private final AtomicBoolean running = new AtomicBoolean();
    // Only recovery's async scheduler accesses this map. Each old journal is
    // validated incrementally; a completed replay cursor alone is not proof
    // that legacy records were delivered.
    private final Map<Path, PruneScan> pruneScans = new LinkedHashMap<>() {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Path, PruneScan> eldest) {
            return size() > MAX_PRUNE_TRACKED_JOURNALS;
        }
    };

    private static final class PruneScan {
        private final long size;
        private final FileTime modified;
        private long offset;
        private boolean unsafe;

        private PruneScan(long size, FileTime modified) {
            this.size = size;
            this.modified = modified;
        }
    }
    private final AtomicLong nextWarningAtMillis = new AtomicLong();
    private static final long WARNING_INTERVAL_MILLIS = 60_000L;

    StaffActionOutboxRecovery(Path directory, Logger log, OutboxWriter writer) {
        this.directory = directory;
        this.log = log;
        this.writer = writer;
    }

    void recoverOnce() {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        try {
            if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)
                    || Files.isSymbolicLink(directory)) {
                return;
            }
            int remaining = MAX_PER_TICK;
            for (Path journal : journals()) {
                if (remaining == 0) {
                    break;
                }
                ReplayResult replayed = replay(journal, remaining);
                remaining -= replayed.inspected();
                if (replayed.outboxUnavailable()) {
                    // Once a database insertion fails, do not hammer it for
                    // every other journal during this pass.
                    break;
                }
                // Share the per-pass record budget with retention validation.
                remaining -= pruneCompletedOldJournal(journal, remaining);
            }
        } catch (IOException | RuntimeException exception) {
            warnOnce("Staff-action Discord recovery could not read journal directory", exception);
        } finally {
            running.set(false);
        }
    }

    private List<Path> journals() throws IOException {
        List<Path> result = new ArrayList<>();
        try (DirectoryStream<Path> entries = Files.newDirectoryStream(directory, PREFIX + "*" + EXTENSION)) {
            for (Path candidate : entries) {
                if (!Files.isRegularFile(candidate, LinkOption.NOFOLLOW_LINKS)
                        || Files.isSymbolicLink(candidate)) {
                    continue;
                }
                String name = candidate.getFileName().toString();
                if (name.length() != PREFIX.length() + 10 + EXTENSION.length()) {
                    continue;
                }
                try {
                    LocalDate date = LocalDate.parse(name.substring(PREFIX.length(), PREFIX.length() + 10));
                    // Never discard an unacknowledged journal simply because the
                    // database was unavailable beyond the normal retention period.
                    if (!date.isAfter(LocalDate.now(ZoneOffset.UTC))) {
                        result.add(candidate);
                    }
                } catch (DateTimeParseException exception) {
                    log.log(Level.FINE, "Ignoring invalid staff-action journal filename", exception);
                }
            }
        }
        result.sort(Comparator.comparing(path -> path.getFileName().toString()));
        return result;
    }

    /**
     * A fully acknowledged cursor is necessary but not sufficient for pruning:
     * older replay code advanced past unidentified records without inserting
     * them into MariaDB. Verify EVERY record, using the same bounded budget as
     * journal replay, before removing historical data.
     */
    private int pruneCompletedOldJournal(Path journal, int budget) {
        if (budget == 0 || !pastRetentionPeriod(journal)) {
            return 0;
        }
        try {
            PruneResult result = safeToPrune(journal, budget);
            if (result.complete()) {
                // Verify that the checkpoint still describes this entire file.
                // A historical journal should no longer be written by the logger.
                Files.delete(journal);
                Files.deleteIfExists(checkpointFor(journal));
                pruneScans.remove(journal);
            }
            return result.scanned();
        } catch (IOException | RuntimeException exception) {
            warnOnce("Old staff-action journal retention check failed; preserving journal", exception);
            return 0;
        }
    }

    private record PruneResult(int scanned, boolean complete) {}

    private static boolean pastRetentionPeriod(Path journal) {
        String name = journal.getFileName().toString();
        LocalDate date = LocalDate.parse(name.substring(PREFIX.length(), PREFIX.length() + 10));
        return date.isBefore(LocalDate.now(ZoneOffset.UTC).minusDays(RETENTION_DAYS));
    }

    private PruneResult safeToPrune(Path journal, int budget) throws IOException {
        Path checkpoint = checkpointFor(journal);
        if (!isSafeRegularFile(journal) || !isSafeRegularFile(checkpoint)) {
            return new PruneResult(0, false);
        }
        long size = Files.size(journal);
        if (size == 0 || readPosition(checkpoint) != size) {
            pruneScans.remove(journal);
            warnOnce("Old staff-action Discord journal retained pending database replay", null);
            return new PruneResult(0, false);
        }
        FileTime modified = Files.getLastModifiedTime(journal, LinkOption.NOFOLLOW_LINKS);
        PruneScan scan = currentPruneScan(journal, size, modified);
        if (scan.unsafe) {
            return new PruneResult(0, false);
        }
        PruneResult scanned = validateOldRecords(journal, scan, size, budget);
        if (!scanned.complete()) {
            return scanned;
        }
        if (!unchangedAfterScan(journal, checkpoint, size, modified)) {
            pruneScans.remove(journal);
            return new PruneResult(scanned.scanned(), false);
        }
        return scanned;
    }

    private static boolean isSafeRegularFile(Path path) {
        return Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)
                && !Files.isSymbolicLink(path);
    }

    private PruneScan currentPruneScan(Path journal, long size, FileTime modified) {
        PruneScan scan = pruneScans.get(journal);
        if (scan == null || scan.size != size || !scan.modified.equals(modified)) {
            scan = new PruneScan(size, modified);
            pruneScans.put(journal, scan);
        }
        return scan;
    }

    private PruneResult validateOldRecords(Path journal, PruneScan scan, long size, int budget)
            throws IOException {
        int inspected = 0;
        try (RandomAccessFile file = new RandomAccessFile(journal.toFile(), "r")) {
            if (file.length() != size) {
                pruneScans.remove(journal);
                return new PruneResult(0, false);
            }
            file.seek(scan.offset);
            while (inspected < budget && scan.offset < size) {
                String json = readCompleteLine(file);
                if (json == null || !hasReplaySafeIdentity(json)) {
                    // An unidentified, partial, or malformed historical record
                    // must be retained even when its cursor says EOF.
                    scan.unsafe = true;
                    warnOnce("Historical staff-action journal retained: unsafe legacy record", null);
                    return new PruneResult(inspected + 1, false);
                }
                scan.offset = file.getFilePointer();
                inspected++;
            }
        }
        return new PruneResult(inspected, scan.offset == size);
    }

    private static boolean unchangedAfterScan(Path journal, Path checkpoint,
                                              long size, FileTime modified) throws IOException {
        return Files.size(journal) == size
                && Files.getLastModifiedTime(journal, LinkOption.NOFOLLOW_LINKS).equals(modified)
                && readPosition(checkpoint) == size;
    }

    private static boolean hasReplaySafeIdentity(String json) {
        try {
            JsonNode node = JSON.readTree(json);
            if (node == null || !node.path(EVENT_ID_FIELD).isTextual()
                    || !node.path(TIMESTAMP_FIELD).isTextual()) {
                return false;
            }
            UUID.fromString(node.path(EVENT_ID_FIELD).asText());
            Instant.parse(node.path(TIMESTAMP_FIELD).asText());
            return true;
        } catch (IOException | RuntimeException exception) {
            return false;
        }
    }

    private record ReplayResult(int inspected, boolean outboxUnavailable) {}

    private static final class OutboxInsertionFailure extends Exception {
        private static final long serialVersionUID = 1L;
        private OutboxInsertionFailure(Exception cause) {
            super(cause);
        }
    }

    private ReplayResult replay(Path journal, int limit) {
        Path checkpoint = checkpointFor(journal);
        long position;
        try {
            position = readPosition(checkpoint);
        } catch (IOException | NumberFormatException exception) {
            warnOnce("Staff-action recovery checkpoint unreadable; manual repair required", exception);
            return new ReplayResult(0, false);
        }
        return replayFromCheckpoint(journal, checkpoint, position, limit);
    }

    private ReplayResult replayFromCheckpoint(Path journal, Path checkpoint, long position, int limit) {
        long accepted = position;
        int processed = 0;
        boolean outboxUnavailable = false;
        try (RandomAccessFile file = new RandomAccessFile(journal.toFile(), "r")) {
            if (position > file.length()) {
                warnOnce("Staff-action journal shorter than checkpoint; refusing automatic offset reset", null);
                return new ReplayResult(0, false);
            }
            file.seek(position);
            while (processed < limit) {
                String json = readCompleteLine(file);
                if (json == null) {
                    break; // EOF or an incomplete final line: never checkpoint it.
                }
                // Count every complete row inspected, including a rejected
                // database write, so outages cannot bypass the work budget.
                processed++;
                storeRecordOrSkipLegacy(json);
                accepted = file.getFilePointer();
            }
        } catch (OutboxInsertionFailure exception) {
            outboxUnavailable = true;
            warnOnce("Staff-action outbox unavailable; pausing replay until next pass", exception);
        } catch (Exception exception) {
            // Keep the failed row in the journal; it will be retried next pass.
            warnOnce("Staff-action Discord replay paused; will retry undelivered record", exception);
        } finally {
            checkpointAcceptedRows(checkpoint, position, accepted);
        }
        return new ReplayResult(processed, outboxUnavailable);
    }

    private void storeRecordOrSkipLegacy(String json) throws Exception {
        JsonNode node = JSON.readTree(json);
        if (node == null || !node.hasNonNull(EVENT_ID_FIELD) || !node.hasNonNull(TIMESTAMP_FIELD)) {
            // Pre-upgrade entries have no unique ID. Inventing one would duplicate
            // potentially delivered historical staff-action notifications.
            warnOnce("Staff-action journal contains legacy/unidentified record; not replaying it", null);
            return;
        }
        UUID eventId = UUID.fromString(node.path(EVENT_ID_FIELD).asText());
        Instant timestamp = Instant.parse(node.path(TIMESTAMP_FIELD).asText());
        try {
            writer.accept(eventId, json, timestamp);
        } catch (Exception exception) {
            throw new OutboxInsertionFailure(exception);
        }
    }

    private void checkpointAcceptedRows(Path checkpoint, long oldPosition, long accepted) {
        if (oldPosition == accepted) {
            return;
        }
        try {
            writePosition(checkpoint, accepted);
        } catch (IOException exception) {
            // The stable database ID permits duplicate offers after checkpoint failures.
            warnOnce("Staff-action replay checkpoint write failed", exception);
        }
    }

    /**
     * Reads no more than one bounded complete UTF-8 JSONL record. The limit is
     * checked while reading, so a malformed/legacy entry cannot allocate an
     * unbounded Java String before being rejected. CRLF and LF are supported.
     */
    private static String readCompleteLine(RandomAccessFile input) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        for (int index = 0; index < MAX_RECORD_BYTES; index++) {
            int value = input.read();
            if (value == END_OF_FILE) {
                return null;
            }
            if (value == LINE_FEED_BYTE) {
                byte[] raw = buffer.toByteArray();
                int length = raw.length > EMPTY_RECORD_LENGTH && raw[raw.length - 1] == '\r'
                        ? raw.length - 1 : raw.length;
                return new String(raw, 0, length, StandardCharsets.UTF_8);
            }
            buffer.write(value);
        }
        throw new IOException("Staff-action journal record exceeds maximum byte length");
    }

    private void warnOnce(String message, Exception error) {
        long now = System.currentTimeMillis();
        long next = nextWarningAtMillis.get();
        if (now >= next && nextWarningAtMillis.compareAndSet(next, now + WARNING_INTERVAL_MILLIS)) {
            if (error == null) {
                log.warning(message);
            } else {
                log.log(Level.WARNING, message, error);
            }
        }
    }

    private Path checkpointFor(Path journal) {
        return directory.resolve(journal.getFileName().toString() + CHECKPOINT_EXTENSION);
    }

    private static long readPosition(Path checkpoint) throws IOException {
        if (!Files.exists(checkpoint, LinkOption.NOFOLLOW_LINKS)) {
            return 0L;
        }
        if (!Files.isRegularFile(checkpoint, LinkOption.NOFOLLOW_LINKS)
                || Files.isSymbolicLink(checkpoint)) {
            throw new IOException("Invalid staff-action replay checkpoint");
        }
        long offset = Long.parseLong(Files.readString(checkpoint, StandardCharsets.UTF_8).strip());
        if (offset < 0) {
            throw new IOException("Negative staff-action replay checkpoint");
        }
        return offset;
    }

    private void writePosition(Path checkpoint, long position) throws IOException {
        Path temporary = checkpoint.resolveSibling(checkpoint.getFileName().toString() + ".tmp");
        if (Files.isSymbolicLink(temporary)) {
            throw new IOException("Unsafe staff-action checkpoint temporary path");
        }

        // A rename alone does not ensure that the checkpoint's bytes survive
        // a host crash. Force the complete temporary file before renaming.
        try (FileChannel file = FileChannel.open(temporary, StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
            ByteBuffer bytes = StandardCharsets.UTF_8.encode(Long.toString(position));
            while (bytes.hasRemaining()) {
                file.write(bytes);
            }
            file.force(true);
        }

        try {
            Files.move(temporary, checkpoint, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(temporary, checkpoint, StandardCopyOption.REPLACE_EXISTING);
        }

        // Directory fsync makes the renamed entry durable on supporting
        // filesystems. Windows and some providers reject directory channels.
        Path directory = checkpoint.getParent();
        if (directory != null) {
            try (FileChannel parent = FileChannel.open(directory, StandardOpenOption.READ)) {
                parent.force(true);
            } catch (IOException | UnsupportedOperationException exception) {
                // Directory sync is best-effort on providers that reject it.
                // Re-offering the stable ID remains safe after a crash.
                log.log(Level.FINE, "Staff-action checkpoint directory fsync unavailable", exception);
            }
        }
    }
}
