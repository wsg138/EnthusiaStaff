package net.enthusia.staff.paper.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import net.enthusia.staff.paper.alert.PunishmentRequestAlertWorkerSettings;
import net.enthusia.staff.paper.tester.CheatTesterSettings;

public final class PaperConfigurationLoader {
    public static final int SUPPORTED_VERSION = 1;

    private final ObjectMapper yaml = new ObjectMapper(new YAMLFactory());

    public PaperConfigurationSnapshot load(Path file, Path dataDirectory) {
        if (file == null || dataDirectory == null) {
            throw new IllegalArgumentException("configuration file and data directory must be present");
        }
        try (Reader reader = Files.newBufferedReader(file)) {
            return parse(yaml.readTree(reader), dataDirectory.toAbsolutePath().normalize());
        } catch (IOException exception) {
            throw new PaperConfigurationValidationException(
                    "Unable to read " + file.getFileName(),
                    exception
            );
        }
    }

    PaperConfigurationSnapshot parse(JsonNode root, Path dataDirectory) {
        List<String> errors = new ArrayList<>();
        if (root == null || !root.isObject()) {
            throw new PaperConfigurationValidationException(List.of("root must be a mapping section"));
        }

        int version = ConfigurationNodes.integer(
                root,
                "config-version",
                "config-version",
                SUPPORTED_VERSION,
                errors
        );
        if (version != SUPPORTED_VERSION) {
            errors.add("config-version must be " + SUPPORTED_VERSION);
        }

        JsonNode storage = ConfigurationNodes.requiredMapping(root, "storage", "storage", errors);
        JsonNode workers = ConfigurationNodes.requiredMapping(root, "workers", "workers", errors);
        JsonNode network = ConfigurationNodes.requiredMapping(root, "network", "network", errors);
        JsonNode inventory = ConfigurationNodes.requiredMapping(root, "inventory", "inventory", errors);
        JsonNode channel = ConfigurationNodes.requiredMapping(root, "channel", "channel", errors);
        JsonNode tls = ConfigurationNodes.requiredMapping(channel, "tls", "channel.tls", errors);

        String serverId = ConfigurationNodes.text(network, "server-id", "network.server-id", "SMP", errors);
        RestartRequiredConfiguration restart = new RestartRequiredConfiguration(
                ConfigurationNodes.text(storage, "jdbc-url-environment", "storage.jdbc-url-environment", "ES_DATABASE_URL", errors),
                ConfigurationNodes.text(storage, "username-environment", "storage.username-environment", "ES_DATABASE_USER", errors),
                ConfigurationNodes.text(storage, "password-environment", "storage.password-environment", "ES_DATABASE_PASSWORD", errors),
                ConfigurationNodes.boundedInteger(storage, "maximum-pool-size", "storage.maximum-pool-size", 8, 1, 128, errors),
                ConfigurationNodes.boundedLong(storage, "connection-timeout-millis", "storage.connection-timeout-millis", 5_000, 250, 120_000, errors),
                ConfigurationNodes.boundedInteger(workers, "threads", "workers.threads", 4, 1, 64, errors),
                ConfigurationNodes.boundedInteger(workers, "queue-capacity", "workers.queue-capacity", 256, 1, 100_000, errors),
                serverId,
                ConfigurationNodes.text(inventory, "scope-id", "inventory.scope-id", serverId, errors),
                ConfigurationNodes.bool(channel, "enabled", "channel.enabled", false, errors),
                ConfigurationNodes.text(channel, "host", "channel.host", "127.0.0.1", errors),
                ConfigurationNodes.boundedInteger(channel, "port", "channel.port", 28_765, 1, 65_535, errors),
                ConfigurationNodes.text(channel, "proxy-id", "channel.proxy-id", "VELOCITY", errors),
                ConfigurationNodes.text(channel, "backend-secret-environment", "channel.backend-secret-environment", "ES_CHANNEL_BACKEND_SECRET", errors),
                ConfigurationNodes.text(channel, "proxy-secret-environment", "channel.proxy-secret-environment", "ES_CHANNEL_PROXY_SECRET", errors),
                ConfigurationNodes.text(tls, "trust-store", "channel.tls.trust-store", "channel-trust.p12", errors),
                ConfigurationNodes.text(tls, "trust-store-password-environment", "channel.tls.trust-store-password-environment", "ES_CHANNEL_TLS_TRUSTSTORE_PASSWORD", errors),
                parseCheatTesterSettings(root, errors)
        );
        validateChannel(restart, dataDirectory, errors);

        PunishmentRequestAlertWorkerSettings alerts =
                new PunishmentRequestAlertConfigurationParser().parse(
                        root.get("punishment-request-alerts"),
                        restart,
                        errors
                );
        ModerationFeatureSettings moderationFeatures =
                new ModerationFeatureConfigurationParser().parse(root, errors);
        if (!errors.isEmpty()) {
            throw new PaperConfigurationValidationException(errors);
        }
        return new PaperConfigurationSnapshot(version, restart, alerts, moderationFeatures);
    }

    private static CheatTesterSettings parseCheatTesterSettings(JsonNode root, List<String> errors) {
        CheatTesterSettings defaults = CheatTesterSettings.defaults();
        JsonNode staffTools = ConfigurationNodes.optionalMapping(root, "staff-tools", "staff-tools", errors);
        JsonNode tester = ConfigurationNodes.optionalMapping(
                staffTools,
                "cheat-tester",
                "staff-tools.cheat-tester",
                errors
        );
        JsonNode velocity = ConfigurationNodes.optionalMapping(
                tester,
                "velocity",
                "staff-tools.cheat-tester.velocity",
                errors
        );
        JsonNode noFall = ConfigurationNodes.optionalMapping(
                tester,
                "no-fall",
                "staff-tools.cheat-tester.no-fall",
                errors
        );
        int global = ConfigurationNodes.boundedInteger(
                tester,
                "maximum-active-global",
                "staff-tools.cheat-tester.maximum-active-global",
                defaults.maximumActiveGlobal(),
                1,
                32,
                errors
        );
        int perStaff = ConfigurationNodes.boundedInteger(
                tester,
                "maximum-active-per-staff",
                "staff-tools.cheat-tester.maximum-active-per-staff",
                defaults.maximumActivePerStaff(),
                1,
                32,
                errors
        );
        if (perStaff > global) {
            errors.add("staff-tools.cheat-tester.maximum-active-per-staff must not exceed maximum-active-global");
            perStaff = Math.min(defaults.maximumActivePerStaff(), global);
        }
        return new CheatTesterSettings(
                Duration.ofMillis(ConfigurationNodes.boundedLong(
                        tester,
                        "timeout-millis",
                        "staff-tools.cheat-tester.timeout-millis",
                        defaults.sessionTimeout().toMillis(),
                        1_000,
                        15_000,
                        errors
                )),
                global,
                perStaff,
                ConfigurationNodes.boundedDouble(
                        tester,
                        "fake-entity-distance",
                        "staff-tools.cheat-tester.fake-entity-distance",
                        defaults.fakeEntityDistance(),
                        1.0D,
                        8.0D,
                        errors
                ),
                ConfigurationNodes.boundedDouble(
                        velocity,
                        "horizontal",
                        "staff-tools.cheat-tester.velocity.horizontal",
                        defaults.velocityHorizontal(),
                        0.0D,
                        2.0D,
                        errors
                ),
                ConfigurationNodes.boundedDouble(
                        velocity,
                        "vertical",
                        "staff-tools.cheat-tester.velocity.vertical",
                        defaults.velocityVertical(),
                        0.0D,
                        2.0D,
                        errors
                ),
                ConfigurationNodes.boundedDouble(
                        noFall,
                        "vertical",
                        "staff-tools.cheat-tester.no-fall.vertical",
                        defaults.noFallVertical(),
                        0.1D,
                        2.0D,
                        errors
                ),
                ConfigurationNodes.boundedInteger(
                        tester,
                        "probe-ticks",
                        "staff-tools.cheat-tester.probe-ticks",
                        defaults.probeTicks(),
                        10,
                        300,
                        errors
                )
        );
    }

    private static void validateChannel(
            RestartRequiredConfiguration restart,
            Path dataDirectory,
            List<String> errors
    ) {
        if (!restart.channelEnabled()) {
            return;
        }
        try {
            Path configured = Path.of(restart.channelTrustStore());
            if (!configured.isAbsolute()) {
                Path resolved = dataDirectory.resolve(configured).normalize();
                if (!resolved.startsWith(dataDirectory)) {
                    errors.add("channel.tls.trust-store must remain inside the plugin data directory when relative");
                }
            }
        } catch (RuntimeException exception) {
            errors.add("channel.tls.trust-store must be a valid path");
        }
    }
}
