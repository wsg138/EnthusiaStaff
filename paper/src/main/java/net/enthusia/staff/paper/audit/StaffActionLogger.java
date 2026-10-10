package net.enthusia.staff.paper.audit;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.sql.DataSource;
import net.enthusia.staff.domain.auth.StaffRank;

/**
 * Structured staff-action audit sink (overnight permission-model work).
 *
 * <p>Every logged action is appended as one JSON line to
 * {@code plugins/EnthusiaStaff/logs/staff-actions-<date>.log} (rotated daily, 30-day retention)
 * and to {@code plugins/EnthusiaStaff/discord-outbox/staff-actions-<date>.jsonl}, which the
 * Discord bot forwards to the configured {@code discord.log-forward-channel}. A best-effort
 * insert into the {@code discord_outbox} table (destination {@code logs-staffmode}) is queued
 * on the worker executor; it never blocks or fails the calling game thread.
 *
 * <p>This logger never throws: audit must not break gameplay.
 */
public final class StaffActionLogger implements AutoCloseable {
    private static final DateTimeFormatter DATE = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final String LOG_FILE_PREFIX = "staff-actions-";
    private static final char FIRST_PRINTABLE_ASCII = 0x20;
    private static final int RETENTION_DAYS = 30;
    private static final String OUTBOX_DESTINATION = "logs-staffmode";
    private static final String OUTBOX_EVENT_TYPE = "STAFF_ACTION";

    private final Logger log;
    private final ExecutorService workers;
    private final Supplier<DataSource> dataSource;
    private final String serverId;
    private final String forwardChannel;
    private final Path logDir;
    private final Path outboxDir;
    private final Object fileLock = new Object();
    private Optional<String> currentDate = Optional.empty();
    private Optional<BufferedWriter> actionWriter = Optional.empty();
    private Optional<BufferedWriter> outboxWriter = Optional.empty();

    public StaffActionLogger(
            Logger log,
            ExecutorService workers,
            Supplier<DataSource> dataSource,
            String serverId,
            String forwardChannel,
            Path dataDirectory
    ) {
        this.log = Objects.requireNonNull(log, "log");
        this.workers = Objects.requireNonNull(workers, "workers");
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        this.serverId = Objects.requireNonNull(serverId, "serverId");
        this.forwardChannel = forwardChannel == null ? "" : forwardChannel;
        Path data = Objects.requireNonNull(dataDirectory, "dataDirectory");
        this.logDir = data.resolve("logs");
        this.outboxDir = data.resolve("discord-outbox");
    }

    /**
     * Records one staff action. Never throws.
     *
     * @param action a stable kebab-case action id, e.g. {@code item-pickup}
     * @param detail free-form human detail (never contains secrets)
     */
    public void log(
            UUID actorId,
            String actorName,
            StaffRank rank,
            boolean vanished,
            boolean staffMode,
            String action,
            String detail
    ) {
        Instant now = Instant.now();
        String line = "{"
                + "\"ts\":\"" + now + "\","
                + "\"server\":" + json(serverId) + ","
                + "\"actor\":" + json(actorId == null ? "console" : actorId.toString()) + ","
                + "\"name\":" + json(actorName == null ? "?" : actorName) + ","
                + "\"rank\":" + json(rank == null ? "NONE" : rank.name()) + ","
                + "\"vanished\":" + vanished + ","
                + "\"staffMode\":" + staffMode + ","
                + "\"action\":" + json(action) + ","
                + "\"detail\":" + json(detail == null ? "" : detail)
                + "}";
        writeFiles(line, now);
        enqueueOutbox(line, now);
    }

    private void writeFiles(String line, Instant now) {
        synchronized (fileLock) {
            try {
                rotateIfNeeded(now);
                if (actionWriter.isEmpty() || outboxWriter.isEmpty()) {
                    return;
                }
                actionWriter.get().write(line);
                actionWriter.get().newLine();
                actionWriter.get().flush();
                outboxWriter.get().write(line);
                outboxWriter.get().newLine();
                outboxWriter.get().flush();
            } catch (IOException | RuntimeException exception) {
                log.log(Level.WARNING, "Staff action file logging failed; the action was not recorded", exception);
            }
        }
    }

    private void rotateIfNeeded(Instant now) throws IOException {
        String date = DATE.format(LocalDate.ofInstant(now, ZoneOffset.UTC));
        if (currentDate.map(date::equals).orElse(false) && actionWriter.isPresent()) {
            return;
        }
        closeWriters();
        Files.createDirectories(logDir);
        Files.createDirectories(outboxDir);
        currentDate = Optional.of(date);
        actionWriter = Optional.of(Files.newBufferedWriter(
                logDir.resolve(LOG_FILE_PREFIX + date + ".log"),
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND));
        outboxWriter = Optional.of(Files.newBufferedWriter(
                outboxDir.resolve(LOG_FILE_PREFIX + date + ".jsonl"),
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND));
        pruneOldFiles(logDir, LOG_FILE_PREFIX, ".log");
        pruneOldFiles(outboxDir, LOG_FILE_PREFIX, ".jsonl");
    }

    private void pruneOldFiles(Path directory, String prefix, String suffix) {
        LocalDate cutoff = LocalDate.now(ZoneOffset.UTC).minusDays(RETENTION_DAYS);
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory, prefix + "*" + suffix)) {
            for (Path file : stream) {
                String name = file.getFileName().toString();
                String datePart = name.substring(prefix.length(), name.length() - suffix.length());
                try {
                    if (LocalDate.parse(datePart, DATE).isBefore(cutoff)) {
                        Files.deleteIfExists(file);
                    }
                } catch (RuntimeException parseFailure) {
                    log.log(Level.FINE, "Ignoring unparsable staff log file name: {0}", name);
                }
            }
        } catch (IOException | RuntimeException exception) {
            log.log(Level.FINE, "Staff log retention pruning failed", exception);
        }
    }

    private void enqueueOutbox(String payloadJson, Instant now) {
        try {
            workers.execute(() -> insertOutbox(payloadJson, now));
        } catch (RejectedExecutionException exception) {
            log.fine("Staff action Discord outbox skipped: worker queue is full");
        } catch (RuntimeException exception) {
            log.log(Level.FINE, "Staff action Discord outbox could not be queued", exception);
        }
    }

    private void insertOutbox(String payloadJson, Instant now) {
        DataSource source = dataSource.get();
        if (source == null) {
            return;
        }
        UUID messageId = UUID.randomUUID();
        String payload = payloadJson.substring(0, payloadJson.length() - 1)
                + ",\"forwardChannel\":" + json(forwardChannel) + "}";
        try (Connection connection = source.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     INSERT INTO discord_outbox(message_id, idempotency_key, destination, event_type,
                         payload_json, available_at, created_at)
                     VALUES (?, ?, ?, ?, ?, ?, ?)
                     """)) {
            statement.setBytes(1, toBytes(messageId));
            statement.setString(2, "staff-action:" + messageId);
            statement.setString(3, OUTBOX_DESTINATION);
            statement.setString(4, OUTBOX_EVENT_TYPE);
            statement.setString(5, payload);
            statement.setTimestamp(6, Timestamp.from(now));
            statement.setTimestamp(7, Timestamp.from(now));
            statement.executeUpdate();
        } catch (Exception exception) {
            // Best-effort by design (M3 lesson): the file sinks already hold the entry.
            log.log(Level.FINE, "Staff action Discord outbox insert failed", exception);
        }
    }

    private static byte[] toBytes(UUID id) {
        byte[] bytes = new byte[16];
        long most = id.getMostSignificantBits();
        long least = id.getLeastSignificantBits();
        for (int i = 0; i < 8; i++) {
            bytes[i] = (byte) (most >>> (8 * (7 - i)));
            bytes[8 + i] = (byte) (least >>> (8 * (7 - i)));
        }
        return bytes;
    }

    private static String json(String value) {
        StringBuilder escaped = new StringBuilder(value.length() + 2);
        escaped.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> escaped.append("\\\"");
                case '\\' -> escaped.append("\\\\");
                case '\n' -> escaped.append("\\n");
                case '\r' -> escaped.append("\\r");
                case '\t' -> escaped.append("\\t");
                default -> {
                    if (c < FIRST_PRINTABLE_ASCII) {
                        escaped.append(String.format("\\u%04x", (int) c));
                    } else {
                        escaped.append(c);
                    }
                }
            }
        }
        return escaped.append('"').toString();
    }

    private void closeWriters() {
        try {
            if (actionWriter.isPresent()) {
                actionWriter.get().close();
            }
        } catch (IOException ignored) {
            // Closing best-effort; nothing to do.
        } finally {
            actionWriter = Optional.empty();
        }
        try {
            if (outboxWriter.isPresent()) {
                outboxWriter.get().close();
            }
        } catch (IOException ignored) {
            // Closing best-effort; nothing to do.
        } finally {
            outboxWriter = Optional.empty();
        }
    }

    @Override
    public void close() {
        synchronized (fileLock) {
            closeWriters();
            currentDate = Optional.empty();
        }
    }
}
