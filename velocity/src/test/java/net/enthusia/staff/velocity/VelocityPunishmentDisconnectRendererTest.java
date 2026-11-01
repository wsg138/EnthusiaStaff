package net.enthusia.staff.velocity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import net.enthusia.staff.common.CaseId;
import net.enthusia.staff.domain.sanction.ActiveSanction;
import net.enthusia.staff.domain.sanction.SanctionType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.junit.jupiter.api.Test;

class VelocityPunishmentDisconnectRendererTest {
    private static final Instant NOVEMBER_EXPIRY = Instant.parse("2026-11-02T14:30:38Z");
    private static final Instant NOW = Instant.parse("2026-11-01T23:00:38Z");

    @Test
    void temporaryBanShowsTimeLeftExactEasternExpiryAndAppealCodeWithoutCaseId() {
        Component result = VelocityPunishmentDisconnectRenderer.render(
                sanction(Optional.of(NOVEMBER_EXPIRY)), NOW,
                "https://enthusia.net/appeals", Optional.of("Y2DD-SVKR-3YBS-SU2D-SM5E-NDWN")
        );
        String displayed = text(result);
        assertEquals(NamedTextColor.RED, result.children().getFirst().color());
        assertEquals(null, result.color());
        assertEquals(null, result.children().get(2).color());
        assertTrue(displayed.contains("CONNECTION BLOCKED"));
        assertTrue(displayed.contains("Reason: Encouraging self-harm"));
        assertTrue(displayed.contains("Time left: 15h 30m"));
        assertTrue(displayed.contains("(Ends Nov 2, 2026 at 9:30:38 AM EST)"));
        assertTrue(displayed.contains("Appeal: https://enthusia.net/appeals"));
        assertTrue(displayed.contains("Appeal code: Y2DD-SVKR-3YBS-SU2D-SM5E-NDWN"));
        assertFalse(displayed.contains("Case:"));
        assertFalse(displayed.contains("Punishment code:"));
    }

    @Test
    void permanentBanDoesNotDisplayCountdownOrEndDate() {
        String displayed = text(VelocityPunishmentDisconnectRenderer.render(
                sanction(Optional.empty()), NOW, "https://enthusia.net/appeals", Optional.empty()
        ));
        assertTrue(displayed.contains("Duration: Permanent ban"));
        assertFalse(displayed.contains("Time left:"));
        assertFalse(displayed.contains("Ends "));
        assertFalse(displayed.contains("Appeal code:"));
    }

    @Test
    void summerExpirationUsesDaylightTimeNotFixedEst() {
        String displayed = text(VelocityPunishmentDisconnectRenderer.render(
                sanction(Optional.of(Instant.parse("2026-07-02T13:00:00Z"))),
                Instant.parse("2026-07-02T12:00:00Z"),
                "https://enthusia.net/appeals", Optional.empty()
        ));
        assertTrue(displayed.contains("9:00:00 AM EDT"));
        assertTrue(displayed.contains("Time left: 1h 0m"));
    }

    @Test
    void remainingHandlesDaysMinutesAndFinalSecondsWithoutShowingZeroMinuteBan() {
        assertEquals("2d 3h 4m", VelocityPunishmentDisconnectRenderer.remaining(
                NOW, NOW.plusSeconds(2 * 86400 + 3 * 3600 + 4 * 60)));
        assertEquals("12m", VelocityPunishmentDisconnectRenderer.remaining(NOW, NOW.plusSeconds(750)));
        assertEquals("Less than 1 minute", VelocityPunishmentDisconnectRenderer.remaining(
                NOW, NOW.plusSeconds(30)));
        assertEquals("Ending now", VelocityPunishmentDisconnectRenderer.remaining(NOW, NOW.minusSeconds(1)));
    }

    private static ActiveSanction sanction(Optional<Instant> expiresAt) {
        return new ActiveSanction(
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                new CaseId("0123456789ABCDEF"),
                UUID.fromString("22222222-2222-2222-2222-222222222222"),
                SanctionType.NETWORK_BAN,
                "Encouraging self-harm",
                Instant.parse("2026-10-30T12:00:00Z"),
                expiresAt,
                Optional.empty()
        );
    }

    private static String text(Component component) {
        StringBuilder result = new StringBuilder();
        if (component instanceof TextComponent segment) {
            result.append(segment.content());
        }
        for (Component child : component.children()) {
            result.append(text(child));
        }
        return result.toString();
    }
}
