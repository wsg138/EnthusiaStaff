package net.enthusia.staff.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import net.enthusia.staff.common.security.ProtectedNetworkIdentity;
import net.enthusia.staff.domain.OperationalMode;
import net.enthusia.staff.domain.alt.AltRelationshipState;
import net.enthusia.staff.domain.alt.AltRelationshipSummary;
import net.enthusia.staff.domain.alt.NetworkIdentityObservationResult;
import net.enthusia.staff.domain.ports.NetworkIdentityStore;
import net.enthusia.staff.persistence.DatabaseConfig;
import net.enthusia.staff.persistence.MariaDb;
import net.enthusia.staff.persistence.MariaDbRuntime;
import net.enthusia.staff.persistence.migration.FencedNetworkIdentityStore;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class NetworkIdentityWriteFenceIntegrationTest {
    @Container
    private static final MariaDBContainer<?> DATABASE = new MariaDBContainer<>("mariadb:11.8.3")
            .withDatabaseName("enthusia_staff_identity_fence_test")
            .withUsername("enthusia_test")
            .withPassword("enthusia_test_password");

    @Test
    void maintenancePreservesObservationButForcesInheritanceSuppression() throws SQLException {
        try (MariaDbRuntime runtime = MariaDb.initialize(databaseConfig());
             HikariDataSource dataSource = dataSource()) {
            setMode(OperationalMode.MAINTENANCE);
            assertEquals(OperationalMode.MAINTENANCE, runtime.operationalStateStore().current().mode());
            TrackingNetworkIdentityStore delegate = new TrackingNetworkIdentityStore();
            NetworkIdentityStore store = new FencedNetworkIdentityStore(dataSource, delegate);

            NetworkIdentityObservationResult result = store.observeAndInherit(
                    null,
                    null,
                    Instant.EPOCH,
                    false
            );

            assertTrue(result.evidenceSuppressed());
            assertTrue(delegate.suppressAutomatedEvidence.get());
            assertEquals(1, delegate.observations.get());
            assertFalse(store.setRelationship(null, null, null, null, null, null));
            assertFalse(store.reopen(null, null, null, null, null));
            assertEquals(0, delegate.manualChanges.get());
        }
    }

    @Test
    void activeModePreservesRequestedInheritanceBehavior() throws SQLException {
        try (MariaDbRuntime runtime = MariaDb.initialize(databaseConfig());
             HikariDataSource dataSource = dataSource()) {
            setMode(OperationalMode.ACTIVE);
            assertEquals(OperationalMode.ACTIVE, runtime.operationalStateStore().current().mode());
            TrackingNetworkIdentityStore delegate = new TrackingNetworkIdentityStore();
            NetworkIdentityStore store = new FencedNetworkIdentityStore(dataSource, delegate);

            NetworkIdentityObservationResult result = store.observeAndInherit(
                    null,
                    null,
                    Instant.EPOCH,
                    false
            );

            assertFalse(result.evidenceSuppressed());
            assertFalse(delegate.suppressAutomatedEvidence.get());
            assertEquals(1, delegate.observations.get());
        }
    }

    private static void setMode(OperationalMode mode) throws SQLException {
        try (Connection connection = sourceConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     UPDATE operational_state
                     SET mode = ?, revision = revision + 1,
                         reason = 'Network identity fence integration test',
                         updated_at = CURRENT_TIMESTAMP(6)
                     WHERE singleton_id = 1
                     """)) {
            statement.setString(1, mode.name());
            assertEquals(1, statement.executeUpdate());
        }
    }

    private static HikariDataSource dataSource() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(jdbcUrl());
        config.setUsername(DATABASE.getUsername());
        config.setPassword(DATABASE.getPassword());
        config.setMaximumPoolSize(4);
        config.setConnectionTimeout(5_000);
        return new HikariDataSource(config);
    }

    private static DatabaseConfig databaseConfig() {
        return new DatabaseConfig(
                jdbcUrl(),
                DATABASE.getUsername(),
                DATABASE.getPassword(),
                4,
                5_000
        );
    }

    private static Connection sourceConnection() throws SQLException {
        return java.sql.DriverManager.getConnection(
                jdbcUrl(),
                DATABASE.getUsername(),
                DATABASE.getPassword()
        );
    }

    private static String jdbcUrl() {
        return DATABASE.getJdbcUrl().replace("jdbc:mysql:", "jdbc:mariadb:");
    }

    private static final class TrackingNetworkIdentityStore implements NetworkIdentityStore {
        private final AtomicInteger observations = new AtomicInteger();
        private final AtomicInteger manualChanges = new AtomicInteger();
        private final AtomicBoolean suppressAutomatedEvidence = new AtomicBoolean();

        @Override
        public NetworkIdentityObservationResult observeAndInherit(
                UUID joiningPlayerId,
                ProtectedNetworkIdentity identity,
                Instant observedAt,
                boolean suppressEvidence
        ) {
            observations.incrementAndGet();
            suppressAutomatedEvidence.set(suppressEvidence);
            return new NetworkIdentityObservationResult(0, 0, 0, suppressEvidence);
        }

        @Override
        public List<AltRelationshipSummary> relationships(UUID playerId) {
            return List.of();
        }

        @Override
        public boolean setRelationship(
                UUID firstPlayerId,
                UUID secondPlayerId,
                AltRelationshipState state,
                UUID actorId,
                Instant changedAt,
                String reason
        ) {
            manualChanges.incrementAndGet();
            return true;
        }

        @Override
        public boolean reopen(
                UUID firstPlayerId,
                UUID secondPlayerId,
                UUID actorId,
                Instant changedAt,
                String reason
        ) {
            manualChanges.incrementAndGet();
            return true;
        }
    }
}
