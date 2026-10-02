package net.enthusia.staff.discordbot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.UUID;
import net.dv8tion.jda.api.audit.ActionType;
import org.junit.jupiter.api.Test;

class JdaKickEnforcerTest {
    private static final UUID PUNISHMENT_ID = UUID.fromString("67ab80ad-acde-4079-95c0-551b8acf7d3e");
    private static final Instant ISSUED_AT = Instant.parse("2026-09-14T20:00:00Z");
    private static final long TARGET_ID = 123L;
    private static final long BOT_ID = 456L;

    @Test
    void ownershipRequiresExactKickActorTargetAndPunishmentMarker() {
        String reason = JdaKickEnforcer.marker(PUNISHMENT_ID) + " reason";

        assertTrue(proves(observation(ActionType.KICK, TARGET_ID, BOT_ID, reason, ISSUED_AT.plusSeconds(1))));
        assertFalse(proves(observation(
                ActionType.BAN, TARGET_ID, BOT_ID, reason, ISSUED_AT.plusSeconds(1)
        )));
        assertFalse(proves(observation(
                ActionType.KICK, TARGET_ID + 1, BOT_ID, reason, ISSUED_AT.plusSeconds(1)
        )));
        assertFalse(proves(observation(
                ActionType.KICK, TARGET_ID, BOT_ID + 1, reason, ISSUED_AT.plusSeconds(1)
        )));
        assertFalse(proves(observation(
                ActionType.KICK, TARGET_ID, BOT_ID, "manual kick", ISSUED_AT.plusSeconds(1)
        )));
        assertFalse(proves(observation(
                ActionType.KICK,
                TARGET_ID,
                BOT_ID,
                JdaKickEnforcer.marker(UUID.randomUUID()) + " reason",
                ISSUED_AT.plusSeconds(1)
        )));
        assertFalse(proves(observation(
                ActionType.KICK, TARGET_ID, BOT_ID, reason, ISSUED_AT.minusSeconds(120)
        )));
    }

    @Test
    void permanentJdaFailureRemainsNonRetryable() {
        DiscordPunishmentGateway.EffectException classified = JdaKickEnforcer.classifyDispatchFailure(
                new DiscordPunishmentGateway.EffectException("APPLY_DISCORD_MISSING_PERMISSIONS", false)
        );

        assertEquals("APPLY_DISCORD_MISSING_PERMISSIONS", classified.errorCode());
        assertFalse(classified.retryable());
    }

    private static boolean proves(JdaKickEnforcer.Observation observation) {
        return JdaKickEnforcer.provesOwnership(
                observation, PUNISHMENT_ID, TARGET_ID, BOT_ID, ISSUED_AT
        );
    }

    private static JdaKickEnforcer.Observation observation(
            ActionType type,
            long targetId,
            long actorId,
            String reason,
            Instant createdAt
    ) {
        return new JdaKickEnforcer.Observation(type, targetId, actorId, reason, createdAt);
    }
}
