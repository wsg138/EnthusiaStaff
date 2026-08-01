package net.enthusia.staff.persistence;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.BatchUpdateException;
import java.sql.SQLException;
import org.junit.jupiter.api.Test;

class JdbcSqlErrorsTest {
    @Test
    void detectsDirectDuplicateKey() {
        assertTrue(JdbcSqlErrors.isDuplicateKey(new SQLException("duplicate", "23000", 1062)));
    }

    @Test
    void detectsDuplicateKeyInCauseChain() {
        RuntimeException wrapper = new RuntimeException(
                "wrapper",
                new SQLException("duplicate", "23000", 1062)
        );

        assertTrue(JdbcSqlErrors.isDuplicateKey(wrapper));
        assertFalse(JdbcSqlErrors.isDeadlock(wrapper));
    }

    @Test
    void detectsDuplicateKeyInNextExceptionChain() {
        SQLException root = new SQLException("root", "HY000", 0);
        root.setNextException(new SQLException("duplicate", "23000", 1062));

        assertTrue(JdbcSqlErrors.isDuplicateKey(root));
    }

    @Test
    void detectsDeadlockInBatchNextExceptionChain() {
        BatchUpdateException batch = new BatchUpdateException("batch", "HY000", 0, new int[0]);
        batch.setNextException(new SQLException("deadlock", "40001", 1213));

        assertTrue(JdbcSqlErrors.isDeadlock(batch));
        assertFalse(JdbcSqlErrors.isDuplicateKey(batch));
    }

    @Test
    void selfReferencingAndRepeatedChainsRemainBounded() {
        LoopingSQLException first = new LoopingSQLException("first", "HY000", 0);
        LoopingSQLException second = new LoopingSQLException("duplicate", "23000", 1062);
        first.next = second;
        second.next = first;

        assertTrue(JdbcSqlErrors.isDuplicateKey(first));
        assertFalse(JdbcSqlErrors.isDeadlock(first));

        LoopingSQLException self = new LoopingSQLException("self", "HY000", 0);
        self.next = self;
        assertFalse(JdbcSqlErrors.isDuplicateKey(self));
    }

    @Test
    void unrelatedSqlChainsAndLockWaitTimeoutAreNotMisclassified() {
        SQLException root = new SQLException("connection", "08006", 0);
        root.setNextException(new SQLException("syntax", "42000", 1064));
        root.setNextException(new SQLException("lock timeout", "HY000", 1205));

        assertFalse(JdbcSqlErrors.isDuplicateKey(root));
        assertFalse(JdbcSqlErrors.isDeadlock(root));
    }

    private static final class LoopingSQLException extends SQLException {
        private static final long serialVersionUID = 1L;
        private SQLException next;

        private LoopingSQLException(String reason, String state, int code) {
            super(reason, state, code);
        }

        @Override
        public SQLException getNextException() {
            return next;
        }
    }
}
