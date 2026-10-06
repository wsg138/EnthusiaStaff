package net.enthusia.staff.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import net.enthusia.staff.domain.ports.StaffPreferenceStore;
import net.enthusia.staff.persistence.MariaDb;
import net.enthusia.staff.persistence.MariaDbRuntime;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class StaffPreferencePersistenceIntegrationTest {
    private static final String DATABASE_PASSWORD = UUID.randomUUID().toString();
    private static final UUID STAFF_ID =
            UUID.fromString("50000000-0000-0000-0000-000000000001");
    private static final Instant NOW = Instant.parse("2026-10-06T20:00:00Z");

    @Container
    private static final MariaDBContainer<?> DATABASE = new MariaDBContainer<>("mariadb:11.8.3")
            .withDatabaseName("enthusia_staff_preferences_test")
            .withUsername("enthusia_test")
            .withPassword(DATABASE_PASSWORD);

    @Test
    void inventoryPreferencePersistsAcrossRuntimeRestartUntilChanged() {
        try (MariaDbRuntime runtime = MariaDb.initialize(MariaDbIntegrationSupport.databaseConfig(DATABASE))) {
            StaffPreferenceStore store = runtime.staffPreferenceStore();
            assertEquals(Optional.empty(), store.toolInventoryEnabled(STAFF_ID));
            store.setToolInventoryEnabled(STAFF_ID, false, NOW);
            assertEquals(Optional.of(false), store.toolInventoryEnabled(STAFF_ID));
        }

        try (MariaDbRuntime restarted = MariaDb.initialize(MariaDbIntegrationSupport.databaseConfig(DATABASE))) {
            StaffPreferenceStore store = restarted.staffPreferenceStore();
            assertEquals(Optional.of(false), store.toolInventoryEnabled(STAFF_ID));
            store.setToolInventoryEnabled(STAFF_ID, true, NOW.plusSeconds(1));
            assertEquals(Optional.of(true), store.toolInventoryEnabled(STAFF_ID));
        }

        try (MariaDbRuntime restartedAgain = MariaDb.initialize(MariaDbIntegrationSupport.databaseConfig(DATABASE))) {
            assertEquals(
                    Optional.of(true),
                    restartedAgain.staffPreferenceStore().toolInventoryEnabled(STAFF_ID)
            );
        }
    }
}
