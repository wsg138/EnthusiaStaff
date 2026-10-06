package net.enthusia.staff.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.enthusia.staff.common.CaseId;
import net.enthusia.staff.common.IdempotencyKey;
import net.enthusia.staff.domain.OperationalMode;
import net.enthusia.staff.domain.application.CrossPlatformPunishmentPlan;
import net.enthusia.staff.domain.application.PunishmentPlan;
import net.enthusia.staff.domain.auth.Actor;
import net.enthusia.staff.domain.auth.DiscordConsequenceType;
import net.enthusia.staff.domain.auth.StaffRank;
import net.enthusia.staff.domain.casefile.CaseVisibility;
import net.enthusia.staff.domain.discord.DiscordPunishment;
import net.enthusia.staff.domain.discord.DiscordPunishmentIntent;
import net.enthusia.staff.domain.escalation.EscalationDecision;
import net.enthusia.staff.domain.escalation.PunishmentStep;
import net.enthusia.staff.domain.moderation.DiscordGuildId;
import net.enthusia.staff.domain.moderation.DiscordMinecraftLinkSource;
import net.enthusia.staff.domain.moderation.DiscordUserId;
import net.enthusia.staff.domain.moderation.ModerationSubjectId;
import net.enthusia.staff.domain.player.PlayerPlatform;
import net.enthusia.staff.domain.sanction.SanctionLength;
import net.enthusia.staff.domain.sanction.SanctionSpec;
import net.enthusia.staff.domain.sanction.SanctionType;
import net.enthusia.staff.persistence.DiscordPunishmentPersistenceRuntime;
import net.enthusia.staff.persistence.MariaDb;
import net.enthusia.staff.persistence.MariaDbRuntime;
import net.enthusia.staff.persistence.ModerationPersistenceException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class CrossPlatformPunishmentAtomicityIntegrationTest {
    private static final Instant NOW = Instant.parse("2026-09-19T22:00:00Z");
    private static final DiscordGuildId GUILD = new DiscordGuildId("1497476349244211311");
    private static final Actor ACTOR = new Actor(
            UUID.fromString("50000000-0000-0000-0000-000000000008"), "D08Admin", StaffRank.ADMIN);
    private static final SanctionLength ONE_HOUR = SanctionLength.temporary(Duration.ofHours(1));

    @Container
    private static final MariaDBContainer<?> DATABASE = new MariaDBContainer<>("mariadb:11.4.8")
            .withDatabaseName("enthusia_staff_d08")
            .withUsername("enthusia")
            .withPassword("enthusia-test-password");

    @BeforeAll
    static void migrate() {
        try (MariaDbRuntime runtime = MariaDb.initialize(MariaDbIntegrationSupport.databaseConfig(DATABASE))) {
            // Migration plus BOOTSTRAP operational state is sufficient for isolated persistence verification.
            runtime.operationalStateStore().current();
        }
    }

    @Test
    void bothIntentsCommitAndReplayTogether() {
        Fixture fixture = fixture("08", "18446744073709551008");
        try (MariaDbRuntime runtime = MariaDb.initialize(MariaDbIntegrationSupport.databaseConfig(DATABASE));
             DiscordPunishmentPersistenceRuntime d08 = openD08()) {
            link(runtime, fixture);
            CrossPlatformPunishmentPlan plan = plan(fixture, fixture.discordUser());

            var first = d08.crossPlatformPunishments().create(plan);
            var replay = d08.crossPlatformPunishments().create(plan);

            assertFalse(first.replayed());
            assertTrue(replay.replayed());
            assertEquals(fixture.caseId(), first.caseId());
            assertEquals(fixture.discordPunishmentId(), first.discordPunishmentId());
            assertEquals(1L, rowCount(runtime, "cases", "case_id", fixture.caseId().value()));
            assertEquals(1L, binaryRowCount(runtime, "moderation_enforcement_targets", "target_id", fixture.discordPunishmentId()));
        }
    }

    @Test
    void discordMembershipFailureRollsBackMinecraftCase() {
        Fixture fixture = fixture("09", "18446744073709551009");
        DiscordUserId unlinked = new DiscordUserId("18446744073709551019");
        try (MariaDbRuntime runtime = MariaDb.initialize(MariaDbIntegrationSupport.databaseConfig(DATABASE));
             DiscordPunishmentPersistenceRuntime d08 = openD08()) {
            link(runtime, fixture);

            assertThrows(ModerationPersistenceException.class, () ->
                    d08.crossPlatformPunishments().create(plan(fixture, unlinked)));

            assertEquals(0L, rowCount(runtime, "cases", "case_id", fixture.caseId().value()));
            assertEquals(0L, binaryRowCount(runtime, "moderation_enforcement_targets", "target_id", fixture.discordPunishmentId()));
        }
    }

    @Test
    void conflictingReplayDoesNotCreateSecondDiscordIntent() {
        Fixture fixture = fixture("0A", "18446744073709551010");
        try (MariaDbRuntime runtime = MariaDb.initialize(MariaDbIntegrationSupport.databaseConfig(DATABASE));
             DiscordPunishmentPersistenceRuntime d08 = openD08()) {
            link(runtime, fixture);
            d08.crossPlatformPunishments().create(plan(fixture, fixture.discordUser()));
            Fixture conflicting = new Fixture(
                    fixture.playerId(), fixture.discordUser(), fixture.subjectId(), new CaseId("AAAAAAAAAAAAAAA8"),
                    UUID.fromString("60000000-0000-0000-0000-000000000018"), fixture.idempotencyKey(), fixture.operationKey());

            assertThrows(ModerationPersistenceException.class, () ->
                    d08.crossPlatformPunishments().create(plan(conflicting, conflicting.discordUser())));

            assertEquals(1L, rowCount(runtime, "cases", "idempotency_key", fixture.idempotencyKey().value()));
            assertEquals(1L, operationRowCount(runtime, fixture.operationKey()));
        }
    }

    @Test
    void shadowModeFenceBlocksBothBeforeAnyIntentCommits() {
        Fixture fixture = fixture("0B", "18446744073709551011");
        try (MariaDbRuntime runtime = MariaDb.initialize(MariaDbIntegrationSupport.databaseConfig(DATABASE));
             DiscordPunishmentPersistenceRuntime d08 = openD08()) {
            link(runtime, fixture);
            setMode(runtime, OperationalMode.SHADOW_MIGRATION);
            try {
                assertThrows(ModerationPersistenceException.class, () ->
                        d08.crossPlatformPunishments().create(plan(fixture, fixture.discordUser())));
                assertEquals(0L, rowCount(runtime, "cases", "case_id", fixture.caseId().value()));
            } finally {
                resetBootstrap(runtime);
            }
        }
    }

    private static DiscordPunishmentPersistenceRuntime openD08() {
        return DiscordPunishmentPersistenceRuntime.open(MariaDbIntegrationSupport.databaseConfig(DATABASE));
    }

    private static void link(MariaDbRuntime runtime, Fixture fixture) {
        runtime.playerDirectory().recordSeenVerified(
                fixture.playerId(), "D08Target" + fixture.caseId().value().substring(14),
                PlayerPlatform.JAVA, "d08-test", NOW);
        ModerationSubjectId minecraftSubject = runtime.discordModerationPersistenceStore()
                .ensureMinecraftSubject(fixture.playerId(), NOW).subject().subjectId();
        runtime.discordModerationPersistenceStore().link(
                fixture.discordUser(), fixture.playerId(), DiscordMinecraftLinkSource.STAFF_RECOVERY,
                "d08-link-" + fixture.operationKey(), NOW);
        ModerationSubjectId linkedSubject = runtime.discordModerationPersistenceStore()
                .subjectForMinecraft(fixture.playerId()).orElseThrow().subject().subjectId();
        assertEquals(linkedSubject, runtime.discordModerationPersistenceStore()
                .subjectForDiscord(fixture.discordUser()).orElseThrow().subject().subjectId());
        assertEquals(linkedSubject, fixture.subjectId());
        assertTrue(minecraftSubject != null);
    }

    private static CrossPlatformPunishmentPlan plan(Fixture fixture, DiscordUserId discordUser) {
        SanctionSpec sanction = new SanctionSpec(SanctionType.MUTE, ONE_HOUR);
        PunishmentStep step = new PunishmentStep(0, "One hour mute", List.of(sanction));
        PunishmentPlan minecraft = new PunishmentPlan(
                fixture.caseId(), fixture.idempotencyKey(), fixture.playerId(), ACTOR,
                "chat.toxicity", "chat", "Chat toxicity", "D08 atomicity integration",
                "d08-test-v1", CaseVisibility.PUBLIC, NOW,
                new EscalationDecision(0, 0, 0, List.of(), step), List.of(sanction)
        );
        DiscordPunishmentIntent intent = new DiscordPunishmentIntent(
                DiscordConsequenceType.MUTE, ONE_HOUR, false, false, Optional.empty(),
                "Chat toxicity", "D08 atomicity integration", 0, true
        );
        DiscordPunishment discord = DiscordPunishment.pending(
                fixture.discordPunishmentId(), fixture.subjectId(), Optional.of(fixture.caseId()),
                discordUser, GUILD, ACTOR, intent, NOW, fixture.operationKey()
        );
        return new CrossPlatformPunishmentPlan(minecraft, discord, fixture.operationKey());
    }

    private static Fixture fixture(String suffix, String discordUser) {
        UUID player = UUID.fromString("70000000-0000-0000-0000-0000000000" + suffix);
        ModerationSubjectId subject = new ModerationSubjectId(player);
        return new Fixture(
                player,
                new DiscordUserId(discordUser),
                subject,
                new CaseId("0123456789ABCD" + suffix),
                UUID.fromString("80000000-0000-0000-0000-0000000000" + suffix),
                new IdempotencyKey("d08:case:" + suffix),
                "d08:discord:" + suffix
        );
    }

    private static long rowCount(MariaDbRuntime runtime, String table, String column, String value) {
        return queryCount(runtime, "SELECT COUNT(*) FROM " + table + " WHERE " + column + " = ?", statement ->
                statement.setString(1, value));
    }

    private static long binaryRowCount(MariaDbRuntime runtime, String table, String column, UUID value) {
        return queryCount(runtime, "SELECT COUNT(*) FROM " + table + " WHERE " + column + " = ?", statement ->
                statement.setBytes(1, uuidBytes(value)));
    }

    private static long operationRowCount(MariaDbRuntime runtime, String operationKey) {
        return queryCount(runtime,
                "SELECT COUNT(*) FROM moderation_enforcement_targets WHERE operation_key = ?",
                statement -> statement.setString(1, operationKey));
    }

    private static long queryCount(MariaDbRuntime runtime, String sql, SqlBinder binder) {
        try (var connection = testConnection(runtime);
             var statement = connection.prepareStatement(sql)) {
            binder.bind(statement);
            try (var rows = statement.executeQuery()) {
                rows.next();
                return rows.getLong(1);
            }
        } catch (SQLException exception) {
            throw new AssertionError(exception);
        }
    }

    private static java.sql.Connection testConnection(MariaDbRuntime ignored) throws SQLException {
        return java.sql.DriverManager.getConnection(DATABASE.getJdbcUrl(), DATABASE.getUsername(), DATABASE.getPassword());
    }

    private static void setMode(MariaDbRuntime runtime, OperationalMode mode) {
        var current = runtime.operationalStateStore().current();
        assertTrue(runtime.operationalStateStore().transition(
                current.revision(), mode, ACTOR.id(), "D08 fence integration test", NOW));
    }

    private static void resetBootstrap(MariaDbRuntime ignored) {
        try (var connection = testConnection(ignored);
             var statement = connection.prepareStatement(
                     "UPDATE operational_state SET mode = 'BOOTSTRAP', revision = revision + 1 WHERE singleton_id = 1")) {
            assertEquals(1, statement.executeUpdate());
        } catch (SQLException exception) {
            throw new AssertionError(exception);
        }
    }

    private static byte[] uuidBytes(UUID value) {
        java.nio.ByteBuffer buffer = java.nio.ByteBuffer.allocate(16);
        buffer.putLong(value.getMostSignificantBits());
        buffer.putLong(value.getLeastSignificantBits());
        return buffer.array();
    }

    @FunctionalInterface
    private interface SqlBinder {
        void bind(java.sql.PreparedStatement statement) throws SQLException;
    }

    private record Fixture(
            UUID playerId,
            DiscordUserId discordUser,
            ModerationSubjectId subjectId,
            CaseId caseId,
            UUID discordPunishmentId,
            IdempotencyKey idempotencyKey,
            String operationKey
    ) {
    }
}
