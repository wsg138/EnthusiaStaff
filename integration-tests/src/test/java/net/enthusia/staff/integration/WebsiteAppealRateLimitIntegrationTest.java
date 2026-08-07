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
import java.security.SecureRandom;
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
@SuppressWarnings("PMD.NcssCount")
class WebsiteAppealRateLimitIntegrationTest {
    private static final Instant NOW = Instant.parse("2026-08-06T12:00:00Z");
    private static final String PLAYER_NAME = "LimitedPlayer";
    private static final String ACCOUNT_ID = uuid(3_001).toString();
    private static final String SHARED_KEY = "rate-shared-key";
    private static final PunishmentCodeProtector CODE_PROTECTOR = testProtector();

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
    void replayExemptionRequiresTheSamePunishmentAndCurrentWindow() throws SQLException {
        List<AppealFixture> fixtures = new ArrayList<>();
        for (int suffix = 1; suffix <= 5; suffix++) {
            fixtures.add(seedEligiblePunishment(suffix));
        }

        try (MariaDbRuntime runtime = MariaDb.initialize(databaseConfig(DATABASE))) {
            WebsiteModerationStore store = runtime.websiteModerationStore(CODE_PROTECTOR);
            for (AppealFixture fixture : fixtures) {
                String code = store.codeForSanction(fixture.sanctionId(), NOW).orElseThrow().code();
                store.claimCode(code, ACCOUNT_ID, PLAYER_NAME, NOW);
            }

            String firstReason = "The first exact punishment is ready for appeal review.";
            store.submitAppeal(
                    fixtures.get(0).sanctionId(),
                    ACCOUNT_ID,
                    PLAYER_NAME,
                    firstReason,
                    SHARED_KEY,
                    NOW.plusSeconds(1)
            );
            store.submitAppeal(
                    fixtures.get(1).sanctionId(),
                    ACCOUNT_ID,
                    PLAYER_NAME,
                    "The second punishment reuses the browser key but remains distinct.",
                    SHARED_KEY,
                    NOW.plusSeconds(2)
            );
            store.submitAppeal(
                    fixtures.get(2).sanctionId(),
                    ACCOUNT_ID,
                    PLAYER_NAME,
                    "The third distinct appeal submission is ready for review.",
                    "rate-submission-3",
                    NOW.plusSeconds(3)
            );

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
                    fixtures.get(0).sanctionId(),
                    ACCOUNT_ID,
                    PLAYER_NAME,
                    firstReason,
                    SHARED_KEY,
                    NOW.plusSeconds(5)
            );
            assertTrue(replay.replayed());

            WebsiteAppealSubmission afterReset = store.submitAppeal(
                    fixtures.get(4).sanctionId(),
                    ACCOUNT_ID,
                    PLAYER_NAME,
                    "The expired replay key is counted in the new window and accepted.",
                    SHARED_KEY,
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

    private static PunishmentCodeProtector testProtector() {
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        return new PunishmentCodeProtector(
                1,
                new SecretKeySpec(key, "HmacSHA256")
        );
    }

    private static UUID uuid(long suffix) {
        return new UUID(0, suffix);
    }

    private record AppealFixture(CaseId caseId, UUID sanctionId) {
    }
}
