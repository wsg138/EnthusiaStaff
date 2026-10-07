package net.enthusia.staff.domain.policyv2.publicview;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import net.enthusia.staff.domain.policyv2.persistence.PolicyV2PublicProjection;

/**
 * Explicit response allowlist for Policy v2 public HTTP/website consumers.
 */
public final class PolicyV2PublicApiAdapter {
    private PolicyV2PublicApiAdapter() {
    }

    @SuppressWarnings("PMD.NullAssignment")
    public static Map<String, Object> toPublicMap(PolicyV2PublicProjection projection) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("caseId", projection.caseId());
        response.put("player", projection.currentPlayerName().orElse(null));
        response.put("historicalName", projection.incidentPlayerName().orElse(null));
        response.put("category", projection.category());
        response.put("offense", projection.publicOffense());
        response.put("reason", projection.publicReason());
        response.put("relatedHistory", projection.relatedHistorySummary().orElse(null));
        response.put("status", projection.status().name());
        response.put("appealStatus", projection.appealStatus().name());
        response.put("issuedAt", projection.issuedAt().toString());
        response.put("expiresAt", projection.expiresAt().map(Instant::toString).orElse(null));
        response.put("sanctions", projection.sanctions().stream()
                .map(PolicyV2PublicApiAdapter::sanction).toList());
        response.put("remedies", projection.remedies().stream()
                .map(PolicyV2PublicApiAdapter::remedy).toList());
        response.put("timeline", projection.timeline().stream()
                .map(PolicyV2PublicApiAdapter::revision).toList());
        response.put("policyVersion", projection.policyVersion().orElse(null));
        response.put("revision", projection.revision());
        return Collections.unmodifiableMap(response);
    }

    @SuppressWarnings("PMD.NullAssignment")
    private static Map<String, Object> sanction(PolicyV2PublicProjection.PublicSanction sanction) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("type", sanction.type().name());
        response.put("summary", sanction.summary());
        response.put("status", sanction.status().name());
        response.put("expiresAt", sanction.endsAt().map(Instant::toString).orElse(null));
        return Collections.unmodifiableMap(response);
    }

    private static Map<String, Object> remedy(PolicyV2PublicProjection.PublicRemedy remedy) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("type", remedy.type().name());
        response.put("summary", remedy.summary());
        response.put("status", remedy.status().name());
        return Collections.unmodifiableMap(response);
    }

    private static Map<String, Object> revision(PolicyV2PublicProjection.PublicRevision revision) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("type", revision.type().name());
        response.put("at", revision.occurredAt().toString());
        response.put("summary", revision.summary());
        return Collections.unmodifiableMap(response);
    }
}
