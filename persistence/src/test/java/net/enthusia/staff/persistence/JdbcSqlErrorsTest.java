package net.enthusia.staff.persistence;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.BatchUpdateException;
import java.sql.SQLException;
import org.junit.jupiter.api.Test;

class JdbcSqlErrorsTest {
    @Test
    void detectsDeadlockInNextExceptionChain() {
        BatchUpdateException batch = new BatchUpdateException("batch", "HY000", 0, new int[0]);
        batch.setNextException(new SQLException("deadlock", "40001", 1213));

        assertTrue(JdbcSqlErrors.isDeadlock(batch));
        assertFalse(JdbcSqlErrors.isDuplicateKey(batch));
    }

    @Test
    void selfReferencingChainsRemainBounded() {
        SQLException failure = new SQLException("duplicate", "23000", 1062);
        failure.setNextException(failure);

        assertTrue(JdbcSqlErrors.isDuplicateKey(failure));
        assertFalse(JdbcSqlErrors.isDeadlock(failure));
    }
}
