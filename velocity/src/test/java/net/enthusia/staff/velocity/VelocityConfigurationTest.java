package net.enthusia.staff.velocity;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class VelocityConfigurationTest {
    @Test
    void bundledDefaultsLoadAndResolveInsideDataDirectory(@TempDir Path directory) throws IOException {
        VelocityConfiguration configuration = VelocityConfiguration.load(directory);

        assertTrue(Files.isRegularFile(directory.resolve("config.properties")));
        assertEquals("ES_DATABASE_URL", configuration.jdbcUrlEnvironment());
        assertEquals(8, configuration.maximumPoolSize());
        assertTrue(configuration.failClosedWhileActive());
        assertEquals("VELOCITY", configuration.serverId());
        assertFalse(configuration.websiteApiEnabled());
        assertEquals(28766, configuration.websiteApiPort());
        assertEquals(
                directory.resolve("channel-server.p12").toAbsolutePath().normalize(),
                configuration.channelTlsKeyStorePath()
        );
        assertEquals(
                Map.of(
                        "HUB", "ES_CHANNEL_HUB_SECRET",
                        "SMP", "ES_CHANNEL_SMP_SECRET"
                ),
                configuration.backendSecretEnvironments()
        );
        assertEquals(
                Set.of("punishments", "reports", "logs-staffmode", "alerts"),
                configuration.discordWebhookEnvironments().keySet()
        );
        assertTrue(configuration.liteBansShadowScheduleEnabled());
        assertEquals(24, configuration.liteBansShadowIntervalHours());
        assertThrows(
                UnsupportedOperationException.class,
                () -> configuration.backendSecretEnvironments().put("OTHER", "SECRET")
        );
        assertThrows(
                UnsupportedOperationException.class,
                () -> configuration.discordWebhookEnvironments().put("other", "SECRET")
        );
    }

    @Test
    void optionalValuesUseSafeDefaults(@TempDir Path directory) throws IOException {
        Properties properties = copiedDefaults(directory);
        properties.remove("channel.tls-key-store");
        properties.remove("channel.tls-key-store-password-environment");
        properties.remove("litebans.shadow-schedule-enabled");
        properties.remove("litebans.shadow-interval-hours");
        store(directory, properties);

        VelocityConfiguration configuration = VelocityConfiguration.load(directory);

        assertEquals(
                directory.resolve("channel-server.p12").toAbsolutePath().normalize(),
                configuration.channelTlsKeyStorePath()
        );
        assertEquals(
                "ES_CHANNEL_TLS_KEYSTORE_PASSWORD",
                configuration.channelTlsKeyStorePasswordEnvironment()
        );
        assertTrue(configuration.liteBansShadowScheduleEnabled());
        assertEquals(24, configuration.liteBansShadowIntervalHours());
    }

    @Test
    void absoluteKeyStorePathIsAcceptedAndNormalized(@TempDir Path directory) throws IOException {
        Properties properties = copiedDefaults(directory);
        Path configured = directory.resolve("nested/../absolute.p12").toAbsolutePath();
        properties.setProperty("channel.tls-key-store", configured.toString());
        store(directory, properties);

        assertEquals(
                configured.normalize(),
                VelocityConfiguration.load(directory).channelTlsKeyStorePath()
        );
    }

    @Test
    void nestedRelativeKeyStorePathRemainsInsideTheDataDirectory(@TempDir Path directory) throws IOException {
        Properties properties = copiedDefaults(directory);
        properties.setProperty("channel.tls-key-store", "tls/../tls/channel.p12");
        store(directory, properties);

        assertEquals(
                directory.resolve("tls/channel.p12").toAbsolutePath().normalize(),
                VelocityConfiguration.load(directory).channelTlsKeyStorePath()
        );
    }

    @Test
    void booleanValuesAreCaseInsensitiveButStrict(@TempDir Path directory) throws IOException {
        Properties properties = copiedDefaults(directory);
        properties.setProperty("enforcement.fail-closed-while-active", "TrUe");
        properties.setProperty("website-api.enabled", "FaLsE");
        store(directory, properties);

        VelocityConfiguration configuration = VelocityConfiguration.load(directory);
        assertTrue(configuration.failClosedWhileActive());
        assertFalse(configuration.websiteApiEnabled());

        assertRejected(directory, properties, candidate ->
                candidate.setProperty("website-api.enabled", "yes"));
    }

    @Test
    void everyBoundedIntegerAcceptsItsLimitsAndRejectsAdjacentValues(@TempDir Path directory) throws IOException {
        Properties baseline = copiedDefaults(directory);
        List<Bound> bounds = List.of(
                new Bound("storage.maximum-pool-size", 1, 64),
                new Bound("storage.connection-timeout-millis", 250, 60_000),
                new Bound("website-api.port", 1, 65_535),
                new Bound("website-api.timestamp-skew-seconds", 30, 900),
                new Bound("website-api.maximum-body-bytes", 1_024, 1_048_576),
                new Bound("website-api.worker-threads", 1, 16),
                new Bound("website-api.queue-capacity", 8, 2_048),
                new Bound("channel.port", 1, 65_535),
                new Bound("discord.maximum-attempts", 1, 100),
                new Bound("discord.failure-threshold", 1, 100),
                new Bound("discord.circuit-open-seconds", 10, 86_400),
                new Bound("discord.request-timeout-millis", 500, 15_000),
                new Bound("litebans.maximum-pool-size", 1, 8),
                new Bound("litebans.connection-timeout-millis", 250, 60_000),
                new Bound("litebans.batch-size", 1, 5_000),
                new Bound("litebans.shadow-interval-hours", 1, 24)
        );

        for (Bound bound : bounds) {
            assertAccepted(directory, baseline, properties ->
                    properties.setProperty(bound.key(), Integer.toString(bound.minimum())));
            assertAccepted(directory, baseline, properties ->
                    properties.setProperty(bound.key(), Integer.toString(bound.maximum())));
            assertRejected(directory, baseline, properties ->
                    properties.setProperty(bound.key(), Integer.toString(bound.minimum() - 1)));
            assertRejected(directory, baseline, properties ->
                    properties.setProperty(bound.key(), Integer.toString(bound.maximum() + 1)));
        }
    }

    @Test
    void backendServerIdsEnforceTheDocumentedCharacterAndLengthBoundary(@TempDir Path directory)
            throws IOException {
        Properties baseline = copiedDefaults(directory);
        String maximumId = "A".repeat(64);

        assertAccepted(directory, baseline, properties ->
                properties.setProperty("channel.backend." + maximumId + ".secret-environment", "SECRET"));
        assertRejected(directory, baseline, properties ->
                properties.setProperty("channel.backend..secret-environment", "SECRET"));
        assertRejected(directory, baseline, properties ->
                properties.setProperty("channel.backend." + "A".repeat(65) + ".secret-environment", "SECRET"));
        assertRejected(directory, baseline, properties ->
                properties.setProperty("channel.backend.path/escape.secret-environment", "SECRET"));
    }

    @Test
    void malformedOrUnsafePropertiesAreRejected(@TempDir Path directory) throws IOException {
        Properties baseline = copiedDefaults(directory);

        assertRejected(directory, baseline, properties ->
                properties.setProperty("channel.backend.bad.id.secret-environment", "SECRET"));
        assertRejected(directory, baseline, properties ->
                properties.setProperty("channel.tls-key-store", "../outside.p12"));
        assertRejected(directory, baseline, properties ->
                properties.setProperty("enforcement.fail-closed-while-active", "sometimes"));
        assertRejected(directory, baseline, properties ->
                properties.setProperty("storage.maximum-pool-size", "0"));
        assertRejected(directory, baseline, properties ->
                properties.setProperty("website-api.port", "not-a-number"));
        assertRejected(directory, baseline, properties ->
                properties.setProperty("server.id", "   "));
    }

    private static Properties copiedDefaults(Path directory) throws IOException {
        VelocityConfiguration.load(directory);
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(directory.resolve("config.properties"))) {
            properties.load(input);
        }
        return properties;
    }

    private static void assertAccepted(
            Path directory,
            Properties baseline,
            Consumer<Properties> mutation
    ) throws IOException {
        Properties candidate = new Properties();
        candidate.putAll(baseline);
        mutation.accept(candidate);
        store(directory, candidate);

        assertDoesNotThrow(() -> VelocityConfiguration.load(directory));
    }

    private static void assertRejected(
            Path directory,
            Properties baseline,
            Consumer<Properties> mutation
    ) throws IOException {
        Properties candidate = new Properties();
        candidate.putAll(baseline);
        mutation.accept(candidate);
        store(directory, candidate);

        assertThrows(IllegalArgumentException.class, () -> VelocityConfiguration.load(directory));
    }

    private static void store(Path directory, Properties properties) throws IOException {
        try (OutputStream output = Files.newOutputStream(directory.resolve("config.properties"))) {
            properties.store(output, "test configuration");
        }
    }

    private record Bound(String key, int minimum, int maximum) {
    }
}
