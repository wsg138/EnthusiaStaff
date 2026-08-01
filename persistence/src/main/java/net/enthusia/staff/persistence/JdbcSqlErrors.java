package net.enthusia.staff.persistence;

import java.sql.SQLException;

final class JdbcSqlErrors {
    private static final int MARIA_DB_DUPLICATE_KEY = 1062;
    private static final int MARIA_DB_DEADLOCK = 1213;
    private static final String INTEGRITY_CONSTRAINT_VIOLATION = "23000";
    private static final String TRANSACTION_ROLLBACK = "40001";

    private JdbcSqlErrors() {
    }

    static boolean isDuplicateKey(Throwable failure) {
        return containsSqlError(failure, MARIA_DB_DUPLICATE_KEY, INTEGRITY_CONSTRAINT_VIOLATION);
    }

    static boolean isDeadlock(Throwable failure) {
        return containsSqlError(failure, MARIA_DB_DEADLOCK, TRANSACTION_ROLLBACK);
    }

    private static boolean containsSqlError(Throwable failure, int errorCode, String sqlState) {
        Throwable current = failure;
        while (current != null) {
            if (current instanceof SQLException sqlException
                    && sqlException.getErrorCode() == errorCode
                    && sqlState.equals(sqlException.getSQLState())) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
