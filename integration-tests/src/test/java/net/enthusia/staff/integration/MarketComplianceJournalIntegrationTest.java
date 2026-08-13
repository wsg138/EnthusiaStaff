package net.enthusia.staff.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import net.enthusia.staff.common.CaseId;
import net.enthusia.staff.common.IdempotencyKey;
import net.enthusia.staff.domain.market.MarketComplianceKind;
import net.enthusia.staff.domain.market.MarketComplianceOperation;
import net.enthusia.staff.domain.market.MarketComplianceRequest;
import net.enthusia.staff.domain.market.MarketComplianceResult;
import net.enthusia.staff.domain.market.MarketComplianceState;
import net.enthusia.staff.domain.market.MarketComplianceUpdate;
import net.enthusia.staff.domain.ports.MarketComplianceStore;
import net.enthusia.staff.persistence.MariaDb;
import net.enthusia.staff.persistence.MariaDbRuntime;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class MarketComplianceJournalIntegrationTest {
    private static final Instant NOW = Instant.parse("2026-08-13T12:00:00Z");
    private static final UUID ACTOR = UUID.fromString("ad22f327-0b8d-4341-af5f-6de6dfddc21d");
    private static final UUID REVIEWER = UUID.fromString("1995c9bb-658b-4296-80b4-3e43fae01c8f");
    private static final UUID TARGET = UUID.fromString("e3b74350-8067-4af9-9c15-26644c43ea6f");
    private static final UUID OPERATION = UUID.fromString("714ad278-7c68-4e80-b475-3d77bb62decc");
    private static final String CASE_ID = "01JMARKET0000001";

    @Container
    private static final MariaDBContainer<?> DATABASE = new MariaDBContainer<>("mariadb:11.8.3")
            .withDatabaseName("enthusia_staff_market_test")
            .withUsername("enthusia_test")
            .withPassword("enthusia_test_password");

    @Test
    void journalSurvivesRestartTransitionsExactlyAndEmitsOneDueAlert() throws Exception {
        createFixtures(CASE_ID);
        MarketComplianceOperation prepared = prepareAndRestart();
        assertEquals(MarketComplianceState.PREPARED, prepared.state());

        try (MariaDbRuntime runtime = MariaDb.initialize(MariaDbIntegrationSupport.databaseConfig(DATABASE))) {
            MarketComplianceStore store = runtime.marketComplianceStore();
            assertEquals(1, store.emitDueReviewAlerts(NOW.plus(Duration.ofDays(8)), 10));
            assertEquals(0, store.emitDueReviewAlerts(NOW.plus(Duration.ofDays(8)), 10));
            assertEquals(1, count("staff_alerts", "alert_type = 'MARKET_REVIEW_DUE'"));
            assertEquals(1, count("discord_outbox", "event_type = 'MARKET_REVIEW_DUE'"));

            MarketComplianceOperation alerted = store.find(OPERATION).orElseThrow();
            MarketComplianceResult held = store.update(
                    OPERATION,
                    alerted.journalRevision(),
                    update(MarketComplianceState.MODERATION_HOLD, 2L, Optional.of(REVIEWER), "b")
            );
            MarketComplianceOperation heldOperation = held.operation().orElseThrow();
            assertEquals(MarketComplianceResult.Status.UPDATED, held.status());
            assertEquals(Optional.of(REVIEWER), heldOperation.reviewedBy());

            MarketComplianceResult restored = store.update(
                    OPERATION,
                    heldOperation.journalRevision(),
                    update(MarketComplianceState.RESTORED, 3L, Optional.of(REVIEWER), "a")
            );
            assertEquals(MarketComplianceState.RESTORED, restored.operation().orElseThrow().state());
            MarketComplianceResult backward = store.update(
                    OPERATION,
                    restored.operation().orElseThrow().journalRevision(),
                    update(MarketComplianceState.PREPARED, 4L, Optional.of(REVIEWER), "a")
            );
            assertEquals(MarketComplianceResult.Status.CONFLICT, backward.status());
        }
    }

    @Test
    void concurrentIdempotentStartsProduceOneDurableIntent() throws Exception {
        String caseId = "01JMARKET0000002";
        createFixtures(caseId);
        try (MariaDbRuntime runtime = MariaDb.initialize(MariaDbIntegrationSupport.databaseConfig(DATABASE))) {
            MarketComplianceStore store = runtime.marketComplianceStore();
            CountDownLatch start = new CountDownLatch(1);
            try (var executor = Executors.newFixedThreadPool(2)) {
                List<java.util.concurrent.Future<MarketComplianceResult>> futures = List.of(
                        executor.submit(() -> {
                            start.await();
                            return store.start(request(UUID.randomUUID(), caseId, "stall-race"));
                        }),
                        executor.submit(() -> {
                            start.await();
                            return store.start(request(UUID.randomUUID(), caseId, "stall-race"));
                        })
                );
                start.countDown();
                List<MarketComplianceResult.Status> statuses = futures.stream()
                        .map(future -> {
                            try {
                                return future.get().status();
                            } catch (Exception exception) {
                                throw new IllegalStateException(exception);
                            }
                        })
                        .toList();

                assertEquals(1L, statuses.stream()
                        .filter(status -> status == MarketComplianceResult.Status.CREATED)
                        .count());
                assertEquals(1L, statuses.stream()
                        .filter(status -> status == MarketComplianceResult.Status.REPLAYED)
                        .count());
                assertEquals(1, count("market_compliance_cases", "case_id = '" + caseId + "'"));
            }
        }
    }

    private static MarketComplianceOperation prepareAndRestart() {
        try (MariaDbRuntime runtime = MariaDb.initialize(MariaDbIntegrationSupport.databaseConfig(DATABASE))) {
            MarketComplianceStore store = runtime.marketComplianceStore();
            MarketComplianceResult started = store.start(request(OPERATION, CASE_ID, "stall-one"));
            assertTrue(started.status() == MarketComplianceResult.Status.CREATED
                    || started.status() == MarketComplianceResult.Status.REPLAYED);
            MarketComplianceOperation pending = started.operation().orElseThrow();
            MarketComplianceResult prepared = store.update(
                    OPERATION,
                    pending.journalRevision(),
                    update(MarketComplianceState.PREPARED, 1L, Optional.empty(), "a")
            );
            assertEquals(MarketComplianceResult.Status.UPDATED, prepared.status());
        }
        try (MariaDbRuntime runtime = MariaDb.initialize(MariaDbIntegrationSupport.databaseConfig(DATABASE))) {
            return runtime.marketComplianceStore().find(OPERATION).orElseThrow();
        }
    }

    private static MarketComplianceRequest request(UUID operationId, String caseId, String stallId) {
        return new MarketComplianceRequest(
                operationId,
                new IdempotencyKey("market:stall:" + caseId + ':' + stallId),
                new CaseId(caseId),
                TARGET,
                MarketComplianceKind.STALL,
                Optional.of(stallId),
                ACTOR,
                Optional.empty(),
                OptionalLong.empty(),
                NOW.plus(Duration.ofDays(7)),
                NOW.plus(Duration.ofDays(30)),
                NOW
        );
    }

    private static MarketComplianceUpdate update(
            MarketComplianceState state,
            long providerRevision,
            Optional<UUID> reviewer,
            String checksumCharacter
    ) {
        return new MarketComplianceUpdate(
                state,
                reviewer,
                Optional.of("a".repeat(64)),
                Optional.of(checksumCharacter.repeat(64)),
                providerRevision,
                "Provider state " + state,
                NOW.plusSeconds(providerRevision)
        );
    }

    private static void createFixtures(String caseId) throws Exception {
        try (MariaDbRuntime runtime = MariaDb.initialize(MariaDbIntegrationSupport.databaseConfig(DATABASE))) {
            assertNotNull(runtime.marketComplianceStore());
            MariaDbIntegrationSupport.insertPlayer(DATABASE, ACTOR, "MarketActor", NOW);
            MariaDbIntegrationSupport.insertPlayer(DATABASE, REVIEWER, "MarketReviewer", NOW);
            MariaDbIntegrationSupport.insertPlayer(DATABASE, TARGET, "MarketTarget", NOW);
            MariaDbIntegrationSupport.insertCase(DATABASE, caseId, TARGET, ACTOR, NOW);
        }
    }

    private static int count(String table, String predicate) throws Exception {
        try (Connection connection = MariaDbIntegrationSupport.connection(DATABASE);
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT COUNT(*) FROM " + table + " WHERE " + predicate);
             ResultSet result = statement.executeQuery()) {
            result.next();
            return result.getInt(1);
        }
    }
}
