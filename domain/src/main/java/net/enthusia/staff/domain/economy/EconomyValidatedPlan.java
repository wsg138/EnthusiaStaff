package net.enthusia.staff.domain.economy;

import java.util.Objects;

public record EconomyValidatedPlan(
        long authoritativeTotal,
        long actualRequestedAmount,
        String beforeChecksum,
        String replacementChecksum,
        String beforeSnapshotJson,
        String planJson
) {
    public EconomyValidatedPlan {
        if (authoritativeTotal < 0L || actualRequestedAmount <= 0L
                || actualRequestedAmount > authoritativeTotal) {
            throw new IllegalArgumentException("validated economy totals are invalid");
        }
        beforeChecksum = EconomyPrepareRequest.requireChecksum(beforeChecksum, "beforeChecksum");
        replacementChecksum = EconomyPrepareRequest.requireChecksum(
                replacementChecksum,
                "replacementChecksum"
        );
        beforeSnapshotJson = requireJson(beforeSnapshotJson, "beforeSnapshotJson");
        planJson = requireJson(planJson, "planJson");
    }

    static String requireJson(String value, String field) {
        Objects.requireNonNull(value, field);
        if (value.isBlank() || value.length() > 16_777_216) {
            throw new IllegalArgumentException(field + " must contain bounded JSON");
        }
        return value;
    }
}
