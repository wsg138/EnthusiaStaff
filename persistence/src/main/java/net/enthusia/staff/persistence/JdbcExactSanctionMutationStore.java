package net.enthusia.staff.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;
import javax.sql.DataSource;
import net.enthusia.staff.common.CaseId;
import net.enthusia.staff.domain.auth.StaffHierarchy;
import net.enthusia.staff.domain.auth.StaffRank;
import net.enthusia.staff.domain.ports.SanctionMutationStore;
import net.enthusia.staff.domain.sanction.ExactSanctionChangeRequest;
import net.enthusia.staff.domain.sanction.ExactSanctionChangeResult;
import net.enthusia.staff.domain.sanction.SanctionActionLimits;
import net.enthusia.staff.domain.sanction.SanctionChangeAction;
import net.enthusia.staff.domain.sanction.SanctionChangeRequest;
import net.enthusia.staff.domain.sanction.SanctionChangeResult;
import net.enthusia.staff.domain.sanction.SanctionStatus;
import net.enthusia.staff.persistence.ExactSanctionMutationDecision.Apply;
import net.enthusia.staff.persistence.ExactSanctionMutationDecision.NoChange;
import net.enthusia.staff.persistence.ExactSanctionMutationDecision.Rejected;

final class JdbcExactSanctionMutationStore implements SanctionMutationStore {
    private static final int EXPECTED_UPDATED_ROWS = 1;
    private static final String CASE_ID_COLUMN = "case_id";
    private static final String OVERTURNED_STATUS = "OVERTURNED";

    private final DataSource dataSource;
    private final Clock clock;
    private final ExactSanctionEventWriter eventWriter;

    JdbcExactSanctionMutationStore(DataSource dataSource, ObjectMapper json, Clock clock) {
        if (dataSource == null || json == null || clock == null) {
            throw new IllegalArgumentException("exact sanction mutation dependencies must be present");
        }
        this.dataSource = dataSource;
        this.clock = clock;
        this.eventWriter = new ExactSanctionEventWriter(json);
    }

    @Override
    public SanctionChangeResult apply(SanctionChangeRequest request) {
        return new SanctionChangeResult.Rejected(
                "UNSUPPORTED",
                "Case-wide sanction changes are not handled by the exact mutation store"
        );
    }

    @Override
    public boolean supportsExactChanges() {
        return true;
    }

    @Override
    public OptionalLong exactRevision(UUID sanctionId) {
        if (sanctionId == null) {
            throw new IllegalArgumentException("sanctionId must be present");
        }
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT revision FROM sanctions WHERE sanction_id = ?"
             )) {
            statement.setBytes(1, UuidBytes.toBytes(sanctionId));
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? OptionalLong.of(result.getLong(1)) : OptionalLong.empty();
            }
        } catch (SQLException exception) {
            throw new ModerationPersistenceException("Unable to read exact sanction revision", exception);
        }
    }

    @Override
    public ExactSanctionChangeResult applyExact(
            ExactSanctionChangeRequest request,
            SanctionActionLimits limits
    ) {
        if (request == null || limits == null) {
            throw new IllegalArgumentException("exact sanction request and limits must be present");
        }
        if (!limits.accepts(request.reason())) {
            return new ExactSanctionChangeResult.Rejected(
                    "INVALID_REASON",
                    "The reason length is outside the configured limits"
            );
        }
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            return executeTransaction(connection, request, limits);
        } catch (SQLException exception) {
            throw new ModerationPersistenceException(
                    "Unable to open exact sanction change transaction",
                    exception
            );
        }
    }

    private ExactSanctionChangeResult executeTransaction(
            Connection connection,
            ExactSanctionChangeRequest request,
            SanctionActionLimits limits
    ) {
        try {
            return applyInTransaction(connection, request, limits);
        } catch (SQLException | JsonProcessingException exception) {
            rollback(connection, exception);
            ExactSanctionChangeResult.Applied replay = replayAfterConflict(
                    request.idempotencyKey().value()
            );
            if (replay != null) {
                return replay;
            }
            throw new ModerationPersistenceException(
                    "Exact sanction change transaction failed",
                    exception
            );
        } catch (RuntimeException exception) {
            rollback(connection, exception);
            throw exception;
        } finally {
            restoreAutoCommit(connection);
        }
    }

    private ExactSanctionChangeResult applyInTransaction(
            Connection connection,
            ExactSanctionChangeRequest request,
            SanctionActionLimits limits
    ) throws SQLException, JsonProcessingException {
        ExactSanctionChangeResult.Applied replay = replay(
                connection,
                request.idempotencyKey().value()
        );
        if (replay != null) {
            return rollbackAndReturn(connection, replay);
        }
        ExactSanctionRow row = lockSanction(connection, request.sanctionId());
        Optional<ExactSanctionChangeResult> stateFailure = validateLockedSanction(
                connection,
                request,
                row
        );
        return stateFailure.isPresent()
                ? stateFailure.orElseThrow()
                : applyToLockedSanction(connection, request, limits, row);
    }

    private Optional<ExactSanctionChangeResult> validateLockedSanction(
            Connection connection,
            ExactSanctionChangeRequest request,
            ExactSanctionRow row
    ) throws SQLException {
        if (row == null) {
            return Optional.of(rollbackAndReturn(
                    connection,
                    new ExactSanctionChangeResult.Rejected(
                            "SANCTION_NOT_FOUND",
                            "The sanction does not exist"
                    )
            ));
        }
        if (row.revision() != request.expectedRevision()) {
            connection.rollback();
            ExactSanctionChangeResult.Applied committed = replayAfterConflict(
                    request.idempotencyKey().value()
            );
            return Optional.of(committed == null
                    ? new ExactSanctionChangeResult.Rejected(
                            "STALE_SANCTION_STATE",
                            "The sanction changed after command validation; review its current state and retry"
                    )
                    : committed);
        }
        if (!StaffHierarchy.mayMutate(
                request.actor().rank(),
                row.issuerRank(),
                request.bypassHierarchy()
        )) {
            return Optional.of(rollbackAndReturn(
                    connection,
                    new ExactSanctionChangeResult.Rejected(
                            "HIERARCHY_DENIED",
                            "The sanction was issued outside the actor's mutation hierarchy"
                    )
            ));
        }
        return Optional.empty();
    }

    private ExactSanctionChangeResult applyToLockedSanction(
            Connection connection,
            ExactSanctionChangeRequest request,
            SanctionActionLimits limits,
            ExactSanctionRow row
    ) throws SQLException, JsonProcessingException {
        Optional<ExactSanctionChangeResult> linkFailure =
                ExactSanctionLinkValidator.validate(connection, request, row);
        if (linkFailure.isPresent()) {
            return rollbackAndReturn(connection, linkFailure.orElseThrow());
        }
        Instant now = clock.instant();
        ExactSanctionMutationDecision decision =
                ExactSanctionMutationPlanner.calculate(request, limits, row, now);
        return resolveDecision(connection, request, row, decision, now);
    }

    private ExactSanctionChangeResult resolveDecision(
            Connection connection,
            ExactSanctionChangeRequest request,
            ExactSanctionRow row,
            ExactSanctionMutationDecision decision,
            Instant now
    ) throws SQLException, JsonProcessingException {
        if (decision instanceof NoChange noChange) {
            return rollbackAndReturn(connection, noChange.result());
        }
        if (decision instanceof Rejected rejected) {
            return rollbackAndReturn(connection, rejected.result());
        }
        if (decision instanceof Apply mutation) {
            persistMutation(connection, request, row, mutation, now);
            connection.commit();
            return applied(request, row, mutation, now, false);
        }
        throw new IllegalStateException("Unsupported exact sanction mutation decision");
    }

    private void persistMutation(
            Connection connection,
            ExactSanctionChangeRequest request,
            ExactSanctionRow row,
            ExactSanctionMutationDecision.Apply mutation,
            Instant now
    ) throws SQLException, JsonProcessingException {
        updateSanction(connection, row, mutation);
        if (request.action() == SanctionChangeAction.FULL_OVERTURN) {
            updateCaseOverturnState(connection, row.caseId());
        }
        eventWriter.write(connection, request, row, mutation, now);
    }

    private static ExactSanctionChangeResult rollbackAndReturn(
            Connection connection,
            ExactSanctionChangeResult result
    ) throws SQLException {
        connection.rollback();
        return result;
    }

    private static void updateSanction(
            Connection connection,
            ExactSanctionRow row,
            ExactSanctionMutationDecision.Apply mutation
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE sanctions
                SET status = ?, expiration_at = ?, ended_at = ?, revision = revision + 1
                WHERE sanction_id = ? AND revision = ?
                """)) {
            statement.setString(1, mutation.resultingStatus().name());
            setInstant(statement, 2, mutation.resultingExpiration());
            setInstant(statement, 3, mutation.resultingEndedAt());
            statement.setBytes(4, UuidBytes.toBytes(row.sanctionId()));
            statement.setLong(5, row.revision());
            if (statement.executeUpdate() != EXPECTED_UPDATED_ROWS) {
                throw new SQLException("locked sanction revision changed unexpectedly");
            }
        }
    }

    private static void updateCaseOverturnState(Connection connection, CaseId caseId) throws SQLException {
        int remaining = 0;
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT sanction_id, status
                FROM sanctions
                WHERE case_id = ?
                ORDER BY sanction_id
                FOR UPDATE
                """)) {
            statement.setString(1, caseId.value());
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    if (!OVERTURNED_STATUS.equals(result.getString("status"))) {
                        remaining++;
                    }
                }
            }
        }
        if (remaining != 0) {
            return;
        }
        try (PreparedStatement cases = connection.prepareStatement("""
                UPDATE cases
                SET state = 'FULLY_OVERTURNED', revision = revision + 1
                WHERE case_id = ? AND state <> 'FULLY_OVERTURNED'
                """);
             PreparedStatement step = connection.prepareStatement("""
                UPDATE punishment_steps
                SET escalation_contributes = FALSE
                WHERE case_id = ? AND escalation_contributes = TRUE
                """)) {
            cases.setString(1, caseId.value());
            cases.executeUpdate();
            step.setString(1, caseId.value());
            step.executeUpdate();
        }
    }

    private static ExactSanctionRow lockSanction(Connection connection, UUID sanctionId) throws SQLException {
        String caseId;
        try (PreparedStatement lookup = connection.prepareStatement(
                "SELECT case_id FROM sanctions WHERE sanction_id = ?"
        )) {
            lookup.setBytes(1, UuidBytes.toBytes(sanctionId));
            try (ResultSet result = lookup.executeQuery()) {
                if (!result.next()) {
                    return null;
                }
                caseId = result.getString(CASE_ID_COLUMN);
            }
        }
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT s.sanction_id, s.case_id, s.target_id, s.status, s.issued_at,
                    s.expiration_at, s.ended_at, s.revision, c.actor_rank
                FROM sanctions s
                JOIN cases c ON c.case_id = s.case_id
                WHERE s.case_id = ?
                ORDER BY s.sanction_id
                FOR UPDATE
                """)) {
            statement.setString(1, caseId);
            try (ResultSet result = statement.executeQuery()) {
                ExactSanctionRow target = null;
                while (result.next()) {
                    UUID currentId = UuidBytes.fromBytes(result.getBytes("sanction_id"));
                    if (currentId.equals(sanctionId)) {
                        target = readSanctionRow(result, currentId);
                    }
                }
                return target;
            }
        }
    }

    private static ExactSanctionRow readSanctionRow(ResultSet result, UUID sanctionId) throws SQLException {
        return new ExactSanctionRow(
                sanctionId,
                new CaseId(result.getString(CASE_ID_COLUMN)),
                UuidBytes.fromBytes(result.getBytes("target_id")),
                SanctionStatus.valueOf(result.getString("status")),
                result.getTimestamp("issued_at").toInstant(),
                optionalInstant(result, "expiration_at"),
                optionalInstant(result, "ended_at"),
                result.getLong("revision"),
                issuerRank(result.getString("actor_rank"))
        );
    }

    private static StaffRank issuerRank(String stored) {
        if (stored == null || stored.isBlank()) {
            return StaffRank.SYSTEM;
        }
        if (stored.equalsIgnoreCase("OWNER")) {
            return StaffRank.FOUNDER;
        }
        try {
            return StaffRank.valueOf(stored.toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return StaffRank.SYSTEM;
        }
    }

    private ExactSanctionChangeResult.Applied replay(Connection connection, String key)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT event.case_id, event.sanction_id, event.subject_id, event.event_type,
                    event.previous_status, event.resulting_status, event.previous_expiration,
                    event.resulting_expiration, event.occurred_at, event.linked_appeal_id,
                    event.linked_punishment_request_id
                FROM sanction_events event
                WHERE event.idempotency_key = ?
                """)) {
            statement.setString(1, key);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    return null;
                }
                return new ExactSanctionChangeResult.Applied(
                        new CaseId(result.getString(CASE_ID_COLUMN)),
                        UuidBytes.fromBytes(result.getBytes("sanction_id")),
                        UuidBytes.fromBytes(result.getBytes("subject_id")),
                        SanctionChangeAction.valueOf(result.getString("event_type")),
                        SanctionStatus.valueOf(result.getString("previous_status")),
                        SanctionStatus.valueOf(result.getString("resulting_status")),
                        optionalInstant(result, "previous_expiration"),
                        optionalInstant(result, "resulting_expiration"),
                        result.getTimestamp("occurred_at").toInstant(),
                        optionalUuid(result, "linked_appeal_id"),
                        optionalUuid(result, "linked_punishment_request_id"),
                        true
                );
            }
        }
    }

    private ExactSanctionChangeResult.Applied replayAfterConflict(String key) {
        try (Connection connection = dataSource.getConnection()) {
            return replay(connection, key);
        } catch (SQLException exception) {
            return null;
        }
    }

    private static ExactSanctionChangeResult.Applied applied(
            ExactSanctionChangeRequest request,
            ExactSanctionRow row,
            ExactSanctionMutationDecision.Apply mutation,
            Instant now,
            boolean replayed
    ) {
        return new ExactSanctionChangeResult.Applied(
                row.caseId(),
                row.sanctionId(),
                row.subjectId(),
                request.action(),
                row.status(),
                mutation.resultingStatus(),
                row.expiration(),
                mutation.resultingExpiration(),
                now,
                request.linkedAppealId(),
                request.linkedPunishmentRequestId(),
                replayed
        );
    }

    private static Optional<Instant> optionalInstant(ResultSet result, String column) throws SQLException {
        Timestamp value = result.getTimestamp(column);
        return value == null ? Optional.empty() : Optional.of(value.toInstant());
    }

    private static Optional<UUID> optionalUuid(ResultSet result, String column) throws SQLException {
        byte[] value = result.getBytes(column);
        return value == null ? Optional.empty() : Optional.of(UuidBytes.fromBytes(value));
    }

    private static void setInstant(
            PreparedStatement statement,
            int index,
            Optional<Instant> value
    ) throws SQLException {
        if (value.isPresent()) {
            statement.setTimestamp(index, Timestamp.from(value.orElseThrow()));
        } else {
            statement.setNull(index, java.sql.Types.TIMESTAMP);
        }
    }

    private static void rollback(Connection connection, Exception original) {
        try {
            connection.rollback();
        } catch (SQLException rollbackFailure) {
            original.addSuppressed(rollbackFailure);
        }
    }

    private static void restoreAutoCommit(Connection connection) {
        try {
            connection.setAutoCommit(true);
        } catch (SQLException ignored) {
            // Closing returns the connection to the pool; the original failure remains authoritative.
        }
    }

}
