package net.enthusia.staff.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zaxxer.hikari.HikariConfig;
import org.junit.jupiter.api.Test;

class MariaDbConfigurationTest {
    private static final String URL = "jdbc:mariadb://127.0.0.1:3306/enthusiastaff";

    @Test
    void configuresTheExplicitMariaDbDriverAndExpectedPoolSafetySettings() {
        HikariConfig config = MariaDb.configuration(database(8, 10_000));

        assertEquals("org.mariadb.jdbc.Driver", config.getDriverClassName());
        assertEquals(URL, config.getJdbcUrl());
        assertEquals("staff", config.getUsername());
        assertEquals("secret", config.getPassword());
        assertEquals(8, config.getMaximumPoolSize());
        assertEquals(1, config.getMinimumIdle());
        assertEquals(10_000, config.getConnectionTimeout());
        assertEquals(5_000, config.getValidationTimeout());
        assertEquals("EnthusiaStaff-MariaDB", config.getPoolName());
        assertTrue(config.isAutoCommit());
        assertEquals("TRANSACTION_READ_COMMITTED", config.getTransactionIsolation());
    }

    @Test
    void validationTimeoutNeverExceedsTheConnectionTimeout() {
        HikariConfig config = MariaDb.configuration(database(2, 1_000));

        assertEquals(1_000, config.getConnectionTimeout());
        assertEquals(1_000, config.getValidationTimeout());
    }

    @Test
    void explicitDriverDiscoverySurvivesAContextClassLoaderThatCannotSeeMariaDb() {
        Thread thread = Thread.currentThread();
        ClassLoader original = thread.getContextClassLoader();
        ClassLoader hostile = new ClassLoader(original) {
            @Override
            protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if (name.startsWith("org.mariadb.")) {
                    throw new ClassNotFoundException("blocked by hostile test context loader: " + name);
                }
                return super.loadClass(name, resolve);
            }
        };

        try {
            thread.setContextClassLoader(hostile);
            HikariConfig config = MariaDb.configuration(database(4, 5_000));
            assertEquals("org.mariadb.jdbc.Driver", config.getDriverClassName());
        } finally {
            thread.setContextClassLoader(original);
        }
    }

    private static DatabaseConfig database(int poolSize, long timeoutMillis) {
        return new DatabaseConfig(URL, "staff", "secret", poolSize, timeoutMillis);
    }
}
