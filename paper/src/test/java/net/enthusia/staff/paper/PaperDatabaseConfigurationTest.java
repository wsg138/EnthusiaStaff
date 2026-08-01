package net.enthusia.staff.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import net.enthusia.staff.persistence.DatabaseConfig;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

final class PaperDatabaseConfigurationTest {
    @Test
    void resolvesOnlyTheConfiguredEnvironmentVariableNames() {
        YamlConfiguration configuration = configuredEnvironmentNames();
        configuration.set("storage.maximum-pool-size", 12);
        configuration.set("storage.connection-timeout-millis", 7_500L);
        Map<String, String> environment = Map.of(
                "STAFF_DATABASE_URL", "jdbc:mariadb://database/enthusia",
                "STAFF_DATABASE_USER", "enthusia",
                "STAFF_DATABASE_PASSWORD", "development-only"
        );

        DatabaseConfig database = new PaperDatabaseConfiguration(configuration, environment::get)
                .load()
                .orElseThrow();

        assertEquals("jdbc:mariadb://database/enthusia", database.jdbcUrl());
        assertEquals("enthusia", database.username());
        assertEquals("development-only", database.password());
        assertEquals(12, database.maximumPoolSize());
        assertEquals(7_500L, database.connectionTimeoutMillis());
    }

    @Test
    void missingEnvironmentValueLeavesStorageUnavailable() {
        YamlConfiguration configuration = configuredEnvironmentNames();
        Map<String, String> environment = Map.of(
                "STAFF_DATABASE_URL", "jdbc:mariadb://database/enthusia",
                "STAFF_DATABASE_USER", "enthusia"
        );

        assertTrue(new PaperDatabaseConfiguration(configuration, environment::get).load().isEmpty());
    }

    private YamlConfiguration configuredEnvironmentNames() {
        YamlConfiguration configuration = new YamlConfiguration();
        configuration.set("storage.jdbc-url-environment", "STAFF_DATABASE_URL");
        configuration.set("storage.username-environment", "STAFF_DATABASE_USER");
        configuration.set("storage.password-environment", "STAFF_DATABASE_PASSWORD");
        return configuration;
    }
}
