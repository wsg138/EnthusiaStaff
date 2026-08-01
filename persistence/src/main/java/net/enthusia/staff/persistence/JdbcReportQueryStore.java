package net.enthusia.staff.persistence;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;
import net.enthusia.staff.domain.report.ReportDetails;
import net.enthusia.staff.domain.report.ReportQueue;
import net.enthusia.staff.domain.report.ReportState;
import net.enthusia.staff.domain.report.ReportSummary;

final class JdbcReportQueryStore {
    private static final String ASSIGNED_TO = "assigned_to";
    private static final String REPORT_ID = "report_id";
    private static final String STATE = "state";
    private static final String REPORT_HEADER_SQL = """
            SELECT r.report_id, r.reporter_id, r.target_id, r.reason_id, r.state,
                   r.assigned_to, r.server_id, r.created_at, r.updated_at, r.revision,
                   r.description, r.world_id, r.reporter_coordinates, r.target_coordinates
            FROM reports r WHERE r.report_id = ?
            """;
    private static final String PUBLIC_CHAT_SQL = """
            SELECT messages_json FROM report_chat_snapshots
            WHERE report_id = ? AND expires_at > CURRENT_TIMESTAMP(6)
            ORDER BY captured_at
            """;
    private static final String PRIVATE_MESSAGES_SQL = """
            SELECT messages_json FROM report_private_message_snapshots
            WHERE report_id = ? AND expires_at > CURRENT_TIMESTAMP(6)
            ORDER BY captured_at
            """;
    private static final String CLIENT_EVIDENCE_SQL = """
            SELECT evidence.evidence_json
            FROM report_client_evidence_snapshots report_evidence
            JOIN client_evidence_snapshots evidence
              ON evidence.snapshot_id = report_evidence.snapshot_id
            WHERE report_evidence.report_id = ?
            ORDER BY report_evidence.captured_at
            """;

    private final DataSource dataSource;

    JdbcReportQueryStore(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    List<ReportSummary> list(ReportQueue queue, UUID actorId, int limit) {
        validateListRequest(queue, actorId, limit);
        QueueQuery query = queueQuery(queue);
        String sql = """
                SELECT r.report_id, r.reporter_id, r.target_id, r.reason_id, r.state,
                       r.assigned_to, r.server_id, r.created_at, r.updated_at, r.revision
                FROM reports r WHERE %s
                ORDER BY r.updated_at DESC LIMIT ?
                """.formatted(query.condition());
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            bindListRequest(statement, query, actorId, limit);
            return readSummaries(statement);
        } catch (SQLException | IllegalArgumentException exception) {
            throw new ModerationPersistenceException("Unable to list staff reports", exception);
        }
    }

    Optional<ReportDetails> details(UUID reportId) {
        if (reportId == null) {
            throw new IllegalArgumentException("reportId must be present");
        }
        try (Connection connection = dataSource.getConnection()) {
            ReportHeader header = reportHeader(connection, reportId);
            if (header == null) {
                return Optional.empty();
            }
            return Optional.of(new ReportDetails(
                    header.summary(),
                    header.description(),
                    header.worldId(),
                    header.reporterCoordinates(),
                    header.targetCoordinates(),
                    publicChatSnapshots(connection, reportId),
                    privateMessageSnapshots(connection, reportId),
                    clientEvidenceSnapshots(connection, reportId)
            ));
        } catch (SQLException | IllegalArgumentException exception) {
            throw new ModerationPersistenceException("Unable to read report details", exception);
        }
    }

    private static void validateListRequest(ReportQueue queue, UUID actorId, int limit) {
        if (queue == null || actorId == null || limit < 1 || limit > 100) {
            throw new IllegalArgumentException("valid report queue, actor, and bounded limit are required");
        }
    }

    private static QueueQuery queueQuery(ReportQueue queue) {
        return switch (queue) {
            case OPEN -> new QueueQuery("r.state = 'OPEN'", false);
            case CLAIMED_BY_ME -> new QueueQuery("r.state = 'CLAIMED' AND r.assigned_to = ?", true);
            case ALL_CLAIMED -> new QueueQuery("r.state = 'CLAIMED'", false);
            case AWAITING_REVIEW -> new QueueQuery("r.state = 'AWAITING_REVIEW'", false);
            case RECENTLY_CLOSED -> new QueueQuery(
                    "r.state IN ('CLOSED', 'NO_VIOLATION') "
                            + "AND r.updated_at >= CURRENT_TIMESTAMP(6) - INTERVAL 7 DAY",
                    false
            );
        };
    }

    private static void bindListRequest(
            PreparedStatement statement,
            QueueQuery query,
            UUID actorId,
            int limit
    ) throws SQLException {
        int limitIndex = 1;
        if (query.bindActor()) {
            statement.setBytes(1, UuidBytes.toBytes(actorId));
            limitIndex = 2;
        }
        statement.setInt(limitIndex, limit);
    }

    private static List<ReportSummary> readSummaries(PreparedStatement statement) throws SQLException {
        try (ResultSet result = statement.executeQuery()) {
            List<ReportSummary> reports = new ArrayList<>();
            while (result.next()) {
                reports.add(readSummary(result));
            }
            return List.copyOf(reports);
        }
    }

    private static ReportHeader reportHeader(Connection connection, UUID reportId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(REPORT_HEADER_SQL)) {
            statement.setBytes(1, UuidBytes.toBytes(reportId));
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    return null;
                }
                return new ReportHeader(
                        readSummary(result),
                        result.getString("description"),
                        Optional.ofNullable(result.getString("world_id")),
                        Optional.ofNullable(result.getString("reporter_coordinates")),
                        Optional.ofNullable(result.getString("target_coordinates"))
                );
            }
        }
    }

    private static ReportSummary readSummary(ResultSet result) throws SQLException {
        byte[] assigned = result.getBytes(ASSIGNED_TO);
        return new ReportSummary(
                UuidBytes.fromBytes(result.getBytes(REPORT_ID)),
                UuidBytes.fromBytes(result.getBytes("reporter_id")),
                UuidBytes.fromBytes(result.getBytes("target_id")),
                result.getString("reason_id"),
                ReportState.valueOf(result.getString(STATE)),
                assigned == null ? Optional.empty() : Optional.of(UuidBytes.fromBytes(assigned)),
                result.getString("server_id"),
                result.getTimestamp("created_at").toInstant(),
                result.getTimestamp("updated_at").toInstant(),
                result.getLong("revision")
        );
    }

    private static List<String> publicChatSnapshots(Connection connection, UUID reportId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(PUBLIC_CHAT_SQL)) {
            return readSnapshots(statement, reportId);
        }
    }

    private static List<String> privateMessageSnapshots(Connection connection, UUID reportId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(PRIVATE_MESSAGES_SQL)) {
            return readSnapshots(statement, reportId);
        }
    }

    private static List<String> clientEvidenceSnapshots(Connection connection, UUID reportId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(CLIENT_EVIDENCE_SQL)) {
            return readSnapshots(statement, reportId);
        }
    }

    private static List<String> readSnapshots(PreparedStatement statement, UUID reportId) throws SQLException {
        statement.setBytes(1, UuidBytes.toBytes(reportId));
        try (ResultSet result = statement.executeQuery()) {
            List<String> snapshots = new ArrayList<>();
            while (result.next()) {
                snapshots.add(result.getString(1));
            }
            return List.copyOf(snapshots);
        }
    }

    private record QueueQuery(String condition, boolean bindActor) {
    }

    private record ReportHeader(
            ReportSummary summary,
            String description,
            Optional<String> worldId,
            Optional<String> reporterCoordinates,
            Optional<String> targetCoordinates
    ) {
    }
}
