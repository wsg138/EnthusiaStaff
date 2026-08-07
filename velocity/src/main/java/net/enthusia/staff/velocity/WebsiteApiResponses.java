package net.enthusia.staff.velocity;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import net.enthusia.staff.domain.website.PublicPunishment;
import net.enthusia.staff.domain.website.PunishmentCodeBinding;
import net.enthusia.staff.domain.website.WebsiteAppealCandidate;
import net.enthusia.staff.domain.website.WebsiteAppealView;

final class WebsiteApiResponses {
    private WebsiteApiResponses() {
    }

    @SuppressWarnings("PMD.NullAssignment") // The public JSON contract uses explicit null for absent values.
    static Map<String, Object> publicPunishment(PublicPunishment punishment) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("player", punishment.player());
        response.put("punishmentType", punishment.punishmentType());
        response.put("broadReason", punishment.broadReason());
        response.put("publicReason", punishment.publicReason());
        response.put("issuedAt", punishment.issuedAt().toString());
        response.put("expiresAt", punishment.expiresAt().map(Instant::toString).orElse(null));
        response.put(
                "remainingSeconds",
                punishment.remainingSeconds().isPresent()
                        ? punishment.remainingSeconds().getAsLong()
                        : null
        );
        response.put("state", punishment.state().name());
        response.put("caseId", punishment.caseId().value());
        response.put("appealAvailable", punishment.appealAvailable());
        return response;
    }

    static Map<String, Object> binding(PunishmentCodeBinding binding) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("punishmentId", binding.punishmentId().toString());
        response.put("caseId", binding.caseId().value());
        response.put("codeGeneration", binding.codeGeneration());
        response.put("punishmentType", binding.punishmentType());
        response.put("boundUsername", binding.boundUsername());
        response.put("eligible", binding.eligible());
        response.put("eligibilityState", binding.eligibilityState());
        return response;
    }

    static Map<String, Object> appealCandidate(WebsiteAppealCandidate candidate) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("id", candidate.punishmentId().toString());
        response.put("caseId", candidate.caseId().value());
        response.put("type", candidate.punishmentType());
        response.put("reason", candidate.publicReason());
        response.put("createdAt", candidate.issuedAt().toString());
        return response;
    }

    @SuppressWarnings("PMD.NullAssignment") // The private JSON contract uses explicit nulls.
    static Map<String, Object> appeal(WebsiteAppealView appeal) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("id", appeal.appealId().toString());
        response.put("punishmentId", appeal.punishmentId().toString());
        response.put("caseId", appeal.caseId().value());
        response.put("punishmentType", appeal.punishmentType());
        response.put("player", appeal.playerUsername());
        response.put("reason", appeal.reason());
        response.put("status", appeal.state());
        response.put("version", appeal.version());
        response.put("decision", appeal.decision());
        response.put("decisionNote", appeal.decisionNote());
        response.put("createdAt", appeal.createdAt().toString());
        response.put("updatedAt", appeal.updatedAt().toString());
        return response;
    }
}
