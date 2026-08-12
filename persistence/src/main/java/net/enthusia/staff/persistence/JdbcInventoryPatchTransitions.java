package net.enthusia.staff.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.enthusia.staff.domain.inventory.InventoryOperationState;
import net.enthusia.staff.domain.inventory.InventoryPatch;

final class JdbcInventoryPatchTransitions {
    private static final String STATE_COLUMN = "state";
    private static final String OPERATION_STATE_COLUMN = "operation_state";
    private static final String OPERATION_FENCE_COLUMN = "operation_fencing_token";
    private static final String OPERATION_PROFILE_COLUMN = "operation_profile_id";

    private final ObjectMapper objectMapper;

    JdbcInventoryPatchTransitions(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    InventoryPatch readPatch(ResultSet result) throws SQLException {
        List<Integer> slots = new ArrayList<>();
        try {
            JsonNode root = objectMapper.readTree(result.getString("patch_json"));
            for (JsonNode slot : root.path("changedSlots")) {
                slots.add(slot.intValue());
            }
        } catch (JsonProcessingException exception) {
            throw new SQLException("Inventory patch JSON is invalid", exception);
        }
        return new InventoryPatch(
                UuidBytes.fromBytes(result.getBytes("patch_id")),
                UuidBytes.fromBytes(result.getBytes("operation_id")),
                UuidBytes.fromBytes(result.getBytes("profile_id")),
                UuidBytes.fromBytes(result.getBytes("player_id")),
                result.getString("scope_id"),
                result.getString("owning_server_id"),
                UuidBytes.fromBytes(result.getBytes("actor_id")),
                Optional.ofNullable(result.getString("case_id")),
                result.getString("operation_type"),
                InventoryOperationState.valueOf(result.getString(STATE_COLUMN)),
                result.getLong("expected_revision"),
                result.getLong("fencing_token"),
                result.getString("expected_checksum"),
                result.getString("replacement_checksum"),
                result.getBytes("replacement_blob"),
                slots,
                result.getTimestamp("created_at").toInstant()
        );
    }

    Optional<LockedPatch> lock(Connection connection, UUID patchId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT q.patch_id, q.operation_id, q.profile_id, p.player_id, p.scope_id,
                    p.owning_server_id, o.actor_id, o.case_id, o.operation_type, q.state,
                    q.expected_revision, q.fencing_token, q.expected_checksum,
                    q.replacement_checksum, q.replacement_blob, q.patch_json, q.created_at,
                    o.state AS operation_state,
                    o.fencing_token AS operation_fencing_token,
                    o.profile_id AS operation_profile_id
                FROM inventory_pending_patches q
                JOIN inventory_profiles p ON p.profile_id = q.profile_id
                JOIN inventory_operations o ON o.operation_id = q.operation_id
                WHERE q.patch_id = ?
                FOR UPDATE
                """)) {
            statement.setBytes(1, UuidBytes.toBytes(patchId));
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    return Optional.empty();
                }
                LockedPatch locked = new LockedPatch(
                        readPatch(result),
                        InventoryOperationState.valueOf(result.getString(OPERATION_STATE_COLUMN)),
                        result.getLong(OPERATION_FENCE_COLUMN),
                        UuidBytes.fromBytes(result.getBytes(OPERATION_PROFILE_COLUMN))
                );
                requireCoherent(locked);
                return Optional.of(locked);
            }
        }
    }

    Optional<InventoryPatch> claim(
            Connection connection,
            UUID patchId,
            UUID operationId,
            Duration leaseDuration,
            Instant now
    ) throws SQLException {
        LockedPatch locked = lock(connection, patchId).orElse(null);
        if (locked == null || !locked.patch().operationId().equals(operationId)) {
            return Optional.empty();
        }
        InventoryPatch patch = locked.patch();
        if (patch.state() == InventoryOperationState.APPLIED) {
            return Optional.of(patch);
        }
        if (!claimable(patch.state())) {
            return Optional.empty();
        }
        if (patch.state() == InventoryOperationState.APPLYING && ownsLease(connection, patch, now)) {
            return Optional.of(patch);
        }
        long fence = JdbcOperationLeaseSupport.acquireAfter(
                connection,
                resourceKey(patch),
                patch.operationId(),
                patch.fencingToken(),
                now.plus(leaseDuration),
                now
        );
        if (fence == JdbcOperationLeaseSupport.UNAVAILABLE) {
            return Optional.empty();
        }
        transitionToApplying(connection, locked, fence, now);
        return Optional.of(copyWithFence(patch, fence, InventoryOperationState.APPLYING));
    }

    boolean ownsLease(Connection connection, InventoryPatch patch, Instant now) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT owner_id, fencing_token, lease_until
                FROM operation_leases
                WHERE resource_key = ?
                FOR UPDATE
                """)) {
            statement.setString(1, resourceKey(patch));
            try (ResultSet result = statement.executeQuery()) {
                return result.next()
                        && result.getString("owner_id").equals(patch.operationId().toString())
                        && result.getLong("fencing_token") == patch.fencingToken()
                        && result.getTimestamp("lease_until").toInstant().isAfter(now);
            }
        }
    }

    void markApplied(Connection connection, LockedPatch locked, Instant now) throws SQLException {
        InventoryPatch patch = locked.patch();
        if (patch.state() != InventoryOperationState.APPLYING) {
            throw new SQLException("Only an applying inventory patch can be committed");
        }
        try (PreparedStatement pending = connection.prepareStatement("""
                UPDATE inventory_pending_patches
                SET state = 'APPLIED', applied_at = ?, conflict_code = NULL, conflict_detail = NULL
                WHERE patch_id = ? AND operation_id = ?
                    AND state = 'APPLYING' AND fencing_token = ?
                """);
             PreparedStatement operation = connection.prepareStatement("""
                UPDATE inventory_operations
                SET state = 'COMMITTED', updated_at = ?
                WHERE operation_id = ? AND state = 'APPLYING' AND fencing_token = ?
                """)) {
            pending.setTimestamp(1, Timestamp.from(now));
            pending.setBytes(2, UuidBytes.toBytes(patch.patchId()));
            pending.setBytes(3, UuidBytes.toBytes(patch.operationId()));
            pending.setLong(4, patch.fencingToken());
            JdbcTransactionSupport.requireSingleUpdate(
                    pending.executeUpdate(),
                    "Inventory patch state changed before commit"
            );

            operation.setTimestamp(1, Timestamp.from(now));
            operation.setBytes(2, UuidBytes.toBytes(patch.operationId()));
            operation.setLong(3, patch.fencingToken());
            JdbcTransactionSupport.requireSingleUpdate(
                    operation.executeUpdate(),
                    "Inventory operation state changed before commit"
            );
        }
    }

    void quarantine(
            Connection connection,
            LockedPatch locked,
            String reasonCode,
            String detail,
            Instant now
    ) throws SQLException {
        InventoryPatch patch = locked.patch();
        if (!claimable(patch.state())) {
            throw new SQLException("Only a pending inventory patch can be quarantined");
        }
        transitionToQuarantined(connection, locked, reasonCode, detail, now);
        upsertQuarantine(connection, patch, reasonCode, detail, now);
        releaseLease(connection, patch);
    }

    boolean hasBlockingPatch(Connection connection, UUID profileId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT 1
                FROM inventory_pending_patches
                WHERE profile_id = ? AND state IN ('PENDING', 'APPLYING', 'QUARANTINED')
                LIMIT 1
                FOR UPDATE
                """)) {
            statement.setBytes(1, UuidBytes.toBytes(profileId));
            try (ResultSet result = statement.executeQuery()) {
                return result.next();
            }
        }
    }

    boolean isLocked(
            Connection connection,
            UUID playerId,
            String scopeId,
            Instant now
    ) throws SQLException {
        if (playerId == null || scopeId == null || scopeId.isBlank() || now == null) {
            throw new IllegalArgumentException("inventory lock lookup is invalid");
        }
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT
                    EXISTS (
                        SELECT 1
                        FROM operation_leases
                        WHERE resource_key = ? AND lease_until > ?
                    ) AS has_live_lease,
                    EXISTS (
                        SELECT 1
                        FROM inventory_pending_patches q
                        JOIN inventory_profiles p ON p.profile_id = q.profile_id
                        WHERE p.player_id = ? AND p.scope_id = ?
                            AND q.state IN ('PENDING', 'APPLYING', 'QUARANTINED')
                    ) AS has_blocking_patch
                """)) {
            statement.setString(1, resourceKey(playerId, scopeId));
            statement.setTimestamp(2, Timestamp.from(now));
            statement.setBytes(3, UuidBytes.toBytes(playerId));
            statement.setString(4, scopeId);
            try (ResultSet result = statement.executeQuery()) {
                return result.next()
                        && (result.getBoolean("has_live_lease")
                        || result.getBoolean("has_blocking_patch"));
            }
        }
    }

    void releaseLease(Connection connection, InventoryPatch patch) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                DELETE FROM operation_leases
                WHERE resource_key = ? AND owner_id = ? AND fencing_token = ?
                """)) {
            statement.setString(1, resourceKey(patch));
            statement.setString(2, patch.operationId().toString());
            statement.setLong(3, patch.fencingToken());
            JdbcTransactionSupport.requireOptionalSingleUpdate(
                    statement.executeUpdate(),
                    "Multiple inventory leases matched one fence"
            );
        }
    }

    private static void transitionToApplying(
            Connection connection,
            LockedPatch locked,
            long nextFence,
            Instant now
    ) throws SQLException {
        InventoryPatch patch = locked.patch();
        try (PreparedStatement pending = connection.prepareStatement("""
                UPDATE inventory_pending_patches
                SET state = 'APPLYING', fencing_token = ?
                WHERE patch_id = ? AND operation_id = ?
                    AND state = ? AND fencing_token = ?
                """);
             PreparedStatement operation = connection.prepareStatement("""
                UPDATE inventory_operations
                SET state = 'APPLYING', fencing_token = ?, updated_at = ?
                WHERE operation_id = ? AND state = ? AND fencing_token = ?
                """)) {
            pending.setLong(1, nextFence);
            pending.setBytes(2, UuidBytes.toBytes(patch.patchId()));
            pending.setBytes(3, UuidBytes.toBytes(patch.operationId()));
            pending.setString(4, patch.state().name());
            pending.setLong(5, patch.fencingToken());
            JdbcTransactionSupport.requireSingleUpdate(
                    pending.executeUpdate(),
                    "Inventory patch state changed during claim"
            );

            operation.setLong(1, nextFence);
            operation.setTimestamp(2, Timestamp.from(now));
            operation.setBytes(3, UuidBytes.toBytes(patch.operationId()));
            operation.setString(4, locked.operationState().name());
            operation.setLong(5, locked.operationFencingToken());
            JdbcTransactionSupport.requireSingleUpdate(
                    operation.executeUpdate(),
                    "Inventory operation state changed during claim"
            );
        }
    }

    private static void transitionToQuarantined(
            Connection connection,
            LockedPatch locked,
            String reasonCode,
            String detail,
            Instant now
    ) throws SQLException {
        InventoryPatch patch = locked.patch();
        try (PreparedStatement pending = connection.prepareStatement("""
                UPDATE inventory_pending_patches
                SET state = 'QUARANTINED', conflict_code = ?, conflict_detail = ?
                WHERE patch_id = ? AND operation_id = ?
                    AND state = ? AND fencing_token = ?
                """);
             PreparedStatement operation = connection.prepareStatement("""
                UPDATE inventory_operations
                SET state = 'QUARANTINED', updated_at = ?
                WHERE operation_id = ? AND state = ? AND fencing_token = ?
                """)) {
            pending.setString(1, reasonCode);
            pending.setString(2, detail);
            pending.setBytes(3, UuidBytes.toBytes(patch.patchId()));
            pending.setBytes(4, UuidBytes.toBytes(patch.operationId()));
            pending.setString(5, patch.state().name());
            pending.setLong(6, patch.fencingToken());
            JdbcTransactionSupport.requireSingleUpdate(
                    pending.executeUpdate(),
                    "Inventory patch state changed before quarantine"
            );

            operation.setTimestamp(1, Timestamp.from(now));
            operation.setBytes(2, UuidBytes.toBytes(patch.operationId()));
            operation.setString(3, locked.operationState().name());
            operation.setLong(4, locked.operationFencingToken());
            JdbcTransactionSupport.requireSingleUpdate(
                    operation.executeUpdate(),
                    "Inventory operation state changed before quarantine"
            );
        }
    }

    private void upsertQuarantine(
            Connection connection,
            InventoryPatch patch,
            String reasonCode,
            String detail,
            Instant now
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO recovery_quarantine(
                    quarantine_id, operation_type, operation_id, resource_key,
                    reason_code, detail_json, quarantined_at
                ) VALUES (?, 'INVENTORY', ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE reason_code = VALUES(reason_code),
                    detail_json = VALUES(detail_json), quarantined_at = VALUES(quarantined_at),
                    resolved_at = NULL, resolved_by = NULL, resolution_json = NULL
                """)) {
            statement.setBytes(1, UuidBytes.toBytes(UUID.randomUUID()));
            statement.setBytes(2, UuidBytes.toBytes(patch.operationId()));
            statement.setString(3, resourceKey(patch));
            statement.setString(4, reasonCode);
            statement.setString(5, serialize(Map.of(
                    "detail", detail,
                    "patchId", patch.patchId().toString()
            )));
            statement.setTimestamp(6, Timestamp.from(now));
            statement.executeUpdate();
        }
    }

    private String serialize(Map<String, ?> values) throws SQLException {
        try {
            return objectMapper.writeValueAsString(new LinkedHashMap<>(values));
        } catch (JsonProcessingException exception) {
            throw new SQLException("Unable to serialize inventory quarantine metadata", exception);
        }
    }

    private static void requireCoherent(LockedPatch locked) throws SQLException {
        InventoryPatch patch = locked.patch();
        boolean statesMatch = patch.state() == InventoryOperationState.APPLIED
                ? locked.operationState() == InventoryOperationState.COMMITTED
                : patch.state() == locked.operationState();
        if (!statesMatch) {
            throw new SQLException("Inventory patch and operation states diverged");
        }
        if (patch.fencingToken() != locked.operationFencingToken()) {
            throw new SQLException("Inventory patch and operation fencing tokens diverged");
        }
        if (!patch.profileId().equals(locked.operationProfileId())) {
            throw new SQLException("Inventory patch and operation profiles diverged");
        }
    }

    private static boolean claimable(InventoryOperationState state) {
        return state == InventoryOperationState.PENDING
                || state == InventoryOperationState.APPLYING;
    }

    private static String resourceKey(InventoryPatch patch) {
        return resourceKey(patch.playerId(), patch.scopeId());
    }

    private static String resourceKey(UUID playerId, String scopeId) {
        return "inventory:" + playerId + ':' + scopeId;
    }

    private static InventoryPatch copyWithFence(
            InventoryPatch patch,
            long fencingToken,
            InventoryOperationState state
    ) {
        return new InventoryPatch(
                patch.patchId(),
                patch.operationId(),
                patch.profileId(),
                patch.playerId(),
                patch.scopeId(),
                patch.owningServerId(),
                patch.actorId(),
                patch.caseId(),
                patch.operationType(),
                state,
                patch.expectedRevision(),
                fencingToken,
                patch.expectedChecksum(),
                patch.replacementChecksum(),
                patch.replacementSnapshot(),
                patch.changedSlots(),
                patch.createdAt()
        );
    }

    record LockedPatch(
            InventoryPatch patch,
            InventoryOperationState operationState,
            long operationFencingToken,
            UUID operationProfileId
    ) {
    }
}
