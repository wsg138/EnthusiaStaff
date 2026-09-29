package net.enthusia.staff.velocity;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Properties;

record VerificationProbeConfiguration(
        Optional<PrivateReadinessEndpoint> staffBotReadyUrl,
        Optional<PrivateReadinessEndpoint> websiteReadyUrl,
        int timeoutMillis
) {
    private static final int DEFAULT_TIMEOUT_MILLIS = 1_500;

    static VerificationProbeConfiguration load(Path dataDirectory) {
        Path file = dataDirectory.resolve("verification.properties");
        if (Files.notExists(file)) {
            return new VerificationProbeConfiguration(Optional.empty(), Optional.empty(), DEFAULT_TIMEOUT_MILLIS);
        }
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(file)) {
            properties.load(input);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to read verification.properties", exception);
        }
        return new VerificationProbeConfiguration(
                PrivateReadinessEndpoint.parseOptional(properties.getProperty("staff-bot.ready-url")),
                PrivateReadinessEndpoint.parseOptional(properties.getProperty("website.ready-url")),
                timeout(properties.getProperty("timeout-millis"))
        );
    }

    private static int timeout(String raw) {
        if (raw == null || raw.isBlank()) {
            return DEFAULT_TIMEOUT_MILLIS;
        }
        try {
            int parsed = Integer.parseInt(raw.trim());
            if (parsed < 250 || parsed > 5_000) {
                throw new IllegalArgumentException("timeout-millis must be between 250 and 5000");
            }
            return parsed;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("timeout-millis must be an integer", exception);
        }
    }
}
