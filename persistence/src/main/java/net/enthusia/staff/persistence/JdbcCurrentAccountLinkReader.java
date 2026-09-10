package net.enthusia.staff.persistence;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;
import net.enthusia.staff.domain.moderation.CurrentLinkedMinecraftAccount;

/**
 * Bounded current-link projection for staff review. It never selects Discord identifiers or
 * historical account-link rows.
 */
final class JdbcCurrentAccountLinkReader {
    private static final int MAXIMUM_LIMIT = 100;

    private JdbcCurrentAccountLinkReader() {
    }

    static List<CurrentLinkedMinecraftAccount> currentLinkedMinecraftAccounts(
            DataSource dataSource,
            UUID minecraftPlayerId,
            int limit
    ) {
        if (dataSource == null || minecraftPlayerId == null || limit < 1 || limit > MAXIMUM_LIMIT) {
            throw new IllegalArgumentException("current account-link query arguments are invalid");
        }
        return JdbcTransactionSupport.execute(dataSource, "Unable to read current linked Minecraft accounts", connection -> {
            try (PreparedStatement statement = connection.prepareStatement("""
                    SELECT related.minecraft_player_id, player.current_username, related.linked_at
                    FROM discord_minecraft_links target
                    JOIN discord_minecraft_links related
                      ON related.discord_user_id = target.discord_user_id
                    JOIN players player ON player.player_id = related.minecraft_player_id
                    WHERE target.minecraft_player_id = ?
                      AND target.unlinked_at IS NULL
                      AND related.unlinked_at IS NULL
                      AND related.minecraft_player_id <> ?
                    ORDER BY related.linked_at ASC, related.minecraft_player_id ASC
                    LIMIT ?
                    """)) {
                statement.setBytes(1, UuidBytes.toBytes(minecraftPlayerId));
                statement.setBytes(2, UuidBytes.toBytes(minecraftPlayerId));
                statement.setInt(3, limit);
                return readAccounts(statement);
            }
        });
    }

    private static List<CurrentLinkedMinecraftAccount> readAccounts(PreparedStatement statement) throws SQLException {
        try (ResultSet result = statement.executeQuery()) {
            List<CurrentLinkedMinecraftAccount> accounts = new ArrayList<>();
            while (result.next()) {
                accounts.add(new CurrentLinkedMinecraftAccount(
                        UuidBytes.fromBytes(result.getBytes("minecraft_player_id")),
                        Optional.ofNullable(result.getString("current_username")),
                        result.getTimestamp("linked_at").toInstant()
                ));
            }
            return List.copyOf(accounts);
        }
    }
}
