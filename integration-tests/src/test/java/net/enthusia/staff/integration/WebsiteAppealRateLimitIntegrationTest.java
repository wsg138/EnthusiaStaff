package net.enthusia.staff.integration;

import static net.enthusia.staff.integration.MariaDbIntegrationSupport.clearWebsiteModerationFixtures;
import static net.enthusia.staff.integration.MariaDbIntegrationSupport.databaseConfig;
import static net.enthusia.staff.integration.MariaDbIntegrationSupport.insertCase;
import static net.enthusia.staff.integration.MariaDbIntegrationSupport.insertPlayer;
import static net.enthusia.staff.integration.MariaDbIntegrationSupport.insertSanction;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.crypto.spec.SecretKeySpec;
import net.enthusia.staff.common.CaseId;
import net.enthusia.staff.common.security.PunishmentCodeProtector;
import net.enthusia.staff.domain.ports.WebsiteModerationStore;
import net.enthusia.staff.domain.website.WebsiteAppealSubmission;
import net.enthusia.staff.domain.website.WebsiteModerationException;
import net.enthusia.staff.persistence.MariaDb;
import net.enthusia.staff.persistence.MariaDbRuntime;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class WebsiteAppealRateLimitIntegrationTest {
    private static final Instant NOW = Instant.parse("2026-08-06T12:00:00Z");
    private static final String PLAYER_NAME = "LimitedPlayer";
    private static final String ACCOUNT_ID = uuid(3_001).toString();
    private static final PunishmentCodeProtector CODE_PROTECTOR = new PunishmentCodeProtector(
            1,
            new SecretKeySpec(
                    "website-appeal-rate-limit-integration-key"
                            .getBytes(StandardCharsets.UTF_8),
                    "HmacSHA256"
            )
    );

    @Container
    private static final MariaDBContainer<?> DATABASE = new MariaDBContainer<>("mariadb:11.8.3")
            .withDatabaseName("enthusia_staff_website_appeal_rate_limit_test")
            .withUsername("website_appeal_rate_limit_user")
            .withPassword(UUID.randomUUID().toString());

    @BeforeAll
    static void migrateSchema() {
        try (MariaDbRuntime runtime = MariaDb.initialize(databaseConfig(DATABASE))) {
            assertNotNull(runtime.websiteModerationStore(CODE_PROTECTOR));
        }
    }

    @BeforeEach
    void clearFixtures() throws SQLException {
        clearWebsiteModerationFixtures(DATABASE);
    }

    @Test
    void identicalRetriesAreExemptWhileFourthDistinctSubmissionIsLimited() throws SQLException {
        List<AppealFixture> fixtures = new ArrayList<>();
        for (int suffix = 1; suffix <= 4; suffix++) {
            fixtures.add(seedEligiblePunishment(suffix));
        }

        try (MariaDbRuntime runtime = MariaDb.initialize(databaseConfig(DATABASE))) {
            WebsiteModerationStore store = runtime.websiteModerationStore(CODE_PROTECTOR);
            for (AppealFixture fixture : fixtures) {
                String code = store.codeForSanction(fixture.sanctionId(), NOW).orElseThrow().code();
                store.claimCode(code, ACCOUNT_ID, PLAYER_NAME, NOW);
            }

            for (int index = 0; index < 3; index++) {
                store.submitAppeal(
                        fixtures.get(index).sanctionId(),
                        ACCOUNT_ID,
                        PLAYER_NAME,
                        "Distinct appeal submission number " + (index + 1) + " is ready for review.",
                        "rate-submission-" + (index + 1),
                        NOW.plusSeconds(index + 1L)
                );
            }

            WebsiteModerationException limited = assertThrows(
                    WebsiteModerationException.class,
                    () -> store.submitAppeal(
                            fixtures.get(3).sanctionId(),
                            ACCOUNT_ID,
                            PLAYER_NAME,
                            "The fourth distinct submission should be limited within the hour.",
                            "rate-submission-4",
                            NOW.plusSeconds(4)
                    )
            );
            assertEquals(WebsiteModerationException.Kind.RATE_LIMITED, limited.kind());
            assertEquals("APPEAL_RATE_LIMITED", limited.code());

            WebsiteAppealSubmission replay = store.submitAppeal(
                    fixtures.getFirst().sanctionId(),
                    ACCOUNT_ID,
                    PLAYER_NAME,
                    "Distinct appeal submission number 1 is ready for review.",
                    "rate-submission-1",
                    NOW.plusSeconds(5)
            );
            assertTrue(replay.replayed());

            WebsiteAppealSubmission afterReset = store.submitAppeal(
                    fixtures.get(3).sanctionId(),
                    ACCOUNT_ID,
                    PLAYER_NAME,
                    "The fourth submission is accepted after the one-hour window resets.",
                    "rate-submission-4",
                    NOW.plusSeconds(3_602)
            );
            assertEquals("OPEN", afterReset.appeal().state());
        }
    }

    private static AppealFixture seedEligiblePunishment(int suffix) throws SQLException {
        CaseId caseId = new CaseId("%016d".formatted(3_000L + suffix));
        UUID playerId = uuid(3_100L + suffix);
        UUID sanctionId = uuid(3_200L + suffix);
        Instant issuedAt = NOW.minusSeconds(60L + suffix);
        insertPlayer(DATABASE, playerId, PLAYER_NAME, issuedAt);
        insertCase(DATABASE, caseId.value(), playerId, uuid(3_300L + suffix), "PUBLIC", issuedAt);
        insertSanction(
                DATABASE,
                sanctionId,
                caseId.value(),
                playerId,
                "BAN",
                "ACTIVE",
                issuedAt,
                NOW.plusSeconds(7_200)
        );
        return new AppealFixture(caseId, sanctionId);
    }

    private static UUID uuid(long suffix) {
        return new UUID(0, suffix);
    }

    private record AppealFixture(CaseId caseId, UUID sanctionId) {
    }
}
