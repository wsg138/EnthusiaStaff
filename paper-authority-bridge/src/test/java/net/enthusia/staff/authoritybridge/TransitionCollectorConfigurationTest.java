package net.enthusia.staff.authoritybridge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TransitionCollectorConfigurationTest {
    @TempDir
    Path tempDir;

    @Test
    void missingFileLeavesCollectorDisabled() {
        assertTrue(TransitionCollectorConfiguration.loadIfPresent(tempDir).isEmpty());
    }

    @Test
    void completeFileLoadsWithBoundedDefaultsAndRedactedDescription() throws IOException {
        Files.writeString(tempDir.resolve(TransitionCollectorConfiguration.FILE_NAME), baseConfiguration());

        TransitionCollectorConfiguration.Value value =
                TransitionCollectorConfiguration.loadIfPresent(tempDir).orElseThrow();

        assertEquals("SMP", value.serverId());
        assertEquals(60L, value.interval().toSeconds());
        assertEquals(2, value.database().maximumPoolSize());
        assertFalse(value.toString().contains("collector-password"));
    }

    @Test
    void unknownMissingAndOutOfRangePropertiesFailClosed() throws IOException {
        Path file = tempDir.resolve(TransitionCollectorConfiguration.FILE_NAME);
        Files.writeString(file, baseConfiguration() + "unsupported=value\n");
        assertThrows(IllegalArgumentException.class,
                () -> TransitionCollectorConfiguration.loadIfPresent(tempDir));

        Files.writeString(file, "db.jdbc-url=jdbc:mariadb://db/enthusia\n");
        assertThrows(IllegalArgumentException.class,
                () -> TransitionCollectorConfiguration.loadIfPresent(tempDir));

        Files.writeString(file, baseConfiguration() + "collector.interval-seconds=1\n");
        assertThrows(IllegalArgumentException.class,
                () -> TransitionCollectorConfiguration.loadIfPresent(tempDir));
    }

    private static String baseConfiguration() {
        return String.join("\n",
                "db.jdbc-url=jdbc:mariadb://db.example:3306/enthusia",
                "db.username=collector",
                "db.credential=collector-password",
                "");
    }
}
