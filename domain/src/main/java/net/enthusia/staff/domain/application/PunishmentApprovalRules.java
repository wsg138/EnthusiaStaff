package net.enthusia.staff.domain.application;

import java.util.List;
import java.util.Objects;
import net.enthusia.staff.domain.auth.StaffRank;
import net.enthusia.staff.domain.sanction.SanctionSpec;

final class PunishmentApprovalRules {
    private PunishmentApprovalRules() {
    }

    static boolean requiresApproval(StaffRank actorRank, List<SanctionSpec> sanctions) {
        Objects.requireNonNull(actorRank);
        Objects.requireNonNull(sanctions);
        if (actorRank == StaffRank.DEVELOPER) {
            return true;
        }
        return actorRank == StaffRank.HELPER && sanctions.stream()
                .anyMatch(specification -> specification.length().isPermanent());
    }
}
