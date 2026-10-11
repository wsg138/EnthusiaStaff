package net.enthusia.staff.authoritybridge;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LegacyBridgeConfigMigrationTest {
    private static final String OLD_DIRECTORY = "EnthusiaStaffAuthorityBridge";
    private static final String PAPER_DIRECTORY = "EnthusiaStaff";
    private static final String AUTHORITY = "authority.properties";
    private static final String COLLECTOR = "collector.properties";
    private static final String SAMPLE_CONFIG = "public-example-test-data-with-no-credential";
    @TempDir
    Path folder;

    @Test
    void absenceOfLegacyFilesLeavesDefaultDisabledAndDoesNotCreateFlags() throws IOException {
        Path target = Files.createDirectory(folder.resolve(PAPER_DIRECTORY));
        assertEquals(new LegacyBridgeConfigMigration.Result(0, 0),
                LegacyBridgeConfigMigration.migrate(target));
        assertFalse(Files.exists(target.resolve(EmbeddedAuthorityBridge.SWITCH_FILE)));
    }

    @Test
    void copiesOnlyTwoKnownFilesPreservesBytesAndLegacyAndIsIdempotent() throws IOException {
        Path legacy = Files.createDirectory(folder.resolve(OLD_DIRECTORY));
        Path target = Files.createDirectory(folder.resolve(PAPER_DIRECTORY));
        byte[] secret = (SAMPLE_CONFIG + "\n").getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] collector = "sample-field=example-only\r\n".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        Files.write(legacy.resolve(AUTHORITY), secret);
        Files.write(legacy.resolve(COLLECTOR), collector);
        Files.writeString(legacy.resolve("other.properties"), "do not migrate");
        assertEquals(new LegacyBridgeConfigMigration.Result(2, 0),
                LegacyBridgeConfigMigration.migrate(target));
        assertArrayEquals(secret, Files.readAllBytes(target.resolve(AUTHORITY)));
        assertArrayEquals(collector, Files.readAllBytes(target.resolve(COLLECTOR)));
        assertArrayEquals(secret, Files.readAllBytes(legacy.resolve(AUTHORITY)));
        assertFalse(Files.exists(target.resolve("other.properties")));
        assertFalse(Files.exists(target.resolve(EmbeddedAuthorityBridge.SWITCH_FILE)));
        assertEquals(new LegacyBridgeConfigMigration.Result(0, 2),
                LegacyBridgeConfigMigration.migrate(target));
        assertEquals(Set.of(AUTHORITY, COLLECTOR),
                fileNames(target));
    }

    @Test
    void preExistingConflictingConfigFailsWithoutOverwritingOrPartialInstall() throws IOException {
        Path legacy = Files.createDirectory(folder.resolve(OLD_DIRECTORY));
        Path target = Files.createDirectory(folder.resolve(PAPER_DIRECTORY));
        Files.writeString(legacy.resolve(AUTHORITY), SAMPLE_CONFIG);
        Files.writeString(legacy.resolve(COLLECTOR), "sample-field=old-example");
        Files.writeString(target.resolve(COLLECTOR), "sample-field=new-example");
        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> LegacyBridgeConfigMigration.migrate(target));
        assertFalse(failure.getMessage().contains("old-example"));
        assertFalse(failure.getMessage().contains("new-example"));
        assertFalse(Files.exists(target.resolve(AUTHORITY)));
        assertEquals("sample-field=new-example", Files.readString(target.resolve(COLLECTOR)));
        assertEquals(Set.of(COLLECTOR), fileNames(target));
    }

    @Test
    void importedFileHasOwnerOnlyPosixPermissionsWhenSupported() throws IOException {
        Path legacy = Files.createDirectory(folder.resolve(OLD_DIRECTORY));
        Path target = Files.createDirectory(folder.resolve(PAPER_DIRECTORY));
        Files.writeString(legacy.resolve(AUTHORITY), SAMPLE_CONFIG);
        LegacyBridgeConfigMigration.migrate(target);
        if (Files.getFileStore(target).supportsFileAttributeView("posix")) {
            assertEquals(java.nio.file.attribute.PosixFilePermissions.fromString("rw-------"),
                    Files.getPosixFilePermissions(target.resolve(AUTHORITY)));
        }
    }

    @Test
    void rejectsOversizedFilesBeforeInstallingEitherConfig() throws IOException {
        Path legacy = Files.createDirectory(folder.resolve(OLD_DIRECTORY));
        Path target = Files.createDirectory(folder.resolve(PAPER_DIRECTORY));
        Files.writeString(legacy.resolve(AUTHORITY), SAMPLE_CONFIG);
        Files.write(legacy.resolve(COLLECTOR), new byte[65_537]);
        assertThrows(IllegalStateException.class, () -> LegacyBridgeConfigMigration.migrate(target));
        assertTrue(fileNames(target).isEmpty());
    }

    @Test
    void refusesSymlinkedLegacyConfigurationWhenFilesystemSupportsIt() throws IOException {
        Path legacy = Files.createDirectory(folder.resolve(OLD_DIRECTORY));
        Path target = Files.createDirectory(folder.resolve(PAPER_DIRECTORY));
        Path external = Files.writeString(folder.resolve("outside"), SAMPLE_CONFIG);
        try {
            Files.createSymbolicLink(legacy.resolve(AUTHORITY), external);
        } catch (UnsupportedOperationException | java.nio.file.FileSystemException
                 | SecurityException exception) {
            return;
        }
        assertThrows(IllegalStateException.class, () -> LegacyBridgeConfigMigration.migrate(target));
        assertTrue(fileNames(target).isEmpty());
    }

    private static Set<String> fileNames(Path directory) throws IOException {
        try (var entries = Files.list(directory)) {
            return entries.map(path -> path.getFileName().toString())
                    .collect(java.util.stream.Collectors.toSet());
        }
    }
}
