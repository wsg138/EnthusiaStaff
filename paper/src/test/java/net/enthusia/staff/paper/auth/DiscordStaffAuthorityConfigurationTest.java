package net.enthusia.staff.paper.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DiscordStaffAuthorityConfigurationTest {
    private static final String SECRET = "a".repeat(40);

    @TempDir
    Path tempDir;

    @Test
    void absentEnvironmentAndFileLeaveAuthorityDisabled() {
        assertTrue(DiscordStaffAuthorityConfiguration.fromSources(tempDir, Map.of()).isEmpty());
    }

    @Test
    void panelFileProvidesSecretAndLoopbackPort() throws IOException {
        Path file = tempDir.resolve(DiscordStaffAuthorityConfiguration.FILE_NAME);
        Files.writeString(file, String.join("\n",
                "authority.url=http://127.0.0.1:8771/v1/staff-rank",
                "authority.secret=" + SECRET,
                ""));

        DiscordStaffAuthorityConfiguration.Value value =
                DiscordStaffAuthorityConfiguration.fromSources(tempDir, Map.of()).orElseThrow();

        assertEquals(SECRET, value.secret());
        assertEquals(8771, value.port());
    }

    @Test
    void fileRejectsMissingWeakAndNonLoopbackAuthorityConfiguration() throws IOException {
        Path file = tempDir.resolve(DiscordStaffAuthorityConfiguration.FILE_NAME);
        Files.writeString(file, "authority.url=http://127.0.0.1:8771/v1/staff-rank\n");
        assertThrows(IllegalArgumentException.class,
                () -> DiscordStaffAuthorityConfiguration.fromSources(tempDir, Map.of()));

        Files.writeString(file, String.join("\n",
                "authority.url=http://127.0.0.1:8771/v1/staff-rank",
                "authority.secret=short",
                ""));
        assertThrows(IllegalArgumentException.class,
                () -> DiscordStaffAuthorityConfiguration.fromSources(tempDir, Map.of()));

        Files.writeString(file, String.join("\n",
                "authority.url=http://10.0.0.2:8771/v1/staff-rank",
                "authority.secret=" + SECRET,
                ""));
        assertThrows(IllegalArgumentException.class,
                () -> DiscordStaffAuthorityConfiguration.fromSources(tempDir, Map.of()));
    }

    @Test
    void existingEnvironmentConfigurationRemainsSupported() {
        DiscordStaffAuthorityConfiguration.Value value = DiscordStaffAuthorityConfiguration.fromSources(
                tempDir,
                Map.of(
                        DiscordStaffAuthorityEndpoint.CREDENTIAL_ENV, SECRET,
                        DiscordStaffAuthorityEndpoint.PORT_ENV, "8772"
                )).orElseThrow();

        assertEquals(SECRET, value.secret());
        assertEquals(8772, value.port());
    }

    @Test
    void partialEnvironmentConfigurationFailsClosedInsteadOfFallingBackToFile() throws IOException {
        Files.writeString(tempDir.resolve(DiscordStaffAuthorityConfiguration.FILE_NAME), String.join("\n",
                "authority.url=http://127.0.0.1:8771/v1/staff-rank",
                "authority.secret=" + SECRET,
                ""));

        assertThrows(IllegalArgumentException.class, () -> DiscordStaffAuthorityConfiguration.fromSources(
                tempDir,
                Map.of(DiscordStaffAuthorityEndpoint.PORT_ENV, "8772")));
    }
}
