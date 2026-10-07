package net.enthusia.staff.velocity;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import net.enthusia.staff.domain.policyv2.publicview.PolicyV2PublicProjectionReader;

/**
 * Safe Policy v2 website/API read path. It has no access to private case
 * records and is intentionally not installed as a live route while v1 remains authoritative.
 */
final class PolicyV2PublicWebsiteView {
    private final PolicyV2PublicProjectionReader projections;

    PolicyV2PublicWebsiteView(PolicyV2PublicProjectionReader projections) {
        this.projections = Objects.requireNonNull(projections, "projections");
    }

    Optional<Map<String, Object>> find(String caseId) {
        return projections.publicProjection(caseId)
                .map(WebsiteApiResponses::policyV2PublicPunishment);
    }
}
