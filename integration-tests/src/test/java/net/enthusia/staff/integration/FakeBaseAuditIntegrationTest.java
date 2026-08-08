package net.enthusia.staff.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.util.UUID;
import net.enthusia.staff.domain.ports.FakeBaseAuditStore;
import net.enthusia.staff.domain.tester.FakeBaseAuditAction;
import net.enthusia.staff.domain.tester.FakeBaseAuditEvent;
import net.enthusia.staff.persistence.MariaDb;
import net.enthusia.staff.persistence.MariaDbRuntime;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class FakeBaseAuditIntegrationTest {
    private static final Instant NOW = Instant.parse("2026-08-07T21:00:00Z");
    private static final UUID STAFF = UUID.fromString("9b000000-0000-0000-0000-000000000001");
    private static final UUID TARGET = UUID.fromString("9b000000-0000-0000-0000-000000000002");
    private static final UUID OPERATION = UUID.fromString("9b000000-0000-0000-0000-000000000003");
    private static final UUID EVENT = UUID.fromString("9b000000-0000-0000-0000-000000000004");

    @Container
    private static final MariaDBContainer<?> DATABASE = new MariaDBContainer<>("mariadb:11.8.3")
            .withDatabaseName("enthusia_staff_fake_base_test")
            .withUsername("enthusia_test")
            .withPassword("enthusia_test_password");

    @Test
    void existingAuditLedgerPersistsCoordinateFreeFakeBaseLifecycleWithoutMigration() throws Exception {
        try (MariaDbRuntime runtime = MariaDb.initialize(MariaDbIntegrationSupport.databaseConfig(DATABASE))) {
            MariaDbIntegrationSupport.insertPlayer(DATABASE, STAFF, "FakeBaseStaff", NOW);
            MariaDbIntegrationSupport.insertPlayer(DATABASE, TARGET, "FakeBaseTarget", NOW);
            FakeBaseAuditStore store = assertInstanceOf(FakeBaseAuditStore.class, runtime.inventoryJournalStore());
            store.record(new FakeBaseAuditEvent(
                    EVENT,
                    OPERATION,
                    "SMP",
                    STAFF,
                    TARGET,
                    FakeBaseAuditAction.CREATED,
                    "COMMITTED",
                    "VIRTUAL_RENDERED",
                    NOW
            ));
        }

        try (Connection connection = MariaDbIntegrationSupport.connection(DATABASE);
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT correlation_id, actor_id, target_id, event_type, outcome, event_json,
                            JSON_VALUE(event_json, '$.serverId') AS server_id,
                            JSON_VALUE(event_json, '$.reasonCode') AS reason_code
                     FROM audit_events
                     WHERE event_id = ?
                     """)) {
            statement.setBytes(1, uuidBytes(EVENT));
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                assertEquals(OPERATION, uuid(result.getBytes("correlation_id")));
                assertEquals(STAFF, uuid(result.getBytes("actor_id")));
                assertEquals(TARGET, uuid(result.getBytes("target_id")));
                assertEquals("FAKE_BASE_CREATED", result.getString("event_type"));
                assertEquals("COMMITTED", result.getString("outcome"));
                assertEquals("SMP", result.getString("server_id"));
                assertEquals("VIRTUAL_RENDERED", result.getString("reason_code"));
                String eventJson = result.getString("event_json");
                assertFalse(eventJson.contains("coordinate"));
                assertFalse(eventJson.contains("location"));
            }
        }
        assertEquals(18, latestMigrationVersion());
    }

    private static int latestMigrationVersion() throws Exception {
        try (Connection connection = MariaDbIntegrationSupport.connection(DATABASE);
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT MAX(CAST(version AS UNSIGNED)) FROM flyway_schema_history WHERE success = 1"
             )) {
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getInt(1);
            }
        }
    }

    private static byte[] uuidBytes(UUID value) {
        byte[] bytes = new byte[16];
        java.nio.ByteBuffer.wrap(bytes)
                .putLong(value.getMostSignificantBits())
                .putLong(value.getLeastSignificantBits());
        return bytes;
    }

    private static UUID uuid(byte[] bytes) {
        java.nio.ByteBuffer buffer = java.nio.ByteBuffer.wrap(bytes);
        return new UUID(buffer.getLong(), buffer.getLong());
    }
}
