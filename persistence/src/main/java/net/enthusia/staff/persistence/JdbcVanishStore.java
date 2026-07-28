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
    public void set(UUID staffId, StaffRank rank, boolean vanished, UUID actorId, Instant now) {
        if (staffId == null || rank == null || rank == StaffRank.SYSTEM || actorId == null || now == null) {
            throw new IllegalArgumentException("valid vanish state fields are required");
        }
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
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
                insertAudit(connection, staffId, actorId, rank, vanished, now);
                insertDiscord(connection, staffId, actorId, rank, vanished, now);
                connection.commit();
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
}
