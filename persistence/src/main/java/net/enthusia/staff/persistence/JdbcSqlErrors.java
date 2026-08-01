package net.enthusia.staff.persistence;

import java.sql.SQLException;

final class JdbcSqlErrors {
    private static final int MARIA_DB_DUPLICATE_KEY = 1062;
    private static final String INTEGRITY_CONSTRAINT_VIOLATION = "23000";

    private JdbcSqlErrors() {
    }

    static boolean isDuplicateKey(Throwable failure) {
        Throwable current = failure;
        while (current != null) {
            if (current instanceof SQLException sqlException
                    && sqlException.getErrorCode() == MARIA_DB_DUPLICATE_KEY
                    && INTEGRITY_CONSTRAINT_VIOLATION.equals(sqlException.getSQLState())) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
