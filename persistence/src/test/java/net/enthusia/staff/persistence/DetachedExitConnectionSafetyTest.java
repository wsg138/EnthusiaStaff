package net.enthusia.staff.persistence;

import static org.junit.jupiter.api.Assertions.*;
import java.lang.reflect.Proxy;
import java.sql.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import net.enthusia.staff.domain.staff.*;
import org.junit.jupiter.api.Test;

class DetachedExitConnectionSafetyTest {
    private static final StaffSessionSnapshot EXPECTED = new StaffSessionSnapshot(UUID.randomUUID(), UUID.randomUUID(),
            StaffSessionOwnership.DETACHED_SERVER_ID, StaffSessionState.EXITING, false, 1, "a".repeat(64),
            new byte[]{1}, Instant.EPOCH, 4);

    @Test
    void committedExitResetsBeforeReturningTheOwnedConnection() {
        Harness h = new Harness(true, false);
        assertEquals(EXPECTED.sessionId(), h.store().beginDetachedExit(EXPECTED, Instant.EPOCH).orElseThrow().sessionId());
        assertEquals(List.of("disable", "commit", "reset", "close"), h.events);
    }

    @Test
    void missingFenceRollsBackBeforeResettingTheOwnedConnection() {
        Harness h = new Harness(false, false);
        assertTrue(h.store().beginDetachedExit(EXPECTED, Instant.EPOCH).isEmpty());
        assertEquals(List.of("disable", "rollback", "reset", "close"), h.events);
    }

    @Test
    void uncertainRollbackNeverEnablesAutoCommit() {
        Harness h = new Harness(false, true);
        ModerationPersistenceException failure = assertThrows(ModerationPersistenceException.class,
                () -> h.store().beginDetachedExit(EXPECTED, Instant.EPOCH));
        assertEquals(1, failure.getCause().getSuppressed().length);
        assertEquals(List.of("disable", "rollback", "close"), h.events);
    }

    private static final class Harness {
        final List<String> events = new ArrayList<>();
        final boolean present;
        final boolean uncertain;
        Harness(boolean present, boolean uncertain) { this.present = present; this.uncertain = uncertain; }
        JdbcStaffSessionStore store() {
            ResultSet rows = proxy(ResultSet.class, (method, args) -> switch (method) {
                case "next" -> present;
                case "close" -> null;
                case "getBytes" -> switch ((String) args[0]) {
                    case "session_id" -> UuidBytes.toBytes(EXPECTED.sessionId());
                    case "staff_id" -> UuidBytes.toBytes(EXPECTED.staffId());
                    default -> EXPECTED.snapshot();
                };
                case "getString" -> switch ((String) args[0]) {
                    case "server_id" -> EXPECTED.serverId();
                    case "state" -> EXPECTED.state().name();
                    default -> EXPECTED.checksum();
                };
                case "getBoolean" -> false;
                case "getInt" -> 1;
                case "getLong" -> EXPECTED.revision();
                case "getTimestamp" -> Timestamp.from(Instant.EPOCH);
                default -> throw new AssertionError(method);
            });
            PreparedStatement statement = proxy(PreparedStatement.class, (method, args) -> switch (method) {
                case "setBytes", "close" -> null;
                case "executeQuery" -> { if (uncertain) throw new SQLException("query failed"); yield rows; }
                default -> throw new AssertionError(method);
            });
            Connection connection = proxy(Connection.class, (method, args) -> switch (method) {
                case "setAutoCommit" -> { events.add((Boolean) args[0] ? "reset" : "disable"); yield null; }
                case "prepareStatement" -> statement;
                case "commit", "close" -> { events.add(method); yield null; }
                case "rollback" -> { events.add(method); if (uncertain) throw new SQLException("rollback failed"); yield null; }
                default -> throw new AssertionError(method);
            });
            return new JdbcStaffSessionStore(proxy(DataSource.class, (method, args) -> {
                if (method.equals("getConnection")) return connection;
                throw new AssertionError(method);
            }));
        }
    }

    private interface Call { Object apply(String method, Object[] args) throws Throwable; }
    private static <T> T proxy(Class<T> type, Call call) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},
                (instance, method, args) -> call.apply(method.getName(), args)));
    }
}
