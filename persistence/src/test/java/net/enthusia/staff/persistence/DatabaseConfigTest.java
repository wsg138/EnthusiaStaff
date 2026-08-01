package net.enthusia.staff.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class DatabaseConfigTest {
    private static final String URL = "jdbc:mariadb://127.0.0.1:3306/enthusiastaff";

    @Test
    void retainsValidConfigurationValues() {
        DatabaseConfig config = new DatabaseConfig(URL, "staff", "secret", 8, 5_000);

        assertEquals(URL, config.jdbcUrl());
        assertEquals("staff", config.username());
        assertEquals("secret", config.password());
        assertEquals(8, config.maximumPoolSize());
        assertEquals(5_000, config.connectionTimeoutMillis());
    }

    @Test
    void acceptsPoolAndTimeoutBoundaryValues() {
        DatabaseConfig minimum = new DatabaseConfig(URL, "staff", "secret", 1, 250);
        DatabaseConfig maximum = new DatabaseConfig(URL, "staff", "secret", 32, 60_000);

        assertEquals(1, minimum.maximumPoolSize());
        assertEquals(250, minimum.connectionTimeoutMillis());
        assertEquals(32, maximum.maximumPoolSize());
        assertEquals(60_000, maximum.connectionTimeoutMillis());
    }

    @Test
    void rejectsMissingAndNonMariaDbUrls() {
        assertThrows(IllegalArgumentException.class, () -> new DatabaseConfig(null, "staff", "secret", 8, 5_000));
        assertThrows(IllegalArgumentException.class, () -> new DatabaseConfig("", "staff", "secret", 8, 5_000));
        assertThrows(IllegalArgumentException.class, () -> new DatabaseConfig(
                "jdbc:mysql://127.0.0.1:3306/enthusiastaff",
                "staff",
                "secret",
                8,
                5_000
        ));
    }

    @Test
    void rejectsMissingCredentials() {
        assertThrows(IllegalArgumentException.class, () -> new DatabaseConfig(URL, null, "secret", 8, 5_000));
        assertThrows(IllegalArgumentException.class, () -> new DatabaseConfig(URL, "", "secret", 8, 5_000));
        assertThrows(IllegalArgumentException.class, () -> new DatabaseConfig(URL, "   ", "secret", 8, 5_000));
        assertThrows(IllegalArgumentException.class, () -> new DatabaseConfig(URL, "staff", null, 8, 5_000));
    }

    @Test
    void rejectsPoolSizesOutsideTheSafetyRange() {
        assertThrows(IllegalArgumentException.class, () -> new DatabaseConfig(URL, "staff", "secret", 0, 5_000));
        assertThrows(IllegalArgumentException.class, () -> new DatabaseConfig(URL, "staff", "secret", 33, 5_000));
    }

    @Test
    void rejectsConnectionTimeoutsOutsideTheSafetyRange() {
        assertThrows(IllegalArgumentException.class, () -> new DatabaseConfig(URL, "staff", "secret", 8, 249));
        assertThrows(IllegalArgumentException.class, () -> new DatabaseConfig(URL, "staff", "secret", 8, 60_001));
    }
}
