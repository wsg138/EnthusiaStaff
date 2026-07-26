package net.enthusia.staff.domain.economy;

import java.time.Instant;
import java.util.Objects;
import java.util.OptionalLong;
import java.util.UUID;
import net.enthusia.staff.common.CaseId;

public record EconomyPrepareRequest(
        UUID operationId,
        String idempotencyKey,
        String caseId,
        UUID targetId,
        UUID actorId,
        EconomyAmountMode amountMode,
        OptionalLong requestedAmount,
        String owningServerId,
        Instant requestedAt
) {
    public EconomyPrepareRequest {
        Objects.requireNonNull(operationId, "operationId");
        if (idempotencyKey == null || !idempotencyKey.matches("[A-Za-z0-9._:/-]{1,128}")) {
            throw new IllegalArgumentException("idempotencyKey is invalid");
        }
        caseId = requireCaseId(caseId);
        Objects.requireNonNull(targetId, "targetId");
        Objects.requireNonNull(actorId, "actorId");
        Objects.requireNonNull(amountMode, "amountMode");
        requestedAmount = Objects.requireNonNull(requestedAmount, "requestedAmount");
        if (amountMode == EconomyAmountMode.ALL && requestedAmount.isPresent()) {
            throw new IllegalArgumentException("ALL operations cannot include a requested amount");
        }
        if (amountMode == EconomyAmountMode.CUSTOM
                && (requestedAmount.isEmpty() || requestedAmount.orElseThrow() <= 0L)) {
            throw new IllegalArgumentException("CUSTOM operations require a positive amount");
        }
        owningServerId = requireIdentifier(owningServerId, "owningServerId");
        Objects.requireNonNull(requestedAt, "requestedAt");
    }

    static String requireCaseId(String value) {
        return new CaseId(value).value();
    }

    static String requireIdentifier(String value, String field) {
        if (value == null || value.isBlank() || value.length() > 64) {
            throw new IllegalArgumentException(field + " must contain 1-64 characters");
        }
        return value;
    }

    static String requireChecksum(String value, String field) {
        if (value == null || !value.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException(field + " must be a lowercase SHA-256 value");
        }
        return value;
    }
}
