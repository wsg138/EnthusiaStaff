package net.enthusia.staff.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import net.enthusia.staff.domain.application.PunishmentRequestAlertAudience;
import net.enthusia.staff.domain.application.PunishmentRequestAlertClaim;
import net.enthusia.staff.domain.application.PunishmentRequestAlertIntent;
import net.enthusia.staff.domain.application.PunishmentRequestAlertIntentKey;
import net.enthusia.staff.domain.application.PunishmentRequestAlertOccurrence;
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
class PunishmentRequestAlertReconciliationIntegrationTest {
    private static final Instant NOW = Instant.parse("2026-07-31T18:00:00Z");
    private static final Duration LEASE = Duration.ofMinutes(2);

    @Container
    private static final MariaDBContainer<?> DATABASE = new MariaDBContainer<>("mariadb:11.8.3")
            .withDatabaseName("enthusia_staff_alert_reconciliation_test")
            .withUsername("enthusia_test")
            .withPassword("enthusia_test_password");

    @BeforeEach
    void clearState() throws Exception {
        try (MariaDbRuntime ignored = runtime()) {
            // Flyway migration and runtime construction are part of this checkpoint.
        }
        try (Connection connection = connection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("DELETE FROM staff_alert_deliveries");
            statement.executeUpdate("DELETE FROM staff_alerts");
        }
    }

    @Test
    void terminalizingIntentCancelsPendingDirectAndAudienceDeliveries() throws Exception {
        try (MariaDbRuntime runtime = runtime()) {
            PunishmentRequestAlertStore store = runtime.punishmentRequestAlertStore();
            UUID directRecipient = UUID.randomUUID();
            PunishmentRequestAlertIntent direct = direct(directRecipient, 0, NOW, NOW.plusSeconds(300));
            assertTrue(store.insert(direct));
            assertTrue(store.closeIntent(direct.alertId(), "REQUEST_RESOLVED", NOW.plusSeconds(1)));
            assertEquals("CANCELLED", deliveryState(direct.alertId(), directRecipient));

            UUID reviewer = UUID.randomUUID();
            PunishmentRequestAlertIntent audience = reviewer(UUID.randomUUID(), 1, NOW);
            store.insert(audience);
            PunishmentRequestAlertClaim claim = only(store.claimAudience(
                    PunishmentRequestAlertAudience.ELIGIBLE_REVIEWERS,
                    reviewer, StaffRank.MOD, "paper:audience", 10, LEASE, NOW));
            assertTrue(store.failed(claim.deliveryId(), "paper:audience", "TRANSIENT",
                    NOW.plusSeconds(1), NOW, 3));
            assertEquals("PENDING", deliveryState(audience.alertId(), reviewer));
            assertTrue(store.closeIntent(audience.alertId(), "REQUEST_RESOLVED", NOW.plusSeconds(2)));
            assertEquals("CANCELLED", deliveryState(audience.alertId(), reviewer));
        }
    }

    @Test
    void validLeaseCanAcknowledgeAcrossExpiryAndExpiredLeasesReconcileByParentState() throws Exception {
        try (MariaDbRuntime runtime = runtime()) {
            PunishmentRequestAlertStore store = runtime.punishmentRequestAlertStore();
            UUID crossingRecipient = UUID.randomUUID();
            PunishmentRequestAlertIntent crossing = direct(
                    crossingRecipient, 0, NOW, NOW.plusSeconds(1));
            store.insert(crossing);
            PunishmentRequestAlertClaim crossingClaim = only(store.claimDirect(
                    crossingRecipient, "paper:crossing", 10, LEASE, NOW));
            assertEquals(1, store.expireIntents(NOW.plusSeconds(2), 10));
            assertTrue(store.delivered(
                    crossingClaim.deliveryId(), "paper:crossing", NOW.plusSeconds(2)));
            assertEquals("DELIVERED", deliveryState(crossing.alertId(), crossingRecipient));

            UUID activeRecipient = UUID.randomUUID();
            PunishmentRequestAlertIntent active = direct(
                    activeRecipient, 1, NOW.plusSeconds(3), NOW.plusSeconds(600));
            store.insert(active);
            PunishmentRequestAlertClaim activeClaim = only(store.claimDirect(
                    activeRecipient, "paper:active-a", 10, LEASE, NOW.plusSeconds(3)));
            Instant reclaimAt = activeClaim.leaseUntil().plusSeconds(1);
            assertEquals(1, store.reclaimExpiredDeliveries(reclaimAt, 10));
            assertEquals("PENDING", deliveryState(active.alertId(), activeRecipient));
            assertEquals(activeClaim.deliveryId(), only(store.claimDirect(
                    activeRecipient, "paper:active-b", 10, LEASE, reclaimAt)).deliveryId());

            UUID terminalRecipient = UUID.randomUUID();
            PunishmentRequestAlertIntent terminal = direct(
                    terminalRecipient, 2, NOW.plusSeconds(4), NOW.plusSeconds(600));
            store.insert(terminal);
            PunishmentRequestAlertClaim terminalClaim = only(store.claimDirect(
                    terminalRecipient, "paper:terminal", 10, LEASE, NOW.plusSeconds(4)));
            assertTrue(store.closeIntent(terminal.alertId(), "REQUEST_RESOLVED", NOW.plusSeconds(5)));
            assertEquals(1, store.reclaimExpiredDeliveries(
                    terminalClaim.leaseUntil().plusSeconds(1), 10));
            assertEquals("CANCELLED", deliveryState(terminal.alertId(), terminalRecipient));
        }
    }

    @Test
    void workerCancellationIsFencedAndStaleOwnerCannotMutateReclaimedLease() throws Exception {
        UUID recipient = UUID.randomUUID();
        try (MariaDbRuntime runtime = runtime()) {
            PunishmentRequestAlertStore store = runtime.punishmentRequestAlertStore();
            PunishmentRequestAlertIntent intent = direct(recipient, 0, NOW, NOW.plusSeconds(600));
            store.insert(intent);
            PunishmentRequestAlertClaim first = only(store.claimDirect(
                    recipient, "paper:first", 10, LEASE, NOW));
            Instant reclaimAt = first.leaseUntil().plusSeconds(1);
            assertEquals(1, store.reclaimExpiredDeliveries(reclaimAt, 10));
            PunishmentRequestAlertClaim second = only(store.claimDirect(
                    recipient, "paper:second", 10, LEASE, reclaimAt));

            assertFalse(store.cancel(first.deliveryId(), "paper:first", "RECIPIENT_INELIGIBLE",
                    reclaimAt.plusSeconds(1)));
            assertFalse(store.delivered(first.deliveryId(), "paper:first", reclaimAt.plusSeconds(1)));
            assertFalse(store.failed(first.deliveryId(), "paper:first", "TRANSIENT",
                    reclaimAt.plusSeconds(2), reclaimAt.plusSeconds(1), 3));
            assertTrue(store.cancel(second.deliveryId(), "paper:second", "RECIPIENT_INELIGIBLE",
                    reclaimAt.plusSeconds(1)));
            assertEquals("CANCELLED", deliveryState(intent.alertId(), recipient));
        }
    }

    @Test
    void authorizationLossSuppressesPresentationWithoutStrandingWork() throws Exception {
        UUID recipient = UUID.randomUUID();
        try (MariaDbRuntime runtime = runtime()) {
            PunishmentRequestAlertStore store = runtime.punishmentRequestAlertStore();
            PunishmentRequestAlertIntent intent = reviewer(UUID.randomUUID(), 0, NOW);
            store.insert(intent);
            PunishmentRequestAlertClaim claim = only(store.claimAudience(
                    PunishmentRequestAlertAudience.ELIGIBLE_REVIEWERS,
                    recipient, StaffRank.MOD, "paper:eligible", 10, LEASE, NOW));
            assertTrue(store.failed(claim.deliveryId(), "paper:eligible", "TRANSIENT",
                    NOW.plusSeconds(1), NOW, 3));

            assertTrue(store.claimAudience(
                    PunishmentRequestAlertAudience.ELIGIBLE_REVIEWERS,
                    recipient, StaffRank.HELPER, "paper:lost-rank", 10, LEASE,
                    NOW.plusSeconds(1)).isEmpty());
            assertEquals("CANCELLED", deliveryState(intent.alertId(), recipient));
            assertEquals("RECIPIENT_INELIGIBLE", cancelReason(intent.alertId(), recipient));
        }
    }

    @Test
    void cancelledWorkDoesNotBlockCleanupButDeadLettersRequireExplicitResolution() throws Exception {
        try (MariaDbRuntime runtime = runtime()) {
            PunishmentRequestAlertStore store = runtime.punishmentRequestAlertStore();
            UUID cancelledRecipient = UUID.randomUUID();
            PunishmentRequestAlertIntent cancelled = direct(
                    cancelledRecipient, 0, NOW.minusSeconds(20), NOW.plusSeconds(600));
            store.insert(cancelled);
            assertTrue(store.closeIntent(cancelled.alertId(), "REQUEST_RESOLVED", NOW.minusSeconds(10)));
            assertEquals(1, store.deleteTerminalIntentsBefore(NOW, 10));
            assertEquals(0, intentCount(cancelled.alertId()));

            UUID deadRecipient = UUID.randomUUID();
            PunishmentRequestAlertIntent dead = reviewer(
                    UUID.randomUUID(), 1, NOW.minusSeconds(20));
            store.insert(dead);
            PunishmentRequestAlertClaim claim = only(store.claimAudience(
                    PunishmentRequestAlertAudience.ELIGIBLE_REVIEWERS,
                    deadRecipient, StaffRank.MOD, "paper:dead", 10, LEASE,
                    NOW.minusSeconds(15)));
            assertTrue(store.failed(claim.deliveryId(), "paper:dead", "PERMANENT",
                    NOW.minusSeconds(14), NOW.minusSeconds(15), 1));
            assertTrue(store.closeIntent(dead.alertId(), "REQUEST_RESOLVED", NOW.minusSeconds(10)));
            assertEquals(0, store.deleteTerminalIntentsBefore(NOW, 10));
            assertTrue(store.resolveDeadLetter(
                    claim.deliveryId(), "OPERATOR_DISCARDED", NOW.minusSeconds(5)));
            assertEquals(1, store.deleteTerminalIntentsBefore(NOW, 10));
            assertEquals(0, intentCount(dead.alertId()));
        }
    }

    @Test
    void claimOccurrencesAreDistinctRepeatableAndConflictCheckedExactly() throws Exception {
        UUID requestId = UUID.randomUUID();
        UUID requester = UUID.randomUUID();
        UUID reviewer = UUID.randomUUID();
        PunishmentRequestAlertIntent first = claimIntent(
                requestId, requester, reviewer, 17, UUID.randomUUID());
        PunishmentRequestAlertIntent retry = claimIntent(
                requestId, requester, reviewer, 17, UUID.randomUUID());
        PunishmentRequestAlertIntent later = claimIntent(
                requestId, requester, reviewer, 18, UUID.randomUUID());

        assertEquals(first.intentKey(), retry.intentKey());
        assertFalse(first.intentKey().equals(later.intentKey()));
        try (MariaDbRuntime runtime = runtime()) {
            PunishmentRequestAlertStore store = runtime.punishmentRequestAlertStore();
            assertTrue(store.insert(first));
            assertFalse(store.insert(retry));
            assertTrue(store.insert(later));
            assertEquals(2, intentCountForRequest(requestId));

            PunishmentRequestAlertIntent conflict = finalizeIntent(new PunishmentRequestAlertIntent(
                    UUID.randomUUID(), first.intentKey(), requestId, 0,
                    PunishmentRequestLifecycleEventType.REQUEST_CLAIMED,
                    PunishmentRequestAlertOccurrence.forClaim(17, UUID.randomUUID()),
                    PunishmentRequestAlertAudience.DIRECT_RECIPIENT,
                    requester, null, null, CaseVisibility.PRIVATE, 2,
                    NOW, NOW.plus(Duration.ofDays(7))
            ), first.intentKey());
            assertThrows(ModerationPersistenceException.class, () -> store.insert(conflict));
        }
    }

    private static PunishmentRequestAlertIntent claimIntent(
            UUID requestId,
            UUID requester,
            UUID reviewer,
            long fence,
            UUID alertId
    ) {
        PunishmentRequestAlertIntent draft = new PunishmentRequestAlertIntent(
                alertId, "pending", requestId, 0,
                PunishmentRequestLifecycleEventType.REQUEST_CLAIMED,
                PunishmentRequestAlertOccurrence.forClaim(fence, reviewer),
                PunishmentRequestAlertAudience.DIRECT_RECIPIENT,
                requester, null, null, CaseVisibility.PRIVATE, 2,
                NOW, NOW.plus(Duration.ofDays(7))
        );
        return finalizeIntent(draft, PunishmentRequestAlertIntentKey.forIntent(draft));
    }

    private static PunishmentRequestAlertIntent direct(
            UUID recipient,
            long revision,
            Instant createdAt,
            Instant expiresAt
    ) {
        PunishmentRequestAlertIntent draft = new PunishmentRequestAlertIntent(
                UUID.randomUUID(), "pending", UUID.randomUUID(), revision,
                PunishmentRequestLifecycleEventType.REQUEST_SUBMITTED,
                PunishmentRequestAlertAudience.DIRECT_RECIPIENT,
                recipient, null, null, CaseVisibility.PRIVATE, 2, createdAt, expiresAt
        );
        return finalizeIntent(draft, PunishmentRequestAlertIntentKey.forIntent(draft));
    }

    private static PunishmentRequestAlertIntent reviewer(
            UUID requester,
            long revision,
            Instant createdAt
    ) {
        PunishmentRequestAlertIntent draft = new PunishmentRequestAlertIntent(
                UUID.randomUUID(), "pending", UUID.randomUUID(), revision,
                PunishmentRequestLifecycleEventType.REQUEST_SUBMITTED,
                PunishmentRequestAlertAudience.ELIGIBLE_REVIEWERS,
                null, requester, StaffRank.MOD, CaseVisibility.PRIVATE, 2,
                createdAt, createdAt.plus(Duration.ofDays(7))
        );
        return finalizeIntent(draft, PunishmentRequestAlertIntentKey.forIntent(draft));
    }

    private static PunishmentRequestAlertIntent finalizeIntent(
            PunishmentRequestAlertIntent source,
            String key
    ) {
        return new PunishmentRequestAlertIntent(
                source.alertId(), key, source.requestId(), source.requestRevision(),
                source.eventType(), source.occurrence(), source.audience(), source.recipientId(),
                source.excludedRecipientId(), source.minimumRank(), source.visibility(),
                source.schemaVersion(), source.createdAt(), source.expiresAt()
        );
    }

    private static PunishmentRequestAlertClaim only(List<PunishmentRequestAlertClaim> claims) {
        assertEquals(1, claims.size());
        return claims.getFirst();
    }

    private static MariaDbRuntime runtime() {
        return MariaDb.initialize(MariaDbIntegrationSupport.databaseConfig(DATABASE));
    }

    private static Connection connection() throws Exception {
        return MariaDbIntegrationSupport.connection(DATABASE);
    }

    private static String deliveryState(UUID alertId, UUID recipientId) throws Exception {
        return deliveryValue(alertId, recipientId, "state");
    }

    private static String cancelReason(UUID alertId, UUID recipientId) throws Exception {
        return deliveryValue(alertId, recipientId, "cancel_reason");
    }

    private static String deliveryValue(UUID alertId, UUID recipientId, String column) throws Exception {
        String sql = switch (column) {
            case "state" -> "SELECT state FROM staff_alert_deliveries WHERE alert_id=? AND recipient_id=?";
            case "cancel_reason" -> "SELECT cancel_reason FROM staff_alert_deliveries WHERE alert_id=? AND recipient_id=?";
            default -> throw new IllegalArgumentException("unsupported test column");
        };
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setBytes(1, MariaDbIntegrationSupport.uuidBytes(alertId));
            statement.setBytes(2, MariaDbIntegrationSupport.uuidBytes(recipientId));
            try (ResultSet result = statement.executeQuery()) {
                assertTrue(result.next());
                return result.getString(1);
            }
        }
    }

    private static int intentCount(UUID alertId) throws Exception {
        return count("SELECT COUNT(*) FROM staff_alerts WHERE alert_id=?", alertId);
    }

    private static int intentCountForRequest(UUID requestId) throws Exception {
        return count("SELECT COUNT(*) FROM staff_alerts WHERE request_id=?", requestId);
    }

    private static int count(String sql, UUID id) throws Exception {
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setBytes(1, MariaDbIntegrationSupport.uuidBytes(id));
            try (ResultSet result = statement.executeQuery()) {
                assertTrue(result.next());
                return result.getInt(1);
            }
        }
    }
}
