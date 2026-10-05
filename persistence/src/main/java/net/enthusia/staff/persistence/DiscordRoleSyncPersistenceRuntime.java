package net.enthusia.staff.persistence;

import com.zaxxer.hikari.HikariDataSource;
import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.enthusia.staff.domain.moderation.DiscordUserId;
import net.enthusia.staff.domain.ports.DiscordModerationPersistenceStore.ReconciliationState;
import net.enthusia.staff.domain.ports.DiscordModerationPersistenceStore.VersionedSubject;

/** Narrow D13 runtime for current-link scans and durable role-reconciliation state. */
public final class DiscordRoleSyncPersistenceRuntime implements AutoCloseable {
    private static final int MAX_BATCH = 100;
    private static final int WRITE_POOL_SIZE = 2;

    private final HikariDataSource dataSource;
    private final JdbcDiscordModerationPersistenceStore store;

    private DiscordRoleSyncPersistenceRuntime(HikariDataSource dataSource) {
        this.dataSource = dataSource;
        this.store = new JdbcDiscordModerationPersistenceStore(dataSource);
    }

    public static DiscordRoleSyncPersistenceRuntime open(DatabaseConfig database) {
        if (database == null) {
            throw new IllegalArgumentException("database must be present");
        }
        DatabaseConfig bounded = new DatabaseConfig(
                database.jdbcUrl(),
                database.username(),
                database.password(),
                WRITE_POOL_SIZE,
                database.connectionTimeoutMillis()
        );
        return new DiscordRoleSyncPersistenceRuntime(MariaDb.open(bounded));
    }

    public List<DiscordUserId> discordUsersAfter(Optional<DiscordUserId> cursor, int limit) {
        if (cursor == null || limit < 1 || limit > MAX_BATCH) {
            throw new IllegalArgumentException("role-sync cursor and batch limit are invalid");
        }
        String sql = cursor.isPresent()
                ? "SELECT discord_user_id FROM moderation_subject_discord_identities "
                        + "WHERE discord_user_id > ? ORDER BY discord_user_id LIMIT ?"
                : "SELECT discord_user_id FROM moderation_subject_discord_identities "
                        + "ORDER BY discord_user_id LIMIT ?";
        try (var connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            bindScan(statement, cursor, limit);
            return readDiscordUsers(statement);
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to scan Discord role-sync subjects", exception);
        }
    }

    public Optional<VersionedSubject> subjectForDiscord(DiscordUserId userId) {
        if (userId == null) {
            throw new IllegalArgumentException("Discord user ID must be present");
        }
        return store.subjectForDiscord(userId);
    }

    public Optional<ReconciliationState> reconciliation(String key) {
        if (key == null || key.isBlank() || key.length() > 160) {
            throw new IllegalArgumentException("reconciliation key is invalid");
        }
        try (var connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT reconciliation_key, resource_type, resource_id, desired_state_json,
                            observed_state_json, state, attempt_count, next_attempt_at,
                            last_error_code, revision
                     FROM discord_reconciliation_state
                     WHERE reconciliation_key = ?
                     """)) {
            statement.setString(1, key);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? Optional.of(readReconciliation(rows)) : Optional.empty();
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to read Discord role reconciliation state", exception);
        }
    }

    public ReconciliationState saveReconciliation(
            ReconciliationState state,
            long expectedRevision,
            Instant now
    ) {
        return store.saveReconciliation(state, expectedRevision, now);
    }

    private static void bindScan(
            PreparedStatement statement,
            Optional<DiscordUserId> cursor,
            int limit
    ) throws SQLException {
        if (cursor.isPresent()) {
            statement.setBigDecimal(1, new BigDecimal(cursor.orElseThrow().value()));
            statement.setInt(2, limit);
        } else {
            statement.setInt(1, limit);
        }
    }

    private static List<DiscordUserId> readDiscordUsers(PreparedStatement statement) throws SQLException {
        List<DiscordUserId> users = new ArrayList<>();
        try (ResultSet rows = statement.executeQuery()) {
            while (rows.next()) {
                users.add(new DiscordUserId(rows.getBigDecimal(1).toPlainString()));
            }
        }
        return List.copyOf(users);
    }

    private static ReconciliationState readReconciliation(ResultSet rows) throws SQLException {
        Timestamp nextAttempt = rows.getTimestamp("next_attempt_at");
        return new ReconciliationState(
                rows.getString("reconciliation_key"),
                rows.getString("resource_type"),
                rows.getString("resource_id"),
                rows.getString("desired_state_json"),
                Optional.ofNullable(rows.getString("observed_state_json")),
                rows.getString("state"),
                rows.getInt("attempt_count"),
                Optional.ofNullable(nextAttempt).map(Timestamp::toInstant),
                Optional.ofNullable(rows.getString("last_error_code")),
                rows.getLong("revision")
        );
    }

    @Override
    public void close() {
        dataSource.close();
    }
}
