package net.enthusia.staff.paper.market;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import net.enthusia.market.api.moderation.MarketBlacklistResult;
import net.enthusia.market.api.moderation.MarketOperationRecord;
import net.enthusia.market.api.moderation.MarketOperationResult;
import net.enthusia.market.api.moderation.StallBlacklistState;
import net.enthusia.staff.domain.market.MarketComplianceKind;
import net.enthusia.staff.domain.market.MarketComplianceOperation;
import net.enthusia.staff.domain.market.MarketComplianceResult;
import net.enthusia.staff.domain.market.MarketComplianceState;
import net.enthusia.staff.domain.market.MarketComplianceUpdate;
import net.enthusia.staff.domain.ports.MarketComplianceStore;

final class MarketJournalReconciler {
    MarketCoordinationResult operation(
            MarketComplianceStore store,
            MarketComplianceOperation local,
            MarketOperationResult provider,
            Instant now
    ) {
        Objects.requireNonNull(provider, "provider");
        MarketOperationRecord record = provider.operation().orElse(null);
        if (record != null && !matches(local, record)) {
            return quarantine(store, local, record.revision(), "Market provider identity mismatch", now);
        }
        if (record != null) {
            return persist(store, local, operationUpdate(record, provider.detail(), now));
        }
        return switch (provider.status()) {
            case REJECTED -> persistTerminal(
                    store, local, MarketComplianceState.REJECTED, provider.detail(), now
            );
            case CONFLICT -> result(
                    MarketCoordinationResult.Status.CONFLICT, local, provider.detail()
            );
            default -> quarantine(
                    store,
                    local,
                    local.providerRevision(),
                    "Market provider returned " + provider.status() + " without an operation record",
                    now
            );
        };
    }

    MarketCoordinationResult blacklist(
            MarketComplianceStore store,
            MarketComplianceOperation local,
            MarketBlacklistResult provider,
            Instant now
    ) {
        Objects.requireNonNull(provider, "provider");
        StallBlacklistState record = provider.blacklist().orElse(null);
        if (record != null && !matches(local, record)) {
            return quarantine(store, local, record.revision(), "Market blacklist identity mismatch", now);
        }
        return switch (provider.status()) {
            case APPLIED, REMOVED, REPLAYED -> record == null
                    ? quarantine(
                            store,
                            local,
                            local.providerRevision(),
                            "Market provider returned " + provider.status() + " without a blacklist record",
                            now
                    )
                    : persist(store, local, blacklistUpdate(record, provider.detail(), now));
            case REJECTED -> persistTerminal(
                    store, local, MarketComplianceState.REJECTED, provider.detail(), now
            );
            case CONFLICT -> persistTerminal(
                    store, local, MarketComplianceState.CONFLICT, provider.detail(), now
            );
        };
    }

    MarketCoordinationResult quarantine(
            MarketComplianceStore store,
            MarketComplianceOperation local,
            long providerRevision,
            String detail,
            Instant now
    ) {
        return persist(
                store,
                local,
                new MarketComplianceUpdate(
                        MarketComplianceState.QUARANTINED,
                        local.reviewedBy(),
                        local.snapshotChecksum(),
                        local.currentChecksum(),
                        Math.max(providerRevision, local.providerRevision()),
                        bounded(detail),
                        now
                )
        );
    }

    private MarketCoordinationResult persistTerminal(
            MarketComplianceStore store,
            MarketComplianceOperation local,
            MarketComplianceState state,
            String detail,
            Instant now
    ) {
        return persist(
                store,
                local,
                new MarketComplianceUpdate(
                        state,
                        local.reviewedBy(),
                        local.snapshotChecksum(),
                        local.currentChecksum(),
                        local.providerRevision(),
                        bounded(detail),
                        now
                )
        );
    }

    private MarketCoordinationResult persist(
            MarketComplianceStore store,
            MarketComplianceOperation local,
            MarketComplianceUpdate update
    ) {
        MarketComplianceResult saved = store.update(
                local.operationId(),
                local.journalRevision(),
                update
        );
        if (saved.status() == MarketComplianceResult.Status.STALE) {
            MarketComplianceOperation current = store.find(local.operationId()).orElse(null);
            if (current != null && !current.equals(local)) {
                saved = store.update(current.operationId(), current.journalRevision(), update);
            }
        }
        MarketComplianceOperation operation = saved.operation().orElse(local);
        return switch (saved.status()) {
            case CREATED, UPDATED -> result(status(update.state()), operation, saved.detail());
            case REPLAYED -> result(MarketCoordinationResult.Status.REPLAYED, operation, saved.detail());
            case CONFLICT, STALE -> result(
                    MarketCoordinationResult.Status.CONFLICT, operation, saved.detail()
            );
            case NOT_FOUND -> new MarketCoordinationResult(
                    MarketCoordinationResult.Status.QUARANTINED,
                    Optional.empty(),
                    "Staff market journal row disappeared during reconciliation"
            );
        };
    }

    private static MarketComplianceUpdate operationUpdate(
            MarketOperationRecord provider,
            String detail,
            Instant now
    ) {
        return new MarketComplianceUpdate(
                switch (provider.state()) {
                    case PREPARED -> MarketComplianceState.PREPARED;
                    case MODERATION_HOLD -> MarketComplianceState.MODERATION_HOLD;
                    case RESTORED -> MarketComplianceState.RESTORED;
                    case RELEASED -> MarketComplianceState.RELEASED;
                    case QUARANTINED -> MarketComplianceState.QUARANTINED;
                },
                provider.reviewerId(),
                Optional.of(provider.snapshotChecksum()),
                provider.currentChecksum(),
                provider.revision(),
                bounded(detail),
                now
        );
    }

    private static MarketComplianceUpdate blacklistUpdate(
            StallBlacklistState provider,
            String detail,
            Instant now
    ) {
        return new MarketComplianceUpdate(
                provider.status() == StallBlacklistState.Status.ACTIVE
                        ? MarketComplianceState.BLACKLIST_ACTIVE
                        : MarketComplianceState.BLACKLIST_REMOVED,
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                provider.revision(),
                bounded(detail),
                now
        );
    }

    private static boolean matches(
            MarketComplianceOperation local,
            MarketOperationRecord provider
    ) {
        return local.operationId().equals(provider.operationId())
                && local.request().targetId().equals(provider.targetId())
                && local.request().caseId().value().equals(provider.caseId())
                && local.request().stallId().filter(provider.stallId()::equals).isPresent()
                && local.request().reviewDueAt().equals(provider.reviewDueAt())
                && local.request().recoveryUntil().equals(provider.recoveryUntil())
                && local.snapshotChecksum()
                        .map(provider.snapshotChecksum()::equals)
                        .orElse(true)
                && local.reviewedBy()
                        .map(reviewer -> provider.reviewerId().filter(reviewer::equals).isPresent())
                        .orElse(true);
    }

    private static boolean matches(
            MarketComplianceOperation local,
            StallBlacklistState provider
    ) {
        if (!local.operationId().equals(provider.operationId())
                || !local.request().targetId().equals(provider.playerId())
                || !local.request().caseId().value().equals(provider.caseId())) {
            return false;
        }
        if (local.request().kind() == MarketComplianceKind.BLACKLIST_APPLY) {
            return provider.status() == StallBlacklistState.Status.ACTIVE
                    && local.request().blacklistExpiresAt().equals(provider.expiresAt());
        }
        return local.request().kind() == MarketComplianceKind.BLACKLIST_REMOVE
                && provider.status() == StallBlacklistState.Status.REMOVED
                && provider.expiresAt().isEmpty();
    }

    private static MarketCoordinationResult.Status status(MarketComplianceState state) {
        return state == MarketComplianceState.QUARANTINED
                ? MarketCoordinationResult.Status.QUARANTINED
                : MarketCoordinationResult.Status.UPDATED;
    }

    private static MarketCoordinationResult result(
            MarketCoordinationResult.Status status,
            MarketComplianceOperation operation,
            String detail
    ) {
        return new MarketCoordinationResult(status, Optional.of(operation), bounded(detail));
    }

    private static String bounded(String detail) {
        if (detail == null || detail.isBlank()) {
            return "Market provider supplied no detail";
        }
        return detail.length() <= 512 ? detail : detail.substring(0, 512);
    }
}
