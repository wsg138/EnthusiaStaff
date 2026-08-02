package net.enthusia.staff.paper.alert;

import java.time.Duration;
import java.util.Objects;

public record PunishmentRequestAlertWorkerSettings(
        boolean enabled,
        Duration pollInterval,
        int recipientLimit,
        int directBatch,
        int reviewerBatch,
        int operationalBatch,
        int totalClaimLimit,
        int presentationLimit,
        Duration leaseDuration,
        int maximumAttempts,
        Duration retryBase,
        Duration retryMaximum,
        Duration joinDelay,
        Duration requestExpirationInterval,
        Duration intentExpirationInterval,
        Duration leaseReclaimInterval,
        Duration retentionInterval,
        int requestExpirationBatch,
        int intentExpirationBatch,
        int leaseReclaimBatch,
        int retentionBatch,
        Duration retentionDuration
) {
    private static final int MAX_BATCH = 100;
    private static final int MAX_RECIPIENTS = 500;

    public PunishmentRequestAlertWorkerSettings {
        requirePositive(pollInterval, "poll interval");
        requirePositive(leaseDuration, "lease duration");
        requirePositive(retryBase, "retry base");
        requirePositive(retryMaximum, "retry maximum");
        requireNonNegative(joinDelay, "join delay");
        requirePositive(requestExpirationInterval, "request expiration interval");
        requirePositive(intentExpirationInterval, "intent expiration interval");
        requirePositive(leaseReclaimInterval, "lease reclaim interval");
        requirePositive(retentionInterval, "retention interval");
        requirePositive(retentionDuration, "retention duration");
        if (retryMaximum.compareTo(retryBase) < 0) {
            throw new IllegalArgumentException("retry maximum must not be shorter than retry base");
        }
        requireRange(recipientLimit, 1, MAX_RECIPIENTS, "recipient limit");
        requireRange(directBatch, 1, MAX_BATCH, "direct batch");
        requireRange(reviewerBatch, 1, MAX_BATCH, "reviewer batch");
        requireRange(operationalBatch, 1, MAX_BATCH, "operational batch");
        requireRange(totalClaimLimit, 1, MAX_RECIPIENTS * MAX_BATCH, "total claim limit");
        requireRange(presentationLimit, 1, totalClaimLimit, "presentation limit");
        requireRange(maximumAttempts, 1, 100, "maximum attempts");
        requireRange(requestExpirationBatch, 1, MAX_BATCH, "request expiration batch");
        requireRange(intentExpirationBatch, 1, MAX_BATCH, "intent expiration batch");
        requireRange(leaseReclaimBatch, 1, MAX_BATCH, "lease reclaim batch");
        requireRange(retentionBatch, 1, MAX_BATCH, "retention batch");
    }

    public static PunishmentRequestAlertWorkerSettings safeDefaults(boolean enabled) {
        return new PunishmentRequestAlertWorkerSettings(
                enabled,
                Duration.ofSeconds(10),
                100,
                4,
                4,
                2,
                100,
                100,
                Duration.ofSeconds(45),
                6,
                Duration.ofSeconds(5),
                Duration.ofMinutes(5),
                Duration.ofSeconds(2),
                Duration.ofMinutes(1),
                Duration.ofMinutes(1),
                Duration.ofSeconds(30),
                Duration.ofHours(1),
                100,
                100,
                100,
                100,
                Duration.ofDays(30)
        );
    }

    public Duration retryDelay(int attemptCount) {
        if (attemptCount < 1) {
            throw new IllegalArgumentException("attempt count must be positive");
        }
        long baseMillis = retryBase.toMillis();
        long maximumMillis = retryMaximum.toMillis();
        int shifts = Math.min(attemptCount - 1, 62);
        long multiplier = 1L << shifts;
        long delay;
        if (baseMillis > Long.MAX_VALUE / multiplier) {
            delay = maximumMillis;
        } else {
            delay = Math.min(maximumMillis, baseMillis * multiplier);
        }
        return Duration.ofMillis(delay);
    }

    private static void requireRange(int value, int minimum, int maximum, String label) {
        if (value < minimum || value > maximum) {
            throw new IllegalArgumentException(label + " must be between " + minimum + " and " + maximum);
        }
    }

    private static void requirePositive(Duration value, String label) {
        Objects.requireNonNull(value, label);
        if (value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(label + " must be positive");
        }
    }

    private static void requireNonNegative(Duration value, String label) {
        Objects.requireNonNull(value, label);
        if (value.isNegative()) {
            throw new IllegalArgumentException(label + " must not be negative");
        }
    }
}
