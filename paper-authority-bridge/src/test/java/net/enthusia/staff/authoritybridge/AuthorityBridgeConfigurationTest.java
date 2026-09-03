package net.enthusia.staff.authoritybridge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AuthorityBridgeConfigurationTest {
    @TempDir
    Path tempDir;

    @Test
    void loadsPanelFileWithDefaultPort() throws IOException {
        String keyMaterial = testKeyMaterial();
        Files.writeString(
                tempDir.resolve(AuthorityBridgeConfiguration.FILE_NAME),
                authenticationEntry(keyMaterial) + "\n"
        );

        AuthorityBridgeConfiguration.Value configuration = AuthorityBridgeConfiguration.load(tempDir);

        assertEquals(keyMaterial, configuration.keyMaterial());
        assertEquals(8771, configuration.port());
    }

    @Test
    void acceptsExplicitBoundedPort() throws IOException {
        Files.writeString(
                tempDir.resolve(AuthorityBridgeConfiguration.FILE_NAME),
                String.join("\n", authenticationEntry(testKeyMaterial()), "authority.port=8772", "")
        );

        assertEquals(8772, AuthorityBridgeConfiguration.load(tempDir).port());
    }

    @Test
    void missingWeakAndUnknownConfigurationFailsClosed() throws IOException {
        assertThrows(IllegalArgumentException.class, () -> AuthorityBridgeConfiguration.load(tempDir));

        Path file = tempDir.resolve(AuthorityBridgeConfiguration.FILE_NAME);
        Files.writeString(file, authenticationEntry("short") + "\n");
        assertThrows(IllegalArgumentException.class, () -> AuthorityBridgeConfiguration.load(tempDir));

        Files.writeString(
                file,
                String.join("\n", authenticationEntry(testKeyMaterial()), "unsupported=value", "")
        );
        assertThrows(IllegalArgumentException.class, () -> AuthorityBridgeConfiguration.load(tempDir));
    }

    @Test
    void invalidPortsFailClosed() throws IOException {
        Path file = tempDir.resolve(AuthorityBridgeConfiguration.FILE_NAME);
        Files.writeString(
                file,
                String.join("\n", authenticationEntry(testKeyMaterial()), "authority.port=0", "")
        );
        assertThrows(IllegalArgumentException.class, () -> AuthorityBridgeConfiguration.load(tempDir));

        Files.writeString(
                file,
                String.join("\n", authenticationEntry(testKeyMaterial()), "authority.port=invalid", "")
        );
        assertThrows(IllegalArgumentException.class, () -> AuthorityBridgeConfiguration.load(tempDir));
    }

    private static String authenticationEntry(String value) {
        return String.join("=", "authority." + "secret", value);
    }

    private static String testKeyMaterial() {
        return UUID.randomUUID().toString() + UUID.randomUUID();
    }
}
