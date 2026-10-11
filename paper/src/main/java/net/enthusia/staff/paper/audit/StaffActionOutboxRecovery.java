package net.enthusia.staff.paper.audit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
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
    private static final int MAX_RECORD_BYTES = 16_384;
    private static final int EMPTY_RECORD_LENGTH = 0;
    private static final int END_OF_FILE = -1;
    private static final int LINE_FEED_BYTE = '\n';
    private static final int RETENTION_DAYS = 30;
    private static final String PREFIX = "staff-actions-";
    private static final String EXTENSION = ".jsonl";
    private static final String CHECKPOINT_EXTENSION = ".cursor";
    private static final ObjectMapper JSON = new ObjectMapper();

    @FunctionalInterface
    interface OutboxWriter {
        void accept(UUID eventId, String json, Instant timestamp) throws Exception;
    }

    private final Path directory;
    private final Logger log;
    private final OutboxWriter writer;
    private final AtomicBoolean running = new AtomicBoolean();
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
                remaining -= replay(journal, remaining);
                pruneCompletedOldJournal(journal);
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
     * Only a fully acknowledged, old journal may be removed. The replay cursor
     * reflects committed database acceptance, not merely an attempted send.
     * Legacy first records are preserved for manual migration/inspection.
     */
    private void pruneCompletedOldJournal(Path journal) {
        String name = journal.getFileName().toString();
        LocalDate date = LocalDate.parse(name.substring(PREFIX.length(), PREFIX.length() + 10));
        if (!date.isBefore(LocalDate.now(ZoneOffset.UTC).minusDays(RETENTION_DAYS))) {
            return;
        }
        Path checkpoint = checkpointFor(journal);
        try {
            if (!Files.isRegularFile(journal, LinkOption.NOFOLLOW_LINKS)
                    || Files.isSymbolicLink(journal)
                    || !Files.isRegularFile(checkpoint, LinkOption.NOFOLLOW_LINKS)
                    || Files.isSymbolicLink(checkpoint)) {
                return;
            }
            long size = Files.size(journal);
            long acknowledged = readPosition(checkpoint);
            if (size == 0 || acknowledged != size) {
                warnOnce("Old staff-action Discord journal retained pending database replay", null);
                return;
            }
            try (RandomAccessFile file = new RandomAccessFile(journal.toFile(), "r")) {
                String first = readCompleteLine(file);
                JsonNode initial = first == null ? null : JSON.readTree(first);
                if (initial == null || !initial.hasNonNull("eventId")
                        || !initial.hasNonNull("ts")) {
                    warnOnce("Historical staff-action journal retained: unsafe to prune legacy records", null);
                    return;
                }
            }
            Files.delete(journal);
            Files.deleteIfExists(checkpoint);
        } catch (IOException | RuntimeException exception) {
            warnOnce("Old staff-action journal retention check failed; preserving journal", exception);
        }
    }

    private int replay(Path journal, int limit) {
        Path checkpoint = checkpointFor(journal);
        long position;
        try {
            position = readPosition(checkpoint);
        } catch (IOException | NumberFormatException exception) {
            warnOnce("Staff-action recovery checkpoint unreadable; manual repair required", exception);
            return 0;
        }
        return replayFromCheckpoint(journal, checkpoint, position, limit);
    }

    private int replayFromCheckpoint(Path journal, Path checkpoint, long position, int limit) {
        long accepted = position;
        int processed = 0;
        try (RandomAccessFile file = new RandomAccessFile(journal.toFile(), "r")) {
            if (position > file.length()) {
                warnOnce("Staff-action journal shorter than checkpoint; refusing automatic offset reset", null);
                return 0;
            }
            file.seek(position);
            while (processed < limit) {
                String json = readCompleteLine(file);
                if (json == null) {
                    break; // EOF or an incomplete final line: never checkpoint it.
                }
                storeRecordOrSkipLegacy(json);
                accepted = file.getFilePointer();
                processed++;
            }
        } catch (Exception exception) {
            // Keep the failed row in the journal; it will be retried next pass.
            warnOnce("Staff-action Discord replay paused; will retry undelivered record", exception);
        } finally {
            checkpointAcceptedRows(checkpoint, position, accepted);
        }
        return processed;
    }

    private void storeRecordOrSkipLegacy(String json) throws Exception {
        JsonNode node = JSON.readTree(json);
        if (node == null || !node.hasNonNull("eventId") || !node.hasNonNull("ts")) {
            // Pre-upgrade entries have no unique ID. Inventing one would duplicate
            // potentially delivered historical staff-action notifications.
            warnOnce("Staff-action journal contains legacy/unidentified record; not replaying it", null);
            return;
        }
        UUID eventId = UUID.fromString(node.path("eventId").asText());
        Instant timestamp = Instant.parse(node.path("ts").asText());
        writer.accept(eventId, json, timestamp);
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

    private static void writePosition(Path checkpoint, long position) throws IOException {
        Path temporary = checkpoint.resolveSibling(checkpoint.getFileName().toString() + ".tmp");
        if (Files.isSymbolicLink(temporary)) {
            throw new IOException("Unsafe staff-action checkpoint temporary path");
        }
        Files.writeString(temporary, Long.toString(position), StandardCharsets.UTF_8);
        try {
            Files.move(temporary, checkpoint, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(temporary, checkpoint, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
