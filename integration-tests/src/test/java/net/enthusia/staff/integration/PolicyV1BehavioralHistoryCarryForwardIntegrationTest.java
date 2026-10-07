package net.enthusia.staff.integration;

import static net.enthusia.staff.integration.MariaDbIntegrationSupport.databaseConfig;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zaxxer.hikari.HikariDataSource;
import java.nio.ByteBuffer;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import net.enthusia.staff.domain.policyv2.legacy.PolicyV1BehavioralHistorySource;
import net.enthusia.staff.persistence.JdbcPolicyV1BehavioralHistorySource;
import net.enthusia.staff.persistence.MariaDb;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class PolicyV1BehavioralHistoryCarryForwardIntegrationTest {
    private static final Instant NOW = Instant.parse("2026-10-07T20:00:00Z");
    private static final UUID TARGET = UUID.fromString("75000000-0000-0000-0000-000000000001");
    private static final UUID ACTOR = UUID.fromString("75000000-0000-0000-0000-000000000002");
    private static final int INCLUDED_CASES = 205;

    @Container
    private static final MariaDBContainer<?> DATABASE = new MariaDBContainer<>("mariadb:11.8.3")
            .withDatabaseName("enthusia_policy_v1_carry_forward")
            .withUsername("staff_test")
            .withPassword(UUID.randomUUID().toString());

    @BeforeAll
    static void migrate() {
        try (HikariDataSource dataSource = MariaDb.open(databaseConfig(DATABASE))) {
            MariaDb.migrate(dataSource);
        }
    }

    @Test
    void completeReaderExceedsDisplayLimitAndFiltersNonContributingOverturnedAndFutureCases() throws Exception {
        try (HikariDataSource dataSource = MariaDb.open(databaseConfig(DATABASE))) {
            seedPlayer(dataSource);
            for (int index = 0; index < INCLUDED_CASES; index++) {
                seedCase(
                        dataSource,
                        caseId(index),
                        NOW.minusSeconds(10_000L - index),
                        "OPEN",
                        true,
                        "cheating.xray-esp"
                );
            }
            seedCase(
                    dataSource,
                    caseId(900),
                    NOW.minusSeconds(20),
                    "FULLY_OVERTURNED",
                    true,
                    "cheating.xray-esp"
            );
            seedCase(
                    dataSource,
                    caseId(901),
                    NOW.minusSeconds(10),
                    "OPEN",
                    false,
                    "cheating.xray-esp"
            );
            seedCase(
                    dataSource,
                    caseId(902),
                    NOW.plusSeconds(10),
                    "OPEN",
                    true,
                    "cheating.xray-esp"
            );

            long beforeCases = count(dataSource, "cases");
            long beforeSteps = count(dataSource, "punishment_steps");

            List<PolicyV1BehavioralHistorySource.LegacyFinding> history =
                    new JdbcPolicyV1BehavioralHistorySource(dataSource).completeHistory(TARGET, NOW);

            assertEquals(INCLUDED_CASES, history.size());
            assertEquals(caseId(0), history.getFirst().caseId());
            assertEquals(caseId(INCLUDED_CASES - 1), history.getLast().caseId());
            assertTrue(history.stream().allMatch(entry -> entry.exactReasonId().equals("cheating.xray-esp")));
            assertTrue(history.stream().allMatch(entry -> !entry.occurredAt().isAfter(NOW)));
            assertEquals(beforeCases, count(dataSource, "cases"));
            assertEquals(beforeSteps, count(dataSource, "punishment_steps"));
        }
    }

    private static void seedPlayer(HikariDataSource dataSource) throws Exception {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     INSERT INTO players(player_id, platform, first_seen_at, last_seen_at)
                     VALUES (?, 'JAVA', ?, ?)
                     """)) {
            statement.setBytes(1, uuidBytes(TARGET));
            statement.setTimestamp(2, Timestamp.from(NOW.minusSeconds(20_000)));
            statement.setTimestamp(3, Timestamp.from(NOW));
            statement.executeUpdate();
        }
    }

    private static void seedCase(
            HikariDataSource dataSource,
            String caseId,
            Instant issuedAt,
            String state,
            boolean contributes,
            String exactReasonId
    ) throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement cases = connection.prepareStatement("""
                    INSERT INTO cases(
                        case_id, idempotency_key, target_id, actor_id, actor_name, actor_rank,
                        public_reason, exact_reason_id, sanction_family, internal_explanation,
                        configuration_version, visibility, state, issued_at
                    ) VALUES (?, ?, ?, ?, 'Legacy Tester', 'ADMIN', 'Legacy test case', ?,
                              'legacy-test', '', 'legacy-test', 'PRIVATE', ?, ?)
                    """);
                 PreparedStatement steps = connection.prepareStatement("""
                    INSERT INTO punishment_steps(
                        case_id, raw_ordinal, effective_ordinal, recency_bonus, step_label,
                        contribution_json, escalation_contributes
                    ) VALUES (?, 0, 0, 0, 'Legacy', '[]', ?)
                    """)) {
                cases.setString(1, caseId);
                cases.setString(2, "legacy-carry-forward:" + caseId);
                cases.setBytes(3, uuidBytes(TARGET));
                cases.setBytes(4, uuidBytes(ACTOR));
                cases.setString(5, exactReasonId);
                cases.setString(6, state);
                cases.setTimestamp(7, Timestamp.from(issuedAt));
                cases.executeUpdate();

                steps.setString(1, caseId);
                steps.setBoolean(2, contributes);
                steps.executeUpdate();
                connection.commit();
            } catch (Exception exception) {
                connection.rollback();
                throw exception;
            }
        }
    }

    private static long count(HikariDataSource dataSource, String table) throws Exception {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT COUNT(*) FROM " + table);
             var result = statement.executeQuery()) {
            result.next();
            return result.getLong(1);
        }
    }

    private static byte[] uuidBytes(UUID value) {
        return ByteBuffer.allocate(16)
                .putLong(value.getMostSignificantBits())
                .putLong(value.getLeastSignificantBits())
                .array();
    }

    private static String caseId(int index) {
        return "L%015d".formatted(index);
    }
}
