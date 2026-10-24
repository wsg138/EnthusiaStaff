package net.enthusia.staff.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javax.sql.DataSource;
import net.enthusia.staff.common.CaseId;
import net.enthusia.staff.common.IdempotencyKey;
import org.junit.jupiter.api.Test;

class JdbcModerationCommittedCaseLookupTest {
    private static final CaseId EXISTING = new CaseId("A000000000000321");

    @Test
    void findsOnlyExactCommittedKeyWithReadOnlySelect() {
        AtomicReference<String> sql = new AtomicReference<>();
        AtomicReference<String> boundKey = new AtomicReference<>();
        AtomicInteger queries = new AtomicInteger();
        DataSource source = source((method, args) -> {
            if ("prepareStatement".equals(method)) {
                sql.set((String) args[0]);
                return statement(boundKey, queries, true);
            }
            throw new AssertionError("Unexpected JDBC operation: " + method);
        });
        JdbcModerationStore store = new JdbcModerationStore(source, new ObjectMapper());
        IdempotencyKey key = new IdempotencyKey("punishment-draft:abc123");
        assertEquals(Optional.of(EXISTING), store.committedCase(key));
        assertEquals("SELECT case_id FROM cases WHERE idempotency_key = ?", sql.get());
        assertEquals(key.value(), boundKey.get());
        assertEquals(1, queries.get());
    }

    @Test
    void missingCaseIsNotMistakenForCommittedAndQueryFailureFailsClosed() {
        DataSource missing = source((method, args) -> {
            if ("prepareStatement".equals(method)) {
                return statement(new AtomicReference<>(), new AtomicInteger(), false);
            }
            throw new AssertionError(method);
        });
        IdempotencyKey key = new IdempotencyKey("punishment-draft:missing");
        assertTrue(new JdbcModerationStore(missing, new ObjectMapper())
                .committedCase(key).isEmpty());

        DataSource failing = source((method, args) -> {
            throw new SQLException("database unavailable");
        });
        assertThrows(ModerationPersistenceException.class,
                () -> new JdbcModerationStore(failing, new ObjectMapper())
                        .committedCase(key));
    }

    private static PreparedStatement statement(
            AtomicReference<String> boundKey, AtomicInteger queries, boolean exists) {
        AtomicInteger rows = new AtomicInteger();
        ResultSet results = proxy(ResultSet.class, (proxy, method, args) -> switch (method.getName()) {
            case "next" -> exists && rows.getAndIncrement() == 0;
            case "getString" -> EXISTING.value();
            case "close" -> null;
            default -> throw new AssertionError(method.getName());
        });
        return proxy(PreparedStatement.class, (proxy, method, args) -> switch (method.getName()) {
            case "setString" -> {
                assertEquals(1, args[0]);
                boundKey.set((String) args[1]);
                yield null;
            }
            case "executeQuery" -> {
                queries.incrementAndGet();
                yield results;
            }
            case "close" -> null;
            default -> throw new AssertionError(method.getName());
        });
    }

    private static DataSource source(JdbcOperation operation) {
        Connection connection = proxy(Connection.class, (proxy, method, args) -> switch (method.getName()) {
            case "prepareStatement" -> operation.call(method.getName(), args);
            case "close" -> null;
            default -> throw new AssertionError(method.getName());
        });
        return proxy(DataSource.class, (proxy, method, args) -> switch (method.getName()) {
            case "getConnection" -> connection;
            default -> throw new AssertionError(method.getName());
        });
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler);
    }

    @FunctionalInterface
    private interface JdbcOperation {
        Object call(String method, Object[] args) throws SQLException;
    }
}
