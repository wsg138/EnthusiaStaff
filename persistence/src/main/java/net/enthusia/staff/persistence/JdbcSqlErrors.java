package net.enthusia.staff.persistence;

import java.sql.SQLException;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

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
        // Retry only MariaDB's transaction-victim deadlock signal. Lock-wait timeout 1205/HY000
        // is intentionally surfaced: unlike SQLState 40001, it is not a portable transaction
        // rollback signal, and blindly retrying it can amplify sustained lock contention.
        return containsSqlError(failure, MARIA_DB_DEADLOCK, TRANSACTION_ROLLBACK);
    }

    private static boolean containsSqlError(Throwable failure, int errorCode, String sqlState) {
        if (failure == null) {
            return false;
        }
        ArrayDeque<Throwable> pending = new ArrayDeque<>();
        Set<Throwable> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        pending.add(failure);
        while (!pending.isEmpty()) {
            Throwable current = pending.removeFirst();
            if (!visited.add(current)) {
                continue;
            }
            if (current instanceof SQLException sqlException) {
                SQLException linked = sqlException;
                while (linked != null) {
                    if (linked != sqlException && !visited.add(linked)) {
                        break;
                    }
                    if (linked.getErrorCode() == errorCode && sqlState.equals(linked.getSQLState())) {
                        return true;
                    }
                    Throwable cause = linked.getCause();
                    if (cause != null && cause != linked && !visited.contains(cause)) {
                        pending.addLast(cause);
                    }
                    SQLException next = linked.getNextException();
                    if (next == linked) {
                        break;
                    }
                    linked = next;
                }
            } else {
                Throwable cause = current.getCause();
                if (cause != null && cause != current && !visited.contains(cause)) {
                    pending.addLast(cause);
                }
            }
        }
        return false;
    }
}
