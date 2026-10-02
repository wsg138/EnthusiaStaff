package net.enthusia.staff.velocity;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class VelocityChannelSecretsWiringTest {
    private static final Path SOURCE = Path.of(
            "src/main/java/net/enthusia/staff/velocity/EnthusiaStaffVelocityPlugin.java"
    );

    @Test
    void deployedSecretFallbackRemainsWiredAndTlsPasswordIsWiped() throws IOException {
        String source = Files.readString(SOURCE, StandardCharsets.UTF_8);

        assertTrue(source.contains(
                "PrivateRuntimeSecrets.required(dataDirectory, environment, System::getenv)"
        ));
        assertTrue(source.contains("Arrays.fill(password, '\\0')"));
        assertFalse(source.contains("System.getenv(loaded.channelProxySecretEnvironment())"));
    }
}
