package net.enthusia.staff.domain.report;

import java.time.Duration;
import java.util.Objects;

public record ReportPolicy(
        Duration anyCooldown,
        Duration targetCooldown,
        Duration duplicateWindow,
        int maxOpenReports,
        int queryLimit,
        Duration recentlyClosedWindow,
        Duration evidenceRetention,
        int evidencePurgeBatchLimit
) {
    private static final int MAX_QUERY_LIMIT = 100;
    private static final int MAX_PURGE_BATCH_LIMIT = 1_000;

    public ReportPolicy {
        requirePositive(anyCooldown, "anyCooldown");
        requirePositive(targetCooldown, "targetCooldown");
        requirePositive(duplicateWindow, "duplicateWindow");
        requirePositive(recentlyClosedWindow, "recentlyClosedWindow");
        requirePositive(evidenceRetention, "evidenceRetention");
        requireRange(maxOpenReports, 1, 100, "maxOpenReports");
        requireRange(queryLimit, 1, MAX_QUERY_LIMIT, "queryLimit");
        requireRange(evidencePurgeBatchLimit, 1, MAX_PURGE_BATCH_LIMIT, "evidencePurgeBatchLimit");
    }

    public static ReportPolicy defaults() {
        return new ReportPolicy(
                Duration.ofMinutes(2),
                Duration.ofMinutes(30),
                Duration.ofHours(2),
                5,
                100,
                Duration.ofDays(7),
                Duration.ofDays(7),
                500
        );
    }

    private static void requirePositive(Duration value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(name + " must be positive");
        }
    }

    private static void requireRange(int value, int minimum, int maximum, String name) {
        if (value < minimum || value > maximum) {
            throw new IllegalArgumentException(name + " must be between " + minimum + " and " + maximum);
        }
    }
}
