package net.enthusia.staff.persistence;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.SQLException;
import org.junit.jupiter.api.Test;

final class PolicyV2JdbcSupportTest {
    @Test
    void duplicateKeyDetectionAcceptsMariaDbUniqueViolationOnly() {
        assertTrue(PolicyV2JdbcSupport.isDuplicateKey(
                new SQLException("duplicate", "23000", 1062)
        ));
        assertFalse(PolicyV2JdbcSupport.isDuplicateKey(
                new SQLException("deadlock", "40001", 1213)
        ));
        assertFalse(PolicyV2JdbcSupport.isDuplicateKey(
                new SQLException("other integrity violation", "23000", 1452)
        ));
    }
}
