package net.enthusia.staff.integration;

import static net.enthusia.staff.integration.InventoryRestorationTestSupport.checksum;
import static net.enthusia.staff.integration.MariaDbIntegrationSupport.databaseConfig;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import net.enthusia.staff.domain.player.PlayerPlatform;
import net.enthusia.staff.persistence.MariaDb;
import net.enthusia.staff.persistence.MariaDbRuntime;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class InventoryPlayerRegistrationRaceIntegrationTest {
    private static final Instant NOW = Instant.parse("2026-09-24T03:45:00Z");

    @Container
    private static final MariaDBContainer<?> DATABASE = new MariaDBContainer<>("mariadb:11.8.3")
            .withDatabaseName("enthusia_staff_inventory_player_race")
            .withUsername("enthusia_test")
            .withPassword("enthusia_test_password");

    @Test
    void observationAndPlayerRegistrationCanCommitConcurrentlyWithoutFkFailure() throws Exception {
        UUID playerId = UUID.randomUUID();
        byte[] snapshot = {4, 2, 4, 2};
        CountDownLatch start = new CountDownLatch(1);

        try (MariaDbRuntime runtime = MariaDb.initialize(databaseConfig(DATABASE));
             ExecutorService workers = Executors.newFixedThreadPool(2)) {
            Future<?> observation = workers.submit(() -> {
                await(start);
                runtime.inventoryJournalStore().recordObservation(
                        playerId,
                        "survival",
                        "paper-race",
                        checksum(snapshot),
                        snapshot,
                        NOW
                );
            });
            Future<?> registration = workers.submit(() -> {
                await(start);
                runtime.playerDirectory().recordSeen(
                        playerId,
                        "RaceTarget",
                        PlayerPlatform.UNKNOWN,
                        "paper-race",
                        NOW.plusMillis(1)
                );
            });

            start.countDown();
            observation.get(10, TimeUnit.SECONDS);
            registration.get(10, TimeUnit.SECONDS);

            var player = runtime.playerDirectory().find(playerId.toString()).orElseThrow();
            assertEquals("RaceTarget", player.currentUsername().orElseThrow());
            assertTrue(runtime.inventoryJournalStore().latest(playerId, "survival").isPresent());
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new AssertionError("race start latch timed out");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError("race start interrupted", exception);
        }
    }
}
