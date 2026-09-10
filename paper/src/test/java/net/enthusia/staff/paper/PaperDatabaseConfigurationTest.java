package net.enthusia.staff.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import net.enthusia.staff.persistence.DatabaseConfig;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class PaperDatabaseConfigurationTest {
    private static final String URL_ENVIRONMENT = "STAFF_DATABASE_URL";
    private static final String USER_ENVIRONMENT = "STAFF_DATABASE_USER";
    private static final String AUTH_ENVIRONMENT = "STAFF_DATABASE_PASSWORD";
    private static final String JDBC_URL = "jdbc:mariadb://database/enthusia";
    private static final String USERNAME = "enthusia";
    private static final String AUTHENTICATION_VALUE = "development-only";

    @TempDir
    Path tempDir;

    @Test
    void resolvesOnlyTheConfiguredEnvironmentVariableNames() {
        YamlConfiguration configuration = configuredEnvironmentNames(null);
        configuration.set("storage.maximum-pool-size", 12);
        configuration.set("storage.connection-timeout-millis", 7_500L);

        DatabaseConfig database = new PaperDatabaseConfiguration(
                PaperDatabaseConfiguration.snapshot(configuration),
                completeEnvironment()::get
        ).load().orElseThrow();

        assertEquals(JDBC_URL, database.jdbcUrl());
        assertEquals(USERNAME, database.username());
        assertEquals(AUTHENTICATION_VALUE, database.password());
        assertEquals(12, database.maximumPoolSize());
        assertEquals(7_500L, database.connectionTimeoutMillis());
    }

    @Test
    void panelCredentialsFileProvidesDatabaseWithoutEnvironmentVariables() throws IOException {
        Path credentials = tempDir.resolve("database.properties");
        Files.writeString(credentials, String.join("\n",
                "db.jdbc-url=" + JDBC_URL,
                "db.username=" + USERNAME,
                "db.password=" + AUTHENTICATION_VALUE,
                ""));
        YamlConfiguration configuration = configuredEnvironmentNames(credentials.toString());

        DatabaseConfig database = new PaperDatabaseConfiguration(
                PaperDatabaseConfiguration.snapshot(configuration),
                ignored -> null
        ).load().orElseThrow();

        assertEquals(JDBC_URL, database.jdbcUrl());
        assertEquals(USERNAME, database.username());
        assertEquals(AUTHENTICATION_VALUE, database.password());
    }

    @Test
    void partialEnvironmentFailsClosedInsteadOfFallingBackToFile() throws IOException {
        Path credentials = tempDir.resolve("database.properties");
        Files.writeString(credentials, String.join("\n",
                "db.jdbc-url=" + JDBC_URL,
                "db.username=" + USERNAME,
                "db.password=" + AUTHENTICATION_VALUE,
                ""));
        YamlConfiguration configuration = configuredEnvironmentNames(credentials.toString());
        Map<String, String> partial = Map.of(URL_ENVIRONMENT, JDBC_URL);

        assertThrows(IllegalArgumentException.class, () -> new PaperDatabaseConfiguration(
                PaperDatabaseConfiguration.snapshot(configuration),
                partial::get
        ).load());
    }

    @Test
    void credentialsFileRejectsUnknownAndIncompleteProperties() throws IOException {
        Path credentials = tempDir.resolve("database.properties");
        YamlConfiguration configuration = configuredEnvironmentNames(credentials.toString());
        PaperDatabaseConfiguration loader = new PaperDatabaseConfiguration(
                PaperDatabaseConfiguration.snapshot(configuration),
                ignored -> null
        );

        Files.writeString(credentials, "unsupported=value\n");
        assertThrows(IllegalArgumentException.class, loader::load);

        Files.writeString(credentials, "db.jdbc-url=" + JDBC_URL + "\n");
        assertThrows(IllegalArgumentException.class, loader::load);
    }

    @Test
    void missingEnvironmentAndCredentialsFileLeaveStorageUnavailable() {
        YamlConfiguration configuration = configuredEnvironmentNames(
                tempDir.resolve("missing.properties").toString());

        assertTrue(new PaperDatabaseConfiguration(
                PaperDatabaseConfiguration.snapshot(configuration),
                ignored -> null
        ).load().isEmpty());
    }

    @Test
    void blankEnvironmentValueFailsClosedWithoutFileFallback() {
        YamlConfiguration configuration = configuredEnvironmentNames(null);
        Map<String, String> environment = Map.of(
                URL_ENVIRONMENT, JDBC_URL,
                USER_ENVIRONMENT, USERNAME,
                AUTH_ENVIRONMENT, "   "
        );

        assertThrows(IllegalArgumentException.class, () -> new PaperDatabaseConfiguration(
                PaperDatabaseConfiguration.snapshot(configuration),
                environment::get
        ).load());
    }

    @Test
    void appliesSafePoolDefaultsWhenSettingsAreAbsent() {
        YamlConfiguration configuration = configuredEnvironmentNames(null);

        DatabaseConfig database = new PaperDatabaseConfiguration(
                PaperDatabaseConfiguration.snapshot(configuration),
                completeEnvironment()::get
        ).load().orElseThrow();

        assertEquals(8, database.maximumPoolSize());
        assertEquals(5_000L, database.connectionTimeoutMillis());
    }

    private YamlConfiguration configuredEnvironmentNames(String credentialsFile) {
        YamlConfiguration configuration = new YamlConfiguration();
        configuration.set("storage.jdbc-url-environment", URL_ENVIRONMENT);
        configuration.set("storage.username-environment", USER_ENVIRONMENT);
        configuration.set("storage.password-environment", AUTH_ENVIRONMENT);
        configuration.set("storage.credentials-file", credentialsFile);
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
