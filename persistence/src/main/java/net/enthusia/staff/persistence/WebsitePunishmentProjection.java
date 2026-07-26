package net.enthusia.staff.persistence;

import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;
import net.enthusia.staff.domain.website.PublicPunishmentState;
import net.enthusia.staff.domain.website.WebsiteModerationException;

final class WebsitePunishmentProjection {
    private WebsitePunishmentProjection() {
    }

    static boolean isPublicType(String type) {
        return switch (type) {
            case "BAN", "NETWORK_BAN", "NETWORK_IDENTITY_BAN", "MUTE", "WARNING" -> true;
            default -> false;
        };
    }

    static boolean isCodeEligibleType(String type) {
        return switch (type) {
            case "BAN", "NETWORK_BAN", "NETWORK_IDENTITY_BAN", "MUTE" -> true;
            default -> false;
        };
    }

    static String publicType(String type) {
        return switch (type) {
            case "BAN", "NETWORK_BAN" -> "BAN";
            case "NETWORK_IDENTITY_BAN" -> "IP_BAN";
            case "MUTE" -> "MUTE";
            case "WARNING" -> "WARNING";
            default -> throw new IllegalArgumentException("Unsupported public sanction type");
        };
    }

    static PublicPunishmentState publicState(String status, Instant expiration, Instant now) {
        if (status == null || now == null) {
            throw new IllegalArgumentException("Sanction status and current time are required");
        }
        return switch (status) {
            case "REVOKED", "ENDED_EARLY" -> PublicPunishmentState.REVOKED;
            case "EXPIRED" -> PublicPunishmentState.EXPIRED;
            case "ACTIVE" -> expiration != null && !expiration.isAfter(now)
                    ? PublicPunishmentState.EXPIRED
                    : PublicPunishmentState.ACTIVE;
            case "APPLIED" -> PublicPunishmentState.ACTIVE;
            default -> throw new IllegalArgumentException("Unsupported public sanction state");
        };
    }

    static String eligibilityState(
            String codeStatus,
            String caseState,
            String sanctionStatus,
            String sanctionType,
            Instant expiration,
            Instant now
    ) {
        if (!isCodeEligibleType(sanctionType)) {
            return "TYPE_INELIGIBLE";
        }
        if ("FULLY_OVERTURNED".equals(caseState) || "OVERTURNED".equals(sanctionStatus)) {
            return "OVERTURNED";
        }
        if (!"ACTIVE".equals(codeStatus)) {
            return "CODE_REVOKED";
        }
        if ("REVOKED".equals(sanctionStatus) || "ENDED_EARLY".equals(sanctionStatus)) {
            return "SANCTION_REVOKED";
        }
        if ("EXPIRED".equals(sanctionStatus)
                || ("ACTIVE".equals(sanctionStatus) && expiration != null && !expiration.isAfter(now))) {
            return "SANCTION_EXPIRED";
        }
        if (!"ACTIVE".equals(sanctionStatus)) {
            return "SANCTION_INACTIVE";
        }
        return "ELIGIBLE";
    }

    static String encodeCursor(Instant issuedAt, UUID sanctionId) {
        if (issuedAt == null || sanctionId == null) {
            throw new IllegalArgumentException("Cursor values are required");
        }
        ByteBuffer bytes = ByteBuffer.allocate(Long.BYTES + Integer.BYTES + 16)
                .putLong(issuedAt.getEpochSecond())
                .putInt(issuedAt.getNano())
                .putLong(sanctionId.getMostSignificantBits())
                .putLong(sanctionId.getLeastSignificantBits());
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes.array());
    }

    static Optional<Cursor> decodeCursor(Optional<String> encoded) {
        if (encoded == null || encoded.isEmpty()) {
            return Optional.empty();
        }
        String value = encoded.orElseThrow();
        if (value.length() > 64 || !value.matches("[A-Za-z0-9_-]+")) {
            throw invalidCursor();
        }
        try {
            byte[] decoded = Base64.getUrlDecoder().decode(value);
            if (decoded.length != Long.BYTES + Integer.BYTES + 16) {
                throw invalidCursor();
            }
            ByteBuffer bytes = ByteBuffer.wrap(decoded);
            Instant issuedAt = Instant.ofEpochSecond(bytes.getLong(), bytes.getInt());
            UUID sanctionId = new UUID(bytes.getLong(), bytes.getLong());
            return Optional.of(new Cursor(issuedAt, sanctionId));
        } catch (RuntimeException exception) {
            if (exception instanceof WebsiteModerationException moderationException) {
                throw moderationException;
            }
            throw invalidCursor();
        }
    }

    private static WebsiteModerationException invalidCursor() {
        return new WebsiteModerationException(
                WebsiteModerationException.Kind.INVALID,
                "INVALID_CURSOR",
                "The punishment cursor is invalid"
        );
    }

    record Cursor(Instant issuedAt, UUID sanctionId) {
    }
}
