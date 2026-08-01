package net.enthusia.staff.persistence;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.enthusia.staff.common.CaseId;

final class JdbcWebsiteAppealRepository {
    private static final int DUPLICATE_KEY_ERROR = 1_062;
    private static final String INTEGRITY_SQL_STATE = "23000";

    List<AppealRow> selectCandidates(
            Connection connection,
            UUID appealId,
            UUID punishmentId,
            String idempotencyKey,
            boolean lock
    ) throws SQLException {
        String sql = """
                SELECT appeal_id, punishment_id, case_id, player_account_token,
                       idempotency_key, state, outcome_code
                FROM website_appeal_requests
                WHERE appeal_id = ? OR punishment_id = ? OR idempotency_key = ?
                """ + lockClause(lock);
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setBytes(1, UuidBytes.toBytes(appealId));
            statement.setBytes(2, UuidBytes.toBytes(punishmentId));
            statement.setString(3, idempotencyKey);
            try (ResultSet result = statement.executeQuery()) {
                List<AppealRow> candidates = new ArrayList<>();
                while (result.next()) {
                    candidates.add(readAppeal(result));
                }
                return candidates;
            }
        }
    }

    AppealRow selectById(Connection connection, UUID appealId, boolean lock) throws SQLException {
        String sql = """
                SELECT appeal_id, punishment_id, case_id, player_account_token,
                       idempotency_key, state, outcome_code
                FROM website_appeal_requests
                WHERE appeal_id = ?
                """ + lockClause(lock);
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setBytes(1, UuidBytes.toBytes(appealId));
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? readAppeal(result) : null;
            }
        }
    }

    void insert(
            Connection connection,
            UUID appealId,
            UUID punishmentId,
            CaseId caseId,
            byte[] accountToken,
            String idempotencyKey,
            Instant now
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO website_appeal_requests(
                    appeal_id, punishment_id, case_id, player_account_token,
                    idempotency_key, state, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, 'PREPARED', ?, ?)
                """)) {
            statement.setBytes(1, UuidBytes.toBytes(appealId));
            statement.setBytes(2, UuidBytes.toBytes(punishmentId));
            statement.setString(3, caseId.value());
            statement.setBytes(4, accountToken);
            statement.setString(5, idempotencyKey);
            statement.setTimestamp(6, Timestamp.from(now));
            statement.setTimestamp(7, Timestamp.from(now));
            JdbcTransactionSupport.requireSingleUpdate(
                    statement.executeUpdate(),
                    "Appeal preparation was not inserted"
            );
        }
    }

    void complete(
            Connection connection,
            UUID appealId,
            String state,
            String outcomeCode,
            Instant now
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE website_appeal_requests
                SET state = ?, outcome_code = ?, updated_at = ?
                WHERE appeal_id = ? AND state = 'PREPARED'
                """)) {
            statement.setString(1, state);
            statement.setString(2, outcomeCode);
            statement.setTimestamp(3, Timestamp.from(now));
            statement.setBytes(4, UuidBytes.toBytes(appealId));
            JdbcTransactionSupport.requireSingleUpdate(
                    statement.executeUpdate(),
                    "Appeal state changed during completion"
            );
        }
    }

    static boolean isDuplicateKey(SQLException exception) {
        return exception.getErrorCode() == DUPLICATE_KEY_ERROR
                && INTEGRITY_SQL_STATE.equals(exception.getSQLState());
    }

    private static AppealRow readAppeal(ResultSet result) throws SQLException {
        return new AppealRow(
                UuidBytes.fromBytes(result.getBytes("appeal_id")),
                UuidBytes.fromBytes(result.getBytes("punishment_id")),
                new CaseId(result.getString("case_id")),
                result.getBytes("player_account_token"),
                result.getString("idempotency_key"),
                result.getString("state"),
                result.getString("outcome_code")
        );
    }

    private static String lockClause(boolean lock) {
        return lock ? " FOR UPDATE" : "";
    }

    record AppealRow(
            UUID appealId,
            UUID punishmentId,
            CaseId caseId,
            byte[] accountToken,
            String idempotencyKey,
            String state,
            String outcomeCode
    ) {
    }
}
