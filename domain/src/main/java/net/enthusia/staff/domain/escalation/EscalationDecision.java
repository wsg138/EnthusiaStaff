package net.enthusia.staff.domain.escalation;

import java.util.List;

public record EscalationDecision(
        int rawOrdinal,
        int effectiveOrdinal,
        int recencyBonus,
        List<Contribution> contributions,
        DecayEligibility resultingOffenseDecayEligibility,
        PunishmentStep selectedStep
) {
    public EscalationDecision {
        contributions = List.copyOf(contributions);
        if (rawOrdinal < 0 || effectiveOrdinal < 0 || recencyBonus < 0
                || resultingOffenseDecayEligibility == null || selectedStep == null) {
            throw new IllegalArgumentException("invalid escalation decision");
        }
    }

    /**
     * Compatibility constructor for pre-snapshot callers. Production policy evaluation always supplies
     * an explicit value; callers without one remain UNKNOWN and are persisted as legacy-unspecified.
     */
    public EscalationDecision(
            int rawOrdinal,
            int effectiveOrdinal,
            int recencyBonus,
            List<Contribution> contributions,
            PunishmentStep selectedStep
    ) {
        this(
                rawOrdinal,
                effectiveOrdinal,
                recencyBonus,
                contributions,
                DecayEligibility.UNKNOWN,
                selectedStep
        );
    }

    public record Contribution(
            int priorSeverity,
            int base,
            DecayEligibility decayEligibility,
            int decayedBy,
            int effective
    ) {
        public Contribution {
            if (priorSeverity < 0 || base < 0 || decayEligibility == null || decayedBy < 0 || effective < 0) {
                throw new IllegalArgumentException("invalid escalation contribution");
            }
        }
    }
}
