package net.enthusia.staff.persistence;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;
import net.enthusia.staff.domain.ports.StaffSessionStore;
import net.enthusia.staff.domain.staff.StaffSessionSnapshot;
import net.enthusia.staff.domain.staff.StaffSessionState;

public final class JdbcStaffSessionStore implements StaffSessionStore {
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
        JdbcStaffSessionValidation.validateSnapshot(
                staffId,
                serverId,
                schemaVersion,
                checksum,
                snapshot,
                now
        );
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                return beginTransaction(
                        connection,
                        staffId,
                        serverId,
                        schemaVersion,
                        checksum,
                        snapshot,
                        now
                );
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

    private StaffSessionSnapshot beginTransaction(
            Connection connection,
            UUID staffId,
            String serverId,
            int schemaVersion,
            String checksum,
            byte[] snapshot,
            Instant now
    ) throws SQLException {
        StaffSessionSnapshot existing = active(connection, staffId, true);
        if (existing != null) {
            connection.rollback();
            return existing;
        }
        UUID sessionId = UUID.randomUUID();
        JdbcStaffSessionTransitions.insertSessionAndSnapshot(
                connection,
                sessionId,
                staffId,
                serverId,
                schemaVersion,
                checksum,
                snapshot,
                now
        );
        insertAudit(connection, staffId, sessionId, "STAFF_MODE_ENTERED", "Snapshot committed", now);
        insertDiscord(connection, staffId, sessionId, "STAFF_MODE_ENTERED", now);
        StaffSessionSnapshot created = active(connection, staffId, true);
        connection.commit();
        return created;
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
                return completeExitTransaction(connection, sessionId, restoredChecksum, now);
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

    private static boolean completeExitTransaction(
            Connection connection,
            UUID sessionId,
            String restoredChecksum,
            Instant now
    ) throws SQLException {
        SessionIdentity session = lockSessionIdentity(connection, sessionId);
        if (session == null || session.state() != StaffSessionState.EXITING) {
            connection.rollback();
            return false;
        }
        String expected = snapshotChecksum(connection, sessionId);
        if (!restoredChecksum.equals(expected)) {
            markChecksumMismatch(connection, sessionId, session, now);
            return false;
        }
        JdbcStaffSessionTransitions.closeSession(connection, sessionId, now);
        insertAudit(
                connection,
                session.staffId(),
                sessionId,
                "STAFF_MODE_EXITED",
                "Restoration verified",
                now
        );
        insertDiscord(connection, session.staffId(), sessionId, "STAFF_MODE_EXITED", now);
        connection.commit();
        return true;
    }

    private static void markChecksumMismatch(
            Connection connection,
            UUID sessionId,
            SessionIdentity session,
            Instant now
    ) throws SQLException {
        markRecovery(connection, sessionId);
        insertAudit(
                connection,
                session.staffId(),
                sessionId,
                "STAFF_MODE_RECOVERY_REQUIRED",
                "Restored state checksum mismatch",
                now
        );
        connection.commit();
    }

    @Override
    public void recoveryRequired(UUID sessionId, String reason, Instant now) {
        JdbcStaffSessionValidation.validateRecoveryRequest(sessionId, reason, now);
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
    public int recoveryRequiredForServer(String serverId, String reason, Instant now) {
        JdbcStaffSessionValidation.validateServerRecovery(serverId, reason, now);
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                List<RecoverableSession> sessions = lockRecoverableSessions(connection, serverId);
                if (sessions.isEmpty()) {
                    connection.rollback();
                    return 0;
                }
                int updated = markServerRecovery(connection, serverId);
                if (updated != sessions.size()) {
                    throw new SQLException("staff-session shutdown recovery lost a locked state transition");
                }
                for (RecoverableSession session : sessions) {
                    insertAudit(
                            connection,
                            session.staffId(),
                            session.sessionId(),
                            "STAFF_MODE_RECOVERY_REQUIRED",
                            reason,
                            now
                    );
                }
                connection.commit();
                return updated;
            } catch (SQLException exception) {
                rollback(connection, exception);
                throw exception;
            } finally {
                restoreAutoCommit(connection);
            }
        } catch (SQLException exception) {
            throw new ModerationPersistenceException(
                    "Unable to mark server staff sessions for shutdown recovery",
                    exception
            );
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

    private static List<RecoverableSession> lockRecoverableSessions(
            Connection connection,
            String serverId
    ) throws SQLException {
        List<RecoverableSession> sessions = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT session_id, staff_id
                FROM staff_sessions
                WHERE server_id = ? AND state IN ('ACTIVE', 'EXITING')
                ORDER BY session_id
                FOR UPDATE
                """)) {
            statement.setString(1, serverId);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    sessions.add(new RecoverableSession(
                            UuidBytes.fromBytes(result.getBytes("session_id")),
                            UuidBytes.fromBytes(result.getBytes("staff_id"))
                    ));
                }
            }
        }
        return sessions;
    }

    private static int markServerRecovery(Connection connection, String serverId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE staff_sessions
                SET state = 'RECOVERY_REQUIRED', revision = revision + 1
                WHERE server_id = ? AND state IN ('ACTIVE', 'EXITING')
                """)) {
            statement.setString(1, serverId);
            return statement.executeUpdate();
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

    private record RecoverableSession(UUID sessionId, UUID staffId) {
    }
}
