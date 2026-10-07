package net.enthusia.staff.domain.policyv2.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import net.enthusia.staff.domain.sanction.SanctionType;

/**
 * Deliberately allowlisted player-facing Policy v2 data. Private findings,
 * structured attributes, history math, evidence, actors, and internal notes
 * are not representable through this type.
 */
public record PolicyV2PublicProjection(
        String caseId,
        String publicOffense,
        String publicReason,
        Status status,
        Instant incidentAt,
        List<PublicSanction> sanctions,
        long revision
) {
    public enum Status {
        ACTIVE,
        RESOLVED,
        OVERTURNED
    }

    public PolicyV2PublicProjection {
        caseId = requireText(caseId, "case id");
        publicOffense = requireText(publicOffense, "public offense");
        publicReason = requireText(publicReason, "public reason");
        if (status == null || incidentAt == null || sanctions == null || revision < 0
                || sanctions.stream().anyMatch(java.util.Objects::isNull)) {
            throw new IllegalArgumentException("public projection is invalid");
        }
        sanctions = List.copyOf(sanctions);
    }

    public record PublicSanction(
            SanctionType type,
            SanctionStatus status,
            Optional<Instant> endsAt
    ) {
        public enum SanctionStatus {
            ACTIVE,
            COMPLETED,
            REVOKED
        }

        public PublicSanction {
            if (type == null || status == null || endsAt == null) {
                throw new IllegalArgumentException("public sanction is invalid");
            }
        }
    }

    private static String requireText(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " must not be blank");
        }
        return value.trim();
    }
}
