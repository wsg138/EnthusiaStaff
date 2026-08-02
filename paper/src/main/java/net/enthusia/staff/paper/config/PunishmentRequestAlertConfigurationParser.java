package net.enthusia.staff.paper.config;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import net.enthusia.staff.paper.alert.PunishmentRequestAlertWorkerSettings;

final class PunishmentRequestAlertConfigurationParser {
    private static final long MIN_POLL_MILLIS = 250;
    private static final long MAX_POLL_MILLIS = Duration.ofMinutes(5).toMillis();
    private static final int MAX_RECIPIENTS = 500;
    private static final int MAX_BATCH = 100;
    private static final int MAX_TOTAL_CLAIMS = MAX_RECIPIENTS * MAX_BATCH;
    private static final int MAX_ATTEMPTS = 100;
    private static final long MAX_JOIN_TICKS = 1_000_000;
    private static final long MAX_INTERVAL_SECONDS = Duration.ofDays(30).toSeconds();
    private static final long MAX_RETENTION_MINUTES = Duration.ofDays(30).toMinutes();
    private static final long MAX_RETENTION_DAYS = 3_650;

    private static final Set<String> ROOT_FIELDS = Set.of("enabled", "polling", "delivery", "maintenance");
    private static final Set<String> POLLING_FIELDS = Set.of(
            "interval-millis", "recipient-limit", "direct-batch", "reviewer-batch",
            "operational-batch", "total-claim-limit", "presentation-limit"
    );
    private static final Set<String> DELIVERY_FIELDS = Set.of(
            "lease-seconds", "maximum-attempts", "retry-base-seconds",
            "retry-maximum-seconds", "join-delay-ticks"
    );
    private static final Set<String> MAINTENANCE_FIELDS = Set.of(
            "request-expiration", "intent-expiration", "lease-reclaim", "retention"
    );
    private static final Set<String> INTERVAL_BATCH_FIELDS = Set.of("interval-seconds", "batch");
    private static final Set<String> RETENTION_FIELDS = Set.of("interval-minutes", "batch", "days");

    PunishmentRequestAlertWorkerSettings parse(
            JsonNode root,
            RestartRequiredConfiguration restart,
            List<String> errors
    ) {
        PunishmentRequestAlertWorkerSettings defaults = PunishmentRequestAlertWorkerSettings.safeDefaults(false);
        if (root == null) {
            return defaults;
        }
        if (!root.isObject()) {
            errors.add("punishment-request-alerts must be a mapping section");
            return defaults;
        }
        ConfigurationNodes.rejectUnknown(root, ROOT_FIELDS, "punishment-request-alerts", errors);

        JsonNode polling = section(root, "polling", "punishment-request-alerts.polling", POLLING_FIELDS, errors);
        JsonNode delivery = section(root, "delivery", "punishment-request-alerts.delivery", DELIVERY_FIELDS, errors);
        JsonNode maintenance = section(root, "maintenance", "punishment-request-alerts.maintenance", MAINTENANCE_FIELDS, errors);
        JsonNode requests = section(maintenance, "request-expiration",
                "punishment-request-alerts.maintenance.request-expiration", INTERVAL_BATCH_FIELDS, errors);
        JsonNode intents = section(maintenance, "intent-expiration",
                "punishment-request-alerts.maintenance.intent-expiration", INTERVAL_BATCH_FIELDS, errors);
        JsonNode reclaim = section(maintenance, "lease-reclaim",
                "punishment-request-alerts.maintenance.lease-reclaim", INTERVAL_BATCH_FIELDS, errors);
        JsonNode retention = section(maintenance, "retention",
                "punishment-request-alerts.maintenance.retention", RETENTION_FIELDS, errors);

        Values values = new Values(
                ConfigurationNodes.bool(root, "enabled", "punishment-request-alerts.enabled", defaults.enabled(), errors),
                ConfigurationNodes.boundedLong(polling, "interval-millis",
                        "punishment-request-alerts.polling.interval-millis", defaults.pollInterval().toMillis(),
                        MIN_POLL_MILLIS, MAX_POLL_MILLIS, errors),
                ConfigurationNodes.boundedInteger(polling, "recipient-limit",
                        "punishment-request-alerts.polling.recipient-limit", defaults.recipientLimit(),
                        1, MAX_RECIPIENTS, errors),
                batch(polling, "direct-batch", defaults.directBatch(), errors),
                batch(polling, "reviewer-batch", defaults.reviewerBatch(), errors),
                batch(polling, "operational-batch", defaults.operationalBatch(), errors),
                ConfigurationNodes.boundedInteger(polling, "total-claim-limit",
                        "punishment-request-alerts.polling.total-claim-limit", defaults.totalClaimLimit(),
                        1, MAX_TOTAL_CLAIMS, errors),
                ConfigurationNodes.boundedInteger(polling, "presentation-limit",
                        "punishment-request-alerts.polling.presentation-limit", defaults.presentationLimit(),
                        1, MAX_TOTAL_CLAIMS, errors),
                seconds(delivery, "lease-seconds", defaults.leaseDuration(), errors),
                ConfigurationNodes.boundedInteger(delivery, "maximum-attempts",
                        "punishment-request-alerts.delivery.maximum-attempts", defaults.maximumAttempts(),
                        1, MAX_ATTEMPTS, errors),
                seconds(delivery, "retry-base-seconds", defaults.retryBase(), errors),
                seconds(delivery, "retry-maximum-seconds", defaults.retryMaximum(), errors),
                ConfigurationNodes.boundedLong(delivery, "join-delay-ticks",
                        "punishment-request-alerts.delivery.join-delay-ticks", defaults.joinDelay().toMillis() / 50L,
                        0, MAX_JOIN_TICKS, errors),
                intervalSeconds(requests, defaults.requestExpirationInterval(),
                        "punishment-request-alerts.maintenance.request-expiration", errors),
                maintenanceBatch(requests, defaults.requestExpirationBatch(),
                        "punishment-request-alerts.maintenance.request-expiration", errors),
                intervalSeconds(intents, defaults.intentExpirationInterval(),
                        "punishment-request-alerts.maintenance.intent-expiration", errors),
                maintenanceBatch(intents, defaults.intentExpirationBatch(),
                        "punishment-request-alerts.maintenance.intent-expiration", errors),
                intervalSeconds(reclaim, defaults.leaseReclaimInterval(),
                        "punishment-request-alerts.maintenance.lease-reclaim", errors),
                maintenanceBatch(reclaim, defaults.leaseReclaimBatch(),
                        "punishment-request-alerts.maintenance.lease-reclaim", errors),
                ConfigurationNodes.boundedLong(retention, "interval-minutes",
                        "punishment-request-alerts.maintenance.retention.interval-minutes",
                        defaults.retentionInterval().toMinutes(), 1, MAX_RETENTION_MINUTES, errors),
                maintenanceBatch(retention, defaults.retentionBatch(),
                        "punishment-request-alerts.maintenance.retention", errors),
                ConfigurationNodes.boundedLong(retention, "days",
                        "punishment-request-alerts.maintenance.retention.days",
                        defaults.retentionDuration().toDays(), 1, MAX_RETENTION_DAYS, errors)
        );
        PunishmentRequestAlertConfigurationValidator.validate(values, restart, errors);
        return values.toSettings(defaults, errors);
    }

    private static JsonNode section(
            JsonNode parent,
            String field,
            String path,
            Set<String> fields,
            List<String> errors
    ) {
        JsonNode section = ConfigurationNodes.optionalMapping(parent, field, path, errors);
        ConfigurationNodes.rejectUnknown(section, fields, path, errors);
        return section;
    }

    private static int batch(JsonNode node, String field, int fallback, List<String> errors) {
        return ConfigurationNodes.boundedInteger(node, field,
                "punishment-request-alerts.polling." + field, fallback, 1, MAX_BATCH, errors);
    }

    private static long seconds(JsonNode node, String field, Duration fallback, List<String> errors) {
        return ConfigurationNodes.boundedLong(node, field,
                "punishment-request-alerts.delivery." + field, fallback.toSeconds(),
                1, MAX_INTERVAL_SECONDS, errors);
    }

    private static long intervalSeconds(
            JsonNode node,
            Duration fallback,
            String path,
            List<String> errors
    ) {
        return ConfigurationNodes.boundedLong(node, "interval-seconds", path + ".interval-seconds",
                fallback.toSeconds(), 1, MAX_INTERVAL_SECONDS, errors);
    }

    private static int maintenanceBatch(JsonNode node, int fallback, String path, List<String> errors) {
        return ConfigurationNodes.boundedInteger(node, "batch", path + ".batch",
                fallback, 1, MAX_BATCH, errors);
    }

    record Values(
            boolean enabled,
            long pollMillis,
            int recipientLimit,
            int directBatch,
            int reviewerBatch,
            int operationalBatch,
            int totalClaimLimit,
            int presentationLimit,
            long leaseSeconds,
            int maximumAttempts,
            long retryBaseSeconds,
            long retryMaximumSeconds,
            long joinDelayTicks,
            long requestExpirationSeconds,
            int requestExpirationBatch,
            long intentExpirationSeconds,
            int intentExpirationBatch,
            long leaseReclaimSeconds,
            int leaseReclaimBatch,
            long retentionMinutes,
            int retentionBatch,
            long retentionDays
    ) {
        PunishmentRequestAlertWorkerSettings toSettings(
                PunishmentRequestAlertWorkerSettings fallback,
                List<String> errors
        ) {
            try {
                return new PunishmentRequestAlertWorkerSettings(
                        enabled, Duration.ofMillis(pollMillis), recipientLimit,
                        directBatch, reviewerBatch, operationalBatch,
                        totalClaimLimit, presentationLimit, Duration.ofSeconds(leaseSeconds),
                        maximumAttempts, Duration.ofSeconds(retryBaseSeconds),
                        Duration.ofSeconds(retryMaximumSeconds),
                        Duration.ofMillis(Math.multiplyExact(joinDelayTicks, 50L)),
                        Duration.ofSeconds(requestExpirationSeconds),
                        Duration.ofSeconds(intentExpirationSeconds),
                        Duration.ofSeconds(leaseReclaimSeconds), Duration.ofMinutes(retentionMinutes),
                        requestExpirationBatch, intentExpirationBatch, leaseReclaimBatch,
                        retentionBatch, Duration.ofDays(retentionDays)
                );
            } catch (ArithmeticException exception) {
                errors.add("punishment-request-alerts contains a duration that cannot be represented safely");
            } catch (IllegalArgumentException exception) {
                errors.add("punishment-request-alerts violates an alert-worker invariant");
            }
            return fallback;
        }
    }
}
