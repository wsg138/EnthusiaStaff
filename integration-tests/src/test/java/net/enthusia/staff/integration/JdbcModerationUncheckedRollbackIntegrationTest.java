package net.enthusia.staff.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.PrintWriter;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.Statement;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;
import javax.sql.DataSource;
import net.enthusia.staff.common.CaseId;
import net.enthusia.staff.domain.application.PunishmentPlan;
import net.enthusia.staff.domain.application.PunishmentResult;
import net.enthusia.staff.persistence.JdbcModerationStore;
import net.enthusia.staff.persistence.MariaDb;
import net.enthusia.staff.persistence.MariaDbRuntime;
import net.enthusia.staff.persistence.ModerationPersistenceException;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class JdbcModerationUncheckedRollbackIntegrationTest extends PunishmentRequestMariaDbSupport {
    @Test
    void checkedSqlFailureRollsBackAndRestoresAutoCommit() throws Exception {
        CaseId caseId = new CaseId("A000000000000297");
        PunishmentPlan plan = plan(
                caseId,
                identifier("checked-rollback-target"),
                "checked.rollback",
                sevenDayBan(),
                NOW
        );
        initializeSchema();
        try (Connection connection = connection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    CREATE TRIGGER fail_punishment_step_insert BEFORE INSERT ON punishment_steps
                    FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'forced checked failure'
                    """);
        }
        TrackingDataSource tracking = new TrackingDataSource(new DriverManagerDataSource(), false);
        try {
            JdbcModerationStore store = new JdbcModerationStore(tracking, new ObjectMapper());
            assertThrows(ModerationPersistenceException.class, () -> store.createPunishment(plan));
            assertEquals(1, tracking.rollbackCalls.get());
            assertTrue(tracking.autoCommitRestored.get());
            assertEquals(0, countCases(caseId));
        } finally {
            try (Connection connection = connection(); Statement statement = connection.createStatement()) {
                statement.executeUpdate("DROP TRIGGER IF EXISTS fail_punishment_step_insert");
            }
        }
    }

    @Test
    void uncheckedSerializationFailureRollsBackAllEarlierWrites() throws Exception {
        CaseId caseId = new CaseId("A000000000000299");
        PunishmentPlan plan = plan(
                caseId,
                identifier("unchecked-rollback-target"),
                "unchecked.rollback",
                sevenDayBan(),
                NOW
        );
        initializeSchema();

        IllegalStateException programmedFailure = new IllegalStateException(
                "simulated unchecked serialization failure");
        TrackingDataSource tracking = new TrackingDataSource(new DriverManagerDataSource(), false);
        JdbcModerationStore failing = new JdbcModerationStore(
                tracking,
                failingJson(programmedFailure)
        );

        IllegalStateException thrown = assertThrows(
                IllegalStateException.class,
                () -> failing.createPunishment(plan)
        );
        assertSame(programmedFailure, thrown);
        assertEquals(1, tracking.rollbackCalls.get());
        assertTrue(tracking.autoCommitRestored.get());
        assertEquals(0, countCases(caseId));

        try (MariaDbRuntime runtime = MariaDb.initialize(databaseConfig())) {
            runtime.moderationStore().createPunishment(plan);
        }
        assertEquals(1, countCases(caseId));
    }

    @Test
    void rollbackFailureIsSuppressedWithoutReplacingUncheckedFailure() throws Exception {
        CaseId caseId = new CaseId("A000000000000298");
        PunishmentPlan plan = plan(
                caseId,
                identifier("suppressed-rollback-target"),
                "suppressed.rollback",
                sevenDayBan(),
                NOW
        );
        initializeSchema();

        IllegalStateException programmedFailure = new IllegalStateException("original unchecked failure");
        TrackingDataSource tracking = new TrackingDataSource(new DriverManagerDataSource(), true);
        JdbcModerationStore failing = new JdbcModerationStore(
                tracking,
                failingJson(programmedFailure)
        );

        IllegalStateException thrown = assertThrows(
                IllegalStateException.class,
                () -> failing.createPunishment(plan)
        );
        assertSame(programmedFailure, thrown);
        assertEquals(1, thrown.getSuppressed().length);
        assertEquals("simulated rollback failure", thrown.getSuppressed()[0].getMessage());
        assertEquals(1, tracking.rollbackCalls.get());
        assertTrue(tracking.autoCommitRestored.get());
        assertEquals(0, countCases(caseId));
    }

    @Test
    void duplicateKeyReplayStillReturnsTheCanonicalCase() throws Exception {
        CaseId caseId = new CaseId("A000000000000296");
        PunishmentPlan plan = plan(
                caseId,
                identifier("duplicate-replay-target"),
                "duplicate.replay",
                sevenDayBan(),
                NOW
        );
        try (MariaDbRuntime runtime = MariaDb.initialize(databaseConfig())) {
            PunishmentResult.Accepted first = runtime.moderationStore().createPunishment(plan);
            PunishmentResult.Accepted replay = runtime.moderationStore().createPunishment(plan);

            assertFalse(first.replayed());
            assertTrue(replay.replayed());
            assertEquals(first.caseId(), replay.caseId());
        }
        assertEquals(1, countCases(caseId));
    }

    private static void initializeSchema() {
        try (MariaDbRuntime runtime = MariaDb.initialize(databaseConfig())) {
            assertNotNull(runtime.moderationStore());
        }
    }

    private static Connection connection() throws SQLException {
        return DriverManager.getConnection(
                DATABASE.getJdbcUrl(), DATABASE.getUsername(), DATABASE.getPassword());
    }

    private static ObjectMapper failingJson(IllegalStateException failure) {
        return new ObjectMapper() {
            private static final long serialVersionUID = 1L;

            @Override
            public String writeValueAsString(Object value) throws JsonProcessingException {
                throw failure;
            }
        };
    }

    private static final class TrackingDataSource implements DataSource {
        private final DataSource delegate;
        private final boolean failRollback;
        private final AtomicInteger rollbackCalls = new AtomicInteger();
        private final AtomicBoolean autoCommitRestored = new AtomicBoolean();

        private TrackingDataSource(DataSource delegate, boolean failRollback) {
            this.delegate = delegate;
            this.failRollback = failRollback;
        }

        @Override
        public Connection getConnection() throws SQLException {
            return wrap(delegate.getConnection());
        }

        @Override
        public Connection getConnection(String username, String password) throws SQLException {
            return wrap(delegate.getConnection(username, password));
        }

        private Connection wrap(Connection connection) {
            return (Connection) Proxy.newProxyInstance(
                    Connection.class.getClassLoader(),
                    new Class<?>[]{Connection.class},
                    (proxy, method, arguments) -> {
                        if (method.getName().equals("rollback") && method.getParameterCount() == 0) {
                            rollbackCalls.incrementAndGet();
                            connection.rollback();
                            if (failRollback) {
                                throw new SQLException("simulated rollback failure");
                            }
                            return null;
                        }
                        if (method.getName().equals("setAutoCommit")
                                && arguments != null && Boolean.TRUE.equals(arguments[0])) {
                            try {
                                return method.invoke(connection, arguments);
                            } catch (InvocationTargetException exception) {
                                throw exception.getCause();
                            } finally {
                                autoCommitRestored.set(true);
                            }
                        }
                        try {
                            return method.invoke(connection, arguments);
                        } catch (InvocationTargetException exception) {
                            throw exception.getCause();
                        }
                    }
            );
        }

        @Override
        public PrintWriter getLogWriter() throws SQLException {
            return delegate.getLogWriter();
        }

        @Override
        public void setLogWriter(PrintWriter out) throws SQLException {
            delegate.setLogWriter(out);
        }

        @Override
        public void setLoginTimeout(int seconds) throws SQLException {
            delegate.setLoginTimeout(seconds);
        }

        @Override
        public int getLoginTimeout() throws SQLException {
            return delegate.getLoginTimeout();
        }

        @Override
        public Logger getParentLogger() throws SQLFeatureNotSupportedException {
            return delegate.getParentLogger();
        }

        @Override
        public <T> T unwrap(Class<T> iface) throws SQLException {
            return delegate.unwrap(iface);
        }

        @Override
        public boolean isWrapperFor(Class<?> iface) throws SQLException {
            return delegate.isWrapperFor(iface);
        }
    }

    private static final class DriverManagerDataSource implements DataSource {
        @Override
        public Connection getConnection() throws SQLException {
            return DriverManager.getConnection(
                    DATABASE.getJdbcUrl(), DATABASE.getUsername(), DATABASE.getPassword());
        }

        @Override
        public Connection getConnection(String username, String password) throws SQLException {
            return DriverManager.getConnection(DATABASE.getJdbcUrl(), username, password);
        }

        @Override
        public PrintWriter getLogWriter() {
            return null;
        }

        @Override
        public void setLogWriter(PrintWriter out) {
        }

        @Override
        public void setLoginTimeout(int seconds) {
        }

        @Override
        public int getLoginTimeout() {
            return 0;
        }

        @Override
        public Logger getParentLogger() throws SQLFeatureNotSupportedException {
            throw new SQLFeatureNotSupportedException();
        }

        @Override
        public <T> T unwrap(Class<T> iface) throws SQLException {
            throw new SQLException("not a wrapper");
        }

        @Override
        public boolean isWrapperFor(Class<?> iface) {
            return false;
        }
    }
}
