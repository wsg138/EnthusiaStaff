package net.enthusia.staff.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.concurrent.atomic.AtomicReference;
import javax.sql.DataSource;
import net.enthusia.staff.common.IdempotencyKey;
import org.junit.jupiter.api.Test;

class JdbcPunishmentRequestRepositoryLookupTest {
    @Test
    void committedSubmissionRecoveryUsesExactKeyAndReadOnlyQuery() {
        AtomicReference<String> query = new AtomicReference<>();
        AtomicReference<String> boundKey = new AtomicReference<>();
        ResultSet result = proxy(ResultSet.class, (object, method, args) -> switch (method.getName()) {
            case "next" -> false;
            case "close" -> null;
            default -> throw new AssertionError("Unexpected result operation " + method.getName());
        });
        PreparedStatement statement = proxy(PreparedStatement.class, (object, method, args) ->
                switch (method.getName()) {
                    case "setString" -> {
                        assertEquals(1, args[0]);
                        boundKey.set((String) args[1]);
                        yield null;
                    }
                    case "executeQuery" -> result;
                    case "close" -> null;
                    default -> throw new AssertionError("Unexpected statement operation " + method.getName());
                });
        Connection connection = proxy(Connection.class, (object, method, args) ->
                switch (method.getName()) {
                    case "prepareStatement" -> {
                        query.set((String) args[0]);
                        yield statement;
                    }
                    case "close" -> null;
                    default -> throw new AssertionError("Unexpected connection operation " + method.getName());
                });
        DataSource dataSource = proxy(DataSource.class, (object, method, args) ->
                switch (method.getName()) {
                    case "getConnection" -> connection;
                    default -> throw new AssertionError("Unexpected data source operation " + method.getName());
                });
        JdbcPunishmentRequestRepository repository = new JdbcPunishmentRequestRepository(
                dataSource, new JdbcPunishmentRequestCodec(new ObjectMapper()));
        IdempotencyKey key = new IdempotencyKey("punishment-draft:53000000-0000-0000-0000-000000000001");

        assertTrue(repository.findBySubmissionKey(key).isEmpty());
        assertTrue(query.get().contains("WHERE submission_key = ?"));
        assertTrue(!query.get().contains("open_match_key = ?"));
        assertTrue(!query.get().contains("FOR UPDATE"));
        assertEquals(key.value(), boundKey.get());
    }

    private static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return type.cast(Proxy.newProxyInstance(
                type.getClassLoader(), new Class<?>[]{type}, handler));
    }
}
