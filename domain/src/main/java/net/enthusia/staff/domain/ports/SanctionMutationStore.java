package net.enthusia.staff.domain.ports;

import net.enthusia.staff.domain.sanction.ExactSanctionChangeRequest;
import net.enthusia.staff.domain.sanction.ExactSanctionChangeResult;
import net.enthusia.staff.domain.sanction.SanctionActionLimits;
import net.enthusia.staff.domain.sanction.SanctionChangeRequest;
import net.enthusia.staff.domain.sanction.SanctionChangeResult;
import java.util.OptionalLong;
import java.util.UUID;

public interface SanctionMutationStore {
    SanctionChangeResult apply(SanctionChangeRequest request);

    default OptionalLong exactRevision(UUID sanctionId) {
        return OptionalLong.empty();
    }

    default ExactSanctionChangeResult applyExact(
            ExactSanctionChangeRequest request,
            SanctionActionLimits limits
    ) {
        return new ExactSanctionChangeResult.Rejected(
                "UNSUPPORTED",
                "Exact sanction changes are not available from this store"
        );
    }
}
