package net.enthusia.staff.persistence;

import com.zaxxer.hikari.HikariDataSource;
import net.enthusia.staff.domain.ports.DiscordPunishmentRepository;

/**
 * Narrow read/write D07 runtime for the isolated staff bot.
 *
 * <p>This opens the configured pool but deliberately does not run Flyway. Schema ownership remains
 * with the normal EnthusiaStaff deployment lifecycle; the staff bot fails if V19 is unavailable.</p>
 */
public final class DiscordPunishmentPersistenceRuntime implements AutoCloseable {
    private final HikariDataSource dataSource;
    private final JdbcDiscordPunishmentRepository punishments;

    private DiscordPunishmentPersistenceRuntime(HikariDataSource dataSource) {
        this.dataSource = dataSource;
        this.punishments = new JdbcDiscordPunishmentRepository(dataSource);
    }

    public static DiscordPunishmentPersistenceRuntime open(DatabaseConfig database) {
        if (database == null) {
            throw new IllegalArgumentException("database must be present");
        }
        return new DiscordPunishmentPersistenceRuntime(MariaDb.open(database));
    }

    public DiscordPunishmentRepository punishments() {
        return punishments;
    }

    @Override
    public void close() {
        dataSource.close();
    }
}
