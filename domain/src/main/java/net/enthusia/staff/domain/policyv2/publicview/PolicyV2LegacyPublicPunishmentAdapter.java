package net.enthusia.staff.domain.policyv2.publicview;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import net.enthusia.staff.common.CaseId;
import net.enthusia.staff.domain.policyv2.persistence.PolicyV2PublicProjection;
import net.enthusia.staff.domain.website.PublicPunishment;
import net.enthusia.staff.domain.website.PublicPunishmentState;

/**
 * Explicit compatibility adapter for the existing v1 public website contract.
 * Policy v2 remains inactive; this class does not change live routes.
 */
public final class PolicyV2LegacyPublicPunishmentAdapter {
    private PolicyV2LegacyPublicPunishmentAdapter() {
    }

    public static List<PublicPunishment> toLegacy(
            PolicyV2PublicProjection projection,
            Instant now
    ) {
        if (projection == null || now == null || projection.currentPlayerName().isEmpty()) {
            return List.of();
        }
        return projection.sanctions().stream()
                .map(sanction -> legacy(projection, sanction, now))
                .flatMap(Optional::stream)
                .toList();
    }

    private static Optional<PublicPunishment> legacy(
            PolicyV2PublicProjection projection,
            PolicyV2PublicProjection.PublicSanction sanction,
            Instant now
    ) {
        Optional<String> type = legacyType(sanction.type());
        if (type.isEmpty()) {
            return Optional.empty();
        }
        PublicPunishmentState state = legacyState(projection, sanction, now);
        return Optional.of(new PublicPunishment(
                projection.currentPlayerName().orElseThrow(),
                type.orElseThrow(),
                projection.category(),
                projection.publicReason(),
                projection.issuedAt(),
                sanction.endsAt(),
                remainingSeconds(sanction.endsAt(), state, now),
                state,
                new CaseId(projection.caseId()),
                appealAvailable(projection, type.orElseThrow(), state)
        ));
    }

    private static Optional<String> legacyType(PolicyV2PublicProjection.PublicSanctionType type) {
        return switch (type) {
            case BAN -> Optional.of("BAN");
            case MUTE -> Optional.of("MUTE");
            case WARNING -> Optional.of("WARNING");
            case KICK, REPORT_RESTRICTION, REPUTATION_RESTRICTION, MARKET_RESTRICTION ->
                    Optional.empty();
        };
    }

    private static PublicPunishmentState legacyState(
            PolicyV2PublicProjection projection,
            PolicyV2PublicProjection.PublicSanction sanction,
            Instant now
    ) {
        if (projection.status() == PolicyV2PublicProjection.Status.OVERTURNED
                || sanction.status() == PolicyV2PublicProjection.PublicSanction.SanctionStatus.REVOKED) {
            return PublicPunishmentState.REVOKED;
        }
        if (sanction.status() == PolicyV2PublicProjection.PublicSanction.SanctionStatus.COMPLETED
                || sanction.endsAt().filter(end -> !end.isAfter(now)).isPresent()) {
            return PublicPunishmentState.EXPIRED;
        }
        return PublicPunishmentState.ACTIVE;
    }

    private static OptionalLong remainingSeconds(
            Optional<Instant> expiresAt,
            PublicPunishmentState state,
            Instant now
    ) {
        if (expiresAt.isEmpty()) {
            return OptionalLong.empty();
        }
        long seconds = state == PublicPunishmentState.ACTIVE
                ? Math.max(0, Duration.between(now, expiresAt.orElseThrow()).toSeconds())
                : 0;
        return OptionalLong.of(seconds);
    }

    private static boolean appealAvailable(
            PolicyV2PublicProjection projection,
            String type,
            PublicPunishmentState state
    ) {
        boolean eligibleType = "BAN".equals(type) || "MUTE".equals(type);
        return projection.appealStatus() == PolicyV2PublicProjection.AppealStatus.AVAILABLE
                && state == PublicPunishmentState.ACTIVE
                && eligibleType;
    }
}
