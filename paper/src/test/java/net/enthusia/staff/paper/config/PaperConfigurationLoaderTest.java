package net.enthusia.staff.paper.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PaperConfigurationLoaderTest {
    @TempDir
    Path temporaryDirectory;

    private final PaperConfigurationLoader loader = new PaperConfigurationLoader();

    @Test
    void loadsCompleteValidatedConfiguration() throws IOException {
        PaperConfigurationSnapshot snapshot = load(validConfiguration());

        assertEquals(1, snapshot.version());
        assertEquals(6, snapshot.restartRequired().workerThreads());
        assertEquals(300, snapshot.restartRequired().workerQueueCapacity());
        assertEquals("SMP", snapshot.restartRequired().networkServerId());
        assertTrue(snapshot.punishmentRequestAlerts().enabled());
        assertEquals(Duration.ofSeconds(10), snapshot.punishmentRequestAlerts().pollInterval());
        assertEquals(Duration.ofSeconds(2), snapshot.punishmentRequestAlerts().joinDelay());
        assertEquals(Duration.ofDays(30), snapshot.punishmentRequestAlerts().retentionDuration());
    }

    @Test
    void missingOptionalAlertSectionsUseDocumentedDefaults() throws IOException {
        String configuration = validConfiguration().replace(
                alertConfiguration(),
                "punishment-request-alerts:\n  enabled: false\n"
        );

        PaperConfigurationSnapshot snapshot = load(configuration);

        assertEquals(PunishmentRequestAlertDefaults.disabled(), snapshot.punishmentRequestAlerts());
    }

    @Test
    void rejectsUnsupportedVersionWrongNodeTypesAndUnknownAlertKeysTogether() throws IOException {
        String configuration = validConfiguration()
                .replace("config-version: 1", "config-version: 2")
                .replace("  polling:\n", "  polling: []\n  unknown-worker-key: true\n  ignored-polling:\n")
                .replace("  delivery:\n", "  delivery: false\n  ignored-delivery:\n");

        PaperConfigurationValidationException exception = assertThrows(
                PaperConfigurationValidationException.class,
                () -> load(configuration)
        );

        assertContains(exception.errors(), "config-version must be 1");
        assertContains(exception.errors(), "punishment-request-alerts.polling must be a mapping section");
        assertContains(exception.errors(), "punishment-request-alerts.delivery must be a mapping section");
        assertContains(exception.errors(), "punishment-request-alerts contains unknown key unknown-worker-key");
    }

    @Test
    void reportsMultipleScalarAndCrossFieldFailuresWithoutPublishingValues() throws IOException {
        String secretValue = "do-not-leak-this-secret";
        String configuration = validConfiguration()
                .replace("  recipient-limit: 100", "  recipient-limit: 500")
                .replace("  interval-millis: 10000", "  interval-millis: 10")
                .replace("  total-claim-limit: 100", "  total-claim-limit: 10")
                .replace("  presentation-limit: 100", "  presentation-limit: 11")
                .replace("  lease-seconds: 45", "  lease-seconds: 1")
                .replace("  retry-base-seconds: 5", "  retry-base-seconds: 30")
                .replace("  retry-maximum-seconds: 300", "  retry-maximum-seconds: 10")
                .replace("  backend-secret-environment: ES_CHANNEL_SMP_SECRET",
                        "  backend-secret-environment: " + secretValue);

        PaperConfigurationValidationException exception = assertThrows(
                PaperConfigurationValidationException.class,
                () -> load(configuration)
        );

        assertTrue(exception.errors().size() >= 5);
        assertContains(exception.errors(),
                "punishment-request-alerts.polling.interval-millis must be between 250 and 300000");
        assertContains(exception.errors(),
                "punishment-request-alerts.polling.presentation-limit must not exceed "
                        + "punishment-request-alerts.polling.total-claim-limit");
        assertContains(exception.errors(),
                "punishment-request-alerts.delivery.retry-maximum-seconds must not be shorter than "
                        + "punishment-request-alerts.delivery.retry-base-seconds");
        assertContains(exception.errors(),
                "punishment-request-alerts.polling.recipient-limit must not exceed "
                        + "workers.threads plus workers.queue-capacity");
        assertFalse(exception.getMessage().contains(secretValue));
        assertTrue(exception.errors().stream().noneMatch(error -> error.contains(secretValue)));
    }

    @Test
    void rejectsZeroNegativeExcessiveAndNonScalarValues() throws IOException {
        String configuration = validConfiguration()
                .replace("  maximum-attempts: 6", "  maximum-attempts: 0")
                .replace("  join-delay-ticks: 40", "  join-delay-ticks: -1")
                .replace("      batch: 100", "      batch: 101")
                .replace("      days: 30", "      days: [30]");

        PaperConfigurationValidationException exception = assertThrows(
                PaperConfigurationValidationException.class,
                () -> load(configuration)
        );

        assertContains(exception.errors(),
                "punishment-request-alerts.delivery.maximum-attempts must be between 1 and 100");
        assertContains(exception.errors(),
                "punishment-request-alerts.delivery.join-delay-ticks must be between 0 and 1000000");
        assertTrue(exception.errors().stream()
                .anyMatch(error -> error.endsWith("batch must be between 1 and 100")));
        assertContains(exception.errors(),
                "punishment-request-alerts.maintenance.retention.days must be an integer");
    }

    @Test
    void restartSignatureReportsEveryChangedStartupOnlyPath() throws IOException {
        RestartRequiredConfiguration active = load(validConfiguration()).restartRequired();
        RestartRequiredConfiguration changed = load(validConfiguration()
                .replace("maximum-pool-size: 8", "maximum-pool-size: 9")
                .replace("queue-capacity: 300", "queue-capacity: 301")
                .replace("server-id: SMP", "server-id: HUB")
                .replace("scope-id: SMP", "scope-id: HUB")
                .replace("host: 127.0.0.1", "host: 127.0.0.2")
        ).restartRequired();

        assertEquals(List.of(
                "storage.maximum-pool-size",
                "workers.queue-capacity",
                "network.server-id",
                "inventory.scope-id",
                "channel.host"
        ), active.differencePaths(changed));
    }

    @Test
    void invalidYamlIsSanitized() throws IOException {
        Path file = temporaryDirectory.resolve("config.yml");
        Files.writeString(file, "storage: [\n");

        PaperConfigurationValidationException exception = assertThrows(
                PaperConfigurationValidationException.class,
                () -> loader.load(file, temporaryDirectory)
        );

        assertTrue(exception.getMessage().startsWith("Unable to read config.yml"));
        assertFalse(exception.getMessage().contains("storage: ["));
    }

    private PaperConfigurationSnapshot load(String configuration) throws IOException {
        Path file = temporaryDirectory.resolve("config-" + System.nanoTime() + ".yml");
        Files.writeString(file, configuration);
        return loader.load(file, temporaryDirectory);
    }

    private static void assertContains(List<String> errors, String expected) {
        assertTrue(errors.contains(expected), () -> "Expected error '" + expected + "' in " + errors);
    }

    private static String validConfiguration() {
        return """
                config-version: 1
                storage:
                  jdbc-url-environment: ES_DATABASE_URL
                  username-environment: ES_DATABASE_USER
                  password-environment: ES_DATABASE_PASSWORD
                  maximum-pool-size: 8
                  connection-timeout-millis: 5000
                workers:
                  threads: 6
                  queue-capacity: 300
                network:
                  server-id: SMP
                inventory:
                  scope-id: SMP
                channel:
                  enabled: false
                  host: 127.0.0.1
                  port: 28765
                  proxy-id: VELOCITY
                  backend-secret-environment: ES_CHANNEL_SMP_SECRET
                  proxy-secret-environment: ES_CHANNEL_VELOCITY_SECRET
                  tls:
                    trust-store: channel-trust.p12
                    trust-store-password-environment: ES_CHANNEL_TLS_TRUSTSTORE_PASSWORD
                """ + alertConfiguration();
    }

    private static String alertConfiguration() {
        return """
                punishment-request-alerts:
                  enabled: true
                  polling:
                    interval-millis: 10000
                    recipient-limit: 100
                    direct-batch: 4
                    reviewer-batch: 4
                    operational-batch: 2
                    total-claim-limit: 100
                    presentation-limit: 100
                  delivery:
                    lease-seconds: 45
                    maximum-attempts: 6
                    retry-base-seconds: 5
                    retry-maximum-seconds: 300
                    join-delay-ticks: 40
                  maintenance:
                    request-expiration:
                      interval-seconds: 60
                      batch: 100
                    intent-expiration:
                      interval-seconds: 60
                      batch: 100
                    lease-reclaim:
                      interval-seconds: 30
                      batch: 100
                    retention:
                      interval-minutes: 60
                      batch: 100
                      days: 30
                """;
    }

    private static final class PunishmentRequestAlertDefaults {
        private static net.enthusia.staff.paper.alert.PunishmentRequestAlertWorkerSettings disabled() {
            return net.enthusia.staff.paper.alert.PunishmentRequestAlertWorkerSettings.safeDefaults(false);
        }
    }
}
