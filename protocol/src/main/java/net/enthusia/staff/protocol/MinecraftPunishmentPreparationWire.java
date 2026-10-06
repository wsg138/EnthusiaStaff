package net.enthusia.staff.protocol;

import java.util.List;
import java.util.UUID;
import net.enthusia.staff.domain.auth.StaffRank;
import net.enthusia.staff.domain.casefile.CaseVisibility;
import net.enthusia.staff.domain.escalation.DecayEligibility;
import net.enthusia.staff.domain.sanction.SanctionLength;
import net.enthusia.staff.domain.sanction.SanctionType;

/** Explicit allowlisted wire contract for D08 Minecraft punishment preparation. */
public final class MinecraftPunishmentPreparationWire {
    public static final int VERSION = 1;
    public static final String PATH = "/v1/cross-platform/minecraft-prepare";
    public static final int MAX_BODY_BYTES = 16_384;

    private MinecraftPunishmentPreparationWire() {
    }

    public record Request(
            int version,
            String caseId,
            String idempotencyKey,
            UUID actorId,
            String actorName,
            UUID targetId,
            String reasonId,
            String internalExplanation,
            CaseVisibility visibility,
            List<Sanction> overrideSanctions
    ) {
        public Request {
            if (version != VERSION || blank(caseId) || blank(idempotencyKey) || actorId == null || blank(actorName) || targetId == null
                    || blank(reasonId) || internalExplanation == null || visibility == null || overrideSanctions == null) {
                throw new IllegalArgumentException("Minecraft preparation request fields are invalid");
            }
            overrideSanctions = List.copyOf(overrideSanctions);
        }
    }

    public record Response(
            int version,
            Outcome outcome,
            String code,
            String message,
            PreparedPlan plan
    ) {
        public Response {
            if (version != VERSION || outcome == null || code == null || message == null) {
                throw new IllegalArgumentException("Minecraft preparation response fields are invalid");
            }
            if ((outcome == Outcome.PREPARED) != (plan != null)) {
                throw new IllegalArgumentException("prepared responses must carry exactly one plan");
            }
            if (outcome == Outcome.REJECTED && blank(code)) {
                throw new IllegalArgumentException("rejected responses require a code");
            }
        }

        public static Response prepared(PreparedPlan plan) {
            return new Response(VERSION, Outcome.PREPARED, "", "", plan);
        }

        public static Response rejected(String code, String message) {
            return new Response(VERSION, Outcome.REJECTED, code, message, null);
        }
    }

    public enum Outcome {
        PREPARED,
        REJECTED
    }

    public record PreparedPlan(
            String caseId,
            String idempotencyKey,
            UUID targetId,
            UUID actorId,
            String actorName,
            StaffRank actorRank,
            String reasonId,
            String family,
            String publicReason,
            String internalExplanation,
            String configurationVersion,
            CaseVisibility visibility,
            String issuedAt,
            Escalation escalation,
            List<Sanction> sanctions
    ) {
        public PreparedPlan {
            if (blank(caseId) || blank(idempotencyKey) || targetId == null || actorId == null || blank(actorName)
                    || actorRank == null || blank(reasonId) || blank(family) || blank(publicReason)
                    || internalExplanation == null || blank(configurationVersion) || visibility == null
                    || blank(issuedAt) || escalation == null || sanctions == null || sanctions.isEmpty()) {
                throw new IllegalArgumentException("prepared Minecraft plan fields are invalid");
            }
            sanctions = List.copyOf(sanctions);
        }
    }

    public record Escalation(
            int rawOrdinal,
            int effectiveOrdinal,
            int recencyBonus,
            List<Contribution> contributions,
            DecayEligibility resultingDecayEligibility,
            int selectedOrdinal,
            String selectedLabel,
            List<Sanction> selectedSanctions
    ) {
        public Escalation {
            if (rawOrdinal < 0 || effectiveOrdinal < 0 || recencyBonus < 0 || contributions == null
                    || resultingDecayEligibility == null || selectedOrdinal < 0 || blank(selectedLabel)
                    || selectedSanctions == null || selectedSanctions.isEmpty()) {
                throw new IllegalArgumentException("prepared escalation fields are invalid");
            }
            contributions = List.copyOf(contributions);
            selectedSanctions = List.copyOf(selectedSanctions);
        }
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
                throw new IllegalArgumentException("prepared escalation contribution is invalid");
            }
        }
    }

    public record Sanction(SanctionType type, SanctionLength.Kind lengthKind, Long durationSeconds) {
        public Sanction {
            if (type == null || lengthKind == null || (lengthKind == SanctionLength.Kind.TEMPORARY)
                    != (durationSeconds != null)) {
                throw new IllegalArgumentException("prepared sanction fields are invalid");
            }
            if (durationSeconds != null && durationSeconds <= 0) {
                throw new IllegalArgumentException("temporary sanction duration must be positive");
            }
        }
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
