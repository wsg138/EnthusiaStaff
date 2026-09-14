package net.enthusia.staff.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zaxxer.hikari.HikariDataSource;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.enthusia.staff.domain.auth.Actor;
import net.enthusia.staff.domain.auth.DiscordConsequenceType;
import net.enthusia.staff.domain.auth.StaffRank;
import net.enthusia.staff.domain.discord.DiscordDeliveryOutcome;
import net.enthusia.staff.domain.discord.DiscordPunishment;
import net.enthusia.staff.domain.discord.DiscordPunishmentIntent;
import net.enthusia.staff.domain.discord.DiscordPunishmentState;
import net.enthusia.staff.domain.moderation.DiscordGuildId;
import net.enthusia.staff.domain.moderation.DiscordUserId;
import net.enthusia.staff.domain.ports.DiscordPunishmentRepository.WorkSchedule;
import net.enthusia.staff.domain.ports.DiscordPunishmentRepository.WorkType;
import net.enthusia.staff.domain.sanction.SanctionLength;
import net.enthusia.staff.persistence.JdbcDiscordModerationPersistenceStore;
import net.enthusia.staff.persistence.JdbcDiscordPunishmentRepository;
import net.enthusia.staff.persistence.MariaDb;
import net.enthusia.staff.persistence.ModerationPersistenceException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class DiscordPunishmentPersistenceIntegrationTest {
    private static final Instant NOW = Instant.parse("2026-09-14T20:00:00Z");

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

    @Test
    void intentLeaseRecoveryRevisionConflictAndRestartAreDurable() {
        DiscordUserId userId = new DiscordUserId("18446744073709551001");
        DiscordGuildId guildId = new DiscordGuildId("1410303324745371709");
        DiscordPunishment punishment;
        String operationKey;

        try (HikariDataSource dataSource = open()) {
            JdbcDiscordModerationPersistenceStore identities = new JdbcDiscordModerationPersistenceStore(dataSource);
            var subject = identities.ensureDiscordSubject(userId, NOW);
            punishment = punishment(subject.subject().subjectId(), userId, guildId);
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
            assertEquals(1, repository.activeForTarget(guildId, userId, DiscordConsequenceType.MUTE, 10).size());

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

    private static HikariDataSource open() {
        return MariaDb.open(MariaDbIntegrationSupport.databaseConfig(DATABASE));
    }

    private static DiscordPunishment punishment(
            net.enthusia.staff.domain.moderation.ModerationSubjectId subjectId,
            DiscordUserId userId,
            DiscordGuildId guildId
    ) {
        DiscordPunishmentIntent intent = new DiscordPunishmentIntent(
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
        return DiscordPunishment.pending(
                UUID.randomUUID(),
                subjectId,
                userId,
                guildId,
                new Actor(UUID.randomUUID(), "IntegrationStaff", StaffRank.ADMIN),
                intent,
                NOW,
                "pending"
        );
    }
}
