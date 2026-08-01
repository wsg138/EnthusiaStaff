package net.enthusia.staff.paper.config;

import java.util.List;

final class PunishmentRequestAlertConfigurationValidator {
    private PunishmentRequestAlertConfigurationValidator() {
    }

    static void validate(
            PunishmentRequestAlertConfigurationParser.Values values,
            RestartRequiredConfiguration restart,
            List<String> errors
    ) {
        if (values.presentationLimit() > values.totalClaimLimit()) {
            errors.add("punishment-request-alerts.polling.presentation-limit must not exceed "
                    + "punishment-request-alerts.polling.total-claim-limit");
        }
        if (values.retryMaximumSeconds() < values.retryBaseSeconds()) {
            errors.add("punishment-request-alerts.delivery.retry-maximum-seconds must not be shorter than "
                    + "punishment-request-alerts.delivery.retry-base-seconds");
        }
        long executorCapacity = (long) restart.workerThreads() + restart.workerQueueCapacity();
        if (values.recipientLimit() > executorCapacity) {
            errors.add("punishment-request-alerts.polling.recipient-limit must not exceed "
                    + "workers.threads plus workers.queue-capacity");
        }
        long minimumLease = minimumLeaseSeconds(values.pollMillis());
        if (values.leaseSeconds() < minimumLease) {
            errors.add("punishment-request-alerts.delivery.lease-seconds must be at least "
                    + minimumLease + " for the configured polling interval");
        }
        validateExponentialDelay(values, errors);
    }

    private static long minimumLeaseSeconds(long pollMillis) {
        try {
            long pressureMillis = Math.addExact(Math.multiplyExact(pollMillis, 2L), 5_000L);
            return Math.max(5L, Math.floorDiv(Math.addExact(pressureMillis, 999L), 1_000L));
        } catch (ArithmeticException exception) {
            return Long.MAX_VALUE;
        }
    }

    private static void validateExponentialDelay(
            PunishmentRequestAlertConfigurationParser.Values values,
            List<String> errors
    ) {
        try {
            long baseMillis = Math.multiplyExact(values.retryBaseSeconds(), 1_000L);
            long maximumMillis = Math.multiplyExact(values.retryMaximumSeconds(), 1_000L);
            int shifts = Math.min(Math.max(values.maximumAttempts() - 1, 0), 62);
            long multiplier = 1L << shifts;
            if (baseMillis <= Long.MAX_VALUE / multiplier) {
                Math.min(maximumMillis, Math.multiplyExact(baseMillis, multiplier));
            }
        } catch (ArithmeticException exception) {
            errors.add("punishment-request-alerts.delivery retry delay cannot be represented safely");
        }
    }
}
