package net.enthusia.staff.paper.punishment.policyv2;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import net.enthusia.staff.domain.policyv2.IncidentFinding;

/**
 * Independent, read-only evidence verification boundary for severe real-world
 * coercion. A staff-selected boolean is never proof of real-world leverage.
 *
 * <p>No trusted provider is wired by default. Future integrations must verify
 * private, durable evidence independently of the submitted incident answers.</p>
 */
@FunctionalInterface
public interface PolicyV2RealWorldEvidenceGate {
    boolean verified(VerificationRequest request);

    static PolicyV2RealWorldEvidenceGate unavailable() {
        return ignored -> false;
    }

    record VerificationRequest(
            UUID reviewerId,
            UUID subjectId,
            Instant incidentAt,
            String policyVersion,
            IncidentFinding finding
    ) {
        public VerificationRequest {
            Objects.requireNonNull(reviewerId, "reviewerId");
            Objects.requireNonNull(subjectId, "subjectId");
            Objects.requireNonNull(incidentAt, "incidentAt");
            if (policyVersion == null || policyVersion.isBlank()) {
                throw new IllegalArgumentException("Evidence verification requires the policy version");
            }
            Objects.requireNonNull(finding, "finding");
        }
    }
}
