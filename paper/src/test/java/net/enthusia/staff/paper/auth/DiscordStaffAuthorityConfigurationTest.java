package net.enthusia.staff.paper.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DiscordStaffAuthorityConfigurationTest {
    private static final String TEST_AUTHORITY_VALUE = randomAuthorityValue();
    private static final String LOOPBACK_URL = "authority.url=http://127.0.0.1:8771/v1/staff-rank";
    private static final String SECRET_PROPERTY = "authority.secret=";

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
                LOOPBACK_URL,
                SECRET_PROPERTY + TEST_AUTHORITY_VALUE,
                ""));

        DiscordStaffAuthorityConfiguration.Value value =
                DiscordStaffAuthorityConfiguration.fromSources(tempDir, Map.of()).orElseThrow();

        assertEquals(TEST_AUTHORITY_VALUE, value.secret());
        assertEquals("127.0.0.1", value.bindHost());
        assertEquals(8771, value.port());
        assertFalse(value.privateSplit());
    }

    @Test
    void panelFileCanOptIntoAuthenticatedBloomPrivateSplitBinding() throws IOException {
        Path file = tempDir.resolve(DiscordStaffAuthorityConfiguration.FILE_NAME);
        Files.writeString(file, String.join("\n",
                "authority.bind=bloom-private-split",
                "authority.port=8771",
                SECRET_PROPERTY + TEST_AUTHORITY_VALUE,
                ""));

        DiscordStaffAuthorityConfiguration.Value value =
                DiscordStaffAuthorityConfiguration.fromSources(tempDir, Map.of()).orElseThrow();

        assertEquals("0.0.0.0", value.bindHost());
        assertEquals(8771, value.port());
        assertTrue(value.privateSplit());
    }

    @Test
    void fileRejectsMissingWeakUnknownAndAmbiguousConfiguration() throws IOException {
        Path file = tempDir.resolve(DiscordStaffAuthorityConfiguration.FILE_NAME);
        Files.writeString(file, LOOPBACK_URL + "\n");
        assertThrows(IllegalArgumentException.class,
                () -> DiscordStaffAuthorityConfiguration.fromSources(tempDir, Map.of()));

        Files.writeString(file, String.join("\n",
                LOOPBACK_URL,
                SECRET_PROPERTY + "short",
                ""));
        assertThrows(IllegalArgumentException.class,
                () -> DiscordStaffAuthorityConfiguration.fromSources(tempDir, Map.of()));

        Files.writeString(file, String.join("\n",
                "authority.bind=bloom-private-split",
                "authority.port=8771",
                LOOPBACK_URL,
                SECRET_PROPERTY + TEST_AUTHORITY_VALUE,
                ""));
        assertThrows(IllegalArgumentException.class,
                () -> DiscordStaffAuthorityConfiguration.fromSources(tempDir, Map.of()));

        Files.writeString(file, String.join("\n",
                LOOPBACK_URL,
                SECRET_PROPERTY + TEST_AUTHORITY_VALUE,
                "unsupported=value",
                ""));
        assertThrows(IllegalArgumentException.class,
                () -> DiscordStaffAuthorityConfiguration.fromSources(tempDir, Map.of()));
    }

    @Test
    void fileRejectsNonLoopbackLegacyUrlAndUnsupportedPrivateBind() throws IOException {
        Path file = tempDir.resolve(DiscordStaffAuthorityConfiguration.FILE_NAME);
        Files.writeString(file, String.join("\n",
                "authority.url=http://10.0.0.2:8771/v1/staff-rank",
                SECRET_PROPERTY + TEST_AUTHORITY_VALUE,
                ""));
        assertThrows(IllegalArgumentException.class,
                () -> DiscordStaffAuthorityConfiguration.fromSources(tempDir, Map.of()));

        Files.writeString(file, String.join("\n",
                "authority.bind=public",
                "authority.port=8771",
                SECRET_PROPERTY + TEST_AUTHORITY_VALUE,
                ""));
        assertThrows(IllegalArgumentException.class,
                () -> DiscordStaffAuthorityConfiguration.fromSources(tempDir, Map.of()));
    }

    @Test
    void existingEnvironmentConfigurationRemainsLoopbackOnly() {
        DiscordStaffAuthorityConfiguration.Value value = DiscordStaffAuthorityConfiguration.fromSources(
                tempDir,
                Map.of(
                        DiscordStaffAuthorityEndpoint.CREDENTIAL_ENV, TEST_AUTHORITY_VALUE,
                        DiscordStaffAuthorityEndpoint.PORT_ENV, "8772"
                )).orElseThrow();

        assertEquals(TEST_AUTHORITY_VALUE, value.secret());
        assertEquals("127.0.0.1", value.bindHost());
        assertEquals(8772, value.port());
        assertFalse(value.privateSplit());
    }

    @Test
    void partialEnvironmentConfigurationFailsClosedInsteadOfFallingBackToFile() throws IOException {
        Files.writeString(tempDir.resolve(DiscordStaffAuthorityConfiguration.FILE_NAME), String.join("\n",
                LOOPBACK_URL,
                SECRET_PROPERTY + TEST_AUTHORITY_VALUE,
                ""));

        assertThrows(IllegalArgumentException.class, () -> DiscordStaffAuthorityConfiguration.fromSources(
                tempDir,
                Map.of(DiscordStaffAuthorityEndpoint.PORT_ENV, "8772")));
    }

    private static String randomAuthorityValue() {
        return UUID.randomUUID().toString() + UUID.randomUUID();
    }
}
