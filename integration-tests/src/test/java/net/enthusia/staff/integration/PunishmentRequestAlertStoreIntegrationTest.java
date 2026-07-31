package net.enthusia.staff.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import net.enthusia.staff.domain.application.PunishmentRequestAlertAudience;
import net.enthusia.staff.domain.application.PunishmentRequestAlertIntent;
import net.enthusia.staff.domain.application.PunishmentRequestAlertIntentKey;
import net.enthusia.staff.domain.application.PunishmentRequestLifecycleEventType;
import net.enthusia.staff.domain.auth.StaffRank;
import net.enthusia.staff.domain.casefile.CaseVisibility;
import net.enthusia.staff.domain.ports.PunishmentRequestAlertStore;
import net.enthusia.staff.persistence.MariaDb;
import net.enthusia.staff.persistence.MariaDbRuntime;
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

    @Test
    void migrationAndIdempotentInsertionPreservePendingState() throws Exception {
        try (MariaDbRuntime runtime = MariaDb.initialize(MariaDbIntegrationSupport.databaseConfig(DATABASE))) {
            PunishmentRequestAlertStore store = runtime.punishmentRequestAlertStore();
            PunishmentRequestAlertIntent intent = direct(0, PunishmentRequestLifecycleEventType.REQUEST_SUBMITTED);
            assertTrue(store.insert(intent));
            assertFalse(store.insert(withNewAlertId(intent)));
            try (Connection connection = MariaDbIntegrationSupport.connection(DATABASE);
                 PreparedStatement statement = connection.prepareStatement("""
                         SELECT state, delivered_at, read_at, intent_key FROM staff_alerts WHERE intent_key = ?
                         """)) {
                statement.setString(1, intent.intentKey());
                try (ResultSet result = statement.executeQuery()) {
                    assertTrue(result.next());
                    assertEquals("PENDING", result.getString("state"));
                    assertEquals(null, result.getTimestamp("delivered_at"));
                    assertEquals(null, result.getTimestamp("read_at"));
                    assertNotNull(result.getString("intent_key"));
                }
            }
        }
    }

    @Test
    void claimsAreBoundedOrderedAndFencedAcrossRestart() {
        UUID recipient = UUID.randomUUID();
        PunishmentRequestAlertIntent first = direct(recipient, 0, NOW);
        PunishmentRequestAlertIntent second = direct(recipient, 1, NOW.plusSeconds(1));
        try (MariaDbRuntime runtime = MariaDb.initialize(MariaDbIntegrationSupport.databaseConfig(DATABASE))) {
            PunishmentRequestAlertStore store = runtime.punishmentRequestAlertStore();
            store.insert(second);
            store.insert(first);
            var claimed = store.claimDirect(recipient, "paper:a", 1, LEASE, NOW.plusSeconds(2));
            assertEquals(1, claimed.size());
            assertEquals(first.alertId(), claimed.getFirst().alertId());
            assertFalse(store.delivered(first.alertId(), "paper:b", NOW.plusSeconds(3)));
            assertFalse(store.failed(first.alertId(), "paper:a", "TOO_LATE",
                    NOW.plus(LEASE).plusSeconds(4), NOW.plus(LEASE).plusSeconds(3), 3));
        }
        try (MariaDbRuntime restarted = MariaDb.initialize(MariaDbIntegrationSupport.databaseConfig(DATABASE))) {
            PunishmentRequestAlertStore store = restarted.punishmentRequestAlertStore();
            assertEquals(1, store.reclaimExpired(NOW.plus(LEASE).plusSeconds(3), 10));
            var reclaimed = store.claimDirect(recipient, "paper:b", 10, LEASE, NOW.plus(LEASE).plusSeconds(4));
            assertEquals(2, reclaimed.size());
            assertFalse(store.delivered(first.alertId(), "paper:a", NOW.plus(LEASE).plusSeconds(5)));
            assertFalse(store.failed(first.alertId(), "paper:a", "STALE_OWNER",
                    NOW.plus(LEASE).plusSeconds(7), NOW.plus(LEASE).plusSeconds(6), 3));
            assertTrue(store.delivered(first.alertId(), "paper:b", NOW.plus(LEASE).plusSeconds(5)));
        }
    }

    @Test
    void audienceAuthorizationRetryDeadLetterBacklogAndCleanupAreDurable() {
        UUID requester = UUID.randomUUID();
        PunishmentRequestAlertIntent reviewer = audience(
                PunishmentRequestAlertAudience.ELIGIBLE_REVIEWERS, requester, StaffRank.ADMIN);
        PunishmentRequestAlertIntent operational = audience(
                PunishmentRequestAlertAudience.OPERATIONAL_ADMINISTRATORS, null, null);
        try (MariaDbRuntime runtime = MariaDb.initialize(MariaDbIntegrationSupport.databaseConfig(DATABASE))) {
            PunishmentRequestAlertStore store = runtime.punishmentRequestAlertStore();
            store.insert(reviewer);
            store.insert(operational);
            assertTrue(store.claimAudience(PunishmentRequestAlertAudience.ELIGIBLE_REVIEWERS,
                    requester, StaffRank.FOUNDER, "paper:self", 10, LEASE, NOW).isEmpty());
            assertTrue(store.claimAudience(PunishmentRequestAlertAudience.ELIGIBLE_REVIEWERS,
                    UUID.randomUUID(), StaffRank.MOD, "paper:mod", 10, LEASE, NOW).isEmpty());
            var adminClaim = store.claimAudience(PunishmentRequestAlertAudience.ELIGIBLE_REVIEWERS,
                    UUID.randomUUID(), StaffRank.ADMIN, "paper:admin", 10, LEASE, NOW);
            assertEquals(1, adminClaim.size());
            assertTrue(store.failed(reviewer.alertId(), "paper:admin", "TRANSIENT",
                    NOW.plusSeconds(5), NOW.plusSeconds(1), 2));
            var retry = store.claimAudience(PunishmentRequestAlertAudience.ELIGIBLE_REVIEWERS,
                    UUID.randomUUID(), StaffRank.FOUNDER, "paper:founder", 10, LEASE, NOW.plusSeconds(6));
            assertEquals(1, retry.size());
            assertTrue(store.failed(reviewer.alertId(), "paper:founder", "PERMANENT",
                    NOW.plusSeconds(7), NOW.plusSeconds(6), 2));
            assertEquals(1, store.backlog(NOW.plusSeconds(8)).deadLetter());

            assertTrue(store.claimAudience(PunishmentRequestAlertAudience.OPERATIONAL_ADMINISTRATORS,
                    UUID.randomUUID(), StaffRank.MOD, "paper:mod", 10, LEASE, NOW).isEmpty());
            var adminOps = store.claimAudience(PunishmentRequestAlertAudience.OPERATIONAL_ADMINISTRATORS,
                    UUID.randomUUID(), StaffRank.ADMIN, "paper:ops", 10, LEASE, NOW);
            assertEquals(1, adminOps.size());
            assertTrue(store.delivered(operational.alertId(), "paper:ops", NOW.plusSeconds(1)));
            assertEquals(1, store.deleteDeliveredBefore(NOW.plusSeconds(2), 10));
        }
    }

    private static PunishmentRequestAlertIntent direct(long revision,
                                                        PunishmentRequestLifecycleEventType event) {
        return direct(UUID.randomUUID(), revision, NOW, event);
    }

    private static PunishmentRequestAlertIntent direct(UUID recipient, long revision, Instant createdAt) {
        return direct(recipient, revision, createdAt, PunishmentRequestLifecycleEventType.REQUEST_SUBMITTED);
    }

    private static PunishmentRequestAlertIntent direct(UUID recipient, long revision, Instant createdAt,
                                                        PunishmentRequestLifecycleEventType event) {
        return finalized(new PunishmentRequestAlertIntent(UUID.randomUUID(), "pending", UUID.randomUUID(),
                revision, event, PunishmentRequestAlertAudience.DIRECT_RECIPIENT, recipient, null, null,
                CaseVisibility.PRIVATE, 1, createdAt));
    }

    private static PunishmentRequestAlertIntent audience(PunishmentRequestAlertAudience audience,
                                                          UUID excluded, StaffRank minimumRank) {
        return finalized(new PunishmentRequestAlertIntent(UUID.randomUUID(), "pending", UUID.randomUUID(),
                0, PunishmentRequestLifecycleEventType.REQUEST_SUBMITTED, audience, null, excluded,
                minimumRank, CaseVisibility.PRIVATE, 1, NOW));
    }

    private static PunishmentRequestAlertIntent finalized(PunishmentRequestAlertIntent draft) {
        String key = PunishmentRequestAlertIntentKey.forIntent(draft);
        return new PunishmentRequestAlertIntent(draft.alertId(), key, draft.requestId(), draft.requestRevision(),
                draft.eventType(), draft.audience(), draft.recipientId(), draft.excludedRecipientId(),
                draft.minimumRank(), draft.visibility(), draft.schemaVersion(), draft.createdAt());
    }

    private static PunishmentRequestAlertIntent withNewAlertId(PunishmentRequestAlertIntent source) {
        return new PunishmentRequestAlertIntent(UUID.randomUUID(), source.intentKey(), source.requestId(),
                source.requestRevision(), source.eventType(), source.audience(), source.recipientId(),
                source.excludedRecipientId(), source.minimumRank(), source.visibility(), source.schemaVersion(),
                source.createdAt());
    }
}
