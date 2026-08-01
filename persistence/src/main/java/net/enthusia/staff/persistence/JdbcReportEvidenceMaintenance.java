package net.enthusia.staff.persistence;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;

final class JdbcReportEvidenceMaintenance {
    private static final Duration CLIENT_EVIDENCE_RETENTION = Duration.ofDays(7);
    private static final int MAX_BATCH_LIMIT = 1_000;
    private static final String PUBLIC_CHAT_PURGE_SQL = """
            DELETE FROM report_chat_snapshots WHERE expires_at <= ? ORDER BY expires_at LIMIT ?
            """;
    private static final String PRIVATE_MESSAGE_PURGE_SQL = """
            DELETE FROM report_private_message_snapshots WHERE expires_at <= ? ORDER BY expires_at LIMIT ?
            """;

    private final DataSource dataSource;

    JdbcReportEvidenceMaintenance(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    int purgeExpired(Instant now, int batchLimit) {
        validateRequest(now, batchLimit);
        return JdbcTransactionSupport.execute(
                dataSource,
                "Unable to purge expired report evidence",
                connection -> purgeInTransaction(connection, now, batchLimit)
        );
    }

    private static int purgeInTransaction(
            Connection connection,
            Instant now,
            int batchLimit
    ) throws SQLException {
        int publicChat = purgeExpiringTable(connection, PUBLIC_CHAT_PURGE_SQL, now, batchLimit);
        int privateMessages = purgeExpiringTable(
                connection,
                PRIVATE_MESSAGE_PURGE_SQL,
                now,
                batchLimit
        );
        int clientEvidence = purgeClientEvidence(connection, now, batchLimit);
        return publicChat + privateMessages + clientEvidence;
    }

    private static int purgeExpiringTable(
            Connection connection,
            String sql,
            Instant now,
            int batchLimit
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setTimestamp(1, Timestamp.from(now));
            statement.setInt(2, batchLimit);
            return statement.executeUpdate();
        }
    }

    private static int purgeClientEvidence(
            Connection connection,
            Instant now,
            int batchLimit
    ) throws SQLException {
        List<EvidenceLink> expired = lockExpiredClientEvidence(
                connection,
                now.minus(CLIENT_EVIDENCE_RETENTION),
                batchLimit
        );
        if (expired.isEmpty()) {
            return 0;
        }
        deleteEvidenceLinks(connection, expired);
        deleteOrphanedEvidence(connection, expired);
        return expired.size();
    }

    private static List<EvidenceLink> lockExpiredClientEvidence(
            Connection connection,
            Instant cutoff,
            int batchLimit
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT report_id, snapshot_id FROM report_client_evidence_snapshots
                WHERE captured_at <= ? ORDER BY captured_at LIMIT ? FOR UPDATE
                """)) {
            statement.setTimestamp(1, Timestamp.from(cutoff));
            statement.setInt(2, batchLimit);
            try (ResultSet result = statement.executeQuery()) {
                List<EvidenceLink> links = new ArrayList<>();
                while (result.next()) {
                    links.add(new EvidenceLink(
                            UuidBytes.fromBytes(result.getBytes("report_id")),
                            UuidBytes.fromBytes(result.getBytes("snapshot_id"))
                    ));
                }
                return List.copyOf(links);
            }
        }
    }

    private static void deleteEvidenceLinks(Connection connection, List<EvidenceLink> links) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                DELETE FROM report_client_evidence_snapshots WHERE report_id = ? AND snapshot_id = ?
                """)) {
            for (EvidenceLink link : links) {
                statement.setBytes(1, UuidBytes.toBytes(link.reportId()));
                statement.setBytes(2, UuidBytes.toBytes(link.snapshotId()));
                statement.addBatch();
            }
            JdbcTransactionSupport.requireBatchUpdate(
                    statement.executeBatch(),
                    links.size(),
                    "expired report evidence links changed during purge"
            );
        }
    }

    private static void deleteOrphanedEvidence(Connection connection, List<EvidenceLink> links) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                DELETE FROM client_evidence_snapshots
                WHERE snapshot_id = ? AND NOT EXISTS (
                    SELECT 1 FROM report_client_evidence_snapshots WHERE snapshot_id = ?
                )
                """)) {
            for (EvidenceLink link : links) {
                byte[] snapshotId = UuidBytes.toBytes(link.snapshotId());
                statement.setBytes(1, snapshotId);
                statement.setBytes(2, snapshotId);
                statement.addBatch();
            }
            JdbcTransactionSupport.requireIdempotentBatchUpdate(
                    statement.executeBatch(),
                    links.size(),
                    "expired report evidence cleanup returned an invalid update count"
            );
        }
    }

    private static void validateRequest(Instant now, int batchLimit) {
        if (now == null || batchLimit < 1 || batchLimit > MAX_BATCH_LIMIT) {
            throw new IllegalArgumentException("a current time and batch limit from 1 to 1000 are required");
        }
    }

    private record EvidenceLink(UUID reportId, UUID snapshotId) {
    }
}
