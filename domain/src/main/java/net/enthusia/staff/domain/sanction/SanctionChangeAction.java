package net.enthusia.staff.domain.sanction;

import net.enthusia.staff.domain.auth.ModerationAction;

public enum SanctionChangeAction {
    END_EARLY,
    REDUCE_DURATION,
    REPLACE_EXPIRATION,
    REVOKE,
    FULL_OVERTURN,
    REMOVE_ESCALATION_CONTRIBUTION,
    RESTORE_ESCALATION_CONTRIBUTION,
    REQUEST_FULL_OVERTURN,
    APPROVE_FULL_OVERTURN,
    DENY_FULL_OVERTURN;

    public ModerationAction requiredModerationAction() {
        return switch (this) {
            case END_EARLY -> ModerationAction.END_SANCTION;
            case REVOKE -> ModerationAction.REVOKE_SANCTION;
            case REDUCE_DURATION, REMOVE_ESCALATION_CONTRIBUTION -> ModerationAction.LOWER_RECOMMENDATION;
            case REPLACE_EXPIRATION -> ModerationAction.USE_CUSTOM_DURATION;
            case RESTORE_ESCALATION_CONTRIBUTION -> ModerationAction.RAISE_RECOMMENDATION;
            case FULL_OVERTURN -> ModerationAction.FULL_OVERTURN;
            case REQUEST_FULL_OVERTURN -> ModerationAction.REQUEST_FULL_OVERTURN;
            case APPROVE_FULL_OVERTURN, DENY_FULL_OVERTURN -> ModerationAction.APPROVE_OVERTURN;
        };
    }
}
