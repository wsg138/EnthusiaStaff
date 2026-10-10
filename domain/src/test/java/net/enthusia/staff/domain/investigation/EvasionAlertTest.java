package net.enthusia.staff.domain.investigation;

import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import net.enthusia.staff.domain.auth.DiscordConsequenceType;
import net.enthusia.staff.domain.discord.DiscordPunishmentState;
import net.enthusia.staff.domain.moderation.DiscordUserId;
import net.enthusia.staff.domain.moderation.ModerationSubjectId;
import net.enthusia.staff.domain.ports.DiscordInvestigationStore;
import org.junit.jupiter.api.Test;

class EvasionAlertTest {
    private static final Instant NOW = Instant.parse("2026-09-18T12:00:00Z");
    private static final UUID ALERT_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");

    @Test
    void contextRejectsPunishmentExpiredAtOrBeforeTrigger() {
        assertThrows(IllegalArgumentException.class, () -> context(Optional.of(NOW)));
        assertThrows(IllegalArgumentException.class, () -> context(Optional.of(NOW.minusSeconds(1))));

        context(Optional.of(NOW.plusSeconds(1)));
        context(Optional.empty());
    }

    @Test
    void failedDeliveryRequiresStrictlyFutureRetryTime() {
        assertThrows(IllegalArgumentException.class, () -> new DiscordInvestigationStore.EvasionDeliveryUpdate(
                ALERT_ID, DiscordInvestigationStore.EvasionDeliveryChannel.DISCORD, false,
                Optional.of("TEMPORARY"), Optional.of(NOW), 0, NOW
        ));

        new DiscordInvestigationStore.EvasionDeliveryUpdate(
                ALERT_ID, DiscordInvestigationStore.EvasionDeliveryChannel.DISCORD, false,
                Optional.of("TEMPORARY"), Optional.of(NOW.plusMillis(1)), 0, NOW
        );
    }

    private static EvasionAlert.Context context(Optional<Instant> expiresAt) {
        return new EvasionAlert.Context(
                new ModerationSubjectId(UUID.fromString("20000000-0000-0000-0000-000000000001")),
                UUID.fromString("30000000-0000-0000-0000-000000000001"),
                new DiscordUserId("223456789012345678"), DiscordConsequenceType.BAN,
                "Active ban", DiscordPunishmentState.APPLIED, expiresAt,
                UUID.fromString("40000000-0000-0000-0000-000000000001"), Optional.empty(),
                "survival", 1, EvasionAlert.TriggerType.LINKED_MINECRAFT_ONLINE, NOW
        );
    }
}
