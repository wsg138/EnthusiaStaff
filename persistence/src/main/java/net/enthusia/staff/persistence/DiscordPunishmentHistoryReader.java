package net.enthusia.staff.persistence;

import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;
import net.enthusia.staff.domain.discord.DiscordPunishment;
import net.enthusia.staff.domain.moderation.DiscordGuildId;
import net.enthusia.staff.domain.moderation.DiscordUserId;

/** Bounded read-only projection of durable Discord records, scoped to one guild and user. */
public final class DiscordPunishmentHistoryReader {
    public record Page(List<DiscordPunishment> records, long total) {
        public Page {
            records = List.copyOf(records);
            if (total < records.size()) throw new IllegalArgumentException("invalid history count");
        }
    }

    private final DataSource dataSource;
    private final DiscordPunishmentJsonCodec codec = new DiscordPunishmentJsonCodec();

    public DiscordPunishmentHistoryReader(DataSource dataSource) {
        this.dataSource = java.util.Objects.requireNonNull(dataSource);
    }

    public Page recent(DiscordGuildId guild, DiscordUserId user, int limit) {
        if (guild == null || user == null || limit < 1 || limit > 50) {
            throw new IllegalArgumentException("invalid Discord history query");
        }
        return JdbcTransactionSupport.execute(dataSource, "Unable to read Discord punishment history", connection -> {
            String predicate = """
                    FROM discord_reconciliation_state
                    WHERE resource_type = 'D07_DISCORD_PUNISHMENT'
                      AND JSON_UNQUOTE(JSON_EXTRACT(desired_state_json, '$.guildId')) = ?
                      AND JSON_UNQUOTE(JSON_EXTRACT(desired_state_json, '$.targetUserId')) = ?
                    """;
            long total;
            try (PreparedStatement count = connection.prepareStatement("SELECT COUNT(*) " + predicate)) {
                bind(count, guild, user);
                try (var rows = count.executeQuery()) {
                    rows.next();
                    total = rows.getLong(1);
                }
            }
            List<DiscordPunishment> records = new ArrayList<>();
            try (PreparedStatement query = connection.prepareStatement("SELECT desired_state_json " + predicate
                    + " ORDER BY created_at DESC, resource_id DESC LIMIT ?")) {
                bind(query, guild, user);
                query.setInt(3, limit);
                try (var rows = query.executeQuery()) {
                    while (rows.next()) records.add(codec.decode(rows.getString(1)));
                }
            }
            return new Page(records, Math.max(total, records.size()));
        });
    }

    private static void bind(PreparedStatement statement, DiscordGuildId guild, DiscordUserId user)
            throws java.sql.SQLException {
        statement.setString(1, guild.value());
        statement.setString(2, user.value());
    }
}
