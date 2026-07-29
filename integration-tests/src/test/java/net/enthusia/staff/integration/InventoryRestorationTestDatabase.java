package net.enthusia.staff.integration;

import static net.enthusia.staff.integration.MariaDbIntegrationSupport.connection;
import static net.enthusia.staff.integration.MariaDbIntegrationSupport.uuidBytes;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import net.enthusia.staff.common.CaseId;
import net.enthusia.staff.domain.inventory.InventoryObservation;
import org.testcontainers.containers.MariaDBContainer;

final class InventoryRestorationTestDatabase {
    private static final int EXPECTED_UPDATE_COUNT = 1;

    private final MariaDBContainer<?> database;
    private final Instant now;

    InventoryRestorationTestDatabase(MariaDBContainer<?> database, Instant now) {
        this.database = database;
        this.now = now;
    }

    void insertCase(CaseId caseId, UUID targetId, UUID actorId) throws SQLException {
        MariaDbIntegrationSupport.insertCase(database, caseId.value(), targetId, actorId, now);
    }

    void insertOperation(
            UUID operationId,
            InventoryObservation observation,
            CaseId caseId,
            UUID actorId,
            String operationType,
            String state
    ) throws SQLException {
        try (Connection connection = connection(database);
             PreparedStatement statement = connection.prepareStatement("""
                     INSERT INTO inventory_operations(
                         operation_id, idempotency_key, profile_id, case_id, actor_id,
                         operation_type, state, expected_revision, fencing_token,
                         operation_json, created_at, updated_at
                     ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, 1, '{}', ?, ?)
                     """)) {
            statement.setBytes(1, uuidBytes(operationId));
            statement.setString(2, "inventory:corrupt-test:" + operationId);
            statement.setBytes(3, uuidBytes(observation.profileId()));
            statement.setString(4, caseId.value());
            statement.setBytes(5, uuidBytes(actorId));
            statement.setString(6, operationType);
            statement.setString(7, state);
            statement.setLong(8, observation.revision());
            statement.setTimestamp(9, Timestamp.from(now.plusSeconds(9)));
            statement.setTimestamp(10, Timestamp.from(now.plusSeconds(9)));
            statement.executeUpdate();
        }
    }

    UUID insertAppliedPatch(
            UUID operationId,
            InventoryObservation observation,
            CaseId caseId,
            UUID actorId
    ) throws SQLException {
        UUID patchId = UUID.randomUUID();
        try (Connection connection = connection(database);
             PreparedStatement statement = connection.prepareStatement("""
                     INSERT INTO inventory_pending_patches(
                         patch_id, operation_id, profile_id, expected_revision,
                         expected_checksum, replacement_checksum, replacement_blob,
                         actor_id, case_id, owning_server_id, fencing_token,
                         state, patch_json, created_at, applied_at
                     ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 1,
                         'APPLIED', '{"changedSlots":[1]}', ?, ?)
                     """)) {
            statement.setBytes(1, uuidBytes(patchId));
            statement.setBytes(2, uuidBytes(operationId));
            statement.setBytes(3, uuidBytes(observation.profileId()));
            statement.setLong(4, observation.revision());
            statement.setString(5, observation.checksum());
            statement.setString(6, observation.checksum());
            statement.setBytes(7, observation.snapshot());
            statement.setBytes(8, uuidBytes(actorId));
            statement.setString(9, caseId.value());
            statement.setString(10, observation.owningServerId());
            statement.setTimestamp(11, Timestamp.from(now.plusSeconds(9)));
            statement.setTimestamp(12, Timestamp.from(now.plusSeconds(9)));
            statement.executeUpdate();
        }
        return patchId;
    }

    void updateOperationType(UUID operationId, String operationType) throws SQLException {
        try (Connection connection = connection(database);
             PreparedStatement statement = connection.prepareStatement("""
                     UPDATE inventory_operations
                     SET operation_type = ?
                     WHERE operation_id = ?
                     """)) {
            statement.setString(1, operationType);
            statement.setBytes(2, uuidBytes(operationId));
            if (statement.executeUpdate() != EXPECTED_UPDATE_COUNT) {
                throw new SQLException("Restoration test operation was not updated");
            }
        }
    }

    long unreservedSnapshotCount(CaseId caseId) throws SQLException {
        try (Connection connection = connection(database);
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT COUNT(*)
                     FROM confiscated_asset_snapshots
                     WHERE case_id = ? AND restoration_operation_id IS NULL
                         AND restored_at IS NULL
                     """)) {
            statement.setString(1, caseId.value());
            return singleLong(statement);
        }
    }

    long reservedSnapshotCount(CaseId caseId, UUID operationId) throws SQLException {
        try (Connection connection = connection(database);
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT COUNT(*)
                     FROM confiscated_asset_snapshots
                     WHERE case_id = ? AND restoration_operation_id = ?
                         AND restoration_state = 'RESERVED' AND restored_at IS NULL
                     """)) {
            statement.setString(1, caseId.value());
            statement.setBytes(2, uuidBytes(operationId));
            return singleLong(statement);
        }
    }

    long operationCount(UUID operationId) throws SQLException {
        try (Connection connection = connection(database);
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT COUNT(*) FROM inventory_operations WHERE operation_id = ?
                     """)) {
            statement.setBytes(1, uuidBytes(operationId));
            return singleLong(statement);
        }
    }

    long appliedSnapshotCount(
            CaseId caseId,
            UUID operationId,
            String restoredChecksum
    ) throws SQLException {
        try (Connection connection = connection(database);
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT COUNT(*)
                     FROM confiscated_asset_snapshots
                     WHERE case_id = ? AND restoration_operation_id = ?
                         AND restoration_state = 'APPLIED'
                         AND restored_at IS NOT NULL AND restored_checksum = ?
                     """)) {
            statement.setString(1, caseId.value());
            statement.setBytes(2, uuidBytes(operationId));
            statement.setString(3, restoredChecksum);
            return singleLong(statement);
        }
    }

    private static long singleLong(PreparedStatement statement) throws SQLException {
        try (ResultSet result = statement.executeQuery()) {
            result.next();
            return result.getLong(1);
        }
    }
}
