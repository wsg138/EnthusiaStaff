package net.enthusia.staff.persistence;

import java.math.BigDecimal;
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
import net.enthusia.staff.domain.auth.DiscordConsequenceType;
import net.enthusia.staff.domain.discord.DiscordPunishmentState;
import net.enthusia.staff.domain.investigation.EvasionAlert;
import net.enthusia.staff.domain.moderation.DiscordUserId;
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
                    alert_id, operation_key, subject_id, punishment_id, target_discord_user_id,
                    punishment_type, punishment_summary, punishment_state, punishment_expires_at,
                    triggering_minecraft_player_id, triggering_minecraft_username, current_server,
                    player_revision, trigger_type, triggered_at, state, discord_delivery, minecraft_delivery,
                    discord_attempts, minecraft_attempts, discord_error_code, minecraft_error_code,
                    discord_next_attempt_at, minecraft_next_attempt_at, created_at, updated_at, resolved_at, revision
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?,
                    'OPEN', 'PENDING', 'PENDING', 0, 0, NULL, NULL, ?, ?, ?, ?, NULL, 0)
                """)) {
            EvasionAlert.Context context = draft.context();
            statement.setBytes(1, UuidBytes.toBytes(draft.alertId()));
            statement.setString(2, draft.operationKey());
            bindContext(statement, context);
            statement.setTimestamp(16, Timestamp.from(draft.now()));
            statement.setTimestamp(17, Timestamp.from(draft.now()));
            statement.setTimestamp(18, Timestamp.from(draft.now()));
            statement.setTimestamp(19, Timestamp.from(draft.now()));
            JdbcTransactionSupport.requireSingleUpdate(statement.executeUpdate(), "linked-alt alert was not inserted");
        }
    }

    private static void bindContext(PreparedStatement statement, EvasionAlert.Context context) throws SQLException {
        statement.setBytes(3, UuidBytes.toBytes(context.subjectId().value()));
        statement.setBytes(4, UuidBytes.toBytes(context.punishmentId()));
        statement.setBigDecimal(5, new BigDecimal(context.targetDiscordUserId().value()));
        statement.setString(6, context.punishmentType().name());
        statement.setString(7, context.punishmentSummary());
        statement.setString(8, context.punishmentState().name());
        setInstant(statement, 9, context.punishmentExpiresAt());
        statement.setBytes(10, UuidBytes.toBytes(context.triggeringMinecraftPlayerId()));
        if (context.triggeringMinecraftUsername().isPresent()) {
            statement.setString(11, context.triggeringMinecraftUsername().orElseThrow());
        } else {
            statement.setNull(11, Types.VARCHAR);
        }
        statement.setString(12, context.currentServer());
        statement.setLong(13, context.playerRevision());
        statement.setString(14, context.triggerType().name());
        statement.setTimestamp(15, Timestamp.from(context.triggeredAt()));
    }

    private static void updateDelivery(
            Connection connection,
            Current current,
            EvasionDeliveryUpdate update
    ) throws SQLException {
        if (update.channel() == EvasionDeliveryChannel.DISCORD) {
            updateDiscordDelivery(connection, current, update);
        } else {
            updateMinecraftDelivery(connection, current, update);
        }
    }

    private static void updateDiscordDelivery(
            Connection connection,
            Current current,
            EvasionDeliveryUpdate update
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE discord_evasion_alerts
                SET discord_delivery = ?, discord_attempts = discord_attempts + 1,
                    discord_error_code = ?, discord_next_attempt_at = ?,
                    updated_at = ?, revision = revision + 1
                WHERE alert_id = ? AND revision = ? AND state = 'OPEN'
                """)) {
            bindDeliveryUpdate(statement, current, update);
        }
    }

    private static void updateMinecraftDelivery(
            Connection connection,
            Current current,
            EvasionDeliveryUpdate update
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE discord_evasion_alerts
                SET minecraft_delivery = ?, minecraft_attempts = minecraft_attempts + 1,
                    minecraft_error_code = ?, minecraft_next_attempt_at = ?,
                    updated_at = ?, revision = revision + 1
                WHERE alert_id = ? AND revision = ? AND state = 'OPEN'
                """)) {
            bindDeliveryUpdate(statement, current, update);
        }
    }

    private static void bindDeliveryUpdate(
            PreparedStatement statement,
            Current current,
            EvasionDeliveryUpdate update
    ) throws SQLException {
        statement.setString(1, update.delivered() ? EvasionAlert.DeliveryState.DELIVERED.name()
                : EvasionAlert.DeliveryState.RETRY.name());
        setError(statement, 2, update.errorCode());
        setInstant(statement, 3, update.nextAttemptAt());
        statement.setTimestamp(4, Timestamp.from(update.now()));
        statement.setBytes(5, UuidBytes.toBytes(update.alertId()));
        statement.setLong(6, current.revision());
        JdbcTransactionSupport.requireSingleUpdate(statement.executeUpdate(), "linked-alt delivery revision changed");
    }

    private Current requireById(Connection connection, UUID alertId, boolean lock) throws SQLException {
        Current current = byId(connection, alertId, lock);
        if (current == null) {
            throw new SQLException("linked-alt alert does not exist");
        }
        return current;
    }

    private Current byId(Connection connection, UUID alertId, boolean lock) throws SQLException {
        if (lock) {
            try (PreparedStatement statement = connection.prepareStatement("""
                    SELECT * FROM discord_evasion_alerts WHERE alert_id = ? FOR UPDATE
                    """)) {
                statement.setBytes(1, UuidBytes.toBytes(alertId));
                return readOne(statement);
            }
        }
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT * FROM discord_evasion_alerts WHERE alert_id = ?
                """)) {
            statement.setBytes(1, UuidBytes.toBytes(alertId));
            return readOne(statement);
        }
    }

    private Current byOperation(Connection connection, String operationKey, boolean lock) throws SQLException {
        if (lock) {
            try (PreparedStatement statement = connection.prepareStatement("""
                    SELECT * FROM discord_evasion_alerts WHERE operation_key = ? FOR UPDATE
                    """)) {
                statement.setString(1, operationKey);
                return readOne(statement);
            }
        }
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT * FROM discord_evasion_alerts WHERE operation_key = ?
                """)) {
            statement.setString(1, operationKey);
            return readOne(statement);
        }
    }

    private static Current readOne(PreparedStatement statement) throws SQLException {
        try (ResultSet rows = statement.executeQuery()) {
            return rows.next() ? read(rows) : null;
        }
    }

    private static Current read(ResultSet rows) throws SQLException {
        return new Current(
                UuidBytes.fromBytes(rows.getBytes("alert_id")),
                rows.getString("operation_key"),
                readContext(rows),
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

    private static EvasionAlert.Context readContext(ResultSet rows) throws SQLException {
        return new EvasionAlert.Context(
                new ModerationSubjectId(UuidBytes.fromBytes(rows.getBytes("subject_id"))),
                UuidBytes.fromBytes(rows.getBytes("punishment_id")),
                new DiscordUserId(rows.getString("target_discord_user_id")),
                DiscordConsequenceType.valueOf(rows.getString("punishment_type")),
                rows.getString("punishment_summary"),
                DiscordPunishmentState.valueOf(rows.getString("punishment_state")),
                optionalInstant(rows.getTimestamp("punishment_expires_at")),
                UuidBytes.fromBytes(rows.getBytes("triggering_minecraft_player_id")),
                Optional.ofNullable(rows.getString("triggering_minecraft_username")),
                rows.getString("current_server"),
                rows.getLong("player_revision"),
                EvasionAlert.TriggerType.valueOf(rows.getString("trigger_type")),
                rows.getTimestamp("triggered_at").toInstant()
        );
    }

    private static Optional<Instant> optionalInstant(Timestamp timestamp) {
        return timestamp == null ? Optional.empty() : Optional.of(timestamp.toInstant());
    }

    private static void requireReplay(Current current, EvasionAlertDraft draft) throws SQLException {
        if (!current.alertId().equals(draft.alertId()) || !current.context().equals(draft.context())) {
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

    private record Current(
            UUID alertId,
            String operationKey,
            EvasionAlert.Context context,
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
                    alertId, operationKey, context, state, discordDelivery, minecraftDelivery, discordAttempts,
                    minecraftAttempts, discordErrorCode, minecraftErrorCode, discordNextAttemptAt,
                    minecraftNextAttemptAt, createdAt, updatedAt, revision, replayed
            );
        }
    }
}
