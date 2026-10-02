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
        return delegate.prepare(request, leaseDuration, now);
    }

    @Override
    public List<InventoryPatch> pending(UUID playerId, String scopeId, String owningServerId, int limit) {
        return delegate.pending(playerId, scopeId, owningServerId, limit);
    }

    @Override
    public List<InventoryCursorJournal> pendingCursorTransfersByActor(
            UUID actorId,
            String requestingServerId,
            int limit
    ) {
        if (actorId == null || requestingServerId == null || requestingServerId.isBlank()
                || limit < 1 || limit > MAX_CURSOR_QUERY) {
            throw new IllegalArgumentException("cursor transfer actor query is invalid");
        }
        return queryCursorJournals("""
                WHERE o.actor_id = ?
                    AND o.operation_type LIKE 'ONLINE_CURSOR_%'
                    AND q.state IN ('PENDING', 'APPLYING', 'QUARANTINED')
                ORDER BY q.created_at
                LIMIT ?
                """, statement -> {
            statement.setBytes(1, UuidBytes.toBytes(actorId));
            statement.setInt(2, limit);
        });
    }

    @Override
    public Optional<InventoryCursorJournal> cursorTransfer(UUID operationId) {
        if (operationId == null) {
            throw new IllegalArgumentException("operationId must be present");
        }
        List<InventoryCursorJournal> matches = queryCursorJournals("""
                WHERE o.operation_id = ? AND o.operation_type LIKE 'ONLINE_CURSOR_%'
                    AND q.state IN ('PENDING', 'APPLYING', 'QUARANTINED')
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
    public boolean resolveCursorRollback(
            UUID patchId,
            UUID operationId,
            long fencingToken,
            Instant now
    ) {
        if (patchId == null || operationId == null || fencingToken < 1L || now == null) {
            throw new IllegalArgumentException("cursor rollback identity is invalid");
        }
        return JdbcTransactionSupport.execute(
                dataSource,
                "Unable to resolve durable cursor rollback",
                connection -> resolveCursorRollback(
                        connection, patchId, operationId, fencingToken, now
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

    private boolean resolveCursorRollback(
            Connection connection,
            UUID patchId,
            UUID operationId,
            long fencingToken,
            Instant now
    ) throws SQLException {
        CursorRollbackRow row = lockCursorRollback(connection, patchId, operationId, fencingToken);
        if (row == null) {
            return false;
        }
        CursorRollbackDecision decision = rollbackDecision(row);
        if (decision == CursorRollbackDecision.REJECT) {
            return false;
        }
        if (decision == CursorRollbackDecision.ALREADY_RESTORED) {
            return true;
        }
        markCursorRollbackRestored(connection, patchId, operationId, fencingToken, row, now);
        releaseCursorLease(connection, operationId, fencingToken, row);
        return true;
    }

    private CursorRollbackRow lockCursorRollback(
            Connection connection,
            UUID patchId,
            UUID operationId,
            long fencingToken
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT q.state AS patch_state, o.state AS operation_state,
                    o.operation_type, o.operation_json, p.player_id, p.scope_id
                FROM inventory_pending_patches q
                JOIN inventory_operations o ON o.operation_id = q.operation_id
                JOIN inventory_profiles p ON p.profile_id = q.profile_id
                WHERE q.patch_id = ? AND o.operation_id = ?
                    AND q.fencing_token = ? AND o.fencing_token = ?
                FOR UPDATE
                """)) {
            statement.setBytes(1, UuidBytes.toBytes(patchId));
            statement.setBytes(2, UuidBytes.toBytes(operationId));
            statement.setLong(3, fencingToken);
            statement.setLong(4, fencingToken);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? new CursorRollbackRow(
                        result.getString("patch_state"),
                        result.getString("operation_state"),
                        result.getString("operation_type"),
                        objectNode(result.getString("operation_json")),
                        UuidBytes.fromBytes(result.getBytes("player_id")),
                        result.getString("scope_id")
                ) : null;
            }
        }
    }

    private CursorRollbackDecision rollbackDecision(CursorRollbackRow row) throws SQLException {
        if ("RESTORED".equals(row.patchState()) && "RESTORED".equals(row.operationState())) {
            return CursorRollbackDecision.ALREADY_RESTORED;
        }
        if (!"APPLYING".equals(row.patchState()) || !"APPLYING".equals(row.operationState())
                || !row.operationType().startsWith("ONLINE_CURSOR_")) {
            return CursorRollbackDecision.REJECT;
        }
        JsonNode transfer = row.operation().get(CURSOR_TRANSFER_FIELD);
        JsonNode phase = row.operation().get(CURSOR_PHASE_FIELD);
        if (transfer == null || transfer.isNull()) {
            return phase == null || phase.isNull()
                    ? CursorRollbackDecision.RESOLVE
                    : CursorRollbackDecision.REJECT;
        }
        if (phase == null || phase.isNull()) {
            return CursorRollbackDecision.REJECT;
        }
        return readPhase(row.operation()) == InventoryCursorPhase.CURSOR_APPLIED
                ? CursorRollbackDecision.REJECT
                : CursorRollbackDecision.RESOLVE;
    }

    private void markCursorRollbackRestored(
            Connection connection,
            UUID patchId,
            UUID operationId,
            long fencingToken,
            CursorRollbackRow row,
            Instant now
    ) throws SQLException {
        ObjectNode operation = row.operation().deepCopy();
        operation.put("cursorRollbackResolvedAt", now.toString());
        try (PreparedStatement patch = connection.prepareStatement("""
                UPDATE inventory_pending_patches
                SET state = 'RESTORED', conflict_code = NULL, conflict_detail = NULL
                WHERE patch_id = ? AND operation_id = ?
                    AND state = 'APPLYING' AND fencing_token = ?
                """);
             PreparedStatement operationRow = connection.prepareStatement("""
                UPDATE inventory_operations
                SET state = 'RESTORED', operation_json = ?, updated_at = ?
                WHERE operation_id = ? AND state = 'APPLYING' AND fencing_token = ?
                """)) {
            patch.setBytes(1, UuidBytes.toBytes(patchId));
            patch.setBytes(2, UuidBytes.toBytes(operationId));
            patch.setLong(3, fencingToken);
            JdbcTransactionSupport.requireSingleUpdate(
                    patch.executeUpdate(), "Inventory cursor rollback patch changed concurrently"
            );
            operationRow.setString(1, serialize(operation));
            operationRow.setTimestamp(2, Timestamp.from(now));
            operationRow.setBytes(3, UuidBytes.toBytes(operationId));
            operationRow.setLong(4, fencingToken);
            JdbcTransactionSupport.requireSingleUpdate(
                    operationRow.executeUpdate(), "Inventory cursor rollback operation changed concurrently"
            );
        }
    }

    private static void releaseCursorLease(
            Connection connection,
            UUID operationId,
            long fencingToken,
            CursorRollbackRow row
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                DELETE FROM operation_leases
                WHERE resource_key = ? AND owner_id = ? AND fencing_token = ?
                """)) {
            statement.setString(1, "inventory:" + row.playerId() + ':' + row.scopeId());
            statement.setString(2, operationId.toString());
            statement.setLong(3, fencingToken);
            JdbcTransactionSupport.requireOptionalSingleUpdate(
                    statement.executeUpdate(), "Multiple cursor rollback leases matched one fence"
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
                    readCursorJournal(result).ifPresent(journals::add);
                }
                return List.copyOf(journals);
            }
        } catch (SQLException exception) {
            throw new ModerationPersistenceException("Unable to load live inventory cursor escrow", exception);
        }
    }

    private Optional<InventoryCursorJournal> readCursorJournal(ResultSet result) throws SQLException {
        ObjectNode operation = objectNode(result.getString("operation_json"));
        JsonNode cursorNode = operation.get(CURSOR_TRANSFER_FIELD);
        if (cursorNode == null || cursorNode.isNull()) {
            return Optional.empty();
        }
        InventoryCursorTransfer transfer = readCursorTransfer(cursorNode);
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
        return Optional.of(new InventoryCursorJournal(
                patch,
                result.getBytes("before_snapshot"),
                transfer,
                readPhase(operation)
        ));
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

    private enum CursorRollbackDecision {
        RESOLVE,
        ALREADY_RESTORED,
        REJECT
    }

    private record CursorRollbackRow(
            String patchState,
            String operationState,
            String operationType,
            ObjectNode operation,
            UUID playerId,
            String scopeId
    ) {
    }

    @FunctionalInterface
    private interface SqlBinder {
        void bind(PreparedStatement statement) throws SQLException;
    }
}
