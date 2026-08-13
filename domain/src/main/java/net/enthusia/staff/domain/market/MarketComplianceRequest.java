package net.enthusia.staff.domain.market;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;
import net.enthusia.staff.common.CaseId;
import net.enthusia.staff.common.IdempotencyKey;

public record MarketComplianceRequest(
        UUID operationId,
        IdempotencyKey idempotencyKey,
        CaseId caseId,
        UUID targetId,
        MarketComplianceKind kind,
        Optional<String> stallId,
        UUID requestedBy,
        Optional<Instant> blacklistExpiresAt,
        OptionalLong expectedBlacklistRevision,
        Instant reviewDueAt,
        Instant recoveryUntil,
        Instant createdAt
) {
    private static final Duration MAXIMUM_RECOVERY_WINDOW = Duration.ofDays(31);

    public MarketComplianceRequest {
        operationId = Objects.requireNonNull(operationId, "operationId");
        idempotencyKey = Objects.requireNonNull(idempotencyKey, "idempotencyKey");
        caseId = Objects.requireNonNull(caseId, "caseId");
        targetId = Objects.requireNonNull(targetId, "targetId");
        kind = Objects.requireNonNull(kind, "kind");
        stallId = Objects.requireNonNull(stallId, "stallId");
        requestedBy = Objects.requireNonNull(requestedBy, "requestedBy");
        blacklistExpiresAt = Objects.requireNonNull(blacklistExpiresAt, "blacklistExpiresAt");
        expectedBlacklistRevision = Objects.requireNonNull(
                expectedBlacklistRevision,
                "expectedBlacklistRevision"
        );
        reviewDueAt = Objects.requireNonNull(reviewDueAt, "reviewDueAt");
        recoveryUntil = Objects.requireNonNull(recoveryUntil, "recoveryUntil");
        createdAt = Objects.requireNonNull(createdAt, "createdAt");
        validateShape(kind, stallId, blacklistExpiresAt, expectedBlacklistRevision);
        stallId.ifPresent(MarketComplianceRequest::validateStallId);
        if (blacklistExpiresAt.isPresent()
                && !blacklistExpiresAt.orElseThrow().isAfter(createdAt)) {
            throw new IllegalArgumentException("blacklist expiration must be in the future");
        }
        Duration recoveryWindow = Duration.between(reviewDueAt, recoveryUntil);
        if (recoveryWindow.isNegative() || recoveryWindow.isZero()
                || recoveryWindow.compareTo(MAXIMUM_RECOVERY_WINDOW) > 0) {
            throw new IllegalArgumentException("recovery window must be positive and at most 31 days");
        }
    }

    private static void validateShape(
            MarketComplianceKind kind,
            Optional<String> stallId,
            Optional<Instant> blacklistExpiresAt,
            OptionalLong expectedBlacklistRevision
    ) {
        if (kind == MarketComplianceKind.STALL && stallId.isEmpty()) {
            throw new IllegalArgumentException("stall operations require a stall id");
        }
        if (kind != MarketComplianceKind.STALL && stallId.isPresent()) {
            throw new IllegalArgumentException("blacklist operations cannot contain a stall id");
        }
        if (kind == MarketComplianceKind.BLACKLIST_REMOVE) {
            if (expectedBlacklistRevision.isEmpty() || expectedBlacklistRevision.orElseThrow() < 1L) {
                throw new IllegalArgumentException("blacklist removal requires a positive provider revision");
            }
            if (blacklistExpiresAt.isPresent()) {
                throw new IllegalArgumentException("blacklist removal cannot contain an expiration");
            }
        } else if (expectedBlacklistRevision.isPresent()) {
            throw new IllegalArgumentException("only blacklist removal can contain a provider revision");
        }
    }

    private static void validateStallId(String value) {
        if (value == null || value.isBlank() || value.length() > 128) {
            throw new IllegalArgumentException("stall id must contain 1-128 characters");
        }
    }
}
