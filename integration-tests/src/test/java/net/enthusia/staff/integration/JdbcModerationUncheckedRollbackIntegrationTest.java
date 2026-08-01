package net.enthusia.staff.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.logging.Logger;
import javax.sql.DataSource;
import net.enthusia.staff.common.CaseId;
import net.enthusia.staff.domain.application.PunishmentPlan;
import net.enthusia.staff.persistence.JdbcModerationStore;
import net.enthusia.staff.persistence.MariaDb;
import net.enthusia.staff.persistence.MariaDbRuntime;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class JdbcModerationUncheckedRollbackIntegrationTest extends PunishmentRequestMariaDbSupport {
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
        try (MariaDbRuntime ignored = MariaDb.initialize(databaseConfig())) {
            // Run migrations before exercising a store with a deliberately failing serializer.
        }

        ObjectMapper failingJson = new ObjectMapper() {
            @Override
            public String writeValueAsString(Object value) throws JsonProcessingException {
                throw new IllegalStateException("simulated unchecked serialization failure");
            }
        };
        JdbcModerationStore failing = new JdbcModerationStore(new DriverManagerDataSource(), failingJson);

        assertThrows(IllegalStateException.class, () -> failing.createPunishment(plan));
        assertEquals(0, countCases(caseId));

        try (MariaDbRuntime runtime = MariaDb.initialize(databaseConfig())) {
            runtime.moderationStore().createPunishment(plan);
        }
        assertEquals(1, countCases(caseId));
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
