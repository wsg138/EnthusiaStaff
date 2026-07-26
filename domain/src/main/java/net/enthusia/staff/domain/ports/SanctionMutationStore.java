package net.enthusia.staff.domain.ports;

import net.enthusia.staff.domain.sanction.SanctionChangeRequest;
import net.enthusia.staff.domain.sanction.SanctionChangeResult;

public interface SanctionMutationStore {
    SanctionChangeResult apply(SanctionChangeRequest request);
}
