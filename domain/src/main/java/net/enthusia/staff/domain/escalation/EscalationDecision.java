package net.enthusia.staff.domain.escalation;

import java.util.List;

public record EscalationDecision(
        int rawOrdinal,
        int effectiveOrdinal,
        int recencyBonus,
        List<Contribution> contributions,
        PunishmentStep selectedStep
) {
    public EscalationDecision {
        contributions = List.copyOf(contributions);
        if (rawOrdinal < 0 || effectiveOrdinal < 0 || recencyBonus < 0 || selectedStep == null) {
            throw new IllegalArgumentException("invalid escalation decision");
        }
    }

    public record Contribution(int priorSeverity, int base, int decayedBy, int effective) {
        public Contribution {
            if (priorSeverity < 0 || base < 0 || decayedBy < 0 || effective < 0) {
                throw new IllegalArgumentException("invalid escalation contribution");
            }
        }
    }
}
