package net.enthusia.staff.velocity;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Properties;

record VerificationProbeConfiguration(
        Optional<URI> staffBotReadyUrl,
        Optional<URI> websiteReadyUrl,
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
                uri(properties.getProperty("staff-bot.ready-url")),
                uri(properties.getProperty("website.ready-url")),
                timeout(properties.getProperty("timeout-millis"))
        );
    }

    private static Optional<URI> uri(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        URI parsed = URI.create(raw.trim());
        String scheme = parsed.getScheme();
        if (parsed.getUserInfo() != null || parsed.getFragment() != null
                || parsed.getHost() == null || parsed.getHost().isBlank()
                || !("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))) {
            throw new IllegalArgumentException("Readiness URLs must be plain HTTP(S) endpoints without credentials or fragments");
        }
        return Optional.of(parsed);
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
