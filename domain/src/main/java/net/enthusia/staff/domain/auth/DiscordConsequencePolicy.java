package net.enthusia.staff.domain.auth;

import java.time.Duration;
import java.util.Set;
import net.enthusia.staff.domain.moderation.ModerationPlatform;
import net.enthusia.staff.domain.sanction.SanctionLength;

final class DiscordConsequencePolicy {
    private static final Set<StaffRank> ELEVATED = Set.of(StaffRank.ADMIN, StaffRank.FOUNDER);
    private static final Set<StaffRank> MODERATOR_EQUIVALENT = Set.of(StaffRank.MOD, StaffRank.DEVELOPER);

    private final DiscordAuthorizationLimits limits;

    DiscordConsequencePolicy(DiscordAuthorizationLimits limits) {
        this.limits = limits;
    }

    DiscordAuthorizationDecision authorize(StaffRank rank, DiscordAuthorizationRequest request) {
        return request.consequences().stream()
                .filter(intent -> intent.platform() == ModerationPlatform.DISCORD)
                .map(intent -> authorize(rank, intent))
                .filter(decision -> !decision.permitted())
                .findFirst()
                .orElseGet(() -> DiscordAuthorizationDecision.allow(Set.of()));
    }

    private DiscordAuthorizationDecision authorize(StaffRank rank, DiscordConsequenceIntent intent) {
        if (ELEVATED.contains(rank)) {
            return DiscordAuthorizationDecision.allow(Set.of());
        }
        if (MODERATOR_EQUIVALENT.contains(rank)) {
            return moderator(intent);
        }
        return rank == StaffRank.HELPER
                ? helper(intent)
                : DiscordAuthorizationDecision.deny(DiscordAuthorizationDenial.UNAUTHORIZED_CONSEQUENCE);
    }

    private DiscordAuthorizationDecision helper(DiscordConsequenceIntent intent) {
        if (intent.customDuration()) {
            return DiscordAuthorizationDecision.deny(DiscordAuthorizationDenial.CUSTOM_DURATION_NOT_PERMITTED);
        }
        if (intent.customConsequence()) {
            return DiscordAuthorizationDecision.deny(DiscordAuthorizationDenial.CUSTOM_CONSEQUENCE_REQUIRES_ADMIN);
        }
        if (intent.type() == DiscordConsequenceType.WARNING) {
            return DiscordAuthorizationDecision.allow(Set.of());
        }
        if (intent.type() != DiscordConsequenceType.MUTE) {
            return DiscordAuthorizationDecision.deny(DiscordAuthorizationDenial.UNAUTHORIZED_CONSEQUENCE);
        }
        if (intent.length().isPermanent()) {
            return DiscordAuthorizationDecision.deny(DiscordAuthorizationDenial.PERMANENT_ACTION_REQUIRES_ADMIN);
        }
        return within(intent.length(), limits.helperMaxMute());
    }

    private DiscordAuthorizationDecision moderator(DiscordConsequenceIntent intent) {
        if (intent.customConsequence()) {
            return DiscordAuthorizationDecision.deny(DiscordAuthorizationDenial.CUSTOM_CONSEQUENCE_REQUIRES_ADMIN);
        }
        if (intent.length().isPermanent()) {
            return DiscordAuthorizationDecision.deny(DiscordAuthorizationDenial.PERMANENT_ACTION_REQUIRES_ADMIN);
        }
        return intent.length().kind() == SanctionLength.Kind.TEMPORARY
                ? within(intent.length(), limits.moderatorMaximum(intent.type()))
                : DiscordAuthorizationDecision.allow(Set.of());
    }

    private static DiscordAuthorizationDecision within(SanctionLength length, Duration maximum) {
        Duration duration = length.temporary().orElseThrow();
        return duration.compareTo(maximum) > 0
                ? DiscordAuthorizationDecision.deny(DiscordAuthorizationDenial.DURATION_EXCEEDS_LIMIT)
                : DiscordAuthorizationDecision.allow(Set.of());
    }
}
