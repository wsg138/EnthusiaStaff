package net.enthusia.staff.domain.economy;

import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;

public record EconomyTerminalUpdate(
        EconomyTerminalOutcome outcome,
        OptionalLong resultTotal,
        Optional<String> resultChecksum,
        Optional<String> resultSnapshotJson,
        Optional<String> failureCode,
        Optional<String> failureDetail
) {
    public EconomyTerminalUpdate {
        Objects.requireNonNull(outcome, "outcome");
        resultTotal = Objects.requireNonNull(resultTotal, "resultTotal");
        if (resultTotal.isPresent() && resultTotal.orElseThrow() < 0L) {
            throw new IllegalArgumentException("resultTotal cannot be negative");
        }
        resultChecksum = Objects.requireNonNull(resultChecksum, "resultChecksum")
                .map(value -> EconomyPrepareRequest.requireChecksum(value, "resultChecksum"));
        resultSnapshotJson = Objects.requireNonNull(resultSnapshotJson, "resultSnapshotJson")
                .map(value -> EconomyValidatedPlan.requireJson(value, "resultSnapshotJson"));
        failureCode = Objects.requireNonNull(failureCode, "failureCode").map(String::trim);
        failureDetail = Objects.requireNonNull(failureDetail, "failureDetail").map(String::trim);
        if (failureCode.isPresent()
                && !failureCode.orElseThrow().matches("[A-Z0-9_]{1,64}")) {
            throw new IllegalArgumentException("failureCode is invalid");
        }
        if (failureDetail.isPresent()
                && (failureDetail.orElseThrow().isEmpty() || failureDetail.orElseThrow().length() > 1024)) {
            throw new IllegalArgumentException("failureDetail is invalid");
        }
        boolean completeSnapshot = resultChecksum.isPresent() == resultSnapshotJson.isPresent();
        if (!completeSnapshot) {
            throw new IllegalArgumentException("result checksum and snapshot must be supplied together");
        }
        if (outcome == EconomyTerminalOutcome.COMMITTED
                && (resultTotal.isEmpty() || resultChecksum.isEmpty())) {
            throw new IllegalArgumentException("committed operations require a verified result snapshot");
        }
        if (outcome == EconomyTerminalOutcome.QUARANTINED && failureCode.isEmpty()) {
            throw new IllegalArgumentException("quarantined operations require a failure code");
        }
    }

    public static EconomyTerminalUpdate committed(
            long resultTotal,
            String resultChecksum,
            String resultSnapshotJson
    ) {
        return new EconomyTerminalUpdate(
                EconomyTerminalOutcome.COMMITTED,
                OptionalLong.of(resultTotal),
                Optional.of(resultChecksum),
                Optional.of(resultSnapshotJson),
                Optional.empty(),
                Optional.empty()
        );
    }

    public static EconomyTerminalUpdate rolledBack(
            OptionalLong resultTotal,
            Optional<String> resultChecksum,
            Optional<String> resultSnapshotJson,
            String failureCode,
            String failureDetail
    ) {
        return new EconomyTerminalUpdate(
                EconomyTerminalOutcome.ROLLED_BACK,
                resultTotal,
                resultChecksum,
                resultSnapshotJson,
                Optional.of(failureCode),
                Optional.of(failureDetail)
        );
    }

    public static EconomyTerminalUpdate quarantined(
            OptionalLong resultTotal,
            Optional<String> resultChecksum,
            Optional<String> resultSnapshotJson,
            String failureCode,
            String failureDetail
    ) {
        return new EconomyTerminalUpdate(
                EconomyTerminalOutcome.QUARANTINED,
                resultTotal,
                resultChecksum,
                resultSnapshotJson,
                Optional.of(failureCode),
                Optional.of(failureDetail)
        );
    }
}
