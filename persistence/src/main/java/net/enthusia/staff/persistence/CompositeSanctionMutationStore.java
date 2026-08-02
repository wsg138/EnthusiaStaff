package net.enthusia.staff.persistence;

import net.enthusia.staff.domain.ports.SanctionMutationStore;
import net.enthusia.staff.domain.sanction.ExactSanctionChangeRequest;
import net.enthusia.staff.domain.sanction.ExactSanctionChangeResult;
import net.enthusia.staff.domain.sanction.SanctionActionLimits;
import net.enthusia.staff.domain.sanction.SanctionChangeRequest;
import net.enthusia.staff.domain.sanction.SanctionChangeResult;

final class CompositeSanctionMutationStore implements SanctionMutationStore {
    private final SanctionMutationStore caseWide;
    private final SanctionMutationStore exact;

    CompositeSanctionMutationStore(
            SanctionMutationStore caseWide,
            SanctionMutationStore exact
    ) {
        if (caseWide == null || exact == null) {
            throw new IllegalArgumentException("sanction mutation delegates must be present");
        }
        this.caseWide = caseWide;
        this.exact = exact;
    }

    @Override
    public SanctionChangeResult apply(SanctionChangeRequest request) {
        return caseWide.apply(request);
    }

    @Override
    public ExactSanctionChangeResult applyExact(
            ExactSanctionChangeRequest request,
            SanctionActionLimits limits
    ) {
        return exact.applyExact(request, limits);
    }
}
