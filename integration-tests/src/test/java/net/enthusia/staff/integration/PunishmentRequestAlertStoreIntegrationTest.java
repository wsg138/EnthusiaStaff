package net.enthusia.staff.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.ByteBuffer;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import net.enthusia.staff.domain.application.PunishmentRequestAlertAudience;
import net.enthusia.staff.domain.application.PunishmentRequestAlertBacklog;
import net.enthusia.staff.domain.application.PunishmentRequestAlertClaim;
import net.enthusia.staff.domain.application.PunishmentRequestAlertDeliveryId;
import net.enthusia.staff.domain.application.PunishmentRequestAlertIntent;
import net.enthusia.staff.domain.application.PunishmentRequestAlertIntentKey;
import net.enthusia.staff.domain.application.PunishmentRequestLifecycleEventType;
import net.enthusia.staff.domain.auth.StaffRank;
import net.enthusia.staff.domain.casefile.CaseVisibility;
import net.enthusia.staff.domain.ports.PunishmentRequestAlertStore;
import net.enthusia.staff.persistence.MariaDb;
import net.enthusia.staff.persistence.MariaDbRuntime;
import net.enthusia.staff.persistence.ModerationPersistenceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class PunishmentRequestAlertStoreIntegrationTest {
    private static final Instant NOW = Instant.parse("2026-07-31T03:00:00Z");
    private static final Duration LEASE = Duration.ofMinutes(2);

    @Container
    private static final MariaDBContainer<?> DATABASE = new MariaDBContainer<>("mariadb:11.8.3")
            .withDatabaseName("enthusia_staff_alert_test")
            .withUsername("enthusia_test")
            .withPassword("enthusia_test_password");

    @BeforeEach
    void clearAlertState() throws Exception {
        try (MariaDbRuntime runtime = runtime()) {
            assertNotNull(runtime.punishmentRequestAlertStore());
        }
        try (Connection connection = connection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("DELETE FROM staff_alert_deliveries");
            statement.executeUpdate("DELETE FROM staff_alerts");
            statement.executeUpdate("DROP TRIGGER IF EXISTS fail_staff_alert_insert");
        }
    }

    @Test
    void directDeliveryIsUniqueIdempotentAndReclaimable() throws Exception {
        UUID recipient = UUID.randomUUID();
        PunishmentRequestAlertIntent intent = direct(recipient, 0, NOW, NOW.plus(Duration.ofHours(1)));
        try (MariaDbRuntime runtime = runtime()) {
            PunishmentRequestAlertStore store = runtime.punishmentRequestAlertStore();
            assertTrue(store.insert(intent));
            assertFalse(store.insert(withNewAlertId(intent)));
            assertEquals(1, deliveryCount(intent.alertId(), recipient));

            PunishmentRequestAlertClaim first = only(store.claimDirect(recipient, "paper:a", 10, LEASE, NOW));
            assertEquals(new PunishmentRequestAlertDeliveryId(intent.alertId(), recipient), first.deliveryId());
            assertFalse(store.delivered(first.deliveryId(), "paper:wrong", NOW.plusSeconds(1)));
            assertTrue(store.delivered(first.deliveryId(), "paper:a", NOW.plusSeconds(1)));
            assertTrue(store.claimDirect(recipient, "paper:b", 10, LEASE, NOW.plusSeconds(2)).isEmpty());
            assertEquals("CLOSED", intentState(intent.alertId()));

            UUID interruptedRecipient = UUID.randomUUID();
            PunishmentRequestAlertIntent interrupted = direct(
                    interruptedRecipient, 1, NOW.plusSeconds(3), NOW.plus(Duration.ofHours(1)));
            store.insert(interrupted);
            PunishmentRequestAlertClaim lost = only(store.claimDirect(
                    interruptedRecipient, "paper:lost", 10, LEASE, NOW.plusSeconds(4)));
            Instant reclaimAt = NOW.plusSeconds(4).plus(LEASE).plusSeconds(1);
            assertEquals(1, store.reclaimExpiredDeliveries(reclaimAt, 10));
            PunishmentRequestAlertClaim recovered = only(store.claimDirect(
                    interruptedRecipient, "paper:recovered", 10, LEASE, reclaimAt));
            assertEquals(lost.deliveryId(), recovered.deliveryId());
            assertFalse(store.delivered(lost.deliveryId(), "paper:lost", reclaimAt.plusSeconds(1)));
            assertTrue(store.delivered(recovered.deliveryId(), "paper:recovered", reclaimAt.plusSeconds(1)));
        }
    }

    @Test
    void reviewerAudienceIsIndependentAuthorizedAndRestartSafe() throws Exception {
        UUID requester = UUID.randomUUID();
        UUID reviewerA = UUID.randomUUID();
        UUID reviewerB = UUID.randomUUID();
        UUID reviewerC = UUID.randomUUID();
        PunishmentRequestAlertIntent intent = reviewer(requester, StaffRank.MOD, 0, NOW);
        try (MariaDbRuntime runtime = runtime()) {
            PunishmentRequestAlertStore store = runtime.punishmentRequestAlertStore();
            store.insert(intent);
            assertTrue(store.claimAudience(PunishmentRequestAlertAudience.ELIGIBLE_REVIEWERS,
                    requester, StaffRank.FOUNDER, "paper:self", 10, LEASE, NOW).isEmpty());
            assertTrue(store.claimAudience(PunishmentRequestAlertAudience.ELIGIBLE_REVIEWERS,
                    UUID.randomUUID(), StaffRank.DEVELOPER, "paper:developer", 10, LEASE, NOW).isEmpty());

            PunishmentRequestAlertClaim a = only(store.claimAudience(
                    PunishmentRequestAlertAudience.ELIGIBLE_REVIEWERS,
                    reviewerA, StaffRank.MOD, "paper:a", 10, LEASE, NOW));
            assertTrue(store.delivered(a.deliveryId(), "paper:a", NOW.plusSeconds(1)));
            assertTrue(store.claimAudience(PunishmentRequestAlertAudience.ELIGIBLE_REVIEWERS,
                    reviewerA, StaffRank.MOD, "paper:a2", 10, LEASE, NOW.plusSeconds(2)).isEmpty());

            PunishmentRequestAlertClaim b = only(store.claimAudience(
                    PunishmentRequestAlertAudience.ELIGIBLE_REVIEWERS,
                    reviewerB, StaffRank.ADMIN, "paper:b", 10, LEASE, NOW.plusSeconds(2)));
            assertTrue(store.delivered(b.deliveryId(), "paper:b", NOW.plusSeconds(3)));
            assertEquals("DELIVERED", deliveryState(intent.alertId(), reviewerA));
            assertEquals("DELIVERED", deliveryState(intent.alertId(), reviewerB));
            assertEquals("ACTIVE", intentState(intent.alertId()));
        }
        try (MariaDbRuntime restarted = runtime()) {
            PunishmentRequestAlertStore store = restarted.punishmentRequestAlertStore();
            assertTrue(store.claimAudience(PunishmentRequestAlertAudience.ELIGIBLE_REVIEWERS,
                    reviewerA, StaffRank.MOD, "paper:a3", 10, LEASE, NOW.plusSeconds(4)).isEmpty());
            PunishmentRequestAlertClaim c = only(store.claimAudience(
                    PunishmentRequestAlertAudience.ELIGIBLE_REVIEWERS,
                    reviewerC, StaffRank.FOUNDER, "paper:c", 10, LEASE, NOW.plusSeconds(4)));
            assertTrue(store.delivered(c.deliveryId(), "paper:c", NOW.plusSeconds(5)));
            assertEquals(3, deliveryCount(intent.alertId()));
        }
    }

    @Test
    void rankAndOperationalBoundariesAreEvaluatedPerRecipient() throws Exception {
        UUID requester = UUID.randomUUID();
        PunishmentRequestAlertIntent mod = reviewer(requester, StaffRank.MOD, 0, NOW);
        PunishmentRequestAlertIntent admin = reviewer(requester, StaffRank.ADMIN, 1, NOW.plusSeconds(1));
        PunishmentRequestAlertIntent founder = reviewer(requester, StaffRank.FOUNDER, 2, NOW.plusSeconds(2));
        PunishmentRequestAlertIntent operational = operational(3, NOW.plusSeconds(3));
        try (MariaDbRuntime runtime = runtime()) {
            PunishmentRequestAlertStore store = runtime.punishmentRequestAlertStore();
            List.of(mod, admin, founder, operational).forEach(store::insert);
            assertEquals(Set.of(mod.alertId()), alertIds(store.claimAudience(
                    PunishmentRequestAlertAudience.ELIGIBLE_REVIEWERS,
                    UUID.randomUUID(), StaffRank.MOD, "paper:mod", 10, LEASE, NOW.plusSeconds(4))));
            assertEquals(Set.of(mod.alertId(), admin.alertId()), alertIds(store.claimAudience(
                    PunishmentRequestAlertAudience.ELIGIBLE_REVIEWERS,
                    UUID.randomUUID(), StaffRank.ADMIN, "paper:admin", 10, LEASE, NOW.plusSeconds(4))));
            assertEquals(Set.of(mod.alertId(), admin.alertId(), founder.alertId()), alertIds(store.claimAudience(
                    PunishmentRequestAlertAudience.ELIGIBLE_REVIEWERS,
                    UUID.randomUUID(), StaffRank.FOUNDER, "paper:founder", 10, LEASE, NOW.plusSeconds(4))));
            assertTrue(store.claimAudience(PunishmentRequestAlertAudience.ELIGIBLE_REVIEWERS,
                    UUID.randomUUID(), StaffRank.HELPER, "paper:helper", 10, LEASE, NOW).isEmpty());

            UUID opsAdmin = UUID.randomUUID();
            UUID opsFounder = UUID.randomUUID();
            assertTrue(store.claimAudience(PunishmentRequestAlertAudience.OPERATIONAL_ADMINISTRATORS,
                    UUID.randomUUID(), StaffRank.MOD, "paper:ops-mod", 10, LEASE, NOW.plusSeconds(4)).isEmpty());
            assertTrue(store.claimAudience(PunishmentRequestAlertAudience.OPERATIONAL_ADMINISTRATORS,
                    UUID.randomUUID(), StaffRank.DEVELOPER, "paper:ops-dev", 10, LEASE, NOW.plusSeconds(4)).isEmpty());
            PunishmentRequestAlertClaim adminOps = only(store.claimAudience(
                    PunishmentRequestAlertAudience.OPERATIONAL_ADMINISTRATORS,
                    opsAdmin, StaffRank.ADMIN, "paper:ops-admin", 10, LEASE, NOW.plusSeconds(4)));
            PunishmentRequestAlertClaim founderOps = only(store.claimAudience(
                    PunishmentRequestAlertAudience.OPERATIONAL_ADMINISTRATORS,
                    opsFounder, StaffRank.FOUNDER, "paper:ops-founder", 10, LEASE, NOW.plusSeconds(4)));
            assertTrue(store.delivered(adminOps.deliveryId(), "paper:ops-admin", NOW.plusSeconds(5)));
            assertTrue(store.delivered(founderOps.deliveryId(), "paper:ops-founder", NOW.plusSeconds(5)));
            assertEquals("ACTIVE", intentState(operational.alertId()));
        }
    }

    @Test
    void concurrentClaimsFenceSameRecipientAndAllowDifferentRecipients() throws Exception {
        UUID requester = UUID.randomUUID();
        UUID sameRecipient = UUID.randomUUID();
        PunishmentRequestAlertIntent intent = reviewer(requester, StaffRank.MOD, 0, NOW);
        try (MariaDbRuntime runtime = runtime()) {
            PunishmentRequestAlertStore store = runtime.punishmentRequestAlertStore();
            store.insert(intent);
            List<List<PunishmentRequestAlertClaim>> same = concurrently(
                    () -> store.claimAudience(PunishmentRequestAlertAudience.ELIGIBLE_REVIEWERS,
                            sameRecipient, StaffRank.MOD, "paper:same-a", 10, LEASE, NOW),
                    () -> store.claimAudience(PunishmentRequestAlertAudience.ELIGIBLE_REVIEWERS,
                            sameRecipient, StaffRank.MOD, "paper:same-b", 10, LEASE, NOW));
            assertEquals(1, same.stream().mapToInt(List::size).sum());
            assertEquals(1, deliveryCount(intent.alertId(), sameRecipient));

            UUID recipientA = UUID.randomUUID();
            UUID recipientB = UUID.randomUUID();
            List<List<PunishmentRequestAlertClaim>> different = concurrently(
                    () -> store.claimAudience(PunishmentRequestAlertAudience.ELIGIBLE_REVIEWERS,
                            recipientA, StaffRank.ADMIN, "paper:different-a", 10, LEASE, NOW.plusSeconds(1)),
                    () -> store.claimAudience(PunishmentRequestAlertAudience.ELIGIBLE_REVIEWERS,
                            recipientB, StaffRank.FOUNDER, "paper:different-b", 10, LEASE, NOW.plusSeconds(1)));
            assertEquals(1, different.get(0).size());
            assertEquals(1, different.get(1).size());
            assertEquals(intent.alertId(), different.get(0).getFirst().deliveryId().alertId());
            assertEquals(intent.alertId(), different.get(1).getFirst().deliveryId().alertId());
        }
    }

    @Test
    void recipientFailureLeaseRecoveryAndBacklogAreIndependent() throws Exception {
        UUID requester = UUID.randomUUID();
        UUID failedRecipient = UUID.randomUUID();
        UUID leasedRecipient = UUID.randomUUID();
        UUID laterRecipient = UUID.randomUUID();
        PunishmentRequestAlertIntent intent = reviewer(requester, StaffRank.MOD, 0, NOW);
        try (MariaDbRuntime runtime = runtime()) {
            PunishmentRequestAlertStore store = runtime.punishmentRequestAlertStore();
            store.insert(intent);
            assertEquals(new PunishmentRequestAlertBacklog(1, 0, 0, 0, 0, 0), store.backlog(NOW));
            PunishmentRequestAlertClaim failed = only(store.claimAudience(
                    PunishmentRequestAlertAudience.ELIGIBLE_REVIEWERS,
                    failedRecipient, StaffRank.MOD, "paper:failed", 10, LEASE, NOW));
            PunishmentRequestAlertClaim leased = only(store.claimAudience(
                    PunishmentRequestAlertAudience.ELIGIBLE_REVIEWERS,
                    leasedRecipient, StaffRank.ADMIN, "paper:leased", 10, LEASE, NOW));
            assertTrue(store.failed(failed.deliveryId(), "paper:failed", "PERMANENT",
                    NOW.plusSeconds(1), NOW, 1));
            Instant reclaimAt = NOW.plus(LEASE).plusSeconds(1);
            assertEquals(new PunishmentRequestAlertBacklog(1, 0, 1, 0, 1, 1), store.backlog(reclaimAt));
            assertEquals(1, store.reclaimExpiredDeliveries(reclaimAt, 10));
            PunishmentRequestAlertClaim reclaimed = only(store.claimAudience(
                    PunishmentRequestAlertAudience.ELIGIBLE_REVIEWERS,
                    leasedRecipient, StaffRank.ADMIN, "paper:reclaimed", 10, LEASE, reclaimAt));
            assertEquals(leased.deliveryId(), reclaimed.deliveryId());
            assertFalse(store.delivered(leased.deliveryId(), "paper:leased", reclaimAt.plusSeconds(1)));
            assertTrue(store.delivered(reclaimed.deliveryId(), "paper:reclaimed", reclaimAt.plusSeconds(1)));
            assertEquals(new PunishmentRequestAlertBacklog(1, 0, 0, 1, 1, 0),
                    store.backlog(reclaimAt.plusSeconds(1)));
        }
        try (MariaDbRuntime restarted = runtime()) {
            PunishmentRequestAlertClaim later = only(restarted.punishmentRequestAlertStore().claimAudience(
                    PunishmentRequestAlertAudience.ELIGIBLE_REVIEWERS,
                    laterRecipient, StaffRank.FOUNDER, "paper:later", 10, LEASE, NOW.plus(LEASE).plusSeconds(3)));
            assertEquals(intent.alertId(), later.deliveryId().alertId());
            assertEquals("DEAD_LETTER", deliveryState(intent.alertId(), failedRecipient));
        }
    }

    @Test
    void intentExpiryClosureAndRetentionAreBounded() throws Exception {
        PunishmentRequestAlertIntent expired = reviewer(
                UUID.randomUUID(), StaffRank.MOD, 0,
                NOW.minus(Duration.ofHours(2)), NOW.minus(Duration.ofHours(1)));
        PunishmentRequestAlertIntent closed = reviewer(UUID.randomUUID(), StaffRank.MOD, 1, NOW);
        UUID crossingRecipient = UUID.randomUUID();
        PunishmentRequestAlertIntent crossing = direct(
                crossingRecipient, 2, NOW.minusSeconds(30), NOW.plusSeconds(1));
        try (MariaDbRuntime runtime = runtime()) {
            PunishmentRequestAlertStore store = runtime.punishmentRequestAlertStore();
            store.insert(expired);
            assertTrue(store.claimAudience(PunishmentRequestAlertAudience.ELIGIBLE_REVIEWERS,
                    UUID.randomUUID(), StaffRank.MOD, "paper:expired", 10, LEASE, NOW).isEmpty());
            assertEquals(1, store.expireIntents(NOW, 10));
            assertEquals("EXPIRED", intentState(expired.alertId()));

            store.insert(closed);
            assertTrue(store.closeIntent(closed.alertId(), "REQUEST_RESOLVED", NOW.plusSeconds(1)));
            assertTrue(store.claimAudience(PunishmentRequestAlertAudience.ELIGIBLE_REVIEWERS,
                    UUID.randomUUID(), StaffRank.FOUNDER, "paper:closed", 10, LEASE, NOW.plusSeconds(2)).isEmpty());

            store.insert(crossing);
            PunishmentRequestAlertClaim presented = only(store.claimDirect(
                    crossingRecipient, "paper:crossing", 10, LEASE, NOW));
            assertEquals(1, store.expireIntents(NOW.plusSeconds(2), 10));
            assertTrue(store.delivered(presented.deliveryId(), "paper:crossing", NOW.plusSeconds(2)));
            assertEquals("EXPIRED", intentState(crossing.alertId()));
            assertEquals("DELIVERED", deliveryState(crossing.alertId(), crossingRecipient));

            PunishmentRequestAlertIntent first = deliveredDirect(store, 10, NOW.minusSeconds(100));
            PunishmentRequestAlertIntent second = deliveredDirect(store, 11, NOW.minusSeconds(99));
            PunishmentRequestAlertIntent third = deliveredDirect(store, 12, NOW.minusSeconds(98));
            UUID deadRecipient = UUID.randomUUID();
            PunishmentRequestAlertIntent retained = reviewer(UUID.randomUUID(), StaffRank.MOD, 13, NOW.minusSeconds(90));
            store.insert(retained);
            PunishmentRequestAlertClaim dead = only(store.claimAudience(
                    PunishmentRequestAlertAudience.ELIGIBLE_REVIEWERS,
                    deadRecipient, StaffRank.MOD, "paper:dead", 10, LEASE, NOW.minusSeconds(50)));
            assertTrue(store.failed(dead.deliveryId(), "paper:dead", "PERMANENT",
                    NOW.minusSeconds(40), NOW.minusSeconds(50), 1));
            assertTrue(store.closeIntent(retained.alertId(), "REQUEST_RESOLVED", NOW.minusSeconds(30)));

            assertEquals(2, store.deleteTerminalIntentsBefore(NOW, 2));
            assertEquals(1, store.deleteTerminalIntentsBefore(NOW, 2));
            assertEquals(0, store.deleteTerminalIntentsBefore(NOW, 2));
            assertEquals(0, intentCount(first.alertId()) + intentCount(second.alertId()) + intentCount(third.alertId()));
            assertEquals(1, intentCount(retained.alertId()));
            assertEquals("DEAD_LETTER", deliveryState(retained.alertId(), deadRecipient));
        }
    }

    @Test
    void duplicateHandlingVerifiesCanonicalFieldsAndPropagatesOtherSqlFailures() throws Exception {
        PunishmentRequestAlertIntent canonical = direct(
                UUID.randomUUID(), 0, NOW, NOW.plus(Duration.ofHours(1)));
        try (MariaDbRuntime runtime = runtime()) {
            PunishmentRequestAlertStore store = runtime.punishmentRequestAlertStore();
            assertTrue(store.insert(canonical));
            assertFalse(store.insert(withNewAlertId(canonical)));

            List<PunishmentRequestAlertIntent> conflicts = List.of(
                    mutate(canonical, UUID.randomUUID(), canonical.intentKey(), UUID.randomUUID(),
                            canonical.requestRevision(), canonical.eventType(), canonical.audience(),
                            canonical.recipientId(), canonical.visibility(), canonical.schemaVersion(),
                            canonical.createdAt(), canonical.expiresAt()),
                    mutate(canonical, UUID.randomUUID(), canonical.intentKey(), canonical.requestId(),
                            canonical.requestRevision() + 1, canonical.eventType(), canonical.audience(),
                            canonical.recipientId(), canonical.visibility(), canonical.schemaVersion(),
                            canonical.createdAt(), canonical.expiresAt()),
                    mutate(canonical, UUID.randomUUID(), canonical.intentKey(), canonical.requestId(),
                            canonical.requestRevision(), PunishmentRequestLifecycleEventType.REQUEST_APPROVED,
                            canonical.audience(), canonical.recipientId(), canonical.visibility(),
                            canonical.schemaVersion(), canonical.createdAt(), canonical.expiresAt()),
                    mutate(canonical, UUID.randomUUID(), canonical.intentKey(), canonical.requestId(),
                            canonical.requestRevision(), canonical.eventType(), canonical.audience(),
                            UUID.randomUUID(), canonical.visibility(), canonical.schemaVersion(),
                            canonical.createdAt(), canonical.expiresAt()),
                    mutate(canonical, UUID.randomUUID(), canonical.intentKey(), canonical.requestId(),
                            canonical.requestRevision(), canonical.eventType(), canonical.audience(),
                            canonical.recipientId(), CaseVisibility.PUBLIC, canonical.schemaVersion(),
                            canonical.createdAt(), canonical.expiresAt()),
                    mutate(canonical, UUID.randomUUID(), canonical.intentKey(), canonical.requestId(),
                            canonical.requestRevision(), canonical.eventType(), canonical.audience(),
                            canonical.recipientId(), canonical.visibility(), canonical.schemaVersion() + 1,
                            canonical.createdAt(), canonical.expiresAt()),
                    mutate(canonical, UUID.randomUUID(), canonical.intentKey(), canonical.requestId(),
                            canonical.requestRevision(), canonical.eventType(), canonical.audience(),
                            canonical.recipientId(), canonical.visibility(), canonical.schemaVersion(),
                            canonical.createdAt().plusSeconds(1), canonical.expiresAt().plusSeconds(1)));
            for (PunishmentRequestAlertIntent conflict : conflicts) {
                assertThrows(ModerationPersistenceException.class, () -> store.insert(conflict));
            }

            PunishmentRequestAlertIntent other = direct(
                    UUID.randomUUID(), 5, NOW.plusSeconds(5), NOW.plus(Duration.ofHours(2)));
            PunishmentRequestAlertIntent duplicateAlertId = new PunishmentRequestAlertIntent(
                    canonical.alertId(), other.intentKey(), other.requestId(), other.requestRevision(),
                    other.eventType(), other.audience(), other.recipientId(), null, null,
                    other.visibility(), other.schemaVersion(), other.createdAt(), other.expiresAt());
            assertThrows(ModerationPersistenceException.class, () -> store.insert(duplicateAlertId));

            assertThrows(SQLException.class, () -> rawUpdate(
                    "UPDATE staff_alerts SET audience = 'INVALID_AUDIENCE' WHERE alert_id = ?", canonical.alertId()));
            assertThrows(SQLException.class, () -> rawUpdate(
                    "UPDATE staff_alerts SET intent_key = REPEAT('x', 161) WHERE alert_id = ?", canonical.alertId()));
            assertThrows(SQLException.class, () -> rawUpdate(
                    "UPDATE staff_alerts SET payload_json = 'not-json' WHERE alert_id = ?", canonical.alertId()));

            try (Connection connection = connection(); Statement statement = connection.createStatement()) {
                statement.executeUpdate("""
                        CREATE TRIGGER fail_staff_alert_insert BEFORE INSERT ON staff_alerts
                        FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'forced failure'
                        """);
            }
            try {
                assertThrows(ModerationPersistenceException.class, () -> store.insert(direct(
                        UUID.randomUUID(), 9, NOW.plusSeconds(9), NOW.plus(Duration.ofHours(2)))));
            } finally {
                try (Connection connection = connection(); Statement statement = connection.createStatement()) {
                    statement.executeUpdate("DROP TRIGGER IF EXISTS fail_staff_alert_insert");
                }
            }
        }
    }

    @Test
    void concurrentIdenticalProducersCreateOneCanonicalIntentAndDelivery() throws Exception {
        PunishmentRequestAlertIntent canonical = direct(
                UUID.randomUUID(), 0, NOW, NOW.plus(Duration.ofHours(1)));
        try (MariaDbRuntime runtime = runtime()) {
            PunishmentRequestAlertStore store = runtime.punishmentRequestAlertStore();
            List<Boolean> outcomes = concurrently(() -> store.insert(canonical),
                    () -> store.insert(withNewAlertId(canonical)));
            assertEquals(1L, outcomes.stream().filter(Boolean::booleanValue).count());
            assertEquals(1, intentCountByKey(canonical.intentKey()));
            UUID storedAlertId = alertIdByKey(canonical.intentKey());
            assertNotNull(storedAlertId);
            assertEquals(1, deliveryCount(storedAlertId, canonical.recipientId()));
        }
    }

    private static PunishmentRequestAlertIntent deliveredDirect(
            PunishmentRequestAlertStore store, long revision, Instant createdAt) throws Exception {
        UUID recipient = UUID.randomUUID();
        PunishmentRequestAlertIntent intent = direct(recipient, revision, createdAt, NOW.plus(Duration.ofHours(1)));
        store.insert(intent);
        PunishmentRequestAlertClaim claim = only(store.claimDirect(
                recipient, "paper:cleanup-" + revision, 10, LEASE, NOW.minusSeconds(50)));
        assertTrue(store.delivered(claim.deliveryId(), "paper:cleanup-" + revision, NOW.minusSeconds(40)));
        return intent;
    }

    private static MariaDbRuntime runtime() {
        return MariaDb.initialize(MariaDbIntegrationSupport.databaseConfig(DATABASE));
    }

    private static Connection connection() throws SQLException {
        return MariaDbIntegrationSupport.connection(DATABASE);
    }

    private static PunishmentRequestAlertClaim only(List<PunishmentRequestAlertClaim> claims) {
        assertEquals(1, claims.size());
        return claims.getFirst();
    }

    private static Set<UUID> alertIds(List<PunishmentRequestAlertClaim> claims) {
        Set<UUID> ids = new HashSet<>();
        claims.forEach(claim -> ids.add(claim.deliveryId().alertId()));
        return Set.copyOf(ids);
    }

    private static <T> List<T> concurrently(Callable<T> first, Callable<T> second) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<T> a = executor.submit(() -> { start.await(); return first.call(); });
            Future<T> b = executor.submit(() -> { start.await(); return second.call(); });
            start.countDown();
            return List.of(a.get(), b.get());
        } finally {
            executor.shutdownNow();
        }
    }

    private static PunishmentRequestAlertIntent direct(
            UUID recipient, long revision, Instant createdAt, Instant expiresAt) {
        return finalized(new PunishmentRequestAlertIntent(
                UUID.randomUUID(), "pending", UUID.randomUUID(), revision,
                PunishmentRequestLifecycleEventType.REQUEST_SUBMITTED,
                PunishmentRequestAlertAudience.DIRECT_RECIPIENT,
                recipient, null, null, CaseVisibility.PRIVATE, 1, createdAt, expiresAt));
    }

    private static PunishmentRequestAlertIntent reviewer(
            UUID requester, StaffRank rank, long revision, Instant createdAt) {
        return reviewer(requester, rank, revision, createdAt, createdAt.plus(Duration.ofDays(7)));
    }

    private static PunishmentRequestAlertIntent reviewer(
            UUID requester, StaffRank rank, long revision, Instant createdAt, Instant expiresAt) {
        return finalized(new PunishmentRequestAlertIntent(
                UUID.randomUUID(), "pending", UUID.randomUUID(), revision,
                PunishmentRequestLifecycleEventType.REQUEST_SUBMITTED,
                PunishmentRequestAlertAudience.ELIGIBLE_REVIEWERS,
                null, requester, rank, CaseVisibility.PRIVATE, 1, createdAt, expiresAt));
    }

    private static PunishmentRequestAlertIntent operational(long revision, Instant createdAt) {
        return finalized(new PunishmentRequestAlertIntent(
                UUID.randomUUID(), "pending", UUID.randomUUID(), revision,
                PunishmentRequestLifecycleEventType.REQUEST_SUBMITTED,
                PunishmentRequestAlertAudience.OPERATIONAL_ADMINISTRATORS,
                null, null, null, CaseVisibility.PRIVATE, 1,
                createdAt, createdAt.plus(Duration.ofDays(7))));
    }

    private static PunishmentRequestAlertIntent finalized(PunishmentRequestAlertIntent draft) {
        return new PunishmentRequestAlertIntent(
                draft.alertId(), PunishmentRequestAlertIntentKey.forIntent(draft), draft.requestId(),
                draft.requestRevision(), draft.eventType(), draft.audience(), draft.recipientId(),
                draft.excludedRecipientId(), draft.minimumRank(), draft.visibility(), draft.schemaVersion(),
                draft.createdAt(), draft.expiresAt());
    }

    private static PunishmentRequestAlertIntent withNewAlertId(PunishmentRequestAlertIntent source) {
        return new PunishmentRequestAlertIntent(
                UUID.randomUUID(), source.intentKey(), source.requestId(), source.requestRevision(),
                source.eventType(), source.audience(), source.recipientId(), source.excludedRecipientId(),
                source.minimumRank(), source.visibility(), source.schemaVersion(),
                source.createdAt(), source.expiresAt());
    }

    private static PunishmentRequestAlertIntent mutate(
            PunishmentRequestAlertIntent source, UUID alertId, String key, UUID requestId, long revision,
            PunishmentRequestLifecycleEventType event, PunishmentRequestAlertAudience audience,
            UUID recipient, CaseVisibility visibility, int schemaVersion, Instant createdAt, Instant expiresAt) {
        return new PunishmentRequestAlertIntent(
                alertId, key, requestId, revision, event, audience, recipient,
                source.excludedRecipientId(), source.minimumRank(), visibility, schemaVersion, createdAt, expiresAt);
    }

    private static int deliveryCount(UUID alertId, UUID recipientId) throws SQLException {
        return count("SELECT COUNT(*) FROM staff_alert_deliveries WHERE alert_id=? AND recipient_id=?",
                alertId, recipientId);
    }

    private static int deliveryCount(UUID alertId) throws SQLException {
        return count("SELECT COUNT(*) FROM staff_alert_deliveries WHERE alert_id=?", alertId);
    }

    private static int intentCount(UUID alertId) throws SQLException {
        return count("SELECT COUNT(*) FROM staff_alerts WHERE alert_id=?", alertId);
    }

    private static int count(String sql, UUID... ids) throws SQLException {
        try (Connection connection = connection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int index = 0; index < ids.length; index++) {
                statement.setBytes(index + 1, MariaDbIntegrationSupport.uuidBytes(ids[index]));
            }
            try (ResultSet result = statement.executeQuery()) {
                assertTrue(result.next());
                return result.getInt(1);
            }
        }
    }

    private static int intentCountByKey(String key) throws SQLException {
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT COUNT(*) FROM staff_alerts WHERE intent_key=?")) {
            statement.setString(1, key);
            try (ResultSet result = statement.executeQuery()) {
                assertTrue(result.next());
                return result.getInt(1);
            }
        }
    }

    private static String deliveryState(UUID alertId, UUID recipientId) throws SQLException {
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT state FROM staff_alert_deliveries WHERE alert_id=? AND recipient_id=?")) {
            statement.setBytes(1, MariaDbIntegrationSupport.uuidBytes(alertId));
            statement.setBytes(2, MariaDbIntegrationSupport.uuidBytes(recipientId));
            try (ResultSet result = statement.executeQuery()) {
                assertTrue(result.next());
                return result.getString(1);
            }
        }
    }

    private static String intentState(UUID alertId) throws SQLException {
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT intent_state FROM staff_alerts WHERE alert_id=?")) {
            statement.setBytes(1, MariaDbIntegrationSupport.uuidBytes(alertId));
            try (ResultSet result = statement.executeQuery()) {
                assertTrue(result.next());
                return result.getString(1);
            }
        }
    }

    private static UUID alertIdByKey(String key) throws SQLException {
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT alert_id FROM staff_alerts WHERE intent_key=?")) {
            statement.setString(1, key);
            try (ResultSet result = statement.executeQuery()) {
                assertTrue(result.next());
                ByteBuffer buffer = ByteBuffer.wrap(result.getBytes(1));
                return new UUID(buffer.getLong(), buffer.getLong());
            }
        }
    }

    private static void rawUpdate(String sql, UUID alertId) throws SQLException {
        try (Connection connection = connection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setBytes(1, MariaDbIntegrationSupport.uuidBytes(alertId));
            statement.executeUpdate();
        }
    }
}
