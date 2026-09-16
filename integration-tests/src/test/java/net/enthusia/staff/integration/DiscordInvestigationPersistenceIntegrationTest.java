package net.enthusia.staff.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zaxxer.hikari.HikariDataSource;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.ZoneOffset;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.enthusia.staff.common.CaseId;
import net.enthusia.staff.domain.auth.Actor;
import net.enthusia.staff.domain.auth.DiscordConsequenceType;
import net.enthusia.staff.domain.auth.StaffRank;
import net.enthusia.staff.domain.discord.DiscordPunishment;
import net.enthusia.staff.domain.discord.DiscordPunishmentIntent;
import net.enthusia.staff.domain.discord.DiscordPunishmentState;
import net.enthusia.staff.domain.investigation.EvasionAlert;
import net.enthusia.staff.domain.investigation.InvestigationEvidence;
import net.enthusia.staff.domain.investigation.InvestigationNote;
import net.enthusia.staff.domain.casefile.CaseReview;
import net.enthusia.staff.domain.moderation.DiscordGuildId;
import net.enthusia.staff.domain.moderation.DiscordUserId;
import net.enthusia.staff.domain.moderation.ModerationSubjectId;
import net.enthusia.staff.domain.ports.DiscordInvestigationStore;
import net.enthusia.staff.domain.sanction.SanctionLength;
import net.enthusia.staff.persistence.JdbcDiscordInvestigationStore;
import net.enthusia.staff.persistence.JdbcDiscordModerationPersistenceStore;
import net.enthusia.staff.persistence.JdbcDiscordPunishmentRepository;
import net.enthusia.staff.persistence.DiscordStaffReadRuntime;
import net.enthusia.staff.persistence.MariaDb;
import net.enthusia.staff.persistence.ModerationPersistenceException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class DiscordInvestigationPersistenceIntegrationTest {
    private static final Instant NOW = Instant.parse("2026-09-15T12:00:00Z");
    private static final UUID ACTOR = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final DiscordUserId TARGET = new DiscordUserId("223456789012345678");
    private static final DiscordGuildId GUILD = new DiscordGuildId("1410303324745371709");

    @Container
    private static final MariaDBContainer<?> DATABASE = new MariaDBContainer<>("mariadb:11.4.8")
            .withDatabaseName("enthusia_staff_d09")
            .withUsername("enthusia")
            .withPassword(UUID.randomUUID().toString());

    @BeforeAll
    static void migrateAndSeedActor() throws SQLException {
        try (HikariDataSource dataSource = open()) {
            MariaDb.migrate(dataSource);
        }
        MariaDbIntegrationSupport.insertPlayer(DATABASE, ACTOR, "D09Staff", NOW.minusSeconds(60));
    }

    @Test
    void investigationCasesAndVersionedNotesReplayAndRejectStaleEdits() {
        try (HikariDataSource dataSource = open()) {
            ModerationSubjectId subjectId = ensureSubject(dataSource, TARGET);
            JdbcDiscordInvestigationStore store = new JdbcDiscordInvestigationStore(dataSource);
            Actor actor = new Actor(ACTOR, "D09Staff", StaffRank.ADMIN);
            DiscordInvestigationStore.InvestigationCaseDraft caseDraft = new DiscordInvestigationStore.InvestigationCaseDraft(
                    "d09:test:case:1", subjectId, "Investigation only", actor, NOW
            );

            var createdCase = store.createInvestigationCase(caseDraft);
            assertFalse(createdCase.replayed());
            assertTrue(store.createInvestigationCase(caseDraft).replayed());
            CaseId caseId = createdCase.caseId();

            UUID noteId = UUID.fromString("30000000-0000-0000-0000-000000000001");
            InvestigationNote.Scope scope = new InvestigationNote.Scope(
                    InvestigationNote.ScopeType.CASE, caseId.value());
            var created = store.createNote(new DiscordInvestigationStore.NoteDraft(
                    noteId, "d09:test:note:1", subjectId, scope,
                    InvestigationNote.Visibility.STAFF, "first private version", ACTOR, NOW.plusSeconds(1)
            ));
            assertEquals(0L, created.revision());

            DiscordInvestigationStore.NoteEdit edit = new DiscordInvestigationStore.NoteEdit(
                    noteId, "d09:test:note-edit:1", subjectId, 0,
                    "second private version", ACTOR, NOW.plusSeconds(2)
            );
            var updated = store.editNote(edit);
            assertEquals(1L, updated.revision());
            assertTrue(store.editNote(edit).replayed());
            assertThrows(ModerationPersistenceException.class, () -> store.editNote(
                    new DiscordInvestigationStore.NoteEdit(
                            noteId, "d09:test:note-edit:stale", subjectId, 0,
                            "stale version", ACTOR, NOW.plusSeconds(3)
                    )
            ));
            assertEquals(List.of(1L, 0L), store.noteHistory(noteId, 10).stream()
                    .map(InvestigationNote.Version::revision).toList());
            try (DiscordStaffReadRuntime reads = DiscordStaffReadRuntime.open(
                    MariaDbIntegrationSupport.databaseConfig(DATABASE), Clock.fixed(NOW, ZoneOffset.UTC))) {
                assertEquals(List.of("second private version"), reads.recentInvestigationNotes(subjectId, 10).stream()
                        .map(InvestigationNote::text).toList());
                CaseReview caseReview = reads.caseReview(caseId).orElseThrow();
                assertTrue(caseReview.minecraftTargetId().isEmpty());
                assertEquals(Optional.of(subjectId), caseReview.subjectId());
                assertEquals(caseId, reads.recentCases(subjectId, 10).getFirst().caseId());
            }
        }
    }

    @Test
    void boundedPunishmentObservationQueryAdvancesAfterReconciliation() {
        try (HikariDataSource dataSource = open()) {
            JdbcDiscordPunishmentRepository punishments = new JdbcDiscordPunishmentRepository(dataSource);
            JdbcDiscordInvestigationStore investigations = new JdbcDiscordInvestigationStore(dataSource);
            for (int index = 0; index < 101; index++) {
                persistBatchWarning(dataSource, punishments, index);
            }

            List<DiscordInvestigationStore.PunishmentObservation> first = investigations.punishmentObservations(100);
            assertEquals(100, first.size());
            first.forEach(observation -> investigations.ensurePunishmentCase(punishmentCase(observation)));

            List<DiscordInvestigationStore.PunishmentObservation> second = investigations.punishmentObservations(100);
            assertEquals(1, second.size());
        }
    }

    @Test
    void evidenceRetainsUntilThirtyDaysAfterCaseClosureThenPurgesPrivatePayload() throws SQLException {
        try (HikariDataSource dataSource = open()) {
            ModerationSubjectId subjectId = ensureSubject(dataSource, new DiscordUserId("223456789012345679"));
            JdbcDiscordInvestigationStore store = new JdbcDiscordInvestigationStore(dataSource);
            CaseId caseId = store.createInvestigationCase(new DiscordInvestigationStore.InvestigationCaseDraft(
                    "d09:test:case:2", subjectId, "Evidence retention",
                    new Actor(ACTOR, "D09Staff", StaffRank.ADMIN), NOW
            )).caseId();

            UUID evidenceId = UUID.fromString("40000000-0000-0000-0000-000000000001");
            InvestigationEvidence.Message focus = message("1541286004298752191", "223456789012345679", "private body");
            store.captureEvidence(new InvestigationEvidence.Capture(
                    evidenceId, "d09:test:evidence:1", subjectId, caseId, focus,
                    List.of(), List.of(), ACTOR, "MESSAGE_CONTEXT", NOW.plusSeconds(1)
            ));
            assertEvidenceMetadata(dataSource, evidenceId, caseId, subjectId);

            Instant closedAt = NOW.plus(Duration.ofDays(31));
            assertTrue(store.closeInactiveCases(NOW.plusSeconds(2), closedAt, 10) >= 1);
            assertEquals(0, store.purgeEligibleEvidence(closedAt.plus(Duration.ofDays(29)), 10));
            assertEquals(1, store.purgeEligibleEvidence(closedAt.plus(Duration.ofDays(31)), 10));
            assertTrue(store.findEvidenceByMessage(
                    focus.guildId(), focus.channelId(), focus.messageId()).isEmpty());
            assertPurgedMetadata(dataSource, evidenceId);
        }
    }

    @Test
    void evasionAlertDeliveryRetriesIndependentlyAndResolutionIsRevisionChecked() throws SQLException {
        UUID trigger = UUID.fromString("50000000-0000-0000-0000-000000000001");
        MariaDbIntegrationSupport.insertPlayer(DATABASE, trigger, "LinkedAlt", NOW.minusSeconds(30));
        try (HikariDataSource dataSource = open()) {
            ModerationSubjectId subjectId = ensureSubject(dataSource, new DiscordUserId("223456789012345680"));
            JdbcDiscordInvestigationStore store = new JdbcDiscordInvestigationStore(dataSource);
            UUID alertId = UUID.fromString("60000000-0000-0000-0000-000000000001");
            EvasionAlert.Context context = new EvasionAlert.Context(
                    subjectId, UUID.randomUUID(), new DiscordUserId("223456789012345680"),
                    DiscordConsequenceType.BAN, "Active ban", DiscordPunishmentState.APPLIED, Optional.empty(),
                    trigger, Optional.of("LinkedAlt"), "survival", 7,
                    EvasionAlert.TriggerType.LINKED_MINECRAFT_ONLINE, NOW.minusSeconds(30)
            );
            EvasionAlert created = store.createEvasionAlert(new DiscordInvestigationStore.EvasionAlertDraft(
                    alertId, "d09:test:alert:1", context, NOW
            ));
            assertEquals(context, created.context());
            assertEquals(context, store.findEvasionAlert(alertId).orElseThrow().context());
            assertEquals(1, store.pendingEvasionAlerts(NOW, 10).size());

            Instant retryAt = NOW.plusSeconds(30);
            EvasionAlert discordRetry = store.updateEvasionDelivery(new DiscordInvestigationStore.EvasionDeliveryUpdate(
                    alertId, DiscordInvestigationStore.EvasionDeliveryChannel.DISCORD, false,
                    Optional.of("TEMPORARY"), Optional.of(retryAt), created.revision(), NOW.plusSeconds(1)
            ));
            EvasionAlert minecraftDelivered = store.updateEvasionDelivery(new DiscordInvestigationStore.EvasionDeliveryUpdate(
                    alertId, DiscordInvestigationStore.EvasionDeliveryChannel.MINECRAFT, true,
                    Optional.empty(), Optional.empty(), discordRetry.revision(), NOW.plusSeconds(2)
            ));
            assertTrue(store.pendingEvasionAlerts(NOW.plusSeconds(10), 10).isEmpty());
            assertEquals(1, store.pendingEvasionAlerts(retryAt, 10).size());

            EvasionAlert discordDelivered = store.updateEvasionDelivery(new DiscordInvestigationStore.EvasionDeliveryUpdate(
                    alertId, DiscordInvestigationStore.EvasionDeliveryChannel.DISCORD, true,
                    Optional.empty(), Optional.empty(), minecraftDelivered.revision(), retryAt
            ));
            assertTrue(store.pendingEvasionAlerts(retryAt.plusSeconds(1), 10).isEmpty());
            assertThrows(ModerationPersistenceException.class, () ->
                    store.resolveEvasionAlert(alertId, created.revision(), retryAt.plusSeconds(1)));
            EvasionAlert resolved = store.resolveEvasionAlert(
                    alertId, discordDelivered.revision(), retryAt.plusSeconds(1));
            assertEquals(EvasionAlert.State.RESOLVED, resolved.state());
            assertTrue(store.resolveEvasionAlert(alertId, resolved.revision(), retryAt.plusSeconds(2)).replayed());
        }
    }

    private static void persistBatchWarning(
            HikariDataSource dataSource,
            JdbcDiscordPunishmentRepository punishments,
            int index
    ) {
        DiscordUserId userId = new DiscordUserId(Long.toString(223456789012346000L + index));
        ModerationSubjectId subjectId = ensureSubject(dataSource, userId);
        DiscordPunishment punishment = warning(subjectId, userId, index);
        punishments.create(punishment, "d09-batch-warning-" + index, NOW.plusMillis(index));
    }

    private static DiscordPunishment warning(
            ModerationSubjectId subjectId,
            DiscordUserId userId,
            int index
    ) {
        DiscordPunishmentIntent intent = new DiscordPunishmentIntent(
                DiscordConsequenceType.WARNING,
                SanctionLength.instant(),
                false,
                false,
                Optional.empty(),
                "Batch warning " + index,
                "D09 bounded observation test",
                0,
                true
        );
        return DiscordPunishment.pending(
                UUID.randomUUID(), subjectId, userId, GUILD,
                new Actor(ACTOR, "D09Staff", StaffRank.ADMIN), intent, NOW.plusMillis(index), "pending"
        );
    }

    private static DiscordInvestigationStore.PunishmentCaseDraft punishmentCase(
            DiscordInvestigationStore.PunishmentObservation observation
    ) {
        return new DiscordInvestigationStore.PunishmentCaseDraft(
                "d09:punishment:" + observation.punishmentId(),
                observation.punishmentId(),
                observation.subjectId(),
                observation.issuer(),
                observation.consequenceType(),
                observation.summary(),
                observation.state(),
                observation.expiresAt(),
                observation.revision(),
                observation.observedAt()
        );
    }

    private static void assertEvidenceMetadata(
            HikariDataSource dataSource,
            UUID evidenceId,
            CaseId caseId,
            ModerationSubjectId subjectId
    ) throws SQLException {
        try (var connection = dataSource.getConnection();
             var statement = connection.prepareStatement("""
                     SELECT m.case_id, LOWER(HEX(c.subject_id)) subject_hex, c.visibility, c.state,
                            JSON_UNQUOTE(JSON_EXTRACT(m.metadata_json, '$.action')) action,
                            JSON_UNQUOTE(JSON_EXTRACT(m.metadata_json, '$.capturedBy')) captured_by
                     FROM discord_evidence_metadata m
                     JOIN cases c ON c.case_id = m.case_id
                     WHERE m.evidence_id = ?
                     """)) {
            statement.setBytes(1, MariaDbIntegrationSupport.uuidBytes(evidenceId));
            try (var rows = statement.executeQuery()) {
                assertTrue(rows.next());
                assertEquals(caseId.value(), rows.getString("case_id"));
                assertEquals(subjectId.value().toString().replace("-", ""), rows.getString("subject_hex"));
                assertEquals("PRIVATE", rows.getString("visibility"));
                assertEquals("OPEN", rows.getString("state"));
                assertEquals("MESSAGE_CONTEXT", rows.getString("action"));
                assertEquals(ACTOR.toString(), rows.getString("captured_by"));
            }
        }
    }

    private static void assertPurgedMetadata(HikariDataSource dataSource, UUID evidenceId) throws SQLException {
        try (var connection = dataSource.getConnection();
             var statement = connection.prepareStatement(
                     "SELECT purge_state, metadata_json FROM discord_evidence_metadata WHERE evidence_id = ?")) {
            statement.setBytes(1, MariaDbIntegrationSupport.uuidBytes(evidenceId));
            try (var rows = statement.executeQuery()) {
                assertTrue(rows.next());
                assertEquals("PURGED", rows.getString("purge_state"));
                assertTrue(rows.getString("metadata_json").contains("\"purged\":true"));
                assertFalse(rows.getString("metadata_json").contains("private body"));
            }
        }
    }

    private static InvestigationEvidence.Message message(String messageId, String authorId, String content) {
        return new InvestigationEvidence.Message(
                "1410303324745371709", "1541286004298752091", messageId,
                new DiscordUserId(authorId), NOW.minusSeconds(10), Optional.empty(),
                "https://discord.com/channels/1410303324745371709/1541286004298752091/" + messageId,
                content, List.of()
        );
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
}
