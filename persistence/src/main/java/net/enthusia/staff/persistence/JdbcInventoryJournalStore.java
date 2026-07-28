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
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;
import net.enthusia.staff.domain.inventory.InventoryFinalizeResult;
import net.enthusia.staff.domain.inventory.ConfiscatedAssetReservation;
import net.enthusia.staff.domain.inventory.ConfiscatedAssetSnapshot;
import net.enthusia.staff.domain.inventory.InventoryConfiscationCommitRequest;
import net.enthusia.staff.domain.inventory.InventoryConfiscationSession;
import net.enthusia.staff.domain.inventory.InventoryConfiscationStart;
import net.enthusia.staff.domain.inventory.InventoryConfiscationStartRequest;
import net.enthusia.staff.domain.inventory.InventoryObservation;
import net.enthusia.staff.domain.inventory.InventoryOperationState;
import net.enthusia.staff.domain.inventory.InventoryPatch;
import net.enthusia.staff.domain.inventory.InventoryPreparation;
import net.enthusia.staff.domain.inventory.InventoryPrepareRequest;
import net.enthusia.staff.domain.ports.InventoryJournalStore;
import net.enthusia.staff.common.CaseId;

public final class JdbcInventoryJournalStore implements InventoryJournalStore {
    private static final Duration SNAPSHOT_RETENTION = Duration.ofDays(30);
    private static final String CONFISCATION_OPERATION_TYPE = "CONFISCATION";
    private static final String RESTORATION_OPERATION_TYPE = "RESTORE_CONFISCATED";
    private static final String STATE_COLUMN = "state";
    private static final long NO_SNAPSHOTS = 0L;

    private final DataSource dataSource;
    private final ObjectMapper json;

    public JdbcInventoryJournalStore(DataSource dataSource, ObjectMapper json) {
        if (dataSource == null || json == null) {
            throw new IllegalArgumentException("dataSource and json must be present");
        }
        this.dataSource = dataSource;
        this.json = json;
    }

    @Override
    public InventoryConfiscationStart beginConfiscation(
            InventoryConfiscationStartRequest request,
            Duration leaseDuration,
            Instant now
    ) {
        if (request == null || leaseDuration == null || leaseDuration.isNegative()
                || leaseDuration.isZero() || leaseDuration.compareTo(Duration.ofMinutes(5)) > 0
                || now == null) {
            throw new IllegalArgumentException("confiscation request, leaseDuration, and now are invalid");
        }
        BinarySnapshotIntegrity.requireMatch(
                request.beforeChecksum(),
                request.beforeSnapshot(),
                "confiscation before"
        );
        return transaction(connection -> {
            Optional<InventoryConfiscationSession> replay = lockConfiscationSessionByIdentity(
                    connection,
                    request.operationId(),
                    request.idempotencyKey()
            );
            if (replay.isPresent()) {
                validateConfiscationReplay(replay.orElseThrow(), request);
                return new InventoryConfiscationStart(
                        InventoryConfiscationStart.Status.REPLAYED,
                        replay,
                        "The confiscation selection lock was already prepared"
                );
            }
            Profile profile = lockOrCreateProfile(
                    connection,
                    request.playerId(),
                    request.scopeId(),
                    request.owningServerId()
            );
            if (hasPendingPatch(connection, profile.profileId())
                    || hasOpenConfiscation(connection, profile.profileId())) {
                return new InventoryConfiscationStart(
                        InventoryConfiscationStart.Status.CONFLICT,
                        Optional.empty(),
                        "Another inventory operation must finish before confiscation"
                );
            }
            Optional<InventoryObservation> existing = observation(connection, profile, true);
            long revision = existing.filter(value -> value.checksum().equals(request.beforeChecksum()))
                    .map(InventoryObservation::revision)
                    .orElse(existing.isPresent() ? profile.revision() + 1L : profile.revision());
            updateProfile(connection, profile.profileId(), request.owningServerId(), revision);
            saveObservation(
                    connection,
                    profile.profileId(),
                    revision,
                    request.beforeChecksum(),
                    request.beforeSnapshot(),
                    now
            );
            saveRevision(connection, profile.profileId(), revision, request.beforeChecksum(), now);
            long fence = JdbcOperationLeaseSupport.acquire(
                    connection,
                    resourceKey(request.playerId(), request.scopeId()),
                    request.operationId(),
                    now.plus(leaseDuration),
                    now
            );
            if (fence < 1L) {
                return new InventoryConfiscationStart(
                        InventoryConfiscationStart.Status.CONFLICT,
                        Optional.empty(),
                        "Another inventory operation owns the network asset lease"
                );
            }
            insertConfiscationStart(connection, request, profile, revision, fence, now);
            insertAudit(
                    connection,
                    request.operationId(),
                    request.actorId(),
                    request.playerId(),
                    Optional.of(request.caseId().value()),
                    "INVENTORY_CONFISCATION_LOCKED",
                    "LOCKED",
                    Map.of(
                            "scopeId", request.scopeId(),
                            "owningServerId", request.owningServerId(),
                            "expectedRevision", revision,
                            "beforeChecksum", request.beforeChecksum()
                    ),
                    "inventory:confiscation:locked:" + request.operationId(),
                    now
            );
            return new InventoryConfiscationStart(
                    InventoryConfiscationStart.Status.LOCKED,
                    lockConfiscationSession(connection, request.operationId()),
                    "Confiscation selection owns the durable inventory fence"
            );
        });
    }

    @Override
    public Optional<InventoryConfiscationSession> renewConfiscation(
            UUID operationId,
            long fencingToken,
            Duration leaseDuration,
            Instant now
    ) {
        if (operationId == null || fencingToken < 1L || leaseDuration == null
                || leaseDuration.isNegative() || leaseDuration.isZero()
                || leaseDuration.compareTo(Duration.ofMinutes(5)) > 0 || now == null) {
            throw new IllegalArgumentException("confiscation lease identity is invalid");
        }
        return transaction(connection -> {
            InventoryConfiscationSession session =
                    lockConfiscationSession(connection, operationId).orElse(null);
            if (session == null || session.fencingToken() != fencingToken
                    || !confiscationState(connection, operationId).equals("LOCKED")
                    || !ownsLease(connection, session, now)) {
                return Optional.empty();
            }
            Instant leaseUntil = now.plus(leaseDuration);
            renewLease(connection, session, leaseUntil, now);
            return lockConfiscationSession(connection, operationId);
        });
    }

    @Override
    public InventoryPreparation prepareConfiscation(
            InventoryConfiscationCommitRequest request,
            Instant now
    ) {
        if (request == null || now == null) {
            throw new IllegalArgumentException("confiscation commit request and now are required");
        }
        BinarySnapshotIntegrity.requireMatch(
                request.replacementChecksum(),
                request.replacementSnapshot(),
                "confiscation replacement"
        );
        BinarySnapshotIntegrity.requireMatch(
                request.assetsChecksum(),
                request.assetsSnapshot(),
                "confiscated assets"
        );
        return transaction(connection -> {
            InventoryConfiscationSession session =
                    lockConfiscationSession(connection, request.operationId()).orElse(null);
            if (session == null) {
                return rejected(
                        InventoryPreparation.Status.PROFILE_NOT_FOUND,
                        "Confiscation selection session was not found"
                );
            }
            Optional<InventoryPatch> replay = findPatchByOperation(connection, request.operationId());
            if (replay.isPresent()) {
                validateConfiscationCommitReplay(replay.orElseThrow(), request);
                return new InventoryPreparation(
                        InventoryPreparation.Status.REPLAYED,
                        replay,
                        "The confiscation patch was already prepared"
                );
            }
            if (!confiscationState(connection, request.operationId()).equals("LOCKED")
                    || session.fencingToken() != request.fencingToken()
                    || session.expectedRevision() != request.expectedRevision()
                    || !session.beforeChecksum().equals(request.expectedChecksum())) {
                return rejected(
                        InventoryPreparation.Status.STALE,
                        "Confiscation selection session changed before validation"
                );
            }
            if (!ownsLease(connection, session, now)) {
                return rejected(
                        InventoryPreparation.Status.LOCKED,
                        "Confiscation selection no longer owns its inventory fence"
                );
            }
            Profile profile = lockProfile(
                    connection,
                    session.playerId(),
                    session.scopeId()
            ).orElseThrow();
            InventoryObservation current = observation(connection, profile, true).orElseThrow();
            if (profile.revision() != request.expectedRevision()
                    || !current.checksum().equals(request.expectedChecksum())) {
                return rejected(
                        InventoryPreparation.Status.STALE,
                        "Authoritative inventory changed during confiscation selection"
                );
            }
            InventoryPatch patch = insertConfiscationPatch(connection, session, request, now);
            insertAudit(
                    connection,
                    session.operationId(),
                    session.actorId(),
                    session.playerId(),
                    Optional.of(session.caseId().value()),
                    "INVENTORY_CONFISCATION_VALIDATED",
                    "VALIDATED",
                    Map.of(
                            "scopeId", session.scopeId(),
                            "changedSlots", request.changedSlots(),
                            "selectedPaths", request.selectedPaths(),
                            "assetsChecksum", request.assetsChecksum()
                    ),
                    "inventory:confiscation:validated:" + session.operationId(),
                    now
            );
            return new InventoryPreparation(
                    InventoryPreparation.Status.PREPARED,
                    Optional.of(patch),
                    "Selected assets, before snapshot, and exact removal patch committed"
            );
        });
    }

    @Override
    public boolean cancelConfiscation(
            UUID operationId,
            long fencingToken,
            String reasonCode,
            String detail,
            Instant now
    ) {
        if (operationId == null || fencingToken < 1L
                || reasonCode == null || !reasonCode.matches("[A-Z0-9_]{1,64}")
                || detail == null || detail.isBlank() || detail.length() > 512 || now == null) {
            throw new IllegalArgumentException("confiscation cancellation fields are invalid");
        }
        return transaction(connection -> {
            InventoryConfiscationSession session =
                    lockConfiscationSession(connection, operationId).orElse(null);
            if (session == null) {
                return false;
            }
            String state = confiscationState(connection, operationId);
            if (state.equals("ROLLED_BACK")) {
                return true;
            }
            if (!state.equals("LOCKED") || session.fencingToken() != fencingToken) {
                return false;
            }
            markConfiscationCancelled(connection, session, reasonCode, detail, now);
            return true;
        });
    }

    @Override
    public int cancelAbandonedConfiscations(
            UUID playerId,
            String scopeId,
            String owningServerId,
            Instant now
    ) {
        if (playerId == null || scopeId == null || scopeId.isBlank()
                || owningServerId == null || owningServerId.isBlank() || now == null) {
            throw new IllegalArgumentException("abandoned confiscation query is invalid");
        }
        return transaction(connection -> {
            List<InventoryConfiscationSession> sessions = lockOpenConfiscations(
                    connection,
                    playerId,
                    scopeId,
                    owningServerId
            );
            for (InventoryConfiscationSession session : sessions) {
                markConfiscationCancelled(
                        connection,
                        session,
                        "SELECTION_ABANDONED",
                        "Confiscation selection ended before a removal patch was validated",
                        now
                );
            }
            return sessions.size();
        });
    }

    @Override
    public ConfiscatedAssetReservation reserveRestoration(
            CaseId caseId,
            UUID restorationOperationId,
            Instant now
    ) {
        if (caseId == null || restorationOperationId == null || now == null) {
            throw new IllegalArgumentException("restoration reservation identity is invalid");
        }
        return transaction(connection -> {
            Optional<RestorationOperationBinding> operation =
                    lockRestorationOperation(connection, restorationOperationId);
            if (operation.isPresent() && !operation.orElseThrow().matches(caseId)) {
                return new ConfiscatedAssetReservation(
                        ConfiscatedAssetReservation.Status.LOCKED,
                        restorationOperationId,
                        List.of(),
                        "The restoration identifier belongs to another inventory operation"
                );
            }
            finalizeCommittedRestorationReservations(connection, caseId, now);
            RestorationSnapshotSet locked = lockRestorationSnapshots(connection, caseId, now);
            if (!locked.safe()) {
                return new ConfiscatedAssetReservation(
                        ConfiscatedAssetReservation.Status.LOCKED,
                        restorationOperationId,
                        List.of(),
                        "At least one active snapshot is not backed by a committed confiscation"
                );
            }
            if (locked.snapshots().isEmpty()) {
                return new ConfiscatedAssetReservation(
                        ConfiscatedAssetReservation.Status.NOT_FOUND,
                        restorationOperationId,
                        List.of(),
                        "The case has no unexpired unrestored confiscated asset snapshots"
                );
            }
            return reserveRestorationSnapshots(
                    connection,
                    caseId,
                    restorationOperationId,
                    now,
                    operation,
                    locked.snapshots()
            );
        });
    }

    private static ConfiscatedAssetReservation reserveRestorationSnapshots(
            Connection connection,
            CaseId caseId,
            UUID restorationOperationId,
            Instant now,
            Optional<RestorationOperationBinding> operation,
            List<ConfiscatedAssetSnapshot> snapshots
    ) throws SQLException {
        List<ConfiscatedAssetSnapshot> available = snapshots;
        Optional<UUID> existingReservation = available.stream()
                .map(ConfiscatedAssetSnapshot::restorationOperationId)
                .flatMap(Optional::stream)
                .findFirst();
        boolean conflictingReservation = available.stream()
                .map(ConfiscatedAssetSnapshot::restorationOperationId)
                .flatMap(Optional::stream)
                .anyMatch(existing -> !existing.equals(restorationOperationId));
        if (conflictingReservation) {
            return restorationLocked(
                    restorationOperationId,
                    "Another restoration operation owns this case"
            );
        }
        long unreserved = unreservedSnapshotCount(available);
        if (existingRestorationConflicts(operation, existingReservation, unreserved)) {
            return restorationLocked(
                    restorationOperationId,
                    "The existing restoration operation does not match this reservation"
            );
        }
        if (unreserved > NO_SNAPSHOTS) {
            reserveUnreservedSnapshots(
                    connection, caseId, restorationOperationId, now, unreserved
            );
            RestorationSnapshotSet updated = lockRestorationSnapshots(connection, caseId, now);
            if (!updated.safe() || updated.snapshots().isEmpty()) {
                throw new SQLException("Confiscated asset reservation became unsafe");
            }
            available = updated.snapshots();
        }
        return new ConfiscatedAssetReservation(
                existingReservation.isPresent()
                        ? ConfiscatedAssetReservation.Status.REPLAYED
                        : ConfiscatedAssetReservation.Status.RESERVED,
                restorationOperationId,
                available,
                "Confiscated asset snapshots are reserved for exact restoration"
        );
    }

    private static long unreservedSnapshotCount(List<ConfiscatedAssetSnapshot> snapshots) {
        return snapshots.stream()
                .filter(snapshot -> snapshot.restorationOperationId().isEmpty())
                .count();
    }

    private static ConfiscatedAssetReservation restorationLocked(
            UUID operationId,
            String detail
    ) {
        return new ConfiscatedAssetReservation(
                ConfiscatedAssetReservation.Status.LOCKED,
                operationId,
                List.of(),
                detail
        );
    }

    private static boolean existingRestorationConflicts(
            Optional<RestorationOperationBinding> operation,
            Optional<UUID> existingReservation,
            long unreserved
    ) {
        if (operation.isEmpty()) {
            return false;
        }
        return !operation.orElseThrow().replayable()
                || existingReservation.isEmpty()
                || unreserved > NO_SNAPSHOTS;
    }

    private static void reserveUnreservedSnapshots(
            Connection connection,
            CaseId caseId,
            UUID operationId,
            Instant now,
            long expectedCount
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE confiscated_asset_snapshots
                SET restoration_operation_id = ?, restoration_state = 'RESERVED',
                    restoration_reserved_at = ?
                WHERE case_id = ? AND restored_at IS NULL AND expires_at > ?
                    AND restoration_operation_id IS NULL
                """)) {
            statement.setBytes(1, UuidBytes.toBytes(operationId));
            statement.setTimestamp(2, Timestamp.from(now));
            statement.setString(3, caseId.value());
            statement.setTimestamp(4, Timestamp.from(now));
            if (statement.executeUpdate() != expectedCount) {
                throw new SQLException("Confiscated asset reservation changed concurrently");
            }
        }
    }

    @Override
    public boolean cancelRestoration(
            CaseId caseId,
            UUID restorationOperationId,
            Instant now
    ) {
        if (caseId == null || restorationOperationId == null || now == null) {
            throw new IllegalArgumentException("restoration cancellation identity is invalid");
        }
        return transaction(connection -> {
            String operationState = inventoryOperationState(connection, restorationOperationId);
            if (!operationState.isEmpty() && !operationState.equals("ROLLED_BACK")) {
                return false;
            }
            try (PreparedStatement statement = connection.prepareStatement("""
                    UPDATE confiscated_asset_snapshots
                    SET restoration_operation_id = NULL, restoration_state = NULL,
                        restoration_reserved_at = NULL
                    WHERE case_id = ? AND restoration_operation_id = ?
                        AND restoration_state = 'RESERVED' AND restored_at IS NULL
                    """)) {
                statement.setString(1, caseId.value());
                statement.setBytes(2, UuidBytes.toBytes(restorationOperationId));
                return statement.executeUpdate() > 0;
            }
        });
    }

    @Override
    public boolean finalizeRestoration(
            CaseId caseId,
            UUID restorationOperationId,
            String restoredChecksum,
            Instant now
    ) {
        if (caseId == null || restorationOperationId == null
                || restoredChecksum == null || !restoredChecksum.matches("[0-9a-f]{64}")
                || now == null) {
            throw new IllegalArgumentException("restoration finalization identity is invalid");
        }
        return transaction(connection -> {
            if (finalizeCommittedRestoration(
                    connection, caseId, restorationOperationId, restoredChecksum, now
            ) > 0) {
                return true;
            }
            return restorationAlreadyFinalized(
                    connection,
                    caseId,
                    restorationOperationId,
                    restoredChecksum
            );
        });
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
        InventoryObservation validated = new InventoryObservation(
                UUID.randomUUID(), playerId, scopeId, owningServerId, 0L, checksum, snapshot, observedAt
        );
        BinarySnapshotIntegrity.requireMatch(
                validated.checksum(),
                validated.snapshot(),
                "inventory observation"
        );
        return transaction(connection -> {
            Profile profile = lockOrCreateProfile(connection, playerId, scopeId, owningServerId);
            Optional<InventoryObservation> existing = observation(connection, profile, true);
            long revision = existing.filter(value -> value.checksum().equals(checksum))
                    .map(InventoryObservation::revision)
                    .orElse(existing.isPresent() ? profile.revision() + 1L : profile.revision());
            updateProfile(connection, profile.profileId(), owningServerId, revision);
            saveObservation(connection, profile.profileId(), revision, checksum, snapshot, observedAt);
            saveRevision(connection, profile.profileId(), revision, checksum, observedAt);
            return new InventoryObservation(
                    profile.profileId(),
                    validated.playerId(),
                    validated.scopeId(),
                    validated.owningServerId(),
                    revision,
                    validated.checksum(),
                    validated.snapshot(),
                    validated.observedAt()
            );
        });
    }

    @Override
    public Optional<InventoryObservation> latest(UUID playerId, String scopeId) {
        if (playerId == null || scopeId == null || scopeId.isBlank() || scopeId.length() > 64) {
            throw new IllegalArgumentException("playerId and scopeId are invalid");
        }
        String sql = """
                SELECT p.profile_id, p.player_id, p.scope_id, p.owning_server_id,
                    o.revision, o.checksum, o.snapshot_blob, o.observed_at
                FROM inventory_profiles p
                JOIN inventory_observations o ON o.profile_id = p.profile_id
                WHERE p.player_id = ? AND p.scope_id = ?
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setBytes(1, UuidBytes.toBytes(playerId));
            statement.setString(2, scopeId);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? Optional.of(readObservation(result)) : Optional.empty();
            }
        } catch (SQLException exception) {
            throw failure("Unable to load the latest inventory observation", exception);
        }
    }

    @Override
    public InventoryPreparation prepare(
            InventoryPrepareRequest request,
            Duration leaseDuration,
            Instant now
    ) {
        if (request == null || leaseDuration == null || leaseDuration.isNegative()
                || leaseDuration.isZero() || leaseDuration.compareTo(Duration.ofMinutes(5)) > 0 || now == null) {
            throw new IllegalArgumentException("request, leaseDuration, and now are invalid");
        }
        BinarySnapshotIntegrity.requireMatch(
                request.expectedChecksum(),
                request.beforeSnapshot(),
                "inventory before"
        );
        BinarySnapshotIntegrity.requireMatch(
                request.replacementChecksum(),
                request.replacementSnapshot(),
                "inventory replacement"
        );
        return transaction(connection -> {
            Optional<InventoryPatch> replay = findReplay(connection, request);
            if (replay.isPresent()) {
                validateReplay(replay.orElseThrow(), request);
                return new InventoryPreparation(
                        InventoryPreparation.Status.REPLAYED,
                        replay,
                        "The inventory operation was already prepared"
                );
            }
            Optional<InventoryPreparation> restorationFailure =
                    validateRestorationReservation(connection, request, now);
            if (restorationFailure.isPresent()) {
                return restorationFailure.orElseThrow();
            }
            if (request.requireNetworkOffline() && playerOnline(connection, request.playerId())) {
                return rejected(InventoryPreparation.Status.PLAYER_ONLINE, "The player is online network-wide");
            }
            Profile profile = lockProfile(connection, request.playerId(), request.scopeId()).orElse(null);
            if (profile == null || !profile.owningServerId().equals(request.owningServerId())) {
                return rejected(
                        InventoryPreparation.Status.PROFILE_NOT_FOUND,
                        "No authoritative inventory observation exists on this backend"
                );
            }
            InventoryObservation current = observation(connection, profile, true).orElse(null);
            if (current == null) {
                return rejected(
                        InventoryPreparation.Status.PROFILE_NOT_FOUND,
                        "The inventory profile has no authoritative observation"
                );
            }
            if (profile.revision() != request.expectedRevision()
                    || !current.checksum().equals(request.expectedChecksum())) {
                return rejected(InventoryPreparation.Status.STALE, "The inventory revision changed before prepare");
            }
            if (hasPendingPatch(connection, profile.profileId())) {
                return rejected(
                        InventoryPreparation.Status.LOCKED,
                        "Another prepared inventory patch must be applied or quarantined first"
                );
            }
            long fence = JdbcOperationLeaseSupport.acquire(
                    connection,
                    resourceKey(request.playerId(), request.scopeId()),
                    request.operationId(),
                    now.plus(leaseDuration),
                    now
            );
            if (fence < 1L) {
                return rejected(InventoryPreparation.Status.LOCKED, "Another inventory operation owns the lease");
            }
            InventoryPatch patch = insertPreparedOperation(connection, request, profile, fence, now);
            insertAudit(
                    connection,
                    request.operationId(),
                    request.actorId(),
                    request.playerId(),
                    request.caseId(),
                    "INVENTORY_OPERATION_PREPARED",
                    "PREPARED",
                    Map.of(
                            "operationType", request.operationType(),
                            "scopeId", request.scopeId(),
                            "expectedRevision", request.expectedRevision(),
                            "changedSlots", request.changedSlots()
                    ),
                    "inventory:prepare:" + request.operationId(),
                    now
            );
            return new InventoryPreparation(
                    InventoryPreparation.Status.PREPARED,
                    Optional.of(patch),
                    "Inventory operation, lease, and before snapshot committed"
            );
        });
    }

    @Override
    public List<InventoryPatch> pending(UUID playerId, String scopeId, String owningServerId, int limit) {
        if (playerId == null || scopeId == null || scopeId.isBlank()
                || owningServerId == null || owningServerId.isBlank() || limit < 1 || limit > 32) {
            throw new IllegalArgumentException("pending patch query is invalid");
        }
        String sql = """
                SELECT q.patch_id, q.operation_id, q.profile_id, p.player_id, p.scope_id,
                    p.owning_server_id, o.actor_id, o.case_id, o.operation_type, q.state,
                    q.expected_revision, q.fencing_token, q.expected_checksum,
                    q.replacement_checksum, q.replacement_blob, q.patch_json, q.created_at
                FROM inventory_pending_patches q
                JOIN inventory_profiles p ON p.profile_id = q.profile_id
                JOIN inventory_operations o ON o.operation_id = q.operation_id
                WHERE p.player_id = ? AND p.scope_id = ? AND p.owning_server_id = ?
                    AND q.state IN ('PENDING', 'APPLYING', 'QUARANTINED')
                    AND q.expected_checksum IS NOT NULL
                    AND q.replacement_checksum IS NOT NULL
                    AND q.replacement_blob IS NOT NULL
                    AND q.fencing_token IS NOT NULL
                ORDER BY q.created_at
                LIMIT ?
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setBytes(1, UuidBytes.toBytes(playerId));
            statement.setString(2, scopeId);
            statement.setString(3, owningServerId);
            statement.setInt(4, limit);
            try (ResultSet result = statement.executeQuery()) {
                List<InventoryPatch> patches = new ArrayList<>();
                while (result.next()) {
                    patches.add(readPatch(result));
                }
                return List.copyOf(patches);
            }
        } catch (SQLException exception) {
            throw failure("Unable to load pending inventory patches", exception);
        }
    }

    @Override
    public Optional<InventoryPatch> claimForApply(
            UUID patchId,
            UUID operationId,
            Duration leaseDuration,
            Instant now
    ) {
        if (patchId == null || operationId == null || leaseDuration == null || leaseDuration.isZero()
                || leaseDuration.isNegative() || leaseDuration.compareTo(Duration.ofMinutes(5)) > 0 || now == null) {
            throw new IllegalArgumentException("patch application identity is invalid");
        }
        return transaction(connection -> {
            InventoryPatch patch = lockPatch(connection, patchId).orElse(null);
            if (patch == null || !patch.operationId().equals(operationId)) {
                return Optional.empty();
            }
            if (patch.state() == InventoryOperationState.APPLIED) {
                return Optional.of(patch);
            }
            if (patch.state() != InventoryOperationState.PENDING
                    && patch.state() != InventoryOperationState.APPLYING) {
                return Optional.empty();
            }
            if (patch.state() == InventoryOperationState.APPLYING
                    && ownsLease(connection, patch, now)) {
                return Optional.empty();
            }
            long fence = claimLease(connection, patch, now.plus(leaseDuration), now);
            if (fence < 1L) {
                return Optional.empty();
            }
            try (PreparedStatement pending = connection.prepareStatement("""
                    UPDATE inventory_pending_patches
                    SET state = 'APPLYING', fencing_token = ?
                    WHERE patch_id = ? AND operation_id = ?
                    """);
                 PreparedStatement operation = connection.prepareStatement("""
                    UPDATE inventory_operations
                    SET state = 'APPLYING', fencing_token = ?, updated_at = ?
                    WHERE operation_id = ?
                    """)) {
                pending.setLong(1, fence);
                pending.setBytes(2, UuidBytes.toBytes(patchId));
                pending.setBytes(3, UuidBytes.toBytes(operationId));
                pending.executeUpdate();
                operation.setLong(1, fence);
                operation.setTimestamp(2, Timestamp.from(now));
                operation.setBytes(3, UuidBytes.toBytes(operationId));
                operation.executeUpdate();
            }
            return Optional.of(copyWithFence(patch, fence, InventoryOperationState.APPLYING));
        });
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
        InventoryObservation validation = new InventoryObservation(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "validation",
                "validation",
                0L,
                observedChecksum,
                observedSnapshot,
                now
        );
        BinarySnapshotIntegrity.requireMatch(
                validation.checksum(),
                validation.snapshot(),
                "applied inventory"
        );
        return transaction(connection -> {
            InventoryPatch patch = lockPatch(connection, patchId).orElse(null);
            if (patch == null || !patch.operationId().equals(operationId)) {
                return finalized(InventoryFinalizeResult.Status.NOT_FOUND, 0L, "Inventory patch was not found");
            }
            Profile profile = lockProfile(connection, patch.playerId(), patch.scopeId()).orElseThrow();
            InventoryObservation current = observation(connection, profile, true).orElseThrow();
            if (patch.state() == InventoryOperationState.APPLIED
                    && current.checksum().equals(patch.replacementChecksum())) {
                markRestorationApplied(connection, patch, now);
                insertCommitAudit(connection, patch, current.revision(), now);
                return finalized(
                        InventoryFinalizeResult.Status.REPLAYED,
                        current.revision(),
                        "The inventory patch was already committed"
                );
            }
            if (patch.fencingToken() != fencingToken || !ownsLease(connection, patch, now)) {
                return finalized(
                        InventoryFinalizeResult.Status.FENCE_LOST,
                        current.revision(),
                        "The inventory lease fence is no longer owned"
                );
            }
            if (!validation.checksum().equals(patch.replacementChecksum())) {
                quarantineInternal(
                        connection, patch, "POST_APPLY_CHECKSUM_MISMATCH",
                        "Applied inventory did not match the prepared replacement checksum", now
                );
                return finalized(
                        InventoryFinalizeResult.Status.STALE,
                        current.revision(),
                        "Applied inventory checksum did not match the prepared replacement"
                );
            }
            if (profile.revision() != patch.expectedRevision()
                    || !current.checksum().equals(patch.expectedChecksum())) {
                if (profile.revision() == patch.expectedRevision() + 1L
                        && current.checksum().equals(patch.replacementChecksum())) {
                    markApplied(connection, patch, now);
                    insertCommitAudit(connection, patch, current.revision(), now);
                    releaseLease(connection, patch);
                    return finalized(
                            InventoryFinalizeResult.Status.REPLAYED,
                            current.revision(),
                            "The replacement was already observed and has been finalized"
                    );
                }
                quarantineInternal(
                        connection, patch, "PROFILE_REVISION_CHANGED",
                        "The durable inventory profile changed before finalization", now
                );
                return finalized(
                        InventoryFinalizeResult.Status.STALE,
                        current.revision(),
                        "The durable inventory profile changed before finalization"
                );
            }
            long nextRevision = profile.revision() + 1L;
            updateProfile(connection, profile.profileId(), profile.owningServerId(), nextRevision);
            saveObservation(
                    connection,
                    profile.profileId(),
                    nextRevision,
                    patch.replacementChecksum(),
                    validation.snapshot(),
                    now
            );
            saveRevision(connection, profile.profileId(), nextRevision, patch.replacementChecksum(), now);
            markApplied(connection, patch, now);
            insertCommitAudit(connection, patch, nextRevision, now);
            releaseLease(connection, patch);
            return finalized(
                    InventoryFinalizeResult.Status.COMMITTED,
                    nextRevision,
                    "Inventory replacement verified and committed"
            );
        });
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
        if (patchId == null || operationId == null || fencingToken < 1L
                || reasonCode == null || !reasonCode.matches("[A-Z0-9_]{1,64}")
                || detail == null || detail.isBlank() || detail.length() > 512 || now == null) {
            throw new IllegalArgumentException("quarantine fields are invalid");
        }
        transaction(connection -> {
            InventoryPatch patch = lockPatch(connection, patchId).orElse(null);
            if (patch != null && patch.operationId().equals(operationId)
                    && patch.fencingToken() == fencingToken
                    && (patch.state() == InventoryOperationState.PENDING
                    || patch.state() == InventoryOperationState.APPLYING)) {
                quarantineInternal(connection, patch, reasonCode, detail, now);
            }
            return null;
        });
    }

    @Override
    public boolean isLocked(UUID playerId, String scopeId, Instant now) {
        if (playerId == null || scopeId == null || scopeId.isBlank() || now == null) {
            throw new IllegalArgumentException("inventory lock query is invalid");
        }
        String sql = """
                SELECT lease_until
                FROM operation_leases
                WHERE resource_key = ?
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, resourceKey(playerId, scopeId));
            try (ResultSet result = statement.executeQuery()) {
                return result.next() && result.getTimestamp("lease_until").toInstant().isAfter(now);
            }
        } catch (SQLException exception) {
            throw failure("Unable to inspect the inventory operation lease", exception);
        }
    }

    @Override
    public Optional<String> lockedOwningServer(UUID playerId, Instant now) {
        if (playerId == null || now == null) {
            throw new IllegalArgumentException("playerId and now must be present");
        }
        String sql = """
                SELECT p.owning_server_id
                FROM inventory_operations o
                JOIN inventory_profiles p ON p.profile_id = o.profile_id
                WHERE p.player_id = ?
                    AND o.state IN ('LOCKED', 'PENDING', 'APPLYING', 'QUARANTINED')
                ORDER BY o.created_at
                LIMIT 1
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setBytes(1, UuidBytes.toBytes(playerId));
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? Optional.of(result.getString("owning_server_id")) : Optional.empty();
            }
        } catch (SQLException exception) {
            throw failure("Unable to inspect the player's pending inventory owner", exception);
        }
    }

    private static void finalizeCommittedRestorationReservations(
            Connection connection,
            CaseId caseId,
            Instant now
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                JdbcInventoryRestorationSql.FINALIZE_COMMITTED_RESERVATIONS
        )) {
            statement.setTimestamp(1, Timestamp.from(now));
            statement.setString(2, caseId.value());
            statement.setString(3, CONFISCATION_OPERATION_TYPE);
            statement.setString(4, RESTORATION_OPERATION_TYPE);
            statement.executeUpdate();
        }
    }

    private static int finalizeCommittedRestoration(
            Connection connection,
            CaseId caseId,
            UUID operationId,
            String restoredChecksum,
            Instant now
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                JdbcInventoryRestorationSql.FINALIZE_COMMITTED_RESERVATION
        )) {
            statement.setTimestamp(1, Timestamp.from(now));
            statement.setString(2, restoredChecksum);
            statement.setString(3, caseId.value());
            statement.setBytes(4, UuidBytes.toBytes(operationId));
            statement.setString(5, CONFISCATION_OPERATION_TYPE);
            statement.setString(6, RESTORATION_OPERATION_TYPE);
            statement.setString(7, restoredChecksum);
            return statement.executeUpdate();
        }
    }

    private static Optional<RestorationOperationBinding> lockRestorationOperation(
            Connection connection,
            UUID operationId
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT case_id, operation_type, state
                FROM inventory_operations
                WHERE operation_id = ?
                FOR UPDATE
                """)) {
            statement.setBytes(1, UuidBytes.toBytes(operationId));
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    return Optional.empty();
                }
                return Optional.of(new RestorationOperationBinding(
                        result.getString("case_id"),
                        result.getString("operation_type"),
                        result.getString(STATE_COLUMN)
                ));
            }
        }
    }

    private static RestorationSnapshotSet lockRestorationSnapshots(
            Connection connection,
            CaseId caseId,
            Instant now
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                JdbcInventoryRestorationSql.LOCK_ACTIVE_SNAPSHOTS
        )) {
            statement.setString(1, caseId.value());
            statement.setTimestamp(2, Timestamp.from(now));
            try (ResultSet result = statement.executeQuery()) {
                List<ConfiscatedAssetSnapshot> snapshots = new ArrayList<>();
                boolean safe = true;
                while (result.next()) {
                    byte[] restorationBytes = result.getBytes("restoration_operation_id");
                    Instant expiresAt = result.getTimestamp("expires_at").toInstant();
                    safe &= sourceRestorable(result, caseId.value())
                            && restorationMarkerConsistent(
                                    result, restorationBytes, expiresAt, now
                            );
                    snapshots.add(new ConfiscatedAssetSnapshot(
                            UuidBytes.fromBytes(result.getBytes("snapshot_id")),
                            caseId,
                            UuidBytes.fromBytes(result.getBytes("inventory_operation_id")),
                            result.getString("checksum"),
                            result.getBytes("asset_blob"),
                            result.getTimestamp("created_at").toInstant(),
                            expiresAt,
                            restorationBytes == null
                                    ? Optional.empty()
                                    : Optional.of(UuidBytes.fromBytes(restorationBytes)),
                            Optional.empty()
                    ));
                }
                return new RestorationSnapshotSet(List.copyOf(snapshots), safe);
            }
        }
    }

    private static boolean sourceRestorable(ResultSet result, String caseId) throws SQLException {
        return caseId.equals(result.getString("source_case_id"))
                && CONFISCATION_OPERATION_TYPE.equals(result.getString("source_operation_type"))
                && "COMMITTED".equals(result.getString("source_operation_state"))
                && Arrays.equals(
                        result.getBytes("source_player_id"),
                        result.getBytes("case_target_id")
                )
                && result.getBoolean("source_patch_applied");
    }

    private static boolean restorationMarkerConsistent(
            ResultSet result,
            byte[] restorationOperationId,
            Instant expiresAt,
            Instant now
    ) throws SQLException {
        String state = result.getString("restoration_state");
        Timestamp reservedAt = result.getTimestamp("restoration_reserved_at");
        String restoredChecksum = result.getString("restored_checksum");
        if (restorationOperationId == null) {
            return state == null && reservedAt == null && restoredChecksum == null;
        }
        return "RESERVED".equals(state)
                && reservedAt != null
                && !reservedAt.toInstant().isAfter(now)
                && reservedAt.toInstant().isBefore(expiresAt)
                && restoredChecksum == null;
    }

    private static Optional<InventoryPreparation> validateRestorationReservation(
            Connection connection,
            InventoryPrepareRequest request,
            Instant now
    ) throws SQLException {
        boolean restoration = RESTORATION_OPERATION_TYPE.equals(request.operationType());
        if (request.caseId().isEmpty()) {
            return restoration
                    ? Optional.of(rejected(
                            InventoryPreparation.Status.STALE,
                            "A restoration operation requires its reserved case"
                    ))
                    : Optional.empty();
        }
        try (PreparedStatement statement = connection.prepareStatement(
                JdbcInventoryRestorationSql.VALIDATE_RESERVATION
        )) {
            String caseId = request.caseId().orElseThrow();
            statement.setString(1, caseId);
            statement.setBytes(2, UuidBytes.toBytes(request.operationId()));
            try (ResultSet result = statement.executeQuery()) {
                if (!restoration) {
                    return result.next()
                            ? Optional.of(rejected(
                                    InventoryPreparation.Status.LOCKED,
                                    "The operation identifier is reserved for confiscated assets"
                            ))
                            : Optional.empty();
                }
                return validateRestorationRows(result, request, caseId, now);
            }
        }
    }

    private static Optional<InventoryPreparation> validateRestorationRows(
            ResultSet result,
            InventoryPrepareRequest request,
            String caseId,
            Instant now
    ) throws SQLException {
        boolean found = false;
        byte[] playerId = UuidBytes.toBytes(request.playerId());
        while (result.next()) {
            found = true;
            if (!restorationReservationMatches(result, request, caseId, playerId, now)) {
                return Optional.of(rejected(
                        InventoryPreparation.Status.STALE,
                        "The restoration reservation does not match the case inventory"
                ));
            }
        }
        return found
                ? Optional.empty()
                : Optional.of(rejected(
                        InventoryPreparation.Status.STALE,
                        "No active confiscated-asset reservation matches this operation"
                ));
    }

    private static boolean restorationReservationMatches(
            ResultSet result,
            InventoryPrepareRequest request,
            String caseId,
            byte[] playerId,
            Instant now
    ) throws SQLException {
        return activeRestorationReservation(result, now)
                && sourceRestorable(result, caseId)
                && Arrays.equals(playerId, result.getBytes("source_player_id"))
                && Arrays.equals(playerId, result.getBytes("case_target_id"))
                && request.scopeId().equals(result.getString("source_scope_id"));
    }

    private static boolean activeRestorationReservation(
            ResultSet result,
            Instant now
    ) throws SQLException {
        Timestamp reservedAt = result.getTimestamp("restoration_reserved_at");
        Timestamp restoredAt = result.getTimestamp("restored_at");
        Instant expiresAt = result.getTimestamp("expires_at").toInstant();
        return "RESERVED".equals(result.getString("restoration_state"))
                && reservedAt != null
                && !reservedAt.toInstant().isAfter(now)
                && reservedAt.toInstant().isBefore(expiresAt)
                && restoredAt == null
                && result.getString("restored_checksum") == null
                && expiresAt.isAfter(now);
    }

    private static String inventoryOperationState(
            Connection connection,
            UUID operationId
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT state
                FROM inventory_operations
                WHERE operation_id = ?
                FOR UPDATE
                """)) {
            statement.setBytes(1, UuidBytes.toBytes(operationId));
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? result.getString(STATE_COLUMN) : "";
            }
        }
    }

    private static boolean restorationAlreadyFinalized(
            Connection connection,
            CaseId caseId,
            UUID operationId,
            String restoredChecksum
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                JdbcInventoryRestorationSql.ALREADY_FINALIZED
        )) {
            statement.setString(1, restoredChecksum);
            statement.setString(2, caseId.value());
            statement.setBytes(3, UuidBytes.toBytes(operationId));
            statement.setString(4, CONFISCATION_OPERATION_TYPE);
            statement.setString(5, RESTORATION_OPERATION_TYPE);
            statement.setString(6, restoredChecksum);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    return false;
                }
                long total = result.getLong("total_count");
                return total > 0L && total == result.getLong("applied_count");
            }
        }
    }

    private void insertConfiscationStart(
            Connection connection,
            InventoryConfiscationStartRequest request,
            Profile profile,
            long revision,
            long fence,
            Instant now
    ) throws SQLException {
        UUID snapshotId = UUID.randomUUID();
        try (PreparedStatement operation = connection.prepareStatement("""
                INSERT INTO inventory_operations(
                    operation_id, idempotency_key, profile_id, case_id, actor_id,
                    operation_type, state, expected_revision, fencing_token,
                    operation_json, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, 'CONFISCATION', 'LOCKED', ?, ?, ?, ?, ?)
                """);
             PreparedStatement snapshot = connection.prepareStatement("""
                INSERT INTO inventory_snapshots(
                    snapshot_id, operation_id, profile_id, revision, checksum,
                    snapshot_blob, created_at, expires_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """)) {
            operation.setBytes(1, UuidBytes.toBytes(request.operationId()));
            operation.setString(2, request.idempotencyKey());
            operation.setBytes(3, UuidBytes.toBytes(profile.profileId()));
            operation.setString(4, request.caseId().value());
            operation.setBytes(5, UuidBytes.toBytes(request.actorId()));
            operation.setLong(6, revision);
            operation.setLong(7, fence);
            operation.setString(8, json(Map.of(
                    "scopeId", request.scopeId(),
                    "owningServerId", request.owningServerId(),
                    "beforeChecksum", request.beforeChecksum(),
                    "selectionState", "OPEN"
            )));
            operation.setTimestamp(9, Timestamp.from(now));
            operation.setTimestamp(10, Timestamp.from(now));
            operation.executeUpdate();

            snapshot.setBytes(1, UuidBytes.toBytes(snapshotId));
            snapshot.setBytes(2, UuidBytes.toBytes(request.operationId()));
            snapshot.setBytes(3, UuidBytes.toBytes(profile.profileId()));
            snapshot.setLong(4, revision);
            snapshot.setString(5, request.beforeChecksum());
            snapshot.setBytes(6, request.beforeSnapshot());
            snapshot.setTimestamp(7, Timestamp.from(now));
            snapshot.setTimestamp(8, Timestamp.from(now.plus(SNAPSHOT_RETENTION)));
            snapshot.executeUpdate();
        }
    }

    private InventoryPatch insertConfiscationPatch(
            Connection connection,
            InventoryConfiscationSession session,
            InventoryConfiscationCommitRequest request,
            Instant now
    ) throws SQLException {
        UUID patchId = UUID.randomUUID();
        UUID assetSnapshotId = UUID.randomUUID();
        String patchJson = json(Map.of(
                "changedSlots", request.changedSlots(),
                "selectedPaths", request.selectedPaths(),
                "assetsChecksum", request.assetsChecksum()
        ));
        try (PreparedStatement operation = connection.prepareStatement("""
                UPDATE inventory_operations
                SET state = 'PENDING', operation_json = ?, updated_at = ?
                WHERE operation_id = ?
                """);
             PreparedStatement patch = connection.prepareStatement("""
                INSERT INTO inventory_pending_patches(
                    patch_id, operation_id, profile_id, expected_revision,
                    expected_checksum, replacement_checksum, replacement_blob,
                    actor_id, case_id, owning_server_id, fencing_token,
                    state, patch_json, created_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'PENDING', ?, ?)
                """);
             PreparedStatement assets = connection.prepareStatement("""
                INSERT INTO confiscated_asset_snapshots(
                    snapshot_id, case_id, inventory_operation_id, checksum,
                    asset_blob, created_at, expires_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?)
                """)) {
            operation.setString(1, json(Map.of(
                    "scopeId", session.scopeId(),
                    "owningServerId", session.owningServerId(),
                    "beforeChecksum", request.expectedChecksum(),
                    "replacementChecksum", request.replacementChecksum(),
                    "selectedPaths", request.selectedPaths(),
                    "assetsChecksum", request.assetsChecksum(),
                    "selectionState", "VALIDATED"
            )));
            operation.setTimestamp(2, Timestamp.from(now));
            operation.setBytes(3, UuidBytes.toBytes(session.operationId()));
            if (operation.executeUpdate() != 1) {
                throw new SQLException("Confiscation operation disappeared during patch validation");
            }

            patch.setBytes(1, UuidBytes.toBytes(patchId));
            patch.setBytes(2, UuidBytes.toBytes(session.operationId()));
            patch.setBytes(3, UuidBytes.toBytes(session.profileId()));
            patch.setLong(4, request.expectedRevision());
            patch.setString(5, request.expectedChecksum());
            patch.setString(6, request.replacementChecksum());
            patch.setBytes(7, request.replacementSnapshot());
            patch.setBytes(8, UuidBytes.toBytes(session.actorId()));
            patch.setString(9, session.caseId().value());
            patch.setString(10, session.owningServerId());
            patch.setLong(11, request.fencingToken());
            patch.setString(12, patchJson);
            patch.setTimestamp(13, Timestamp.from(now));
            patch.executeUpdate();

            assets.setBytes(1, UuidBytes.toBytes(assetSnapshotId));
            assets.setString(2, session.caseId().value());
            assets.setBytes(3, UuidBytes.toBytes(session.operationId()));
            assets.setString(4, request.assetsChecksum());
            assets.setBytes(5, request.assetsSnapshot());
            assets.setTimestamp(6, Timestamp.from(now));
            assets.setTimestamp(7, Timestamp.from(now.plus(SNAPSHOT_RETENTION)));
            assets.executeUpdate();
        }
        return new InventoryPatch(
                patchId,
                session.operationId(),
                session.profileId(),
                session.playerId(),
                session.scopeId(),
                session.owningServerId(),
                session.actorId(),
                Optional.of(session.caseId().value()),
                "CONFISCATION",
                InventoryOperationState.PENDING,
                request.expectedRevision(),
                request.fencingToken(),
                request.expectedChecksum(),
                request.replacementChecksum(),
                request.replacementSnapshot(),
                request.changedSlots(),
                now
        );
    }

    private Optional<InventoryConfiscationSession> lockConfiscationSessionByIdentity(
            Connection connection,
            UUID operationId,
            String idempotencyKey
    ) throws SQLException {
        String sql = """
                SELECT o.operation_id, o.profile_id, p.player_id, p.scope_id,
                    p.owning_server_id, o.actor_id, o.case_id, o.expected_revision,
                    o.fencing_token, s.checksum, s.snapshot_blob, o.created_at
                FROM inventory_operations o
                JOIN inventory_profiles p ON p.profile_id = o.profile_id
                JOIN inventory_snapshots s ON s.operation_id = o.operation_id
                WHERE o.operation_type = 'CONFISCATION'
                    AND (o.operation_id = ? OR o.idempotency_key = ?)
                LIMIT 1
                FOR UPDATE
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setBytes(1, UuidBytes.toBytes(operationId));
            statement.setString(2, idempotencyKey);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? Optional.of(readConfiscationSession(result)) : Optional.empty();
            }
        }
    }

    private Optional<InventoryConfiscationSession> lockConfiscationSession(
            Connection connection,
            UUID operationId
    ) throws SQLException {
        String sql = """
                SELECT o.operation_id, o.profile_id, p.player_id, p.scope_id,
                    p.owning_server_id, o.actor_id, o.case_id, o.expected_revision,
                    o.fencing_token, s.checksum, s.snapshot_blob, o.created_at
                FROM inventory_operations o
                JOIN inventory_profiles p ON p.profile_id = o.profile_id
                JOIN inventory_snapshots s ON s.operation_id = o.operation_id
                WHERE o.operation_type = 'CONFISCATION' AND o.operation_id = ?
                FOR UPDATE
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setBytes(1, UuidBytes.toBytes(operationId));
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? Optional.of(readConfiscationSession(result)) : Optional.empty();
            }
        }
    }

    private List<InventoryConfiscationSession> lockOpenConfiscations(
            Connection connection,
            UUID playerId,
            String scopeId,
            String owningServerId
    ) throws SQLException {
        String sql = """
                SELECT o.operation_id, o.profile_id, p.player_id, p.scope_id,
                    p.owning_server_id, o.actor_id, o.case_id, o.expected_revision,
                    o.fencing_token, s.checksum, s.snapshot_blob, o.created_at
                FROM inventory_operations o
                JOIN inventory_profiles p ON p.profile_id = o.profile_id
                JOIN inventory_snapshots s ON s.operation_id = o.operation_id
                WHERE o.operation_type = 'CONFISCATION' AND o.state = 'LOCKED'
                    AND p.player_id = ? AND p.scope_id = ? AND p.owning_server_id = ?
                FOR UPDATE
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setBytes(1, UuidBytes.toBytes(playerId));
            statement.setString(2, scopeId);
            statement.setString(3, owningServerId);
            try (ResultSet result = statement.executeQuery()) {
                List<InventoryConfiscationSession> sessions = new ArrayList<>();
                while (result.next()) {
                    sessions.add(readConfiscationSession(result));
                }
                return sessions;
            }
        }
    }

    private static InventoryConfiscationSession readConfiscationSession(ResultSet result)
            throws SQLException {
        return new InventoryConfiscationSession(
                UuidBytes.fromBytes(result.getBytes("operation_id")),
                UuidBytes.fromBytes(result.getBytes("profile_id")),
                UuidBytes.fromBytes(result.getBytes("player_id")),
                result.getString("scope_id"),
                result.getString("owning_server_id"),
                UuidBytes.fromBytes(result.getBytes("actor_id")),
                new net.enthusia.staff.common.CaseId(result.getString("case_id")),
                result.getLong("expected_revision"),
                result.getLong("fencing_token"),
                result.getString("checksum"),
                result.getBytes("snapshot_blob"),
                result.getTimestamp("created_at").toInstant()
        );
    }

    private String confiscationState(Connection connection, UUID operationId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT state
                FROM inventory_operations
                WHERE operation_id = ? AND operation_type = 'CONFISCATION'
                FOR UPDATE
                """)) {
            statement.setBytes(1, UuidBytes.toBytes(operationId));
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? result.getString(STATE_COLUMN) : "";
            }
        }
    }

    private Optional<InventoryPatch> findPatchByOperation(
            Connection connection,
            UUID operationId
    ) throws SQLException {
        String sql = """
                SELECT q.patch_id, q.operation_id, q.profile_id, p.player_id, p.scope_id,
                    p.owning_server_id, o.actor_id, o.case_id, o.operation_type, q.state,
                    q.expected_revision, q.fencing_token, q.expected_checksum,
                    q.replacement_checksum, q.replacement_blob, q.patch_json, q.created_at
                FROM inventory_pending_patches q
                JOIN inventory_profiles p ON p.profile_id = q.profile_id
                JOIN inventory_operations o ON o.operation_id = q.operation_id
                WHERE q.operation_id = ?
                FOR UPDATE
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setBytes(1, UuidBytes.toBytes(operationId));
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? Optional.of(readPatch(result)) : Optional.empty();
            }
        }
    }

    private static void validateConfiscationReplay(
            InventoryConfiscationSession session,
            InventoryConfiscationStartRequest request
    ) {
        if (!session.operationId().equals(request.operationId())
                || !session.playerId().equals(request.playerId())
                || !session.scopeId().equals(request.scopeId())
                || !session.owningServerId().equals(request.owningServerId())
                || !session.actorId().equals(request.actorId())
                || !session.caseId().equals(request.caseId())
                || !session.beforeChecksum().equals(request.beforeChecksum())) {
            throw new IllegalArgumentException(
                    "idempotency key is already bound to another confiscation selection"
            );
        }
    }

    private static void validateConfiscationCommitReplay(
            InventoryPatch patch,
            InventoryConfiscationCommitRequest request
    ) {
        if (!patch.operationId().equals(request.operationId())
                || patch.fencingToken() != request.fencingToken()
                || patch.expectedRevision() != request.expectedRevision()
                || !patch.expectedChecksum().equals(request.expectedChecksum())
                || !patch.replacementChecksum().equals(request.replacementChecksum())
                || !patch.changedSlots().equals(request.changedSlots())) {
            throw new IllegalArgumentException(
                    "confiscation operation is already bound to another exact patch"
            );
        }
    }

    private static boolean hasOpenConfiscation(Connection connection, UUID profileId)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT 1
                FROM inventory_operations
                WHERE profile_id = ? AND operation_type = 'CONFISCATION'
                    AND state IN ('LOCKED', 'PENDING', 'APPLYING', 'QUARANTINED')
                LIMIT 1
                FOR UPDATE
                """)) {
            statement.setBytes(1, UuidBytes.toBytes(profileId));
            try (ResultSet result = statement.executeQuery()) {
                return result.next();
            }
        }
    }

    private static boolean ownsLease(
            Connection connection,
            InventoryConfiscationSession session,
            Instant now
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT owner_id, fencing_token, lease_until
                FROM operation_leases
                WHERE resource_key = ?
                FOR UPDATE
                """)) {
            statement.setString(1, resourceKey(session.playerId(), session.scopeId()));
            try (ResultSet result = statement.executeQuery()) {
                return result.next()
                        && result.getString("owner_id").equals(session.operationId().toString())
                        && result.getLong("fencing_token") == session.fencingToken()
                        && result.getTimestamp("lease_until").toInstant().isAfter(now);
            }
        }
    }

    private static void renewLease(
            Connection connection,
            InventoryConfiscationSession session,
            Instant leaseUntil,
            Instant now
    ) throws SQLException {
        try (PreparedStatement lease = connection.prepareStatement("""
                UPDATE operation_leases
                SET lease_until = ?, updated_at = ?
                WHERE resource_key = ? AND owner_id = ? AND fencing_token = ?
                """);
             PreparedStatement operation = connection.prepareStatement("""
                UPDATE inventory_operations
                SET updated_at = ?
                WHERE operation_id = ? AND fencing_token = ?
                """)) {
            lease.setTimestamp(1, Timestamp.from(leaseUntil));
            lease.setTimestamp(2, Timestamp.from(now));
            lease.setString(3, resourceKey(session.playerId(), session.scopeId()));
            lease.setString(4, session.operationId().toString());
            lease.setLong(5, session.fencingToken());
            if (lease.executeUpdate() != 1) {
                throw new SQLException("Confiscation inventory lease disappeared during renewal");
            }
            operation.setTimestamp(1, Timestamp.from(now));
            operation.setBytes(2, UuidBytes.toBytes(session.operationId()));
            operation.setLong(3, session.fencingToken());
            if (operation.executeUpdate() != 1) {
                throw new SQLException("Confiscation operation disappeared during renewal");
            }
        }
    }

    private void markConfiscationCancelled(
            Connection connection,
            InventoryConfiscationSession session,
            String reasonCode,
            String detail,
            Instant now
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE inventory_operations
                SET state = 'ROLLED_BACK', updated_at = ?
                WHERE operation_id = ? AND state = 'LOCKED'
                """)) {
            statement.setTimestamp(1, Timestamp.from(now));
            statement.setBytes(2, UuidBytes.toBytes(session.operationId()));
            if (statement.executeUpdate() != 1) {
                throw new SQLException("Confiscation selection changed before cancellation");
            }
        }
        insertAudit(
                connection,
                session.operationId(),
                session.actorId(),
                session.playerId(),
                Optional.of(session.caseId().value()),
                "INVENTORY_CONFISCATION_CANCELLED",
                "ROLLED_BACK",
                Map.of("reasonCode", reasonCode, "detail", detail),
                "inventory:confiscation:cancelled:" + session.operationId(),
                now
        );
        releaseLease(connection, session);
    }

    private static void releaseLease(
            Connection connection,
            InventoryConfiscationSession session
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                DELETE FROM operation_leases
                WHERE resource_key = ? AND owner_id = ? AND fencing_token = ?
                """)) {
            statement.setString(1, resourceKey(session.playerId(), session.scopeId()));
            statement.setString(2, session.operationId().toString());
            statement.setLong(3, session.fencingToken());
            statement.executeUpdate();
        }
    }

    private InventoryPatch insertPreparedOperation(
            Connection connection,
            InventoryPrepareRequest request,
            Profile profile,
            long fence,
            Instant now
    ) throws SQLException {
        UUID snapshotId = UUID.randomUUID();
        UUID patchId = UUID.randomUUID();
        String operationJson = json(Map.of(
                "scopeId", request.scopeId(),
                "owningServerId", request.owningServerId(),
                "expectedChecksum", request.expectedChecksum(),
                "replacementChecksum", request.replacementChecksum(),
                "changedSlots", request.changedSlots(),
                "requireNetworkOffline", request.requireNetworkOffline()
        ));
        try (PreparedStatement operation = connection.prepareStatement("""
                INSERT INTO inventory_operations(
                    operation_id, idempotency_key, profile_id, case_id, actor_id,
                    operation_type, state, expected_revision, fencing_token,
                    operation_json, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, 'PENDING', ?, ?, ?, ?, ?)
                """);
             PreparedStatement snapshot = connection.prepareStatement("""
                INSERT INTO inventory_snapshots(
                    snapshot_id, operation_id, profile_id, revision, checksum,
                    snapshot_blob, created_at, expires_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """);
             PreparedStatement patch = connection.prepareStatement("""
                INSERT INTO inventory_pending_patches(
                    patch_id, operation_id, profile_id, expected_revision,
                    expected_checksum, replacement_checksum, replacement_blob,
                    actor_id, case_id, owning_server_id, fencing_token,
                    state, patch_json, created_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'PENDING', ?, ?)
                """)) {
            operation.setBytes(1, UuidBytes.toBytes(request.operationId()));
            operation.setString(2, request.idempotencyKey());
            operation.setBytes(3, UuidBytes.toBytes(profile.profileId()));
            if (request.caseId().isPresent()) {
                operation.setString(4, request.caseId().orElseThrow());
            } else {
                operation.setNull(4, java.sql.Types.CHAR);
            }
            operation.setBytes(5, UuidBytes.toBytes(request.actorId()));
            operation.setString(6, request.operationType());
            operation.setLong(7, request.expectedRevision());
            operation.setLong(8, fence);
            operation.setString(9, operationJson);
            operation.setTimestamp(10, Timestamp.from(now));
            operation.setTimestamp(11, Timestamp.from(now));
            operation.executeUpdate();

            snapshot.setBytes(1, UuidBytes.toBytes(snapshotId));
            snapshot.setBytes(2, UuidBytes.toBytes(request.operationId()));
            snapshot.setBytes(3, UuidBytes.toBytes(profile.profileId()));
            snapshot.setLong(4, request.expectedRevision());
            snapshot.setString(5, request.expectedChecksum());
            snapshot.setBytes(6, request.beforeSnapshot());
            snapshot.setTimestamp(7, Timestamp.from(now));
            snapshot.setTimestamp(8, Timestamp.from(now.plus(SNAPSHOT_RETENTION)));
            snapshot.executeUpdate();

            patch.setBytes(1, UuidBytes.toBytes(patchId));
            patch.setBytes(2, UuidBytes.toBytes(request.operationId()));
            patch.setBytes(3, UuidBytes.toBytes(profile.profileId()));
            patch.setLong(4, request.expectedRevision());
            patch.setString(5, request.expectedChecksum());
            patch.setString(6, request.replacementChecksum());
            patch.setBytes(7, request.replacementSnapshot());
            patch.setBytes(8, UuidBytes.toBytes(request.actorId()));
            if (request.caseId().isPresent()) {
                patch.setString(9, request.caseId().orElseThrow());
            } else {
                patch.setNull(9, java.sql.Types.CHAR);
            }
            patch.setString(10, request.owningServerId());
            patch.setLong(11, fence);
            patch.setString(12, json(Map.of("changedSlots", request.changedSlots())));
            patch.setTimestamp(13, Timestamp.from(now));
            patch.executeUpdate();
        }
        return new InventoryPatch(
                patchId,
                request.operationId(),
                profile.profileId(),
                request.playerId(),
                request.scopeId(),
                request.owningServerId(),
                request.actorId(),
                request.caseId(),
                request.operationType(),
                InventoryOperationState.PENDING,
                request.expectedRevision(),
                fence,
                request.expectedChecksum(),
                request.replacementChecksum(),
                request.replacementSnapshot(),
                request.changedSlots(),
                now
        );
    }

    private Optional<InventoryPatch> findReplay(Connection connection, InventoryPrepareRequest request)
            throws SQLException {
        String sql = """
                SELECT q.patch_id, q.operation_id, q.profile_id, p.player_id, p.scope_id,
                    p.owning_server_id, o.actor_id, o.case_id, o.operation_type, q.state,
                    q.expected_revision, q.fencing_token, q.expected_checksum,
                    q.replacement_checksum, q.replacement_blob, q.patch_json, q.created_at
                FROM inventory_operations o
                JOIN inventory_pending_patches q ON q.operation_id = o.operation_id
                JOIN inventory_profiles p ON p.profile_id = q.profile_id
                WHERE o.operation_id = ? OR o.idempotency_key = ?
                LIMIT 1
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setBytes(1, UuidBytes.toBytes(request.operationId()));
            statement.setString(2, request.idempotencyKey());
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? Optional.of(readPatch(result)) : Optional.empty();
            }
        }
    }

    private void validateReplay(InventoryPatch patch, InventoryPrepareRequest request) {
        if (!patch.operationId().equals(request.operationId())
                || !patch.playerId().equals(request.playerId())
                || !patch.scopeId().equals(request.scopeId())
                || !patch.owningServerId().equals(request.owningServerId())
                || !patch.actorId().equals(request.actorId())
                || !patch.caseId().equals(request.caseId())
                || !patch.operationType().equals(request.operationType())
                || patch.expectedRevision() != request.expectedRevision()
                || !patch.expectedChecksum().equals(request.expectedChecksum())
                || !patch.replacementChecksum().equals(request.replacementChecksum())
                || !patch.changedSlots().equals(request.changedSlots())) {
            throw new IllegalArgumentException("idempotency key is already bound to another inventory operation");
        }
    }

    private Optional<InventoryPatch> lockPatch(Connection connection, UUID patchId) throws SQLException {
        String sql = """
                SELECT q.patch_id, q.operation_id, q.profile_id, p.player_id, p.scope_id,
                    p.owning_server_id, o.actor_id, o.case_id, o.operation_type, q.state,
                    q.expected_revision, q.fencing_token, q.expected_checksum,
                    q.replacement_checksum, q.replacement_blob, q.patch_json, q.created_at
                FROM inventory_pending_patches q
                JOIN inventory_profiles p ON p.profile_id = q.profile_id
                JOIN inventory_operations o ON o.operation_id = q.operation_id
                WHERE q.patch_id = ?
                FOR UPDATE
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setBytes(1, UuidBytes.toBytes(patchId));
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? Optional.of(readPatch(result)) : Optional.empty();
            }
        }
    }

    private InventoryPatch readPatch(ResultSet result) throws SQLException {
        List<Integer> slots = new ArrayList<>();
        try {
            JsonNode root = json.readTree(result.getString("patch_json"));
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

    private Profile lockOrCreateProfile(
            Connection connection,
            UUID playerId,
            String scopeId,
            String owningServerId
    ) throws SQLException {
        Optional<Profile> existing = lockProfile(connection, playerId, scopeId);
        if (existing.isPresent()) {
            return existing.orElseThrow();
        }
        UUID profileId = UUID.randomUUID();
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO inventory_profiles(
                    profile_id, player_id, scope_id, owning_server_id, current_revision
                ) VALUES (?, ?, ?, ?, 0)
                """)) {
            statement.setBytes(1, UuidBytes.toBytes(profileId));
            statement.setBytes(2, UuidBytes.toBytes(playerId));
            statement.setString(3, scopeId);
            statement.setString(4, owningServerId);
            statement.executeUpdate();
        }
        return new Profile(profileId, playerId, scopeId, owningServerId, 0L);
    }

    private Optional<Profile> lockProfile(Connection connection, UUID playerId, String scopeId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT profile_id, player_id, scope_id, owning_server_id, current_revision
                FROM inventory_profiles
                WHERE player_id = ? AND scope_id = ?
                FOR UPDATE
                """)) {
            statement.setBytes(1, UuidBytes.toBytes(playerId));
            statement.setString(2, scopeId);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? Optional.of(new Profile(
                        UuidBytes.fromBytes(result.getBytes("profile_id")),
                        UuidBytes.fromBytes(result.getBytes("player_id")),
                        result.getString("scope_id"),
                        result.getString("owning_server_id"),
                        result.getLong("current_revision")
                )) : Optional.empty();
            }
        }
    }

    private Optional<InventoryObservation> observation(Connection connection, Profile profile, boolean lock)
            throws SQLException {
        String sql = """
                SELECT p.profile_id, p.player_id, p.scope_id, p.owning_server_id,
                    o.revision, o.checksum, o.snapshot_blob, o.observed_at
                FROM inventory_profiles p
                JOIN inventory_observations o ON o.profile_id = p.profile_id
                WHERE p.profile_id = ?
                """ + (lock ? " FOR UPDATE" : "");
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setBytes(1, UuidBytes.toBytes(profile.profileId()));
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? Optional.of(readObservation(result)) : Optional.empty();
            }
        }
    }

    private static InventoryObservation readObservation(ResultSet result) throws SQLException {
        return new InventoryObservation(
                UuidBytes.fromBytes(result.getBytes("profile_id")),
                UuidBytes.fromBytes(result.getBytes("player_id")),
                result.getString("scope_id"),
                result.getString("owning_server_id"),
                result.getLong("revision"),
                result.getString("checksum"),
                result.getBytes("snapshot_blob"),
                result.getTimestamp("observed_at").toInstant()
        );
    }

    private static void updateProfile(
            Connection connection,
            UUID profileId,
            String owningServerId,
            long revision
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE inventory_profiles
                SET owning_server_id = ?, current_revision = ?
                WHERE profile_id = ?
                """)) {
            statement.setString(1, owningServerId);
            statement.setLong(2, revision);
            statement.setBytes(3, UuidBytes.toBytes(profileId));
            if (statement.executeUpdate() != 1) {
                throw new SQLException("Inventory profile disappeared during update");
            }
        }
    }

    private static void saveObservation(
            Connection connection,
            UUID profileId,
            long revision,
            String checksum,
            byte[] snapshot,
            Instant observedAt
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO inventory_observations(
                    profile_id, revision, checksum, snapshot_blob, observed_at
                ) VALUES (?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE revision = VALUES(revision), checksum = VALUES(checksum),
                    snapshot_blob = VALUES(snapshot_blob), observed_at = VALUES(observed_at)
                """)) {
            statement.setBytes(1, UuidBytes.toBytes(profileId));
            statement.setLong(2, revision);
            statement.setString(3, checksum);
            statement.setBytes(4, snapshot);
            statement.setTimestamp(5, Timestamp.from(observedAt));
            statement.executeUpdate();
        }
    }

    private static void saveRevision(
            Connection connection,
            UUID profileId,
            long revision,
            String checksum,
            Instant recordedAt
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT IGNORE INTO inventory_profile_revisions(
                    profile_id, revision, checksum, recorded_at
                ) VALUES (?, ?, ?, ?)
                """)) {
            statement.setBytes(1, UuidBytes.toBytes(profileId));
            statement.setLong(2, revision);
            statement.setString(3, checksum);
            statement.setTimestamp(4, Timestamp.from(recordedAt));
            statement.executeUpdate();
        }
    }

    private boolean playerOnline(Connection connection, UUID playerId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT current_server
                FROM players
                WHERE player_id = ?
                FOR UPDATE
                """)) {
            statement.setBytes(1, UuidBytes.toBytes(playerId));
            try (ResultSet result = statement.executeQuery()) {
                return result.next() && result.getString("current_server") != null;
            }
        }
    }

    private static boolean hasPendingPatch(Connection connection, UUID profileId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT 1
                FROM inventory_pending_patches
                WHERE profile_id = ? AND state IN ('PENDING', 'APPLYING')
                LIMIT 1
                FOR UPDATE
                """)) {
            statement.setBytes(1, UuidBytes.toBytes(profileId));
            try (ResultSet result = statement.executeQuery()) {
                return result.next();
            }
        }
    }

    private static long claimLease(
            Connection connection,
            InventoryPatch patch,
            Instant leaseUntil,
            Instant now
    ) throws SQLException {
        String resourceKey = resourceKey(patch.playerId(), patch.scopeId());
        try (PreparedStatement select = connection.prepareStatement("""
                SELECT owner_id, fencing_token, lease_until
                FROM operation_leases
                WHERE resource_key = ?
                FOR UPDATE
                """)) {
            select.setString(1, resourceKey);
            try (ResultSet result = select.executeQuery()) {
                if (!result.next()) {
                    long fence = patch.fencingToken() + 1L;
                    try (PreparedStatement insert = connection.prepareStatement("""
                            INSERT INTO operation_leases(
                                resource_key, owner_id, fencing_token, lease_until, updated_at
                            ) VALUES (?, ?, ?, ?, ?)
                            """)) {
                        insert.setString(1, resourceKey);
                        insert.setString(2, patch.operationId().toString());
                        insert.setLong(3, fence);
                        insert.setTimestamp(4, Timestamp.from(leaseUntil));
                        insert.setTimestamp(5, Timestamp.from(now));
                        insert.executeUpdate();
                    }
                    return fence;
                }
                String owner = result.getString("owner_id");
                long currentFence = result.getLong("fencing_token");
                Instant currentExpiry = result.getTimestamp("lease_until").toInstant();
                boolean sameOwner = owner.equals(patch.operationId().toString());
                if (currentExpiry.isAfter(now) && !sameOwner) {
                    return 0L;
                }
                long fence = Math.max(currentFence, patch.fencingToken()) + 1L;
                try (PreparedStatement update = connection.prepareStatement("""
                        UPDATE operation_leases
                        SET owner_id = ?, fencing_token = ?, lease_until = ?, updated_at = ?
                        WHERE resource_key = ?
                        """)) {
                    update.setString(1, patch.operationId().toString());
                    update.setLong(2, fence);
                    update.setTimestamp(3, Timestamp.from(leaseUntil));
                    update.setTimestamp(4, Timestamp.from(now));
                    update.setString(5, resourceKey);
                    update.executeUpdate();
                }
                return fence;
            }
        }
    }

    private static boolean ownsLease(Connection connection, InventoryPatch patch, Instant now) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT owner_id, fencing_token, lease_until
                FROM operation_leases
                WHERE resource_key = ?
                FOR UPDATE
                """)) {
            statement.setString(1, resourceKey(patch.playerId(), patch.scopeId()));
            try (ResultSet result = statement.executeQuery()) {
                return result.next()
                        && result.getString("owner_id").equals(patch.operationId().toString())
                        && result.getLong("fencing_token") == patch.fencingToken()
                        && result.getTimestamp("lease_until").toInstant().isAfter(now);
            }
        }
    }

    private static void releaseLease(Connection connection, InventoryPatch patch) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                DELETE FROM operation_leases
                WHERE resource_key = ? AND owner_id = ? AND fencing_token = ?
                """)) {
            statement.setString(1, resourceKey(patch.playerId(), patch.scopeId()));
            statement.setString(2, patch.operationId().toString());
            statement.setLong(3, patch.fencingToken());
            statement.executeUpdate();
        }
    }

    private static void markApplied(Connection connection, InventoryPatch patch, Instant now) throws SQLException {
        try (PreparedStatement pending = connection.prepareStatement("""
                UPDATE inventory_pending_patches
                SET state = 'APPLIED', applied_at = ?, conflict_code = NULL, conflict_detail = NULL
                WHERE patch_id = ?
                """);
             PreparedStatement operation = connection.prepareStatement("""
                UPDATE inventory_operations
                SET state = 'COMMITTED', updated_at = ?
                WHERE operation_id = ?
                """)) {
            pending.setTimestamp(1, Timestamp.from(now));
            pending.setBytes(2, UuidBytes.toBytes(patch.patchId()));
            pending.executeUpdate();
            operation.setTimestamp(1, Timestamp.from(now));
            operation.setBytes(2, UuidBytes.toBytes(patch.operationId()));
            operation.executeUpdate();
        }
        markRestorationApplied(connection, patch, now);
    }

    private void insertCommitAudit(
            Connection connection,
            InventoryPatch patch,
            long resultingRevision,
            Instant now
    ) throws SQLException {
        insertAudit(
                connection,
                patch.operationId(),
                patch.actorId(),
                patch.playerId(),
                patch.caseId(),
                "INVENTORY_OPERATION_COMMITTED",
                "COMMITTED",
                Map.of(
                        "operationType", patch.operationType(),
                        "scopeId", patch.scopeId(),
                        "resultingRevision", resultingRevision,
                        "changedSlots", patch.changedSlots()
                ),
                "inventory:commit:" + patch.operationId(),
                now
        );
    }

    private static void markRestorationApplied(
            Connection connection,
            InventoryPatch patch,
            Instant now
    ) throws SQLException {
        if (!patch.operationType().equals(RESTORATION_OPERATION_TYPE)
                || patch.caseId().isEmpty()) {
            return;
        }
        try (PreparedStatement statement = connection.prepareStatement(
                JdbcInventoryRestorationSql.MARK_APPLIED
        )) {
            statement.setBytes(1, UuidBytes.toBytes(patch.patchId()));
            statement.setTimestamp(2, Timestamp.from(now));
            statement.setString(3, patch.replacementChecksum());
            statement.setString(4, patch.caseId().orElseThrow());
            statement.setBytes(5, UuidBytes.toBytes(patch.operationId()));
            statement.setString(6, CONFISCATION_OPERATION_TYPE);
            statement.setString(7, RESTORATION_OPERATION_TYPE);
            statement.setString(8, patch.replacementChecksum());
            statement.executeUpdate();
        }
    }

    private void quarantineInternal(
            Connection connection,
            InventoryPatch patch,
            String reasonCode,
            String detail,
            Instant now
    ) throws SQLException {
        try (PreparedStatement pending = connection.prepareStatement("""
                UPDATE inventory_pending_patches
                SET state = 'QUARANTINED', conflict_code = ?, conflict_detail = ?
                WHERE patch_id = ?
                """);
             PreparedStatement operation = connection.prepareStatement("""
                UPDATE inventory_operations
                SET state = 'QUARANTINED', updated_at = ?
                WHERE operation_id = ?
                """);
             PreparedStatement quarantine = connection.prepareStatement("""
                INSERT INTO recovery_quarantine(
                    quarantine_id, operation_type, operation_id, resource_key,
                    reason_code, detail_json, quarantined_at
                ) VALUES (?, 'INVENTORY', ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE reason_code = VALUES(reason_code),
                    detail_json = VALUES(detail_json), quarantined_at = VALUES(quarantined_at)
                """)) {
            pending.setString(1, reasonCode);
            pending.setString(2, detail);
            pending.setBytes(3, UuidBytes.toBytes(patch.patchId()));
            pending.executeUpdate();
            operation.setTimestamp(1, Timestamp.from(now));
            operation.setBytes(2, UuidBytes.toBytes(patch.operationId()));
            operation.executeUpdate();
            quarantine.setBytes(1, UuidBytes.toBytes(UUID.randomUUID()));
            quarantine.setBytes(2, UuidBytes.toBytes(patch.operationId()));
            quarantine.setString(3, resourceKey(patch.playerId(), patch.scopeId()));
            quarantine.setString(4, reasonCode);
            quarantine.setString(5, json(Map.of("detail", detail, "patchId", patch.patchId().toString())));
            quarantine.setTimestamp(6, Timestamp.from(now));
            quarantine.executeUpdate();
        }
        releaseLease(connection, patch);
    }

    private void insertAudit(
            Connection connection,
            UUID correlationId,
            UUID actorId,
            UUID targetId,
            Optional<String> caseId,
            String eventType,
            String outcome,
            Map<String, ?> detail,
            String idempotencyKey,
            Instant occurredAt
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT IGNORE INTO audit_events(
                    event_id, correlation_id, actor_id, target_id, case_id,
                    event_type, outcome, event_json, idempotency_key, occurred_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """)) {
            statement.setBytes(1, UuidBytes.toBytes(UUID.randomUUID()));
            statement.setBytes(2, UuidBytes.toBytes(correlationId));
            statement.setBytes(3, UuidBytes.toBytes(actorId));
            statement.setBytes(4, UuidBytes.toBytes(targetId));
            if (caseId.isPresent()) {
                statement.setString(5, caseId.orElseThrow());
            } else {
                statement.setNull(5, java.sql.Types.CHAR);
            }
            statement.setString(6, eventType);
            statement.setString(7, outcome);
            statement.setString(8, json(detail));
            statement.setString(9, idempotencyKey);
            statement.setTimestamp(10, Timestamp.from(occurredAt));
            statement.executeUpdate();
        }
    }

    private String json(Map<String, ?> values) {
        try {
            return json.writeValueAsString(new LinkedHashMap<>(values));
        } catch (JsonProcessingException exception) {
            throw failure("Unable to serialize inventory journal metadata", exception);
        }
    }

    private static String resourceKey(UUID playerId, String scopeId) {
        return "inventory:" + playerId + ':' + scopeId;
    }

    private static InventoryPreparation rejected(InventoryPreparation.Status status, String detail) {
        return new InventoryPreparation(status, Optional.empty(), detail);
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

    private static InventoryFinalizeResult finalized(
            InventoryFinalizeResult.Status status,
            long revision,
            String detail
    ) {
        return new InventoryFinalizeResult(status, revision, detail);
    }

    private <T> T transaction(JdbcTransactionSupport.TransactionWork<T> work) {
        return JdbcTransactionSupport.execute(
                dataSource,
                "Inventory journal transaction failed",
                work
        );
    }

    private static ModerationPersistenceException failure(String message, Exception cause) {
        return new ModerationPersistenceException(message, cause);
    }

    private record Profile(
            UUID profileId,
            UUID playerId,
            String scopeId,
            String owningServerId,
            long revision
    ) {
    }

    private record RestorationOperationBinding(
            String caseId,
            String operationType,
            String state
    ) {
        private boolean matches(CaseId expectedCaseId) {
            return expectedCaseId.value().equals(caseId)
                    && RESTORATION_OPERATION_TYPE.equals(operationType);
        }

        private boolean replayable() {
            return "PENDING".equals(state);
        }
    }

    private record RestorationSnapshotSet(
            List<ConfiscatedAssetSnapshot> snapshots,
            boolean safe
    ) {
    }
}
