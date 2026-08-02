package net.enthusia.staff.domain.application;

import net.enthusia.staff.domain.OperationalMode;
import net.enthusia.staff.domain.auth.AuthorizationPolicy;
import net.enthusia.staff.domain.ports.SanctionMutationStore;
import net.enthusia.staff.domain.sanction.ExactSanctionChangeRequest;
import net.enthusia.staff.domain.sanction.ExactSanctionChangeResult;
import net.enthusia.staff.domain.sanction.SanctionActionLimits;
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

    public ExactSanctionChangeResult applyExact(
            ExactSanctionChangeRequest request,
            OperationalMode mode,
            SanctionActionLimits limits
    ) {
        if (request == null || mode == null || limits == null) {
            throw new IllegalArgumentException("exact sanction change request, mode, and limits must be present");
        }
        if (mode != OperationalMode.ACTIVE) {
            return new ExactSanctionChangeResult.Rejected(
                    "MODE_BLOCKED",
                    "Sanction changes are disabled in " + mode
            );
        }
        if (!authorization.permits(request.actor(), request.action().requiredModerationAction())) {
            return new ExactSanctionChangeResult.Rejected(
                    "FORBIDDEN",
                    "The actor is not permitted to perform this change"
            );
        }
        if (!limits.accepts(request.reason())) {
            return new ExactSanctionChangeResult.Rejected(
                    "INVALID_REASON",
                    "The reason length is outside the configured limits"
            );
        }
        return store.applyExact(request, limits);
    }
}
