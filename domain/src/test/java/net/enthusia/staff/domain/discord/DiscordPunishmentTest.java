package net.enthusia.staff.domain.discord;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import net.enthusia.staff.domain.auth.Actor;
import net.enthusia.staff.domain.auth.DiscordConsequenceType;
import net.enthusia.staff.domain.auth.StaffRank;
import net.enthusia.staff.domain.moderation.DiscordGuildId;
import net.enthusia.staff.domain.moderation.DiscordUserId;
import net.enthusia.staff.domain.moderation.ModerationSubjectId;
import net.enthusia.staff.domain.sanction.SanctionLength;
import org.junit.jupiter.api.Test;

class DiscordPunishmentTest {
    private static final Instant NOW = Instant.parse("2026-09-14T20:00:00Z");

    @Test
    void reversibleRemovalRequiresAppliedOrFailedRemovalState() {
        DiscordPunishment pending = punishment();
        DiscordPunishment failedApply = pending.withProcessingResult(
                DiscordPunishmentState.FAILED_APPLY,
                DiscordDeliveryOutcome.NOT_ATTEMPTED,
                false,
                Optional.empty(),
                Optional.of("APPLY_FAILED"),
                "failed"
        );
        DiscordPunishment applied = pending.withProcessingResult(
                DiscordPunishmentState.APPLIED,
                DiscordDeliveryOutcome.DELIVERED,
                true,
                Optional.empty(),
                Optional.empty(),
                "applied"
        );

        assertThrows(IllegalStateException.class,
                () -> pending.requestRemoval(DiscordPunishmentTermination.END, "remove-pending"));
        assertThrows(IllegalStateException.class,
                () -> failedApply.requestRemoval(DiscordPunishmentTermination.END, "remove-failed"));
        assertEquals(DiscordPunishmentState.PENDING_REMOVE,
                applied.requestRemoval(DiscordPunishmentTermination.END, "remove-applied").state());
    }

    @Test
    void pendingAndRetryApplyCanExpireWithoutDiscordEffect() {
        DiscordPunishment pending = punishment();
        DiscordPunishment retry = pending.withProcessingResult(
                DiscordPunishmentState.RETRY_APPLY,
                DiscordDeliveryOutcome.NOT_ATTEMPTED,
                false,
                Optional.empty(),
                Optional.of("RETRY"),
                "retry"
        );

        DiscordPunishment expired = pending.expireWithoutEffect("expire-pending");
        assertEquals(DiscordPunishmentState.EXPIRED, expired.state());
        assertEquals(DiscordPunishmentTermination.EXPIRE, expired.termination());
        assertFalse(expired.externalApplied());
        assertEquals(DiscordPunishmentState.EXPIRED, retry.expireWithoutEffect("expire-retry").state());

        DiscordPunishment applied = pending.withProcessingResult(
                DiscordPunishmentState.APPLIED,
                DiscordDeliveryOutcome.DELIVERED,
                true,
                Optional.empty(),
                Optional.empty(),
                "applied"
        );
        assertThrows(IllegalStateException.class, () -> applied.expireWithoutEffect("invalid"));
    }

    private static DiscordPunishment punishment() {
        DiscordPunishmentIntent intent = new DiscordPunishmentIntent(
                DiscordConsequenceType.MUTE,
                SanctionLength.temporary(Duration.ofHours(1)),
                false,
                false,
                Optional.empty(),
                "Reason",
                "",
                0,
                true
        );
        return DiscordPunishment.pending(
                UUID.randomUUID(),
                new ModerationSubjectId(UUID.randomUUID()),
                new DiscordUserId("123"),
                new DiscordGuildId("456"),
                new Actor(UUID.randomUUID(), "admin", StaffRank.ADMIN),
                intent,
                NOW,
                "issue"
        );
    }
}
