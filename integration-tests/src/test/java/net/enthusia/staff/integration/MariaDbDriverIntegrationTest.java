package net.enthusia.staff.integration;

import static net.enthusia.staff.integration.MariaDbIntegrationSupport.databaseConfig;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zaxxer.hikari.HikariConfig;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Driver;
import java.util.zip.ZipFile;
import net.enthusia.staff.persistence.DatabaseConfig;
import net.enthusia.staff.persistence.MariaDb;
import net.enthusia.staff.persistence.MariaDbRuntime;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
// Reflection is intentional: these tests validate a package-private runtime factory in both
// the normal module and an isolated shaded-JAR class loader without widening production API.
@SuppressWarnings("PMD.AvoidAccessibilityAlteration")
class MariaDbDriverIntegrationTest {
    private static final String DRIVER_CLASS_NAME = "org.mariadb.jdbc.Driver";
    private static final String DRIVER_CLASS_ENTRY = "org/mariadb/jdbc/Driver.class";
    private static final String DRIVER_SERVICE_ENTRY = "META-INF/services/java.sql.Driver";
    private static final String PAPER_RUNTIME_PROPERTY = "enthusia.paperRuntimeJar";

    @Container
    private static final MariaDBContainer<?> DATABASE = new MariaDBContainer<>("mariadb:11.8.3")
            .withDatabaseName("enthusia_staff_driver_test")
            .withUsername("enthusia_test")
            .withPassword("enthusia_test_password");

    @Test
    void hikariConfigurationExplicitlyNamesMariaDbDriver() throws ReflectiveOperationException {
        DatabaseConfig database = new DatabaseConfig(
                "jdbc:mariadb://127.0.0.1:3306/driver_configuration_test",
                "enthusia_test",
                "enthusia_test_password",
                4,
                5_000
        );
        Method configurationFactory = MariaDb.class.getDeclaredMethod("hikariConfig", DatabaseConfig.class);
        configurationFactory.setAccessible(true);

        HikariConfig config = (HikariConfig) configurationFactory.invoke(null, database);

        assertEquals(DRIVER_CLASS_NAME, config.getDriverClassName());
        assertEquals(database.jdbcUrl(), config.getJdbcUrl());
        assertEquals(database.username(), config.getUsername());
    }

    @Test
    void initializationSucceedsAgainstMariaDbTestcontainer() {
        try (MariaDbRuntime runtime = MariaDb.initialize(databaseConfig(DATABASE))) {
            assertNotNull(runtime.moderationStore());
            assertNotNull(runtime.operationalStateStore());
        }
    }

    @Test
    void paperRuntimeContainsAndLoadsMariaDbDriverThroughIsolatedClassLoader() throws Exception {
        String runtimePath = System.getProperty(PAPER_RUNTIME_PROPERTY);
        assertNotNull(runtimePath, "Paper runtime jar property must be configured by Gradle");
        Path runtimeJar = Path.of(runtimePath);
        assertTrue(Files.isRegularFile(runtimeJar), "Paper runtime jar must exist");

        try (ZipFile archive = new ZipFile(runtimeJar.toFile())) {
            assertNotNull(archive.getEntry(DRIVER_CLASS_ENTRY));
            assertNotNull(archive.getEntry(DRIVER_SERVICE_ENTRY));
        }

        URL runtimeUrl = runtimeJar.toUri().toURL();
        try (URLClassLoader loader = new URLClassLoader(
                new URL[]{runtimeUrl},
                ClassLoader.getPlatformClassLoader()
        )) {
            Class<?> databaseConfigType = Class.forName(
                    "net.enthusia.staff.persistence.DatabaseConfig",
                    true,
                    loader
            );
            Constructor<?> databaseConfigConstructor = databaseConfigType.getConstructor(
                    String.class,
                    String.class,
                    String.class,
                    int.class,
                    long.class
            );
            Object isolatedDatabase = databaseConfigConstructor.newInstance(
                    "jdbc:mariadb://127.0.0.1:3306/isolated_driver_test",
                    "enthusia_test",
                    "enthusia_test_password",
                    4,
                    5_000L
            );

            Class<?> mariaDbType = Class.forName(
                    "net.enthusia.staff.persistence.MariaDb",
                    true,
                    loader
            );
            Method configurationFactory = mariaDbType.getDeclaredMethod(
                    "hikariConfig",
                    databaseConfigType
            );
            configurationFactory.setAccessible(true);
            Object isolatedConfig = configurationFactory.invoke(null, isolatedDatabase);
            Method driverClassName = isolatedConfig.getClass().getMethod("getDriverClassName");
            assertEquals(DRIVER_CLASS_NAME, driverClassName.invoke(isolatedConfig));

            Class<?> driverType = Class.forName(DRIVER_CLASS_NAME, true, loader);
            Object driver = driverType.getDeclaredConstructor().newInstance();
            assertInstanceOf(Driver.class, driver);

            try (InputStream serviceStream = loader.getResourceAsStream(DRIVER_SERVICE_ENTRY)) {
                assertNotNull(serviceStream, "MariaDB JDBC service registration must be present");
                String providers = new String(serviceStream.readAllBytes(), StandardCharsets.UTF_8);
                assertTrue(
                        providers.lines().map(String::trim).anyMatch(DRIVER_CLASS_NAME::equals),
                        "MariaDB JDBC service registration must name the driver"
                );
            }
        }
    }
}
