package net.enthusia.staff.domain.ports;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.enthusia.staff.common.CaseId;
import net.enthusia.staff.domain.sanction.SanctionType;

public interface CaseLookup {
    Optional<CaseId> latestCase(UUID targetId, Set<SanctionType> types, boolean activeOnly);

    Optional<UUID> target(CaseId caseId);

    boolean containsSanction(CaseId caseId, Set<SanctionType> types, boolean activeOnly);

    boolean exists(CaseId caseId);
}
