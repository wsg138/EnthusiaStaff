package net.enthusia.staff.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;
import net.enthusia.staff.domain.ports.ReportStore;
import net.enthusia.staff.domain.report.CreateReportRequest;
import net.enthusia.staff.domain.report.ReportDetails;
import net.enthusia.staff.domain.report.ReportQueue;
import net.enthusia.staff.domain.report.ReportStateChangeRequest;
import net.enthusia.staff.domain.report.ReportStateChangeResult;
import net.enthusia.staff.domain.report.ReportSubmissionResult;
import net.enthusia.staff.domain.report.ReportSummary;

public final class JdbcReportStore implements ReportStore {
    private static final Duration ANY_COOLDOWN = Duration.ofMinutes(2);
    private static final Duration TARGET_COOLDOWN = Duration.ofMinutes(30);
    private static final Duration DUPLICATE_WINDOW = Duration.ofHours(2);
    private static final Duration CONTEXT_RETENTION = Duration.ofDays(7);
    private static final int MAX_OPEN_REPORTS = 5;
    private final DataSource dataSource;
    private final ObjectMapper json;
    private final JdbcReportQueryStore queries;
    private final JdbcReportStateStore states;

    public JdbcReportStore(DataSource dataSource, ObjectMapper json) {
        this.dataSource = dataSource;
        this.json = json;
        this.queries = new JdbcReportQueryStore(dataSource);
        this.states = new JdbcReportStateStore(dataSource, json);
    }

    @Override
    public ReportSubmissionResult submit(CreateReportRequest request) {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                ExistingSubmission replay = existingByIdempotency(connection, request.idempotencyKey().value());
                if (replay != null) {
                    connection.rollback();
                    return new ReportSubmissionResult.Accepted(replay.reportId(), replay.merged(), true);
                }
                lockReporter(connection, request.reporterId());
                UUID duplicate = nearDuplicate(connection, request);
                if (duplicate != null) {
                    merge(connection, duplicate, request);
                    insertContextSnapshots(connection, duplicate, request);
                    insertSubmissionKey(connection, duplicate, request, true);
                    insertAudit(connection, duplicate, request, "REPORT_MERGED");
                    connection.commit();
                    return new ReportSubmissionResult.Accepted(duplicate, true, false);
                }
                String cooldown = cooldown(connection, request);
                if (cooldown != null) {
                    connection.rollback();
                    return new ReportSubmissionResult.Rejected("COOLDOWN", cooldown);
                }
                if (openReportCount(connection, request.reporterId()) >= MAX_OPEN_REPORTS) {
                    connection.rollback();
                    return new ReportSubmissionResult.Rejected(
                            "OPEN_REPORT_LIMIT", "Resolve an existing report before opening another"
                    );
                }
                UUID reportId = UUID.randomUUID();
                insertReport(connection, reportId, request);
                insertSubmissionKey(connection, reportId, request, false);
                insertContextSnapshots(connection, reportId, request);
                insertAudit(connection, reportId, request, "REPORT_CREATED");
                insertDiscordOutbox(connection, reportId, request);
                connection.commit();
                return new ReportSubmissionResult.Accepted(reportId, false, false);
            } catch (SQLException | JsonProcessingException exception) {
                rollback(connection, exception);
                ExistingSubmission replay = existingAfterConflict(request.idempotencyKey().value());
                if (replay != null) {
                    return new ReportSubmissionResult.Accepted(replay.reportId(), replay.merged(), true);
                }
                throw new ModerationPersistenceException("Report transaction failed", exception);
            } finally {
                restoreAutoCommit(connection);
            }
        } catch (SQLException exception) {
            throw new ModerationPersistenceException("Unable to open report transaction", exception);
        }
    }

    @Override
    public List<ReportSummary> list(ReportQueue queue, UUID actorId, int limit) {
        return queries.list(queue, actorId, limit);
    }

    @Override
    public Optional<ReportDetails> details(UUID reportId) {
        return queries.details(reportId);
    }

    @Override
    public ReportStateChangeResult changeState(ReportStateChangeRequest request) {
        return states.changeState(request);
    }

    private void insertChatContextSnapshot(
            Connection connection,
            UUID reportId,
            Instant capturedAt,
            List<?> messages
    ) throws SQLException, JsonProcessingException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO report_chat_snapshots
                    (snapshot_id, report_id, captured_at, expires_at, messages_json)
                VALUES (?, ?, ?, ?, ?)
                """)) {
            writeContextSnapshot(statement, reportId, capturedAt, messages);
        }
    }

    private void insertPrivateMessageContextSnapshot(
            Connection connection,
            UUID reportId,
            Instant capturedAt,
            List<?> messages
    ) throws SQLException, JsonProcessingException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO report_private_message_snapshots
                    (snapshot_id, report_id, captured_at, expires_at, messages_json)
                VALUES (?, ?, ?, ?, ?)
                """)) {
            writeContextSnapshot(statement, reportId, capturedAt, messages);
        }
    }

    private void writeContextSnapshot(
            PreparedStatement statement,
            UUID reportId,
            Instant capturedAt,
            List<?> messages
    ) throws SQLException, JsonProcessingException {
        statement.setBytes(1, UuidBytes.toBytes(UUID.randomUUID()));
        statement.setBytes(2, UuidBytes.toBytes(reportId));
        statement.setTimestamp(3, Timestamp.from(capturedAt));
        statement.setTimestamp(4, Timestamp.from(capturedAt.plus(CONTEXT_RETENTION)));
        statement.setString(5, json.writeValueAsString(messages));
        statement.executeUpdate();
    }

    private static void lockReporter(Connection connection, UUID reporterId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT revision FROM players WHERE player_id = ? FOR UPDATE")) {
            statement.setBytes(1, UuidBytes.toBytes(reporterId));
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    throw new SQLException("reporter is absent from the player directory");
                }
            }
        }
    }

    private static UUID nearDuplicate(Connection connection, CreateReportRequest request) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT report_id FROM reports
                WHERE reporter_id = ? AND target_id = ? AND reason_id = ?
                  AND state IN ('OPEN', 'CLAIMED', 'AWAITING_REVIEW') AND created_at >= ?
                ORDER BY created_at DESC LIMIT 1 FOR UPDATE
                """)) {
            statement.setBytes(1, UuidBytes.toBytes(request.reporterId()));
            statement.setBytes(2, UuidBytes.toBytes(request.targetId()));
            statement.setString(3, request.reasonId());
            statement.setTimestamp(4, Timestamp.from(request.createdAt().minus(DUPLICATE_WINDOW)));
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? UuidBytes.fromBytes(result.getBytes(1)) : null;
            }
        }
    }

    private static String cooldown(Connection connection, CreateReportRequest request) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT MAX(created_at) latest_any,
                       MAX(CASE WHEN target_id = ? THEN created_at END) latest_target
                FROM reports WHERE reporter_id = ?
                """)) {
            statement.setBytes(1, UuidBytes.toBytes(request.targetId()));
            statement.setBytes(2, UuidBytes.toBytes(request.reporterId()));
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                Timestamp any = result.getTimestamp("latest_any");
                Timestamp target = result.getTimestamp("latest_target");
                if (any != null && any.toInstant().isAfter(request.createdAt().minus(ANY_COOLDOWN))) {
                    return "Wait two minutes between reports";
                }
                if (target != null && target.toInstant().isAfter(request.createdAt().minus(TARGET_COOLDOWN))) {
                    return "Wait thirty minutes before reporting the same target again";
                }
                return null;
            }
        }
    }

    private static int openReportCount(Connection connection, UUID reporterId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT COUNT(*) FROM reports
                WHERE reporter_id = ? AND state IN ('OPEN', 'CLAIMED', 'AWAITING_REVIEW')
                """)) {
            statement.setBytes(1, UuidBytes.toBytes(reporterId));
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getInt(1);
            }
        }
    }

    private static void insertReport(Connection connection, UUID reportId, CreateReportRequest request)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO reports(report_id, idempotency_key, reporter_id, target_id, reason_id,
                    description, state, server_id, world_id, reporter_coordinates, target_coordinates,
                    created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, 'OPEN', ?, ?, ?, ?, ?, ?)
                """)) {
            statement.setBytes(1, UuidBytes.toBytes(reportId));
            statement.setString(2, request.idempotencyKey().value());
            statement.setBytes(3, UuidBytes.toBytes(request.reporterId()));
            statement.setBytes(4, UuidBytes.toBytes(request.targetId()));
            statement.setString(5, request.reasonId());
            statement.setString(6, request.description());
            statement.setString(7, request.serverId());
            optionalString(statement, 8, request.worldId().orElse(null));
            optionalString(statement, 9, request.reporterCoordinates().orElse(null));
            optionalString(statement, 10, request.targetCoordinates().orElse(null));
            statement.setTimestamp(11, Timestamp.from(request.createdAt()));
            statement.setTimestamp(12, Timestamp.from(request.createdAt()));
            statement.executeUpdate();
        }
    }

    private static void merge(Connection connection, UUID reportId, CreateReportRequest request) throws SQLException {
        try (PreparedStatement message = connection.prepareStatement("""
                INSERT INTO report_messages(message_id, report_id, actor_id, body, created_at)
                VALUES (?, ?, ?, ?, ?)
                """);
             PreparedStatement update = connection.prepareStatement("""
                UPDATE reports SET updated_at = ?, revision = revision + 1 WHERE report_id = ?
                """)) {
            message.setBytes(1, UuidBytes.toBytes(UUID.randomUUID()));
            message.setBytes(2, UuidBytes.toBytes(reportId));
            message.setBytes(3, UuidBytes.toBytes(request.reporterId()));
            message.setString(4, request.description());
            message.setTimestamp(5, Timestamp.from(request.createdAt()));
            message.executeUpdate();
            update.setTimestamp(1, Timestamp.from(request.createdAt()));
            update.setBytes(2, UuidBytes.toBytes(reportId));
            update.executeUpdate();
        }
    }

    private void insertContextSnapshots(
            Connection connection,
            UUID reportId,
            CreateReportRequest request
    ) throws SQLException, JsonProcessingException {
        insertChatContextSnapshot(
                connection,
                reportId,
                request.createdAt(),
                request.publicChatContext()
        );
        insertPrivateMessageContextSnapshot(
                connection,
                reportId,
                request.createdAt(),
                request.privateMessageContext()
        );
        if (request.targetClientEvidence().isPresent()) {
            net.enthusia.staff.domain.evidence.ClientEvidenceSnapshot snapshot =
                    request.targetClientEvidence().orElseThrow();
            UUID snapshotId = ClientEvidencePersistence.insert(
                    connection,
                    json,
                    snapshot
            );
            linkClientEvidence(connection, reportId, snapshotId, snapshot.capturedAt());
        }
    }

    private static void linkClientEvidence(
            Connection connection,
            UUID reportId,
            UUID snapshotId,
            Instant capturedAt
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO report_client_evidence_snapshots(report_id, snapshot_id, captured_at)
                VALUES (?, ?, ?)
                """)) {
            statement.setBytes(1, UuidBytes.toBytes(reportId));
            statement.setBytes(2, UuidBytes.toBytes(snapshotId));
            statement.setTimestamp(3, Timestamp.from(capturedAt));
            statement.executeUpdate();
        }
    }

    private void insertAudit(
            Connection connection,
            UUID reportId,
            CreateReportRequest request,
            String eventType
    ) throws SQLException, JsonProcessingException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO audit_events(event_id, correlation_id, actor_id, target_id,
                    event_type, outcome, event_json, occurred_at, idempotency_key)
                VALUES (?, ?, ?, ?, ?, 'COMMITTED', ?, ?, ?)
                """)) {
            statement.setBytes(1, UuidBytes.toBytes(UUID.randomUUID()));
            statement.setBytes(2, UuidBytes.toBytes(reportId));
            statement.setBytes(3, UuidBytes.toBytes(request.reporterId()));
            statement.setBytes(4, UuidBytes.toBytes(request.targetId()));
            statement.setString(5, eventType);
            statement.setString(6, json.writeValueAsString(Map.of(
                    "reportId", reportId.toString(), "reasonId", request.reasonId()
            )));
            statement.setTimestamp(7, Timestamp.from(request.createdAt()));
            statement.setString(8, request.idempotencyKey().value());
            statement.executeUpdate();
        }
    }

    private void insertDiscordOutbox(Connection connection, UUID reportId, CreateReportRequest request)
            throws SQLException, JsonProcessingException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO discord_outbox(message_id, idempotency_key, destination, event_type,
                    payload_json, available_at, created_at)
                VALUES (?, ?, 'reports', 'REPORT_CREATED', ?, ?, ?)
                """)) {
            statement.setBytes(1, UuidBytes.toBytes(UUID.randomUUID()));
            statement.setString(2, "report:" + reportId + ":discord");
            Map<String, Object> payload = new java.util.LinkedHashMap<>();
            payload.put("reportId", reportId.toString());
            payload.put("reporterId", request.reporterId().toString());
            payload.put("targetId", request.targetId().toString());
            payload.put("reasonId", request.reasonId());
            payload.put("serverId", request.serverId());
            payload.put("targetClientEvidence", request.targetClientEvidence()
                    .map(ClientEvidencePersistence::toJson)
                    .orElse(null));
            statement.setString(3, json.writeValueAsString(payload));
            statement.setTimestamp(4, Timestamp.from(request.createdAt()));
            statement.setTimestamp(5, Timestamp.from(request.createdAt()));
            statement.executeUpdate();
        }
    }

    private static ExistingSubmission existingByIdempotency(Connection connection, String key) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT report_id, merged FROM report_submission_keys WHERE idempotency_key = ?")) {
            statement.setString(1, key);
            try (ResultSet result = statement.executeQuery()) {
                return result.next()
                        ? new ExistingSubmission(UuidBytes.fromBytes(result.getBytes(1)), result.getBoolean(2))
                        : null;
            }
        }
    }

    private static void insertSubmissionKey(
            Connection connection,
            UUID reportId,
            CreateReportRequest request,
            boolean merged
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO report_submission_keys(idempotency_key, report_id, merged, created_at)
                VALUES (?, ?, ?, ?)
                """)) {
            statement.setString(1, request.idempotencyKey().value());
            statement.setBytes(2, UuidBytes.toBytes(reportId));
            statement.setBoolean(3, merged);
            statement.setTimestamp(4, Timestamp.from(request.createdAt()));
            statement.executeUpdate();
        }
    }

    private ExistingSubmission existingAfterConflict(String key) {
        try (Connection connection = dataSource.getConnection()) {
            return existingByIdempotency(connection, key);
        } catch (SQLException exception) {
            return null;
        }
    }

    private record ExistingSubmission(UUID reportId, boolean merged) {
    }

    private static void optionalString(PreparedStatement statement, int index, String value) throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.VARCHAR);
        } else {
            statement.setString(index, value);
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
