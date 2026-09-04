package net.enthusia.staff.domain.auth;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

final class DiscordOperationPolicy {
    private static final Map<StaffRank, Set<DiscordModerationOperation>> ALLOWED = Map.of(
            StaffRank.HELPER, withReads(DiscordModerationOperation.ISSUE_SANCTION),
            StaffRank.MOD, withReads(
                    DiscordModerationOperation.ISSUE_SANCTION,
                    DiscordModerationOperation.END_SANCTION,
                    DiscordModerationOperation.REVOKE_SANCTION,
                    DiscordModerationOperation.APPROVE_SANCTION_REQUEST,
                    DiscordModerationOperation.REQUEST_OVERTURN
            ),
            StaffRank.DEVELOPER, withReads(
                    DiscordModerationOperation.ISSUE_SANCTION,
                    DiscordModerationOperation.END_SANCTION,
                    DiscordModerationOperation.REVOKE_SANCTION,
                    DiscordModerationOperation.APPROVE_SANCTION_REQUEST,
                    DiscordModerationOperation.REQUEST_OVERTURN
            ),
            StaffRank.ADMIN, Set.copyOf(EnumSet.allOf(DiscordModerationOperation.class)),
            StaffRank.FOUNDER, Set.copyOf(EnumSet.allOf(DiscordModerationOperation.class)),
            StaffRank.SYSTEM, Set.of()
    );

    private static final Map<StaffRank, Integer> AUTHORITY_LEVEL = Map.of(
            StaffRank.HELPER, 10,
            StaffRank.MOD, 20,
            StaffRank.DEVELOPER, 20,
            StaffRank.ADMIN, 30,
            StaffRank.FOUNDER, 40,
            StaffRank.SYSTEM, 50
    );

    boolean permits(StaffRank rank, DiscordModerationOperation operation) {
        Set<DiscordModerationOperation> allowed = ALLOWED.get(rank);
        return allowed != null && allowed.contains(operation);
    }

    DiscordAuthorizationDecision authorizeTarget(
            Actor actor,
            Optional<Actor> targetStaff,
            DiscordModerationOperation operation
    ) {
        if (!operation.isMutation() || targetStaff.isEmpty()) {
            return DiscordAuthorizationDecision.allow(Set.of());
        }
        Actor target = targetStaff.orElseThrow();
        if (actor.id().equals(target.id())) {
            return DiscordAuthorizationDecision.deny(DiscordAuthorizationDenial.SELF_TARGET);
        }
        return isProtected(actor.rank(), target.rank())
                ? DiscordAuthorizationDecision.deny(DiscordAuthorizationDenial.TARGET_STAFF_PROTECTED)
                : DiscordAuthorizationDecision.allow(Set.of());
    }

    private static boolean isProtected(StaffRank actor, StaffRank target) {
        return AUTHORITY_LEVEL.get(actor) <= AUTHORITY_LEVEL.get(target);
    }

    private static Set<DiscordModerationOperation> withReads(DiscordModerationOperation... operations) {
        EnumSet<DiscordModerationOperation> allowed = EnumSet.of(
                DiscordModerationOperation.VIEW_LINKED_ACCOUNTS,
                DiscordModerationOperation.VIEW_HISTORY,
                DiscordModerationOperation.VIEW_NOTES,
                DiscordModerationOperation.VIEW_EVIDENCE
        );
        allowed.addAll(List.of(operations));
        return Set.copyOf(allowed);
    }
}
