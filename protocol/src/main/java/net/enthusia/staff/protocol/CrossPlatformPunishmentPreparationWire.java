package net.enthusia.staff.protocol;

import java.util.List;
import java.util.UUID;
import net.enthusia.staff.domain.auth.StaffRank;
import net.enthusia.staff.domain.casefile.CaseVisibility;
import net.enthusia.staff.domain.escalation.DecayEligibility;
import net.enthusia.staff.domain.sanction.SanctionLength;
import net.enthusia.staff.domain.sanction.SanctionType;

/** Private allowlisted plan contract for an atomic Minecraft + Discord punishment. */
public final class CrossPlatformPunishmentPreparationWire {
    public static final int VERSION = 1;
    public static final String PATH = "/v1/staff-punishments/both-plan";
    public static final int MAX_BODY_BYTES = 8192;

    private CrossPlatformPunishmentPreparationWire() { }

    public record Request(int version, String caseId, String idempotencyKey, UUID actorId, String actorName,
            UUID targetId, String reasonId, String internalExplanation) {
        public Request {
            if (version != VERSION || blank(caseId) || blank(idempotencyKey) || actorId == null
                    || blank(actorName) || targetId == null || blank(reasonId) || internalExplanation == null
                    || internalExplanation.length() > 4000) {
                throw new IllegalArgumentException("cross-platform preparation request is invalid");
            }
        }
    }

    public record Response(int version, Outcome outcome, String code, String message, PreparedPlan plan) {
        public Response {
            if (version != VERSION || outcome == null || code == null || message == null
                    || ((outcome == Outcome.PREPARED) != (plan != null))
                    || (outcome == Outcome.REJECTED && blank(code))) {
                throw new IllegalArgumentException("cross-platform preparation response is invalid");
            }
        }
        public static Response prepared(PreparedPlan plan) {
            return new Response(VERSION, Outcome.PREPARED, "", "", plan);
        }
        public static Response rejected(String code, String message) {
            return new Response(VERSION, Outcome.REJECTED, code, message, null);
        }
    }

    public enum Outcome { PREPARED, REJECTED }

    public record PreparedPlan(String caseId, String idempotencyKey, UUID targetId, UUID actorId,
            String actorName, StaffRank actorRank, String reasonId, String family, String publicReason,
            String internalExplanation, String configurationVersion, CaseVisibility visibility, String issuedAt,
            Escalation escalation, List<Sanction> sanctions) {
        public PreparedPlan {
            if (blank(caseId) || blank(idempotencyKey) || targetId == null || actorId == null || blank(actorName)
                    || actorRank == null || blank(reasonId) || blank(family) || blank(publicReason)
                    || internalExplanation == null || blank(configurationVersion) || visibility == null
                    || blank(issuedAt) || escalation == null || sanctions == null || sanctions.isEmpty()) {
                throw new IllegalArgumentException("prepared cross-platform plan is invalid");
            }
            sanctions = List.copyOf(sanctions);
        }
    }

    public record Escalation(int rawOrdinal, int effectiveOrdinal, int recencyBonus,
            List<Contribution> contributions, DecayEligibility resultingDecayEligibility,
            int selectedOrdinal, String selectedLabel, List<Sanction> selectedSanctions) {
        public Escalation {
            if (rawOrdinal < 0 || effectiveOrdinal < 0 || recencyBonus < 0 || contributions == null
                    || resultingDecayEligibility == null || selectedOrdinal < 0 || blank(selectedLabel)
                    || selectedSanctions == null || selectedSanctions.isEmpty()) {
                throw new IllegalArgumentException("prepared escalation is invalid");
            }
            contributions = List.copyOf(contributions);
            selectedSanctions = List.copyOf(selectedSanctions);
        }
    }

    public record Contribution(int priorSeverity, int base, DecayEligibility decayEligibility,
            int decayedBy, int effective) {
        public Contribution {
            if (priorSeverity < 0 || base < 0 || decayEligibility == null || decayedBy < 0 || effective < 0) {
                throw new IllegalArgumentException("prepared escalation contribution is invalid");
            }
        }
    }

    public record Sanction(SanctionType type, SanctionLength.Kind lengthKind, Long durationSeconds) {
        public Sanction {
            if (type == null || lengthKind == null
                    || ((lengthKind == SanctionLength.Kind.TEMPORARY) != (durationSeconds != null))
                    || (durationSeconds != null && durationSeconds <= 0)) {
                throw new IllegalArgumentException("prepared sanction is invalid");
            }
        }
    }

    private static boolean blank(String value) { return value == null || value.isBlank(); }
}
