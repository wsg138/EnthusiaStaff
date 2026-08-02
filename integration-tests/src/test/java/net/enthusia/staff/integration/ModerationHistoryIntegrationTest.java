package net.enthusia.staff.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import net.enthusia.staff.common.CaseId;
import net.enthusia.staff.domain.history.CaseHistoryDetail;
import net.enthusia.staff.domain.history.HistoryEventType;
import net.enthusia.staff.domain.history.HistoryQueryOptions;
import net.enthusia.staff.domain.history.ModerationHistoryEntry;
import net.enthusia.staff.domain.history.ModerationHistoryPage;
import net.enthusia.staff.domain.player.PlayerPlatform;
import net.enthusia.staff.domain.player.PlayerResolution;
import net.enthusia.staff.persistence.MariaDb;
import net.enthusia.staff.persistence.MariaDbRuntime;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class ModerationHistoryIntegrationTest {
    private static final String USERNAME = "moderation_history_user";
    private static final String PASSWORD = UUID.randomUUID().toString();
    private static final UUID ACTOR_ID = uuid(9000);
    private static final Instant BASE = Instant.parse("2026-08-01T00:00:00Z");

    @Container
    private static final MariaDBContainer<?> DATABASE = new MariaDBContainer<>("mariadb:11.8.3")
            .withDatabaseName("enthusia_staff_history_test")
            .withUsername(USERNAME)
            .withPassword(PASSWORD);

    @BeforeAll
    static void migrateSchema() {
        try (MariaDbRuntime runtime = MariaDb.initialize(databaseConfig())) {
            assertNotNull(runtime.moderationHistoryStore());
        }
    }

    @BeforeEach
    void clearFixtures() throws SQLException {
        try (HikariDataSource dataSource = MariaDb.open(databaseConfig());
             Connection connection = dataSource.getConnection()) {
            for (String table : List.of(
                    "network_outbox_deliveries",
                    "network_outbox",
                    "discord_outbox",
                    "audit_events",
                    "sanction_events",
                    "website_appeal_requests",
                    "punishment_request_events",
                    "punishment_requests",
                    "punishment_overturn_requests",
                    "staff_notes",
                    "sanction_links",
                    "sanctions",
                    "punishment_steps",
                    "cases",
                    "player_names",
                    "players"
            )) {
                try (PreparedStatement statement = connection.prepareStatement("DELETE FROM " + table)) { // nosemgrep
                    statement.executeUpdate();
                }
            }
        }
        insertPlayer(ACTOR_ID, "HistoryModerator", PlayerPlatform.JAVA, BASE);
    }

    @Test
    void playerResolutionSupportsUuidCurrentHistoricalBedrockAndAmbiguity() throws Exception {
        UUID javaId = uuid(1);
        UUID bedrockId = uuid(2);
        UUID ambiguousId = uuid(3);
        insertPlayer(javaId, "CurrentJava", PlayerPlatform.JAVA, BASE.plusSeconds(30));
        insertName(javaId, "OldJava", BASE, BASE.plusSeconds(10));
        insertPlayer(bedrockId, "BedrockKnown", PlayerPlatform.BEDROCK, BASE.plusSeconds(20));
        insertName(bedrockId, "SharedOld", BASE, BASE.plusSeconds(5));
        insertPlayer(ambiguousId, "OtherCurrent", PlayerPlatform.JAVA, BASE.plusSeconds(15));
        insertName(ambiguousId, "SharedOld", BASE, BASE.plusSeconds(6));

        try (MariaDbRuntime runtime = MariaDb.initialize(databaseConfig())) {
            PlayerResolution.Resolved uuidResult = assertInstanceOf(
                    PlayerResolution.Resolved.class,
                    runtime.playerDirectory().resolve(javaId.toString())
            );
            assertEquals(PlayerResolution.MatchKind.UUID, uuidResult.matchKind());
            assertEquals(javaId, uuidResult.identity().playerId());

            PlayerResolution.Resolved current = assertInstanceOf(
                    PlayerResolution.Resolved.class,
                    runtime.playerDirectory().resolve("currentjava")
            );
            assertEquals(PlayerResolution.MatchKind.CURRENT_USERNAME, current.matchKind());
            assertEquals(javaId, current.identity().playerId());

            PlayerResolution.Resolved historical = assertInstanceOf(
                    PlayerResolution.Resolved.class,
                    runtime.playerDirectory().resolve("OLDJAVA")
            );
            assertEquals(PlayerResolution.MatchKind.HISTORICAL_USERNAME, historical.matchKind());
            assertEquals(javaId, historical.identity().playerId());

            PlayerResolution.Resolved bedrock = assertInstanceOf(
                    PlayerResolution.Resolved.class,
                    runtime.playerDirectory().resolve("BedrockKnown")
            );
            assertEquals(PlayerPlatform.BEDROCK, bedrock.identity().platform());

            PlayerResolution.Ambiguous ambiguous = assertInstanceOf(
                    PlayerResolution.Ambiguous.class,
                    runtime.playerDirectory().resolve("sharedold")
            );
            assertEquals(Set.of(bedrockId, ambiguousId), ambiguous.matches().stream()
                    .map(identity -> identity.playerId())
                    .collect(Collectors.toSet()));
            assertInstanceOf(
                    PlayerResolution.Missing.class,
                    runtime.playerDirectory().resolve("missing-player")
            );
        }
    }

    @Test
    void emptyHistoryAndDatabasePaginationHaveStableBoundaries() throws Exception {
        UUID subjectId = uuid(10);
        insertPlayer(subjectId, "EmptyThenPaged", PlayerPlatform.JAVA, BASE);
        try (MariaDbRuntime runtime = MariaDb.initialize(databaseConfig())) {
            ModerationHistoryPage empty = runtime.moderationHistoryStore().page(
                    subjectId,
                    1,
                    2,
                    HistoryQueryOptions.publicStaffView(true, true)
            );
            assertEquals(0, empty.totalEntries());
            assertEquals(0, empty.totalPages());
            assertTrue(empty.entries().isEmpty());
            assertThrows(IllegalArgumentException.class, () -> runtime.moderationHistoryStore().page(
                    subjectId,
                    2,
                    2,
                    HistoryQueryOptions.publicStaffView(true, true)
            ));
        }

        insertCase(caseId(1), subjectId, BASE.plusSeconds(50), "Reason one", "Internal one");
        insertCase(caseId(2), subjectId, BASE.plusSeconds(50), "Reason two", "Internal two");
        insertCase(caseId(3), subjectId, BASE.plusSeconds(50), "Reason three", "Internal three");

        try (MariaDbRuntime runtime = MariaDb.initialize(databaseConfig())) {
            ModerationHistoryPage first = runtime.moderationHistoryStore().page(
                    subjectId,
                    1,
                    2,
                    HistoryQueryOptions.publicStaffView(false, false)
            );
            ModerationHistoryPage second = runtime.moderationHistoryStore().page(
                    subjectId,
                    2,
                    2,
                    HistoryQueryOptions.publicStaffView(false, false)
            );
            assertEquals(3, first.totalEntries());
            assertEquals(2, first.totalPages());
            assertEquals(List.of("case:" + caseId(3).value(), "case:" + caseId(2).value()),
                    first.entries().stream().map(ModerationHistoryEntry::stableKey).toList());
            assertEquals(List.of("case:" + caseId(1).value()),
                    second.entries().stream().map(ModerationHistoryEntry::stableKey).toList());
            assertThrows(IllegalArgumentException.class, () -> runtime.moderationHistoryStore().page(
                    subjectId,
                    3,
                    2,
                    HistoryQueryOptions.publicStaffView(false, false)
            ));
        }
    }

    @Test
    void historyIncludesRequestAppealSanctionAndMutationEventsWithSensitiveGating() throws Exception {
        UUID subjectId = uuid(20);
        UUID sanctionId = uuid(21);
        UUID requestId = uuid(22);
        UUID appealId = uuid(23);
        CaseId caseId = caseId(20);
        insertPlayer(subjectId, "TimelinePlayer", PlayerPlatform.BEDROCK, BASE);
        insertCase(caseId, subjectId, BASE, "Public case reason", "Private case evidence");
        insertSanction(
                sanctionId,
                caseId,
                subjectId,
                "BAN",
                "EXPIRED",
                BASE.plusSeconds(10),
                BASE.plusSeconds(30)
        );
        insertSanctionEvent(
                uuid(24),
                sanctionId,
                caseId,
                subjectId,
                "REDUCE_DURATION",
                "ACTIVE",
                "ACTIVE",
                BASE.plusSeconds(50),
                BASE.plusSeconds(40),
                "Private reduction explanation"
        );
        insertPunishmentRequest(requestId, subjectId, caseId, BASE.plusSeconds(5));
        insertPunishmentRequestEvent(uuid(25), requestId, "SUBMITTED", null, BASE.plusSeconds(5));
        insertPunishmentRequestEvent(uuid(26), requestId, "APPROVED", caseId, BASE.plusSeconds(15));
        insertAppeal(appealId, sanctionId, caseId, BASE.plusSeconds(60), BASE.plusSeconds(70));
        insertStaffNote(uuid(27), subjectId, "Private administrative note", BASE.plusSeconds(80));

        try (MariaDbRuntime runtime = MariaDb.initialize(databaseConfig())) {
            ModerationHistoryPage publicPage = runtime.moderationHistoryStore().page(
                    subjectId,
                    1,
                    50,
                    new HistoryQueryOptions(true, true, false)
            );
            Set<HistoryEventType> publicTypes = publicPage.entries().stream()
                    .map(ModerationHistoryEntry::eventType)
                    .collect(Collectors.toSet());
            assertTrue(publicTypes.containsAll(Set.of(
                    HistoryEventType.CASE_CREATED,
                    HistoryEventType.SANCTION_CREATED,
                    HistoryEventType.SANCTION_ACTIVATED,
                    HistoryEventType.SANCTION_EXPIRED,
                    HistoryEventType.SANCTION_REDUCED,
                    HistoryEventType.PUNISHMENT_REQUEST_SUBMITTED,
                    HistoryEventType.PUNISHMENT_REQUEST_APPROVED,
                    HistoryEventType.APPEAL_SUBMITTED,
                    HistoryEventType.APPEAL_DECIDED
            )));
            assertFalse(publicTypes.contains(HistoryEventType.ADMINISTRATIVE_NOTE));
            assertTrue(publicPage.entries().stream().allMatch(entry -> entry.actorId().isEmpty()));
            assertTrue(publicPage.entries().stream().allMatch(entry -> entry.actorName().isEmpty()));
            assertTrue(publicPage.entries().stream().allMatch(entry -> entry.sensitiveReason().isEmpty()));

            ModerationHistoryPage sensitivePage = runtime.moderationHistoryStore().page(
                    subjectId,
                    1,
                    50,
                    new HistoryQueryOptions(true, true, true)
            );
            assertTrue(sensitivePage.entries().stream()
                    .anyMatch(entry -> entry.eventType() == HistoryEventType.ADMINISTRATIVE_NOTE));
            assertTrue(sensitivePage.entries().stream()
                    .anyMatch(entry -> entry.sensitiveReason().orElse("")
                            .contains("Private reduction explanation")));
            assertTrue(sensitivePage.entries().stream()
                    .anyMatch(entry -> entry.actorId().isPresent()));

            ModerationHistoryPage filtered = runtime.moderationHistoryStore().page(
                    subjectId,
                    1,
                    50,
                    new HistoryQueryOptions(false, false, false)
            );
            assertFalse(filtered.entries().stream().anyMatch(entry ->
                    entry.punishmentRequestId().isPresent() || entry.appealId().isPresent()));
        }
    }

    @Test
    void caseDetailShowsMultipleSanctionsAndTheCompleteCaseTimeline() throws Exception {
        UUID subjectId = uuid(30);
        CaseId caseId = caseId(30);
        insertPlayer(subjectId, "MultiSanction", PlayerPlatform.JAVA, BASE);
        insertCase(caseId, subjectId, BASE, "Multiple actions", "Private detail");
        insertSanction(uuid(31), caseId, subjectId, "BAN", "ACTIVE", BASE.plusSeconds(1), null);
        insertSanction(
                uuid(32),
                caseId,
                subjectId,
                "MUTE",
                "ENDED_EARLY",
                BASE.plusSeconds(2),
                BASE.plusSeconds(100)
        );
        insertSanctionEvent(
                uuid(33),
                uuid(32),
                caseId,
                subjectId,
                "END_EARLY",
                "ACTIVE",
                "ENDED_EARLY",
                BASE.plusSeconds(100),
                BASE.plusSeconds(100),
                "Ended after review"
        );

        try (MariaDbRuntime runtime = MariaDb.initialize(databaseConfig())) {
            CaseHistoryDetail detail = runtime.moderationHistoryStore().caseDetail(
                    caseId,
                    new HistoryQueryOptions(true, true, true)
            ).orElseThrow();
            assertEquals(2, detail.caseReview().sanctions().size());
            assertTrue(detail.timeline().stream()
                    .anyMatch(entry -> entry.eventType() == HistoryEventType.SANCTION_ENDED_EARLY));
            assertEquals(caseId, detail.caseReview().caseId());
        }
    }

    private static void insertPlayer(
            UUID playerId,
            String username,
            PlayerPlatform platform,
            Instant seenAt
    ) throws SQLException {
        try (HikariDataSource dataSource = MariaDb.open(databaseConfig());
             Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     INSERT INTO players(
                         player_id, current_username, lowercase_username, platform,
                         first_seen_at, last_seen_at)
                     VALUES (?, ?, LOWER(?), ?, ?, ?)
                     """)) {
            statement.setBytes(1, uuidBytes(playerId));
            statement.setString(2, username);
            statement.setString(3, username);
            statement.setString(4, platform.name());
            statement.setTimestamp(5, Timestamp.from(seenAt));
            statement.setTimestamp(6, Timestamp.from(seenAt));
            assertEquals(1, statement.executeUpdate());
        }
    }

    private static void insertName(
            UUID playerId,
            String username,
            Instant firstSeen,
            Instant lastSeen
    ) throws SQLException {
        try (HikariDataSource dataSource = MariaDb.open(databaseConfig());
             Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     INSERT INTO player_names(
                         player_id, username, lowercase_username, first_seen_at, last_seen_at)
                     VALUES (?, ?, LOWER(?), ?, ?)
                     """)) {
            statement.setBytes(1, uuidBytes(playerId));
            statement.setString(2, username);
            statement.setString(3, username);
            statement.setTimestamp(4, Timestamp.from(firstSeen));
            statement.setTimestamp(5, Timestamp.from(lastSeen));
            assertEquals(1, statement.executeUpdate());
        }
    }

    private static void insertCase(
            CaseId caseId,
            UUID subjectId,
            Instant issuedAt,
            String publicReason,
            String internalReason
    ) throws SQLException {
        try (HikariDataSource dataSource = MariaDb.open(databaseConfig());
             Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     INSERT INTO cases(
                         case_id, idempotency_key, target_id, actor_id, actor_name, actor_rank,
                         public_reason, exact_reason_id, sanction_family, internal_explanation,
                         configuration_version, visibility, state, issued_at, revision)
                     VALUES (?, ?, ?, ?, 'HistoryModerator', 'FOUNDER', ?, 'history.test',
                         'BAN', ?, 'history-test', 'PRIVATE', 'OPEN', ?, 0)
                     """)) {
            statement.setString(1, caseId.value());
            statement.setString(2, "history-case-" + caseId.value());
            statement.setBytes(3, uuidBytes(subjectId));
            statement.setBytes(4, uuidBytes(ACTOR_ID));
            statement.setString(5, publicReason);
            statement.setString(6, internalReason);
            statement.setTimestamp(7, Timestamp.from(issuedAt));
            assertEquals(1, statement.executeUpdate());
        }
    }

    private static void insertSanction(
            UUID sanctionId,
            CaseId caseId,
            UUID subjectId,
            String type,
            String status,
            Instant issuedAt,
            Instant expiration
    ) throws SQLException {
        try (HikariDataSource dataSource = MariaDb.open(databaseConfig());
             Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     INSERT INTO sanctions(
                         sanction_id, case_id, target_id, sanction_type, status,
                         issued_at, activated_at, expiration_at, ended_at, revision)
                     VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 0)
                     """)) {
            statement.setBytes(1, uuidBytes(sanctionId));
            statement.setString(2, caseId.value());
            statement.setBytes(3, uuidBytes(subjectId));
            statement.setString(4, type);
            statement.setString(5, status);
            statement.setTimestamp(6, Timestamp.from(issuedAt));
            statement.setTimestamp(7, Timestamp.from(issuedAt));
            if (expiration == null) {
                statement.setNull(8, java.sql.Types.TIMESTAMP);
            } else {
                statement.setTimestamp(8, Timestamp.from(expiration));
            }
            if (status.equals("ENDED_EARLY") || status.equals("REVOKED") || status.equals("OVERTURNED")) {
                statement.setTimestamp(9, Timestamp.from(issuedAt.plusSeconds(1)));
            } else {
                statement.setNull(9, java.sql.Types.TIMESTAMP);
            }
            assertEquals(1, statement.executeUpdate());
        }
    }

    private static void insertSanctionEvent(
            UUID eventId,
            UUID sanctionId,
            CaseId caseId,
            UUID subjectId,
            String type,
            String previousStatus,
            String resultingStatus,
            Instant previousExpiration,
            Instant resultingExpiration,
            String reason
    ) throws SQLException {
        try (HikariDataSource dataSource = MariaDb.open(databaseConfig());
             Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     INSERT INTO sanction_events(
                         event_id, sanction_id, case_id, subject_id, event_type,
                         previous_status, resulting_status, previous_expiration,
                         resulting_expiration, origin_runtime, actor_id, occurred_at,
                         reason, event_json, idempotency_key)
                     VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'SMP', ?, ?, ?, JSON_OBJECT(), ?)
                     """)) {
            statement.setBytes(1, uuidBytes(eventId));
            statement.setBytes(2, uuidBytes(sanctionId));
            statement.setString(3, caseId.value());
            statement.setBytes(4, uuidBytes(subjectId));
            statement.setString(5, type);
            statement.setString(6, previousStatus);
            statement.setString(7, resultingStatus);
            statement.setTimestamp(8, Timestamp.from(previousExpiration));
            statement.setTimestamp(9, Timestamp.from(resultingExpiration));
            statement.setBytes(10, uuidBytes(ACTOR_ID));
            statement.setTimestamp(11, Timestamp.from(BASE.plusSeconds(40)));
            statement.setString(12, reason);
            statement.setString(13, "history-event-" + eventId);
            assertEquals(1, statement.executeUpdate());
        }
    }

    private static void insertPunishmentRequest(
            UUID requestId,
            UUID subjectId,
            CaseId caseId,
            Instant createdAt
    ) throws SQLException {
        try (HikariDataSource dataSource = MariaDb.open(databaseConfig());
             Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     INSERT INTO punishment_requests(
                         request_id, submission_key, match_key, open_match_key, target_id,
                         requester_id, requester_name, requester_rank, reason_id, sanction_family,
                         public_reason, internal_explanation, configuration_version, visibility,
                         required_rank, raw_ordinal, effective_ordinal, selected_ordinal,
                         recency_bonus, step_label, contribution_json, sanctions_json,
                         status, revision, resolved_by, resolution_note, resulting_case_id,
                         created_at, updated_at, expires_at, resolved_at)
                     VALUES (?, ?, ?, NULL, ?, ?, 'HistoryModerator', 'HELPER', 'history.request',
                         'BAN', 'Request public reason', 'Request private reason', 'history-test',
                         'PRIVATE', 'MOD', 1, 1, 1, 0, 'Step 1', JSON_OBJECT(), JSON_ARRAY(),
                         'APPROVED', 1, ?, 'Approved', ?, ?, ?, ?, ?)
                     """)) {
            statement.setBytes(1, uuidBytes(requestId));
            statement.setString(2, "submission-" + requestId);
            statement.setString(3, hex64(requestId));
            statement.setBytes(4, uuidBytes(subjectId));
            statement.setBytes(5, uuidBytes(ACTOR_ID));
            statement.setBytes(6, uuidBytes(ACTOR_ID));
            statement.setString(7, caseId.value());
            statement.setTimestamp(8, Timestamp.from(createdAt));
            statement.setTimestamp(9, Timestamp.from(createdAt.plusSeconds(10)));
            statement.setTimestamp(10, Timestamp.from(createdAt.plusSeconds(100)));
            statement.setTimestamp(11, Timestamp.from(createdAt.plusSeconds(10)));
            assertEquals(1, statement.executeUpdate());
        }
    }

    private static void insertPunishmentRequestEvent(
            UUID eventId,
            UUID requestId,
            String type,
            CaseId caseId,
            Instant occurredAt
    ) throws SQLException {
        try (HikariDataSource dataSource = MariaDb.open(databaseConfig());
             Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     INSERT INTO punishment_request_events(
                         event_id, request_id, event_type, actor_id, fence_token,
                         resulting_case_id, note, occurred_at)
                     VALUES (?, ?, ?, ?, NULL, ?, 'Private request decision note', ?)
                     """)) {
            statement.setBytes(1, uuidBytes(eventId));
            statement.setBytes(2, uuidBytes(requestId));
            statement.setString(3, type);
            statement.setBytes(4, uuidBytes(ACTOR_ID));
            if (caseId == null) {
                statement.setNull(5, java.sql.Types.CHAR);
            } else {
                statement.setString(5, caseId.value());
            }
            statement.setTimestamp(6, Timestamp.from(occurredAt));
            assertEquals(1, statement.executeUpdate());
        }
    }

    private static void insertAppeal(
            UUID appealId,
            UUID sanctionId,
            CaseId caseId,
            Instant createdAt,
            Instant updatedAt
    ) throws SQLException {
        try (HikariDataSource dataSource = MariaDb.open(databaseConfig());
             Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     INSERT INTO website_appeal_requests(
                         appeal_id, punishment_id, case_id, player_account_token,
                         idempotency_key, state, outcome_code, created_at, updated_at)
                     VALUES (?, ?, ?, ?, ?, 'APPLIED', 'ACCEPTED', ?, ?)
                     """)) {
            statement.setBytes(1, uuidBytes(appealId));
            statement.setBytes(2, uuidBytes(sanctionId));
            statement.setString(3, caseId.value());
            statement.setBytes(4, new byte[32]);
            statement.setString(5, "history-appeal-" + appealId);
            statement.setTimestamp(6, Timestamp.from(createdAt));
            statement.setTimestamp(7, Timestamp.from(updatedAt));
            assertEquals(1, statement.executeUpdate());
        }
    }

    private static void insertStaffNote(
            UUID noteId,
            UUID subjectId,
            String note,
            Instant createdAt
    ) throws SQLException {
        try (HikariDataSource dataSource = MariaDb.open(databaseConfig());
             Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     INSERT INTO staff_notes(note_id, target_id, actor_id, note_text, created_at)
                     VALUES (?, ?, ?, ?, ?)
                     """)) {
            statement.setBytes(1, uuidBytes(noteId));
            statement.setBytes(2, uuidBytes(subjectId));
            statement.setBytes(3, uuidBytes(ACTOR_ID));
            statement.setString(4, note);
            statement.setTimestamp(5, Timestamp.from(createdAt));
            assertEquals(1, statement.executeUpdate());
        }
    }

    private static net.enthusia.staff.persistence.DatabaseConfig databaseConfig() {
        return MariaDbIntegrationSupport.databaseConfig(DATABASE);
    }

    private static byte[] uuidBytes(UUID value) {
        return MariaDbIntegrationSupport.uuidBytes(value);
    }

    private static UUID uuid(int value) {
        return new UUID(0L, value);
    }

    private static CaseId caseId(int value) {
        return new CaseId("HIST" + String.format(java.util.Locale.ROOT, "%012d", value));
    }

    private static String hex64(UUID value) {
        return String.format(java.util.Locale.ROOT, "%064x", value.getLeastSignificantBits());
    }
}
