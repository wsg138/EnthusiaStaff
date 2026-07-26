package net.enthusia.staff.domain.application;

import net.enthusia.staff.domain.OperationalMode;
import net.enthusia.staff.domain.auth.AuthorizationPolicy;
import net.enthusia.staff.domain.auth.ModerationAction;
import net.enthusia.staff.domain.ports.SanctionMutationStore;
import net.enthusia.staff.domain.sanction.SanctionChangeAction;
import net.enthusia.staff.domain.sanction.SanctionChangeRequest;
import net.enthusia.staff.domain.sanction.SanctionChangeResult;

public final class SanctionChangeService {
    private final AuthorizationPolicy authorization;
    private final SanctionMutationStore store;

    public SanctionChangeService(AuthorizationPolicy authorization, SanctionMutationStore store) {
        if (authorization == null || store == null) {
            throw new IllegalArgumentException("sanction change dependencies must be present");
        }
        this.authorization = authorization;
        this.store = store;
    }

    public SanctionChangeResult apply(SanctionChangeRequest request, OperationalMode mode) {
        if (request == null || mode == null) {
            throw new IllegalArgumentException("request and mode must be present");
        }
        if (mode != OperationalMode.ACTIVE) {
            return new SanctionChangeResult.Rejected("MODE_BLOCKED", "Sanction changes are disabled in " + mode);
        }
        ModerationAction required = requiredPermission(request.action());
        if (!authorization.permits(request.actor(), required)) {
            return new SanctionChangeResult.Rejected("FORBIDDEN", "The actor is not permitted to perform this change");
        }
        return store.apply(request);
    }

    private static ModerationAction requiredPermission(SanctionChangeAction action) {
        return switch (action) {
            case END_EARLY -> ModerationAction.END_SANCTION;
            case REVOKE, REMOVE_ESCALATION_CONTRIBUTION, RESTORE_ESCALATION_CONTRIBUTION ->
                    ModerationAction.REVOKE_SANCTION;
            case REDUCE_DURATION, REPLACE_EXPIRATION -> ModerationAction.USE_CUSTOM_DURATION;
            case FULL_OVERTURN -> ModerationAction.FULL_OVERTURN;
            case REQUEST_FULL_OVERTURN -> ModerationAction.REQUEST_FULL_OVERTURN;
            case APPROVE_FULL_OVERTURN, DENY_FULL_OVERTURN -> ModerationAction.APPROVE_OVERTURN;
        };
    }
}
