package net.enthusia.staff.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import net.enthusia.staff.domain.moderation.DiscordGuildId;
import net.enthusia.staff.domain.moderation.DiscordGuildScope;
import net.enthusia.staff.domain.moderation.DiscordIdentityRef;
import net.enthusia.staff.domain.moderation.DiscordMinecraftLinkSource;
import net.enthusia.staff.domain.moderation.DiscordUserId;
import net.enthusia.staff.domain.moderation.EnforcementTarget;
import net.enthusia.staff.domain.moderation.MinecraftIdentityRef;
import net.enthusia.staff.domain.moderation.MinecraftNetworkScope;
import net.enthusia.staff.domain.ports.DiscordModerationPersistenceStore.EvidenceMetadata;
import net.enthusia.staff.persistence.JdbcDiscordModerationPersistenceStore;
import net.enthusia.staff.persistence.MariaDb;
import net.enthusia.staff.persistence.ModerationPersistenceException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class DiscordPersistenceSafetyIntegrationTest {
    private static final Instant BASE_TIME = Instant.parse("2026-08-23T19:15:00Z");

    @Container
    private static final MariaDBContainer<?> DATABASE = new MariaDBContainer<>("mariadb:11.8.3")
            .withDatabaseName("enthusia_staff_discord_v19_safety")
            .withUsername("enthusia_test")
            .withPassword("enthusia_test_password");

    @BeforeAll
    static void migrateDatabase() {
        try (HikariDataSource dataSource = MariaDb.open(MariaDbIntegrationSupport.databaseConfig(DATABASE))) {
            MariaDb.migrate(dataSource);
        }
    }

    @Test
    void linkingIntoExistingDiscordSubjectCarriesD02OwnedHistoryAtomically() throws Exception {
        UUID player = UUID.randomUUID();
        DiscordUserId discord = new DiscordUserId("810000000000000001");
        DiscordUserId evidenceAuthor = new DiscordUserId("810000000000000002");
        MariaDbIntegrationSupport.insertPlayer(DATABASE, player, "MergeHistory", BASE_TIME);

        String enforcementOperation = "d02-merge-enforcement-" + player;
        UUID evidenceId = UUID.randomUUID();
        String evidenceOperation = "d02-merge-evidence-" + evidenceId;

        try (HikariDataSource dataSource = MariaDb.open(MariaDbIntegrationSupport.databaseConfig(DATABASE))) {
            JdbcDiscordModerationPersistenceStore store = new JdbcDiscordModerationPersistenceStore(dataSource);
            var minecraftSubject = store.ensureMinecraftSubject(player, BASE_TIME);
            store.recordEnforcementTarget(
                    minecraftSubject.subject().subjectId(),
                    new EnforcementTarget(
                            new MinecraftIdentityRef(player),
                            new MinecraftNetworkScope()
                    ),
                    enforcementOperation,
                    BASE_TIME.plusSeconds(1)
            );
            store.recordEvidence(new EvidenceMetadata(
                    evidenceId,
                    evidenceOperation,
                    minecraftSubject.subject().subjectId(),
                    Optional.empty(),
                    "810000000000000010",
                    "810000000000000011",
                    "810000000000000012",
                    evidenceAuthor,
                    BASE_TIME.plusSeconds(2),
                    BASE_TIME.plus(Duration.ofDays(30)),
                    "{\"kind\":\"merge-safety\",\"contentStored\":false}"
            ));

            var discordSubject = store.ensureDiscordSubject(discord, BASE_TIME.plusSeconds(3));
            assertNotEquals(
                    minecraftSubject.subject().subjectId(),
                    discordSubject.subject().subjectId()
            );

            var linked = store.link(
                    discord,
                    player,
                    DiscordMinecraftLinkSource.DISCORD_CODE,
                    "d02-merge-link-" + player,
                    BASE_TIME.plusSeconds(4)
            );
            assertEquals(discordSubject.subject().subjectId(), linked.subjectId());
            assertEquals(
                    discordSubject.subject().subjectId(),
                    store.subjectForMinecraft(player).orElseThrow().subject().subjectId()
            );
            assertEquals(
                    discordSubject.subject().subjectId(),
                    store.subjectForDiscord(discord).orElseThrow().subject().subjectId()
            );

            assertEquals(
                    discordSubject.subject().subjectId().value(),
                    enforcementSubject(enforcementOperation)
            );
            assertEquals(
                    discordSubject.subject().subjectId().value(),
                    evidenceSubject(evidenceOperation)
            );
            assertFalse(subjectExists(minecraftSubject.subject().subjectId().value()));
        }
    }

    @Test
    void operationKeysFailClosedWhenReusedForDifferentRequests() throws Exception {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        MariaDbIntegrationSupport.insertPlayer(DATABASE, first, "ReplayFirst", BASE_TIME);
        MariaDbIntegrationSupport.insertPlayer(DATABASE, second, "ReplaySecond", BASE_TIME.plusSeconds(1));
        DiscordUserId discord = new DiscordUserId("820000000000000001");
        String linkOperation = "d02-replay-link-" + first;

        try (HikariDataSource dataSource = MariaDb.open(MariaDbIntegrationSupport.databaseConfig(DATABASE))) {
            JdbcDiscordModerationPersistenceStore store = new JdbcDiscordModerationPersistenceStore(dataSource);
            var link = store.link(
                    discord,
                    first,
                    DiscordMinecraftLinkSource.DISCORD_CODE,
                    linkOperation,
                    BASE_TIME.plusSeconds(10)
            );
            assertThrows(
                    ModerationPersistenceException.class,
                    () -> store.link(
                            discord,
                            second,
                            DiscordMinecraftLinkSource.DISCORD_CODE,
                            linkOperation,
                            BASE_TIME.plusSeconds(11)
                    )
            );
            assertFalse(store.subjectForMinecraft(second).isPresent());

            var subject = store.subjectForDiscord(discord).orElseThrow();
            String enforcementOperation = "d02-replay-enforcement-" + first;
            EnforcementTarget firstTarget = new EnforcementTarget(
                    new DiscordIdentityRef(discord),
                    new DiscordGuildScope(new DiscordGuildId("820000000000000010"))
            );
            EnforcementTarget differentTarget = new EnforcementTarget(
                    new DiscordIdentityRef(discord),
                    new DiscordGuildScope(new DiscordGuildId("820000000000000011"))
            );
            store.recordEnforcementTarget(
                    subject.subject().subjectId(),
                    firstTarget,
                    enforcementOperation,
                    BASE_TIME.plusSeconds(12)
            );
            assertThrows(
                    ModerationPersistenceException.class,
                    () -> store.recordEnforcementTarget(
                            subject.subject().subjectId(),
                            differentTarget,
                            enforcementOperation,
                            BASE_TIME.plusSeconds(13)
                    )
            );

            UUID evidenceId = UUID.randomUUID();
            String evidenceOperation = "d02-replay-evidence-" + evidenceId;
            EvidenceMetadata evidence = evidence(
                    evidenceId,
                    evidenceOperation,
                    subject.subject().subjectId(),
                    discord,
                    "820000000000000020"
            );
            store.recordEvidence(evidence);
            assertThrows(
                    ModerationPersistenceException.class,
                    () -> store.recordEvidence(evidence(
                            UUID.randomUUID(),
                            evidenceOperation,
                            subject.subject().subjectId(),
                            discord,
                            "820000000000000020"
                    ))
            );

            String lockOperation = "d02-replay-lock-" + first;
            var lock = store.activateSecurityLock(
                    subject.subject().subjectId(),
                    discord,
                    "ACCOUNT_SECURITY",
                    lockOperation,
                    BASE_TIME.plusSeconds(14)
            );
            assertThrows(
                    ModerationPersistenceException.class,
                    () -> store.activateSecurityLock(
                            subject.subject().subjectId(),
                            discord,
                            "DIFFERENT_REASON",
                            lockOperation,
                            BASE_TIME.plusSeconds(15)
                    )
            );

            String releaseOperation = "d02-replay-release-" + first;
            var released = store.releaseSecurityLock(
                    lock.lockId(),
                    lock.revision(),
                    releaseOperation,
                    BASE_TIME.plusSeconds(16)
            );
            assertThrows(
                    ModerationPersistenceException.class,
                    () -> store.releaseSecurityLock(
                            lock.lockId(),
                            released.revision(),
                            releaseOperation,
                            BASE_TIME.plusSeconds(17)
                    )
            );

            String unlinkOperation = "d02-replay-unlink-" + first;
            store.unlink(
                    discord,
                    first,
                    link.revision(),
                    unlinkOperation,
                    BASE_TIME.plusSeconds(18)
            );
            assertThrows(
                    ModerationPersistenceException.class,
                    () -> store.unlink(
                            discord,
                            second,
                            link.revision(),
                            unlinkOperation,
                            BASE_TIME.plusSeconds(19)
                    )
            );
        }
    }

    private static EvidenceMetadata evidence(
            UUID evidenceId,
            String operationKey,
            net.enthusia.staff.domain.moderation.ModerationSubjectId subjectId,
            DiscordUserId author,
            String messageId
    ) {
        return new EvidenceMetadata(
                evidenceId,
                operationKey,
                subjectId,
                Optional.empty(),
                "820000000000000021",
                "820000000000000022",
                messageId,
                author,
                BASE_TIME.plusSeconds(20),
                BASE_TIME.plus(Duration.ofDays(30)),
                "{\"kind\":\"replay-safety\",\"contentStored\":false}"
        );
    }

    private static UUID enforcementSubject(String operationKey) throws Exception {
        try (Connection connection = MariaDbIntegrationSupport.connection(DATABASE);
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT subject_id
                     FROM moderation_enforcement_targets
                     WHERE operation_key = ?
                     """)) {
            statement.setString(1, operationKey);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    throw new AssertionError("missing enforcement target");
                }
                return uuid(result.getBytes("subject_id"));
            }
        }
    }

    private static UUID evidenceSubject(String operationKey) throws Exception {
        try (Connection connection = MariaDbIntegrationSupport.connection(DATABASE);
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT subject_id
                     FROM discord_evidence_metadata
                     WHERE operation_key = ?
                     """)) {
            statement.setString(1, operationKey);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    throw new AssertionError("missing Discord evidence");
                }
                return uuid(result.getBytes("subject_id"));
            }
        }
    }

    private static boolean subjectExists(UUID subjectId) throws Exception {
        try (Connection connection = MariaDbIntegrationSupport.connection(DATABASE);
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT 1 FROM moderation_subjects WHERE subject_id = ?")) {
            statement.setBytes(1, MariaDbIntegrationSupport.uuidBytes(subjectId));
            try (ResultSet result = statement.executeQuery()) {
                return result.next();
            }
        }
    }

    private static UUID uuid(byte[] bytes) {
        java.nio.ByteBuffer buffer = java.nio.ByteBuffer.wrap(bytes);
        return new UUID(buffer.getLong(), buffer.getLong());
    }
}
