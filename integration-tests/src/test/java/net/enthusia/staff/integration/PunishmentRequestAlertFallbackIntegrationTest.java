package net.enthusia.staff.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import net.enthusia.staff.domain.application.PunishmentRequestAlertAudience;
import net.enthusia.staff.domain.application.PunishmentRequestAlertClaim;
import net.enthusia.staff.domain.application.PunishmentRequestAlertIntent;
import net.enthusia.staff.domain.application.PunishmentRequestAlertIntentKey;
import net.enthusia.staff.domain.application.PunishmentRequestLifecycleEventType;
import net.enthusia.staff.domain.auth.StaffRank;
import net.enthusia.staff.domain.casefile.CaseVisibility;
import net.enthusia.staff.domain.ports.PunishmentRequestAlertStore;
import net.enthusia.staff.persistence.MariaDb;
import net.enthusia.staff.persistence.MariaDbRuntime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class PunishmentRequestAlertFallbackIntegrationTest {
    private static final Instant NOW = Instant.parse("2026-08-01T20:00:00Z");
    private static final Duration LEASE = Duration.ofMinutes(2);

    @Container
    private static final MariaDBContainer<?> DATABASE = new MariaDBContainer<>("mariadb:11.8.3")
            .withDatabaseName("enthusia_staff_alert_fallback_test")
            .withUsername("enthusia_test")
            .withPassword("enthusia_test_password");

    @BeforeEach
    void clearAlertState() throws Exception {
        try (MariaDbRuntime ignored = runtime()) {
            // Initializes and migrates the database.
        }
        try (Connection connection = connection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("DELETE FROM staff_alert_deliveries");
            statement.executeUpdate("DELETE FROM staff_alerts");
        }
    }

    @Test
    void legacyReviewerIntentWithNullExclusionIsDeniedSafely() throws Exception {
        UUID requester = UUID.randomUUID();
        UUID reviewer = UUID.randomUUID();
        PunishmentRequestAlertIntent intent = reviewerIntent(requester, 1);
        try (MariaDbRuntime runtime = runtime()) {
            PunishmentRequestAlertStore store = runtime.punishmentRequestAlertStore();
            assertTrue(store.insert(intent));
            setExcludedRecipientToNull(intent.alertId());

            List<PunishmentRequestAlertClaim> claims = store.claimAudience(
                    PunishmentRequestAlertAudience.ELIGIBLE_REVIEWERS,
                    reviewer,
                    StaffRank.MOD,
                    "paper:legacy-null",
                    4,
                    LEASE,
                    NOW
            );

            assertTrue(claims.isEmpty());
            assertEquals(0, deliveryCount(intent.alertId(), reviewer));
        }
    }

    @Test
    void sharedParentLockUsesFallbackAndPreservesIndependentRecipientProgress() throws Exception {
        UUID requester = UUID.randomUUID();
        UUID independentRecipient = UUID.randomUUID();
        PunishmentRequestAlertIntent intent = reviewerIntent(requester, 2);
        try (MariaDbRuntime runtime = runtime(); Connection lockHolder = connection()) {
            PunishmentRequestAlertStore store = runtime.punishmentRequestAlertStore();
            assertTrue(store.insert(intent));
            insertPendingDelivery(intent, independentRecipient);

            lockHolder.setAutoCommit(false);
            lockParentForSharedRead(lockHolder, intent.alertId());

            ExecutorService executor = Executors.newSingleThreadExecutor();
            try {
                Future<List<PunishmentRequestAlertClaim>> future = executor.submit(() -> store.claimAudience(
                        PunishmentRequestAlertAudience.ELIGIBLE_REVIEWERS,
                        independentRecipient,
                        StaffRank.ADMIN,
                        "paper:fallback",
                        4,
                        LEASE,
                        NOW
                ));
                List<PunishmentRequestAlertClaim> claims = future.get(5, TimeUnit.SECONDS);

                assertEquals(1, claims.size());
                assertEquals(intent.alertId(), claims.getFirst().deliveryId().alertId());
                assertEquals(independentRecipient, claims.getFirst().deliveryId().recipientId());
            } finally {
                lockHolder.rollback();
                executor.shutdownNow();
            }
        }
    }

    private static void lockParentForSharedRead(Connection connection, UUID alertId) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT alert_id FROM staff_alerts WHERE alert_id=? LOCK IN SHARE MODE")) {
            statement.setBytes(1, MariaDbIntegrationSupport.uuidBytes(alertId));
            try (ResultSet result = statement.executeQuery()) {
                assertTrue(result.next());
            }
        }
    }

    private static void insertPendingDelivery(
            PunishmentRequestAlertIntent intent,
            UUID recipientId
    ) throws Exception {
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement("""
                     INSERT INTO staff_alert_deliveries(
                         alert_id, recipient_id, state, attempt_count,
                         available_at, created_at, updated_at)
                     VALUES (?, ?, 'PENDING', 0, ?, ?, ?)
                     """)) {
            Timestamp timestamp = Timestamp.from(intent.createdAt());
            statement.setBytes(1, MariaDbIntegrationSupport.uuidBytes(intent.alertId()));
            statement.setBytes(2, MariaDbIntegrationSupport.uuidBytes(recipientId));
            statement.setTimestamp(3, timestamp);
            statement.setTimestamp(4, timestamp);
            statement.setTimestamp(5, timestamp);
            assertEquals(1, statement.executeUpdate());
        }
    }

    private static int deliveryCount(UUID alertId, UUID recipientId) throws Exception {
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT COUNT(*) FROM staff_alert_deliveries WHERE alert_id=? AND recipient_id=?")) {
            statement.setBytes(1, MariaDbIntegrationSupport.uuidBytes(alertId));
            statement.setBytes(2, MariaDbIntegrationSupport.uuidBytes(recipientId));
            try (ResultSet result = statement.executeQuery()) {
                assertTrue(result.next());
                return result.getInt(1);
            }
        }
    }

    private static void setExcludedRecipientToNull(UUID alertId) throws Exception {
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(
                     "UPDATE staff_alerts SET excluded_recipient_id=NULL WHERE alert_id=?")) {
            statement.setBytes(1, MariaDbIntegrationSupport.uuidBytes(alertId));
            assertEquals(1, statement.executeUpdate());
        }
    }

    private static PunishmentRequestAlertIntent reviewerIntent(UUID requester, long revision) {
        PunishmentRequestAlertIntent draft = new PunishmentRequestAlertIntent(
                UUID.randomUUID(),
                "pending",
                UUID.randomUUID(),
                revision,
                PunishmentRequestLifecycleEventType.REQUEST_SUBMITTED,
                PunishmentRequestAlertAudience.ELIGIBLE_REVIEWERS,
                null,
                requester,
                StaffRank.MOD,
                CaseVisibility.PRIVATE,
                1,
                NOW,
                NOW.plus(Duration.ofDays(7))
        );
        return new PunishmentRequestAlertIntent(
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

    private static MariaDbRuntime runtime() {
        return MariaDb.initialize(MariaDbIntegrationSupport.databaseConfig(DATABASE));
    }

    private static Connection connection() throws Exception {
        return MariaDbIntegrationSupport.connection(DATABASE);
    }
}
