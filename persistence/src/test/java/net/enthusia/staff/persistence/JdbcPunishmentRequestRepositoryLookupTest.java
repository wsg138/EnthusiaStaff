package net.enthusia.staff.persistence;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import javax.sql.DataSource;
import net.enthusia.staff.common.IdempotencyKey;
import net.enthusia.staff.domain.auth.StaffRank;
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

    @Test
    void reviewableQueryFiltersByRequesterAndApprovalRankBeforeLimiting() {
        AtomicReference<String> query = new AtomicReference<>();
        Map<Integer, Object> bindings = new HashMap<>();
        ResultSet result = proxy(ResultSet.class, (object, method, args) -> switch (method.getName()) {
            case "next" -> false;
            case "close" -> null;
            default -> throw new AssertionError("Unexpected result operation " + method.getName());
        });
        PreparedStatement statement = proxy(PreparedStatement.class, (object, method, args) ->
                switch (method.getName()) {
                    case "setTimestamp", "setBytes", "setString", "setInt" -> {
                        bindings.put((Integer) args[0], args[1]);
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
        UUID reviewer = UUID.fromString("55555555-5555-5555-5555-555555555555");
        Instant now = Instant.parse("2026-10-24T10:00:00Z");

        for (StaffRank rank : new StaffRank[]{StaffRank.MOD, StaffRank.ADMIN, StaffRank.FOUNDER}) {
            bindings.clear();
            assertTrue(repository.pendingReviewable(now, reviewer, rank, 10).isEmpty());
            assertEquals(Timestamp.from(now), bindings.get(1));
            assertArrayEquals(UuidBytes.toBytes(reviewer), (byte[]) bindings.get(2));
            assertEquals(rank.name(), bindings.get(3));
            assertEquals(rank.name(), bindings.get(4));
            assertEquals(10, bindings.get(5));
        }
        String sql = query.get();
        assertTrue(sql.contains("status = 'PENDING' AND expires_at > ?"));
        assertTrue(sql.contains("requester_id <> ?"));
        assertTrue(sql.contains("required_rank IN ('HELPER', 'MOD')"));
        assertTrue(sql.contains("required_rank = 'ADMIN' AND ? IN ('ADMIN', 'FOUNDER')"));
        assertTrue(sql.contains("required_rank = 'FOUNDER' AND ? = 'FOUNDER'"));
        assertTrue(sql.indexOf("requester_id <> ?") < sql.indexOf("LIMIT ?"));
        assertTrue(sql.indexOf("required_rank IN") < sql.indexOf("LIMIT ?"));
        assertTrue(sql.contains("ORDER BY created_at, request_id LIMIT ?"));
        assertFalse(sql.contains("FOR UPDATE"));
    }

    private static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return type.cast(Proxy.newProxyInstance(
                type.getClassLoader(), new Class<?>[]{type}, handler));
    }
}
