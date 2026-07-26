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
import net.enthusia.staff.domain.freeze.FreezeRecord;
import net.enthusia.staff.domain.ports.FreezeStore;

public final class JdbcFreezeStore implements FreezeStore {
    private final DataSource dataSource;

    public JdbcFreezeStore(DataSource dataSource) {
        if (dataSource == null) {
            throw new IllegalArgumentException("dataSource must be present");
        }
        this.dataSource = dataSource;
    }

    @Override
    public FreezeRecord apply(UUID playerId, UUID actorId, String reason, Instant now) {
        validateChange(playerId, actorId, reason, now);
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement freeze = connection.prepareStatement("""
                    INSERT INTO player_freezes(player_id, state, frozen_by, reason, frozen_at,
                        offline_expires_at, keep_active)
                    VALUES (?, 'ACTIVE', ?, ?, ?, NULL, FALSE)
                    ON DUPLICATE KEY UPDATE state = 'ACTIVE', frozen_by = VALUES(frozen_by),
                        reason = VALUES(reason), frozen_at = VALUES(frozen_at), offline_expires_at = NULL,
                        keep_active = FALSE, released_by = NULL, release_reason = NULL, released_at = NULL,
                        revision = revision + 1
                    """)) {
                freeze.setBytes(1, UuidBytes.toBytes(playerId));
                freeze.setBytes(2, UuidBytes.toBytes(actorId));
                freeze.setString(3, reason.trim());
                freeze.setTimestamp(4, Timestamp.from(now));
                freeze.executeUpdate();
                insertAudit(connection, playerId, actorId, "PLAYER_FROZEN", reason, now);
                insertDiscord(connection, playerId, actorId, "PLAYER_FROZEN", reason, now);
                FreezeRecord record = lockAndRead(connection, playerId);
                connection.commit();
                return record;
            } catch (SQLException exception) {
                rollback(connection, exception);
                throw exception;
            } finally {
                restoreAutoCommit(connection);
            }
        } catch (SQLException exception) {
            throw new ModerationPersistenceException("Unable to apply player freeze", exception);
        }
    }

    @Override
    public boolean release(UUID playerId, UUID actorId, String reason, Instant now) {
        validateChange(playerId, actorId, reason, now);
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement release = connection.prepareStatement("""
                    UPDATE player_freezes
                    SET state = 'RELEASED', released_by = ?, release_reason = ?, released_at = ?,
                        offline_expires_at = NULL, keep_active = FALSE, revision = revision + 1
                    WHERE player_id = ? AND state = 'ACTIVE'
                    """)) {
                release.setBytes(1, UuidBytes.toBytes(actorId));
                release.setString(2, reason.trim());
                release.setTimestamp(3, Timestamp.from(now));
                release.setBytes(4, UuidBytes.toBytes(playerId));
                boolean changed = release.executeUpdate() == 1;
                if (changed) {
                    insertAudit(connection, playerId, actorId, "PLAYER_UNFROZEN", reason, now);
                    insertDiscord(connection, playerId, actorId, "PLAYER_UNFROZEN", reason, now);
                }
                connection.commit();
                return changed;
            } catch (SQLException exception) {
                rollback(connection, exception);
                throw exception;
            } finally {
                restoreAutoCommit(connection);
            }
        } catch (SQLException exception) {
            throw new ModerationPersistenceException("Unable to release player freeze", exception);
        }
    }

    @Override
    public boolean keepActive(UUID playerId, UUID actorId, String reason, Instant now) {
        validateChange(playerId, actorId, reason, now);
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement statement = connection.prepareStatement("""
                    UPDATE player_freezes
                    SET keep_active = TRUE, offline_expires_at = NULL, revision = revision + 1
                    WHERE player_id = ? AND state = 'ACTIVE'
                    """)) {
                statement.setBytes(1, UuidBytes.toBytes(playerId));
                boolean changed = statement.executeUpdate() == 1;
                if (changed) {
                    insertAudit(connection, playerId, actorId, "PLAYER_FREEZE_EXTENDED", reason, now);
                }
                connection.commit();
                return changed;
            } catch (SQLException exception) {
                rollback(connection, exception);
                throw exception;
            } finally {
                restoreAutoCommit(connection);
            }
        } catch (SQLException exception) {
            throw new ModerationPersistenceException("Unable to extend player freeze", exception);
        }
    }

    @Override
    public void disconnected(UUID playerId, Instant offlineExpiration, Instant now) {
        if (playerId == null || offlineExpiration == null || now == null || !offlineExpiration.isAfter(now)) {
            throw new IllegalArgumentException("valid freeze disconnect fields are required");
        }
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     UPDATE player_freezes
                     SET offline_expires_at = CASE WHEN keep_active THEN NULL ELSE ? END,
                         revision = revision + 1
                     WHERE player_id = ? AND state = 'ACTIVE'
                     """)) {
            statement.setTimestamp(1, Timestamp.from(offlineExpiration));
            statement.setBytes(2, UuidBytes.toBytes(playerId));
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new ModerationPersistenceException("Unable to persist frozen-player disconnect", exception);
        }
    }

    @Override
    public Optional<FreezeRecord> active(UUID playerId, Instant now) {
        if (playerId == null || now == null) {
            throw new IllegalArgumentException("player and current time are required");
        }
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                FreezeRecord record = lockAndRead(connection, playerId);
                if (record == null) {
                    connection.rollback();
                    return Optional.empty();
                }
                if (!record.keepActive() && record.offlineExpiresAt().filter(expiry -> !expiry.isAfter(now)).isPresent()) {
                    try (PreparedStatement expire = connection.prepareStatement("""
                            UPDATE player_freezes SET state = 'EXPIRED', released_at = ?, revision = revision + 1
                            WHERE player_id = ? AND state = 'ACTIVE'
                            """)) {
                        expire.setTimestamp(1, Timestamp.from(now));
                        expire.setBytes(2, UuidBytes.toBytes(playerId));
                        expire.executeUpdate();
                    }
                    insertAudit(connection, playerId, null, "PLAYER_FREEZE_EXPIRED", "Offline timeout elapsed", now);
                    connection.commit();
                    return Optional.empty();
                }
                connection.commit();
                return Optional.of(record);
            } catch (SQLException exception) {
                rollback(connection, exception);
                throw exception;
            } finally {
                restoreAutoCommit(connection);
            }
        } catch (SQLException exception) {
            throw new ModerationPersistenceException("Unable to read active player freeze", exception);
        }
    }

    private static FreezeRecord lockAndRead(Connection connection, UUID playerId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT player_id, frozen_by, reason, frozen_at, offline_expires_at, keep_active, revision
                FROM player_freezes WHERE player_id = ? AND state = 'ACTIVE' FOR UPDATE
                """)) {
            statement.setBytes(1, UuidBytes.toBytes(playerId));
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    return null;
                }
                Timestamp offline = result.getTimestamp("offline_expires_at");
                return new FreezeRecord(
                        UuidBytes.fromBytes(result.getBytes("player_id")),
                        UuidBytes.fromBytes(result.getBytes("frozen_by")),
                        result.getString("reason"),
                        result.getTimestamp("frozen_at").toInstant(),
                        Optional.ofNullable(offline).map(Timestamp::toInstant),
                        result.getBoolean("keep_active"),
                        result.getLong("revision")
                );
            }
        }
    }

    private static void insertAudit(
            Connection connection,
            UUID playerId,
            UUID actorId,
            String eventType,
            String reason,
            Instant now
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO audit_events(event_id, correlation_id, actor_id, target_id,
                    event_type, outcome, event_json, occurred_at)
                VALUES (?, ?, ?, ?, ?, 'COMMITTED', ?, ?)
                """)) {
            statement.setBytes(1, UuidBytes.toBytes(UUID.randomUUID()));
            statement.setBytes(2, UuidBytes.toBytes(UUID.randomUUID()));
            if (actorId == null) {
                statement.setNull(3, java.sql.Types.BINARY);
            } else {
                statement.setBytes(3, UuidBytes.toBytes(actorId));
            }
            statement.setBytes(4, UuidBytes.toBytes(playerId));
            statement.setString(5, eventType);
            statement.setString(6, "{\"reason\":\"" + escape(reason.trim()) + "\"}");
            statement.setTimestamp(7, Timestamp.from(now));
            statement.executeUpdate();
        }
    }

    private static void insertDiscord(
            Connection connection,
            UUID playerId,
            UUID actorId,
            String eventType,
            String reason,
            Instant now
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO discord_outbox(message_id, idempotency_key, destination, event_type,
                    payload_json, available_at, created_at)
                VALUES (?, ?, 'logs-staffmode', ?, ?, ?, ?)
                """)) {
            UUID messageId = UUID.randomUUID();
            statement.setBytes(1, UuidBytes.toBytes(messageId));
            statement.setString(2, "freeze:" + messageId);
            statement.setString(3, eventType);
            statement.setString(4, "{\"targetId\":\"" + playerId + "\",\"actorId\":\""
                    + actorId + "\",\"reason\":\"" + escape(reason.trim()) + "\"}");
            statement.setTimestamp(5, Timestamp.from(now));
            statement.setTimestamp(6, Timestamp.from(now));
            statement.executeUpdate();
        }
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\r", "\\r").replace("\n", "\\n");
    }

    private static void validateChange(UUID playerId, UUID actorId, String reason, Instant now) {
        if (playerId == null || actorId == null || reason == null || reason.isBlank()
                || reason.length() > 512 || now == null) {
            throw new IllegalArgumentException("valid freeze change fields are required");
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
