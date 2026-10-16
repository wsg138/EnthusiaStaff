package net.enthusia.staff.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import net.enthusia.staff.persistence.DiscordStaffReadRuntime;
import net.enthusia.staff.persistence.MariaDb;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Proves the private review queue's actual SQL against the deployed Flyway schema. */
@Testcontainers
class DiscordReviewQueueReadIntegrationTest {
    @Container
    private static final MariaDBContainer<?> DATABASE = new MariaDBContainer<>("mariadb:11.8.3")
            .withDatabaseName("enthusia_staff_discord_review_read_test")
            .withUsername("enthusia_test")
            .withPassword("enthusia_test_password");

    @BeforeAll
    static void migrate() {
        try (var runtime = MariaDb.initialize(MariaDbIntegrationSupport.databaseConfig(DATABASE))) {
            // Flyway must accept V31 after V30 before any bot read is possible.
            assertNotNull(runtime.reportStore());
        }
    }

    @Test
    void emptyQueueAndReviewPulseAreReadableUsingReadOnlyDatabasePool() throws Exception {
        try (DiscordStaffReadRuntime read = DiscordStaffReadRuntime.open(
                MariaDbIntegrationSupport.databaseConfig(DATABASE), Clock.systemUTC())) {
            Instant now = Instant.now();
            assertTrue(read.pendingReviews(now, 4).isEmpty());
            assertTrue(read.pendingReports(4).isEmpty());
            assertTrue(read.pendingAltAlerts(2).isEmpty());
            var pulse = read.reviewPulse(now);
            assertEquals(0L, pulse.pendingPunishmentRequests());
            assertEquals(0L, pulse.openReports());
            assertEquals(0L, pulse.claimedReports());
            assertEquals(0L, pulse.altSignalsInLastDay());
        }
        UUID reporter = UUID.randomUUID();
        UUID target = UUID.randomUUID();
        MariaDbIntegrationSupport.insertPlayer(DATABASE, reporter, "PreviewReporter",
                ReportIntegrationFixtures.NOW);
        MariaDbIntegrationSupport.insertPlayer(DATABASE, target, "PreviewTarget",
                ReportIntegrationFixtures.NOW);
        try (var write = MariaDb.initialize(MariaDbIntegrationSupport.databaseConfig(DATABASE))) {
            ReportIntegrationFixtures.accepted(write.reportStore().submit(
                    ReportIntegrationFixtures.request(reporter, target,
                            "report:private-preview-" + reporter,
                            ReportIntegrationFixtures.NOW, "private test body")));
        }
        try (DiscordStaffReadRuntime read = DiscordStaffReadRuntime.open(
                MariaDbIntegrationSupport.databaseConfig(DATABASE), Clock.systemUTC())) {
            var metadata = read.pendingReports(4);
            assertEquals(1, metadata.size());
            assertEquals("", metadata.getFirst().description());
            var ephemeral = read.pendingReports(4, true);
            assertEquals(1, ephemeral.size());
            assertEquals("Report description private test body", ephemeral.getFirst().description());
            assertEquals("PreviewReporter", ephemeral.getFirst().reporterName());
            assertEquals("PreviewTarget", ephemeral.getFirst().targetName());
        }
    }
}
