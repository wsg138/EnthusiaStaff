package net.enthusia.staff.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import net.enthusia.staff.domain.website.PublicPunishmentState;
import net.enthusia.staff.domain.website.WebsiteModerationException;
import org.junit.jupiter.api.Test;

final class WebsitePunishmentProjectionTest {
    private static final Instant NOW = Instant.parse("2026-07-23T12:00:00.123456Z");
    private static final String TYPE_BAN = "BAN";
    private static final String TYPE_WARNING = "WARNING";
    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String STATUS_APPLIED = "APPLIED";
    private static final String CASE_OPEN = "OPEN";

    @Test
    void mapsOnlySanitizedPublicTypes() {
        assertEquals(TYPE_BAN, WebsitePunishmentProjection.publicType("NETWORK_BAN"));
        assertEquals("IP_BAN", WebsitePunishmentProjection.publicType("NETWORK_IDENTITY_BAN"));
        assertEquals("MUTE", WebsitePunishmentProjection.publicType("MUTE"));
        assertEquals(TYPE_WARNING, WebsitePunishmentProjection.publicType(TYPE_WARNING));
        assertThrows(
                IllegalArgumentException.class,
                () -> WebsitePunishmentProjection.publicType("INVENTORY_CONFISCATION")
        );
    }

    @Test
    void derivesLivePublicStateFromStatusAndExpiration() {
        assertEquals(
                PublicPunishmentState.ACTIVE,
                WebsitePunishmentProjection.publicState(STATUS_ACTIVE, NOW.plusSeconds(1), NOW)
        );
        assertEquals(
                PublicPunishmentState.EXPIRED,
                WebsitePunishmentProjection.publicState(STATUS_ACTIVE, NOW, NOW)
        );
        assertEquals(
                PublicPunishmentState.REVOKED,
                WebsitePunishmentProjection.publicState("ENDED_EARLY", NOW.plusSeconds(60), NOW)
        );
        assertEquals(
                PublicPunishmentState.ACTIVE,
                WebsitePunishmentProjection.publicState(STATUS_APPLIED, null, NOW)
        );
        assertEquals(
                PublicPunishmentState.EXPIRED,
                WebsitePunishmentProjection.publicState(STATUS_APPLIED, NOW, NOW)
        );
    }

    @Test
    void codeEligibilityAcceptsLiveSanctionsUntilExpiration() {
        assertEquals(
                "ELIGIBLE",
                WebsitePunishmentProjection.eligibilityState(
                        STATUS_ACTIVE, CASE_OPEN, STATUS_ACTIVE, TYPE_BAN, NOW.plusSeconds(60), NOW
                )
        );
        assertEquals(
                "SANCTION_EXPIRED",
                WebsitePunishmentProjection.eligibilityState(
                        STATUS_ACTIVE, CASE_OPEN, STATUS_ACTIVE, "MUTE", NOW, NOW
                )
        );
        assertEquals(
                "ELIGIBLE",
                WebsitePunishmentProjection.eligibilityState(
                        STATUS_ACTIVE, CASE_OPEN, STATUS_APPLIED, TYPE_BAN, NOW.plusSeconds(60), NOW
                )
        );
        assertEquals(
                "SANCTION_EXPIRED",
                WebsitePunishmentProjection.eligibilityState(
                        STATUS_ACTIVE, CASE_OPEN, STATUS_APPLIED, TYPE_BAN, NOW, NOW
                )
        );
    }

    @Test
    void codeEligibilityRejectsInvalidLifecycleStates() {
        assertEquals(
                "CODE_REVOKED",
                WebsitePunishmentProjection.eligibilityState(
                        "REVOKED", CASE_OPEN, STATUS_ACTIVE, TYPE_BAN, null, NOW
                )
        );
        assertEquals(
                "OVERTURNED",
                WebsitePunishmentProjection.eligibilityState(
                        STATUS_ACTIVE, "FULLY_OVERTURNED", "OVERTURNED", TYPE_BAN, null, NOW
                )
        );
        assertEquals(
                "TYPE_INELIGIBLE",
                WebsitePunishmentProjection.eligibilityState(
                        STATUS_ACTIVE, CASE_OPEN, STATUS_ACTIVE, TYPE_WARNING, null, NOW
                )
        );
        assertEquals(
                "SANCTION_REVOKED",
                WebsitePunishmentProjection.eligibilityState(
                        STATUS_ACTIVE, CASE_OPEN, "ENDED_EARLY", TYPE_BAN, null, NOW
                )
        );
        assertEquals(
                "SANCTION_INACTIVE",
                WebsitePunishmentProjection.eligibilityState(
                        STATUS_ACTIVE, CASE_OPEN, "PENDING", TYPE_BAN, null, NOW
                )
        );
    }

    @Test
    void reportsAppealAvailabilityForLiveCodeEligibleSanctions() {
        assertFalse(WebsitePunishmentProjection.isCodeEligibleType(TYPE_WARNING));
        assertTrue(WebsitePunishmentProjection.isCodeEligibleType("NETWORK_IDENTITY_BAN"));
        assertTrue(WebsitePunishmentProjection.appealAvailable(
                PublicPunishmentState.ACTIVE,
                TYPE_BAN,
                STATUS_ACTIVE
        ));
        assertFalse(WebsitePunishmentProjection.appealAvailable(
                PublicPunishmentState.EXPIRED,
                TYPE_BAN,
                STATUS_ACTIVE
        ));
    }

    @Test
    void eligibilityStateRequiresTheCurrentTime() {
        assertThrows(
                IllegalArgumentException.class,
                () -> WebsitePunishmentProjection.eligibilityState(
                        STATUS_ACTIVE,
                        CASE_OPEN,
                        STATUS_ACTIVE,
                        TYPE_BAN,
                        null,
                        null
                )
        );
    }

    @Test
    void cursorRoundTripsMicrosecondInstantAndBinaryUuid() {
        UUID sanctionId = UUID.fromString("9ea10ad2-1a44-4e2f-a8ae-324aca4f6a0e");
        String encoded = WebsitePunishmentProjection.encodeCursor(NOW, sanctionId);
        WebsitePunishmentProjection.Cursor decoded = WebsitePunishmentProjection
                .decodeCursor(Optional.of(encoded))
                .orElseThrow();

        assertEquals(NOW, decoded.issuedAt());
        assertEquals(sanctionId, decoded.sanctionId());
        assertTrue(WebsitePunishmentProjection.decodeCursor(Optional.empty()).isEmpty());
        WebsiteModerationException error = assertThrows(
                WebsiteModerationException.class,
                () -> WebsitePunishmentProjection.decodeCursor(Optional.of("not+a+cursor"))
        );
        assertEquals("INVALID_CURSOR", error.code());
    }
}
