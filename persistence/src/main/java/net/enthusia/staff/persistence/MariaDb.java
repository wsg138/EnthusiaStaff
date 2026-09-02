package net.enthusia.staff.persistence;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.util.Objects;
import java.util.function.Supplier;
import javax.sql.DataSource;
import net.enthusia.staff.domain.report.ReportPolicy;
import net.enthusia.staff.domain.report.ReportPolicyRuntime;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;

public final class MariaDb {
    private MariaDb() {
    }

    public static HikariDataSource open(DatabaseConfig database) {
        return new HikariDataSource(configuration(database));
    }

    public static HikariDataSource openReadOnly(DatabaseConfig database) {
        return new HikariDataSource(readOnlyConfiguration(database));
    }

    static HikariConfig configuration(DatabaseConfig database) {
        HikariConfig config = baseConfiguration(database);
        config.setPoolName("EnthusiaStaff-MariaDB");
        return config;
    }

    static HikariConfig readOnlyConfiguration(DatabaseConfig database) {
        HikariConfig config = baseConfiguration(database);
        config.setReadOnly(true);
        config.setPoolName("EnthusiaStaff-MariaDB-ReadOnly");
        return config;
    }

    private static HikariConfig baseConfiguration(DatabaseConfig database) {
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
        return config;
    }

    // Retained for existing runtime-JAR verification that reflects this helper by name.
    private static HikariConfig hikariConfig(DatabaseConfig database) {
        return configuration(database);
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
        return initialize(database, ReportPolicyRuntime::current);
    }

    public static MariaDbRuntime initialize(
            DatabaseConfig database,
            Supplier<ReportPolicy> reportPolicy
    ) {
        Objects.requireNonNull(reportPolicy, "reportPolicy");
        HikariDataSource dataSource = open(database);
        try {
            migrate(dataSource);
            return new MariaDbRuntime(dataSource, reportPolicy);
        } catch (RuntimeException exception) {
            dataSource.close();
            throw exception;
        }
    }
}
