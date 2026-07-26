package net.enthusia.staff.domain.auth;

public final class DefaultAuthorizationPolicy implements AuthorizationPolicy {
    @Override
    public boolean permits(Actor actor, ModerationAction action) {
        if (actor == null || action == null) {
            return false;
        }
        if (actor.rank() == StaffRank.SYSTEM) {
            return action == ModerationAction.ISSUE_POLICY_SANCTION;
        }
        return switch (action) {
            case ISSUE_POLICY_SANCTION, LOWER_RECOMMENDATION, END_SANCTION,
                    REVOKE_SANCTION, REQUEST_FULL_OVERTURN, RESTORE_ASSETS -> true;
            case RAISE_RECOMMENDATION, USE_CUSTOM_DURATION, FULL_OVERTURN,
                    APPROVE_OVERTURN -> actor.rank().atLeast(StaffRank.ADMIN);
            case USE_CUSTOM_COMBINATION, OWNER_RECOVERY -> actor.rank().atLeast(StaffRank.FOUNDER);
        };
    }
}
