package net.enthusia.staff.domain.application;

import net.enthusia.staff.domain.OperationalMode;
import net.enthusia.staff.domain.auth.AuthorizationPolicy;
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
        if (!authorization.permits(request.actor(), request.action().requiredModerationAction())) {
            return new SanctionChangeResult.Rejected("FORBIDDEN", "The actor is not permitted to perform this change");
        }
        return store.apply(request);
    }

}
