package net.enthusia.staff.domain.ports;

import net.enthusia.staff.domain.sanction.ExactSanctionChangeRequest;
import net.enthusia.staff.domain.sanction.ExactSanctionChangeResult;
import net.enthusia.staff.domain.sanction.SanctionActionLimits;
import net.enthusia.staff.domain.sanction.SanctionChangeRequest;
import net.enthusia.staff.domain.sanction.SanctionChangeResult;

public interface SanctionMutationStore {
    SanctionChangeResult apply(SanctionChangeRequest request);

    ExactSanctionChangeResult applyExact(
            ExactSanctionChangeRequest request,
            SanctionActionLimits limits
    );
}
