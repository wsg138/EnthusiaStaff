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
    void validFileLoadsPrivateBoundedHttpEndpoints() throws Exception {
        Files.writeString(directory.resolve("verification.properties"), """
                staff-bot.ready-url=http://10.0.0.20:8765/ready
                website.ready-url=https://192.168.10.50:8443/health
                timeout-millis=900
                """);

        VerificationProbeConfiguration configuration = VerificationProbeConfiguration.load(directory);

        assertEquals("10.0.0.20", configuration.staffBotReadyUrl().orElseThrow().uri().getHost());
        assertEquals("192.168.10.50", configuration.websiteReadyUrl().orElseThrow().uri().getHost());
        assertEquals(900, configuration.timeoutMillis());
    }

    @Test
    void credentialsInReadinessUrlAreRejected() throws Exception {
        Files.writeString(directory.resolve("verification.properties"),
                "staff-bot.ready-url=http://user:secret@127.0.0.1:8765/ready\n");

        assertThrows(IllegalArgumentException.class, () -> VerificationProbeConfiguration.load(directory));
    }

    @Test
    void hostnamesAndPublicAddressesAreRejected() throws Exception {
        Files.writeString(directory.resolve("verification.properties"),
                "staff-bot.ready-url=https://example.com/ready\n");
        assertThrows(IllegalArgumentException.class, () -> VerificationProbeConfiguration.load(directory));

        Files.writeString(directory.resolve("verification.properties"),
                "staff-bot.ready-url=https://8.8.8.8/ready\n");
        assertThrows(IllegalArgumentException.class, () -> VerificationProbeConfiguration.load(directory));
    }

    @Test
    void linkLocalMetadataAddressIsRejected() throws Exception {
        Files.writeString(directory.resolve("verification.properties"),
                "staff-bot.ready-url=http://169.254.169.254/latest/meta-data\n");

        assertThrows(IllegalArgumentException.class, () -> VerificationProbeConfiguration.load(directory));
    }

    @Test
    void timeoutOutsideSafetyBoundsIsRejected() throws Exception {
        Files.writeString(directory.resolve("verification.properties"), "timeout-millis=60000\n");

        assertThrows(IllegalArgumentException.class, () -> VerificationProbeConfiguration.load(directory));
    }
}
