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
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;
import net.enthusia.staff.domain.investigation.EvasionAlert;
import net.enthusia.staff.domain.moderation.ModerationSubjectId;
import net.enthusia.staff.domain.ports.DiscordInvestigationStore.EvasionAlertDraft;
import net.enthusia.staff.domain.ports.DiscordInvestigationStore.EvasionDeliveryChannel;
import net.enthusia.staff.domain.ports.DiscordInvestigationStore.EvasionDeliveryUpdate;

final class JdbcDiscordEvasionAlertStore {
    private final DataSource dataSource;

    JdbcDiscordEvasionAlertStore(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    EvasionAlert create(EvasionAlertDraft draft) {
        return JdbcTransactionSupport.execute(dataSource, "Unable to create linked-alt alert", connection -> {
            Current replay = byOperation(connection, draft.operationKey(), true);
            if (replay != null) {
                requireReplay(replay, draft);
                return replay.toDomain(true);
            }
            insert(connection, draft);
            return requireById(connection, draft.alertId(), false).toDomain(false);
        });
    }

    Optional<EvasionAlert> find(UUID alertId) {
        if (alertId == null) {
            throw new IllegalArgumentException("alertId must be present");
        }
        return JdbcTransactionSupport.execute(dataSource, "Unable to read linked-alt alert", connection ->
                Optional.ofNullable(byId(connection, alertId, false)).map(current -> current.toDomain(false)));
    }

    List<EvasionAlert> pending(Instant now, int limit) {
        if (now == null || limit < 1 || limit > 250) {
            throw new IllegalArgumentException("linked-alt alert query is invalid");
        }
        return JdbcTransactionSupport.execute(dataSource, "Unable to read pending linked-alt alerts", connection -> {
            List<EvasionAlert> alerts = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement("""
                    SELECT *
                    FROM discord_evasion_alerts
                    WHERE state = 'OPEN'
                      AND ((discord_delivery <> 'DELIVERED' AND discord_next_attempt_at <= ?)
                        OR (minecraft_delivery <> 'DELIVERED' AND minecraft_next_attempt_at <= ?))
                    ORDER BY updated_at, alert_id
                    LIMIT ?
                    """)) {
                statement.setTimestamp(1, Timestamp.from(now));
                statement.setTimestamp(2, Timestamp.from(now));
                statement.setInt(3, limit);
                try (ResultSet rows = statement.executeQuery()) {
                    while (rows.next()) {
                        alerts.add(read(rows).toDomain(false));
                    }
                }
            }
            return List.copyOf(alerts);
        });
    }

    EvasionAlert updateDelivery(EvasionDeliveryUpdate update) {
        return JdbcTransactionSupport.execute(dataSource, "Unable to update linked-alt alert delivery", connection -> {
            Current current = requireById(connection, update.alertId(), true);
            if (current.revision() != update.expectedRevision() || current.state() != EvasionAlert.State.OPEN) {
                throw new SQLException("linked-alt alert revision or state changed");
            }
            updateDelivery(connection, current, update);
            return requireById(connection, update.alertId(), false).toDomain(false);
        });
    }

    EvasionAlert resolve(UUID alertId, long expectedRevision, Instant now) {
        if (alertId == null || expectedRevision < 0 || now == null) {
            throw new IllegalArgumentException("linked-alt alert resolution is invalid");
        }
        return JdbcTransactionSupport.execute(dataSource, "Unable to resolve linked-alt alert", connection -> {
            Current current = requireById(connection, alertId, true);
            if (current.state() == EvasionAlert.State.RESOLVED) {
                return current.toDomain(true);
            }
            if (current.revision() != expectedRevision) {
                throw new SQLException("linked-alt alert revision changed");
            }
            resolve(connection, alertId, expectedRevision, now);
            return requireById(connection, alertId, false).toDomain(false);
        });
    }

    private static void resolve(Connection connection, UUID alertId, long revision, Instant now) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE discord_evasion_alerts
                SET state = 'RESOLVED', resolved_at = ?, updated_at = ?, revision = revision + 1
                WHERE alert_id = ? AND revision = ? AND state = 'OPEN'
                """)) {
            statement.setTimestamp(1, Timestamp.from(now));
            statement.setTimestamp(2, Timestamp.from(now));
            statement.setBytes(3, UuidBytes.toBytes(alertId));
            statement.setLong(4, revision);
            JdbcTransactionSupport.requireSingleUpdate(statement.executeUpdate(), "linked-alt alert resolution changed");
        }
    }

    private static void insert(Connection connection, EvasionAlertDraft draft) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO discord_evasion_alerts(
                    alert_id, operation_key, subject_id, punishment_id, triggering_minecraft_player_id,
                    current_server, player_revision, state, discord_delivery, minecraft_delivery,
                    discord_attempts, minecraft_attempts, discord_error_code, minecraft_error_code,
                    discord_next_attempt_at, minecraft_next_attempt_at,
                    created_at, updated_at, resolved_at, revision
                ) VALUES (?, ?, ?, ?, ?, ?, ?, 'OPEN', 'PENDING', 'PENDING', 0, 0, NULL, NULL,
                    ?, ?, ?, ?, NULL, 0)
                """)) {
            statement.setBytes(1, UuidBytes.toBytes(draft.alertId()));
            statement.setString(2, draft.operationKey());
            statement.setBytes(3, UuidBytes.toBytes(draft.subjectId().value()));
            statement.setBytes(4, UuidBytes.toBytes(draft.punishmentId()));
            statement.setBytes(5, UuidBytes.toBytes(draft.triggeringMinecraftPlayerId()));
            statement.setString(6, draft.currentServer());
            statement.setLong(7, draft.playerRevision());
            statement.setTimestamp(8, Timestamp.from(draft.now()));
            statement.setTimestamp(9, Timestamp.from(draft.now()));
            statement.setTimestamp(10, Timestamp.from(draft.now()));
            statement.setTimestamp(11, Timestamp.from(draft.now()));
            JdbcTransactionSupport.requireSingleUpdate(statement.executeUpdate(), "linked-alt alert was not inserted");
        }
    }

    private static void updateDelivery(
            Connection connection,
            Current current,
            EvasionDeliveryUpdate update
    ) throws SQLException {
        String prefix = update.channel() == EvasionDeliveryChannel.DISCORD ? "discord" : "minecraft";
        String sql = "UPDATE discord_evasion_alerts SET " + prefix + "_delivery = ?, "
                + prefix + "_attempts = " + prefix + "_attempts + 1, "
                + prefix + "_error_code = ?, " + prefix + "_next_attempt_at = ?, "
                + "updated_at = ?, revision = revision + 1 "
                + "WHERE alert_id = ? AND revision = ? AND state = 'OPEN'";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, update.delivered() ? EvasionAlert.DeliveryState.DELIVERED.name()
                    : EvasionAlert.DeliveryState.RETRY.name());
            setError(statement, 2, update.errorCode());
            setInstant(statement, 3, update.nextAttemptAt());
            statement.setTimestamp(4, Timestamp.from(update.now()));
            statement.setBytes(5, UuidBytes.toBytes(update.alertId()));
            statement.setLong(6, current.revision());
            JdbcTransactionSupport.requireSingleUpdate(statement.executeUpdate(), "linked-alt delivery revision changed");
        }
    }

    private Current requireById(Connection connection, UUID alertId, boolean lock) throws SQLException {
        Current current = byId(connection, alertId, lock);
        if (current == null) {
            throw new SQLException("linked-alt alert does not exist");
        }
        return current;
    }

    private Current byId(Connection connection, UUID alertId, boolean lock) throws SQLException {
        return queryOne(connection, "alert_id = ?", statement -> statement.setBytes(1, UuidBytes.toBytes(alertId)), lock);
    }

    private Current byOperation(Connection connection, String operationKey, boolean lock) throws SQLException {
        return queryOne(connection, "operation_key = ?", statement -> statement.setString(1, operationKey), lock);
    }

    private Current queryOne(Connection connection, String predicate, Binder binder, boolean lock) throws SQLException {
        String suffix = lock ? " FOR UPDATE" : "";
        String sql = "SELECT * FROM discord_evasion_alerts WHERE " + predicate + suffix;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            binder.bind(statement);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? read(rows) : null;
            }
        }
    }

    private static Current read(ResultSet rows) throws SQLException {
        return new Current(
                UuidBytes.fromBytes(rows.getBytes("alert_id")),
                rows.getString("operation_key"),
                new ModerationSubjectId(UuidBytes.fromBytes(rows.getBytes("subject_id"))),
                UuidBytes.fromBytes(rows.getBytes("punishment_id")),
                UuidBytes.fromBytes(rows.getBytes("triggering_minecraft_player_id")),
                rows.getString("current_server"),
                rows.getLong("player_revision"),
                EvasionAlert.State.valueOf(rows.getString("state")),
                EvasionAlert.DeliveryState.valueOf(rows.getString("discord_delivery")),
                EvasionAlert.DeliveryState.valueOf(rows.getString("minecraft_delivery")),
                rows.getInt("discord_attempts"),
                rows.getInt("minecraft_attempts"),
                Optional.ofNullable(rows.getString("discord_error_code")),
                Optional.ofNullable(rows.getString("minecraft_error_code")),
                optionalInstant(rows.getTimestamp("discord_next_attempt_at")),
                optionalInstant(rows.getTimestamp("minecraft_next_attempt_at")),
                rows.getTimestamp("created_at").toInstant(),
                rows.getTimestamp("updated_at").toInstant(),
                rows.getLong("revision")
        );
    }

    private static Optional<Instant> optionalInstant(Timestamp timestamp) {
        return timestamp == null ? Optional.empty() : Optional.of(timestamp.toInstant());
    }

    private static void requireReplay(Current current, EvasionAlertDraft draft) throws SQLException {
        if (!current.alertId().equals(draft.alertId()) || !current.subjectId().equals(draft.subjectId())
                || !current.punishmentId().equals(draft.punishmentId())
                || !current.triggeringPlayer().equals(draft.triggeringMinecraftPlayerId())) {
            throw new SQLException("linked-alt alert operation key was reused for a different request");
        }
    }

    private static void setError(PreparedStatement statement, int index, Optional<String> errorCode) throws SQLException {
        if (errorCode.isPresent()) {
            String value = errorCode.orElseThrow();
            statement.setString(index, value.length() <= 96 ? value : value.substring(0, 96));
        } else {
            statement.setNull(index, Types.VARCHAR);
        }
    }

    private static void setInstant(PreparedStatement statement, int index, Optional<Instant> value) throws SQLException {
        if (value.isPresent()) {
            statement.setTimestamp(index, Timestamp.from(value.orElseThrow()));
        } else {
            statement.setNull(index, Types.TIMESTAMP);
        }
    }

    @FunctionalInterface
    private interface Binder {
        void bind(PreparedStatement statement) throws SQLException;
    }

    private record Current(
            UUID alertId,
            String operationKey,
            ModerationSubjectId subjectId,
            UUID punishmentId,
            UUID triggeringPlayer,
            String currentServer,
            long playerRevision,
            EvasionAlert.State state,
            EvasionAlert.DeliveryState discordDelivery,
            EvasionAlert.DeliveryState minecraftDelivery,
            int discordAttempts,
            int minecraftAttempts,
            Optional<String> discordErrorCode,
            Optional<String> minecraftErrorCode,
            Optional<Instant> discordNextAttemptAt,
            Optional<Instant> minecraftNextAttemptAt,
            Instant createdAt,
            Instant updatedAt,
            long revision
    ) {
        EvasionAlert toDomain(boolean replayed) {
            return new EvasionAlert(
                    alertId, operationKey, subjectId, punishmentId, triggeringPlayer, currentServer,
                    playerRevision, state, discordDelivery, minecraftDelivery, discordAttempts,
                    minecraftAttempts, discordErrorCode, minecraftErrorCode, discordNextAttemptAt,
                    minecraftNextAttemptAt, createdAt, updatedAt, revision, replayed
            );
        }
    }
}
