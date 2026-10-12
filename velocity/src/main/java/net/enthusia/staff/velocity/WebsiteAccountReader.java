package net.enthusia.staff.velocity;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import java.time.Clock;
import java.util.function.Function;
import net.enthusia.staff.domain.application.AccountLinkingService.IssuedCode;
import net.enthusia.staff.domain.moderation.DiscordUserId;
import net.enthusia.staff.persistence.UuidBytes;

/** Current account ownership only, behind the website API's signed service boundary. */
final class WebsiteAccountReader {
    private static final int MAX_ACCOUNTS = 100;
    private final DataSource dataSource;
    private final Clock clock;
    private final Function<DiscordUserId, IssuedCode> issuer;

    WebsiteAccountReader(DataSource dataSource, Clock clock, Function<DiscordUserId, IssuedCode> issuer) {
        this.dataSource = java.util.Objects.requireNonNull(dataSource);
        this.clock = java.util.Objects.requireNonNull(clock);
        this.issuer = java.util.Objects.requireNonNull(issuer);
    }

    Map<String, Object> start(String rawDiscordId) {
        IssuedCode issued = issuer.apply(new DiscordUserId(rawDiscordId));
        return Map.of("status", "WAITING_FOR_MINECRAFT", "code", issued.code(),
                "requestId", issued.requestId(), "expiresAt", issued.expiresAt().toString(),
                "command", "/link " + issued.code());
    }

    Map<String, Object> status(String rawDiscordId, String requestId) {
        String discordId = new DiscordUserId(rawDiscordId).value();
        if (requestId == null || !requestId.matches("[0-9a-f]{64}")) {
            throw new WebsiteApiException(400, "INVALID_LINK_REQUEST", "Link request is invalid");
        }
        try (var connection = dataSource.getConnection();
                var statement = connection.prepareStatement("""
                    SELECT code.state, code.expires_at, link.minecraft_player_id, player.current_username
                    FROM discord_link_codes code
                    LEFT JOIN discord_minecraft_links link
                      ON link.operation_key = code.consumed_operation_key
                     AND link.discord_user_id = code.initiator_discord_user_id AND link.unlinked_at IS NULL
                    LEFT JOIN players player ON player.player_id = link.minecraft_player_id
                    WHERE code.code_hash = ? AND code.initiator_discord_user_id = ?
                      AND code.direction = 'DISCORD_TO_MINECRAFT'
                    """)) {
            statement.setString(1, requestId);
            statement.setString(2, discordId);
            try (var rows = statement.executeQuery()) {
                if (!rows.next()) return Map.of("status", "EXPIRED_OR_USED");
                if ("CONSUMED".equals(rows.getString("state")) && rows.getBytes("minecraft_player_id") != null) {
                    return Map.of("status", "LINKED", "minecraft", Map.of(
                            "uuid", UuidBytes.fromBytes(rows.getBytes("minecraft_player_id")).toString(),
                            "name", rows.getString("current_username")));
                }
                if (!"ACTIVE".equals(rows.getString("state"))
                        || !rows.getTimestamp("expires_at").toInstant().isAfter(clock.instant())) {
                    return Map.of("status", "EXPIRED_OR_USED");
                }
                return Map.of("status", "WAITING_FOR_MINECRAFT", "expiresAt", rows.getTimestamp("expires_at").toInstant().toString());
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Authoritative link status is unavailable", exception);
        }
    }

    Map<String, Object> read(String rawDiscordId) {
        String discordId = new DiscordUserId(rawDiscordId).value();
        try (var connection = dataSource.getConnection();
                var statement = connection.prepareStatement("""
                    SELECT link.minecraft_player_id, player.current_username, link.linked_at
                    FROM discord_minecraft_links link
                    JOIN players player ON player.player_id = link.minecraft_player_id
                    LEFT JOIN moderation_subject_main_accounts main ON main.subject_id = link.subject_id
                    WHERE link.discord_user_id = ? AND link.unlinked_at IS NULL
                    ORDER BY (main.player_id = link.minecraft_player_id) DESC, link.linked_at ASC, link.minecraft_player_id ASC
                    LIMIT 101
                    """)) {
            statement.setString(1, discordId);
            try (var rows = statement.executeQuery()) {
                List<Map<String, String>> accounts = new ArrayList<>();
                while (rows.next()) {
                    if (accounts.size() >= MAX_ACCOUNTS) {
                        throw new WebsiteApiException(503, "ACCOUNT_LIMIT_EXCEEDED", "Account list exceeds the supported limit");
                    }
                    accounts.add(Map.of(
                            "uuid", UuidBytes.fromBytes(rows.getBytes("minecraft_player_id")).toString(),
                            "name", rows.getString("current_username"),
                            "linkedAt", rows.getTimestamp("linked_at").toInstant().toString()
                    ));
                }
                return Map.of("accounts", List.copyOf(accounts));
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Authoritative account links are unavailable", exception);
        }
    }
}
