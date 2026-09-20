package net.enthusia.staff.discordbot;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
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
    void channelPermissionManagementFailsClosedBeforeMutation() {
        DiscordPunishmentGateway.EffectException failure = assertThrows(
                DiscordPunishmentGateway.EffectException.class,
                () -> JdaDiscordPunishmentGateway.requirePermissionManagement(false)
        );

        assertEquals("CHANNEL_PERMISSION_MANAGE_DENIED", failure.errorCode());
        assertFalse(failure.retryable());
        assertDoesNotThrow(() -> JdaDiscordPunishmentGateway.requirePermissionManagement(true));
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

    @Test
    void currentMuteOwnershipRequiresLatestRoleChangeToBeOwnedAssignment() {
        long roleId = 789L;
        String ownedReason = JdaMuteRoleOwnership.marker(PUNISHMENT_ID) + " reason";
        JdaMuteRoleOwnership.Observation owned = roleObservation(
                TARGET_ID, BOT_ID, ownedReason, ISSUED_AT.plusSeconds(1), roleId
        );
        JdaMuteRoleOwnership.Observation external = roleObservation(
                TARGET_ID, BOT_ID + 1, "manual mute", ISSUED_AT.plusSeconds(2), roleId
        );

        assertTrue(currentlyOwned(List.of(owned), roleId));
        assertFalse(currentlyOwned(List.of(owned, external), roleId));
        assertFalse(currentlyOwned(List.of(external), roleId));
    }

    @Test
    void currentMuteOwnershipSearchesBeyondFirstAuditPage() {
        long roleId = 789L;
        List<JdaMuteRoleOwnership.Observation> observations = unrelatedNewerObservations(roleId, 125);
        observations.add(roleObservation(
                TARGET_ID,
                BOT_ID,
                JdaMuteRoleOwnership.marker(PUNISHMENT_ID) + " reason",
                ISSUED_AT.plusSeconds(1),
                roleId
        ));

        assertTrue(currentlyOwnedNewestFirst(observations, roleId));
    }

    @Test
    void currentMuteOwnershipRejectsNewerExternalChangeAcrossPages() {
        long roleId = 789L;
        List<JdaMuteRoleOwnership.Observation> observations = unrelatedNewerObservations(roleId, 125);
        observations.add(roleObservation(
                TARGET_ID, BOT_ID + 1, "manual mute", ISSUED_AT.plusSeconds(2), roleId
        ));
        observations.add(roleObservation(
                TARGET_ID,
                BOT_ID,
                JdaMuteRoleOwnership.marker(PUNISHMENT_ID) + " reason",
                ISSUED_AT.plusSeconds(1),
                roleId
        ));

        assertFalse(currentlyOwnedNewestFirst(observations, roleId));
    }

    @Test
    void currentMuteOwnershipRejectsNewerExternalRemovalAcrossPages() {
        long roleId = 789L;
        List<JdaMuteRoleOwnership.Observation> observations = unrelatedNewerObservations(roleId, 125);
        observations.add(roleRemovalObservation(
                TARGET_ID, BOT_ID + 1, "manual unmute", ISSUED_AT.plusSeconds(2), roleId
        ));
        observations.add(roleObservation(
                TARGET_ID,
                BOT_ID,
                JdaMuteRoleOwnership.marker(PUNISHMENT_ID) + " reason",
                ISSUED_AT.plusSeconds(1),
                roleId
        ));

        assertFalse(currentlyOwnedNewestFirst(observations, roleId));
    }

    @Test
    void currentMuteOwnershipDoesNotSearchBeforeIssuanceBoundary() {
        long roleId = 789L;
        List<JdaMuteRoleOwnership.Observation> observations = new ArrayList<>();
        observations.add(roleObservation(
                TARGET_ID + 1, BOT_ID + 1, "unrelated", ISSUED_AT.plusSeconds(1), roleId
        ));
        observations.add(roleObservation(
                TARGET_ID,
                BOT_ID,
                JdaMuteRoleOwnership.marker(PUNISHMENT_ID) + " stale",
                ISSUED_AT.minusSeconds(120),
                roleId
        ));

        assertFalse(currentlyOwnedNewestFirst(observations, roleId));
    }

    private static List<JdaMuteRoleOwnership.Observation> unrelatedNewerObservations(long roleId, int count) {
        List<JdaMuteRoleOwnership.Observation> observations = new ArrayList<>(count + 1);
        for (int index = 0; index < count; index++) {
            observations.add(roleObservation(
                    TARGET_ID + index + 1,
                    BOT_ID + 1,
                    "unrelated",
                    ISSUED_AT.plusSeconds(count - index + 10L),
                    roleId
            ));
        }
        return observations;
    }

    private static boolean currentlyOwned(List<JdaMuteRoleOwnership.Observation> observations, long roleId) {
        return JdaMuteRoleOwnership.provesCurrentOwnership(
                observations, PUNISHMENT_ID, TARGET_ID, BOT_ID, roleId, ISSUED_AT
        );
    }

    private static boolean currentlyOwnedNewestFirst(
            List<JdaMuteRoleOwnership.Observation> observations,
            long roleId
    ) {
        return JdaMuteRoleOwnership.provesCurrentOwnershipNewestFirst(
                observations.stream(), PUNISHMENT_ID, TARGET_ID, BOT_ID, roleId, ISSUED_AT
        );
    }

    private static JdaMuteRoleOwnership.Observation roleObservation(
            long targetId,
            long actorId,
            String reason,
            Instant createdAt,
            long roleId
    ) {
        return new JdaMuteRoleOwnership.Observation(
                ActionType.MEMBER_ROLE_UPDATE,
                targetId,
                actorId,
                reason,
                createdAt,
                Set.of(roleId),
                Set.of()
        );
    }

    private static JdaMuteRoleOwnership.Observation roleRemovalObservation(
            long targetId,
            long actorId,
            String reason,
            Instant createdAt,
            long roleId
    ) {
        return new JdaMuteRoleOwnership.Observation(
                ActionType.MEMBER_ROLE_UPDATE,
                targetId,
                actorId,
                reason,
                createdAt,
                Set.of(),
                Set.of(roleId)
        );
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
