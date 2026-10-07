package net.enthusia.staff.domain.policyv2;

import java.util.List;
import net.enthusia.staff.domain.sanction.SanctionSpec;

public sealed interface CaseRevision permits CaseRevision.SanctionRevision,
        CaseRevision.FindingReclassification, CaseRevision.FindingOverturn {
    String reason();

    record SanctionRevision(String reason, List<SanctionSpec> replacementSanctions) implements CaseRevision {
        public SanctionRevision {
            reason = requireReason(reason);
            if (replacementSanctions == null
                    || replacementSanctions.stream().anyMatch(java.util.Objects::isNull)) {
                throw new IllegalArgumentException("replacement sanctions must be present");
            }
            replacementSanctions = List.copyOf(replacementSanctions);
        }
    }

    record FindingReclassification(
            String fromOffenseId,
            String toOffenseId,
            String reason
    ) implements CaseRevision {
        public FindingReclassification {
            fromOffenseId = PolicyIds.require(fromOffenseId, "source offense id");
            toOffenseId = PolicyIds.require(toOffenseId, "replacement offense id");
            if (fromOffenseId.equals(toOffenseId)) {
                throw new IllegalArgumentException("reclassification must change the finding");
            }
            reason = requireReason(reason);
        }
    }

    record FindingOverturn(String offenseId, String reason) implements CaseRevision {
        public FindingOverturn {
            offenseId = PolicyIds.require(offenseId, "overturned offense id");
            reason = requireReason(reason);
        }
    }

    private static String requireReason(String reason) {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("revision reason must not be blank");
        }
        return reason.trim();
    }
}
