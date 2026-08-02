package net.enthusia.staff.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class DatabaseConfigTest {
    private static final String URL = "jdbc:mariadb://127.0.0.1:3306/enthusiastaff";
    private static final String USERNAME = DatabaseConfigTest.class.getSimpleName();
    private static final String AUTHENTICATION_VALUE = DatabaseConfigTest.class.getName();

    @Test
    void retainsValidConfigurationValues() {
        DatabaseConfig config = new DatabaseConfig(URL, USERNAME, AUTHENTICATION_VALUE, 8, 5_000);

        assertEquals(URL, config.jdbcUrl());
        assertEquals(USERNAME, config.username());
        assertEquals(AUTHENTICATION_VALUE, config.password());
        assertEquals(8, config.maximumPoolSize());
        assertEquals(5_000, config.connectionTimeoutMillis());
    }

    @Test
    void acceptsPoolAndTimeoutBoundaryValues() {
        DatabaseConfig minimum = new DatabaseConfig(URL, USERNAME, AUTHENTICATION_VALUE, 2, 250);
        DatabaseConfig maximum = new DatabaseConfig(URL, USERNAME, AUTHENTICATION_VALUE, 32, 60_000);

        assertEquals(2, minimum.maximumPoolSize());
        assertEquals(250, minimum.connectionTimeoutMillis());
        assertEquals(32, maximum.maximumPoolSize());
        assertEquals(60_000, maximum.connectionTimeoutMillis());
    }

    @Test
    void rejectsMissingAndNonMariaDbUrls() {
        assertThrows(IllegalArgumentException.class, () -> new DatabaseConfig(
                null,
                USERNAME,
                AUTHENTICATION_VALUE,
                8,
                5_000
        ));
        assertThrows(IllegalArgumentException.class, () -> new DatabaseConfig(
                "",
                USERNAME,
                AUTHENTICATION_VALUE,
                8,
                5_000
        ));
        assertThrows(IllegalArgumentException.class, () -> new DatabaseConfig(
                "jdbc:mysql://127.0.0.1:3306/enthusiastaff",
                USERNAME,
                AUTHENTICATION_VALUE,
                8,
                5_000
        ));
    }

    @Test
    void rejectsMissingCredentials() {
        assertThrows(IllegalArgumentException.class, () -> new DatabaseConfig(
                URL,
                null,
                AUTHENTICATION_VALUE,
                8,
                5_000
        ));
        assertThrows(IllegalArgumentException.class, () -> new DatabaseConfig(
                URL,
                "",
                AUTHENTICATION_VALUE,
                8,
                5_000
        ));
        assertThrows(IllegalArgumentException.class, () -> new DatabaseConfig(
                URL,
                "   ",
                AUTHENTICATION_VALUE,
                8,
                5_000
        ));
        assertThrows(IllegalArgumentException.class, () -> new DatabaseConfig(URL, USERNAME, null, 8, 5_000));
    }

    @Test
    void rejectsPoolSizesOutsideTheSafetyRange() {
        assertThrows(IllegalArgumentException.class, () -> new DatabaseConfig(
                URL,
                USERNAME,
                AUTHENTICATION_VALUE,
                1,
                5_000
        ));
        assertThrows(IllegalArgumentException.class, () -> new DatabaseConfig(
                URL,
                USERNAME,
                AUTHENTICATION_VALUE,
                33,
                5_000
        ));
    }

    @Test
    void rejectsConnectionTimeoutsOutsideTheSafetyRange() {
        assertThrows(IllegalArgumentException.class, () -> new DatabaseConfig(
                URL,
                USERNAME,
                AUTHENTICATION_VALUE,
                8,
                249
        ));
        assertThrows(IllegalArgumentException.class, () -> new DatabaseConfig(
                URL,
                USERNAME,
                AUTHENTICATION_VALUE,
                8,
                60_001
        ));
    }
}
