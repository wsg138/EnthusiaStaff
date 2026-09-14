package net.enthusia.staff.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zaxxer.hikari.HikariDataSource;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import net.enthusia.staff.domain.auth.Actor;
import net.enthusia.staff.domain.auth.DiscordConsequenceType;
import net.enthusia.staff.domain.auth.StaffRank;
import net.enthusia.staff.domain.discord.DiscordDeliveryOutcome;
import net.enthusia.staff.domain.discord.DiscordPunishment;
import net.enthusia.staff.domain.discord.DiscordPunishmentIntent;
import net.enthusia.staff.domain.discord.DiscordPunishmentState;
import net.enthusia.staff.domain.discord.DiscordRestrictionTarget;
import net.enthusia.staff.domain.moderation.DiscordGuildId;
import net.enthusia.staff.domain.moderation.DiscordUserId;
import net.enthusia.staff.domain.moderation.ModerationSubjectId;
import net.enthusia.staff.domain.ports.DiscordPunishmentRepository.WorkSchedule;
import net.enthusia.staff.domain.ports.DiscordPunishmentRepository.WorkType;
import net.enthusia.staff.domain.sanction.SanctionLength;
import net.enthusia.staff.persistence.JdbcDiscordModerationPersistenceStore;
import net.enthusia.staff.persistence.JdbcDiscordPunishmentRepository;
import net.enthusia.staff.persistence.MariaDb;
import net.enthusia.staff.persistence.ModerationPersistenceException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class DiscordPunishmentPersistenceIntegrationTest {
    private static final Instant NOW = Instant.parse("2026-09-14T20:00:00Z");
    private static final DiscordGuildId GUILD_ID = new DiscordGuildId("1410303324745371709");

    @Container
    private static final MariaDBContainer<?> DATABASE = new MariaDBContainer<>("mariadb:11.4.8")
            .withDatabaseName("enthusia_staff_d07")
            .withUsername("enthusia")
            .withPassword("enthusia-test-password");

    @BeforeAll
    static void migrate() {
        try (HikariDataSource dataSource = MariaDb.open(MariaDbIntegrationSupport.databaseConfig(DATABASE))) {
            MariaDb.migrate(dataSource);
        }
    }

    @BeforeEach
    void clearD07WorkQueue() throws SQLException {
        try (HikariDataSource dataSource = open();
             var connection = dataSource.getConnection();
             var statement = connection.prepareStatement(
                     "DELETE FROM discord_maintenance_work WHERE work_type IN ('D07_APPLY', 'D07_REMOVE', 'D07_RECONCILE')"
             )) {
            statement.executeUpdate();
        }
    }

    @Test
    void intentLeaseRecoveryRevisionConflictAndRestartAreDurable() {
        DiscordUserId userId = new DiscordUserId("18446744073709551001");
        DiscordPunishment punishment;
        String operationKey;

        try (HikariDataSource dataSource = open()) {
            JdbcDiscordModerationPersistenceStore identities = new JdbcDiscordModerationPersistenceStore(dataSource);
            var subject = identities.ensureDiscordSubject(userId, NOW);
            punishment = punishment(subject.subject().subjectId(), userId, muteIntent());
            operationKey = "d07-create-" + punishment.punishmentId();
            JdbcDiscordPunishmentRepository repository = new JdbcDiscordPunishmentRepository(dataSource);

            var created = repository.create(punishment, operationKey, NOW);
            var replay = repository.create(punishment, operationKey, NOW);
            assertFalse(created.replayed());
            assertTrue(replay.replayed());
            assertEquals(created.punishment().punishmentId(), replay.punishment().punishmentId());

            var firstLease = repository.claimDue(NOW, 1, "worker-a", NOW.plusSeconds(30));
            assertEquals(1, firstLease.size());
            assertEquals(1, firstLease.getFirst().attemptCount());
            assertTrue(repository.claimDue(NOW.plusSeconds(10), 1, "worker-b", NOW.plusSeconds(40)).isEmpty());
        }

        try (HikariDataSource dataSource = open()) {
            JdbcDiscordPunishmentRepository repository = new JdbcDiscordPunishmentRepository(dataSource);
            var persisted = repository.find(punishment.punishmentId()).orElseThrow();
            assertEquals(DiscordPunishmentState.PENDING_APPLY, persisted.punishment().state());

            Instant recoveredAt = NOW.plusSeconds(31);
            var recovered = repository.claimDue(
                    recoveredAt, 1, "worker-b", recoveredAt.plusSeconds(30)
            );
            assertEquals(1, recovered.size());
            assertEquals(2, recovered.getFirst().attemptCount());

            DiscordPunishment applied = persisted.punishment().withProcessingResult(
                    DiscordPunishmentState.APPLIED,
                    DiscordDeliveryOutcome.DELIVERED,
                    true,
                    Optional.empty(),
                    Optional.empty(),
                    "d07:work:recovered"
            );
            Instant reconcileAt = recoveredAt.plusSeconds(60);
            var settled = repository.settle(
                    recovered.getFirst(),
                    persisted,
                    applied,
                    List.of(new WorkSchedule(WorkType.RECONCILE, reconcileAt)),
                    recoveredAt
            );
            assertEquals(1, settled.revision());
            assertEquals(1, repository.activeForTarget(GUILD_ID, userId, DiscordConsequenceType.MUTE, 10).size());

            assertThrows(ModerationPersistenceException.class, () -> repository.transition(
                    persisted,
                    applied,
                    "d07-stale-" + UUID.randomUUID(),
                    List.of(),
                    recoveredAt.plusSeconds(1)
            ));
        }

        try (HikariDataSource dataSource = open()) {
            JdbcDiscordPunishmentRepository repository = new JdbcDiscordPunishmentRepository(dataSource);
            var restarted = repository.find(punishment.punishmentId()).orElseThrow();
            assertEquals(DiscordPunishmentState.APPLIED, restarted.punishment().state());
            assertTrue(restarted.punishment().externalApplied());
            assertTrue(repository.claimDue(NOW.plusSeconds(60), 10, "worker-c", NOW.plusSeconds(90)).isEmpty());
            var reconcile = repository.claimDue(
                    NOW.plusSeconds(91), 10, "worker-c", NOW.plusSeconds(121)
            );
            assertEquals(1, reconcile.size());
            assertEquals(WorkType.RECONCILE, reconcile.getFirst().type());
        }
    }

    @Test
    void concurrentDuplicateMuteCreationAllowsExactlyOneActivePunishment() throws Exception {
        DiscordUserId userId = new DiscordUserId("18446744073709551002");
        try (HikariDataSource dataSource = open()) {
            ModerationSubjectId subjectId = ensureSubject(dataSource, userId);
            JdbcDiscordPunishmentRepository repository = new JdbcDiscordPunishmentRepository(dataSource);
            DiscordPunishment first = punishment(subjectId, userId, muteIntent());
            DiscordPunishment second = punishment(subjectId, userId, muteIntent());
            CountDownLatch ready = new CountDownLatch(2);
            CountDownLatch start = new CountDownLatch(1);
            ExecutorService executor = Executors.newFixedThreadPool(2);
            try {
                Future<CreateAttempt> firstAttempt = executor.submit(
                        () -> createWhenReleased(repository, first, ready, start)
                );
                Future<CreateAttempt> secondAttempt = executor.submit(
                        () -> createWhenReleased(repository, second, ready, start)
                );
                assertTrue(ready.await(2, TimeUnit.SECONDS));
                start.countDown();

                CreateAttempt firstResult = firstAttempt.get(10, TimeUnit.SECONDS);
                CreateAttempt secondResult = secondAttempt.get(10, TimeUnit.SECONDS);
                long successes = List.of(firstResult, secondResult).stream().filter(CreateAttempt::success).count();
                assertEquals(1L, successes);
                CreateAttempt rejected = firstResult.success() ? secondResult : firstResult;
                assertInstanceOf(ModerationPersistenceException.class, rejected.failure());
                assertEquals(1, repository.activeForTarget(
                        GUILD_ID, userId, DiscordConsequenceType.MUTE, 10
                ).size());
            } finally {
                start.countDown();
                executor.shutdownNow();
            }
        }
    }

    @Test
    void channelRestrictionConflictIsScopedToTheSameDiscordResource() {
        DiscordUserId userId = new DiscordUserId("18446744073709551003");
        try (HikariDataSource dataSource = open()) {
            ModerationSubjectId subjectId = ensureSubject(dataSource, userId);
            JdbcDiscordPunishmentRepository repository = new JdbcDiscordPunishmentRepository(dataSource);
            DiscordPunishment first = punishment(subjectId, userId, restrictionIntent("5001"));
            DiscordPunishment differentChannel = punishment(subjectId, userId, restrictionIntent("5002"));
            DiscordPunishment sameChannel = punishment(subjectId, userId, restrictionIntent("5001"));

            repository.create(first, "d07-create-" + first.punishmentId(), NOW);
            repository.create(differentChannel, "d07-create-" + differentChannel.punishmentId(), NOW);
            assertThrows(ModerationPersistenceException.class, () -> repository.create(
                    sameChannel,
                    "d07-create-" + sameChannel.punishmentId(),
                    NOW
            ));
            assertEquals(2, repository.activeForTarget(
                    GUILD_ID, userId, DiscordConsequenceType.CHANNEL_RESTRICTION, 10
            ).size());
        }
    }

    private static CreateAttempt createWhenReleased(
            JdbcDiscordPunishmentRepository repository,
            DiscordPunishment punishment,
            CountDownLatch ready,
            CountDownLatch start
    ) throws InterruptedException {
        ready.countDown();
        start.await();
        try {
            repository.create(punishment, "d07-create-" + punishment.punishmentId(), NOW);
            return new CreateAttempt(true, null);
        } catch (RuntimeException failure) {
            return new CreateAttempt(false, failure);
        }
    }

    private static ModerationSubjectId ensureSubject(HikariDataSource dataSource, DiscordUserId userId) {
        return new JdbcDiscordModerationPersistenceStore(dataSource)
                .ensureDiscordSubject(userId, NOW)
                .subject()
                .subjectId();
    }

    private static HikariDataSource open() {
        return MariaDb.open(MariaDbIntegrationSupport.databaseConfig(DATABASE));
    }

    private static DiscordPunishment punishment(
            ModerationSubjectId subjectId,
            DiscordUserId userId,
            DiscordPunishmentIntent intent
    ) {
        return DiscordPunishment.pending(
                UUID.randomUUID(),
                subjectId,
                userId,
                GUILD_ID,
                new Actor(UUID.randomUUID(), "IntegrationStaff", StaffRank.ADMIN),
                intent,
                NOW,
                "pending"
        );
    }

    private static DiscordPunishmentIntent muteIntent() {
        return new DiscordPunishmentIntent(
                DiscordConsequenceType.MUTE,
                SanctionLength.temporary(Duration.ofHours(1)),
                false,
                false,
                Optional.empty(),
                "Repeated disruption",
                "Integration test",
                0,
                true
        );
    }

    private static DiscordPunishmentIntent restrictionIntent(String channelId) {
        return new DiscordPunishmentIntent(
                DiscordConsequenceType.CHANNEL_RESTRICTION,
                SanctionLength.temporary(Duration.ofHours(1)),
                false,
                false,
                Optional.of(new DiscordRestrictionTarget(
                        DiscordRestrictionTarget.Kind.CHANNEL,
                        channelId,
                        DiscordRestrictionTarget.Mode.READ_ONLY
                )),
                "Repeated disruption",
                "Integration test",
                0,
                true
        );
    }

    private record CreateAttempt(boolean success, Throwable failure) {
    }
}
