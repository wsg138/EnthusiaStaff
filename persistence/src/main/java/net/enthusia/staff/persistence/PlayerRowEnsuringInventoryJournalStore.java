package net.enthusia.staff.persistence;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
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
import net.enthusia.staff.domain.inventory.InventoryFinalizeResult;
import net.enthusia.staff.domain.inventory.InventoryObservation;
import net.enthusia.staff.domain.inventory.InventoryPatch;
import net.enthusia.staff.domain.inventory.InventoryPreparation;
import net.enthusia.staff.domain.inventory.InventoryPrepareRequest;
import net.enthusia.staff.domain.ports.InventoryJournalStore;

/**
 * Ensures the inventory profile FK parent exists before profile-creating journal operations.
 * The placeholder never overwrites an observed player row; JdbcPlayerDirectory later enriches
 * it atomically with authoritative name, platform, and presence data.
 */
public final class PlayerRowEnsuringInventoryJournalStore implements InventoryJournalStore {
    private static final String ENSURE_PLAYER = """
            INSERT INTO players(player_id, platform, first_seen_at, last_seen_at)
            VALUES (?, 'UNKNOWN', ?, ?)
            ON DUPLICATE KEY UPDATE player_id = VALUES(player_id)
            """;

    private final DataSource dataSource;
    private final InventoryJournalStore delegate;

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
        if (request != null) {
            ensurePlayer(request.playerId(), now);
        }
        return delegate.beginConfiscation(request, leaseDuration, now);
    }

    @Override
    public Optional<InventoryConfiscationSession> renewConfiscation(
            UUID operationId,
            long fencingToken,
            Duration leaseDuration,
            Instant now
    ) {
        return delegate.renewConfiscation(operationId, fencingToken, leaseDuration, now);
    }

    @Override
    public InventoryPreparation prepareConfiscation(InventoryConfiscationCommitRequest request, Instant now) {
        return delegate.prepareConfiscation(request, now);
    }

    @Override
    public boolean cancelConfiscation(
            UUID operationId,
            long fencingToken,
            String reasonCode,
            String detail,
            Instant now
    ) {
        return delegate.cancelConfiscation(operationId, fencingToken, reasonCode, detail, now);
    }

    @Override
    public int cancelAbandonedConfiscations(
            UUID playerId,
            String scopeId,
            String owningServerId,
            Instant now
    ) {
        return delegate.cancelAbandonedConfiscations(playerId, scopeId, owningServerId, now);
    }

    @Override
    public ConfiscatedAssetReservation reserveRestoration(CaseId caseId, UUID restorationOperationId, Instant now) {
        return delegate.reserveRestoration(caseId, restorationOperationId, now);
    }

    @Override
    public boolean cancelRestoration(CaseId caseId, UUID restorationOperationId, Instant now) {
        return delegate.cancelRestoration(caseId, restorationOperationId, now);
    }

    @Override
    public boolean finalizeRestoration(
            CaseId caseId,
            UUID restorationOperationId,
            String restoredChecksum,
            Instant now
    ) {
        return delegate.finalizeRestoration(caseId, restorationOperationId, restoredChecksum, now);
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
        ensurePlayer(playerId, observedAt);
        return delegate.recordObservation(playerId, scopeId, owningServerId, checksum, snapshot, observedAt);
    }

    @Override
    public Optional<InventoryObservation> latest(UUID playerId, String scopeId) {
        return delegate.latest(playerId, scopeId);
    }

    @Override
    public InventoryPreparation prepare(InventoryPrepareRequest request, Duration leaseDuration, Instant now) {
        if (request != null) {
            ensurePlayer(request.playerId(), now);
        }
        return delegate.prepare(request, leaseDuration, now);
    }

    @Override
    public List<InventoryPatch> pending(UUID playerId, String scopeId, String owningServerId, int limit) {
        return delegate.pending(playerId, scopeId, owningServerId, limit);
    }

    @Override
    public Optional<InventoryPatch> claimForApply(
            UUID patchId,
            UUID operationId,
            Duration leaseDuration,
            Instant now
    ) {
        return delegate.claimForApply(patchId, operationId, leaseDuration, now);
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
                patchId,
                operationId,
                fencingToken,
                observedChecksum,
                observedSnapshot,
                now
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

    private void ensurePlayer(UUID playerId, Instant observedAt) {
        if (playerId == null || observedAt == null) {
            return;
        }
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(ENSURE_PLAYER)) {
            statement.setBytes(1, UuidBytes.toBytes(playerId));
            statement.setTimestamp(2, Timestamp.from(observedAt));
            statement.setTimestamp(3, Timestamp.from(observedAt));
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new ModerationPersistenceException("Unable to ensure inventory player parent row", exception);
        }
    }
}
