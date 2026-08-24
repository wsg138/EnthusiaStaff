package net.enthusia.staff.persistence;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;
import net.enthusia.staff.domain.auth.Actor;
import net.enthusia.staff.domain.auth.StaffRank;
import net.enthusia.staff.domain.moderation.AccountLinkAudit;
import net.enthusia.staff.domain.moderation.AccountLinkAuditAction;
import net.enthusia.staff.domain.moderation.DiscordUserId;
import net.enthusia.staff.domain.ports.AccountLinkAuditStore;

public final class JdbcAccountLinkAuditStore implements AccountLinkAuditStore {
    private final DataSource dataSource;

    public JdbcAccountLinkAuditStore(DataSource dataSource) {
        if (dataSource == null) {
            throw new IllegalArgumentException("dataSource must be present");
        }
        this.dataSource = dataSource;
    }

    @Override
    public boolean append(AccountLinkAudit audit) {
        if (audit == null) {
            throw new IllegalArgumentException("audit must be present");
        }
        return JdbcTransactionSupport.execute(dataSource, "Unable to append account-link audit", connection -> {
            AccountLinkAudit existing = find(connection, audit.operationKey(), true);
            if (existing != null) {
                if (!same(existing, audit)) {
                    throw new SQLException("account-link audit operation key was reused for a different event");
                }
                return false;
            }
            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO discord_link_audit(
                        audit_id, operation_key, actor_id, actor_name, actor_rank, action,
                        discord_user_id, minecraft_player_id, detail, created_at
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """)) {
                statement.setBytes(1, UuidBytes.toBytes(UUID.randomUUID()));
                statement.setString(2, audit.operationKey());
                statement.setBytes(3, UuidBytes.toBytes(audit.actor().id()));
                statement.setString(4, audit.actor().displayName());
                statement.setString(5, audit.actor().rank().name());
                statement.setString(6, audit.action().name());
                if (audit.discordUserId().isPresent()) {
                    statement.setBigDecimal(7, new BigDecimal(audit.discordUserId().orElseThrow().value()));
                } else {
                    statement.setNull(7, java.sql.Types.DECIMAL);
                }
                if (audit.minecraftPlayerId().isPresent()) {
                    statement.setBytes(8, UuidBytes.toBytes(audit.minecraftPlayerId().orElseThrow()));
                } else {
                    statement.setNull(8, java.sql.Types.BINARY);
                }
                statement.setString(9, audit.detail());
                statement.setTimestamp(10, Timestamp.from(audit.createdAt()));
                JdbcTransactionSupport.requireSingleUpdate(statement.executeUpdate(), "account-link audit was not inserted");
            }
            return true;
        });
    }

    @Override
    public Optional<AccountLinkAudit> findByOperationKey(String operationKey) {
        if (operationKey == null || operationKey.isBlank()) {
            throw new IllegalArgumentException("operationKey must be present");
        }
        return JdbcTransactionSupport.execute(dataSource, "Unable to read account-link audit", connection ->
                Optional.ofNullable(find(connection, operationKey, false)));
    }

    private static AccountLinkAudit find(java.sql.Connection connection, String operationKey, boolean lock)
            throws SQLException {
        String sql = "SELECT operation_key, actor_id, actor_name, actor_rank, action, discord_user_id, "
                + "minecraft_player_id, detail, created_at FROM discord_link_audit WHERE operation_key = ?"
                + (lock ? " FOR UPDATE" : "");
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, operationKey);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    return null;
                }
                BigDecimal discord = result.getBigDecimal("discord_user_id");
                byte[] minecraft = result.getBytes("minecraft_player_id");
                return new AccountLinkAudit(
                        result.getString("operation_key"),
                        new Actor(
                                UuidBytes.fromBytes(result.getBytes("actor_id")),
                                result.getString("actor_name"),
                                StaffRank.valueOf(result.getString("actor_rank"))
                        ),
                        AccountLinkAuditAction.valueOf(result.getString("action")),
                        discord == null ? Optional.empty() : Optional.of(new DiscordUserId(
                                discord.toBigIntegerExact().toString())),
                        minecraft == null ? Optional.empty() : Optional.of(UuidBytes.fromBytes(minecraft)),
                        result.getString("detail"),
                        result.getTimestamp("created_at").toInstant()
                );
            }
        }
    }

    private static boolean same(AccountLinkAudit left, AccountLinkAudit right) {
        return left.operationKey().equals(right.operationKey())
                && left.actor().equals(right.actor())
                && left.action() == right.action()
                && left.discordUserId().equals(right.discordUserId())
                && left.minecraftPlayerId().equals(right.minecraftPlayerId())
                && left.detail().equals(right.detail());
    }
}
