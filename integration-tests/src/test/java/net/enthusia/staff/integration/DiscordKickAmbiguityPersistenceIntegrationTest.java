package net.enthusia.staff.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zaxxer.hikari.HikariDataSource;
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
import net.enthusia.staff.domain.moderation.ModerationSubjectId;
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
class DiscordKickAmbiguityPersistenceIntegrationTest {
    private static final Instant NOW = Instant.parse("2026-09-21T18:30:00Z");
    private static final DiscordGuildId GUILD_ID = new DiscordGuildId("1410303324745371709");
    private static final String AMBIGUOUS = "KICK_RESULT_AMBIGUOUS";

    @Container
    private static final MariaDBContainer<?> DATABASE = new MariaDBContainer<>("mariadb:11.4.8")
            .withDatabaseName("enthusia_staff_r09_001")
            .withUsername("enthusia")
            .withPassword("enthusia-test-password");

    @BeforeAll
    static void migrate() {
        try (HikariDataSource dataSource = open()) {
            MariaDb.migrate(dataSource);
        }
    }

    @Test
    void ambiguousKickStateSurvivesCrashLeaseRecoveryAndProcessRestart() {
        DiscordUserId userId = new DiscordUserId("18446744073709551101");
        UUID punishmentId = createKick(userId);
        var recovered = recoverExpiredFirstLease(punishmentId);
        settleAmbiguity(punishmentId, recovered);
        verifyRestartedAmbiguity(punishmentId);
    }

    @Test
    void punishmentCreationRejectsTargetOutsideDurableSubjectMembership() {
        DiscordUserId linked = new DiscordUserId("18446744073709551102");
        DiscordUserId unlinked = new DiscordUserId("18446744073709551103");
        try (HikariDataSource dataSource = open()) {
            ModerationSubjectId subjectId = ensureSubject(dataSource, linked);
            JdbcDiscordPunishmentRepository repository = new JdbcDiscordPunishmentRepository(dataSource);
            DiscordPunishment punishment = kick(subjectId, unlinked);

            assertThrows(ModerationPersistenceException.class, () -> repository.create(
                    punishment, "r09-001-unlinked-" + punishment.punishmentId(), NOW
            ));
        }
    }

    private static UUID createKick(DiscordUserId userId) {
        try (HikariDataSource dataSource = open()) {
            ModerationSubjectId subjectId = ensureSubject(dataSource, userId);
            DiscordPunishment punishment = kick(subjectId, userId);
            JdbcDiscordPunishmentRepository repository = new JdbcDiscordPunishmentRepository(dataSource);
            String operation = "r09-001-create-" + punishment.punishmentId();

            assertFalse(repository.create(punishment, operation, NOW).replayed());
            assertTrue(repository.create(punishment, operation, NOW).replayed());
            var lease = repository.claimDue(NOW, 1, "worker-a", NOW.plusSeconds(30));
            assertEquals(1, lease.size());
            assertEquals(1, lease.getFirst().attemptCount());
            return punishment.punishmentId();
        }
    }

    private static net.enthusia.staff.domain.ports.DiscordPunishmentRepository.WorkLease recoverExpiredFirstLease(
            UUID punishmentId
    ) {
        try (HikariDataSource dataSource = open()) {
            JdbcDiscordPunishmentRepository repository = new JdbcDiscordPunishmentRepository(dataSource);
            var persisted = repository.find(punishmentId).orElseThrow();
            assertEquals(DiscordPunishmentState.PENDING_APPLY, persisted.punishment().state());
            assertFalse(persisted.punishment().externalApplied());

            Instant recoveredAt = NOW.plusSeconds(31);
            var recovered = repository.claimDue(recoveredAt, 1, "worker-b", recoveredAt.plusSeconds(30));
            assertEquals(1, recovered.size());
            assertEquals(2, recovered.getFirst().attemptCount());
            return recovered.getFirst();
        }
    }

    private static void settleAmbiguity(
            UUID punishmentId,
            net.enthusia.staff.domain.ports.DiscordPunishmentRepository.WorkLease recovered
    ) {
        try (HikariDataSource dataSource = open()) {
            JdbcDiscordPunishmentRepository repository = new JdbcDiscordPunishmentRepository(dataSource);
            var persisted = repository.find(punishmentId).orElseThrow();
            DiscordPunishment ambiguous = persisted.punishment().withProcessingResult(
                    DiscordPunishmentState.RETRY_APPLY,
                    DiscordDeliveryOutcome.NOT_ATTEMPTED,
                    false,
                    Optional.empty(),
                    Optional.of(AMBIGUOUS),
                    "r09-001-ambiguous"
            );
            Instant retryAt = NOW.plusSeconds(40);
            repository.settle(
                    recovered,
                    persisted,
                    ambiguous,
                    List.of(new WorkSchedule(WorkType.APPLY, retryAt)),
                    NOW.plusSeconds(32)
            );
            assertThrows(ModerationPersistenceException.class, () -> repository.settle(
                    recovered, persisted, ambiguous, List.of(), NOW.plusSeconds(33)
            ));
        }
    }

    private static void verifyRestartedAmbiguity(UUID punishmentId) {
        try (HikariDataSource dataSource = open()) {
            JdbcDiscordPunishmentRepository repository = new JdbcDiscordPunishmentRepository(dataSource);
            var restarted = repository.find(punishmentId).orElseThrow();
            assertEquals(DiscordPunishmentState.RETRY_APPLY, restarted.punishment().state());
            assertEquals(Optional.of(AMBIGUOUS), restarted.punishment().lastErrorCode());
            assertFalse(restarted.punishment().externalApplied());
            assertEquals(DiscordDeliveryOutcome.NOT_ATTEMPTED, restarted.punishment().dmOutcome());

            var retry = repository.claimDue(NOW.plusSeconds(41), 1, "worker-c", NOW.plusSeconds(71));
            assertEquals(1, retry.size());
            assertEquals(WorkType.APPLY, retry.getFirst().type());
            assertEquals(3, retry.getFirst().attemptCount());
        }
    }

    private static ModerationSubjectId ensureSubject(HikariDataSource dataSource, DiscordUserId userId) {
        return new JdbcDiscordModerationPersistenceStore(dataSource)
                .ensureDiscordSubject(userId, NOW)
                .subject()
                .subjectId();
    }

    private static DiscordPunishment kick(ModerationSubjectId subjectId, DiscordUserId userId) {
        return DiscordPunishment.pending(
                UUID.randomUUID(),
                subjectId,
                userId,
                GUILD_ID,
                new Actor(UUID.randomUUID(), "IntegrationStaff", StaffRank.ADMIN),
                new DiscordPunishmentIntent(
                        DiscordConsequenceType.KICK,
                        SanctionLength.instant(),
                        false,
                        false,
                        Optional.empty(),
                        "Repeated disruption",
                        "Integration test",
                        0,
                        true
                ),
                NOW,
                "r09-001-pending"
        );
    }

    private static HikariDataSource open() {
        return MariaDb.open(MariaDbIntegrationSupport.databaseConfig(DATABASE));
    }
}
