package net.enthusia.staff.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import net.enthusia.staff.common.CaseId;
import net.enthusia.staff.domain.OperationalMode;
import net.enthusia.staff.domain.application.PunishmentApprovalLease;
import net.enthusia.staff.domain.application.PunishmentApprovalRequest;
import net.enthusia.staff.domain.application.PunishmentPlan;
import net.enthusia.staff.domain.application.PunishmentRequestResult;
import net.enthusia.staff.domain.application.PunishmentResult;
import net.enthusia.staff.domain.auth.Actor;
import net.enthusia.staff.domain.escalation.PriorOffense;
import net.enthusia.staff.domain.ports.ModerationStore;
import net.enthusia.staff.domain.ports.PunishmentRequestStore;
import net.enthusia.staff.domain.ports.SanctionMutationStore;
import net.enthusia.staff.domain.sanction.SanctionChangeRequest;
import net.enthusia.staff.domain.sanction.SanctionChangeResult;
import net.enthusia.staff.persistence.DatabaseConfig;
import net.enthusia.staff.persistence.MariaDb;
import net.enthusia.staff.persistence.MariaDbRuntime;
import net.enthusia.staff.persistence.ModerationPersistenceException;
import net.enthusia.staff.persistence.migration.FencedModerationStore;
import net.enthusia.staff.persistence.migration.FencedPunishmentRequestStore;
import net.enthusia.staff.persistence.migration.FencedSanctionMutationStore;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class AuthoritativeWriteFenceIntegrationTest {
    private static final CaseId CASE_ID = new CaseId("01HZX3K8M2N4P6QR");

    @Container
    private static final MariaDBContainer<?> DATABASE = new MariaDBContainer<>("mariadb:11.8.3")
            .withDatabaseName("enthusia_staff_fence_test")
            .withUsername("enthusia_test")
            .withPassword("enthusia_test_password");

    @Test
    void maintenanceBlocksEveryAuthoritativeStoreWithoutCallingDelegates() throws SQLException {
        try (MariaDbRuntime runtime = MariaDb.initialize(databaseConfig());
             HikariDataSource dataSource = dataSource()) {
            setMode(OperationalMode.MAINTENANCE);
            assertEquals(OperationalMode.MAINTENANCE, runtime.operationalStateStore().current().mode());
            AtomicInteger calls = new AtomicInteger();

            ModerationStore moderation = new FencedModerationStore(
                    dataSource,
                    trackingModeration(calls, null, null)
            );
            PunishmentRequestStore requests = new FencedPunishmentRequestStore(
                    dataSource,
                    new TrackingPunishmentRequestStore(calls)
            );
            SanctionMutationStore sanctions = new FencedSanctionMutationStore(
                    dataSource,
                    request -> {
                        calls.incrementAndGet();
                        return new SanctionChangeResult.Applied(1, false);
                    }
            );

            assertThrows(ModerationPersistenceException.class, () -> moderation.createPunishment(null));
            assertModeBlocked(requests.submit(null));
            assertTrue(requests.acquire(null, null, null, null).isEmpty());
            assertModeBlocked(requests.approve(null, null, null, null));
            assertModeBlocked(requests.deny(null, null, null, null));
            assertEquals(0, requests.expire(Instant.EPOCH));
            assertEquals(0, requests.expire(Instant.EPOCH, 25));
            assertModeBlocked(sanctions.apply(null));
            assertEquals(0, calls.get());
        }
    }

    @Test
    void activeModeAllowsAuthoritativeDelegates() throws SQLException {
        try (MariaDbRuntime runtime = MariaDb.initialize(databaseConfig());
             HikariDataSource dataSource = dataSource()) {
            setMode(OperationalMode.ACTIVE);
            assertEquals(OperationalMode.ACTIVE, runtime.operationalStateStore().current().mode());
            AtomicInteger calls = new AtomicInteger();
            ModerationStore moderation = new FencedModerationStore(
                    dataSource,
                    trackingModeration(calls, null, null)
            );
            SanctionMutationStore sanctions = new FencedSanctionMutationStore(
                    dataSource,
                    request -> {
                        calls.incrementAndGet();
                        return new SanctionChangeResult.Applied(1, false);
                    }
            );

            assertEquals(CASE_ID, moderation.createPunishment(null).caseId());
            assertInstanceOf(SanctionChangeResult.Applied.class, sanctions.apply(null));
            assertEquals(2, calls.get());
        }
    }

    @Test
    void operationalTransitionWaitsUntilGuardedWriteCompletes() throws Exception {
        try (MariaDbRuntime runtime = MariaDb.initialize(databaseConfig());
             HikariDataSource dataSource = dataSource();
             ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            setMode(OperationalMode.ACTIVE);
            assertEquals(OperationalMode.ACTIVE, runtime.operationalStateStore().current().mode());
            CountDownLatch delegateEntered = new CountDownLatch(1);
            CountDownLatch releaseDelegate = new CountDownLatch(1);
            ModerationStore moderation = new FencedModerationStore(
                    dataSource,
                    trackingModeration(new AtomicInteger(), delegateEntered, releaseDelegate)
            );

            Future<PunishmentResult.Accepted> write = executor.submit(
                    () -> moderation.createPunishment(null)
            );
            assertTrue(delegateEntered.await(5, TimeUnit.SECONDS));
            Future<Integer> transition = executor.submit(
                    () -> updateMode(OperationalMode.MAINTENANCE)
            );

            Thread.sleep(200);
            assertFalse(transition.isDone(), "mode transition must wait for the fenced write");
            releaseDelegate.countDown();

            assertEquals(CASE_ID, write.get(5, TimeUnit.SECONDS).caseId());
            assertEquals(1, transition.get(5, TimeUnit.SECONDS));
            assertEquals(OperationalMode.MAINTENANCE, currentMode());
        }
    }

    private static ModerationStore trackingModeration(
            AtomicInteger calls,
            CountDownLatch entered,
            CountDownLatch release
    ) {
        return new ModerationStore() {
            @Override
            public List<PriorOffense> relatedHistory(UUID targetId, String family) {
                return List.of();
            }

            @Override
            public PunishmentResult.Accepted createPunishment(PunishmentPlan plan) {
                calls.incrementAndGet();
                if (entered != null) {
                    entered.countDown();
                }
                if (release != null) {
                    try {
                        if (!release.await(5, TimeUnit.SECONDS)) {
                            throw new AssertionError("guarded write was not released");
                        }
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                        throw new AssertionError("guarded write was interrupted", exception);
                    }
                }
                return new PunishmentResult.Accepted(CASE_ID, false);
            }
        };
    }

    private static void assertModeBlocked(PunishmentRequestResult result) {
        PunishmentRequestResult.Rejected rejected = assertInstanceOf(
                PunishmentRequestResult.Rejected.class,
                result
        );
        assertEquals("MODE_BLOCKED", rejected.code());
    }

    private static void assertModeBlocked(SanctionChangeResult result) {
        SanctionChangeResult.Rejected rejected = assertInstanceOf(
                SanctionChangeResult.Rejected.class,
                result
        );
        assertEquals("MODE_BLOCKED", rejected.code());
    }

    private static void setMode(OperationalMode mode) throws SQLException {
        assertEquals(1, updateMode(mode));
    }

    private static int updateMode(OperationalMode mode) throws SQLException {
        try (Connection connection = sourceConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     UPDATE operational_state
                     SET mode = ?, revision = revision + 1, reason = 'Writer fence integration test',
                         updated_at = CURRENT_TIMESTAMP(6)
                     WHERE singleton_id = 1
                     """)) {
            statement.setString(1, mode.name());
            return statement.executeUpdate();
        }
    }

    private static OperationalMode currentMode() throws SQLException {
        try (Connection connection = sourceConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT mode FROM operational_state WHERE singleton_id = 1"
             );
             ResultSet result = statement.executeQuery()) {
            if (!result.next()) {
                throw new AssertionError("operational state singleton missing");
            }
            return OperationalMode.valueOf(result.getString(1));
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

    private static final class TrackingPunishmentRequestStore implements PunishmentRequestStore {
        private final AtomicInteger calls;

        private TrackingPunishmentRequestStore(AtomicInteger calls) {
            this.calls = calls;
        }

        @Override
        public PunishmentRequestResult submit(PunishmentApprovalRequest request) {
            calls.incrementAndGet();
            return new PunishmentRequestResult.Rejected("DELEGATED", "delegate called");
        }

        @Override
        public Optional<PunishmentApprovalRequest> find(UUID requestId) {
            return Optional.empty();
        }

        @Override
        public List<PunishmentApprovalRequest> pending(Instant now, int limit) {
            return List.of();
        }

        @Override
        public Optional<PunishmentApprovalLease> acquire(
                UUID requestId,
                UUID ownerId,
                Instant now,
                Instant leaseExpiresAt
        ) {
            calls.incrementAndGet();
            return Optional.empty();
        }

        @Override
        public PunishmentRequestResult approve(
                PunishmentApprovalLease lease,
                Actor approver,
                CaseId caseId,
                Instant now
        ) {
            calls.incrementAndGet();
            return new PunishmentRequestResult.Rejected("DELEGATED", "delegate called");
        }

        @Override
        public PunishmentRequestResult deny(
                PunishmentApprovalLease lease,
                Actor approver,
                String note,
                Instant now
        ) {
            calls.incrementAndGet();
            return new PunishmentRequestResult.Rejected("DELEGATED", "delegate called");
        }

        @Override
        public int expire(Instant now) {
            calls.incrementAndGet();
            return 1;
        }

        @Override
        public int expire(Instant now, int limit) {
            calls.incrementAndGet();
            return 1;
        }
    }
}
