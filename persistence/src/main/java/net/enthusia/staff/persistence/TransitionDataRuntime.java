package net.enthusia.staff.persistence;

import com.zaxxer.hikari.HikariDataSource;
import net.enthusia.staff.domain.ports.DiscordModerationPersistenceStore;
import net.enthusia.staff.domain.ports.PlayerDirectory;

/** Narrow write-capable runtime used only to seed transition observation data. */
public final class TransitionDataRuntime implements AutoCloseable {
    private final HikariDataSource dataSource;
    private final PlayerDirectory players;
    private final DiscordModerationPersistenceStore identities;

    private TransitionDataRuntime(HikariDataSource dataSource) {
        this.dataSource = dataSource;
        this.players = new JdbcPlayerDirectory(dataSource);
        this.identities = new JdbcDiscordModerationPersistenceStore(dataSource);
    }

    public static TransitionDataRuntime open(DatabaseConfig database) {
        if (database == null) {
            throw new IllegalArgumentException("database must be present");
        }
        HikariDataSource dataSource = MariaDb.open(database);
        try {
            MariaDb.migrate(dataSource);
            return new TransitionDataRuntime(dataSource);
        } catch (RuntimeException exception) {
            dataSource.close();
            throw exception;
        }
    }

    public PlayerDirectory players() {
        return players;
    }

    public DiscordModerationPersistenceStore identities() {
        return identities;
    }

    @Override
    public void close() {
        dataSource.close();
    }
}
