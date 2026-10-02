package net.enthusia.staff.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import net.enthusia.staff.domain.auth.StaffRank;
import net.enthusia.staff.domain.player.PlayerPlatform;
import net.enthusia.staff.domain.ports.VanishStore;
import net.enthusia.staff.domain.staff.StaffSessionSnapshot;
import net.enthusia.staff.persistence.MariaDb;
import net.enthusia.staff.persistence.MariaDbRuntime;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class VanishPreferencePersistenceIntegrationTest {
    private static final Instant NOW = Instant.parse("2026-10-01T05:00:00Z");
    private static final String SERVER_ID = "SMP";
    private static final String SNAPSHOT_CHECKSUM = "b".repeat(64);
    private static final String DATABASE_PASSWORD = UUID.randomUUID().toString();

    @Container
    private static final MariaDBContainer<?> DATABASE = new MariaDBContainer<>("mariadb:11.8.3")
            .withDatabaseName("enthusia_staff_vanish_preference_test")
            .withUsername("enthusia_test")
            .withPassword(DATABASE_PASSWORD);

    @Test
    void rememberedVanishSurvivesAutomaticExitCleanupAndRestart() {
        UUID staffId = identifier("remembered-vanish");
        try (MariaDbRuntime runtime = runtimeWithActiveSession(staffId)) {
            VanishStore store = runtime.vanishStore();
            assertEquals(Optional.empty(), store.preferred(staffId));
            assertEquals(
                    VanishStore.WriteResult.COMMITTED,
                    write(store, staffId, true, NOW.plusSeconds(1), true, VanishStore.PreferenceUpdate.SET)
            );
            assertEquals(Optional.of(true), store.preferred(staffId));

            StaffSessionSnapshot exiting = runtime.staffSessionStore()
                    .beginExit(staffId, NOW.plusSeconds(2))
                    .orElseThrow();
            assertTrue(runtime.staffSessionStore().completeExit(
                    exiting.sessionId(), SNAPSHOT_CHECKSUM, NOW.plusSeconds(3)
            ));
            assertEquals(
                    VanishStore.WriteResult.COMMITTED,
                    write(store, staffId, false, NOW.plusSeconds(4), false, VanishStore.PreferenceUpdate.KEEP)
            );
            assertEquals(Optional.of(true), store.preferred(staffId));
        }

        try (MariaDbRuntime restarted = MariaDb.initialize(MariaDbIntegrationSupport.databaseConfig(DATABASE))) {
            assertEquals(Optional.of(true), restarted.vanishStore().preferred(staffId));
            assertTrue(restarted.vanishStore().active(10).stream()
                    .noneMatch(record -> record.staffId().equals(staffId)));
        }
    }

    @Test
    void explicitVisibleChoiceIsRememberedWhileSessionIsActive() {
        UUID staffId = identifier("remembered-visible");
        try (MariaDbRuntime runtime = runtimeWithActiveSession(staffId)) {
            assertEquals(
                    VanishStore.WriteResult.COMMITTED,
                    write(
                            runtime.vanishStore(),
                            staffId,
                            false,
                            NOW.plusSeconds(1),
                            true,
                            VanishStore.PreferenceUpdate.SET
                    )
            );
            assertEquals(Optional.of(false), runtime.vanishStore().preferred(staffId));
        }
    }

    @Test
    void activeReconciliationDoesNotOverwriteRememberedPreference() {
        UUID staffId = identifier("active-reconciliation-keeps-preference");
        try (MariaDbRuntime runtime = runtimeWithActiveSession(staffId)) {
            VanishStore store = runtime.vanishStore();
            write(store, staffId, true, NOW.plusSeconds(1), true, VanishStore.PreferenceUpdate.SET);
            assertEquals(
                    VanishStore.WriteResult.COMMITTED,
                    write(store, staffId, false, NOW.plusSeconds(2), true, VanishStore.PreferenceUpdate.KEEP)
            );
            assertEquals(Optional.of(true), store.preferred(staffId));
        }
    }

    @Test
    void automaticCleanupWithoutPriorChoiceDoesNotInventPreference() {
        UUID staffId = identifier("no-invented-preference");
        try (MariaDbRuntime runtime = runtimeWithPlayer(staffId)) {
            VanishStore store = runtime.vanishStore();
            assertEquals(
                    VanishStore.WriteResult.COMMITTED,
                    write(store, staffId, false, NOW.plusSeconds(1), false, VanishStore.PreferenceUpdate.KEEP)
            );
            assertEquals(Optional.empty(), store.preferred(staffId));
        }
    }

    @Test
    void explicitChoiceWithoutActiveSessionFailsClosed() {
        UUID staffId = identifier("requires-session");
        try (MariaDbRuntime runtime = runtimeWithPlayer(staffId)) {
            VanishStore store = runtime.vanishStore();
            assertEquals(
                    VanishStore.WriteResult.STAFF_SESSION_NOT_ACTIVE,
                    write(store, staffId, true, NOW.plusSeconds(1), true, VanishStore.PreferenceUpdate.SET)
            );
            assertEquals(Optional.empty(), store.preferred(staffId));
        }
    }

    @Test
    void preferenceMutationCannotBypassSessionRequirement() {
        UUID staffId = identifier("preference-requires-session");
        try (MariaDbRuntime runtime = runtimeWithPlayer(staffId)) {
            VanishStore store = runtime.vanishStore();
            assertEquals(
                    VanishStore.WriteResult.STAFF_SESSION_NOT_ACTIVE,
                    write(store, staffId, true, NOW.plusSeconds(1), false, VanishStore.PreferenceUpdate.SET)
            );
            assertEquals(Optional.empty(), store.preferred(staffId));
        }
    }

    private static VanishStore.WriteResult write(
            VanishStore store,
            UUID staffId,
            boolean vanished,
            Instant now,
            boolean requireActiveSession,
            VanishStore.PreferenceUpdate preferenceUpdate
    ) {
        return store.set(
                staffId,
                StaffRank.MOD,
                vanished,
                staffId,
                now,
                requireActiveSession,
                preferenceUpdate
        );
    }

    private static MariaDbRuntime runtimeWithActiveSession(UUID staffId) {
        MariaDbRuntime runtime = runtimeWithPlayer(staffId);
        runtime.staffSessionStore().begin(
                staffId,
                SERVER_ID,
                1,
                SNAPSHOT_CHECKSUM,
                new byte[]{1},
                NOW
        );
        return runtime;
    }

    private static MariaDbRuntime runtimeWithPlayer(UUID staffId) {
        MariaDbRuntime runtime = MariaDb.initialize(MariaDbIntegrationSupport.databaseConfig(DATABASE));
        runtime.playerDirectory().recordSeen(
                staffId,
                playerName(staffId),
                PlayerPlatform.JAVA,
                SERVER_ID,
                NOW
        );
        return runtime;
    }

    private static UUID identifier(String seed) {
        return UUID.nameUUIDFromBytes(seed.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private static String playerName(UUID staffId) {
        return "Vanish" + staffId.toString().replace("-", "").substring(0, 8);
    }
}
