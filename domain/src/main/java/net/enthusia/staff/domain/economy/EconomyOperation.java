package net.enthusia.staff.domain.economy;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;

public record EconomyOperation(
        UUID operationId,
        String idempotencyKey,
        String caseId,
        UUID targetId,
        Optional<UUID> actorId,
        EconomyAmountMode amountMode,
        OptionalLong requestedAmount,
        OptionalLong authoritativeTotal,
        Optional<String> owningServerId,
        EconomyOperationState state,
        Optional<EconomyTerminalOutcome> terminalOutcome,
        long fencingToken,
        Optional<Instant> leaseUntil,
        Optional<String> beforeChecksum,
        Optional<String> replacementChecksum,
        Optional<String> beforeSnapshotJson,
        Optional<String> planJson,
        OptionalLong resultTotal,
        Optional<String> resultChecksum,
        Optional<String> resultSnapshotJson,
        Optional<String> failureCode,
        Optional<String> failureDetail,
        Instant createdAt,
        Instant updatedAt
) {
    public EconomyOperation {
        Objects.requireNonNull(operationId, "operationId");
        if (idempotencyKey == null || !idempotencyKey.matches("[A-Za-z0-9._:/-]{1,128}")) {
            throw new IllegalArgumentException("idempotencyKey is invalid");
        }
        caseId = EconomyPrepareRequest.requireCaseId(caseId);
        Objects.requireNonNull(targetId, "targetId");
        actorId = Objects.requireNonNull(actorId, "actorId");
        Objects.requireNonNull(amountMode, "amountMode");
        requestedAmount = nonNegative(requestedAmount, "requestedAmount");
        authoritativeTotal = nonNegative(authoritativeTotal, "authoritativeTotal");
        owningServerId = Objects.requireNonNull(owningServerId, "owningServerId")
                .map(value -> EconomyPrepareRequest.requireIdentifier(value, "owningServerId"));
        Objects.requireNonNull(state, "state");
        terminalOutcome = Objects.requireNonNull(terminalOutcome, "terminalOutcome");
        if (fencingToken < 0L) {
            throw new IllegalArgumentException("fencingToken cannot be negative");
        }
        leaseUntil = Objects.requireNonNull(leaseUntil, "leaseUntil");
        beforeChecksum = checksum(beforeChecksum, "beforeChecksum");
        replacementChecksum = checksum(replacementChecksum, "replacementChecksum");
        beforeSnapshotJson = json(beforeSnapshotJson, "beforeSnapshotJson");
        planJson = json(planJson, "planJson");
        resultTotal = nonNegative(resultTotal, "resultTotal");
        resultChecksum = checksum(resultChecksum, "resultChecksum");
        resultSnapshotJson = json(resultSnapshotJson, "resultSnapshotJson");
        failureCode = Objects.requireNonNull(failureCode, "failureCode").map(String::trim);
        failureDetail = Objects.requireNonNull(failureDetail, "failureDetail").map(String::trim);
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
        if (updatedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("updatedAt cannot precede createdAt");
        }
        if (state == EconomyOperationState.UNLOCKED && terminalOutcome.isEmpty()) {
            throw new IllegalArgumentException("unlocked operations require a terminal outcome");
        }
        if (state == EconomyOperationState.QUARANTINED
                && terminalOutcome.orElse(null) != EconomyTerminalOutcome.QUARANTINED) {
            throw new IllegalArgumentException("quarantined state requires a quarantined outcome");
        }
    }

    public boolean unresolved() {
        return state != EconomyOperationState.UNLOCKED;
    }

    public boolean terminalMatches(EconomyTerminalUpdate update) {
        Objects.requireNonNull(update, "update");
        return terminalOutcome.equals(Optional.of(update.outcome()))
                && resultTotal.equals(update.resultTotal())
                && resultChecksum.equals(update.resultChecksum())
                && resultSnapshotJson.equals(update.resultSnapshotJson())
                && failureCode.equals(update.failureCode())
                && failureDetail.equals(update.failureDetail());
    }

    public boolean hasAnyDurablePlanEvidence() {
        return authoritativeTotal.isPresent()
                || beforeChecksum.isPresent()
                || replacementChecksum.isPresent()
                || beforeSnapshotJson.isPresent()
                || planJson.isPresent();
    }

    public boolean hasCompleteDurablePlanEvidence() {
        return requestedAmount.isPresent()
                && authoritativeTotal.isPresent()
                && beforeChecksum.isPresent()
                && replacementChecksum.isPresent()
                && beforeSnapshotJson.isPresent()
                && planJson.isPresent();
    }

    private static OptionalLong nonNegative(OptionalLong value, String field) {
        Objects.requireNonNull(value, field);
        if (value.isPresent() && value.orElseThrow() < 0L) {
            throw new IllegalArgumentException(field + " cannot be negative");
        }
        return value;
    }

    private static Optional<String> checksum(Optional<String> value, String field) {
        return Objects.requireNonNull(value, field)
                .map(checksum -> EconomyPrepareRequest.requireChecksum(checksum, field));
    }

    private static Optional<String> json(Optional<String> value, String field) {
        return Objects.requireNonNull(value, field)
                .map(body -> EconomyValidatedPlan.requireJson(body, field));
    }
}
