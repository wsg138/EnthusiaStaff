package net.enthusia.staff.domain.application;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.enthusia.staff.domain.auth.StaffRank;
import net.enthusia.staff.domain.escalation.PunishmentStep;
import net.enthusia.staff.domain.escalation.ReasonPolicy;
import net.enthusia.staff.domain.sanction.SanctionSpec;
import net.enthusia.staff.domain.sanction.SanctionType;

final class PunishmentApprovalRules {
    private PunishmentApprovalRules() {
    }

    static boolean requiresApproval(
            StaffRank actorRank,
            List<SanctionSpec> sanctions,
            List<SanctionSpec> selectedStepSanctions
    ) {
        Objects.requireNonNull(actorRank);
        Objects.requireNonNull(sanctions);
        Objects.requireNonNull(selectedStepSanctions);
        return requiresApproval(actorRank, isCustomDuration(selectedStepSanctions, sanctions));
    }

    /**
     * Compatibility overload for configured sanctions, which carry no custom-duration intent.
     */
    static boolean requiresApproval(StaffRank actorRank, List<SanctionSpec> configuredSanctions) {
        Objects.requireNonNull(configuredSanctions);
        return requiresApproval(actorRank, false);
    }

    static boolean requiresApproval(StaffRank actorRank, boolean customDuration) {
        Objects.requireNonNull(actorRank);
        if (actorRank == StaffRank.DEVELOPER || actorRank == StaffRank.HELPER) {
            return true;
        }
        return actorRank == StaffRank.ADMIN && customDuration;
    }

    static StaffRank requiredApprovalRank(
            StaffRank requesterRank,
            StaffRank policyRequiredRank,
            boolean customDuration
    ) {
        Objects.requireNonNull(requesterRank);
        Objects.requireNonNull(policyRequiredRank);
        if (requesterRank == StaffRank.ADMIN && customDuration) {
            return StaffRank.FOUNDER;
        }
        return policyRequiredRank;
    }

    static boolean isCustomDuration(ReasonPolicy policy, List<SanctionSpec> requested) {
        Objects.requireNonNull(policy);
        Objects.requireNonNull(requested);
        boolean exactConfiguredStep = policy.steps().stream()
                .map(PunishmentStep::sanctions)
                .anyMatch(requested::equals);
        if (exactConfiguredStep) {
            return false;
        }
        return policy.steps().stream()
                .map(PunishmentStep::sanctions)
                .anyMatch(configured -> sameTypeShape(configured, requested));
    }

    static boolean isCustomDuration(
            List<SanctionSpec> configured,
            List<SanctionSpec> requested
    ) {
        Objects.requireNonNull(configured);
        Objects.requireNonNull(requested);
        return !configured.equals(requested) && sameTypeShape(configured, requested);
    }

    static boolean sameTypeShape(List<SanctionSpec> left, List<SanctionSpec> right) {
        return typeCounts(left).equals(typeCounts(right));
    }

    private static Map<SanctionType, Integer> typeCounts(List<SanctionSpec> sanctions) {
        Map<SanctionType, Integer> counts = new EnumMap<>(SanctionType.class);
        sanctions.forEach(spec -> counts.merge(spec.type(), 1, Integer::sum));
        return counts;
    }
}
