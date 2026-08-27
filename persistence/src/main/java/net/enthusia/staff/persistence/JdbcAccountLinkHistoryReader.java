package net.enthusia.staff.persistence;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;
import net.enthusia.staff.domain.moderation.DiscordMinecraftLink;
import net.enthusia.staff.domain.moderation.DiscordMinecraftLinkSource;
import net.enthusia.staff.domain.moderation.DiscordUserId;
import net.enthusia.staff.domain.moderation.ModerationSubjectId;
import net.enthusia.staff.domain.ports.DiscordModerationPersistenceStore.VersionedLink;

/** Read-only account-link history queries, kept separate from mutation transaction ownership. */
final class JdbcAccountLinkHistoryReader {
    private JdbcAccountLinkHistoryReader() {
    }

    static List<VersionedLink> historyForMinecraft(DataSource dataSource, UUID minecraftPlayerId) {
        return JdbcTransactionSupport.execute(dataSource, "Unable to read account-link history", connection -> {
            try (PreparedStatement statement = connection.prepareStatement("""
                    SELECT link_id, subject_id, discord_user_id, minecraft_player_id, linked_at,
                           unlinked_at, source, revision
                    FROM discord_minecraft_links
                    WHERE minecraft_player_id = ?
                    ORDER BY linked_at DESC, link_id DESC
                    """)) {
                statement.setBytes(1, UuidBytes.toBytes(minecraftPlayerId));
                return readHistory(statement);
            }
        });
    }

    static List<VersionedLink> historyForDiscord(DataSource dataSource, DiscordUserId discordUserId) {
        return JdbcTransactionSupport.execute(dataSource, "Unable to read account-link history", connection -> {
            try (PreparedStatement statement = connection.prepareStatement("""
                    SELECT link_id, subject_id, discord_user_id, minecraft_player_id, linked_at,
                           unlinked_at, source, revision
                    FROM discord_minecraft_links
                    WHERE discord_user_id = ?
                    ORDER BY linked_at DESC, link_id DESC
                    """)) {
                statement.setBigDecimal(1, discordId(discordUserId));
                return readHistory(statement);
            }
        });
    }

    private static List<VersionedLink> readHistory(PreparedStatement statement) throws SQLException {
        try (ResultSet result = statement.executeQuery()) {
            List<VersionedLink> links = new ArrayList<>();
            while (result.next()) {
                links.add(readLink(result));
            }
            return List.copyOf(links);
        }
    }

    private static VersionedLink readLink(ResultSet result) throws SQLException {
        Timestamp unlinked = result.getTimestamp("unlinked_at");
        return new VersionedLink(
                UuidBytes.fromBytes(result.getBytes("link_id")),
                new ModerationSubjectId(UuidBytes.fromBytes(result.getBytes("subject_id"))),
                new DiscordMinecraftLink(
                        discordUserId(result.getBigDecimal("discord_user_id")),
                        UuidBytes.fromBytes(result.getBytes("minecraft_player_id")),
                        result.getTimestamp("linked_at").toInstant(),
                        unlinked == null ? Optional.empty() : Optional.of(unlinked.toInstant()),
                        DiscordMinecraftLinkSource.valueOf(result.getString("source"))
                ),
                result.getLong("revision"),
                false
        );
    }

    private static BigDecimal discordId(DiscordUserId userId) {
        return new BigDecimal(userId.value());
    }

    private static DiscordUserId discordUserId(BigDecimal value) {
        return new DiscordUserId(value.toBigIntegerExact().toString());
    }
}
