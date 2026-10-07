package net.enthusia.staff.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import net.enthusia.staff.domain.policyv2.persistence.PolicyV2PublicProjection;
import net.enthusia.staff.domain.sanction.SanctionType;

/**
 * Decodes the current public projection shape while preserving read
 * compatibility with W2 rows already stored in V28 JSON.
 */
final class PolicyV2PublicProjectionDecoder {
    private static final String CURRENT_ISSUED_AT = "issuedAt";
    private static final String LEGACY_INCIDENT_AT = "incidentAt";

    private final PolicyV2JdbcSupport support;

    PolicyV2PublicProjectionDecoder(PolicyV2JdbcSupport support) {
        if (support == null) {
            throw new IllegalArgumentException("Policy v2 JDBC support is required");
        }
        this.support = support;
    }

    PolicyV2PublicProjection decode(String value) {
        JsonNode root = support.readTree(value);
        if (root == null || !root.isObject()) {
            throw new ModerationPersistenceException("Unknown Policy v2 public projection JSON shape");
        }
        if (root.has(CURRENT_ISSUED_AT)) {
            return support.read(value, PolicyV2PublicProjection.class);
        }
        if (root.has(LEGACY_INCIDENT_AT)) {
            return fromLegacy(support.read(value, LegacyProjection.class));
        }
        throw new ModerationPersistenceException("Unknown Policy v2 public projection JSON shape");
    }

    private static PolicyV2PublicProjection fromLegacy(LegacyProjection legacy) {
        List<PolicyV2PublicProjection.PublicSanction> sanctions = legacy.sanctions().stream()
                .map(PolicyV2PublicProjectionDecoder::legacySanction)
                .flatMap(Optional::stream)
                .toList();
        return new PolicyV2PublicProjection(
                legacy.caseId(),
                Optional.empty(),
                Optional.empty(),
                "Moderation",
                legacy.publicOffense(),
                legacy.publicReason(),
                Optional.empty(),
                legacyStatus(legacy.status()),
                PolicyV2PublicProjection.AppealStatus.NONE,
                legacy.incidentAt(),
                latestExpiry(sanctions),
                sanctions,
                List.of(),
                List.of(new PolicyV2PublicProjection.PublicRevision(
                        PolicyV2PublicProjection.RevisionType.ISSUED,
                        legacy.incidentAt(),
                        "Case issued"
                )),
                Optional.empty(),
                legacy.revision()
        );
    }

    private static Optional<PolicyV2PublicProjection.PublicSanction> legacySanction(
            LegacySanction legacy
    ) {
        PolicyV2PublicProjection.PublicSanctionType type;
        try {
            type = PolicyV2PublicProjection.publicType(legacy.type());
        } catch (IllegalArgumentException ignored) {
            return Optional.empty();
        }
        return Optional.of(new PolicyV2PublicProjection.PublicSanction(
                type,
                PolicyV2PublicProjection.publicSummary(type),
                legacySanctionStatus(legacy.status()),
                legacy.endsAt()
        ));
    }

    private static PolicyV2PublicProjection.PublicSanction.SanctionStatus legacySanctionStatus(
            LegacySanctionStatus status
    ) {
        return switch (status) {
            case ACTIVE -> PolicyV2PublicProjection.PublicSanction.SanctionStatus.ACTIVE;
            case COMPLETED -> PolicyV2PublicProjection.PublicSanction.SanctionStatus.COMPLETED;
            case REVOKED -> PolicyV2PublicProjection.PublicSanction.SanctionStatus.REVOKED;
        };
    }

    private static PolicyV2PublicProjection.Status legacyStatus(LegacyStatus status) {
        return switch (status) {
            case ACTIVE -> PolicyV2PublicProjection.Status.ACTIVE;
            case RESOLVED -> PolicyV2PublicProjection.Status.EXPIRED;
            case OVERTURNED -> PolicyV2PublicProjection.Status.OVERTURNED;
        };
    }

    private static Optional<Instant> latestExpiry(
            List<PolicyV2PublicProjection.PublicSanction> sanctions
    ) {
        boolean permanentActive = sanctions.stream().anyMatch(sanction ->
                sanction.status() == PolicyV2PublicProjection.PublicSanction.SanctionStatus.ACTIVE
                        && sanction.endsAt().isEmpty()
        );
        if (permanentActive) {
            return Optional.empty();
        }
        return sanctions.stream()
                .map(PolicyV2PublicProjection.PublicSanction::endsAt)
                .flatMap(Optional::stream)
                .max(java.util.Comparator.naturalOrder());
    }

    private enum LegacyStatus {
        ACTIVE,
        RESOLVED,
        OVERTURNED
    }

    private enum LegacySanctionStatus {
        ACTIVE,
        COMPLETED,
        REVOKED
    }

    private record LegacyProjection(
            String caseId,
            String publicOffense,
            String publicReason,
            LegacyStatus status,
            Instant incidentAt,
            List<LegacySanction> sanctions,
            long revision
    ) {
        private LegacyProjection {
            if (caseId == null || caseId.isBlank() || publicOffense == null || publicOffense.isBlank()
                    || publicReason == null || publicReason.isBlank() || status == null
                    || incidentAt == null || sanctions == null || revision < 0
                    || sanctions.stream().anyMatch(java.util.Objects::isNull)) {
                throw new IllegalArgumentException("legacy public projection is invalid");
            }
            sanctions = List.copyOf(sanctions);
        }
    }

    private record LegacySanction(
            SanctionType type,
            LegacySanctionStatus status,
            Optional<Instant> endsAt
    ) {
        private LegacySanction {
            if (type == null || status == null || endsAt == null) {
                throw new IllegalArgumentException("legacy public sanction is invalid");
            }
        }
    }
}
