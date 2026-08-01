package net.enthusia.staff.persistence;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;

public final class MariaDb {
    private MariaDb() {
    }

    public static HikariDataSource open(DatabaseConfig database) {
        return new HikariDataSource(hikariConfig(database));
    }

    static HikariConfig hikariConfig(DatabaseConfig database) {
        HikariConfig config = new HikariConfig();
        config.setDriverClassName("org.mariadb.jdbc.Driver");
        config.setJdbcUrl(database.jdbcUrl());
        config.setUsername(database.username());
        config.setPassword(database.password());
        config.setMaximumPoolSize(database.maximumPoolSize());
        config.setMinimumIdle(1);
        config.setConnectionTimeout(database.connectionTimeoutMillis());
        config.setValidationTimeout(Math.min(database.connectionTimeoutMillis(), 5_000));
        config.setPoolName("EnthusiaStaff-MariaDB");
        config.setAutoCommit(true);
        config.setTransactionIsolation("TRANSACTION_READ_COMMITTED");
        return config;
    }

    public static MigrateResult migrate(DataSource dataSource) {
        return Flyway.configure(MariaDb.class.getClassLoader())
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .validateMigrationNaming(true)
                .cleanDisabled(true)
                .load()
                .migrate();
    }

    public static MariaDbRuntime initialize(DatabaseConfig database) {
        HikariDataSource dataSource = open(database);
        try {
            migrate(dataSource);
            return new MariaDbRuntime(dataSource);
        } catch (RuntimeException exception) {
            dataSource.close();
            throw exception;
        }
    }
}
