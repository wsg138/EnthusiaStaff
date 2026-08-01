package net.enthusia.staff.velocity;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import net.enthusia.staff.domain.website.PublicPunishment;
import net.enthusia.staff.domain.website.PunishmentCodeBinding;

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
}
