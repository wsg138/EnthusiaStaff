package net.enthusia.staff.discordbot;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

/** A small, durable, explicit allowlist of Discord snowflake IDs; never caches guild members. */
final class DiscordAccountAgeBypassStore {
    private static final int MAX_IDS = 1024;
    private static final long MAX_FILE_BYTES = 64 * 1024;
    private static final Pattern SNOWFLAKE = Pattern.compile("[1-9][0-9]{15,19}");

    private final Path file;
    private volatile Set<Long> ids;

    private DiscordAccountAgeBypassStore(Path file, Set<Long> initial) {
        this.file = file;
        this.ids = Set.copyOf(initial);
    }

    static DiscordAccountAgeBypassStore open(Path file) throws IOException {
        Objects.requireNonNull(file, "file");
        Path canonical = file.toAbsolutePath().normalize();
        Set<Long> initial = new HashSet<>();
        if (Files.exists(canonical)) {
            if (!Files.isRegularFile(canonical) || Files.size(canonical) > MAX_FILE_BYTES) {
                throw new IOException("account-age bypass file is not a bounded regular file");
            }
            for (String row : Files.readAllLines(canonical, StandardCharsets.UTF_8)) {
                if (row.isBlank()) {
                    continue;
                }
                try {
                    initial.add(parseId(row.trim()));
                } catch (IllegalArgumentException error) {
                    throw new IOException("account-age bypass file has an invalid ID", error);
                }
                if (initial.size() > MAX_IDS) {
                    throw new IOException("account-age bypass file exceeds the maximum entry count");
                }
            }
        }
        return new DiscordAccountAgeBypassStore(canonical, initial);
    }

    static long parseId(String value) {
        if (value == null || !SNOWFLAKE.matcher(value).matches()) {
            throw new IllegalArgumentException("Discord user ID must contain 16-20 digits");
        }
        return Long.parseUnsignedLong(value);
    }

    boolean contains(long id) {
        return ids.contains(id);
    }

    boolean add(long id) throws IOException {
        synchronized (this) {
        if (ids.contains(id)) {
            return false;
        }
        Set<Long> next = new HashSet<>(ids);
        if (next.size() >= MAX_IDS) {
            throw new IOException("account-age bypass list is full");
        }
        next.add(id);
        persist(next);
        ids = Set.copyOf(next);
        return true;
        }
    }

    boolean remove(long id) throws IOException {
        synchronized (this) {
        if (!ids.contains(id)) {
            return false;
        }
        Set<Long> next = new HashSet<>(ids);
        next.remove(id);
        persist(next);
        ids = Set.copyOf(next);
        return true;
    }

    int size() {
        return ids.size();
    }

    private void persist(Set<Long> next) throws IOException {
        Path parent = file.getParent();
        if (parent == null || !Files.isDirectory(parent)) {
            throw new IOException("account-age bypass directory does not exist");
        }
        Path temporary = Files.createTempFile(parent, ".age-bypass-", ".tmp");
        try {
            writeTemporary(temporary, serialized(next));
            replaceAtomically(temporary, parent);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static String serialized(Set<Long> next) {
        StringBuilder lines = new StringBuilder();
        TreeSet<String> sorted = new TreeSet<>();
        for (long id : next) {
            sorted.add(Long.toUnsignedString(id));
        }
        for (String id : sorted) {
            lines.append(id).append('\n');
        }
        return lines.toString();
    }

    private static void writeTemporary(Path temporary, String lines) throws IOException {
        try {
            Files.setPosixFilePermissions(temporary, PosixFilePermissions.fromString("rw-------"));
        } catch (UnsupportedOperationException ignored) {
            // Windows has no POSIX permissions; existing ACLs protect the container.
        }
        Files.writeString(temporary, lines, StandardCharsets.UTF_8, StandardOpenOption.TRUNCATE_EXISTING);
        try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.WRITE)) {
            channel.force(true);
        }
    }

    private void replaceAtomically(Path temporary, Path parent) throws IOException {
        try {
            Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException failure) {
            throw new IOException("atomic account-age bypass persistence is unsupported", failure);
        }
        syncDirectory(parent);
    }

    private static void syncDirectory(Path parent) {
        try (FileChannel directory = FileChannel.open(parent, StandardOpenOption.READ)) {
            directory.force(true);
        } catch (IOException | UnsupportedOperationException ignored) {
            // Windows and some filesystems do not support directory fsync.
        }
    }

}
