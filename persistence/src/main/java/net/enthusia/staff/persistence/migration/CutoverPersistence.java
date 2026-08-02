package net.enthusia.staff.persistence.migration;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.UUID;
import net.enthusia.staff.domain.OperationalMode;
import net.enthusia.staff.domain.migration.CutoverAssessment;
import net.enthusia.staff.domain.runtime.OperationalStateSnapshot;
import net.enthusia.staff.persistence.UuidBytes;

final class CutoverPersistence {
    OperationalStateSnapshot readOperationalState(Connection connection, boolean forUpdate)
            throws SQLException {
        String sql = forUpdate ? """
                SELECT mode, revision, reason, updated_at
                FROM operational_state WHERE singleton_id = 1 FOR UPDATE
                """ : """
                SELECT mode, revision, reason, updated_at
                FROM operational_state WHERE singleton_id = 1
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet result = statement.executeQuery()) {
            if (!result.next()) {
                throw new SQLException("operational state singleton missing");
            }
            return new OperationalStateSnapshot(
                    OperationalMode.valueOf(result.getString("mode")),
                    result.getLong("revision"),
                    result.getString("reason"),
                    result.getTimestamp("updated_at").toInstant()
            );
        } catch (IllegalArgumentException exception) {
            throw new SQLException("unknown persisted operational mode", exception);
        }
    }

    void transitionOperationalState(
            Connection connection,
            OperationalStateSnapshot current,
            OperationalMode expected,
            OperationalMode next,
            UUID actorId,
            String reason,
            Instant changedAt
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE operational_state
                SET mode = ?, revision = revision + 1, reason = ?, updated_by = ?, updated_at = ?
                WHERE singleton_id = 1 AND revision = ? AND mode = ?
                """)) {
            statement.setString(1, next.name());
            statement.setString(2, reason);
            statement.setBytes(3, UuidBytes.toBytes(actorId));
            statement.setTimestamp(4, Timestamp.from(changedAt));
            statement.setLong(5, current.revision());
            statement.setString(6, expected.name());
            MigrationTransactionSupport.requireSingleUpdate(
                    statement.executeUpdate(),
                    "operational mode changed during migration transition"
            );
        }
    }

    void insertCutoverRecord(
            Connection connection,
            UUID cutoverId,
            CutoverEvidenceBundle bundle,
            CutoverAssessment assessment,
            UUID actorId,
            Instant authorizedAt,
            String assessmentJson,
            String blockersJson
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO cutover_records(cutover_id, migration_run_id, assessment_json,
                    blockers_json, founder_override_used, authorized_by, authorized_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """)) {
            statement.setBytes(1, UuidBytes.toBytes(cutoverId));
            if (bundle.finalRunId().isPresent()) {
                statement.setBytes(2, UuidBytes.toBytes(bundle.finalRunId().orElseThrow()));
            } else {
                statement.setNull(2, Types.BINARY);
            }
            statement.setString(3, assessmentJson);
            statement.setString(4, blockersJson);
            statement.setBoolean(5, assessment.founderOverrideUsed());
            statement.setBytes(6, UuidBytes.toBytes(actorId));
            statement.setTimestamp(7, Timestamp.from(authorizedAt));
            MigrationTransactionSupport.requireSingleUpdate(
                    statement.executeUpdate(),
                    "cutover record insert did not affect exactly one row"
            );
        }
    }
}
