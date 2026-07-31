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
import java.util.ArrayList;
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
    void clearAlertState() throws SQLException {
        try (MariaDbRuntime ignored = MariaDb.initialize(MariaDbIntegrationSupport.databaseConfig(DATABASE));
             Connection connection = MariaDbIntegrationSupport.connection(DATABASE);
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("DELETE FROM staff_alert_deliveries");
            statement.executeUpdate("DELETE FROM staff_alerts");
        }
    }

    @Test
    void directDeliveryIsUniqueIdempotentAndReclaimable() throws SQLException {
        UUID recipient = UUID.randomUUID();
        PunishmentRequestAlertIntent intent = direct(recipient, 0, NOW, NOW.plus(Duration.ofHours(1)));
        try (MariaDbRuntime runtime = runtime()) {
            PunishmentRequestAlertStore store = runtime.punishmentRequestAlertStore();
            assertTrue(store.insert(intent));
            assertFalse(store.insert(withNewAlertId(intent)));
            assertEquals(1, deliveryCount(intent.alertId(), recipient));

            PunishmentRequestAlertClaim claim = only(store.claimDirect(
                    recipient, "paper:direct-a", 10, LEASE, NOW));
            assertEquals(new PunishmentRequestAlertDeliveryId(intent.alertId(), recipient), claim.deliveryId());
            assertFalse(store.delivered(claim.deliveryId(), "paper:other", NOW.plusSeconds(1)));
            assertTrue(store.delivered(claim.deliveryId(), "paper:direct-a", NOW.plusSeconds(1)));
            assertTrue(store.claimDirect(recipient, "paper:direct-b", 10, LEASE, NOW.plusSeconds(2)).isEmpty());
            assertEquals("CLOSED", intentState(intent.alertId()));

            UUID interruptedRecipient = UUID.randomUUID();
            PunishmentRequestAlertIntent interrupted = direct(
                    interruptedRecipient, 1, NOW.plusSeconds(3), NOW.plus(Duration.ofHours(1)));
            assertTrue(store.insert(interrupted));
            PunishmentRequestAlertClaim original = only(store.claimDirect(
                    interruptedRecipient, "paper:lost", 10, LEASE, NOW.plusSeconds(4)));
            Instant reclaimedAt = NOW.plusSeconds(4).plus(LEASE).plusSeconds(1);
            assertEquals(1, store.reclaimExpiredDeliveries(reclaimedAt, 10));
            PunishmentRequestAlertClaim reclaimed = only(store.claimDirect(
                    interruptedRecipient, "paper:recovered", 10, LEASE, reclaimedAt));
            assertEquals(original.deliveryId(), reclaimed.deliveryId());
            assertFalse(store.delivered(original.deliveryId(), "paper:lost", reclaimedAt.plusSeconds(1)));
            assertTrue(store.delivered(reclaimed.deliveryId(), "paper:recovered", reclaimedAt.plusSeconds(1)));
        }
    }

    @Test
    void reviewerAudienceDeliveryIsIndependentAcrossRecipientsAndRestart() throws SQLException {
        UUID requester = UUID.randomUUID();
        UUID reviewerA = UUID.randomUUID();
        UUID reviewerB = UUID.randomUUID();
        UUID reviewerC = UUID.randomUUID();
        PunishmentRequestAlertIntent intent = reviewer(requester, StaffRank.MOD, 0, NOW);

        try (MariaDbRuntime runtime = runtime()) {
            PunishmentRequestAlertStore store = runtime.punishmentRequestAlertStore();
            assertTrue(store.insert(intent));
            assertTrue(store.claimAudience(PunishmentRequestAlertAudience.ELIGIBLE_REVIEWERS,
                    requester, StaffRank.FOUNDER, "paper:self", 10, LEASE, NOW).isEmpty());
            assertTrue(store.claimAudience(PunishmentRequestAlertAudience.ELIGIBLE_REVIEWERS,
                    UUID.randomUUID(), StaffRank.DEVELOPER, "paper:developer", 10, LEASE, NOW).isEmpty());

            PunishmentRequestAlertClaim first = only(store.claimAudience(
                    PunishmentRequestAlertAudience.ELIGIBLE_REVIEWERS,
                    reviewerA, StaffRank.MOD, "paper:a", 10, LEASE, NOW));
            assertTrue(store.delivered(first.deliveryId(), "paper:a", NOW.plusSeconds(1)));
            assertTrue(store.claimAudience(PunishmentRequestAlertAudience.ELIGIBLE_REVIEWERS,
                    reviewerA, StaffRank.MOD, "paper:a2", 10, LEASE, NOW.plusSeconds(2)).isEmpty());

            PunishmentRequestAlertClaim second = only(store.claimAudience(
                    PunishmentRequestAlertAudience.ELIGIBLE_REVIEWERS,
                    reviewerB, StaffRank.ADMIN, "paper:b", 10, LEASE, NOW.plusSeconds(2)));
            assertTrue(store.delivered(second.deliveryId(), "paper:b", NOW.plusSeconds(3)));
            assertEquals("DELIVERED", deliveryState(intent.alertId(), reviewerA));
            assertEquals("DELIVERED", deliveryState(intent.alertId(), reviewerB));
            assertEquals("ACTIVE", intentState(intent.alertId()));
        }

        try (MariaDbRuntime restarted = runtime()) {
            PunishmentRequestAlertStore store = restarted.punishmentRequestAlertStore();
            assertTrue(store.claimAudience(PunishmentRequestAlertAudience.ELIGIBLE_REVIEWERS,
                    reviewerA, StaffRank.MOD, "paper:a3", 10, LEASE, NOW.plusSeconds(4)).isEmpty());
            PunishmentRequestAlertClaim later = only(store.claimAudience(
                    PunishmentRequestAlertAudience.ELIGIBLE_REVIEWERS,
                    reviewerC, StaffRank.FOUNDER, "paper:c", 10, LEASE, NOW.plusSeconds(4)));
            assertTrue(store.delivered(later.deliveryId(), "paper:c", NOW.plusSeconds(5)));
            assertEquals(3, deliveryCount(intent.alertId()));
        }
    }

    @Test
    void reviewerMinimumRankBoundariesAreReevaluatedPerRecipient() {
        UUID requester = UUID.randomUUID();
        PunishmentRequestAlertIntent mod = reviewer(requester, StaffRank.MOD, 0, NOW);
        PunishmentRequestAlertIntent admin = reviewer(requester, StaffRank.ADMIN, 1, NOW.plusSeconds(1));
        PunishmentRequestAlertIntent founder = reviewer(requester, StaffRank.FOUNDER, 2, NOW.plusSeconds(2));
        try (MariaDbRuntime runtime = runtime()) {
            PunishmentRequestAlertStore store = runtime.punishmentRequestAlertStore();
            store.insert(mod);
            store.insert(admin);
            store.insert(founder);

            assertEquals(Set.of(mod.alertId()), alertIds(store.claimAudience(
                    PunishmentRequestAlertAudience.ELIGIBLE_REVIEWERS,
                    UUID.randomUUID(), StaffRank.MOD, "paper:mod", 10, LEASE, NOW.plusSeconds(3))));
            assertEquals(Set.of(mod.alertId(), admin.alertId()), alertIds(store.claimAudience(
                    PunishmentRequestAlertAudience.ELIGIBLE_REVIEWERS,
                    UUID.randomUUID(), StaffRank.ADMIN, "paper:admin", 10, LEASE, NOW.plusSeconds(3))));
            assertEquals(Set.of(mod.alertId(), admin.alertId(), founder.alertId()), alertIds(store.claimAudience(
                    PunishmentRequestAlertAudience.ELIGIBLE_REVIEWERS,
                    UUID.randomUUID(), StaffRank.FOUNDER, "paper:founder", 10, LEASE, NOW.plusSeconds(3))));
            assertTrue(store.claimAudience(PunishmentRequestAlertAudience.ELIGIBLE_REVIEWERS,
                    UUID.randomUUID(), StaffRank.HELPER, "paper:helper", 10, LEASE, NOW.plusSeconds(3)).isEmpty());
            assertTrue(store.claimAudience(PunishmentRequestAlertAudience.ELIGIBLE_REVIEWERS,
                    UUID.randomUUID(), StaffRank.DEVELOPER, "paper:developer", 10, LEASE,
                    NOW.plusSeconds(3)).isEmpty());
        }
    }

    @Test
    void operationalAudienceIsIndependentAndRestrictedToAdminAndFounder() {
        UUID admin = UUID.randomUUID();
        UUID founder = UUID.randomUUID();
        PunishmentRequestAlertIntent intent = operational(0, NOW);
        try (MariaDbRuntime runtime = runtime()) {
            PunishmentRequestAlertStore store = runtime.punishmentRequestAlertStore();
            store.insert(intent);
            assertTrue(store.claimAudience(PunishmentRequestAlertAudience.OPERATIONAL_ADMINISTRATORS,
                    UUID.randomUUID(), StaffRank.MOD, "paper:mod", 10, LEASE, NOW).isEmpty());
            assertTrue(store.claimAudience(PunishmentRequestAlertAudience.OPERATIONAL_ADMINISTRATORS,
                    UUID.randomUUID(), StaffRank.DEVELOPER, "paper:developer", 10, LEASE, NOW).isEmpty());

            PunishmentRequestAlertClaim adminClaim = only(store.claimAudience(
                    PunishmentRequestAlertAudience.OPERATIONAL_ADMINISTRATORS,
                    admin, StaffRank.ADMIN, "paper:admin", 10, LEASE, NOW));
            assertTrue(store.delivered(adminClaim.deliveryId(), "paper:admin", NOW.plusSeconds(1)));
            PunishmentRequestAlertClaim founderClaim = only(store.claimAudience(
                    PunishmentRequestAlertAudience.OPERATIONAL_ADMINISTRATORS,
                    founder, StaffRank.FOUNDER, "paper:founder", 10, LEASE, NOW.plusSeconds(1)));
            assertTrue(store.delivered(founderClaim.deliveryId(), "paper:founder", NOW.plusSeconds(2)));
            assertEquals("DELIVERED", deliveryState(intent.alertId(), admin));
            assertEquals("DELIVERED", deliveryState(intent.alertId(), founder));
            assertEquals("ACTIVE", intentState(intent.alertId()));
        }
    }

    @Test
    void concurrentClaimsFenceSameRecipientButAllowDifferentRecipients() throws Exception {
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
                            sameRecipient, StaffRank.MOD, "paper:same-b", 10, LEASE, NOW)
            );
            assertEquals(1, same.stream().mapToInt(List::size).sum());
            assertEquals(1, deliveryCount(intent.alertId(), sameRecipient));

            UUID recipientA = UUID.randomUUID();
            UUID recipientB = UUID.randomUUID();
            List<List<PunishmentRequestAlertClaim>> different = concurrently(
                    () -> store.claimAudience(PunishmentRequestAlertAudience.ELIGIBLE_REVIEWERS,
                            recipientA, StaffRank.ADMIN, "paper:different-a", 10, LEASE, NOW.plusSeconds(1)),
                    () -> store.claimAudience(PunishmentRequestAlertAudience.ELIGIBLE_REVIEWERS,
                            recipientB, StaffRank.FOUNDER, "paper:different-b", 10, LEASE, NOW.plusSeconds(1))
            );
            assertEquals(1, different.get(0).size());
            assertEquals(1, different.get(1).size());
            assertEquals(intent.alertId(), different.get(0).getFirst().deliveryId().alertId());
            assertEquals(intent.alertId(), different.get(1).getFirst().deliveryId().alertId());
        }
    }

    @Test
    void recipientFailureAndDeadLetterDoNotSuppressOtherRecipients() throws SQLException {
        UUID requester = UUID.randomUUID();
        UUID failedRecipient = UUID.randomUUID();
        UUID deliveredRecipient = UUID.randomUUID();
        UUID laterRecipient = UUID.randomUUID();
        PunishmentRequestAlertIntent intent = reviewer(requester, StaffRank.MOD, 0, NOW);
        try (MariaDbRuntime runtime = runtime()) {
            PunishmentRequestAlertStore store = runtime.punishmentRequestAlertStore();
            store.insert(intent);
            PunishmentRequestAlertClaim failed = only(store.claimAudience(
                    PunishmentRequestAlertAudience.ELIGIBLE_REVIEWERS,
                    failedRecipient, StaffRank.MOD, "paper:failed", 10, LEASE, NOW));
            assertTrue(store.failed(failed.deliveryId(), "paper:failed", "PERMANENT",
                    NOW.plusSeconds(1), NOW, 1));

            PunishmentRequestAlertClaim delivered = only(store.claimAudience(
                    PunishmentRequestAlertAudience.ELIGIBLE_REVIEWERS,
                    deliveredRecipient, StaffRank.ADMIN, "paper:delivered", 10, LEASE, NOW));
            assertTrue(store.delivered(delivered.deliveryId(), "paper:delivered", NOW.plusSeconds(1)));
            assertEquals("DEAD_LETTER", deliveryState(intent.alertId(), failedRecipient));
            assertEquals("DELIVERED", deliveryState(intent.alertId(), deliveredRecipient));
        }
        try (MariaDbRuntime restarted = runtime()) {
            PunishmentRequestAlertClaim later = only(restarted.punishmentRequestAlertStore().claimAudience(
                    PunishmentRequestAlertAudience.ELIGIBLE_REVIEWERS,
                    laterRecipient, StaffRank.FOUNDER, "paper:later", 10, LEASE, NOW.plusSeconds(2)));
            assertEquals(intent.alertId(), later.deliveryId().alertId());
            assertEquals(3, deliveryCount(intent.alertId()));
        }
    }

    @Test
    void expiredAndClosedIntentsRejectNewRecipientsButLeasedDeliveryMayAcknowledge() {
        PunishmentRequestAlertIntent expired = reviewer(
                UUID.randomUUID(), StaffRank.MOD, 0, NOW.minus(Duration.ofHours(2)),
                NOW.minus(Duration.ofHours(1)));
        PunishmentRequestAlertIntent closed = reviewer(UUID.randomUUID(), StaffRank.MOD, 1, NOW);
        UUID directRecipient = UUID.randomUUID();
        PunishmentRequestAlertIntent crossesExpiry = direct(
                directRecipient, 2, NOW.minusSeconds(30), NOW.plusSeconds(1));
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
                    UUID.randomUUID(), StaffRank.FOUNDER, "paper:closed", 10, LEASE,
                    NOW.plusSeconds(2)).isEmpty());

            store.insert(crossesExpiry);
            PunishmentRequestAlertClaim claim = only(store.claimDirect(
                    directRecipient, "paper:crossing", 10, LEASE, NOW));
            assertEquals(1, store.expireIntents(NOW.plusSeconds(2), 10));
            assertTrue(store.delivered(claim.deliveryId(), "paper:crossing", NOW.plusSeconds(2)));
            assertEquals("EXPIRED", intentState(crossesExpiry.alertId()));
            assertEquals("DELIVERED", deliveryState(crossesExpiry.alertId(), directRecipient));
        }
    }

    @Test
    void retentionCleanupIsBoundedDeterministicAndPreservesRequiredDeliveries() throws SQLException {
        List<PunishmentRequestAlertIntent> delivered = new ArrayList<>();
        try (MariaDbRuntime runtime = runtime()) {
            PunishmentRequestAlertStore store = runtime.punishmentRequestAlertStore();
            for (int index = 0; index < 3; index++) {
                UUID recipient = UUID.randomUUID();
                PunishmentRequestAlertIntent intent = direct(
                        recipient,
                        index,
                        NOW.minusSeconds(100 - index),
                        NOW.plus(Duration.ofHours(1))
                );
                delivered.add(intent);
                store.insert(intent);
                PunishmentRequestAlertClaim claim = only(store.claimDirect(
                        recipient, "paper:cleanup-" + index, 10, LEASE, NOW.minusSeconds(50)));
                assertTrue(store.delivered(claim.deliveryId(), "paper:cleanup-" + index,
                        NOW.minusSeconds(40 - index)));
            }

            UUID deadRecipient = UUID.randomUUID();
            PunishmentRequestAlertIntent retained = reviewer(UUID.randomUUID(), StaffRank.MOD, 10, NOW.minusSeconds(90));
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
            assertEquals(1, intentCount(retained.alertId()));
            assertEquals("DEAD_LETTER", deliveryState(retained.alertId(), deadRecipient));
            for (PunishmentRequestAlertIntent intent : delivered) {
                assertEquals(0, intentCount(intent.alertId()));
            }
        }
    }

    @Test
    void backlogSeparatesActiveIntentsAndRecipientDeliveryStates() {
        UUID requester = UUID.randomUUID();
        UUID recipientA = UUID.randomUUID();
        UUID recipientB = UUID.randomUUID();
        PunishmentRequestAlertIntent intent = reviewer(requester, StaffRank.MOD, 0, NOW);
        try (MariaDbRuntime runtime = runtime()) {
            PunishmentRequestAlertStore store = runtime.punishmentRequestAlertStore();
            store.insert(intent);
            assertEquals(new PunishmentRequestAlertBacklog(1, 0, 0, 0, 0, 0), store.backlog(NOW));

            PunishmentRequestAlertClaim first = only(store.claimAudience(
                    PunishmentRequestAlertAudience.ELIGIBLE_REVIEWERS,
                    recipientA, StaffRank.MOD, "paper:a", 10, LEASE, NOW));
            PunishmentRequestAlertClaim second = only(store.claimAudience(
                    PunishmentRequestAlertAudience.ELIGIBLE_REVIEWERS,
                    recipientB, StaffRank.ADMIN, "paper:b", 10, LEASE, NOW));
            assertEquals(new PunishmentRequestAlertBacklog(1, 0, 2, 0, 0, 0), store.backlog(NOW));

            assertTrue(store.failed(first.deliveryId(), "paper:a", "PERMANENT",
                    NOW.plusSeconds(1), NOW, 1));
            assertEquals(new PunishmentRequestAlertBacklog(1, 0, 1, 0, 1, 1),
                    store.backlog(NOW.plus(LEASE).plusSeconds(1)));
            assertEquals(1, store.reclaimExpiredDeliveries(NOW.plus(LEASE).plusSeconds(1), 10));
            PunishmentRequestAlertClaim reclaimed = only(store.claimAudience(
                    PunishmentRequestAlertAudience.ELIGIBLE_REVIEWERS,
                    recipientB, StaffRank.ADMIN, "paper:b2", 10, LEASE,
                    NOW.plus(LEASE).plusSeconds(1)));
            assertTrue(store.delivered(reclaimed.deliveryId(), "paper:b2",
                    NOW.plus(LEASE).plusSeconds(2)));
            assertEquals(new PunishmentRequestAlertBacklog(1, 0, 0, 1, 1, 0),
                    store.backlog(NOW.plus(LEASE).plusSeconds(2)));
            assertEquals(second.deliveryId(), reclaimed.deliveryId());
        }
    }

    @Test
    void duplicateIntentHandlingRequiresExactCanonicalMatch() throws SQLException {
        PunishmentRequestAlertIntent canonical = direct(
                UUID.randomUUID(), 0, NOW, NOW.plus(Duration.ofHours(1)));
        try (MariaDbRuntime runtime = runtime()) {
            PunishmentRequestAlertStore store = runtime.punishmentRequestAlertStore();
            assertTrue(store.insert(canonical));
            assertFalse(store.insert(withNewAlertId(canonical)));

            List<PunishmentRequestAlertIntent> conflicts = List.of(
                    copy(canonical, UUID.randomUUID(), canonical.intentKey(), UUID.randomUUID(),
                            canonical.requestRevision(), canonical.eventType(), canonical.audience(),
                            canonical.recipientId(), canonical.excludedRecipientId(), canonical.minimumRank(),
                            canonical.visibility(), canonical.schemaVersion(), canonical.createdAt(),
                            canonical.expiresAt()),
                    copy(canonical, UUID.randomUUID(), canonical.intentKey(), canonical.requestId(),
                            canonical.requestRevision() + 1, canonical.eventType(), canonical.audience(),
                            canonical.recipientId(), canonical.excludedRecipientId(), canonical.minimumRank(),
                            canonical.visibility(), canonical.schemaVersion(), canonical.createdAt(),
                            canonical.expiresAt()),
                    copy(canonical, UUID.randomUUID(), canonical.intentKey(), canonical.requestId(),
                            canonical.requestRevision(), PunishmentRequestLifecycleEventType.REQUEST_APPROVED,
                            canonical.audience(), canonical.recipientId(), null, null,
                            canonical.visibility(), canonical.schemaVersion(), canonical.createdAt(),
                            canonical.expiresAt()),
                    copy(canonical, UUID.randomUUID(), canonical.intentKey(), canonical.requestId(),
                            canonical.requestRevision(), canonical.eventType(), canonical.audience(),
                            UUID.randomUUID(), null, null, canonical.visibility(), canonical.schemaVersion(),
                            canonical.createdAt(), canonical.expiresAt()),
                    copy(canonical, UUID.randomUUID(), canonical.intentKey(), canonical.requestId(),
                            canonical.requestRevision(), canonical.eventType(),
                            PunishmentRequestAlertAudience.ELIGIBLE_REVIEWERS,
                            null, UUID.randomUUID(), StaffRank.MOD, canonical.visibility(),
                            canonical.schemaVersion(), canonical.createdAt(), canonical.expiresAt()),
                    copy(canonical, UUID.randomUUID(), canonical.intentKey(), canonical.requestId(),
                            canonical.requestRevision(), canonical.eventType(), canonical.audience(),
                            canonical.recipientId(), null, null, CaseVisibility.PUBLIC,
                            canonical.schemaVersion(), canonical.createdAt(), canonical.expiresAt()),
                    copy(canonical, UUID.randomUUID(), canonical.intentKey(), canonical.requestId(),
                            canonical.requestRevision(), canonical.eventType(), canonical.audience(),
                            canonical.recipientId(), null, null, canonical.visibility(),
                            canonical.schemaVersion() + 1, canonical.createdAt(), canonical.expiresAt()),
                    copy(canonical, UUID.randomUUID(), canonical.intentKey(), canonical.requestId(),
                            canonical.requestRevision(), canonical.eventType(), canonical.audience(),
                            canonical.recipientId(), null, null, canonical.visibility(), canonical.schemaVersion(),
                            canonical.createdAt().plusSeconds(1), canonical.expiresAt().plusSeconds(1))
            );
            for (PunishmentRequestAlertIntent conflict : conflicts) {
                assertThrows(ModerationPersistenceException.class, () -> store.insert(conflict));
            }

            PunishmentRequestAlertIntent differentIntent = direct(
                    UUID.randomUUID(), 5, NOW.plusSeconds(5), NOW.plus(Duration.ofHours(2)));
            PunishmentRequestAlertIntent duplicateAlertId = copy(
                    differentIntent,
                    canonical.alertId(),
                    differentIntent.intentKey(),
                    differentIntent.requestId(),
                    differentIntent.requestRevision(),
                    differentIntent.eventType(),
                    differentIntent.audience(),
                    differentIntent.recipientId(),
                    differentIntent.excludedRecipientId(),
                    differentIntent.minimumRank(),
                    differentIntent.visibility(),
                    differentIntent.schemaVersion(),
                    differentIntent.createdAt(),
                    differentIntent.expiresAt()
            );
            assertThrows(ModerationPersistenceException.class, () -> store.insert(duplicateAlertId));
            assertEquals(1, intentCount(canonical.alertId()));
            assertEquals(1, deliveryCount(canonical.alertId(), canonical.recipientId()));
        }
    }

    @Test
    void invalidDatabaseValuesAndUnrelatedSqlFailuresAreNotTreatedAsReplays() throws SQLException {
        PunishmentRequestAlertIntent canonical = direct(
                UUID.randomUUID(), 0, NOW, NOW.plus(Duration.ofHours(1)));
        try (MariaDbRuntime runtime = runtime()) {
            PunishmentRequestAlertStore store = runtime.punishmentRequestAlertStore();
            store.insert(canonical);
            assertThrows(SQLException.class, () -> rawUpdate(
                    "UPDATE staff_alerts SET audience = 'INVALID_AUDIENCE' WHERE alert_id = ?",
                    canonical.alertId()));
            assertThrows(SQLException.class, () -> rawUpdate(
                    "UPDATE staff_alerts SET intent_key = REPEAT('x', 161) WHERE alert_id = ?",
                    canonical.alertId()));
            assertThrows(SQLException.class, () -> rawUpdate(
                    "UPDATE staff_alerts SET payload_json = 'not-json' WHERE alert_id = ?",
                    canonical.alertId()));

            try (Connection connection = MariaDbIntegrationSupport.connection(DATABASE);
                 Statement statement = connection.createStatement()) {
                statement.executeUpdate("DROP TRIGGER IF EXISTS fail_staff_alert_insert");
                statement.executeUpdate("""
                        CREATE TRIGGER fail_staff_alert_insert
                        BEFORE INSERT ON staff_alerts
                        FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'forced non-duplicate failure'
                        """);
            }
            try {
                assertThrows(ModerationPersistenceException.class, () -> store.insert(direct(
                        UUID.randomUUID(), 1, NOW.plusSeconds(1), NOW.plus(Duration.ofHours(1)))));
            } finally {
                try (Connection connection = MariaDbIntegrationSupport.connection(DATABASE);
                     Statement statement = connection.createStatement()) {
                    statement.executeUpdate("DROP TRIGGER IF EXISTS fail_staff_alert_insert");
                }
            }
        }
    }

    @Test
    void concurrentIdenticalProducersCreateOneCanonicalIntentAndDelivery() throws Exception {
        PunishmentRequestAlertIntent canonical = direct(
                UUID.randomUUID(), 0, NOW, NOW.plus(Duration.ofHours(1)));
        PunishmentRequestAlertIntent replay = withNewAlertId(canonical);
        try (MariaDbRuntime runtime = runtime()) {
            PunishmentRequestAlertStore store = runtime.punishmentRequestAlertStore();
            List<Boolean> outcomes = concurrently(() -> store.insert(canonical), () -> store.insert(replay));
            assertEquals(1L, outcomes.stream().filter(Boolean::booleanValue).count());
            assertEquals(1, intentCountByKey(canonical.intentKey()));
            UUID storedAlertId = alertIdByKey(canonical.intentKey());
            assertNotNull(storedAlertId);
            assertEquals(1, deliveryCount(storedAlertId, canonical.recipientId()));
        }
    }

    private static MariaDbRuntime runtime() {
        return MariaDb.initialize(MariaDbIntegrationSupport.databaseConfig(DATABASE));
    }

    private static PunishmentRequestAlertClaim only(List<PunishmentRequestAlertClaim> claims) {
        assertEquals(1, claims.size());
        return claims.getFirst();
    }

    private static Set<UUID> alertIds(List<PunishmentRequestAlertClaim> claims) {
        Set<UUID> identifiers = new HashSet<>();
        for (PunishmentRequestAlertClaim claim : claims) {
            identifiers.add(claim.deliveryId().alertId());
        }
        return Set.copyOf(identifiers);
    }

    private static <T> List<T> concurrently(Callable<T> first, Callable<T> second) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<T> firstFuture = executor.submit(() -> {
                start.await();
                return first.call();
            });
            Future<T> secondFuture = executor.submit(() -> {
                start.await();
                return second.call();
            });
            start.countDown();
            return List.of(firstFuture.get(), secondFuture.get());
        } finally {
            executor.shutdownNow();
        }
    }

    private static PunishmentRequestAlertIntent direct(
            UUID recipient,
            long revision,
            Instant createdAt,
            Instant expiresAt
    ) {
        return finalized(new PunishmentRequestAlertIntent(
                UUID.randomUUID(), "pending", UUID.randomUUID(), revision,
                PunishmentRequestLifecycleEventType.REQUEST_SUBMITTED,
                PunishmentRequestAlertAudience.DIRECT_RECIPIENT,
                recipient, null, null, CaseVisibility.PRIVATE, 1, createdAt, expiresAt
        ));
    }

    private static PunishmentRequestAlertIntent reviewer(
            UUID requester,
            StaffRank minimumRank,
            long revision,
            Instant createdAt
    ) {
        return reviewer(requester, minimumRank, revision, createdAt, createdAt.plus(Duration.ofDays(7)));
    }

    private static PunishmentRequestAlertIntent reviewer(
            UUID requester,
            StaffRank minimumRank,
            long revision,
            Instant createdAt,
            Instant expiresAt
    ) {
        return finalized(new PunishmentRequestAlertIntent(
                UUID.randomUUID(), "pending", UUID.randomUUID(), revision,
                PunishmentRequestLifecycleEventType.REQUEST_SUBMITTED,
                PunishmentRequestAlertAudience.ELIGIBLE_REVIEWERS,
                null, requester, minimumRank, CaseVisibility.PRIVATE, 1, createdAt, expiresAt
        ));
    }

    private static PunishmentRequestAlertIntent operational(long revision, Instant createdAt) {
        return finalized(new PunishmentRequestAlertIntent(
                UUID.randomUUID(), "pending", UUID.randomUUID(), revision,
                PunishmentRequestLifecycleEventType.REQUEST_SUBMITTED,
                PunishmentRequestAlertAudience.OPERATIONAL_ADMINISTRATORS,
                null, null, null, CaseVisibility.PRIVATE, 1,
                createdAt, createdAt.plus(Duration.ofDays(7))
        ));
    }

    private static PunishmentRequestAlertIntent finalized(PunishmentRequestAlertIntent draft) {
        return copy(
                draft,
                draft.alertId(),
                PunishmentRequestAlertIntentKey.forIntent(draft),
                draft.requestId(),
                draft.requestRevision(),
                draft.eventType(),
                draft.audience(),
                draft.recipientId(),
                draft.excludedRecipientId(),
                draft.minimumRank(),
                draft.visibility(),
                draft.schemaVersion(),
                draft.createdAt(),
                draft.expiresAt()
        );
    }

    private static PunishmentRequestAlertIntent withNewAlertId(PunishmentRequestAlertIntent source) {
        return copy(
                source,
                UUID.randomUUID(),
                source.intentKey(),
                source.requestId(),
                source.requestRevision(),
                source.eventType(),
                source.audience(),
                source.recipientId(),
                source.excludedRecipientId(),
                source.minimumRank(),
                source.visibility(),
                source.schemaVersion(),
                source.createdAt(),
                source.expiresAt()
        );
    }

    private static PunishmentRequestAlertIntent copy(
            PunishmentRequestAlertIntent ignored,
            UUID alertId,
            String key,
            UUID requestId,
            long revision,
            PunishmentRequestLifecycleEventType event,
            PunishmentRequestAlertAudience audience,
            UUID recipient,
            UUID excluded,
            StaffRank minimumRank,
            CaseVisibility visibility,
            int schemaVersion,
            Instant createdAt,
            Instant expiresAt
    ) {
        return new PunishmentRequestAlertIntent(
                alertId, key, requestId, revision, event, audience,
                recipient, excluded, minimumRank, visibility, schemaVersion, createdAt, expiresAt
        );
    }

    private static int deliveryCount(UUID alertId, UUID recipientId) throws SQLException {
        return count("SELECT COUNT(*) FROM staff_alert_deliveries WHERE alert_id = ? AND recipient_id = ?",
                alertId, recipientId);
    }

    private static int deliveryCount(UUID alertId) throws SQLException {
        return count("SELECT COUNT(*) FROM staff_alert_deliveries WHERE alert_id = ?", alertId);
    }

    private static int intentCount(UUID alertId) throws SQLException {
        return count("SELECT COUNT(*) FROM staff_alerts WHERE alert_id = ?", alertId);
    }

    private static int intentCountByKey(String key) throws SQLException {
        try (Connection connection = MariaDbIntegrationSupport.connection(DATABASE);
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT COUNT(*) FROM staff_alerts WHERE intent_key = ?")) {
            statement.setString(1, key);
            try (ResultSet result = statement.executeQuery()) {
                assertTrue(result.next());
                return result.getInt(1);
            }
        }
    }

    private static int count(String sql, UUID... identifiers) throws SQLException {
        try (Connection connection = MariaDbIntegrationSupport.connection(DATABASE);
             PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int index = 0; index < identifiers.length; index++) {
                statement.setBytes(index + 1, MariaDbIntegrationSupport.uuidBytes(identifiers[index]));
            }
            try (ResultSet result = statement.executeQuery()) {
                assertTrue(result.next());
                return result.getInt(1);
            }
        }
    }

    private static String deliveryState(UUID alertId, UUID recipientId) throws SQLException {
        try (Connection connection = MariaDbIntegrationSupport.connection(DATABASE);
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT state FROM staff_alert_deliveries
                     WHERE alert_id = ? AND recipient_id = ?
                     """)) {
            statement.setBytes(1, MariaDbIntegrationSupport.uuidBytes(alertId));
            statement.setBytes(2, MariaDbIntegrationSupport.uuidBytes(recipientId));
            try (ResultSet result = statement.executeQuery()) {
                assertTrue(result.next());
                return result.getString(1);
            }
        }
    }

    private static String intentState(UUID alertId) throws SQLException {
        try (Connection connection = MariaDbIntegrationSupport.connection(DATABASE);
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT intent_state FROM staff_alerts WHERE alert_id = ?")) {
            statement.setBytes(1, MariaDbIntegrationSupport.uuidBytes(alertId));
            try (ResultSet result = statement.executeQuery()) {
                assertTrue(result.next());
                return result.getString(1);
            }
        }
    }

    private static UUID alertIdByKey(String key) throws SQLException {
        try (Connection connection = MariaDbIntegrationSupport.connection(DATABASE);
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT alert_id FROM staff_alerts WHERE intent_key = ?")) {
            statement.setString(1, key);
            try (ResultSet result = statement.executeQuery()) {
                assertTrue(result.next());
                ByteBuffer buffer = ByteBuffer.wrap(result.getBytes(1));
                return new UUID(buffer.getLong(), buffer.getLong());
            }
        }
    }

    private static void rawUpdate(String sql, UUID alertId) throws SQLException {
        try (Connection connection = MariaDbIntegrationSupport.connection(DATABASE);
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setBytes(1, MariaDbIntegrationSupport.uuidBytes(alertId));
            statement.executeUpdate();
        }
    }
}
