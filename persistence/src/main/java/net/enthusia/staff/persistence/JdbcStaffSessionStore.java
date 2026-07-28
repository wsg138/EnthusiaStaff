package net.enthusia.staff.persistence;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;
import net.enthusia.staff.domain.ports.StaffSessionStore;
import net.enthusia.staff.domain.staff.StaffSessionSnapshot;
import net.enthusia.staff.domain.staff.StaffSessionState;

public final class JdbcStaffSessionStore implements StaffSessionStore {
    private static final int MAX_SNAPSHOT_BYTES = 8 * 1024 * 1024;

    private final DataSource dataSource;

    public JdbcStaffSessionStore(DataSource dataSource) {
        if (dataSource == null) {
            throw new IllegalArgumentException("dataSource must be present");
        }
        this.dataSource = dataSource;
    }

    @Override
    public StaffSessionSnapshot begin(
            UUID staffId,
            String serverId,
            int schemaVersion,
            String checksum,
            byte[] snapshot,
            Instant now
    ) {
        validateSnapshot(staffId, serverId, schemaVersion, checksum, snapshot, now);
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                StaffSessionSnapshot existing = active(connection, staffId, true);
                if (existing != null) {
                    connection.rollback();
                    return existing;
                }
                UUID sessionId = UUID.randomUUID();
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
                    if (activate.executeUpdate() != 1) {
                        throw new SQLException("staff session activation lost its state transition");
                    }
                }
                insertAudit(connection, staffId, sessionId, "STAFF_MODE_ENTERED", "Snapshot committed", now);
                insertDiscord(connection, staffId, sessionId, "STAFF_MODE_ENTERED", now);
                StaffSessionSnapshot created = active(connection, staffId, true);
                connection.commit();
                return created;
            } catch (SQLException exception) {
                rollback(connection, exception);
                StaffSessionSnapshot existing = activeAfterConflict(staffId);
                if (existing != null) {
                    return existing;
                }
                throw new ModerationPersistenceException("Staff session entry transaction failed", exception);
            } finally {
                restoreAutoCommit(connection);
            }
        } catch (SQLException exception) {
            throw new ModerationPersistenceException("Unable to open staff session transaction", exception);
        }
    }

    @Override
    public Optional<StaffSessionSnapshot> active(UUID staffId) {
        if (staffId == null) {
            throw new IllegalArgumentException("staffId must be present");
        }
        try (Connection connection = dataSource.getConnection()) {
            return Optional.ofNullable(active(connection, staffId, false));
        } catch (SQLException exception) {
            throw new ModerationPersistenceException("Unable to read active staff session", exception);
        }
    }

    @Override
    public Optional<StaffSessionSnapshot> beginExit(UUID staffId, Instant now) {
        if (staffId == null || now == null) {
            throw new IllegalArgumentException("staff and current time are required");
        }
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                StaffSessionSnapshot current = active(connection, staffId, true);
                if (current == null) {
                    connection.rollback();
                    return Optional.empty();
                }
                if (current.state() == StaffSessionState.ACTIVE
                        || current.state() == StaffSessionState.RECOVERY_REQUIRED) {
                    try (PreparedStatement statement = connection.prepareStatement("""
                            UPDATE staff_sessions SET state = 'EXITING', revision = revision + 1
                            WHERE session_id = ? AND state IN ('ACTIVE', 'RECOVERY_REQUIRED')
                            """)) {
                        statement.setBytes(1, UuidBytes.toBytes(current.sessionId()));
                        statement.executeUpdate();
                    }
                    current = active(connection, staffId, true);
                }
                connection.commit();
                return Optional.of(current);
            } catch (SQLException exception) {
                rollback(connection, exception);
                throw exception;
            } finally {
                restoreAutoCommit(connection);
            }
        } catch (SQLException exception) {
            throw new ModerationPersistenceException("Unable to begin staff session exit", exception);
        }
    }

    @Override
    public boolean completeExit(UUID sessionId, String restoredChecksum, Instant now) {
        if (sessionId == null || restoredChecksum == null || !restoredChecksum.matches("[0-9a-f]{64}") || now == null) {
            throw new IllegalArgumentException("valid staff exit verification fields are required");
        }
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                SessionIdentity session = lockSessionIdentity(connection, sessionId);
                if (session == null || session.state() != StaffSessionState.EXITING) {
                    connection.rollback();
                    return false;
                }
                String expected = snapshotChecksum(connection, sessionId);
                if (!restoredChecksum.equals(expected)) {
                    markRecovery(connection, sessionId);
                    insertAudit(connection, session.staffId(), sessionId, "STAFF_MODE_RECOVERY_REQUIRED",
                            "Restored state checksum mismatch", now);
                    connection.commit();
                    return false;
                }
                try (PreparedStatement statement = connection.prepareStatement("""
                        UPDATE staff_sessions
                        SET state = 'CLOSED', vanish_active = FALSE, ended_at = ?, revision = revision + 1
                        WHERE session_id = ? AND state = 'EXITING'
                        """)) {
                    statement.setTimestamp(1, Timestamp.from(now));
                    statement.setBytes(2, UuidBytes.toBytes(sessionId));
                    if (statement.executeUpdate() != 1) {
                        throw new SQLException("staff session closure lost its state transition");
                    }
                }
                insertAudit(connection, session.staffId(), sessionId, "STAFF_MODE_EXITED", "Restoration verified", now);
                insertDiscord(connection, session.staffId(), sessionId, "STAFF_MODE_EXITED", now);
                connection.commit();
                return true;
            } catch (SQLException exception) {
                rollback(connection, exception);
                throw exception;
            } finally {
                restoreAutoCommit(connection);
            }
        } catch (SQLException exception) {
            throw new ModerationPersistenceException("Unable to complete staff session exit", exception);
        }
    }

    @Override
    public void recoveryRequired(UUID sessionId, String reason, Instant now) {
        if (sessionId == null || reason == null || reason.isBlank() || reason.length() > 512 || now == null) {
            throw new IllegalArgumentException("valid staff recovery fields are required");
        }
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                SessionIdentity session = lockSessionIdentity(connection, sessionId);
                if (session == null) {
                    connection.rollback();
                    return;
                }
                markRecovery(connection, sessionId);
                insertAudit(connection, session.staffId(), sessionId, "STAFF_MODE_RECOVERY_REQUIRED", reason, now);
                connection.commit();
            } catch (SQLException exception) {
                rollback(connection, exception);
                throw exception;
            } finally {
                restoreAutoCommit(connection);
            }
        } catch (SQLException exception) {
            throw new ModerationPersistenceException("Unable to mark staff session for recovery", exception);
        }
    }

    @Override
    public boolean setVanish(UUID staffId, boolean vanished, Instant now) {
        if (staffId == null || now == null) {
            throw new IllegalArgumentException("staff and current time are required");
        }
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     UPDATE staff_sessions
                     SET vanish_active = ?, revision = revision + 1
                     WHERE staff_id = ? AND state IN ('ACTIVE', 'RECOVERY_REQUIRED')
                     """)) {
            statement.setBoolean(1, vanished);
            statement.setBytes(2, UuidBytes.toBytes(staffId));
            return statement.executeUpdate() == 1;
        } catch (SQLException exception) {
            throw new ModerationPersistenceException("Unable to persist staff-session vanish state", exception);
        }
    }

    private static StaffSessionSnapshot active(Connection connection, UUID staffId, boolean lock) throws SQLException {
        String suffix = lock ? " FOR UPDATE" : "";
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT s.session_id, s.staff_id, s.server_id, s.state, s.vanish_active,
                       s.started_at, s.revision, p.schema_version, p.checksum, p.snapshot_blob
                FROM staff_sessions s JOIN staff_state_snapshots p ON p.session_id = s.session_id
                WHERE s.staff_id = ? AND s.state IN ('ENTERING', 'ACTIVE', 'EXITING', 'RECOVERY_REQUIRED')
                """ + suffix)) {
            statement.setBytes(1, UuidBytes.toBytes(staffId));
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? read(result) : null;
            }
        }
    }

    private static StaffSessionSnapshot read(ResultSet result) throws SQLException {
        return new StaffSessionSnapshot(
                UuidBytes.fromBytes(result.getBytes("session_id")),
                UuidBytes.fromBytes(result.getBytes("staff_id")),
                result.getString("server_id"),
                StaffSessionState.valueOf(result.getString("state")),
                result.getBoolean("vanish_active"),
                result.getInt("schema_version"),
                result.getString("checksum"),
                result.getBytes("snapshot_blob"),
                result.getTimestamp("started_at").toInstant(),
                result.getLong("revision")
        );
    }

    private StaffSessionSnapshot activeAfterConflict(UUID staffId) {
        try (Connection connection = dataSource.getConnection()) {
            return active(connection, staffId, false);
        } catch (SQLException exception) {
            return null;
        }
    }

    private static SessionIdentity lockSessionIdentity(Connection connection, UUID sessionId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT staff_id, state FROM staff_sessions WHERE session_id = ? FOR UPDATE
                """)) {
            statement.setBytes(1, UuidBytes.toBytes(sessionId));
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? new SessionIdentity(
                        UuidBytes.fromBytes(result.getBytes(1)), StaffSessionState.valueOf(result.getString(2))
                ) : null;
            }
        }
    }

    private static String snapshotChecksum(Connection connection, UUID sessionId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT checksum FROM staff_state_snapshots WHERE session_id = ?")) {
            statement.setBytes(1, UuidBytes.toBytes(sessionId));
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    throw new SQLException("staff state snapshot is missing");
                }
                return result.getString(1);
            }
        }
    }

    private static void markRecovery(Connection connection, UUID sessionId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE staff_sessions SET state = 'RECOVERY_REQUIRED', revision = revision + 1
                WHERE session_id = ? AND state <> 'CLOSED'
                """)) {
            statement.setBytes(1, UuidBytes.toBytes(sessionId));
            statement.executeUpdate();
        }
    }

    private static void insertAudit(
            Connection connection,
            UUID staffId,
            UUID sessionId,
            String eventType,
            String detail,
            Instant now
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO audit_events(event_id, correlation_id, actor_id, target_id,
                    event_type, outcome, event_json, occurred_at)
                VALUES (?, ?, ?, ?, ?, 'COMMITTED', ?, ?)
                """)) {
            statement.setBytes(1, UuidBytes.toBytes(UUID.randomUUID()));
            statement.setBytes(2, UuidBytes.toBytes(sessionId));
            statement.setBytes(3, UuidBytes.toBytes(staffId));
            statement.setBytes(4, UuidBytes.toBytes(staffId));
            statement.setString(5, eventType);
            statement.setString(6, "{\"sessionId\":\"" + sessionId + "\",\"detail\":\""
                    + escape(detail) + "\"}");
            statement.setTimestamp(7, Timestamp.from(now));
            statement.executeUpdate();
        }
    }

    private static void insertDiscord(
            Connection connection,
            UUID staffId,
            UUID sessionId,
            String eventType,
            Instant now
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO discord_outbox(message_id, idempotency_key, destination, event_type,
                    payload_json, available_at, created_at)
                VALUES (?, ?, 'logs-staffmode', ?, ?, ?, ?)
                """)) {
            statement.setBytes(1, UuidBytes.toBytes(UUID.randomUUID()));
            statement.setString(2, "staff-session:" + sessionId + ':' + eventType);
            statement.setString(3, eventType);
            statement.setString(4, "{\"sessionId\":\"" + sessionId + "\",\"staffId\":\"" + staffId + "\"}");
            statement.setTimestamp(5, Timestamp.from(now));
            statement.setTimestamp(6, Timestamp.from(now));
            statement.executeUpdate();
        }
    }

    private static void validateSnapshot(
            UUID staffId,
            String serverId,
            int schemaVersion,
            String checksum,
            byte[] snapshot,
            Instant now
    ) {
        if (staffId == null || serverId == null || !serverId.matches("[A-Za-z0-9_-]{1,64}")
                || schemaVersion < 1 || checksum == null || !checksum.matches("[0-9a-f]{64}")
                || snapshot == null || snapshot.length == 0 || snapshot.length > MAX_SNAPSHOT_BYTES || now == null) {
            throw new IllegalArgumentException("valid bounded staff snapshot fields are required");
        }
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\r", "\\r").replace("\n", "\\n");
    }

    private static void rollback(Connection connection, SQLException original) {
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

    private record SessionIdentity(UUID staffId, StaffSessionState state) {
    }
}
