package net.enthusia.staff.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
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
import net.enthusia.staff.persistence.ModerationPersistenceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class PunishmentRequestAlertCanonicalReplayIntegrationTest {
    private static final Instant NOW = Instant.parse("2026-07-31T04:00:00Z");
    private static final Instant EXPIRES_AT = NOW.plus(Duration.ofDays(7));

    @Container
    private static final MariaDBContainer<?> DATABASE = new MariaDBContainer<>("mariadb:11.8.3")
            .withDatabaseName("enthusia_staff_alert_canonical_test")
            .withUsername("enthusia_test")
            .withPassword("enthusia_test_password");

    @BeforeEach
    void clearAlertState() throws SQLException {
        try (MariaDbRuntime runtime = runtime();
             Connection connection = connection();
             Statement statement = connection.createStatement()) {
            assertNotNull(runtime.punishmentRequestAlertStore());
            statement.executeUpdate("DELETE FROM staff_alert_deliveries");
            statement.executeUpdate("DELETE FROM staff_alerts");
        }
    }

    @Test
    void duplicateIntentKeyRejectsAudienceAndReviewerFilterMismatches() throws SQLException {
        UUID requestId = UUID.randomUUID();
        UUID directRecipient = UUID.randomUUID();
        PunishmentRequestAlertIntent direct = finalized(new PunishmentRequestAlertIntent(
                UUID.randomUUID(),
                "pending",
                requestId,
                4,
                PunishmentRequestLifecycleEventType.REQUEST_SUBMITTED,
                PunishmentRequestAlertAudience.DIRECT_RECIPIENT,
                directRecipient,
                null,
                null,
                CaseVisibility.PRIVATE,
                1,
                NOW,
                EXPIRES_AT
        ));

        try (MariaDbRuntime runtime = runtime()) {
            PunishmentRequestAlertStore store = runtime.punishmentRequestAlertStore();
            assertTrue(store.insert(direct));

            PunishmentRequestAlertIntent audienceMismatch = new PunishmentRequestAlertIntent(
                    UUID.randomUUID(),
                    direct.intentKey(),
                    requestId,
                    direct.requestRevision(),
                    direct.eventType(),
                    PunishmentRequestAlertAudience.ELIGIBLE_REVIEWERS,
                    null,
                    UUID.randomUUID(),
                    StaffRank.MOD,
                    direct.visibility(),
                    direct.schemaVersion(),
                    direct.createdAt(),
                    direct.expiresAt()
            );
            assertThrows(ModerationPersistenceException.class, () -> store.insert(audienceMismatch));

            PunishmentRequestAlertIntent reviewer = finalized(new PunishmentRequestAlertIntent(
                    UUID.randomUUID(),
                    "pending",
                    UUID.randomUUID(),
                    8,
                    PunishmentRequestLifecycleEventType.REQUEST_SUBMITTED,
                    PunishmentRequestAlertAudience.ELIGIBLE_REVIEWERS,
                    null,
                    UUID.randomUUID(),
                    StaffRank.MOD,
                    CaseVisibility.PRIVATE,
                    1,
                    NOW.plusSeconds(1),
                    EXPIRES_AT.plusSeconds(1)
            ));
            assertTrue(store.insert(reviewer));

            PunishmentRequestAlertIntent excludedRequesterMismatch = new PunishmentRequestAlertIntent(
                    UUID.randomUUID(),
                    reviewer.intentKey(),
                    reviewer.requestId(),
                    reviewer.requestRevision(),
                    reviewer.eventType(),
                    reviewer.audience(),
                    null,
                    UUID.randomUUID(),
                    reviewer.minimumRank(),
                    reviewer.visibility(),
                    reviewer.schemaVersion(),
                    reviewer.createdAt(),
                    reviewer.expiresAt()
            );
            assertThrows(
                    ModerationPersistenceException.class,
                    () -> store.insert(excludedRequesterMismatch)
            );

            PunishmentRequestAlertIntent minimumRankMismatch = new PunishmentRequestAlertIntent(
                    UUID.randomUUID(),
                    reviewer.intentKey(),
                    reviewer.requestId(),
                    reviewer.requestRevision(),
                    reviewer.eventType(),
                    reviewer.audience(),
                    null,
                    reviewer.excludedRecipientId(),
                    StaffRank.ADMIN,
                    reviewer.visibility(),
                    reviewer.schemaVersion(),
                    reviewer.createdAt(),
                    reviewer.expiresAt()
            );
            assertThrows(ModerationPersistenceException.class, () -> store.insert(minimumRankMismatch));

            assertEquals(2, intentCount());
        }
    }

    @Test
    void malformedUuidWidthAndForeignKeyFailuresPropagate() throws SQLException {
        PunishmentRequestAlertIntent canonical = finalized(new PunishmentRequestAlertIntent(
                UUID.randomUUID(),
                "pending",
                UUID.randomUUID(),
                0,
                PunishmentRequestLifecycleEventType.REQUEST_SUBMITTED,
                PunishmentRequestAlertAudience.DIRECT_RECIPIENT,
                UUID.randomUUID(),
                null,
                null,
                CaseVisibility.PRIVATE,
                1,
                NOW,
                EXPIRES_AT
        ));

        try (MariaDbRuntime runtime = runtime()) {
            assertTrue(runtime.punishmentRequestAlertStore().insert(canonical));

            assertThrows(SQLException.class, () -> {
                try (Connection connection = connection();
                     PreparedStatement statement = connection.prepareStatement("""
                             UPDATE staff_alerts
                             SET request_id = UNHEX(REPEAT('01', 17))
                             WHERE alert_id = ?
                             """)) {
                    statement.setBytes(1, MariaDbIntegrationSupport.uuidBytes(canonical.alertId()));
                    statement.executeUpdate();
                }
            });

            assertThrows(SQLException.class, () -> {
                try (Connection connection = connection();
                     PreparedStatement statement = connection.prepareStatement("""
                             INSERT INTO staff_alert_deliveries(
                                 alert_id, recipient_id, state, attempt_count,
                                 available_at, created_at, updated_at
                             ) VALUES (?, ?, 'PENDING', 0, ?, ?, ?)
                             """)) {
                    statement.setBytes(1, MariaDbIntegrationSupport.uuidBytes(UUID.randomUUID()));
                    statement.setBytes(2, MariaDbIntegrationSupport.uuidBytes(UUID.randomUUID()));
                    Timestamp timestamp = Timestamp.from(NOW);
                    statement.setTimestamp(3, timestamp);
                    statement.setTimestamp(4, timestamp);
                    statement.setTimestamp(5, timestamp);
                    statement.executeUpdate();
                }
            });

            assertEquals(1, intentCount());
        }
    }

    private static PunishmentRequestAlertIntent finalized(PunishmentRequestAlertIntent draft) {
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

    private static Connection connection() throws SQLException {
        return MariaDbIntegrationSupport.connection(DATABASE);
    }

    private static int intentCount() throws SQLException {
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT COUNT(*) FROM staff_alerts");
             ResultSet result = statement.executeQuery()) {
            assertTrue(result.next());
            return result.getInt(1);
        }
    }
}
