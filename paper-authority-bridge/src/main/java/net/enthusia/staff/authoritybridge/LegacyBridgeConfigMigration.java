package net.enthusia.staff.authoritybridge;

import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.Channels;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.OpenOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclEntryPermission;
import java.nio.file.attribute.AclEntryType;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Copies only legacy bridge secret-bearing configuration files into the Paper data directory.
 * Existing destination files always win; disagreements fail closed, never overwrite.
 */
public final class LegacyBridgeConfigMigration {
    private static final String LEGACY_DIRECTORY = "EnthusiaStaffAuthorityBridge";
    private static final int MAX_FILE_BYTES = 64 * 1024;
    private static final List<String> CONFIG_FILES = List.of(
            AuthorityBridgeConfiguration.FILE_NAME, TransitionCollectorConfiguration.FILE_NAME);

    private LegacyBridgeConfigMigration() {
    }

    public static Result migrate(Path staffDirectory) {
        if (staffDirectory == null || staffDirectory.getParent() == null) {
            throw new IllegalArgumentException("invalid Staff data directory");
        }
        Path destination = staffDirectory.toAbsolutePath().normalize();
        Path legacy = destination.resolveSibling(LEGACY_DIRECTORY);
        try {
            requireDirectory(destination);
            if (Files.notExists(legacy, LinkOption.NOFOLLOW_LINKS)) {
                return new Result(0, 0);
            }
            requireDirectory(legacy);
            return importFiles(legacy, destination);
        } catch (IOException exception) {
            throw new IllegalStateException("legacy bridge configuration import failed", exception);
        }
    }

    private static Result importFiles(Path source, Path target) throws IOException {
        List<StagedFile> staged = new ArrayList<>();
        int existing = 0;
        try {
            for (String name : CONFIG_FILES) {
                Path original = source.resolve(name);
                if (Files.notExists(original, LinkOption.NOFOLLOW_LINKS)) {
                    continue;
                }
                byte[] bytes = readSafe(original);
                Path destination = target.resolve(name);
                if (Files.exists(destination, LinkOption.NOFOLLOW_LINKS)) {
                    if (!Arrays.equals(bytes, readSafe(destination))) {
                        throw new IOException("legacy configuration target differs");
                    }
                    existing++;
                    continue;
                }
                staged.add(stage(target, name, bytes));
            }
            install(staged);
            return new Result(staged.size(), existing);
        } finally {
            for (StagedFile file : staged) {
                Files.deleteIfExists(file.temporary());
            }
        }
    }

    private static void install(List<StagedFile> staged) throws IOException {
        List<Path> created = new ArrayList<>();
        try {
            for (StagedFile entry : staged) {
                if (Files.exists(entry.destination(), LinkOption.NOFOLLOW_LINKS)) {
                    throw new IOException("legacy configuration destination appeared during import");
                }
                // Atomic create-without-replacement: an atomic rename can replace an existing
                // target on some providers even without REPLACE_EXISTING.
                Files.createLink(entry.destination(), entry.temporary());
                created.add(entry.destination());
            }
        } catch (IOException exception) {
            for (Path destination : created) {
                Files.deleteIfExists(destination);
            }
            throw exception;
        }
    }

    private static StagedFile stage(Path folder, String name, byte[] contents) throws IOException {
        Path temporary = secureTemporaryFile(folder);
        try {
            secure(temporary);
            Files.write(temporary, contents);
            return new StagedFile(temporary, folder.resolve(name));
        } catch (IOException | RuntimeException exception) {
            Files.deleteIfExists(temporary);
            throw exception;
        }
    }

    private static Path secureTemporaryFile(Path folder) throws IOException {
        if (Files.getFileStore(folder).supportsFileAttributeView("posix")) {
            return Files.createTempFile(folder, ".bridge-import-", ".tmp",
                    PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
        }
        AclFileAttributeView acl = Files.getFileAttributeView(folder, AclFileAttributeView.class);
        if (acl == null) {
            throw new IOException("filesystem cannot protect temporary configuration");
        }
        return Files.createTempFile(folder, ".bridge-import-", ".tmp", ownerOnlyAcl(acl));
    }

    private static FileAttribute<List<AclEntry>> ownerOnlyAcl(AclFileAttributeView view) throws IOException {
        List<AclEntry> entries = List.of(AclEntry.newBuilder()
                .setType(AclEntryType.ALLOW)
                .setPrincipal(view.getOwner())
                .setPermissions(EnumSet.allOf(AclEntryPermission.class))
                .build());
        return new FileAttribute<>() {
            @Override
            public String name() {
                return "acl:acl";
            }

            @Override
            public List<AclEntry> value() {
                return entries;
            }
        };
    }

    private static void secure(Path file) throws IOException {
        PosixFileAttributeView posix = Files.getFileAttributeView(file, PosixFileAttributeView.class);
        if (posix != null) {
            posix.setPermissions(PosixFilePermissions.fromString("rw-------"));
            return;
        }
        AclFileAttributeView acl = Files.getFileAttributeView(file, AclFileAttributeView.class);
        if (acl == null) {
            throw new IOException("filesystem cannot restrict migrated configuration");
        }
        acl.setAcl(ownerOnlyAcl(acl).value());
    }

    private static byte[] readSafe(Path file) throws IOException {
        if (Files.isSymbolicLink(file) || !Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)
                || Files.size(file) > MAX_FILE_BYTES) {
            throw new IOException("legacy configuration is unsafe or oversized");
        }
        try (InputStream input = Channels.newInputStream(Files.newByteChannel(
                file, Set.<OpenOption>of(StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS)))) {
            byte[] content = input.readNBytes(MAX_FILE_BYTES + 1);
            if (content.length > MAX_FILE_BYTES) {
                throw new IOException("legacy configuration exceeds size limit");
            }
            return content;
        }
    }

    private static void requireDirectory(Path folder) throws IOException {
        if (Files.isSymbolicLink(folder) || !Files.isDirectory(folder, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("legacy bridge configuration directory is unsafe");
        }
    }

    public record Result(int copied, int alreadyPresent) {
    }

    private record StagedFile(Path temporary, Path destination) {
    }
}
