package net.enthusia.staff.domain.policyv2.persistence;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import net.enthusia.staff.domain.sanction.SanctionType;

/**
 * Canonical allowlisted Policy v2 public case representation.
 *
 * <p>Private findings, structured attributes, history math, evidence, actors,
 * operation keys, appeal references, detection metadata, IP/alt linkage, and
 * internal notes are intentionally not representable through this type.</p>
 */
public record PolicyV2PublicProjection(
        String caseId,
        Optional<String> currentPlayerName,
        Optional<String> incidentPlayerName,
        String category,
        String publicOffense,
        String publicReason,
        Optional<String> relatedHistorySummary,
        Status status,
        AppealStatus appealStatus,
        Instant issuedAt,
        Optional<Instant> expiresAt,
        List<PublicSanction> sanctions,
        List<PublicRemedy> remedies,
        List<PublicRevision> timeline,
        Optional<String> policyVersion,
        long revision
) {
    private static final Pattern PLAYER_NAME = Pattern.compile("[A-Za-z0-9_]{3,16}");

    public enum Status {
        ACTIVE,
        EXPIRED,
        REDUCED,
        RECLASSIFIED,
        APPEALED,
        OVERTURNED
    }

    public enum AppealStatus {
        NONE,
        AVAILABLE,
        SUBMITTED,
        UNDER_REVIEW,
        APPROVED,
        DENIED,
        WITHDRAWN
    }

    public enum PublicSanctionType {
        WARNING,
        KICK,
        MUTE,
        BAN,
        REPORT_RESTRICTION,
        REPUTATION_RESTRICTION,
        MARKET_RESTRICTION
    }

    public enum PublicRemedyType {
        CONTENT_REMOVAL,
        CONFISCATION,
        ACCESS_RESTRICTION,
        PROFILE_COMPLIANCE,
        OTHER
    }

    public enum RevisionType {
        ISSUED,
        REDUCED,
        SANCTION_UPDATED,
        RECLASSIFIED,
        APPEALED,
        APPEAL_RESOLVED,
        REMEDY_UPDATED,
        OVERTURNED
    }

    public PolicyV2PublicProjection {
        caseId = requireText(caseId, "case id", 64);
        currentPlayerName = normalizePlayerName(currentPlayerName, "current player name");
        incidentPlayerName = normalizePlayerName(incidentPlayerName, "incident player name");
        category = requireText(category, "public category", 96);
        publicOffense = requireText(publicOffense, "public offense", 128);
        publicReason = requireText(publicReason, "public reason", 320);
        relatedHistorySummary = normalizeText(relatedHistorySummary, "related history summary", 160);
        policyVersion = normalizeText(policyVersion, "policy version", 128);
        if (status == null || appealStatus == null || issuedAt == null || expiresAt == null
                || sanctions == null || remedies == null || timeline == null || revision < 0) {
            throw new IllegalArgumentException("public projection is invalid");
        }
        expiresAt.ifPresent(expiration -> {
            if (expiration.isBefore(issuedAt)) {
                throw new IllegalArgumentException("public expiration precedes issue time");
            }
        });
        requireNoNulls(sanctions, "public sanctions");
        requireNoNulls(remedies, "public remedies");
        requireNoNulls(timeline, "public timeline");
        requireChronological(timeline);
        sanctions = List.copyOf(sanctions);
        remedies = List.copyOf(remedies);
        timeline = List.copyOf(timeline);
    }

    /**
     * Compatibility constructor for the narrow W2 storage contract.
     */
    public PolicyV2PublicProjection(
            String caseId,
            String publicOffense,
            String publicReason,
            Status status,
            Instant incidentAt,
            List<PublicSanction> sanctions,
            long revision
    ) {
        this(
                caseId,
                Optional.empty(),
                Optional.empty(),
                "Moderation",
                publicOffense,
                publicReason,
                Optional.empty(),
                status,
                AppealStatus.NONE,
                incidentAt,
                latestExpiry(sanctions),
                sanctions,
                List.of(),
                List.of(new PublicRevision(RevisionType.ISSUED, incidentAt, "Case issued")),
                Optional.empty(),
                revision
        );
    }

    public record PublicSanction(
            PublicSanctionType type,
            String summary,
            SanctionStatus status,
            Optional<Instant> endsAt
    ) {
        public enum SanctionStatus {
            ACTIVE,
            COMPLETED,
            REVOKED
        }

        public PublicSanction {
            summary = requireText(summary, "public sanction summary", 160);
            if (type == null || status == null || endsAt == null) {
                throw new IllegalArgumentException("public sanction is invalid");
            }
        }

        /**
         * Compatibility constructor for W2 callers. Sensitive/internal sanction
         * variants are collapsed to their safe public type.
         */
        public PublicSanction(
                SanctionType type,
                SanctionStatus status,
                Optional<Instant> endsAt
        ) {
            this(publicType(type), publicSummary(publicType(type)), status, endsAt);
        }
    }

    public record PublicRemedy(
            PublicRemedyType type,
            String summary,
            RemedyStatus status
    ) {
        public enum RemedyStatus {
            REQUIRED,
            SATISFIED,
            WAIVED
        }

        public PublicRemedy {
            summary = requireText(summary, "public remedy summary", 160);
            if (type == null || status == null) {
                throw new IllegalArgumentException("public remedy is invalid");
            }
        }
    }

    public record PublicRevision(
            RevisionType type,
            Instant occurredAt,
            String summary
    ) {
        public PublicRevision {
            summary = requireText(summary, "public revision summary", 160);
            if (type == null || occurredAt == null) {
                throw new IllegalArgumentException("public revision is invalid");
            }
        }
    }

    public static PublicSanctionType publicType(SanctionType type) {
        if (type == null) {
            throw new IllegalArgumentException("sanction type is required");
        }
        return switch (type) {
            case WARNING -> PublicSanctionType.WARNING;
            case KICK -> PublicSanctionType.KICK;
            case MUTE, PUBLIC_MUTE -> PublicSanctionType.MUTE;
            case BAN, NETWORK_BAN, NETWORK_IDENTITY_BAN -> PublicSanctionType.BAN;
            case REPORT_RESTRICTION -> PublicSanctionType.REPORT_RESTRICTION;
            case REPUTATION_BLACKLIST -> PublicSanctionType.REPUTATION_RESTRICTION;
            case MARKET_BLACKLIST -> PublicSanctionType.MARKET_RESTRICTION;
            case INVENTORY_CONFISCATION, ENDER_CHEST_CONFISCATION, ECONOMY_CONFISCATION,
                    CONTENT_REMOVAL, STALL_OWNERSHIP_REMOVAL ->
                    throw new IllegalArgumentException("remedy-like sanction cannot enter public sanctions");
        };
    }

    public static String publicSummary(PublicSanctionType type) {
        if (type == null) {
            throw new IllegalArgumentException("public sanction type is required");
        }
        return switch (type) {
            case WARNING -> "Warning";
            case KICK -> "Kick";
            case MUTE -> "Mute";
            case BAN -> "Ban";
            case REPORT_RESTRICTION -> "Report restriction";
            case REPUTATION_RESTRICTION -> "Reputation restriction";
            case MARKET_RESTRICTION -> "Market restriction";
        };
    }

    private static Optional<Instant> latestExpiry(List<PublicSanction> sanctions) {
        if (sanctions == null) {
            return Optional.empty();
        }
        boolean permanentActive = sanctions.stream()
                .filter(java.util.Objects::nonNull)
                .anyMatch(sanction -> sanction.status() == PublicSanction.SanctionStatus.ACTIVE
                        && sanction.endsAt().isEmpty());
        if (permanentActive) {
            return Optional.empty();
        }
        return sanctions.stream()
                .filter(java.util.Objects::nonNull)
                .map(PublicSanction::endsAt)
                .flatMap(Optional::stream)
                .max(Comparator.naturalOrder());
    }

    private static Optional<String> normalizePlayerName(Optional<String> value, String label) {
        if (value == null) {
            throw new IllegalArgumentException(label + " is required");
        }
        return value.map(name -> {
            String normalized = requireText(name, label, 16);
            if (!PLAYER_NAME.matcher(normalized).matches()) {
                throw new IllegalArgumentException(label + " is invalid");
            }
            return normalized;
        });
    }

    private static Optional<String> normalizeText(Optional<String> value, String label, int maxLength) {
        if (value == null) {
            throw new IllegalArgumentException(label + " is required");
        }
        return value.map(text -> requireText(text, label, maxLength));
    }

    private static String requireText(String value, String label, int maxLength) {
        if (value == null || value.isBlank() || value.length() > maxLength) {
            throw new IllegalArgumentException(label + " is invalid");
        }
        return value.trim();
    }

    private static void requireNoNulls(List<?> values, String label) {
        if (values.stream().anyMatch(java.util.Objects::isNull)) {
            throw new IllegalArgumentException(label + " must not contain nulls");
        }
    }

    private static void requireChronological(List<PublicRevision> timeline) {
        Instant prior = null;
        for (PublicRevision entry : timeline) {
            if (prior != null && entry.occurredAt().isBefore(prior)) {
                throw new IllegalArgumentException("public timeline must be chronological");
            }
            prior = entry.occurredAt();
        }
    }
}
