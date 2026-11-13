package net.enthusia.staff.persistence.modcase;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.List;
import net.enthusia.staff.domain.modcase.CaseDelivery;
import net.enthusia.staff.domain.modcase.CaseNotice;

/** Isolated single-process preproduction journal; no player message bodies are stored. */
final class CaseSnapshotFile {
    record Snapshot(List<CaseNotice> cases, List<CaseDelivery> deliveries) {
        Snapshot {
            cases = List.copyOf(cases);
            deliveries = List.copyOf(deliveries);
        }
    }

    private final Path directory;
    private final Path target;
    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());

    CaseSnapshotFile(Path directory) {
        if (directory == null) {
            throw new IllegalArgumentException("journal directory required");
        }
        this.directory = directory.toAbsolutePath().normalize();
        this.target = this.directory.resolve("moderation-case-ledger.json");
        try {
            Files.createDirectories(this.directory);
            if (Files.isSymbolicLink(this.directory) || Files.isSymbolicLink(target)) {
                throw new IllegalArgumentException("symbolic journal path is not allowed");
            }
        } catch (IOException error) {
            throw new IllegalStateException("cannot prepare moderation case journal", error);
        }
    }

    Snapshot load() {
        if (!Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
            return new Snapshot(List.of(), List.of());
        }
        try {
            Snapshot snapshot = mapper.readValue(target.toFile(), Snapshot.class);
            if (snapshot == null || snapshot.cases() == null || snapshot.deliveries() == null) {
                throw new IOException("incomplete journal snapshot");
            }
            return snapshot;
        } catch (IOException error) {
            throw new IllegalStateException("cannot read moderation case journal; fail closed", error);
        }
    }

    void save(Snapshot snapshot) {
        Path temporary = null;
        try {
            temporary = Files.createTempFile(directory, ".moderation-case-", ".pending");
            mapper.writeValue(temporary.toFile(), snapshot);
            try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.WRITE)) {
                channel.force(true);
            }
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException error) {
            throw new IllegalStateException("cannot persist moderation case delivery state", error);
        } finally {
            if (temporary != null) {
                try {
                    Files.deleteIfExists(temporary);
                } catch (IOException ignored) {
                    // A failed write is never treated as a committed acknowledgement.
                }
            }
        }
    }
}
