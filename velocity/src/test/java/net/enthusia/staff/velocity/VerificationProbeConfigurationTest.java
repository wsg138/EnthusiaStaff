package net.enthusia.staff.velocity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class VerificationProbeConfigurationTest {
    @TempDir
    Path directory;

    @Test
    void missingFileKeepsExternalChecksExplicitlyUnconfigured() {
        VerificationProbeConfiguration configuration = VerificationProbeConfiguration.load(directory);

        assertTrue(configuration.staffBotReadyUrl().isEmpty());
        assertTrue(configuration.websiteReadyUrl().isEmpty());
        assertEquals(1_500, configuration.timeoutMillis());
    }

    @Test
    void validFileLoadsBoundedHttpEndpoints() throws Exception {
        Files.writeString(directory.resolve("verification.properties"), """
                staff-bot.ready-url=http://10.0.0.20:8765/ready
                website.ready-url=https://staff-staging.example.test/health
                timeout-millis=900
                """);

        VerificationProbeConfiguration configuration = VerificationProbeConfiguration.load(directory);

        assertEquals("10.0.0.20", configuration.staffBotReadyUrl().orElseThrow().getHost());
        assertEquals("staff-staging.example.test", configuration.websiteReadyUrl().orElseThrow().getHost());
        assertEquals(900, configuration.timeoutMillis());
    }

    @Test
    void credentialsInReadinessUrlAreRejected() throws Exception {
        Files.writeString(directory.resolve("verification.properties"),
                "staff-bot.ready-url=http://user:secret@127.0.0.1:8765/ready\n");

        assertThrows(IllegalArgumentException.class, () -> VerificationProbeConfiguration.load(directory));
    }

    @Test
    void timeoutOutsideSafetyBoundsIsRejected() throws Exception {
        Files.writeString(directory.resolve("verification.properties"), "timeout-millis=60000\n");

        assertThrows(IllegalArgumentException.class, () -> VerificationProbeConfiguration.load(directory));
    }
}
