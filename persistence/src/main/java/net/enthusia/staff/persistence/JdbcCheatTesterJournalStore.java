package net.enthusia.staff.persistence;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;
import net.enthusia.staff.domain.ports.CheatTesterJournalStore;
import net.enthusia.staff.domain.tester.CheatTesterJournalRecord;
import net.enthusia.staff.domain.tester.CheatTesterJournalStart;
import net.enthusia.staff.domain.tester.CheatTesterSessionState;
import net.enthusia.staff.domain.tester.CheatTesterType;

/** MariaDB-backed recovery journal for temporary cheat-tester state. */
public final class JdbcCheatTesterJournalStore implements CheatTesterJournalStore {
    private static final int MAX_ACTIVE_READ = 256;
    private static final int MAX_EVIDENCE = 32 * 1024;
    private static final int MAX_REASON = 255;

    private final DataSource dataSource;

    public JdbcCheatTesterJournalStore(DataSource dataSource) {
        if (dataSource == null) {
            throw new IllegalArgumentException("dataSource must be present");
        }
        this.dataSource = dataSource;
    }

    @Override
    public CheatTesterJournalRecord start(CheatTesterJournalStart start) {
        if (start == null) {
            throw new IllegalArgumentException("tester start must be present");
        }
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                Optional<CheatTesterJournalRecord> existing = activeForTarget(connection, start.targetId(), true);
                if (existing.isPresent()) {
                    connection.rollback();
                    return existing.orElseThrow();
                }
                insertStart(connection, start);
                insertAudit(connection, start.sessionId(), start.staffId(), start.targetId(),
                        "CHEAT_TESTER_STARTED", start.testerType().id(), start.startedAt());
                CheatTesterJournalRecord created = byId(connection, start.sessionId(), true)
                        .orElseThrow(() -> new SQLException("tester journal row was not readable after insert"));
                connection.commit();
                return created;
            } catch (SQLIntegrityConstraintViolationException conflict) {
                rollback(connection, conflict);
                Optional<CheatTesterJournalRecord> existing = activeForTarget(start.targetId());
                if (existing.isPresent()) {
                    return existing.orElseThrow();
                }
                throw new ModerationPersistenceException("Tester session uniqueness conflict was not recoverable", conflict);
            } catch (SQLException exception) {
                rollback(connection, exception);
                throw new ModerationPersistenceException("Unable to start cheat tester journal", exception);
            } finally {
                restoreAutoCommit(connection);
            }
        } catch (SQLException exception) {
            throw new ModerationPersistenceException("Unable to open cheat tester transaction", exception);
        }
    }

    @Override
    public Optional<CheatTesterJournalRecord> activeForTarget(UUID targetId) {
        validateTarget(targetId);
        try (Connection connection = dataSource.getConnection()) {
            return activeForTarget(connection, targetId, false);
        } catch (SQLException exception) {
            throw new ModerationPersistenceException("Unable to read globally active cheat tester", exception);
        }
    }

    @Override
    public Optional<CheatTesterJournalRecord> activeForTarget(String serverId, UUID targetId) {
        validateServerAndTarget(serverId, targetId);
        try (Connection connection = dataSource.getConnection()) {
            return activeForTarget(connection, serverId, targetId, false);
        } catch (SQLException exception) {
            throw new ModerationPersistenceException("Unable to read active cheat tester", exception);
        }
    }

    @Override
    public List<CheatTesterJournalRecord> activeForServer(String serverId, int limit) {
        validateServer(serverId);
        if (limit < 1 || limit > MAX_ACTIVE_READ) {
            throw new IllegalArgumentException("tester active read limit must be 1.." + MAX_ACTIVE_READ);
        }
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT * FROM cheat_tester_sessions
                     WHERE server_id = ? AND state = 'ACTIVE'
                     ORDER BY started_at, session_id
                     LIMIT ?
                     """)) {
            statement.setString(1, serverId);
            statement.setInt(2, limit);
            List<CheatTesterJournalRecord> records = new ArrayList<>();
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    records.add(read(result));
                }
            }
            return List.copyOf(records);
        } catch (SQLException exception) {
            throw new ModerationPersistenceException("Unable to read recoverable cheat testers", exception);
        }
    }

    @Override
    public Optional<CheatTesterJournalRecord> checkpointEvidence(
            UUID sessionId,
            long expectedRevision,
            String evidence,
            Instant now
    ) {
        validateCheckpoint(sessionId, expectedRevision, evidence, now);
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                int changed;
                try (PreparedStatement statement = connection.prepareStatement("""
                        UPDATE cheat_tester_sessions
                        SET evidence_text = ?, updated_at = ?, revision = revision + 1
                        WHERE session_id = ? AND state = 'ACTIVE' AND revision = ?
                        """)) {
                    statement.setString(1, evidence);
                    statement.setTimestamp(2, Timestamp.from(now));
                    statement.setBytes(3, UuidBytes.toBytes(sessionId));
                    statement.setLong(4, expectedRevision);
                    changed = statement.executeUpdate();
                }
                if (changed != 1) {
                    connection.rollback();
                    return Optional.empty();
                }
                CheatTesterJournalRecord updated = byId(connection, sessionId, true)
                        .orElseThrow(() -> new SQLException("tester journal disappeared after checkpoint"));
                connection.commit();
                return Optional.of(updated);
            } catch (SQLException exception) {
                rollback(connection, exception);
                throw new ModerationPersistenceException("Unable to checkpoint cheat tester evidence", exception);
            } finally {
                restoreAutoCommit(connection);
            }
        } catch (SQLException exception) {
            throw new ModerationPersistenceException("Unable to open tester evidence transaction", exception);
        }
    }

    @Override
    public boolean complete(
            UUID sessionId,
            long expectedRevision,
            CheatTesterSessionState terminalState,
            String reason,
            String evidence,
            Instant now
    ) {
        validateCompletion(sessionId, expectedRevision, terminalState, reason, evidence, now);
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                CheatTesterJournalRecord current = byId(connection, sessionId, true).orElse(null);
                if (current == null || !current.active() || current.revision() != expectedRevision) {
                    connection.rollback();
                    return false;
                }
                try (PreparedStatement statement = connection.prepareStatement("""
                        UPDATE cheat_tester_sessions
                        SET active_target_uuid = NULL, evidence_text = ?, state = ?, completion_reason = ?,
                            completed_at = ?, updated_at = ?, revision = revision + 1
                        WHERE session_id = ? AND state = 'ACTIVE' AND revision = ?
                        """)) {
                    statement.setString(1, evidence);
                    statement.setString(2, terminalState.name());
                    statement.setString(3, reason);
                    statement.setTimestamp(4, Timestamp.from(now));
                    statement.setTimestamp(5, Timestamp.from(now));
                    statement.setBytes(6, UuidBytes.toBytes(sessionId));
                    statement.setLong(7, expectedRevision);
                    if (statement.executeUpdate() != 1) {
                        connection.rollback();
                        return false;
                    }
                }
                insertAudit(connection, sessionId, current.staffId(), current.targetId(),
                        "CHEAT_TESTER_" + terminalState.name(), reason, now);
                connection.commit();
                return true;
            } catch (SQLException exception) {
                rollback(connection, exception);
                throw new ModerationPersistenceException("Unable to complete cheat tester journal", exception);
            } finally {
                restoreAutoCommit(connection);
            }
        } catch (SQLException exception) {
            throw new ModerationPersistenceException("Unable to open tester completion transaction", exception);
        }
    }

    private static void insertStart(Connection connection, CheatTesterJournalStart start) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO cheat_tester_sessions(
                    session_id, server_id, staff_id, target_id, active_target_uuid, tester_type,
                    snapshot_text, configuration_text, state, started_at, expires_at, updated_at, revision
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'ACTIVE', ?, ?, ?, 0)
                """)) {
            statement.setBytes(1, UuidBytes.toBytes(start.sessionId()));
            statement.setString(2, start.serverId());
            statement.setBytes(3, UuidBytes.toBytes(start.staffId()));
            statement.setBytes(4, UuidBytes.toBytes(start.targetId()));
            statement.setString(5, start.targetId().toString());
            statement.setString(6, start.testerType().name());
            statement.setString(7, start.snapshot());
            statement.setString(8, start.configuration());
            statement.setTimestamp(9, Timestamp.from(start.startedAt()));
            statement.setTimestamp(10, Timestamp.from(start.expiresAt()));
            statement.setTimestamp(11, Timestamp.from(start.startedAt()));
            if (statement.executeUpdate() != 1) {
                throw new SQLException("tester journal insert did not affect exactly one row");
            }
        }
    }

    private static Optional<CheatTesterJournalRecord> activeForTarget(
            Connection connection,
            UUID targetId,
            boolean lock
    ) throws SQLException {
        String sql = """
                SELECT * FROM cheat_tester_sessions
                WHERE active_target_uuid = ? AND state = 'ACTIVE'
                LIMIT 1
                """ + (lock ? " FOR UPDATE" : "");
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, targetId.toString());
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? Optional.of(read(result)) : Optional.empty();
            }
        }
    }

    private static Optional<CheatTesterJournalRecord> activeForTarget(
            Connection connection,
            String serverId,
            UUID targetId,
            boolean lock
    ) throws SQLException {
        String sql = """
                SELECT * FROM cheat_tester_sessions
                WHERE server_id = ? AND active_target_uuid = ? AND state = 'ACTIVE'
                LIMIT 1
                """ + (lock ? " FOR UPDATE" : "");
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, serverId);
            statement.setString(2, targetId.toString());
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? Optional.of(read(result)) : Optional.empty();
            }
        }
    }

    private static Optional<CheatTesterJournalRecord> byId(Connection connection, UUID sessionId, boolean lock)
            throws SQLException {
        String sql = "SELECT * FROM cheat_tester_sessions WHERE session_id = ?" + (lock ? " FOR UPDATE" : "");
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setBytes(1, UuidBytes.toBytes(sessionId));
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? Optional.of(read(result)) : Optional.empty();
            }
        }
    }

    private static CheatTesterJournalRecord read(ResultSet result) throws SQLException {
        Timestamp completed = result.getTimestamp("completed_at");
        return new CheatTesterJournalRecord(
                UuidBytes.fromBytes(result.getBytes("session_id")),
                result.getString("server_id"),
                UuidBytes.fromBytes(result.getBytes("staff_id")),
                UuidBytes.fromBytes(result.getBytes("target_id")),
                CheatTesterType.valueOf(result.getString("tester_type")),
                result.getString("snapshot_text"),
                result.getString("configuration_text"),
                result.getString("evidence_text"),
                CheatTesterSessionState.valueOf(result.getString("state")),
                result.getString("completion_reason"),
                result.getTimestamp("started_at").toInstant(),
                result.getTimestamp("expires_at").toInstant(),
                completed == null ? null : completed.toInstant(),
                result.getLong("revision")
        );
    }

    private static void insertAudit(
            Connection connection,
            UUID sessionId,
            UUID staffId,
            UUID targetId,
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
            statement.setBytes(4, UuidBytes.toBytes(targetId));
            statement.setString(5, eventType);
            statement.setString(6, "{\"sessionId\":\"" + sessionId + "\",\"detail\":\""
                    + escape(detail) + "\"}");
            statement.setTimestamp(7, Timestamp.from(now));
            if (statement.executeUpdate() != 1) {
                throw new SQLException("tester audit insert did not affect exactly one row");
            }
        }
    }

    private static void validateServerAndTarget(String serverId, UUID targetId) {
        validateServer(serverId);
        validateTarget(targetId);
    }

    private static void validateTarget(UUID targetId) {
        if (targetId == null) {
            throw new IllegalArgumentException("targetId must be present");
        }
    }

    private static void validateServer(String serverId) {
        if (serverId == null || !serverId.matches("[A-Za-z0-9_-]{1,64}")) {
            throw new IllegalArgumentException("serverId is invalid");
        }
    }

    private static void validateCheckpoint(UUID sessionId, long revision, String evidence, Instant now) {
        if (sessionId == null || revision < 0 || now == null || evidence == null || evidence.length() > MAX_EVIDENCE) {
            throw new IllegalArgumentException("tester evidence checkpoint is invalid");
        }
    }

    private static void validateCompletion(
            UUID sessionId,
            long revision,
            CheatTesterSessionState state,
            String reason,
            String evidence,
            Instant now
    ) {
        if (sessionId == null || revision < 0 || state == null || state == CheatTesterSessionState.ACTIVE
                || reason == null || reason.isBlank() || reason.length() > MAX_REASON
                || evidence == null || evidence.length() > MAX_EVIDENCE || now == null) {
            throw new IllegalArgumentException("tester completion is invalid");
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
}
