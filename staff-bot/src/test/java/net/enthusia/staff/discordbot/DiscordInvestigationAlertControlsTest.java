package net.enthusia.staff.discordbot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import net.enthusia.staff.domain.auth.DiscordConsequenceType;
import net.enthusia.staff.domain.discord.DiscordPunishmentState;
import net.enthusia.staff.domain.investigation.EvasionAlert;
import net.enthusia.staff.domain.moderation.DiscordUserId;
import net.enthusia.staff.domain.moderation.ModerationSubjectId;
import org.junit.jupiter.api.Test;

class DiscordInvestigationAlertControlsTest {
    private static final Instant NOW = Instant.parse("2026-09-15T12:00:00Z");

    @Test
    void quickActionsRoundTripWithoutEmbeddingAuthority() {
        EvasionAlert alert = alert();

        assertEquals(DiscordInvestigationAlertControls.Type.LINKED,
                DiscordInvestigationAlertControls.parse(DiscordInvestigationAlertControls.linked(alert)).type());
        assertEquals(DiscordInvestigationAlertControls.Type.HISTORY,
                DiscordInvestigationAlertControls.parse(DiscordInvestigationAlertControls.history(alert)).type());
        assertEquals(DiscordInvestigationAlertControls.Type.MODERATE,
                DiscordInvestigationAlertControls.parse(DiscordInvestigationAlertControls.moderate(alert)).type());
        var resolve = DiscordInvestigationAlertControls.parse(DiscordInvestigationAlertControls.resolve(alert));
        assertEquals(DiscordInvestigationAlertControls.Type.RESOLVE, resolve.type());
        assertEquals(alert.alertId(), resolve.alertId().orElseThrow());
        assertEquals(Long.parseUnsignedLong(alert.targetDiscordUserId().value()), resolve.targetDiscordId());
        assertTrue(DiscordInvestigationAlertControls.resolve(alert).length() <= 100);
    }

    @Test
    void malformedOrZeroTargetActionsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> DiscordInvestigationAlertControls.parse("d09alert:linked:0"));
        assertThrows(IllegalArgumentException.class, () -> DiscordInvestigationAlertControls.parse("d09alert:resolve:1:not-a-uuid"));
        assertThrows(IllegalArgumentException.class, () -> DiscordInvestigationAlertControls.parse("d09alert:unknown:1"));
    }

    static EvasionAlert alert() {
        EvasionAlert.Context context = new EvasionAlert.Context(
                new ModerationSubjectId(UUID.fromString("10000000-0000-0000-0000-000000000001")),
                UUID.fromString("20000000-0000-0000-0000-000000000001"),
                new DiscordUserId("18446744073709551614"),
                DiscordConsequenceType.BAN,
                "Ban reason",
                DiscordPunishmentState.APPLIED,
                Optional.empty(),
                UUID.fromString("30000000-0000-0000-0000-000000000001"),
                Optional.of("LinkedAlt"),
                "survival",
                7,
                EvasionAlert.TriggerType.LINKED_MINECRAFT_ONLINE,
                NOW.minusSeconds(5)
        );
        return new EvasionAlert(
                UUID.fromString("40000000-0000-0000-0000-000000000001"), "d09:test:alert", context,
                EvasionAlert.State.OPEN, EvasionAlert.DeliveryState.PENDING, EvasionAlert.DeliveryState.PENDING,
                0, 0, Optional.empty(), Optional.empty(), Optional.of(NOW), Optional.of(NOW), NOW, NOW, 0, false
        );
    }
}
