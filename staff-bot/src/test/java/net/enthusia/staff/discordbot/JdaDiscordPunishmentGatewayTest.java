package net.enthusia.staff.discordbot;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.UUID;
import net.dv8tion.jda.api.audit.ActionType;
import org.junit.jupiter.api.Test;

class JdaDiscordPunishmentGatewayTest {
    private static final UUID PUNISHMENT_ID = UUID.fromString("6ebc2fc4-cbce-4a52-92f8-aa1183899fd3");
    private static final Instant ISSUED_AT = Instant.parse("2026-09-14T20:00:00Z");
    private static final long TARGET_ID = 123L;
    private static final long BOT_ID = 456L;

    @Test
    void freshMuteRejectsAnAlreadyPresentManagedRole() {
        DiscordPunishmentGateway.EffectException failure = assertThrows(
                DiscordPunishmentGateway.EffectException.class,
                () -> JdaMuteRoleOwnership.requireFreshRoleAbsent(true)
        );

        assertEquals("MUTE_ROLE_ALREADY_PRESENT", failure.errorCode());
        assertFalse(failure.retryable());
    }

    @Test
    void freshMuteAcceptsAnAbsentManagedRole() {
        assertDoesNotThrow(() -> JdaMuteRoleOwnership.requireFreshRoleAbsent(false));
    }

    @Test
    void applyRetryRequiresExactBotTargetAndPunishmentMarker() {
        String ownedReason = JdaMuteRoleOwnership.marker(PUNISHMENT_ID) + " reason";
        JdaMuteRoleOwnership.Observation owned = observation(
                ActionType.MEMBER_ROLE_UPDATE, TARGET_ID, BOT_ID, ownedReason, ISSUED_AT.plusSeconds(1)
        );

        assertTrue(proves(owned));
        assertFalse(proves(observation(
                ActionType.MEMBER_UPDATE, TARGET_ID, BOT_ID, ownedReason, ISSUED_AT.plusSeconds(1)
        )));
        assertFalse(proves(observation(
                ActionType.MEMBER_ROLE_UPDATE, TARGET_ID + 1, BOT_ID, ownedReason, ISSUED_AT.plusSeconds(1)
        )));
        assertFalse(proves(observation(
                ActionType.MEMBER_ROLE_UPDATE, TARGET_ID, BOT_ID + 1, ownedReason, ISSUED_AT.plusSeconds(1)
        )));
        assertFalse(proves(observation(
                ActionType.MEMBER_ROLE_UPDATE, TARGET_ID, BOT_ID, "manual role assignment", ISSUED_AT.plusSeconds(1)
        )));
        assertFalse(proves(observation(
                ActionType.MEMBER_ROLE_UPDATE,
                TARGET_ID,
                BOT_ID,
                JdaMuteRoleOwnership.marker(UUID.randomUUID()) + " reason",
                ISSUED_AT.plusSeconds(1)
        )));
        assertFalse(proves(observation(
                ActionType.MEMBER_ROLE_UPDATE,
                TARGET_ID,
                BOT_ID,
                ownedReason,
                ISSUED_AT.minusSeconds(120)
        )));
    }

    private static boolean proves(JdaMuteRoleOwnership.Observation observation) {
        return JdaMuteRoleOwnership.provesOwnership(
                observation, PUNISHMENT_ID, TARGET_ID, BOT_ID, ISSUED_AT
        );
    }

    private static JdaMuteRoleOwnership.Observation observation(
            ActionType type,
            long targetId,
            long actorId,
            String reason,
            Instant createdAt
    ) {
        return new JdaMuteRoleOwnership.Observation(type, targetId, actorId, reason, createdAt);
    }
}
