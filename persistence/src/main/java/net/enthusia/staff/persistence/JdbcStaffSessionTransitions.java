package net.enthusia.staff.persistence;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

final class JdbcStaffSessionTransitions {
    private static final int EXPECTED_UPDATED_ROWS = 1;

    private JdbcStaffSessionTransitions() {
    }

    static void insertSessionAndSnapshot(
            Connection connection,
            UUID sessionId,
            UUID staffId,
            String serverId,
            int schemaVersion,
            String checksum,
            byte[] snapshot,
            Instant now
    ) throws SQLException {
        try (PreparedStatement session = connection.prepareStatement("""
                INSERT INTO staff_sessions(session_id, staff_id, server_id, state,
                    vanish_active, started_at)
                VALUES (?, ?, ?, 'ENTERING', FALSE, ?)
                """);
             PreparedStatement state = connection.prepareStatement("""
                INSERT INTO staff_state_snapshots(snapshot_id, session_id, schema_version,
                    checksum, snapshot_blob, created_at)
                VALUES (?, ?, ?, ?, ?, ?)
                """);
             PreparedStatement activate = connection.prepareStatement("""
                UPDATE staff_sessions SET state = 'ACTIVE', revision = revision + 1
                WHERE session_id = ? AND state = 'ENTERING'
                """)) {
            session.setBytes(1, UuidBytes.toBytes(sessionId));
            session.setBytes(2, UuidBytes.toBytes(staffId));
            session.setString(3, serverId);
            session.setTimestamp(4, Timestamp.from(now));
            session.executeUpdate();

            state.setBytes(1, UuidBytes.toBytes(UUID.randomUUID()));
            state.setBytes(2, UuidBytes.toBytes(sessionId));
            state.setInt(3, schemaVersion);
            state.setString(4, checksum);
            state.setBytes(5, snapshot);
            state.setTimestamp(6, Timestamp.from(now));
            state.executeUpdate();

            activate.setBytes(1, UuidBytes.toBytes(sessionId));
            if (activate.executeUpdate() != EXPECTED_UPDATED_ROWS) {
                throw new SQLException("staff session activation lost its state transition");
            }
        }
    }

    static void closeSession(
            Connection connection,
            UUID sessionId,
            Instant now
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE staff_sessions
                SET state = 'CLOSED', vanish_active = FALSE, ended_at = ?, revision = revision + 1
                WHERE session_id = ? AND state = 'EXITING'
                """)) {
            statement.setTimestamp(1, Timestamp.from(now));
            statement.setBytes(2, UuidBytes.toBytes(sessionId));
            if (statement.executeUpdate() != EXPECTED_UPDATED_ROWS) {
                throw new SQLException("staff session closure lost its state transition");
            }
        }
    }
}
