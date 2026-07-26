package net.enthusia.staff.domain.auth;

public final class DefaultAuthorizationPolicy implements AuthorizationPolicy {
    @Override
    public boolean permits(Actor actor, ModerationAction action) {
        if (actor == null || action == null) {
            return false;
        }
        return switch (actor.rank()) {
            case SYSTEM -> action == ModerationAction.ISSUE_POLICY_SANCTION;
            case DEVELOPER -> false;
            case MOD -> switch (action) {
                case ISSUE_POLICY_SANCTION, LOWER_RECOMMENDATION, END_SANCTION,
                        REVOKE_SANCTION, REQUEST_FULL_OVERTURN, ACCEPT_APPEAL,
                        APPLY_CASE_CONFISCATION -> true;
                default -> false;
            };
            case ADMIN -> switch (action) {
                case ISSUE_POLICY_SANCTION, LOWER_RECOMMENDATION, RAISE_RECOMMENDATION,
                        USE_CUSTOM_DURATION, END_SANCTION, REVOKE_SANCTION, FULL_OVERTURN,
                        REQUEST_FULL_OVERTURN, APPROVE_OVERTURN, ACCEPT_APPEAL,
                        APPLY_CASE_CONFISCATION, MODIFY_MARKET_RESTRICTION,
                        MODIFY_REPUTATION_RESTRICTION -> true;
                default -> false;
            };
            case FOUNDER -> true;
        };
    }
}
