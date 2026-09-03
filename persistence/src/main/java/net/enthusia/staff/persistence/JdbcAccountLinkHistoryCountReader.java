package net.enthusia.staff.persistence;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import javax.sql.DataSource;
import net.enthusia.staff.domain.moderation.DiscordUserId;

/** Bounded count-only account-link history query for read-only Discord panels. */
final class JdbcAccountLinkHistoryCountReader {
    private JdbcAccountLinkHistoryCountReader() {
    }

    static long countForDiscord(DataSource dataSource, DiscordUserId discordUserId) {
        return JdbcTransactionSupport.execute(dataSource, "Unable to count account-link history", connection -> {
            try (PreparedStatement statement = connection.prepareStatement("""
                    SELECT COUNT(*) AS link_count
                    FROM discord_minecraft_links
                    WHERE discord_user_id = ?
                    """)) {
                statement.setBigDecimal(1, new BigDecimal(discordUserId.value()));
                try (ResultSet result = statement.executeQuery()) {
                    if (!result.next()) {
                        throw new SQLException("Account-link count query returned no row");
                    }
                    return result.getLong("link_count");
                }
            }
        });
    }
}
