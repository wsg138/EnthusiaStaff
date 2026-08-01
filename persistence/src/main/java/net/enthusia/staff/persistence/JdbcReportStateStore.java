package net.enthusia.staff.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;
import net.enthusia.staff.domain.report.ReportState;
import net.enthusia.staff.domain.report.ReportStateChangeRequest;
import net.enthusia.staff.domain.report.ReportStateChangeResult;

final class JdbcReportStateStore {
    private static final String REPORT_ID_FIELD = "reportId";
    private static final String STATE_FIELD = "state";
    private static final String EXISTING_STATE_CHANGE_SQL = """
            SELECT report_id, actor_id, event_type, note, to_state, resulting_revision
            FROM report_events WHERE idempotency_key = ?
            """;
    private static final String LOCKED_EXISTING_STATE_CHANGE_SQL =
            EXISTING_STATE_CHANGE_SQL + " FOR UPDATE";

    private final DataSource dataSource;
    private final ObjectMapper json;

    JdbcReportStateStore(DataSource dataSource, ObjectMapper json) {
        this.dataSource = dataSource;
        this.json = json;
    }

    ReportStateChangeResult changeState(ReportStateChangeRequest request) {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                return changeInTransaction(connection, request);
            } catch (SQLException | JsonProcessingException | RuntimeException exception) {
                rollback(connection, exception);
                ReportStateChangeResult replay = existingStateChangeAfterConflict(request);
                if (replay != null) {
                    return replay;
                }
                throw new ModerationPersistenceException("Report state transaction failed", exception);
            } catch (Error error) {
                rollback(connection, error);
                throw error;
            } finally {
                restoreAutoCommit(connection);
            }
        } catch (SQLException exception) {
            throw new ModerationPersistenceException("Unable to open report state transaction", exception);
        }
    }

    private ReportStateChangeResult changeInTransaction(
            Connection connection,
            ReportStateChangeRequest request
    ) throws SQLException, JsonProcessingException {
        ReportStateChangeResult replay = existingStateChange(connection, request, false);
        if (replay != null) {
            return rollbackAndReturn(connection, replay);
        }
        LockedReport report = lockReport(connection, request.reportId());
        if (report == null) {
            return rollbackAndReturn(connection, new ReportStateChangeResult.Rejected(
                    "REPORT_NOT_FOUND", "The report does not exist"
            ));
        }
        replay = existingStateChange(connection, request, true);
        if (replay != null) {
            return rollbackAndReturn(connection, replay);
        }
        if (report.revision() != request.expectedRevision()) {
            return rollbackAndReturn(connection, new ReportStateChangeResult.Rejected(
                    "STALE_REVISION", "The report changed; reopen it before taking action"
            ));
        }
        Transition transition = transition(report, request);
        if (transition.rejection() != null) {
            return rollbackAndReturn(connection, transition.rejection());
        }
        return persistChange(connection, request, report, transition.nextState());
    }

    private ReportStateChangeResult persistChange(
            Connection connection,
            ReportStateChangeRequest request,
            LockedReport report,
            ReportState nextState
    ) throws SQLException, JsonProcessingException {
        long nextRevision = report.revision() + 1;
        updateReport(connection, request, nextState, nextRevision);
        insertReportEvent(connection, request, report.state(), nextState, nextRevision);
        insertReportActionMessage(connection, request);
        insertReportActionAudit(connection, request, report.targetId(), nextState);
        insertReportActionDiscord(connection, request, report.targetId(), nextState);
        connection.commit();
        return new ReportStateChangeResult.Applied(nextState, nextRevision, false);
    }

    private static LockedReport lockReport(Connection connection, UUID reportId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT target_id, state, assigned_to, revision FROM reports
                WHERE report_id = ? FOR UPDATE
                """)) {
            statement.setBytes(1, UuidBytes.toBytes(reportId));
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    return null;
                }
                byte[] assigned = result.getBytes("assigned_to");
                return new LockedReport(
                        UuidBytes.fromBytes(result.getBytes("target_id")),
                        ReportState.valueOf(result.getString(STATE_FIELD)),
                        assigned == null ? Optional.empty() : Optional.of(UuidBytes.fromBytes(assigned)),
                        result.getLong("revision")
                );
            }
        }
    }

    private static Transition transition(LockedReport report, ReportStateChangeRequest request) {
        return switch (request.action()) {
            case CLAIM -> claim(report);
            case AWAIT_REVIEW -> awaitReview(report, request.actorId());
            case CLOSE -> resolve(report, ReportState.CLOSED, "Claim the report before closing it");
            case NO_VIOLATION -> resolve(
                    report,
                    ReportState.NO_VIOLATION,
                    "Claim the report before resolving it"
            );
        };
    }

    private static Transition claim(LockedReport report) {
        return report.state() == ReportState.OPEN
                ? Transition.to(ReportState.CLAIMED)
                : Transition.reject("INVALID_STATE", "Only open reports can be claimed");
    }

    private static Transition awaitReview(LockedReport report, UUID actorId) {
        boolean assignedToActor = report.assignedTo().filter(actorId::equals).isPresent();
        return report.state() == ReportState.CLAIMED && assignedToActor
                ? Transition.to(ReportState.AWAITING_REVIEW)
                : Transition.reject("NOT_ASSIGNEE", "Only the assigned staff member can request review");
    }

    private static Transition resolve(LockedReport report, ReportState nextState, String invalidMessage) {
        boolean resolvable = report.state() == ReportState.CLAIMED
                || report.state() == ReportState.AWAITING_REVIEW;
        return resolvable
                ? Transition.to(nextState)
                : Transition.reject("INVALID_STATE", invalidMessage);
    }

    private static void updateReport(
            Connection connection,
            ReportStateChangeRequest request,
            ReportState next,
            long nextRevision
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE reports SET state = ?, assigned_to = CASE WHEN ? = 'CLAIMED' THEN ? ELSE assigned_to END,
                    updated_at = ?, revision = ?
                WHERE report_id = ? AND revision = ?
                """)) {
            statement.setString(1, next.name());
            statement.setString(2, next.name());
            statement.setBytes(3, UuidBytes.toBytes(request.actorId()));
            statement.setTimestamp(4, Timestamp.from(request.changedAt()));
            statement.setLong(5, nextRevision);
            statement.setBytes(6, UuidBytes.toBytes(request.reportId()));
            statement.setLong(7, request.expectedRevision());
            JdbcTransactionSupport.requireSingleUpdate(
                    statement.executeUpdate(),
                    "report revision changed during locked update"
            );
        }
    }

    private static void insertReportEvent(
            Connection connection,
            ReportStateChangeRequest request,
            ReportState previous,
            ReportState next,
            long revision
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO report_events(event_id, report_id, actor_id, event_type, from_state,
                    to_state, note, idempotency_key, occurred_at, resulting_revision)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """)) {
            statement.setBytes(1, UuidBytes.toBytes(UUID.randomUUID()));
            statement.setBytes(2, UuidBytes.toBytes(request.reportId()));
            statement.setBytes(3, UuidBytes.toBytes(request.actorId()));
            statement.setString(4, request.action().name());
            statement.setString(5, previous.name());
            statement.setString(6, next.name());
            statement.setString(7, request.note().trim());
            statement.setString(8, request.idempotencyKey().value());
            statement.setTimestamp(9, Timestamp.from(request.changedAt()));
            statement.setLong(10, revision);
            statement.executeUpdate();
        }
    }

    private static void insertReportActionMessage(Connection connection, ReportStateChangeRequest request)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO report_messages(message_id, report_id, actor_id, body, created_at)
                VALUES (?, ?, ?, ?, ?)
                """)) {
            statement.setBytes(1, UuidBytes.toBytes(UUID.randomUUID()));
            statement.setBytes(2, UuidBytes.toBytes(request.reportId()));
            statement.setBytes(3, UuidBytes.toBytes(request.actorId()));
            statement.setString(4, request.action().name() + ": " + request.note().trim());
            statement.setTimestamp(5, Timestamp.from(request.changedAt()));
            statement.executeUpdate();
        }
    }

    private void insertReportActionAudit(
            Connection connection,
            ReportStateChangeRequest request,
            UUID targetId,
            ReportState next
    ) throws SQLException, JsonProcessingException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO audit_events(event_id, correlation_id, actor_id, target_id,
                    event_type, outcome, event_json, occurred_at, idempotency_key)
                VALUES (?, ?, ?, ?, 'REPORT_STATE_CHANGED', 'COMMITTED', ?, ?, ?)
                """)) {
            statement.setBytes(1, UuidBytes.toBytes(UUID.randomUUID()));
            statement.setBytes(2, UuidBytes.toBytes(request.reportId()));
            statement.setBytes(3, UuidBytes.toBytes(request.actorId()));
            statement.setBytes(4, UuidBytes.toBytes(targetId));
            statement.setString(5, json.writeValueAsString(Map.of(
                    REPORT_ID_FIELD, request.reportId().toString(), STATE_FIELD, next.name()
            )));
            statement.setTimestamp(6, Timestamp.from(request.changedAt()));
            statement.setString(7, request.idempotencyKey().value());
            statement.executeUpdate();
        }
    }

    private void insertReportActionDiscord(
            Connection connection,
            ReportStateChangeRequest request,
            UUID targetId,
            ReportState next
    ) throws SQLException, JsonProcessingException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO discord_outbox(message_id, idempotency_key, destination, event_type,
                    payload_json, available_at, created_at)
                VALUES (?, ?, 'reports', ?, ?, ?, ?)
                """)) {
            statement.setBytes(1, UuidBytes.toBytes(UUID.randomUUID()));
            statement.setString(2, request.idempotencyKey().value() + ":discord");
            statement.setString(3, "REPORT_" + next.name());
            statement.setString(4, json.writeValueAsString(Map.of(
                    REPORT_ID_FIELD, request.reportId().toString(),
                    "targetId", targetId.toString(),
                    "actorId", request.actorId().toString(),
                    STATE_FIELD, next.name()
            )));
            statement.setTimestamp(5, Timestamp.from(request.changedAt()));
            statement.setTimestamp(6, Timestamp.from(request.changedAt()));
            statement.executeUpdate();
        }
    }

    private static ReportStateChangeResult existingStateChange(
            Connection connection,
            ReportStateChangeRequest request,
            boolean lockLatest
    ) throws SQLException {
        String sql = lockLatest ? LOCKED_EXISTING_STATE_CHANGE_SQL : EXISTING_STATE_CHANGE_SQL;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, request.idempotencyKey().value());
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    return null;
                }
                if (!sameStateChange(result, request)) {
                    return new ReportStateChangeResult.Rejected(
                            "IDEMPOTENCY_CONFLICT",
                            "The idempotency key belongs to a different report action"
                    );
                }
                return new ReportStateChangeResult.Applied(
                        ReportState.valueOf(result.getString("to_state")),
                        result.getLong("resulting_revision"),
                        true
                );
            }
        }
    }

    private static boolean sameStateChange(ResultSet result, ReportStateChangeRequest request)
            throws SQLException {
        return UuidBytes.fromBytes(result.getBytes("report_id")).equals(request.reportId())
                && UuidBytes.fromBytes(result.getBytes("actor_id")).equals(request.actorId())
                && request.action().name().equals(result.getString("event_type"))
                && request.note().trim().equals(result.getString("note"));
    }

    private ReportStateChangeResult existingStateChangeAfterConflict(ReportStateChangeRequest request) {
        try (Connection connection = dataSource.getConnection()) {
            return existingStateChange(connection, request, false);
        } catch (SQLException | RuntimeException exception) {
            return null;
        }
    }

    private static <T> T rollbackAndReturn(Connection connection, T result) throws SQLException {
        connection.rollback();
        return result;
    }

    private static void rollback(Connection connection, Throwable original) {
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

    private record LockedReport(UUID targetId, ReportState state, Optional<UUID> assignedTo, long revision) {
    }

    private record Transition(ReportState nextState, ReportStateChangeResult.Rejected rejection) {
        private static Transition to(ReportState next) {
            return new Transition(next, null);
        }

        private static Transition reject(String code, String message) {
            return new Transition(null, new ReportStateChangeResult.Rejected(code, message));
        }
    }
}
