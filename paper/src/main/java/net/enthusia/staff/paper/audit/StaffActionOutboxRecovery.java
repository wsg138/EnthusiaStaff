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
            }
        } catch (IOException | RuntimeException exception) {
            warnOnce("Staff-action Discord recovery could not read journal directory", exception);
        } finally {
            running.set(false);
        }
    }

    private List<Path> journals() throws IOException {
        List<Path> result = new ArrayList<>();
        LocalDate oldest = LocalDate.now(ZoneOffset.UTC).minusDays(RETENTION_DAYS);
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
                    if (!date.isBefore(oldest) && !date.isAfter(LocalDate.now(ZoneOffset.UTC))) {
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

    private int replay(Path journal, int limit) {
        Path checkpoint = checkpointFor(journal);
        long position;
        try {
            position = readPosition(checkpoint);
        } catch (IOException | NumberFormatException exception) {
            warnOnce("Staff-action recovery checkpoint unreadable; manual repair required", exception);
            return 0;
        }
        long accepted = position;
        int processed = 0;
        try (RandomAccessFile file = new RandomAccessFile(journal.toFile(), "r")) {
            if (position > file.length()) {
                // A rotated/truncated journal cannot reuse an old byte offset.
                warnOnce("Staff-action journal shorter than checkpoint; refusing automatic offset reset", null);
                return 0;
            }
            file.seek(position);
            while (processed < limit) {
                String json = readCompleteLine(file);
                if (json == null) {
                    break; // EOF or an incomplete final line: never checkpoint it.
                }
                long end = file.getFilePointer();
                JsonNode node = JSON.readTree(json);
                if (node == null || !node.hasNonNull("eventId") || !node.hasNonNull("ts")) {
                    // Historical logs did not carry an ID. They cannot be replayed with
                    // safe deduplication and must not be given random IDs.
                    warnOnce("Staff-action journal contains legacy/unidentified record; not replaying it", null);
                    accepted = end;
                    processed++;
                    continue;
                }
                UUID eventId = UUID.fromString(node.path("eventId").asText());
                Instant timestamp = Instant.parse(node.path("ts").asText());
                writer.accept(eventId, json, timestamp);
                accepted = end;
                processed++;
            }
        } catch (Exception exception) {
            // Leave failed row uncommitted in the checkpoint for a future retry.
            warnOnce("Staff-action Discord replay paused; will retry undelivered record", exception);
        } finally {
            if (accepted != position) {
                try {
                    writePosition(checkpoint, accepted);
                } catch (IOException exception) {
                    // Replays are safe: the database row uses a stable unique ID.
                    warnOnce("Staff-action replay checkpoint write failed", exception);
                }
            }
        }
        return processed;
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
            if (value == -1) {
                return null;
            }
            if (value == '\n') {
                byte[] raw = buffer.toByteArray();
                int length = raw.length > 0 && raw[raw.length - 1] == '\r'
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
