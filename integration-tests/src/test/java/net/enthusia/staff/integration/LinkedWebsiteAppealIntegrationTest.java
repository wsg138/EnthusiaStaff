package net.enthusia.staff.integration;

import static net.enthusia.staff.integration.MariaDbIntegrationSupport.databaseConfig;
import static net.enthusia.staff.integration.MariaDbIntegrationSupport.connection;
import static net.enthusia.staff.integration.MariaDbIntegrationSupport.insertPlayer;
import static net.enthusia.staff.integration.MariaDbIntegrationSupport.insertCase;
import static net.enthusia.staff.integration.MariaDbIntegrationSupport.insertSanction;
import static net.enthusia.staff.integration.MariaDbIntegrationSupport.uuidBytes;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import javax.crypto.spec.SecretKeySpec;
import net.enthusia.staff.common.security.PunishmentCodeProtector;
import net.enthusia.staff.domain.ports.WebsiteModerationStore;
import net.enthusia.staff.domain.website.WebsiteModerationException;
import net.enthusia.staff.persistence.MariaDb;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class LinkedWebsiteAppealIntegrationTest {
    private static final Instant NOW = Instant.parse("2026-10-13T15:00:00Z");
    private static final String REASON = "Please review the complete context of this punishment.";
    private static final PunishmentCodeProtector PROTECTOR = protector();
    @Container
    private static final MariaDBContainer<?> DATABASE = new MariaDBContainer<>("mariadb:11.8.3")
            .withDatabaseName("linked_website_appeals").withUsername("appeals_test")
            .withPassword(UUID.randomUUID().toString());

    @BeforeAll
    static void migrate() {
        try (var runtime = MariaDb.initialize(databaseConfig(DATABASE))) {
            assertNotNull(runtime.websiteModerationStore(PROTECTOR));
        }
    }

    @Test
    void listsCurrentOwnershipAndExpiredWarningsWithoutCodes() throws Exception {
        var owner = owner("*BedrockPlayer With Spaces");
        UUID active = sanction(owner, "MUTE", "ACTIVE", NOW.plusSeconds(300));
        UUID expired = sanction(owner, "WARN", "EXPIRED", NOW.minusSeconds(30));
        UUID ended = sanction(owner, "MUTE", "ENDED_EARLY", NOW.plusSeconds(300));
        try (var runtime = MariaDb.initialize(databaseConfig(DATABASE))) {
            var store = runtime.websiteModerationStore(PROTECTOR);
            assertEquals(active, store.linkedPunishments(owner.discord(), Optional.empty(), "ACTIVE", Optional.empty(), 100, NOW).punishments().getFirst().punishmentId());
            var historical = store.linkedPunishments(owner.discord(), Optional.empty(), "EXPIRED", Optional.empty(), 100, NOW).punishments();
            assertEquals(2, historical.size());
            assertTrue(historical.stream().anyMatch(item -> item.punishmentId().equals(expired)));
            assertTrue(historical.stream().anyMatch(item -> item.punishmentId().equals(ended)));
            assertTrue(historical.stream().noneMatch(item -> item.active()));
            assertTrue(historical.stream().allMatch(item -> item.appealable()));
            var submitted = store.submitLinkedAppeal(owner.discord(), owner.player(), expired, owner.account(), REASON, "expired-warning", NOW);
            assertEquals(owner.name(), submitted.appeal().playerUsername());
            assertEquals("OPEN", submitted.appeal().state());
            UUID reviewer = UUID.randomUUID();
            var claimed = store.claimAppeal(submitted.appeal().appealId(), 1, reviewer, "MOD", "claim-expired-warning", NOW.plusSeconds(1));
            var approved = store.prepareAppealDecision(submitted.appeal().appealId(), claimed.appeal().version(), "approve",
                    "The expired warning is supported by the evidence.", reviewer, "MOD", "approve-expired-warning", NOW.plusSeconds(2));
            assertTrue(approved.requiresAcceptance());
            assertInstanceOf(net.enthusia.staff.domain.website.AppealAcceptancePreparation.Ready.class,
                    store.prepareAppealAcceptance(submitted.appeal().appealId(), expired, submitted.appeal().caseId(), owner.account(), "workflow:" + submitted.appeal().appealId(), NOW.plusSeconds(3)));
            long revision = runtime.sanctionMutationStore().exactRevision(expired).orElseThrow();
            store.completeAppealAcceptance(submitted.appeal().appealId(), "APPLIED",
                    net.enthusia.staff.domain.website.AppealMutationPendingOutcome.encode(revision), NOW.plusSeconds(4));
            var request = new net.enthusia.staff.domain.sanction.ExactSanctionChangeRequest(
                    new net.enthusia.staff.common.IdempotencyKey("website-appeal:workflow:" + submitted.appeal().appealId()), expired,
                    revision,
                    new net.enthusia.staff.domain.auth.Actor(reviewer, "ReviewStaff", net.enthusia.staff.domain.auth.StaffRank.MOD),
                    net.enthusia.staff.domain.sanction.SanctionChangeAction.FULL_OVERTURN, Optional.empty(),
                    "Reviewing the expired warning", Optional.of(submitted.appeal().appealId()), Optional.empty(), "VELOCITY_WEBSITE", true);
            var service = new net.enthusia.staff.domain.application.SanctionChangeService(
                    new net.enthusia.staff.domain.auth.DefaultAuthorizationPolicy(), runtime.sanctionMutationStore());
            assertInstanceOf(net.enthusia.staff.domain.sanction.ExactSanctionChangeResult.Applied.class,
                    service.applyExact(request, net.enthusia.staff.domain.OperationalMode.ACTIVE,
                            new net.enthusia.staff.domain.sanction.SanctionActionLimits(10, 1000, true)));
            var foreign = owner("ForeignPlayer");
            assertTrue(store.linkedPunishments(foreign.discord(), Optional.empty(), "ALL", Optional.empty(), 100, NOW).punishments().isEmpty());
            assertEquals("PUNISHMENT_NOT_FOUND", assertThrows(WebsiteModerationException.class,
                    () -> store.submitLinkedAppeal(foreign.discord(), foreign.player(), active, foreign.account(), REASON, "foreign-attempt", NOW)).code());
            unlink(owner);
            assertTrue(store.linkedPunishments(owner.discord(), Optional.empty(), "ALL", Optional.empty(), 100, NOW).punishments().isEmpty());
            assertThrows(WebsiteModerationException.class, () -> store.submitLinkedAppeal(owner.discord(), owner.player(), active, owner.account(), REASON, "unlinked-attempt", NOW));
        }
    }

    @Test
    void intervalBoundaryReplaysAndHistorySurviveRestart() throws Exception {
        var owner = owner("IntervalPlayer");
        UUID punishment = sanction(owner, "BAN", "ACTIVE", null);
        UUID first;
        try (var runtime = MariaDb.initialize(databaseConfig(DATABASE))) {
            var store = runtime.websiteModerationStore(PROTECTOR);
            first = submit(store, owner, punishment, "first-attempt", NOW);
            try (Connection connection = connection(DATABASE); PreparedStatement statement = connection.prepareStatement("UPDATE players SET current_username=?,lowercase_username=? WHERE player_id=?")) {
                statement.setString(1, "RenamedPlayer"); statement.setString(2, "renamedplayer");
                statement.setBytes(3, uuidBytes(owner.player())); statement.executeUpdate();
            }
            var replay = store.submitLinkedAppeal(owner.discord(), owner.player(), punishment, owner.account(), REASON, "first-attempt", NOW.plusSeconds(1));
            assertTrue(replay.replayed());
            assertEquals(first, replay.appeal().appealId());
            assertEquals(owner.name(), replay.appeal().playerUsername());
            var list = store.linkedPunishments(owner.discord(), Optional.empty(), "ACTIVE", Optional.empty(), 100, NOW.plusSeconds(1)).punishments().getFirst();
            assertFalse(list.appealable());
            assertEquals(NOW.plus(Duration.ofDays(14)), list.nextAppealAt());
            assertEquals("APPEAL_COOLDOWN", assertThrows(WebsiteModerationException.class,
                    () -> submit(store, owner, punishment, "too-early", NOW.plus(Duration.ofDays(14)).minusSeconds(1))).code());
            UUID other = sanction(owner, "MUTE", "ACTIVE", null);
            assertNotNull(submit(store, owner, other, "different-punishment", NOW));
        }
        try (var runtime = MariaDb.initialize(databaseConfig(DATABASE))) {
            var store = runtime.websiteModerationStore(PROTECTOR);
            UUID second = submit(store, owner, punishment, "second-attempt", NOW.plus(Duration.ofDays(14)));
            assertNotEquals(first, second);
            assertEquals(3, store.playerAppeals(owner.account(), 100, NOW.plus(Duration.ofDays(14))).size());
            try (Connection connection = connection(DATABASE); PreparedStatement statement = connection.prepareStatement("UPDATE discord_minecraft_links SET discord_user_id=? WHERE minecraft_player_id=?")) {
                statement.setBigDecimal(1, new BigDecimal("444444444444444444")); statement.setBytes(2, uuidBytes(owner.player())); statement.executeUpdate();
            }
            assertEquals("APPEAL_COOLDOWN", assertThrows(WebsiteModerationException.class,
                    () -> store.submitLinkedAppeal("444444444444444444", owner.player(), punishment,
                            UUID.randomUUID().toString(), REASON, "new-owner-attempt", NOW.plus(Duration.ofDays(14)).plusSeconds(1))).code());
        }
    }

    @Test
    void concurrentDifferentDraftsCannotBypassPunishmentInterval() throws Exception {
        var owner = owner("ConcurrentPlayer");
        UUID punishment = sanction(owner, "BAN", "ACTIVE", null);
        try (var runtime = MariaDb.initialize(databaseConfig(DATABASE)); var executor = Executors.newFixedThreadPool(2)) {
            var store = runtime.websiteModerationStore(PROTECTOR);
            var start = new CountDownLatch(1);
            var first = executor.submit(() -> attempt(store, owner, punishment, "concurrent-first", start));
            var second = executor.submit(() -> attempt(store, owner, punishment, "concurrent-second", start));
            start.countDown();
            var outcomes = java.util.Set.of(first.get(), second.get());
            assertEquals(java.util.Set.of("SUBMITTED", "APPEAL_COOLDOWN"), outcomes);
            assertEquals(1, store.playerAppeals(owner.account(), 100, NOW).size());
        }
    }

    @Test
    void paginationRetainsEqualTimestampPunishmentsAndRejectsMalformedCursor() throws Exception {
        var owner = owner("PagingPlayer");
        for (int i = 0; i < 4; i++) sanction(owner, "BAN", "ACTIVE", null);
        try (var runtime = MariaDb.initialize(databaseConfig(DATABASE))) {
            var store = runtime.websiteModerationStore(PROTECTOR);
            var first = store.linkedPunishments(owner.discord(), Optional.of(owner.player()), "ACTIVE", Optional.empty(), 2, NOW);
            var second = store.linkedPunishments(owner.discord(), Optional.of(owner.player()), "ACTIVE", first.nextCursor(), 2, NOW);
            assertEquals(2, first.punishments().size());
            assertEquals(2, second.punishments().size());
            assertTrue(second.nextCursor().isEmpty());
            var ids = new java.util.HashSet<UUID>();
            first.punishments().forEach(item -> ids.add(item.punishmentId()));
            second.punishments().forEach(item -> ids.add(item.punishmentId()));
            assertEquals(4, ids.size());
            assertThrows(WebsiteModerationException.class,
                    () -> store.linkedPunishments(owner.discord(), Optional.empty(), "ACTIVE", Optional.of("not-a-cursor"), 2, NOW));
        }
    }

    private static UUID submit(WebsiteModerationStore store, Owner owner, UUID punishment, String key, Instant now) {
        return store.submitLinkedAppeal(owner.discord(), owner.player(), punishment, owner.account(), REASON, key, now).appeal().appealId();
    }

    private static String attempt(WebsiteModerationStore store, Owner owner, UUID punishment, String key, CountDownLatch start) throws Exception {
        start.await();
        try { submit(store, owner, punishment, key, NOW); return "SUBMITTED"; }
        catch (WebsiteModerationException exception) { return exception.code(); }
    }

    private static Owner owner(String name) throws Exception {
        UUID player = UUID.randomUUID();
        UUID subject = UUID.randomUUID();
        String discord = "1" + String.format("%017d", Math.floorMod(player.getLeastSignificantBits(), 100_000_000_000_000_000L));
        insertPlayer(DATABASE, player, name, NOW.minusSeconds(600));
        try (Connection connection = connection(DATABASE)) {
            try (PreparedStatement statement = connection.prepareStatement("INSERT INTO moderation_subjects(subject_id,created_at,updated_at) VALUES (?,?,?)")) {
                statement.setBytes(1, uuidBytes(subject)); statement.setTimestamp(2, Timestamp.from(NOW)); statement.setTimestamp(3, Timestamp.from(NOW)); statement.executeUpdate();
            }
            try (PreparedStatement statement = connection.prepareStatement("INSERT INTO discord_minecraft_links(link_id,operation_key,subject_id,discord_user_id,minecraft_player_id,linked_at,source) VALUES (?,?,?,?,?,?,'STAFF_RECOVERY')")) {
                statement.setBytes(1, uuidBytes(UUID.randomUUID())); statement.setString(2, UUID.randomUUID().toString());
                statement.setBytes(3, uuidBytes(subject)); statement.setBigDecimal(4, new BigDecimal(discord));
                statement.setBytes(5, uuidBytes(player)); statement.setTimestamp(6, Timestamp.from(NOW)); statement.executeUpdate();
            }
        }
        return new Owner(player, discord, UUID.randomUUID().toString(), name);
    }

    private static UUID sanction(Owner owner, String type, String status, Instant expiry) throws Exception {
        UUID id = UUID.randomUUID();
        String caseId = "%016d".formatted(Math.floorMod(id.getLeastSignificantBits(), 10_000_000_000_000_000L));
        insertCase(DATABASE, caseId, owner.player(), UUID.randomUUID(), "PUBLIC", NOW.minusSeconds(60));
        insertSanction(DATABASE, id, caseId, owner.player(), type, status, NOW.minusSeconds(60), expiry);
        return id;
    }

    private static void unlink(Owner owner) throws Exception {
        try (Connection connection = connection(DATABASE); PreparedStatement statement = connection.prepareStatement("UPDATE discord_minecraft_links SET unlinked_at=? WHERE minecraft_player_id=?")) {
            statement.setTimestamp(1, Timestamp.from(NOW)); statement.setBytes(2, uuidBytes(owner.player())); statement.executeUpdate();
        }
    }

    private static PunishmentCodeProtector protector() {
        byte[] key = new byte[32]; new SecureRandom().nextBytes(key);
        return new PunishmentCodeProtector(1, new SecretKeySpec(key, "HmacSHA256"));
    }
    private record Owner(UUID player, String discord, String account, String name) { }
}
