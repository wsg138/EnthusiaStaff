package net.enthusia.staff.persistence;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;
import net.enthusia.staff.domain.staff.StaffSessionInspection;
import net.enthusia.staff.domain.staff.StaffSessionInspectionException;
import net.enthusia.staff.domain.staff.StaffSessionInspectionException.Reason;

/** Separate read-only path; never invokes recovery or the ordinary inner-join lookup. */
final class JdbcStaffSessionInspection {
    private static final int MAX_BYTES = StaffSessionInspection.MAX_SNAPSHOT_BYTES;
    private static final int HEADER_PREFIX_BYTES = 74;
    private static final int ROW_LIMIT = 2;
    private static final int QUERY_SECONDS = 5;
    private JdbcStaffSessionInspection() {
    }

    static Optional<StaffSessionInspection> inspect(DataSource source, UUID staffId) {
        java.util.Objects.requireNonNull(staffId, "staffId");
        try (Connection connection = source.getConnection()) {
            connection.setReadOnly(true);
            connection.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
            connection.setAutoCommit(false);
            Optional<StaffSessionInspection> result;
            try {
                result = read(connection, staffId);
            } catch (SQLException | RuntimeException failure) {
                try {
                    connection.rollback();
                } catch (SQLException rollbackFailure) {
                    failure.addSuppressed(rollbackFailure);
                }
                throw failure;
            }
            connection.rollback();
            return result;
        } catch (SQLException exception) {
            throw new ModerationPersistenceException("Staff session inspection unavailable", exception);
        }
    }

    private static Optional<StaffSessionInspection> read(Connection connection, UUID staffId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT s.session_id, s.staff_id, s.server_id, s.state, s.vanish_active,
                       s.started_at, s.revision, p.session_id AS snapshot_session_id,
                       p.schema_version, p.checksum, OCTET_LENGTH(p.snapshot_blob) AS snapshot_length,
                       CASE WHEN OCTET_LENGTH(p.snapshot_blob) BETWEEN 1 AND ?
                            THEN SHA2(p.snapshot_blob, 256) ELSE NULL END AS computed_checksum,
                       SUBSTRING(p.snapshot_blob, 1, 74) AS snapshot_header
                FROM staff_sessions s LEFT JOIN staff_state_snapshots p ON p.session_id = s.session_id
                WHERE s.staff_id = ? AND s.state <> 'CLOSED'
                LIMIT 2
                """)) {
            statement.setInt(1, MAX_BYTES);
            statement.setBytes(2, UuidBytes.toBytes(staffId));
            statement.setMaxRows(ROW_LIMIT);
            statement.setQueryTimeout(QUERY_SECONDS);
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) {
                    return Optional.empty();
                }
                StaffSessionInspection snapshot = validated(rows, staffId);
                if (rows.next()) {
                    throw new StaffSessionInspectionException(Reason.AMBIGUOUS_SESSION);
                }
                return Optional.of(snapshot);
            }
        }
    }

    private static StaffSessionInspection validated(ResultSet row, UUID staffId) throws SQLException {
        byte[] snapshotSession = row.getBytes("snapshot_session_id");
        if (snapshotSession == null) {
            throw new StaffSessionInspectionException(Reason.MISSING_SNAPSHOT);
        }
        if (!Arrays.equals(snapshotSession, row.getBytes("session_id"))) {
            throw new StaffSessionInspectionException(Reason.INVALID_METADATA);
        }
        validateBytes(row);
        validateRequiredMetadata(row);
        try {
            StaffSessionInspection snapshot = new StaffSessionInspection(
                    UuidBytes.fromBytes(row.getBytes("session_id")),
                    UuidBytes.fromBytes(row.getBytes("staff_id")), row.getString("server_id"),
                    net.enthusia.staff.domain.staff.StaffSessionState.valueOf(row.getString("state")),
                    row.getLong("revision"), row.getTimestamp("started_at").toInstant(),
                    row.getInt("schema_version"), row.getLong("snapshot_length"),
                    row.getString("checksum"), row.getBytes("snapshot_header"));
            if (!staffId.equals(snapshot.staffId())) {
                throw new StaffSessionInspectionException(Reason.INVALID_METADATA);
            }
            return snapshot;
        } catch (IllegalArgumentException exception) {
            throw new StaffSessionInspectionException(Reason.INVALID_METADATA);
        }
    }

    private static void validateRequiredMetadata(ResultSet row) throws SQLException {
        if (row.getString("server_id") == null || row.getString("state") == null
                || row.getTimestamp("started_at") == null) {
            throw new StaffSessionInspectionException(Reason.INVALID_METADATA);
        }
    }

    private static void validateBytes(ResultSet row) throws SQLException {
        long length = row.getLong("snapshot_length");
        if (length < 1 || length > MAX_BYTES) {
            throw new StaffSessionInspectionException(Reason.INVALID_SNAPSHOT_LENGTH);
        }
        byte[] header = row.getBytes("snapshot_header");
        if (header == null || header.length != Math.min(length, HEADER_PREFIX_BYTES)) {
            throw new StaffSessionInspectionException(Reason.INVALID_SNAPSHOT_LENGTH);
        }
        String checksum = row.getString("checksum");
        if (checksum == null || !checksum.equals(row.getString("computed_checksum"))) {
            throw new StaffSessionInspectionException(Reason.CHECKSUM_MISMATCH);
        }
    }
}
