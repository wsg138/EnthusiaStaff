package net.enthusia.staff.domain.auth;

import java.util.EnumSet;
import java.util.Set;
import net.enthusia.staff.domain.moderation.ModerationPlatform;

final class DiscordPreconditionPolicy {
    private static final Set<DiscordModerationOperation> ROLE_HIERARCHY_OPERATIONS = Set.of(
            DiscordModerationOperation.END_SANCTION,
            DiscordModerationOperation.REVOKE_SANCTION,
            DiscordModerationOperation.APPROVE_SANCTION_REQUEST,
            DiscordModerationOperation.APPROVE_OVERTURN,
            DiscordModerationOperation.FULL_OVERTURN
    );

    private DiscordPreconditionPolicy() {
    }

    static Set<DiscordEnforcementPrecondition> required(DiscordAuthorizationRequest request) {
        EnumSet<DiscordEnforcementPrecondition> required = EnumSet.noneOf(DiscordEnforcementPrecondition.class);
        if (isMinecraftMutation(request)) {
            required.add(DiscordEnforcementPrecondition.MINECRAFT_PUNISHMENT_POLICY_REVALIDATION);
        }
        if (requiresDiscordRoleHierarchy(request)) {
            required.add(DiscordEnforcementPrecondition.DISCORD_ROLE_HIERARCHY);
        }
        return required;
    }

    private static boolean isMinecraftMutation(DiscordAuthorizationRequest request) {
        return request.operation().isMutation()
                && request.platforms().contains(ModerationPlatform.MINECRAFT);
    }

    private static boolean requiresDiscordRoleHierarchy(DiscordAuthorizationRequest request) {
        if (!isDiscordMutation(request)) {
            return false;
        }
        return request.operation() == DiscordModerationOperation.ISSUE_SANCTION
                ? hasRoleSensitiveDiscordConsequence(request)
                : ROLE_HIERARCHY_OPERATIONS.contains(request.operation());
    }

    private static boolean isDiscordMutation(DiscordAuthorizationRequest request) {
        return request.operation().isMutation()
                && request.platforms().contains(ModerationPlatform.DISCORD);
    }

    private static boolean hasRoleSensitiveDiscordConsequence(DiscordAuthorizationRequest request) {
        return request.consequences().stream()
                .filter(intent -> intent.platform() == ModerationPlatform.DISCORD)
                .anyMatch(intent -> intent.type() != DiscordConsequenceType.WARNING);
    }
}
