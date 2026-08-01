package net.enthusia.staff.persistence;

import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;
import net.enthusia.staff.domain.website.PublicPunishmentState;
import net.enthusia.staff.domain.website.WebsiteModerationException;

final class WebsitePunishmentProjection {
    private static final String TYPE_BAN = "BAN";
    private static final String TYPE_NETWORK_BAN = "NETWORK_BAN";
    private static final String TYPE_NETWORK_IDENTITY_BAN = "NETWORK_IDENTITY_BAN";
    private static final String TYPE_MUTE = "MUTE";
    private static final String TYPE_WARNING = "WARNING";
    private static final String PUBLIC_TYPE_IP_BAN = "IP_BAN";
    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String STATUS_APPLIED = "APPLIED";
    private static final String STATUS_EXPIRED = "EXPIRED";
    private static final String STATUS_ENDED_EARLY = "ENDED_EARLY";
    private static final String STATUS_REVOKED = "REVOKED";
    private static final String STATUS_OVERTURNED = "OVERTURNED";
    private static final String CASE_FULLY_OVERTURNED = "FULLY_OVERTURNED";
    private static final String ELIGIBLE = "ELIGIBLE";
    private static final String TYPE_INELIGIBLE = "TYPE_INELIGIBLE";
    private static final String OVERTURNED = "OVERTURNED";
    private static final String CODE_REVOKED = "CODE_REVOKED";
    private static final String SANCTION_REVOKED = "SANCTION_REVOKED";
    private static final String SANCTION_EXPIRED = "SANCTION_EXPIRED";
    private static final String SANCTION_INACTIVE = "SANCTION_INACTIVE";

    private WebsitePunishmentProjection() {
    }

    static boolean isPublicType(String type) {
        return switch (type) {
            case TYPE_BAN, TYPE_NETWORK_BAN, TYPE_NETWORK_IDENTITY_BAN, TYPE_MUTE, TYPE_WARNING -> true;
            default -> false;
        };
    }

    static boolean isCodeEligibleType(String type) {
        return switch (type) {
            case TYPE_BAN, TYPE_NETWORK_BAN, TYPE_NETWORK_IDENTITY_BAN, TYPE_MUTE -> true;
            default -> false;
        };
    }

    static String publicType(String type) {
        return switch (type) {
            case TYPE_BAN, TYPE_NETWORK_BAN -> TYPE_BAN;
            case TYPE_NETWORK_IDENTITY_BAN -> PUBLIC_TYPE_IP_BAN;
            case TYPE_MUTE -> TYPE_MUTE;
            case TYPE_WARNING -> TYPE_WARNING;
            default -> throw new IllegalArgumentException("Unsupported public sanction type");
        };
    }

    static PublicPunishmentState publicState(String status, Instant expiration, Instant now) {
        if (status == null || now == null) {
            throw new IllegalArgumentException("Sanction status and current time are required");
        }
        return switch (status) {
            case STATUS_REVOKED, STATUS_ENDED_EARLY -> PublicPunishmentState.REVOKED;
            case STATUS_EXPIRED -> PublicPunishmentState.EXPIRED;
            case STATUS_ACTIVE, STATUS_APPLIED -> activePublicState(expiration, now);
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
        if (now == null) {
            throw new IllegalArgumentException("Current time is required");
        }
        if (!isCodeEligibleType(sanctionType)) {
            return TYPE_INELIGIBLE;
        }
        if (isOverturned(caseState, sanctionStatus)) {
            return OVERTURNED;
        }
        if (!STATUS_ACTIVE.equals(codeStatus)) {
            return CODE_REVOKED;
        }
        return sanctionEligibility(sanctionStatus, expiration, now);
    }

    static boolean appealAvailable(
            PublicPunishmentState state,
            String sanctionType,
            String codeStatus
    ) {
        return state == PublicPunishmentState.ACTIVE
                && isCodeEligibleType(sanctionType)
                && STATUS_ACTIVE.equals(codeStatus);
    }

    private static PublicPunishmentState activePublicState(Instant expiration, Instant now) {
        return expiration != null && !expiration.isAfter(now)
                ? PublicPunishmentState.EXPIRED
                : PublicPunishmentState.ACTIVE;
    }

    private static boolean isOverturned(String caseState, String sanctionStatus) {
        return CASE_FULLY_OVERTURNED.equals(caseState) || STATUS_OVERTURNED.equals(sanctionStatus);
    }

    private static String sanctionEligibility(String sanctionStatus, Instant expiration, Instant now) {
        if (STATUS_REVOKED.equals(sanctionStatus) || STATUS_ENDED_EARLY.equals(sanctionStatus)) {
            return SANCTION_REVOKED;
        }
        if (isExpired(sanctionStatus, expiration, now)) {
            return SANCTION_EXPIRED;
        }
        if (!isActiveSanction(sanctionStatus)) {
            return SANCTION_INACTIVE;
        }
        return ELIGIBLE;
    }

    private static boolean isExpired(String sanctionStatus, Instant expiration, Instant now) {
        return STATUS_EXPIRED.equals(sanctionStatus)
                || (isActiveSanction(sanctionStatus)
                && expiration != null
                && !expiration.isAfter(now));
    }

    private static boolean isActiveSanction(String sanctionStatus) {
        return STATUS_ACTIVE.equals(sanctionStatus) || STATUS_APPLIED.equals(sanctionStatus);
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
        } catch (WebsiteModerationException exception) {
            throw exception;
        } catch (RuntimeException exception) {
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
