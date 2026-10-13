package net.enthusia.staff.persistence;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import java.util.UUID;

/**
 * Snapshots already-verified current Minecraft/Discord links for a private punishment card.
 * This projection does not mutate account links and never emits historical/unlinked IDs.
 */
final class JdbcPunishmentDiscordIdentity {
    private static final String LOOKUP = """
            SELECT p.current_username, link.discord_user_id
            FROM players p
            LEFT JOIN discord_minecraft_links link
                ON link.minecraft_player_id = p.player_id
                AND link.unlinked_at IS NULL
            WHERE p.player_id = ?
            LIMIT 1
            """;

    private JdbcPunishmentDiscordIdentity() {
    }

    static void appendVerifiedCurrentIdentity(Connection connection, UUID targetId,
            Map<String, Object> discordPayload) {
        try (PreparedStatement query = connection.prepareStatement(LOOKUP)) {
            query.setBytes(1, UuidBytes.toBytes(targetId));
            try (ResultSet row = query.executeQuery()) {
                if (!row.next()) {
                    return;
                }
                String username = row.getString("current_username");
                if (safeMinecraftName(username)) {
                    discordPayload.put("targetUsername", username);
                }
                String id = row.getString("discord_user_id");
                if (safeDiscordId(id)) {
                    discordPayload.put("targetDiscordUserId", id);
                }
            }
        } catch (SQLException ignored) {
            // Identity is optional presentation. Never roll back a punishment because
            // the legacy/migration link projection is temporarily unavailable.
        }
    }

    static boolean safeMinecraftName(String value) {
        return value != null && value.matches("[A-Za-z0-9_ .-]{1,32}");
    }

    static boolean safeDiscordId(String value) {
        if (value == null || !value.matches("[1-9][0-9]{15,19}")) {
            return false;
        }
        try {
            Long.parseUnsignedLong(value);
            return true;
        } catch (NumberFormatException invalid) {
            return false;
        }
    }
}
