package net.enthusia.staff.common.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PrivateRuntimeSecretsTest {
    private static final String CHANNEL_ENV_KEY = "ES_CHANNEL_ENV_KEY";

    @Test
    void environmentTakesPrecedenceWithoutReadingFile(@TempDir Path directory) {
        assertEquals("environment-value", PrivateRuntimeSecrets.required(directory, CHANNEL_ENV_KEY, key -> "environment-value"));
    }

    @Test
    void absentEnvironmentUsesOnlyTheNamedPrivateEntry(@TempDir Path directory) throws IOException {
        Files.writeString(directory.resolve("secrets.properties"), "ES_CHANNEL_ENV_KEY=private-value\nES_OTHER_SECRET=other-value\n");
        assertEquals("private-value", PrivateRuntimeSecrets.required(directory, CHANNEL_ENV_KEY, key -> null));
        assertThrows(IllegalStateException.class, () -> PrivateRuntimeSecrets.required(directory, "ES_MISSING_SECRET", key -> null));
    }

    @Test
    void missingFileAndBlankEntriesFailClosed(@TempDir Path directory) throws IOException {
        assertThrows(IllegalStateException.class, () -> PrivateRuntimeSecrets.required(directory, CHANNEL_ENV_KEY, key -> null));
        Files.writeString(directory.resolve("secrets.properties"), "ES_CHANNEL_ENV_KEY=\n");
        assertThrows(IllegalStateException.class, () -> PrivateRuntimeSecrets.required(directory, CHANNEL_ENV_KEY, key -> null));
    }

    @Test
    void oversizedFileAndInvalidNameAreRejected(@TempDir Path directory) throws IOException {
        Files.writeString(directory.resolve("secrets.properties"), "x".repeat(16_385));
        assertThrows(IllegalStateException.class, () -> PrivateRuntimeSecrets.required(directory, CHANNEL_ENV_KEY, key -> null));
        assertThrows(IllegalArgumentException.class, () -> PrivateRuntimeSecrets.required(directory, "../secret", key -> null));
    }
}
