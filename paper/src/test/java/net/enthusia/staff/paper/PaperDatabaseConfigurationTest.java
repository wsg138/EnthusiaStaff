package net.enthusia.staff.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import net.enthusia.staff.persistence.DatabaseConfig;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

final class PaperDatabaseConfigurationTest {
    private static final String URL_ENVIRONMENT = "STAFF_DATABASE_URL";
    private static final String USER_ENVIRONMENT = "STAFF_DATABASE_USER";
    private static final String AUTH_ENVIRONMENT = "STAFF_DATABASE_PASSWORD";
    private static final String JDBC_URL = "jdbc:mariadb://database/enthusia";
    private static final String USERNAME = "enthusia";
    private static final String AUTHENTICATION_VALUE = "development-only";

    @Test
    void resolvesOnlyTheConfiguredEnvironmentVariableNames() {
        YamlConfiguration configuration = configuredEnvironmentNames();
        configuration.set("storage.maximum-pool-size", 12);
        configuration.set("storage.connection-timeout-millis", 7_500L);
        Map<String, String> environment = completeEnvironment();

        DatabaseConfig database = new PaperDatabaseConfiguration(PaperDatabaseConfiguration.snapshot(configuration), environment::get)
                .load()
                .orElseThrow();

        assertEquals(JDBC_URL, database.jdbcUrl());
        assertEquals(USERNAME, database.username());
        assertEquals(AUTHENTICATION_VALUE, database.password());
        assertEquals(12, database.maximumPoolSize());
        assertEquals(7_500L, database.connectionTimeoutMillis());
    }

    @Test
    void missingEnvironmentValueLeavesStorageUnavailable() {
        YamlConfiguration configuration = configuredEnvironmentNames();
        Map<String, String> environment = Map.of(
                URL_ENVIRONMENT, JDBC_URL,
                USER_ENVIRONMENT, USERNAME
        );

        assertTrue(new PaperDatabaseConfiguration(PaperDatabaseConfiguration.snapshot(configuration), environment::get).load().isEmpty());
    }

    @Test
    void blankEnvironmentValueLeavesStorageUnavailable() {
        YamlConfiguration configuration = configuredEnvironmentNames();
        Map<String, String> environment = Map.of(
                URL_ENVIRONMENT, JDBC_URL,
                USER_ENVIRONMENT, USERNAME,
                AUTH_ENVIRONMENT, "   "
        );

        assertTrue(new PaperDatabaseConfiguration(PaperDatabaseConfiguration.snapshot(configuration), environment::get).load().isEmpty());
    }

    @Test
    void appliesSafePoolDefaultsWhenSettingsAreAbsent() {
        YamlConfiguration configuration = configuredEnvironmentNames();

        DatabaseConfig database = new PaperDatabaseConfiguration(PaperDatabaseConfiguration.snapshot(configuration), completeEnvironment()::get)
                .load()
                .orElseThrow();

        assertEquals(8, database.maximumPoolSize());
        assertEquals(5_000L, database.connectionTimeoutMillis());
    }

    private YamlConfiguration configuredEnvironmentNames() {
        YamlConfiguration configuration = new YamlConfiguration();
        configuration.set("storage.jdbc-url-environment", URL_ENVIRONMENT);
        configuration.set("storage.username-environment", USER_ENVIRONMENT);
        configuration.set("storage.password-environment", AUTH_ENVIRONMENT);
        return configuration;
    }

    private Map<String, String> completeEnvironment() {
        return Map.of(
                URL_ENVIRONMENT, JDBC_URL,
                USER_ENVIRONMENT, USERNAME,
                AUTH_ENVIRONMENT, AUTHENTICATION_VALUE
        );
    }
}
