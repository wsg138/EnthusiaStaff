package net.enthusia.staff.discordbot;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

class DiscordPunishmentStatusMessageTest {
    @Test
    void pendingWarningIsNotMisreportedAsDelivered() {
        String status = DiscordPunishmentCommandController.statusMessage(pendingWarning());

        assertTrue(status.contains("PENDING_APPLY"));
        assertTrue(status.contains("NOT_ATTEMPTED"));
        assertTrue(status.contains("read-only status check"));
        assertFalse(status.contains("Target DM: **DELIVERED**"));
    }

    @Test
    void completedWarningReportsTheDurableDeliveryOutcome() {
        DiscordPunishment completed = pendingWarning().withProcessingResult(
                DiscordPunishmentState.COMPLETED,
                DiscordDeliveryOutcome.DELIVERED,
                true,
                Optional.empty(),
                Optional.empty(),
                "worker-delivered");

        String status = DiscordPunishmentCommandController.statusMessage(completed);
        assertTrue(status.contains("COMPLETED"));
        assertTrue(status.contains("Target DM: **DELIVERED**"));
    }

    @Test
    void failedWarningReportsFailureWithoutClaimingTargetWasWarned() {
        DiscordPunishment failed = pendingWarning().withProcessingResult(
                DiscordPunishmentState.FAILED_APPLY,
                DiscordDeliveryOutcome.FAILED_TERMINAL,
                false,
                Optional.empty(),
                Optional.of("WARNING_DM_FAILED"),
                "worker-failed");

        String status = DiscordPunishmentCommandController.statusMessage(failed);
        assertTrue(status.contains("FAILED_APPLY"));
        assertTrue(status.contains("FAILED_TERMINAL"));
        assertFalse(status.contains("Target DM: **DELIVERED**"));
    }

    @Test
    void cancellationControlUsesTheSameSingleUseConfirmationToken() {
        UUID token = UUID.randomUUID();
        var prepared = new DiscordPunishmentCommandController.Prepared(
                "Review", "d07ci:" + token);

        assertTrue(DiscordPunishmentCommandController.isCancellation(prepared.cancellationCustomId()));
        assertTrue(prepared.cancellationCustomId().endsWith(token.toString()));
        assertTrue(DiscordPunishmentCommandController.isStatus("d07s:" + token));
        assertFalse(DiscordPunishmentCommandController.isConfirmation(prepared.cancellationCustomId()));
    }

    private static DiscordPunishment pendingWarning() {
        DiscordPunishmentIntent intent = new DiscordPunishmentIntent(
                DiscordConsequenceType.WARNING,
                SanctionLength.instant(),
                false,
                false,
                Optional.empty(),
                "Chat behavior",
                "",
                0,
                true);
        return DiscordPunishment.pending(
                UUID.randomUUID(),
                new ModerationSubjectId(UUID.randomUUID()),
                new DiscordUserId("123"),
                new DiscordGuildId("456"),
                new Actor(UUID.randomUUID(), "staff", StaffRank.ADMIN),
                intent,
                Instant.parse("2026-11-15T12:00:00Z"),
                "issue");
    }
}
