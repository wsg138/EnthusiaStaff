package net.enthusia.staff.velocity;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class VelocityPrivateDatabaseFallbackTest {
    private static final String DATABASE_FILE = "database.properties";
    private static final String CONFIG_FILE = "config.properties";
    private static final String PRESENT_ENVIRONMENT = "PATH";
    private static final String MISSING_USER_ENVIRONMENT = "ENTHUSIA_TEST_MISSING_DB_USER_276";
    private static final String MISSING_THIRD_ENVIRONMENT = "ENTHUSIA_TEST_MISSING_DB_THIRD_276";

    @Test
    void rejectsSymlinkedPrivateDatabaseFile(@TempDir Path directory) throws IOException {
        VelocityConfiguration configuration = VelocityConfiguration.load(directory);
        Path target = directory.resolve("database-real.properties");
        storeDatabase(target);
        Path link = directory.resolve(DATABASE_FILE);
        try {
            Files.createSymbolicLink(link, target.getFileName());
        } catch (UnsupportedOperationException | IOException exception) {
            assumeTrue(false, "Symbolic links are unavailable in this test environment");
        }

        assertThrows(IllegalStateException.class, () -> configuration.database(directory));
    }

    @Test
    void partialEnvironmentConfigurationDoesNotFallBackToPrivateFile(@TempDir Path directory) throws IOException {
        assumeTrue(System.getenv(PRESENT_ENVIRONMENT) != null, "PATH must be available for this test");
        assumeTrue(System.getenv(MISSING_USER_ENVIRONMENT) == null, "test username environment must be absent");
        assumeTrue(System.getenv(MISSING_THIRD_ENVIRONMENT) == null, "test third environment must be absent");

        VelocityConfiguration.load(directory);
        Properties config = load(directory.resolve(CONFIG_FILE));
        config.setProperty("storage.jdbc-url-environment", PRESENT_ENVIRONMENT);
        config.setProperty("storage.username-environment", MISSING_USER_ENVIRONMENT);
        config.setProperty("storage.password-environment", MISSING_THIRD_ENVIRONMENT);
        store(directory.resolve(CONFIG_FILE), config);
        storeDatabase(directory.resolve(DATABASE_FILE));

        VelocityConfiguration configuration = VelocityConfiguration.load(directory);

        assertThrows(IllegalStateException.class, () -> configuration.database(directory));
    }

    private static void storeDatabase(Path file) throws IOException {
        Properties values = new Properties();
        values.setProperty("db.jdbc-url", "jdbc:mariadb://example.invalid:3306/staff");
        values.setProperty("db.username", "staff-user");
        values.setProperty("db.password", VelocityPrivateDatabaseFallbackTest.class.getName());
        store(file, values);
    }

    private static Properties load(Path file) throws IOException {
        Properties properties = new Properties();
        try (var input = Files.newInputStream(file)) {
            properties.load(input);
        }
        return properties;
    }

    private static void store(Path file, Properties properties) throws IOException {
        try (OutputStream output = Files.newOutputStream(file)) {
            properties.store(output, "test configuration");
        }
    }
}
