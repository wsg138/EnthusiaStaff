package net.enthusia.staff.domain.application;

import java.util.List;
import java.util.Objects;
import net.enthusia.staff.domain.auth.StaffRank;
import net.enthusia.staff.domain.sanction.SanctionSpec;

final class PunishmentApprovalRules {
    private PunishmentApprovalRules() {
    }

    /**
     * Helpers are request-only: every punishment they propose routes to Mod-or-above approval,
     * regardless of sanction severity. They can never issue a punishment directly.
     *
     * <p>Owner-mandated: Admins may request custom durations, but they require
     * Founder approval. Founders may apply custom durations directly.
     */
    static boolean requiresApproval(StaffRank actorRank, List<SanctionSpec> sanctions) {
        Objects.requireNonNull(actorRank);
        Objects.requireNonNull(sanctions);
        if (actorRank == StaffRank.DEVELOPER) {
            return true;
        }
        return actorRank == StaffRank.HELPER;
    }

    /**
     * Owner-mandated: Admins using custom durations require Founder approval.
     * The caller must have already determined that the sanctions use a custom
     * duration (via {@link #isCustomDuration}).
     *
     * @return the minimum rank required to approve, or null if no approval needed
     */
    static StaffRank requiredApprovalRank(StaffRank actorRank, boolean customDuration) {
        Objects.requireNonNull(actorRank);
        if (actorRank == StaffRank.HELPER || actorRank == StaffRank.DEVELOPER) {
            return StaffRank.MOD;
        }
        if (actorRank == StaffRank.ADMIN && customDuration) {
            return StaffRank.FOUNDER;
        }
        return null;
    }

    /**
     * Determines whether the given sanctions use a custom duration (same sanction
     * types as a configured step, but different durations).
     */
    static boolean isCustomDuration(
            List<SanctionSpec> sanctions,
            net.enthusia.staff.domain.escalation.ReasonPolicy policy
    ) {
        Objects.requireNonNull(sanctions);
        Objects.requireNonNull(policy);
        // If sanctions exactly match a configured step, not custom.
        boolean exactMatch = policy.steps().stream()
                .anyMatch(step -> step.sanctions().equals(sanctions));
        if (exactMatch) {
            return false;
        }
        // If same type shape as a configured step (but different durations), it's custom.
        return policy.steps().stream()
                .map(step -> step.sanctions())
                .anyMatch(configured -> sameTypeShape(configured, sanctions));
    }

    private static boolean sameTypeShape(List<SanctionSpec> left, List<SanctionSpec> right) {
        if (left.size() != right.size()) {
            return false;
        }
        for (int i = 0; i < left.size(); i++) {
            if (left.get(i).type() != right.get(i).type()) {
                return false;
            }
        }
        return true;
    }
}
