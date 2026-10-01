package net.enthusia.staff.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;
import net.enthusia.staff.common.CaseId;
import net.enthusia.staff.domain.inventory.ConfiscatedAssetReservation;
import net.enthusia.staff.domain.inventory.InventoryConfiscationCommitRequest;
import net.enthusia.staff.domain.inventory.InventoryConfiscationSession;
import net.enthusia.staff.domain.inventory.InventoryConfiscationStart;
import net.enthusia.staff.domain.inventory.InventoryConfiscationStartRequest;
import net.enthusia.staff.domain.inventory.InventoryCursorJournal;
import net.enthusia.staff.domain.inventory.InventoryCursorPhase;
import net.enthusia.staff.domain.inventory.InventoryCursorTransfer;
import net.enthusia.staff.domain.inventory.InventoryFinalizeResult;
import net.enthusia.staff.domain.inventory.InventoryObservation;
import net.enthusia.staff.domain.inventory.InventoryOperationState;
import net.enthusia.staff.domain.inventory.InventoryPatch;
import net.enthusia.staff.domain.inventory.InventoryPreparation;
import net.enthusia.staff.domain.inventory.InventoryPrepareRequest;
import net.enthusia.staff.domain.ports.InventoryJournalStore;

/** Ensures inventory FK parents and owns cursor-escrow metadata stored in existing operation JSON. */
public final class PlayerRowEnsuringInventoryJournalStore implements InventoryJournalStore {
    private static final String CURSOR_TRANSFER_FIELD = "cursorTransfer";
    private static final String CURSOR_PHASE_FIELD = "cursorPhase";
    private static final String ONLINE_CURSOR_PREFIX = "ONLINE_CURSOR_";
    private static final int MAX_CURSOR_QUERY = 32;

    private final DataSource dataSource;
    private final InventoryJournalStore delegate;
    private final ObjectMapper json = new ObjectMapper();

    public PlayerRowEnsuringInventoryJournalStore(DataSource dataSource, InventoryJournalStore delegate) {
        this.dataSource = java.util.Objects.requireNonNull(dataSource, "dataSource");
        this.delegate = java.util.Objects.requireNonNull(delegate, "delegate");
    }

    @Override
    public InventoryConfiscationStart beginConfiscation(
            InventoryConfiscationStartRequest request,
            Duration leaseDuration,
            Instant now
    ) {
        if (request == null) {
            return delegate.beginConfiscation(null, leaseDuration, now);
        }
        return InventoryPlayerParentContext.withPlayer(
                request.playerId(), now, () -> delegate.beginConfiscation(request, leaseDuration, now)
        );
    }

    @Override
    public Optional<InventoryConfiscationSession> renewConfiscation(
            UUID operationId, long fencingToken, Duration leaseDuration, Instant now
    ) {
        return delegate.renewConfiscation(operationId, fencingToken, leaseDuration, now);
    }

    @Override
    public InventoryPreparation prepareConfiscation(InventoryConfiscationCommitRequest request, Instant now) {
        return delegate.prepareConfiscation(request, now);
    }

    @Override
    public boolean cancelConfiscation(
            UUID operationId, long fencingToken, String reasonCode, String detail, Instant now
    ) {
        return delegate.cancelConfiscation(operationId, fencingToken, reasonCode, detail, now);
    }

    @Override
    public int cancelAbandonedConfiscations(
            UUID playerId, String scopeId, String owningServerId, Instant now
    ) {
        return delegate.cancelAbandonedConfiscations(playerId, scopeId, owningServerId, now);
    }

    @Override
    public ConfiscatedAssetReservation reserveRestoration(CaseId caseId, UUID operationId, Instant now) {
        return delegate.reserveRestoration(caseId, operationId, now);
    }

    @Override
    public boolean cancelRestoration(CaseId caseId, UUID operationId, Instant now) {
        return delegate.cancelRestoration(caseId, operationId, now);
    }

    @Override
    public boolean finalizeRestoration(
            CaseId caseId, UUID operationId, String restoredChecksum, Instant now
    ) {
        return delegate.finalizeRestoration(caseId, operationId, restoredChecksum, now);
    }

    @Override
    public InventoryObservation recordObservation(
            UUID playerId,
            String scopeId,
            String owningServerId,
            String checksum,
            byte[] snapshot,
            Instant observedAt
    ) {
        return InventoryPlayerParentContext.withPlayer(
                playerId,
                observedAt,
                () -> delegate.recordObservation(playerId, scopeId, owningServerId, checksum, snapshot, observedAt)
        );
    }

    @Override
    public Optional<InventoryObservation> latest(UUID playerId, String scopeId) {
        return delegate.latest(playerId, scopeId);
    }

    @Override
    public InventoryPreparation prepare(InventoryPrepareRequest request, Duration leaseDuration, Instant now) {
        InventoryPreparation result = delegate.prepare(request, leaseDuration, now);
        if (request != null && request.cursorTransfer().isPresent() && result.patch().isPresent()) {
            persistCursorMetadata(
                    result.patch().orElseThrow(),
                    request.cursorTransfer().orElseThrow(),
                    now
            );
        }
        return result;
    }

    @Override
    public List<InventoryPatch> pending(UUID playerId, String scopeId, String owningServerId, int limit) {
        return delegate.pending(playerId, scopeId, owningServerId, limit);
    }

    @Override
    public List<InventoryCursorJournal> pendingCursorTransfersByActor(
            UUID actorId,
            String owningServerId,
            int limit
    ) {
        if (actorId == null || owningServerId == null || owningServerId.isBlank()
                || limit < 1 || limit > MAX_CURSOR_QUERY) {
            throw new IllegalArgumentException("cursor transfer actor query is invalid");
        }
        return queryCursorJournals("""
                WHERE o.actor_id = ? AND p.owning_server_id = ?
                    AND o.operation_type LIKE 'ONLINE_CURSOR_%'
                    AND q.state IN ('PENDING', 'APPLYING', 'QUARANTINED')
                ORDER BY q.created_at
                LIMIT ?
                """, statement -> {
            statement.setBytes(1, UuidBytes.toBytes(actorId));
            statement.setString(2, owningServerId);
            statement.setInt(3, limit);
        });
    }

    @Override
    public Optional<InventoryCursorJournal> cursorTransfer(UUID operationId) {
        if (operationId == null) {
            throw new IllegalArgumentException("operationId must be present");
        }
        List<InventoryCursorJournal> matches = queryCursorJournals("""
                WHERE o.operation_id = ? AND o.operation_type LIKE 'ONLINE_CURSOR_%'
                LIMIT 1
                """, statement -> statement.setBytes(1, UuidBytes.toBytes(operationId)));
        return matches.stream().findFirst();
    }

    @Override
    public Optional<InventoryPatch> claimForApply(
            UUID patchId, UUID operationId, Duration leaseDuration, Instant now
    ) {
        return delegate.claimForApply(patchId, operationId, leaseDuration, now);
    }

    @Override
    public boolean advanceCursorPhase(
            UUID patchId,
            UUID operationId,
            long fencingToken,
            InventoryCursorPhase expected,
            InventoryCursorPhase next,
            Instant now
    ) {
        if (patchId == null || operationId == null || fencingToken < 1L
                || expected == null || next == null || now == null || !expected.canAdvanceTo(next)) {
            throw new IllegalArgumentException("cursor phase transition is invalid");
        }
        return JdbcTransactionSupport.execute(
                dataSource,
                "Unable to advance durable cursor transfer phase",
                connection -> advanceCursorPhase(
                        connection, patchId, operationId, fencingToken, expected, next, now
                )
        );
    }

    @Override
    public InventoryFinalizeResult finalizeApplied(
            UUID patchId,
            UUID operationId,
            long fencingToken,
            String observedChecksum,
            byte[] observedSnapshot,
            Instant now
    ) {
        return delegate.finalizeApplied(
                patchId, operationId, fencingToken, observedChecksum, observedSnapshot, now
        );
    }

    @Override
    public void quarantine(
            UUID patchId,
            UUID operationId,
            long fencingToken,
            String reasonCode,
            String detail,
            Instant now
    ) {
        delegate.quarantine(patchId, operationId, fencingToken, reasonCode, detail, now);
    }

    @Override
    public boolean isLocked(UUID playerId, String scopeId, Instant now) {
        return delegate.isLocked(playerId, scopeId, now);
    }

    @Override
    public Optional<String> lockedOwningServer(UUID playerId, Instant now) {
        return delegate.lockedOwningServer(playerId, now);
    }

    private void persistCursorMetadata(
            InventoryPatch patch,
            InventoryCursorTransfer transfer,
            Instant now
    ) {
        JdbcTransactionSupport.execute(
                dataSource,
                "Unable to persist live inventory cursor escrow",
                connection -> {
                    ObjectNode operation = lockOperationJson(connection, patch);
                    JsonNode existing = operation.get(CURSOR_TRANSFER_FIELD);
                    if (existing != null && !existing.isNull()) {
                        InventoryCursorTransfer stored = readCursorTransfer(existing);
                        if (!stored.equals(transfer)) {
                            throw new SQLException("Inventory operation is bound to different cursor escrow bytes");
                        }
                        return null;
                    }
                    operation.set(CURSOR_TRANSFER_FIELD, cursorNode(transfer));
                    operation.put(CURSOR_PHASE_FIELD, InventoryCursorPhase.PREPARED.name());
                    updateOperationJson(connection, patch.operationId(), patch.fencingToken(), operation, now);
                    return null;
                }
        );
    }

    private boolean advanceCursorPhase(
            Connection connection,
            UUID patchId,
            UUID operationId,
            long fencingToken,
            InventoryCursorPhase expected,
            InventoryCursorPhase next,
            Instant now
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT o.operation_json, q.state
                FROM inventory_operations o
                JOIN inventory_pending_patches q ON q.operation_id = o.operation_id
                WHERE q.patch_id = ? AND o.operation_id = ?
                    AND q.fencing_token = ? AND o.fencing_token = ?
                FOR UPDATE
                """)) {
            statement.setBytes(1, UuidBytes.toBytes(patchId));
            statement.setBytes(2, UuidBytes.toBytes(operationId));
            statement.setLong(3, fencingToken);
            statement.setLong(4, fencingToken);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next() || !"APPLYING".equals(result.getString("state"))) {
                    return false;
                }
                ObjectNode operation = objectNode(result.getString("operation_json"));
                InventoryCursorPhase current = readPhase(operation);
                if (current != expected || operation.path(CURSOR_TRANSFER_FIELD).isMissingNode()) {
                    return false;
                }
                operation.put(CURSOR_PHASE_FIELD, next.name());
                updateOperationJson(connection, operationId, fencingToken, operation, now);
                return true;
            }
        }
    }

    private ObjectNode lockOperationJson(Connection connection, InventoryPatch patch) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT operation_json
                FROM inventory_operations
                WHERE operation_id = ? AND fencing_token = ?
                FOR UPDATE
                """)) {
            statement.setBytes(1, UuidBytes.toBytes(patch.operationId()));
            statement.setLong(2, patch.fencingToken());
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    throw new SQLException("Prepared inventory operation disappeared before cursor escrow persistence");
                }
                return objectNode(result.getString("operation_json"));
            }
        }
    }

    private void updateOperationJson(
            Connection connection,
            UUID operationId,
            long fencingToken,
            ObjectNode operation,
            Instant now
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE inventory_operations
                SET operation_json = ?, updated_at = ?
                WHERE operation_id = ? AND fencing_token = ?
                """)) {
            statement.setString(1, serialize(operation));
            statement.setTimestamp(2, Timestamp.from(now));
            statement.setBytes(3, UuidBytes.toBytes(operationId));
            statement.setLong(4, fencingToken);
            JdbcTransactionSupport.requireSingleUpdate(
                    statement.executeUpdate(),
                    "Inventory cursor escrow operation changed concurrently"
            );
        }
    }

    private List<InventoryCursorJournal> queryCursorJournals(
            String whereClause,
            SqlBinder binder
    ) {
        String sql = """
                SELECT q.patch_id, q.operation_id, q.profile_id, p.player_id, p.scope_id,
                    p.owning_server_id, o.actor_id, o.case_id, o.operation_type, q.state,
                    q.expected_revision, q.fencing_token, q.expected_checksum,
                    q.replacement_checksum, q.replacement_blob, q.patch_json, q.created_at,
                    o.operation_json, s.snapshot_blob AS before_snapshot
                FROM inventory_pending_patches q
                JOIN inventory_profiles p ON p.profile_id = q.profile_id
                JOIN inventory_operations o ON o.operation_id = q.operation_id
                JOIN inventory_snapshots s ON s.operation_id = q.operation_id
                """ + whereClause;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            binder.bind(statement);
            try (ResultSet result = statement.executeQuery()) {
                List<InventoryCursorJournal> journals = new ArrayList<>();
                while (result.next()) {
                    journals.add(readCursorJournal(result));
                }
                return List.copyOf(journals);
            }
        } catch (SQLException exception) {
            throw new ModerationPersistenceException("Unable to load live inventory cursor escrow", exception);
        }
    }

    private InventoryCursorJournal readCursorJournal(ResultSet result) throws SQLException {
        ObjectNode operation = objectNode(result.getString("operation_json"));
        InventoryCursorTransfer transfer = readCursorTransfer(operation.path(CURSOR_TRANSFER_FIELD));
        List<Integer> changedSlots = readChangedSlots(result.getString("patch_json"));
        InventoryPatch patch = new InventoryPatch(
                UuidBytes.fromBytes(result.getBytes("patch_id")),
                UuidBytes.fromBytes(result.getBytes("operation_id")),
                UuidBytes.fromBytes(result.getBytes("profile_id")),
                UuidBytes.fromBytes(result.getBytes("player_id")),
                result.getString("scope_id"),
                result.getString("owning_server_id"),
                UuidBytes.fromBytes(result.getBytes("actor_id")),
                Optional.ofNullable(result.getString("case_id")),
                result.getString("operation_type"),
                InventoryOperationState.valueOf(result.getString("state")),
                result.getLong("expected_revision"),
                result.getLong("fencing_token"),
                result.getString("expected_checksum"),
                result.getString("replacement_checksum"),
                result.getBytes("replacement_blob"),
                changedSlots,
                result.getTimestamp("created_at").toInstant()
        );
        return new InventoryCursorJournal(
                patch,
                result.getBytes("before_snapshot"),
                transfer,
                readPhase(operation)
        );
    }

    private List<Integer> readChangedSlots(String patchJson) throws SQLException {
        try {
            List<Integer> slots = new ArrayList<>();
            for (JsonNode slot : json.readTree(patchJson).path("changedSlots")) {
                slots.add(slot.intValue());
            }
            return List.copyOf(slots);
        } catch (JsonProcessingException exception) {
            throw new SQLException("Inventory patch JSON is invalid", exception);
        }
    }

    private ObjectNode cursorNode(InventoryCursorTransfer transfer) {
        ObjectNode node = json.createObjectNode();
        node.put("expectedChecksum", transfer.expectedChecksum());
        node.put("expectedSnapshot", transfer.expectedSnapshot());
        node.put("replacementChecksum", transfer.replacementChecksum());
        node.put("replacementSnapshot", transfer.replacementSnapshot());
        return node;
    }

    private InventoryCursorTransfer readCursorTransfer(JsonNode node) throws SQLException {
        if (node == null || !node.isObject()) {
            throw new SQLException("Inventory cursor escrow metadata is missing");
        }
        try {
            return new InventoryCursorTransfer(
                    node.path("expectedChecksum").asText(),
                    node.path("expectedSnapshot").binaryValue(),
                    node.path("replacementChecksum").asText(),
                    node.path("replacementSnapshot").binaryValue()
            );
        } catch (IOException | IllegalArgumentException exception) {
            throw new SQLException("Inventory cursor escrow metadata is invalid", exception);
        }
    }

    private InventoryCursorPhase readPhase(JsonNode operation) throws SQLException {
        try {
            return InventoryCursorPhase.valueOf(operation.path(CURSOR_PHASE_FIELD).asText());
        } catch (IllegalArgumentException exception) {
            throw new SQLException("Inventory cursor escrow phase is invalid", exception);
        }
    }

    private ObjectNode objectNode(String encoded) throws SQLException {
        try {
            JsonNode node = json.readTree(encoded);
            if (!(node instanceof ObjectNode object)) {
                throw new SQLException("Inventory operation JSON is not an object");
            }
            return object;
        } catch (JsonProcessingException exception) {
            throw new SQLException("Inventory operation JSON is invalid", exception);
        }
    }

    private String serialize(ObjectNode node) throws SQLException {
        try {
            return json.writeValueAsString(node);
        } catch (JsonProcessingException exception) {
            throw new SQLException("Unable to serialize inventory cursor escrow metadata", exception);
        }
    }

    @FunctionalInterface
    private interface SqlBinder {
        void bind(PreparedStatement statement) throws SQLException;
    }
}
