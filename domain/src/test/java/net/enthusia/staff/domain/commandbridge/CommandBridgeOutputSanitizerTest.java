package net.enthusia.staff.domain.commandbridge;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CommandBridgeOutputSanitizerTest {
    private final CommandBridgeOutputSanitizer sanitizer = new CommandBridgeOutputSanitizer();

    @Test
    void redactsSecretsPrivatePathsAndSensitiveEvidence() {
        CommandBridgeOutputSanitizer.Sanitized result = sanitizer.sanitize(
                "Authorization: Bearer abc123\npassword=hunter2\n/home/server/private.yml\nprivate message evidence body"
        );

        assertTrue(result.redacted());
        assertFalse(result.text().contains("abc123"));
        assertFalse(result.text().contains("hunter2"));
        assertFalse(result.text().contains("/home/server/private.yml"));
        assertTrue(result.text().contains("[redacted-sensitive-output]"));
    }

    @Test
    void boundsLinesAndCharacters() {
        String line = "x".repeat(500);
        String output = String.join("\n", java.util.Collections.nCopies(30, line));
        CommandBridgeOutputSanitizer.Sanitized result = sanitizer.sanitize(output);

        assertTrue(result.truncated());
        assertTrue(result.text().length() <= CommandBridgeOutputSanitizer.MAX_OUTPUT_CHARACTERS);
        assertTrue(result.text().lines().count() <= CommandBridgeOutputSanitizer.MAX_OUTPUT_LINES);
    }
}
