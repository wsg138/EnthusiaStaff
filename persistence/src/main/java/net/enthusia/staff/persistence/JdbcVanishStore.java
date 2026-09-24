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
import javax.sql.DataSource;
import net.enthusia.staff.domain.auth.StaffRank;
import net.enthusia.staff.domain.ports.VanishStore;
import net.enthusia.staff.domain.staff.VanishRecord;

public final class JdbcVanishStore implements VanishStore {
    private static final int SINGLE_ROW_UPDATE = 1;

    private final DataSource dataSource;

    public JdbcVanishStore(DataSource dataSource) {
        if (dataSource == null) {
            throw new IllegalArgumentException("dataSource must be present");
        }
        this.dataSource = dataSource;
    }

    @Override
    public List<VanishRecord> active(int limit) {
        if (limit < 1 || limit > 10_000) {
            throw new IllegalArgumentException("vanish load limit must be bounded");
        }
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT staff_id, staff_rank, updated_at, revision
                     FROM staff_vanish_states WHERE active = TRUE ORDER BY updated_at LIMIT ?
                     """)) {
            statement.setInt(1, limit);
            try (ResultSet result = statement.executeQuery()) {
                List<VanishRecord> records = new ArrayList<>();
                while (result.next()) {
                    records.add(new VanishRecord(
                            UuidBytes.fromBytes(result.getBytes("staff_id")),
                            StaffRank.valueOf(result.getString("staff_rank")),
                            result.getTimestamp("updated_at").toInstant(),
                            result.getLong("revision")
                    ));
                }
                return List.copyOf(records);
            }
        } catch (SQLException | IllegalArgumentException exception) {
            throw new ModerationPersistenceException("Unable to load active vanish states", exception);
        }
    }

    @Override
    public WriteResult set(
            UUID staffId,
            StaffRank rank,
            boolean vanished,
            UUID actorId,
            Instant now,
            boolean requireActiveStaffSession
    ) {
        validateWrite(staffId, rank, actorId, now);
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                return setTransaction(
                        connection,
                        staffId,
                        rank,
                        vanished,
                        actorId,
                        now,
                        requireActiveStaffSession
                );
            } catch (SQLException exception) {
                rollback(connection, exception);
                throw exception;
            } finally {
                restoreAutoCommit(connection);
            }
        } catch (SQLException exception) {
            throw new ModerationPersistenceException("Unable to persist vanish state", exception);
        }
    }

    private static WriteResult setTransaction(
            Connection connection,
            UUID staffId,
            StaffRank rank,
            boolean vanished,
            UUID actorId,
            Instant now,
            boolean requireActiveStaffSession
    ) throws SQLException {
        VanishState current = lockVanishState(connection, staffId);
        SessionMirror session = lockActiveSession(connection, staffId);
        if (requireActiveStaffSession && session == null) {
            connection.rollback();
            return WriteResult.STAFF_SESSION_NOT_ACTIVE;
        }
        ChangeSet changes = changes(current, session, rank, vanished);
        if (!changes.changed()) {
            connection.rollback();
            return WriteResult.UNCHANGED;
        }
        persistChanges(connection, staffId, actorId, rank, vanished, now, session, changes);
        connection.commit();
        return WriteResult.COMMITTED;
    }

    private static ChangeSet changes(
            VanishState current,
            SessionMirror session,
            StaffRank rank,
            boolean vanished
    ) {
        return new ChangeSet(
                !matches(current, rank, vanished),
                session != null && session.vanished() != vanished
        );
    }

    private static void persistChanges(
            Connection connection,
            UUID staffId,
            UUID actorId,
            StaffRank rank,
            boolean vanished,
            Instant now,
            SessionMirror session,
            ChangeSet changes
    ) throws SQLException {
        if (!changes.stateChanged()) {
            updateSessionMirror(connection, session.sessionId(), vanished);
            return;
        }
        writeState(connection, staffId, actorId, rank, vanished, now);
        updateSessionMirrorIfChanged(connection, session, vanished);
        insertAudit(connection, staffId, actorId, rank, vanished, now);
        insertDiscord(connection, staffId, actorId, rank, vanished, now);
    }

    private static void updateSessionMirrorIfChanged(
            Connection connection,
            SessionMirror session,
            boolean vanished
    ) throws SQLException {
        if (session != null && session.vanished() != vanished) {
            updateSessionMirror(connection, session.sessionId(), vanished);
        }
    }

    private static void validateWrite(UUID staffId, StaffRank rank, UUID actorId, Instant now) {
        if (staffId == null || rank == null || rank == StaffRank.SYSTEM || actorId == null || now == null) {
            throw new IllegalArgumentException("valid vanish state fields are required");
        }
    }

    private static VanishState lockVanishState(Connection connection, UUID staffId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT active, staff_rank
                FROM staff_vanish_states
                WHERE staff_id = ?
                FOR UPDATE
                """)) {
            statement.setBytes(1, UuidBytes.toBytes(staffId));
            try (ResultSet result = statement.executeQuery()) {
                return result.next()
                        ? new VanishState(result.getBoolean("active"), StaffRank.valueOf(result.getString("staff_rank")))
                        : null;
            }
        }
    }

    private static SessionMirror lockActiveSession(Connection connection, UUID staffId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT session_id, vanish_active
                FROM staff_sessions
                WHERE staff_id = ? AND state IN ('ACTIVE', 'RECOVERY_REQUIRED')
                FOR UPDATE
                """)) {
            statement.setBytes(1, UuidBytes.toBytes(staffId));
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    return null;
                }
                SessionMirror mirror = new SessionMirror(
                        UuidBytes.fromBytes(result.getBytes("session_id")),
                        result.getBoolean("vanish_active")
                );
                if (result.next()) {
                    throw new SQLException("multiple active staff sessions exist for vanish mirror");
                }
                return mirror;
            }
        }
    }

    private static boolean matches(VanishState current, StaffRank rank, boolean vanished) {
        return current != null && current.vanished() == vanished && current.rank() == rank;
    }

    private static void writeState(
            Connection connection,
            UUID staffId,
            UUID actorId,
            StaffRank rank,
            boolean vanished,
            Instant now
    ) throws SQLException {
        try (PreparedStatement state = connection.prepareStatement("""
                INSERT INTO staff_vanish_states(staff_id, active, staff_rank, updated_by, updated_at)
                VALUES (?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE active = VALUES(active), staff_rank = VALUES(staff_rank),
                    updated_by = VALUES(updated_by), updated_at = VALUES(updated_at), revision = revision + 1
                """)) {
            state.setBytes(1, UuidBytes.toBytes(staffId));
            state.setBoolean(2, vanished);
            state.setString(3, rank.name());
            state.setBytes(4, UuidBytes.toBytes(actorId));
            state.setTimestamp(5, Timestamp.from(now));
            state.executeUpdate();
        }
    }

    private static void updateSessionMirror(Connection connection, UUID sessionId, boolean vanished) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE staff_sessions
                SET vanish_active = ?, revision = revision + 1
                WHERE session_id = ? AND state IN ('ACTIVE', 'RECOVERY_REQUIRED')
                """)) {
            statement.setBoolean(1, vanished);
            statement.setBytes(2, UuidBytes.toBytes(sessionId));
            if (statement.executeUpdate() != SINGLE_ROW_UPDATE) {
                throw new SQLException("locked staff session left a mirrorable state before vanish commit");
            }
        }
    }

    private static void insertAudit(
            Connection connection,
            UUID staffId,
            UUID actorId,
            StaffRank rank,
            boolean vanished,
            Instant now
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO audit_events(event_id, correlation_id, actor_id, target_id,
                    event_type, outcome, event_json, occurred_at)
                VALUES (?, ?, ?, ?, 'VANISH_CHANGED', 'COMMITTED', ?, ?)
                """)) {
            statement.setBytes(1, UuidBytes.toBytes(UUID.randomUUID()));
            statement.setBytes(2, UuidBytes.toBytes(UUID.randomUUID()));
            statement.setBytes(3, UuidBytes.toBytes(actorId));
            statement.setBytes(4, UuidBytes.toBytes(staffId));
            statement.setString(5, "{\"active\":" + vanished + ",\"rank\":\"" + rank + "\"}");
            statement.setTimestamp(6, Timestamp.from(now));
            statement.executeUpdate();
        }
    }

    private static void insertDiscord(
            Connection connection,
            UUID staffId,
            UUID actorId,
            StaffRank rank,
            boolean vanished,
            Instant now
    ) throws SQLException {
        UUID messageId = UUID.randomUUID();
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO discord_outbox(message_id, idempotency_key, destination, event_type,
                    payload_json, available_at, created_at)
                VALUES (?, ?, 'logs-staffmode', 'VANISH_CHANGED', ?, ?, ?)
                """)) {
            statement.setBytes(1, UuidBytes.toBytes(messageId));
            statement.setString(2, "vanish:" + messageId);
            statement.setString(3, "{\"staffId\":\"" + staffId + "\",\"actorId\":\"" + actorId
                    + "\",\"rank\":\"" + rank + "\",\"active\":" + vanished + "}");
            statement.setTimestamp(4, Timestamp.from(now));
            statement.setTimestamp(5, Timestamp.from(now));
            statement.executeUpdate();
        }
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

    private record VanishState(boolean vanished, StaffRank rank) {
    }

    private record SessionMirror(UUID sessionId, boolean vanished) {
    }

    private record ChangeSet(boolean stateChanged, boolean sessionChanged) {
        private boolean changed() {
            return stateChanged || sessionChanged;
        }
    }
}
