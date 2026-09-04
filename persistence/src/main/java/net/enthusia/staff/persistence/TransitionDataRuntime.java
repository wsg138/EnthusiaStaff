package net.enthusia.staff.persistence;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.enthusia.staff.domain.application.DiscordSrvMigrationService.ImportStore;
import net.enthusia.staff.domain.moderation.DiscordMinecraftLinkSource;
import net.enthusia.staff.domain.moderation.DiscordUserId;
import net.enthusia.staff.domain.ports.DiscordModerationPersistenceStore.VersionedLink;
import net.enthusia.staff.domain.ports.DiscordModerationPersistenceStore.VersionedSubject;
import net.enthusia.staff.domain.ports.PlayerDirectory;
import org.flywaydb.core.Flyway;

/** Narrow write-capable runtime used only to seed transition observation data. */
public final class TransitionDataRuntime implements AutoCloseable {
    private static final List<String> REQUIRED_MIGRATION_RESOURCES = List.of(
            "db/migration/V1__initial_schema.sql",
            "db/migration/V19__discord_moderation_persistence.sql",
            "db/migration/V20__discord_account_linking.sql");

    private final HikariDataSource dataSource;
    private final PlayerDirectory players;
    private final ImportStore discordSrvImports;

    private TransitionDataRuntime(HikariDataSource dataSource) {
        this.dataSource = dataSource;
        this.players = new JdbcPlayerDirectory(dataSource);
        this.discordSrvImports = new DiscordImportStore(dataSource);
    }

    public static TransitionDataRuntime open(DatabaseConfig database) {
        if (database == null) {
            throw new IllegalArgumentException("database must be present");
        }
        HikariDataSource dataSource = openDataSource(database);
        try {
            migrate(dataSource);
            return new TransitionDataRuntime(dataSource);
        } catch (RuntimeException exception) {
            dataSource.close();
            throw exception;
        }
    }

    public PlayerDirectory players() {
        return players;
    }

    public ImportStore discordSrvImports() {
        return discordSrvImports;
    }

    private static HikariDataSource openDataSource(DatabaseConfig database) {
        HikariConfig config = new HikariConfig();
        config.setDriverClassName("org.mariadb.jdbc.Driver");
        config.setJdbcUrl(database.jdbcUrl());
        config.setUsername(database.username());
        config.setPassword(database.password());
        config.setMaximumPoolSize(database.maximumPoolSize());
        config.setMinimumIdle(1);
        config.setConnectionTimeout(database.connectionTimeoutMillis());
        config.setValidationTimeout(Math.min(database.connectionTimeoutMillis(), 5_000));
        config.setAutoCommit(true);
        config.setTransactionIsolation("TRANSACTION_READ_COMMITTED");
        config.setPoolName("EnthusiaStaff-TransitionCollector");
        return new HikariDataSource(config);
    }

    private static void migrate(HikariDataSource dataSource) {
        ClassLoader classLoader = migrationClassLoader();
        requireMigrationResources(classLoader);
        Flyway.configure(classLoader)
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .validateMigrationNaming(true)
                .cleanDisabled(true)
                .load()
                .migrate();
    }

    static ClassLoader migrationClassLoader() {
        return TransitionDataRuntime.class.getClassLoader();
    }

    static void requireMigrationResources(ClassLoader classLoader) {
        if (classLoader == null) {
            throw new IllegalStateException("transition migration classloader is unavailable");
        }
        for (String resource : REQUIRED_MIGRATION_RESOURCES) {
            if (classLoader.getResource(resource) == null) {
                throw new IllegalStateException("transition migration resources are unavailable");
            }
        }
    }

    @Override
    public void close() {
        dataSource.close();
    }

    private static final class DiscordImportStore implements ImportStore {
        private final JdbcDiscordIdentityRepository identities;
        private final JdbcDiscordLinkRepository links;

        private DiscordImportStore(HikariDataSource dataSource) {
            identities = new JdbcDiscordIdentityRepository(dataSource);
            links = new JdbcDiscordLinkRepository(dataSource);
        }

        @Override
        public Optional<VersionedLink> currentLink(UUID minecraftPlayerId) {
            return identities.currentLink(minecraftPlayerId);
        }

        @Override
        public VersionedLink link(
                DiscordUserId discordUserId,
                UUID minecraftPlayerId,
                DiscordMinecraftLinkSource source,
                String operationKey,
                Instant linkedAt
        ) {
            return links.link(discordUserId, minecraftPlayerId, source, operationKey, linkedAt);
        }

        @Override
        public Optional<VersionedSubject> subjectForDiscord(DiscordUserId discordUserId) {
            return identities.subjectForDiscord(discordUserId);
        }
    }
}
