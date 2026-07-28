package net.enthusia.staff.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import net.enthusia.staff.domain.application.PunishmentDraft;
import net.enthusia.staff.domain.application.PunishmentExpectation;
import net.enthusia.staff.domain.casefile.CaseVisibility;
import net.enthusia.staff.domain.ports.PunishmentDraftStore;
import net.enthusia.staff.domain.sanction.SanctionLength;
import net.enthusia.staff.domain.sanction.SanctionSpec;
import net.enthusia.staff.domain.sanction.SanctionType;
import net.enthusia.staff.persistence.DatabaseConfig;
import net.enthusia.staff.persistence.MariaDb;
import net.enthusia.staff.persistence.MariaDbRuntime;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
class PunishmentDraftIntegrationTest {
    private static final Instant NOW = Instant.parse("2026-07-27T12:00:00Z");
    private static final UUID ACTOR = UUID.fromString("40000000-0000-0000-0000-000000000001");
    private static final UUID OTHER_ACTOR = UUID.fromString("40000000-0000-0000-0000-000000000002");
    private static final UUID TARGET = UUID.fromString("40000000-0000-0000-0000-000000000003");
    private static final UUID FIRST_DRAFT = UUID.fromString("40000000-0000-0000-0000-000000000004");
    private static final UUID REPLACEMENT_DRAFT = UUID.fromString("40000000-0000-0000-0000-000000000005");

    @Container
    private static final MariaDBContainer<?> DATABASE = new MariaDBContainer<>("mariadb:11.8.3")
            .withDatabaseName("enthusia_staff_drafts_test")
            .withUsername("enthusia_test")
            .withPassword("enthusia_test_password");

    @Test
    void draftSurvivesRuntimeRestartAndReplacementRemainsActorBound() throws SQLException {
        PunishmentDraft initial = draft(FIRST_DRAFT, CaseVisibility.PUBLIC, NOW.plus(Duration.ofHours(24)));
        try (MariaDbRuntime runtime = MariaDb.initialize(databaseConfig())) {
            insertPlayer(TARGET, "DraftTarget");
            runtime.punishmentDraftStore().save(initial);
        }

        try (MariaDbRuntime runtime = MariaDb.initialize(databaseConfig())) {
            PunishmentDraftStore store = runtime.punishmentDraftStore();
            assertEquals(initial, store.find(FIRST_DRAFT, ACTOR, NOW.plusSeconds(1)).orElseThrow());
            assertFalse(store.find(FIRST_DRAFT, OTHER_ACTOR, NOW.plusSeconds(1)).isPresent());

            PunishmentDraft replacement = draft(
                    REPLACEMENT_DRAFT,
                    CaseVisibility.PRIVATE,
                    NOW.plus(Duration.ofHours(24))
            );
            store.save(replacement);
            assertFalse(store.find(FIRST_DRAFT, ACTOR, NOW.plusSeconds(1)).isPresent());
            assertEquals(
                    replacement,
                    store.findLatest(ACTOR, TARGET, NOW.plusSeconds(1)).orElseThrow()
            );
            assertFalse(store.find(REPLACEMENT_DRAFT, ACTOR, replacement.expiresAt()).isPresent());
            assertEquals(1, store.deleteExpired(replacement.expiresAt()));
            assertTrue(store.findLatest(ACTOR, TARGET, NOW.plusSeconds(1)).isEmpty());
        }
    }

    private static PunishmentDraft draft(UUID draftId, CaseVisibility visibility, Instant expiresAt) {
        return new PunishmentDraft(
                draftId,
                ACTOR,
                TARGET,
                "chat.toxicity",
                "Persistent internal review note",
                visibility,
                "punish",
                new PunishmentExpectation(
                        "v1",
                        1,
                        "One day mute",
                        List.of(new SanctionSpec(
                                SanctionType.MUTE,
                                SanctionLength.temporary(Duration.ofDays(1))
                        ))
                ),
                NOW,
                expiresAt
        );
    }

    private static DatabaseConfig databaseConfig() {
        return new DatabaseConfig(
                DATABASE.getJdbcUrl(),
                DATABASE.getUsername(),
                DATABASE.getPassword(),
                4,
                5_000
        );
    }

    private static void insertPlayer(UUID playerId, String username) throws SQLException {
        try (Connection connection = DriverManager.getConnection(
                DATABASE.getJdbcUrl(), DATABASE.getUsername(), DATABASE.getPassword()
        ); PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO players(player_id, current_username, lowercase_username, platform,
                    current_server, last_server, first_seen_at, last_seen_at, revision)
                VALUES (?, ?, ?, 'JAVA', 'test', 'test', ?, ?, 0)
                ON DUPLICATE KEY UPDATE current_username = VALUES(current_username)
                """)) {
            statement.setBytes(1, uuid(playerId));
            statement.setString(2, username);
            statement.setString(3, username.toLowerCase(java.util.Locale.ROOT));
            statement.setTimestamp(4, Timestamp.from(NOW));
            statement.setTimestamp(5, Timestamp.from(NOW));
            statement.executeUpdate();
        }
    }

    private static byte[] uuid(UUID value) {
        return java.nio.ByteBuffer.allocate(16)
                .putLong(value.getMostSignificantBits())
                .putLong(value.getLeastSignificantBits())
                .array();
    }
}
