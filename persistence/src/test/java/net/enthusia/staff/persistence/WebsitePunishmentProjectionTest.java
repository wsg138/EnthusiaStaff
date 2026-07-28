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

    @Test
    void mapsOnlySanitizedPublicTypes() {
        assertEquals("BAN", WebsitePunishmentProjection.publicType("NETWORK_BAN"));
        assertEquals("IP_BAN", WebsitePunishmentProjection.publicType("NETWORK_IDENTITY_BAN"));
        assertEquals("MUTE", WebsitePunishmentProjection.publicType("MUTE"));
        assertEquals("WARNING", WebsitePunishmentProjection.publicType("WARNING"));
        assertThrows(
                IllegalArgumentException.class,
                () -> WebsitePunishmentProjection.publicType("INVENTORY_CONFISCATION")
        );
    }

    @Test
    void derivesLivePublicStateFromStatusAndExpiration() {
        assertEquals(
                PublicPunishmentState.ACTIVE,
                WebsitePunishmentProjection.publicState("ACTIVE", NOW.plusSeconds(1), NOW)
        );
        assertEquals(
                PublicPunishmentState.EXPIRED,
                WebsitePunishmentProjection.publicState("ACTIVE", NOW, NOW)
        );
        assertEquals(
                PublicPunishmentState.REVOKED,
                WebsitePunishmentProjection.publicState("ENDED_EARLY", NOW.plusSeconds(60), NOW)
        );
        assertEquals(
                PublicPunishmentState.ACTIVE,
                WebsitePunishmentProjection.publicState("APPLIED", null, NOW)
        );
    }

    @Test
    void codeEligibilityRevalidatesEveryLiveState() {
        assertEquals(
                "ELIGIBLE",
                WebsitePunishmentProjection.eligibilityState(
                        "ACTIVE", "OPEN", "ACTIVE", "BAN", NOW.plusSeconds(60), NOW
                )
        );
        assertEquals(
                "SANCTION_EXPIRED",
                WebsitePunishmentProjection.eligibilityState(
                        "ACTIVE", "OPEN", "ACTIVE", "MUTE", NOW, NOW
                )
        );
        assertEquals(
                "CODE_REVOKED",
                WebsitePunishmentProjection.eligibilityState(
                        "REVOKED", "OPEN", "ACTIVE", "BAN", null, NOW
                )
        );
        assertEquals(
                "OVERTURNED",
                WebsitePunishmentProjection.eligibilityState(
                        "ACTIVE", "FULLY_OVERTURNED", "OVERTURNED", "BAN", null, NOW
                )
        );
        assertFalse(WebsitePunishmentProjection.isCodeEligibleType("WARNING"));
        assertTrue(WebsitePunishmentProjection.isCodeEligibleType("NETWORK_IDENTITY_BAN"));
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
