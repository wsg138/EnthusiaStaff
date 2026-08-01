package net.enthusia.staff.persistence;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import net.enthusia.staff.common.CaseId;

final class JdbcPunishmentCodeRepository {
    private static final String CODE_ROW_SELECT = """
            SELECT pc.sanction_id, pc.case_id, pc.key_version, pc.generation,
                   pc.code_hash, pc.status AS code_status, pc.claimed_account_token,
                   s.target_id, s.sanction_type, s.status AS sanction_status,
                   s.expiration_at, c.state AS case_state, p.current_username
            FROM punishment_codes pc
            JOIN sanctions s ON s.sanction_id = pc.sanction_id
            JOIN cases c ON c.case_id = pc.case_id
            JOIN players p ON p.player_id = s.target_id
            """;

    CodeRow selectCodeByHash(
            Connection connection,
            int keyVersion,
            byte[] hash,
            boolean lock
    ) throws SQLException {
        String sql = CODE_ROW_SELECT + " WHERE pc.key_version = ? AND pc.code_hash = ?"
                + lockClause(lock);
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, keyVersion);
            statement.setBytes(2, hash);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? readCodeRow(result) : null;
            }
        }
    }

    CodeRow selectCodeBySanction(Connection connection, UUID punishmentId, boolean lock)
            throws SQLException {
        String sql = CODE_ROW_SELECT + " WHERE pc.sanction_id = ?" + lockClause(lock);
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setBytes(1, UuidBytes.toBytes(punishmentId));
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? readCodeRow(result) : null;
            }
        }
    }

    SanctionRow selectSanction(Connection connection, UUID punishmentId, boolean lock)
            throws SQLException {
        String sql = sanctionSelect() + " WHERE s.sanction_id = ?" + lockClause(lock);
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setBytes(1, UuidBytes.toBytes(punishmentId));
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? readSanction(result) : null;
            }
        }
    }

    List<SanctionRow> selectSanctionsForCase(Connection connection, CaseId caseId)
            throws SQLException {
        String sql = sanctionSelect() + """
                 WHERE s.case_id = ?
                 ORDER BY s.issued_at, s.sanction_id
                 FOR UPDATE
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, caseId.value());
            try (ResultSet result = statement.executeQuery()) {
                List<SanctionRow> sanctions = new ArrayList<>();
                while (result.next()) {
                    sanctions.add(readSanction(result));
                }
                return sanctions;
            }
        }
    }

    List<SanctionRow> selectEligibleWithoutCodes(
            Connection connection,
            Instant now,
            int limit
    ) throws SQLException {
        String sql = sanctionSelect() + """
                 WHERE s.status = 'ACTIVE'
                   AND (s.expiration_at IS NULL OR s.expiration_at > ?)
                   AND s.sanction_type IN ('BAN', 'NETWORK_BAN', 'NETWORK_IDENTITY_BAN', 'MUTE')
                   AND c.state <> 'FULLY_OVERTURNED'
                   AND NOT EXISTS (
                       SELECT 1 FROM punishment_codes pc WHERE pc.sanction_id = s.sanction_id
                   )
                 ORDER BY s.issued_at, s.sanction_id
                 LIMIT ?
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setTimestamp(1, Timestamp.from(now));
            statement.setInt(2, limit);
            try (ResultSet result = statement.executeQuery()) {
                List<SanctionRow> sanctions = new ArrayList<>();
                while (result.next()) {
                    sanctions.add(readSanction(result));
                }
                return sanctions;
            }
        }
    }

    CodeRecord selectCodeRecord(Connection connection, UUID punishmentId, boolean lock)
            throws SQLException {
        String sql = """
                SELECT key_version, generation, code_hash, status
                FROM punishment_codes
                WHERE sanction_id = ?
                """ + lockClause(lock);
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setBytes(1, UuidBytes.toBytes(punishmentId));
            try (ResultSet result = statement.executeQuery()) {
                return result.next()
                        ? new CodeRecord(
                                result.getInt("key_version"),
                                result.getInt("generation"),
                                result.getBytes("code_hash"),
                                result.getString("status")
                        )
                        : null;
            }
        }
    }

    void insertCode(
            Connection connection,
            SanctionRow sanction,
            int keyVersion,
            int generation,
            byte[] hash,
            Instant now,
            UUID actorId
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO punishment_codes(
                    sanction_id, case_id, key_version, generation, code_hash, status,
                    created_at, rotated_at, rotated_by
                ) VALUES (?, ?, ?, ?, ?, 'ACTIVE', ?, ?, ?)
                """)) {
            bindCodeInsert(statement, sanction, keyVersion, generation, hash, now);
            if (actorId == null) {
                statement.setNull(7, Types.TIMESTAMP);
                statement.setNull(8, Types.BINARY);
            } else {
                statement.setTimestamp(7, Timestamp.from(now));
                statement.setBytes(8, UuidBytes.toBytes(actorId));
            }
            JdbcTransactionSupport.requireSingleUpdate(
                    statement.executeUpdate(),
                    "Punishment code was not inserted"
            );
        }
    }

    int insertCodeIfMissing(
            Connection connection,
            SanctionRow sanction,
            int keyVersion,
            byte[] hash,
            Instant now
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT IGNORE INTO punishment_codes(
                    sanction_id, case_id, key_version, generation, code_hash, status, created_at
                ) VALUES (?, ?, ?, 1, ?, 'ACTIVE', ?)
                """)) {
            statement.setBytes(1, UuidBytes.toBytes(sanction.sanctionId()));
            statement.setString(2, sanction.caseId().value());
            statement.setInt(3, keyVersion);
            statement.setBytes(4, hash);
            statement.setTimestamp(5, Timestamp.from(now));
            int changed = statement.executeUpdate();
            JdbcTransactionSupport.requireOptionalSingleUpdate(
                    changed,
                    "Punishment code backfill changed an unexpected number of rows"
            );
            return changed;
        }
    }

    int claimCode(
            Connection connection,
            UUID punishmentId,
            byte[] accountToken,
            Instant now
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE punishment_codes
                SET claimed_account_token = ?, claimed_at = ?
                WHERE sanction_id = ? AND claimed_account_token IS NULL
                """)) {
            statement.setBytes(1, accountToken);
            statement.setTimestamp(2, Timestamp.from(now));
            statement.setBytes(3, UuidBytes.toBytes(punishmentId));
            int changed = statement.executeUpdate();
            JdbcTransactionSupport.requireOptionalSingleUpdate(
                    changed,
                    "Punishment code binding changed during claim"
            );
            return changed;
        }
    }

    void rotateCode(
            Connection connection,
            UUID punishmentId,
            int keyVersion,
            int generation,
            byte[] hash,
            UUID actorId,
            Instant now
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE punishment_codes
                SET key_version = ?, generation = ?, code_hash = ?, status = 'ACTIVE',
                    claimed_account_token = NULL, claimed_at = NULL, rotated_at = ?,
                    rotated_by = ?, revoked_at = NULL, revoked_by = NULL
                WHERE sanction_id = ?
                """)) {
            statement.setInt(1, keyVersion);
            statement.setInt(2, generation);
            statement.setBytes(3, hash);
            statement.setTimestamp(4, Timestamp.from(now));
            statement.setBytes(5, UuidBytes.toBytes(actorId));
            statement.setBytes(6, UuidBytes.toBytes(punishmentId));
            JdbcTransactionSupport.requireSingleUpdate(
                    statement.executeUpdate(),
                    "Punishment code changed during rotation"
            );
        }
    }

    int revokeCode(
            Connection connection,
            UUID punishmentId,
            UUID actorId,
            Instant now
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE punishment_codes
                SET status = 'REVOKED', revoked_at = ?, revoked_by = ?
                WHERE sanction_id = ? AND status = 'ACTIVE'
                """)) {
            statement.setTimestamp(1, Timestamp.from(now));
            statement.setBytes(2, UuidBytes.toBytes(actorId));
            statement.setBytes(3, UuidBytes.toBytes(punishmentId));
            int changed = statement.executeUpdate();
            JdbcTransactionSupport.requireOptionalSingleUpdate(
                    changed,
                    "Punishment code revocation changed an unexpected number of rows"
            );
            return changed;
        }
    }

    boolean codeExists(Connection connection, UUID punishmentId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT 1 FROM punishment_codes WHERE sanction_id = ?")) {
            statement.setBytes(1, UuidBytes.toBytes(punishmentId));
            try (ResultSet result = statement.executeQuery()) {
                return result.next();
            }
        }
    }

    boolean usernameMatches(Connection connection, UUID targetId, String username)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT 1
                FROM players p
                WHERE p.player_id = ?
                  AND (
                      p.lowercase_username = ?
                      OR EXISTS (
                          SELECT 1 FROM player_names history
                          WHERE history.player_id = p.player_id
                            AND history.lowercase_username = ?
                      )
                  )
                """)) {
            String lower = username.toLowerCase(Locale.ROOT);
            statement.setBytes(1, UuidBytes.toBytes(targetId));
            statement.setString(2, lower);
            statement.setString(3, lower);
            try (ResultSet result = statement.executeQuery()) {
                return result.next();
            }
        }
    }

    private static void bindCodeInsert(
            PreparedStatement statement,
            SanctionRow sanction,
            int keyVersion,
            int generation,
            byte[] hash,
            Instant now
    ) throws SQLException {
        statement.setBytes(1, UuidBytes.toBytes(sanction.sanctionId()));
        statement.setString(2, sanction.caseId().value());
        statement.setInt(3, keyVersion);
        statement.setInt(4, generation);
        statement.setBytes(5, hash);
        statement.setTimestamp(6, Timestamp.from(now));
    }

    private static CodeRow readCodeRow(ResultSet result) throws SQLException {
        Timestamp expiration = result.getTimestamp("expiration_at");
        return new CodeRow(
                UuidBytes.fromBytes(result.getBytes("sanction_id")),
                new CaseId(result.getString("case_id")),
                result.getInt("key_version"),
                result.getInt("generation"),
                result.getBytes("code_hash"),
                result.getString("code_status"),
                result.getBytes("claimed_account_token"),
                UuidBytes.fromBytes(result.getBytes("target_id")),
                result.getString("sanction_type"),
                result.getString("sanction_status"),
                expiration == null ? null : expiration.toInstant(),
                result.getString("case_state"),
                result.getString("current_username")
        );
    }

    private static SanctionRow readSanction(ResultSet result) throws SQLException {
        Timestamp expiration = result.getTimestamp("expiration_at");
        return new SanctionRow(
                UuidBytes.fromBytes(result.getBytes("sanction_id")),
                new CaseId(result.getString("case_id")),
                UuidBytes.fromBytes(result.getBytes("target_id")),
                result.getString("sanction_type"),
                result.getString("sanction_status"),
                expiration == null ? null : expiration.toInstant(),
                result.getString("case_state")
        );
    }

    private static String sanctionSelect() {
        return """
                SELECT s.sanction_id, s.case_id, s.target_id, s.sanction_type,
                       s.status AS sanction_status, s.expiration_at, c.state AS case_state
                FROM sanctions s
                JOIN cases c ON c.case_id = s.case_id
                """;
    }

    private static String lockClause(boolean lock) {
        return lock ? " FOR UPDATE" : "";
    }

    record CodeRow(
            UUID sanctionId,
            CaseId caseId,
            int keyVersion,
            int generation,
            byte[] codeHash,
            String codeStatus,
            byte[] claimedAccountToken,
            UUID targetId,
            String sanctionType,
            String sanctionStatus,
            Instant expiration,
            String caseState,
            String currentUsername
    ) {
    }

    record SanctionRow(
            UUID sanctionId,
            CaseId caseId,
            UUID targetId,
            String sanctionType,
            String sanctionStatus,
            Instant expiration,
            String caseState
    ) {
    }

    record CodeRecord(int keyVersion, int generation, byte[] codeHash, String status) {
    }
}
