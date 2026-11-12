package net.enthusia.staff.persistence;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import net.enthusia.staff.domain.staff.StaffSessionInspectionException;
import net.enthusia.staff.domain.staff.StaffSessionInspectionException.Reason;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class JdbcStaffSessionInspectionTest {
    private static final UUID STAFF = UUID.fromString("73000000-0000-0000-0000-000000000021");
    private static final String DIGEST = "039058c6f2c0cb492c533b0a4d14ef77cc0f78abccced5287d84a1a2011cfb81";

    @Test
    void readsOnlyExactTargetWithoutLockOrWriteAndPreservesSnapshot() {
        Fixture fixture = new Fixture();
        var result = new JdbcStaffSessionStore(fixture.source).inspect(STAFF).orElseThrow();
        assertEquals(STAFF, result.staffId());
        assertEquals("__network_detached__", result.serverId());
        assertArrayEquals(new byte[]{1, 2, 3}, result.header());
        assertTrue(fixture.sql.contains("LEFT JOIN"));
        assertTrue(fixture.sql.contains("SHA2(p.snapshot_blob, 256)"));
        assertFalse(fixture.sql.contains("THEN p.snapshot_blob"), "full payload must never be returned");
        assertTrue(fixture.sql.contains("LIMIT 2"));
        assertFalse(fixture.sql.contains("FOR UPDATE"));
        assertEquals(STAFF, UuidBytes.fromBytes((byte[]) fixture.parameters.get(2)));
        assertTrue(fixture.readOnly);
        assertEquals(Connection.TRANSACTION_REPEATABLE_READ, fixture.isolation);
        assertTrue(fixture.rolledBack);
    }

    @Test
    void noRowsIsConfirmedAbsence() {
        Fixture fixture = new Fixture();
        fixture.rows.clear();
        assertTrue(new JdbcStaffSessionStore(fixture.source).inspect(STAFF).isEmpty());
        assertTrue(fixture.rolledBack);
    }

    @Test
    void missingJoinedSnapshotIsNotAbsence() {
        Fixture fixture = new Fixture();
        fixture.rows.getFirst().put("snapshot_session_id", null);
        assertFailure(fixture, Reason.MISSING_SNAPSHOT);
    }

    @Test
    void multipleActiveRowsFailClosed() {
        Fixture fixture = new Fixture();
        fixture.rows.add(new HashMap<>(fixture.rows.getFirst()));
        assertFailure(fixture, Reason.AMBIGUOUS_SESSION);
    }

    @Test
    void databaseComputedDigestMismatchFailsClosed() {
        Fixture fixture = new Fixture();
        fixture.rows.getFirst().put("computed_checksum", "a".repeat(64));
        assertFailure(fixture, Reason.CHECKSUM_MISMATCH);
    }

    @Test
    void oversizedOrMissingBlobFailsClosed() {
        Fixture fixture = new Fixture();
        fixture.rows.getFirst().put("snapshot_length", Integer.MAX_VALUE);
        fixture.rows.getFirst().put("snapshot_header", null);
        assertFailure(fixture, Reason.INVALID_SNAPSHOT_LENGTH);
    }

    @Test
    void wrongTargetOrSnapshotIdentityFailsClosed() {
        Fixture fixture = new Fixture();
        fixture.rows.getFirst().put("staff_id", UuidBytes.toBytes(UUID.randomUUID()));
        assertFailure(fixture, Reason.INVALID_METADATA);
        fixture = new Fixture();
        fixture.rows.getFirst().put("snapshot_session_id", UuidBytes.toBytes(UUID.randomUUID()));
        assertFailure(fixture, Reason.INVALID_METADATA);
    }

    @Test
    void queryFailureRollsBackAndNeverBecomesAbsence() {
        Fixture fixture = new Fixture();
        fixture.failQuery = true;
        assertThrows(ModerationPersistenceException.class,
                () -> new JdbcStaffSessionStore(fixture.source).inspect(STAFF));
        assertTrue(fixture.rolledBack);
    }

    @ParameterizedTest
    @ValueSource(strings = {"server_id", "state", "started_at"})
    void missingRequiredMetadataFailsClosedWithoutCatchingNullPointer(String field) {
        Fixture fixture = new Fixture();
        fixture.rows.getFirst().put(field, null);
        assertFailure(fixture, Reason.INVALID_METADATA);
    }

    private static void assertFailure(Fixture fixture, Reason reason) {
        var failure = assertThrows(StaffSessionInspectionException.class,
                () -> new JdbcStaffSessionStore(fixture.source).inspect(STAFF));
        assertEquals(reason, failure.reason());
        assertTrue(fixture.rolledBack);
    }

    private static final class Fixture {
        final List<Map<String, Object>> rows = new ArrayList<>();
        final Map<Integer, Object> parameters = new HashMap<>();
        boolean readOnly;
        boolean rolledBack;
        boolean failQuery;
        int isolation;
        int index = -1;
        String sql;
        final ResultSet result = stub(ResultSet.class, (name, args) -> switch (name) {
            case "next" -> ++index < rows.size();
            case "getLong" -> ((Number) rows.get(index).get(args[0])).longValue();
            case "getInt" -> ((Number) rows.get(index).get(args[0])).intValue();
            case "getBoolean", "getString", "getBytes", "getTimestamp" -> rows.get(index).get(args[0]);
            case "close" -> null;
            default -> throw new AssertionError("Unexpected result operation: " + name);
        });
        final PreparedStatement statement = stub(PreparedStatement.class, (name, args) -> switch (name) {
            case "setBytes", "setInt" -> { parameters.put((Integer) args[0], args[1]); yield null; }
            case "setQueryTimeout", "setMaxRows", "close" -> null;
            case "executeQuery" -> {
                if (failQuery) throw new SQLException("failure");
                yield result;
            }
            default -> throw new AssertionError("Unexpected statement operation: " + name);
        });
        final Connection connection = stub(Connection.class, (name, args) -> switch (name) {
            case "setReadOnly" -> { readOnly = (Boolean) args[0]; yield null; }
            case "setTransactionIsolation" -> { isolation = (Integer) args[0]; yield null; }
            case "setAutoCommit", "close" -> null;
            case "rollback" -> { rolledBack = true; yield null; }
            case "prepareStatement" -> { sql = (String) args[0]; yield statement; }
            default -> throw new AssertionError("Unexpected connection operation: " + name);
        });
        final DataSource source = stub(DataSource.class, (name, args) -> {
            if (name.equals("getConnection")) return connection;
            throw new AssertionError("Unexpected source operation: " + name);
        });

        Fixture() {
            byte[] id = UuidBytes.toBytes(UUID.randomUUID());
            Map<String, Object> row = new HashMap<>();
            row.put("session_id", id); row.put("snapshot_session_id", id);
            row.put("staff_id", UuidBytes.toBytes(STAFF));
            row.put("server_id", "__network_detached__"); row.put("state", "ACTIVE");
            row.put("vanish_active", false); row.put("schema_version", 1);
            row.put("revision", 4L); row.put("snapshot_length", 3L);
            row.put("checksum", DIGEST); row.put("computed_checksum", DIGEST);
            row.put("snapshot_header", new byte[]{1, 2, 3});
            row.put("started_at", Timestamp.from(Instant.parse("2026-10-28T00:00:00Z")));
            rows.add(row);
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> T stub(Class<T> type, ThrowingCall call) {
        return (T) Proxy.newProxyInstance(Thread.currentThread().getContextClassLoader(), new Class<?>[]{type},
                (proxy, method, args) -> call.invoke(method.getName(), args));
    }

    private interface ThrowingCall {
        Object invoke(String method, Object[] args) throws Throwable;
    }
}
