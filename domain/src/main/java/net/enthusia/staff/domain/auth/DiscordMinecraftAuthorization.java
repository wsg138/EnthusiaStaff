package net.enthusia.staff.domain.auth;

import java.util.Map;
import java.util.Set;
import net.enthusia.staff.domain.moderation.ModerationPlatform;

final class DiscordMinecraftAuthorization {
    private static final Map<DiscordModerationOperation, ModerationAction> REQUIRED_ACTION = Map.of(
            DiscordModerationOperation.ISSUE_SANCTION, ModerationAction.ISSUE_POLICY_SANCTION,
            DiscordModerationOperation.END_SANCTION, ModerationAction.END_SANCTION,
            DiscordModerationOperation.REVOKE_SANCTION, ModerationAction.REVOKE_SANCTION,
            DiscordModerationOperation.APPROVE_SANCTION_REQUEST, ModerationAction.APPROVE_POLICY_SANCTION,
            DiscordModerationOperation.REQUEST_OVERTURN, ModerationAction.REQUEST_FULL_OVERTURN,
            DiscordModerationOperation.APPROVE_OVERTURN, ModerationAction.APPROVE_OVERTURN,
            DiscordModerationOperation.FULL_OVERTURN, ModerationAction.FULL_OVERTURN
    );

    private final AuthorizationPolicy authorization;

    DiscordMinecraftAuthorization(AuthorizationPolicy authorization) {
        this.authorization = authorization;
    }

    DiscordAuthorizationDecision authorize(Actor actor, DiscordAuthorizationRequest request) {
        if (!isMinecraftMutation(request)) {
            return DiscordAuthorizationDecision.allow(Set.of());
        }
        if (actor.rank() == StaffRank.HELPER) {
            return DiscordAuthorizationDecision.deny(DiscordAuthorizationDenial.HELPER_CROSS_PLATFORM_FORBIDDEN);
        }
        ModerationAction required = REQUIRED_ACTION.get(request.operation());
        if (required == null || !authorization.permits(actor, required)) {
            return DiscordAuthorizationDecision.deny(DiscordAuthorizationDenial.MINECRAFT_AUTHORIZATION_DENIED);
        }
        return customMinecraftAuthority(actor, request)
                ? DiscordAuthorizationDecision.allow(Set.of())
                : DiscordAuthorizationDecision.deny(DiscordAuthorizationDenial.MINECRAFT_AUTHORIZATION_DENIED);
    }

    private boolean customMinecraftAuthority(Actor actor, DiscordAuthorizationRequest request) {
        return request.consequences().stream()
                .filter(intent -> intent.platform() == ModerationPlatform.MINECRAFT)
                .allMatch(intent -> customAuthority(actor, intent));
    }

    private boolean customAuthority(Actor actor, DiscordConsequenceIntent intent) {
        boolean durationAllowed = !intent.customDuration()
                || authorization.permits(actor, ModerationAction.USE_CUSTOM_DURATION);
        boolean consequenceAllowed = !intent.customConsequence()
                || authorization.permits(actor, ModerationAction.USE_CUSTOM_COMBINATION);
        return durationAllowed && consequenceAllowed;
    }

    private static boolean isMinecraftMutation(DiscordAuthorizationRequest request) {
        return request.operation().isMutation()
                && request.platforms().contains(ModerationPlatform.MINECRAFT);
    }
}
