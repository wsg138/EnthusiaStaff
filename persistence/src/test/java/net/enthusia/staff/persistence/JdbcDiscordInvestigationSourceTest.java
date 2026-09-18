package net.enthusia.staff.persistence;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import net.enthusia.staff.domain.auth.Actor;
import net.enthusia.staff.domain.auth.DiscordConsequenceType;
import net.enthusia.staff.domain.auth.StaffRank;
import net.enthusia.staff.domain.discord.DiscordDeliveryOutcome;
import net.enthusia.staff.domain.discord.DiscordPunishment;
import net.enthusia.staff.domain.discord.DiscordPunishmentIntent;
import net.enthusia.staff.domain.discord.DiscordPunishmentState;
import net.enthusia.staff.domain.moderation.DiscordGuildId;
import net.enthusia.staff.domain.moderation.DiscordUserId;
import net.enthusia.staff.domain.moderation.ModerationSubjectId;
import net.enthusia.staff.domain.sanction.SanctionLength;
import org.junit.jupiter.api.Test;

class JdbcDiscordInvestigationSourceTest {
    private static final Instant TRIGGERED_AT = Instant.parse("2026-09-18T12:00:00Z");

    @Test
    void evasionSignalRejectsExpiredPunishmentWithoutRejectingOtherActiveBans() {
        assertFalse(JdbcDiscordInvestigationSource.isBanEvasionSignal(
                appliedBan(TRIGGERED_AT.minus(Duration.ofMinutes(10)), SanctionLength.temporary(Duration.ofMinutes(5))),
                TRIGGERED_AT));
        assertTrue(JdbcDiscordInvestigationSource.isBanEvasionSignal(
                appliedBan(TRIGGERED_AT.minus(Duration.ofMinutes(1)), SanctionLength.temporary(Duration.ofMinutes(5))),
                TRIGGERED_AT));
        assertTrue(JdbcDiscordInvestigationSource.isBanEvasionSignal(
                appliedBan(TRIGGERED_AT.minus(Duration.ofMinutes(10)), SanctionLength.permanent()),
                TRIGGERED_AT));
    }

    private static DiscordPunishment appliedBan(Instant issuedAt, SanctionLength length) {
        DiscordPunishmentIntent intent = new DiscordPunishmentIntent(
                DiscordConsequenceType.BAN, length, false, false, Optional.empty(),
                "Ban reason", "D09 source regression", 0, false);
        return DiscordPunishment.pending(
                        UUID.randomUUID(), new ModerationSubjectId(UUID.randomUUID()),
                        new DiscordUserId("223456789012345678"), new DiscordGuildId("1410303324745371709"),
                        new Actor(UUID.randomUUID(), "D09Staff", StaffRank.ADMIN), intent, issuedAt, "create")
                .withProcessingResult(
                        DiscordPunishmentState.APPLIED, DiscordDeliveryOutcome.NOT_ATTEMPTED, true,
                        Optional.empty(), Optional.empty(), "apply");
    }
}
