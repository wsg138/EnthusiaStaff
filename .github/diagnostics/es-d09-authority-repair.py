from __future__ import annotations

import sys
from pathlib import Path

ROOT = Path(sys.argv[1]).resolve()


def path(name: str) -> Path:
    return ROOT / name


def read(name: str) -> str:
    return path(name).read_text(encoding="utf-8")


def write(name: str, content: str) -> None:
    path(name).write_text(content, encoding="utf-8")


def replace_once(name: str, old: str, new: str) -> None:
    content = read(name)
    count = content.count(old)
    if count != 1:
        raise RuntimeError(f"{name}: expected one replacement, found {count}: {old[:100]!r}")
    write(name, content.replace(old, new, 1))


# 1) V22: extend the existing authoritative cases table and make D09's table a
# lifecycle extension keyed by canonical CaseId rather than a second UUID case authority.
migration = r'''ALTER TABLE cases
    MODIFY target_id BINARY(16) NULL,
    ADD COLUMN subject_id BINARY(16) NULL AFTER target_id,
    ADD INDEX idx_cases_subject_time (subject_id, issued_at),
    ADD CONSTRAINT fk_cases_subject
        FOREIGN KEY (subject_id) REFERENCES moderation_subjects(subject_id),
    ADD CONSTRAINT ck_cases_target_or_subject
        CHECK (target_id IS NOT NULL OR subject_id IS NOT NULL);

UPDATE cases c
JOIN moderation_subject_minecraft_identities membership
  ON membership.player_id = c.target_id
SET c.subject_id = membership.subject_id
WHERE c.subject_id IS NULL;

CREATE TABLE discord_investigation_cases (
    case_id CHAR(16) NOT NULL,
    operation_key VARCHAR(128) NOT NULL,
    subject_id BINARY(16) NOT NULL,
    source ENUM('DISCORD_PUNISHMENT', 'INVESTIGATION') NOT NULL,
    punishment_id BINARY(16) NULL,
    last_activity_at TIMESTAMP(6) NOT NULL,
    closed_at TIMESTAMP(6) NULL,
    punishment_ended_at TIMESTAMP(6) NULL,
    source_revision BIGINT UNSIGNED NOT NULL DEFAULT 0,
    revision BIGINT UNSIGNED NOT NULL DEFAULT 0,
    PRIMARY KEY (case_id),
    UNIQUE KEY uq_discord_investigation_case_operation (operation_key),
    UNIQUE KEY uq_discord_investigation_case_punishment (punishment_id),
    INDEX idx_discord_investigation_case_subject (subject_id, last_activity_at),
    INDEX idx_discord_investigation_case_inactivity (last_activity_at),
    CONSTRAINT fk_discord_investigation_case_authority
        FOREIGN KEY (case_id) REFERENCES cases(case_id),
    CONSTRAINT fk_discord_investigation_case_subject
        FOREIGN KEY (subject_id) REFERENCES moderation_subjects(subject_id),
    CONSTRAINT ck_discord_investigation_case_source CHECK (
        (source = 'DISCORD_PUNISHMENT' AND punishment_id IS NOT NULL)
        OR (source = 'INVESTIGATION' AND punishment_id IS NULL)
    ),
    CONSTRAINT ck_discord_investigation_case_close CHECK (
        closed_at IS NULL OR closed_at >= last_activity_at
    )
) ENGINE=InnoDB;

CREATE TABLE discord_private_notes (
    note_id BINARY(16) NOT NULL,
    operation_key VARCHAR(128) NOT NULL,
    subject_id BINARY(16) NOT NULL,
    scope_type ENUM('SUBJECT', 'DISCORD_USER', 'MINECRAFT_PLAYER', 'CASE') NOT NULL,
    scope_value VARCHAR(160) NOT NULL,
    visibility ENUM('STAFF', 'MANAGEMENT') NOT NULL,
    current_text TEXT NOT NULL,
    created_by BINARY(16) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_by BINARY(16) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    revision BIGINT UNSIGNED NOT NULL DEFAULT 0,
    PRIMARY KEY (note_id),
    UNIQUE KEY uq_discord_private_note_operation (operation_key),
    INDEX idx_discord_private_note_subject (subject_id, updated_at, note_id),
    INDEX idx_discord_private_note_scope (scope_type, scope_value, updated_at),
    CONSTRAINT fk_discord_private_note_subject
        FOREIGN KEY (subject_id) REFERENCES moderation_subjects(subject_id),
    CONSTRAINT fk_discord_private_note_creator
        FOREIGN KEY (created_by) REFERENCES players(player_id),
    CONSTRAINT fk_discord_private_note_updater
        FOREIGN KEY (updated_by) REFERENCES players(player_id)
) ENGINE=InnoDB;

CREATE TABLE discord_private_note_versions (
    note_id BINARY(16) NOT NULL,
    revision BIGINT UNSIGNED NOT NULL,
    operation_key VARCHAR(128) NOT NULL,
    note_text TEXT NOT NULL,
    changed_by BINARY(16) NOT NULL,
    changed_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (note_id, revision),
    UNIQUE KEY uq_discord_private_note_version_operation (operation_key),
    CONSTRAINT fk_discord_private_note_version_note
        FOREIGN KEY (note_id) REFERENCES discord_private_notes(note_id),
    CONSTRAINT fk_discord_private_note_version_actor
        FOREIGN KEY (changed_by) REFERENCES players(player_id)
) ENGINE=InnoDB;

CREATE TABLE discord_investigation_evidence (
    evidence_id BINARY(16) NOT NULL,
    case_id CHAR(16) NOT NULL,
    message_link VARCHAR(512) NOT NULL,
    message_created_at TIMESTAMP(6) NOT NULL,
    last_observed_at TIMESTAMP(6) NOT NULL,
    edited_at TIMESTAMP(6) NULL,
    message_content MEDIUMTEXT NOT NULL,
    attachment_metadata_json JSON NOT NULL,
    revision BIGINT UNSIGNED NOT NULL DEFAULT 0,
    PRIMARY KEY (evidence_id),
    INDEX idx_discord_investigation_evidence_case (case_id, last_observed_at),
    CONSTRAINT fk_discord_investigation_evidence_parent
        FOREIGN KEY (evidence_id) REFERENCES discord_evidence_metadata(evidence_id),
    CONSTRAINT fk_discord_investigation_evidence_case
        FOREIGN KEY (case_id) REFERENCES discord_investigation_cases(case_id),
    CONSTRAINT ck_discord_investigation_evidence_times CHECK (
        last_observed_at >= message_created_at
        AND (edited_at IS NULL OR edited_at >= message_created_at)
    )
) ENGINE=InnoDB;

CREATE TABLE discord_investigation_evidence_versions (
    evidence_id BINARY(16) NOT NULL,
    revision BIGINT UNSIGNED NOT NULL,
    operation_key VARCHAR(128) NOT NULL,
    message_content MEDIUMTEXT NOT NULL,
    attachment_metadata_json JSON NOT NULL,
    edited_at TIMESTAMP(6) NULL,
    recorded_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (evidence_id, revision),
    UNIQUE KEY uq_discord_investigation_evidence_version_operation (operation_key),
    CONSTRAINT fk_discord_investigation_evidence_version_parent
        FOREIGN KEY (evidence_id) REFERENCES discord_investigation_evidence(evidence_id)
) ENGINE=InnoDB;

CREATE TABLE discord_investigation_context (
    evidence_id BINARY(16) NOT NULL,
    message_id DECIMAL(20, 0) UNSIGNED NOT NULL,
    guild_id DECIMAL(20, 0) UNSIGNED NOT NULL,
    channel_id DECIMAL(20, 0) UNSIGNED NOT NULL,
    author_user_id DECIMAL(20, 0) UNSIGNED NOT NULL,
    message_created_at TIMESTAMP(6) NOT NULL,
    edited_at TIMESTAMP(6) NULL,
    message_link VARCHAR(512) NOT NULL,
    message_content MEDIUMTEXT NOT NULL,
    attachment_metadata_json JSON NOT NULL,
    captured_at TIMESTAMP(6) NOT NULL,
    last_observed_at TIMESTAMP(6) NOT NULL,
    revision BIGINT UNSIGNED NOT NULL DEFAULT 0,
    PRIMARY KEY (evidence_id, message_id),
    INDEX idx_discord_investigation_context_channel (guild_id, channel_id, message_id),
    CONSTRAINT fk_discord_investigation_context_evidence
        FOREIGN KEY (evidence_id) REFERENCES discord_investigation_evidence(evidence_id),
    CONSTRAINT ck_discord_investigation_context_snowflakes CHECK (
        message_id BETWEEN 1 AND 18446744073709551615
        AND guild_id BETWEEN 1 AND 18446744073709551615
        AND channel_id BETWEEN 1 AND 18446744073709551615
        AND author_user_id BETWEEN 1 AND 18446744073709551615
    ),
    CONSTRAINT ck_discord_investigation_context_times CHECK (
        last_observed_at >= message_created_at
        AND captured_at >= message_created_at
        AND (edited_at IS NULL OR edited_at >= message_created_at)
    )
) ENGINE=InnoDB;

CREATE TABLE discord_investigation_context_operations (
    operation_key VARCHAR(128) NOT NULL,
    evidence_id BINARY(16) NOT NULL,
    captured_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (operation_key),
    CONSTRAINT fk_discord_investigation_context_operation_evidence
        FOREIGN KEY (evidence_id) REFERENCES discord_investigation_evidence(evidence_id)
) ENGINE=InnoDB;

CREATE TABLE discord_evasion_alerts (
    alert_id BINARY(16) NOT NULL,
    operation_key VARCHAR(160) NOT NULL,
    subject_id BINARY(16) NOT NULL,
    punishment_id BINARY(16) NOT NULL,
    triggering_minecraft_player_id BINARY(16) NOT NULL,
    current_server VARCHAR(64) NOT NULL,
    player_revision BIGINT UNSIGNED NOT NULL,
    state ENUM('OPEN', 'RESOLVED') NOT NULL DEFAULT 'OPEN',
    discord_delivery ENUM('PENDING', 'DELIVERED', 'RETRY') NOT NULL DEFAULT 'PENDING',
    minecraft_delivery ENUM('PENDING', 'DELIVERED', 'RETRY') NOT NULL DEFAULT 'PENDING',
    discord_attempts INT UNSIGNED NOT NULL DEFAULT 0,
    minecraft_attempts INT UNSIGNED NOT NULL DEFAULT 0,
    discord_error_code VARCHAR(96) NULL,
    minecraft_error_code VARCHAR(96) NULL,
    discord_next_attempt_at TIMESTAMP(6) NULL,
    minecraft_next_attempt_at TIMESTAMP(6) NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    resolved_at TIMESTAMP(6) NULL,
    revision BIGINT UNSIGNED NOT NULL DEFAULT 0,
    PRIMARY KEY (alert_id),
    UNIQUE KEY uq_discord_evasion_alert_operation (operation_key),
    INDEX idx_discord_evasion_alert_discord_due (state, discord_delivery, discord_next_attempt_at, updated_at),
    INDEX idx_discord_evasion_alert_minecraft_due (state, minecraft_delivery, minecraft_next_attempt_at, updated_at),
    INDEX idx_discord_evasion_alert_subject (subject_id, created_at),
    CONSTRAINT fk_discord_evasion_alert_subject
        FOREIGN KEY (subject_id) REFERENCES moderation_subjects(subject_id),
    CONSTRAINT fk_discord_evasion_alert_player
        FOREIGN KEY (triggering_minecraft_player_id) REFERENCES players(player_id),
    CONSTRAINT ck_discord_evasion_alert_resolution CHECK (
        (state = 'OPEN' AND resolved_at IS NULL)
        OR (state = 'RESOLVED' AND resolved_at IS NOT NULL AND resolved_at >= created_at)
    ),
    CONSTRAINT ck_discord_evasion_alert_discord_delivery CHECK (
        (discord_delivery = 'DELIVERED' AND discord_error_code IS NULL AND discord_next_attempt_at IS NULL)
        OR (discord_delivery = 'PENDING' AND discord_error_code IS NULL AND discord_next_attempt_at IS NOT NULL)
        OR (discord_delivery = 'RETRY' AND discord_error_code IS NOT NULL AND discord_next_attempt_at IS NOT NULL)
    ),
    CONSTRAINT ck_discord_evasion_alert_minecraft_delivery CHECK (
        (minecraft_delivery = 'DELIVERED' AND minecraft_error_code IS NULL AND minecraft_next_attempt_at IS NULL)
        OR (minecraft_delivery = 'PENDING' AND minecraft_error_code IS NULL AND minecraft_next_attempt_at IS NOT NULL)
        OR (minecraft_delivery = 'RETRY' AND minecraft_error_code IS NOT NULL AND minecraft_next_attempt_at IS NOT NULL)
    )
) ENGINE=InnoDB;
'''
write("persistence/src/main/resources/db/migration/V22__discord_investigation_state.sql", migration)

# 2) Canonical investigation case domain record.
write("domain/src/main/java/net/enthusia/staff/domain/investigation/InvestigationCase.java", r'''package net.enthusia.staff.domain.investigation;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import net.enthusia.staff.common.CaseId;
import net.enthusia.staff.domain.moderation.ModerationSubjectId;

/** Private D09 lifecycle metadata attached to an authoritative moderation case. */
public record InvestigationCase(
        CaseId caseId,
        ModerationSubjectId subjectId,
        Source source,
        Optional<UUID> punishmentId,
        String summary,
        State state,
        UUID openedBy,
        Instant openedAt,
        Instant lastActivityAt,
        Optional<Instant> closedAt,
        Optional<Instant> punishmentEndedAt,
        long sourceRevision,
        long revision,
        boolean replayed
) {
    public enum Source {
        DISCORD_PUNISHMENT,
        INVESTIGATION
    }

    public enum State {
        OPEN,
        CLOSED
    }

    public InvestigationCase {
        if (caseId == null || subjectId == null || source == null || punishmentId == null
                || blank(summary) || state == null || openedBy == null || openedAt == null
                || lastActivityAt == null || closedAt == null || punishmentEndedAt == null
                || sourceRevision < 0 || revision < 0) {
            throw new IllegalArgumentException("investigation case fields must be present and valid");
        }
        if (lastActivityAt.isBefore(openedAt)) {
            throw new IllegalArgumentException("case activity cannot predate opening");
        }
        validateSource(source, punishmentId);
        validateClosedState(state, closedAt, lastActivityAt);
        punishmentEndedAt.ifPresent(endedAt -> {
            if (endedAt.isBefore(openedAt)) {
                throw new IllegalArgumentException("punishment end cannot predate case opening");
            }
        });
    }

    private static void validateSource(Source source, Optional<UUID> punishmentId) {
        if ((source == Source.DISCORD_PUNISHMENT) != punishmentId.isPresent()) {
            throw new IllegalArgumentException("punishment cases require exactly one punishment id");
        }
    }

    private static void validateClosedState(State state, Optional<Instant> closedAt, Instant lastActivityAt) {
        if (state == State.OPEN && closedAt.isPresent()) {
            throw new IllegalArgumentException("open case cannot have a close time");
        }
        if (state == State.CLOSED && closedAt.isEmpty()) {
            throw new IllegalArgumentException("closed case requires a close time");
        }
        closedAt.ifPresent(value -> {
            if (value.isBefore(lastActivityAt)) {
                throw new IllegalArgumentException("case close time cannot predate last activity");
            }
        });
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
''')

# 3) Evidence points at canonical CaseId.
replace_once(
    "domain/src/main/java/net/enthusia/staff/domain/investigation/InvestigationEvidence.java",
    "import java.util.UUID;\nimport net.enthusia.staff.domain.moderation.DiscordUserId;",
    "import java.util.UUID;\nimport net.enthusia.staff.common.CaseId;\nimport net.enthusia.staff.domain.moderation.DiscordUserId;",
)
replace_once(
    "domain/src/main/java/net/enthusia/staff/domain/investigation/InvestigationEvidence.java",
    "            UUID caseId,\n            Message focus,",
    "            CaseId caseId,\n            Message focus,",
)
replace_once(
    "domain/src/main/java/net/enthusia/staff/domain/investigation/InvestigationEvidence.java",
    "            UUID caseId,\n            ModerationSubjectId subjectId,",
    "            CaseId caseId,\n            ModerationSubjectId subjectId,",
)

# 4) Investigation persistence port uses canonical CaseId and carries issuer metadata needed by cases.
write("domain/src/main/java/net/enthusia/staff/domain/ports/DiscordInvestigationStore.java", r'''package net.enthusia.staff.domain.ports;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.enthusia.staff.common.CaseId;
import net.enthusia.staff.domain.auth.Actor;
import net.enthusia.staff.domain.auth.DiscordConsequenceType;
import net.enthusia.staff.domain.discord.DiscordPunishmentState;
import net.enthusia.staff.domain.investigation.EvasionAlert;
import net.enthusia.staff.domain.investigation.InvestigationCase;
import net.enthusia.staff.domain.investigation.InvestigationEvidence;
import net.enthusia.staff.domain.investigation.InvestigationNote;
import net.enthusia.staff.domain.moderation.ModerationSubjectId;

/** Authoritative private D09 investigation persistence boundary. */
public interface DiscordInvestigationStore {
    InvestigationCase ensurePunishmentCase(PunishmentCaseDraft draft);

    InvestigationCase createInvestigationCase(InvestigationCaseDraft draft);

    Optional<InvestigationCase> findCase(CaseId caseId);

    InvestigationCase touchCase(CaseActivity activity);

    int closeInactiveCases(Instant inactivityCutoff, Instant now, int limit);

    InvestigationNote createNote(NoteDraft draft);

    InvestigationNote editNote(NoteEdit edit);

    Optional<InvestigationNote> findNote(UUID noteId);

    List<InvestigationNote.Version> noteHistory(UUID noteId, int limit);

    InvestigationEvidence.Stored captureEvidence(InvestigationEvidence.Capture capture);

    InvestigationEvidence.Stored recordEvidenceEdit(InvestigationEvidence.Edit edit);

    int captureMoreContext(InvestigationEvidence.ContextBatch batch);

    Optional<InvestigationEvidence.Stored> findEvidenceByMessage(String guildId, String channelId, String messageId);

    int purgeEligibleEvidence(Instant now, int limit);

    EvasionAlert createEvasionAlert(EvasionAlertDraft draft);

    Optional<EvasionAlert> findEvasionAlert(UUID alertId);

    List<EvasionAlert> pendingEvasionAlerts(Instant now, int limit);

    EvasionAlert updateEvasionDelivery(EvasionDeliveryUpdate update);

    EvasionAlert resolveEvasionAlert(UUID alertId, long expectedRevision, Instant now);

    List<PunishmentObservation> punishmentObservations(int limit);

    List<EvasionCandidate> evasionCandidates(int limit);

    record PunishmentCaseDraft(
            String operationKey,
            UUID punishmentId,
            ModerationSubjectId subjectId,
            Actor issuer,
            DiscordConsequenceType consequenceType,
            String summary,
            DiscordPunishmentState state,
            Optional<Instant> expiresAt,
            long punishmentRevision,
            Instant observedAt
    ) {
        public PunishmentCaseDraft {
            if (blank(operationKey) || operationKey.length() > 128 || punishmentId == null || subjectId == null
                    || issuer == null || consequenceType == null || blank(summary) || state == null || expiresAt == null
                    || punishmentRevision < 0 || observedAt == null) {
                throw new IllegalArgumentException("punishment case draft fields are invalid");
            }
        }
    }

    record InvestigationCaseDraft(
            String operationKey,
            ModerationSubjectId subjectId,
            String summary,
            Actor openedBy,
            Instant openedAt
    ) {
        public InvestigationCaseDraft {
            if (blank(operationKey) || operationKey.length() > 128 || subjectId == null
                    || blank(summary) || openedBy == null || openedAt == null) {
                throw new IllegalArgumentException("investigation case draft fields are invalid");
            }
        }
    }

    record CaseActivity(CaseId caseId, long expectedRevision, Instant occurredAt) {
        public CaseActivity {
            if (caseId == null || expectedRevision < 0 || occurredAt == null) {
                throw new IllegalArgumentException("case activity fields are invalid");
            }
        }
    }

    record NoteDraft(
            UUID noteId,
            String operationKey,
            ModerationSubjectId subjectId,
            InvestigationNote.Scope scope,
            InvestigationNote.Visibility visibility,
            String text,
            UUID actorId,
            Instant now
    ) {
        public NoteDraft {
            if (noteId == null || blank(operationKey) || operationKey.length() > 128 || subjectId == null
                    || scope == null || visibility == null || actorId == null || now == null) {
                throw new IllegalArgumentException("note draft fields are invalid");
            }
            InvestigationNote.validateText(text);
        }
    }

    record NoteEdit(
            UUID noteId,
            String operationKey,
            ModerationSubjectId subjectId,
            long expectedRevision,
            String text,
            UUID actorId,
            Instant now
    ) {
        public NoteEdit {
            if (noteId == null || blank(operationKey) || operationKey.length() > 128 || subjectId == null
                    || expectedRevision < 0 || actorId == null || now == null) {
                throw new IllegalArgumentException("note edit fields are invalid");
            }
            InvestigationNote.validateText(text);
        }
    }

    record EvasionAlertDraft(
            UUID alertId,
            String operationKey,
            ModerationSubjectId subjectId,
            UUID punishmentId,
            UUID triggeringMinecraftPlayerId,
            String currentServer,
            long playerRevision,
            Instant now
    ) {
        public EvasionAlertDraft {
            if (alertId == null || blank(operationKey) || operationKey.length() > 160 || subjectId == null
                    || punishmentId == null || triggeringMinecraftPlayerId == null || blank(currentServer)
                    || currentServer.length() > 64 || playerRevision < 0 || now == null) {
                throw new IllegalArgumentException("evasion alert draft fields are invalid");
            }
        }
    }

    record EvasionDeliveryUpdate(
            UUID alertId,
            EvasionDeliveryChannel channel,
            boolean delivered,
            Optional<String> errorCode,
            Optional<Instant> nextAttemptAt,
            long expectedRevision,
            Instant now
    ) {
        public EvasionDeliveryUpdate {
            if (alertId == null || channel == null || errorCode == null || nextAttemptAt == null
                    || expectedRevision < 0 || now == null) {
                throw new IllegalArgumentException("evasion delivery update fields are invalid");
            }
            if (delivered && (errorCode.isPresent() || nextAttemptAt.isPresent())) {
                throw new IllegalArgumentException("delivered alert cannot retain retry state");
            }
            if (!delivered && (errorCode.isEmpty() || nextAttemptAt.isEmpty()
                    || nextAttemptAt.orElseThrow().isBefore(now))) {
                throw new IllegalArgumentException("failed delivery requires error and future retry time");
            }
        }
    }

    enum EvasionDeliveryChannel {
        DISCORD,
        MINECRAFT
    }

    record PunishmentObservation(
            UUID punishmentId,
            ModerationSubjectId subjectId,
            Actor issuer,
            DiscordConsequenceType consequenceType,
            String summary,
            DiscordPunishmentState state,
            Optional<Instant> expiresAt,
            long revision,
            Instant observedAt
    ) {
        public PunishmentObservation {
            if (punishmentId == null || subjectId == null || issuer == null || consequenceType == null
                    || blank(summary) || state == null || expiresAt == null || revision < 0 || observedAt == null) {
                throw new IllegalArgumentException("punishment observation fields are invalid");
            }
        }
    }

    record EvasionCandidate(
            ModerationSubjectId subjectId,
            UUID punishmentId,
            UUID minecraftPlayerId,
            String currentServer,
            long playerRevision
    ) {
        public EvasionCandidate {
            if (subjectId == null || punishmentId == null || minecraftPlayerId == null
                    || blank(currentServer) || playerRevision < 0) {
                throw new IllegalArgumentException("evasion candidate fields are invalid");
            }
        }
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
''')

# 5) Canonical case + D09 lifecycle extension persistence.
write("persistence/src/main/java/net/enthusia/staff/persistence/JdbcDiscordInvestigationCaseStore.java", r'''package net.enthusia.staff.persistence;

import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;
import net.enthusia.staff.common.CaseId;
import net.enthusia.staff.common.SecureIdentifiers;
import net.enthusia.staff.domain.auth.Actor;
import net.enthusia.staff.domain.investigation.InvestigationCase;
import net.enthusia.staff.domain.moderation.ModerationSubjectId;
import net.enthusia.staff.domain.ports.DiscordInvestigationStore.CaseActivity;
import net.enthusia.staff.domain.ports.DiscordInvestigationStore.InvestigationCaseDraft;
import net.enthusia.staff.domain.ports.DiscordInvestigationStore.PunishmentCaseDraft;

final class JdbcDiscordInvestigationCaseStore {
    private static final int CASE_ID_ATTEMPTS = 4;
    private static final String CONFIGURATION_VERSION = "discord-d09-v1";
    private final DataSource dataSource;
    private final SecureIdentifiers identifiers = new SecureIdentifiers(new SecureRandom());

    JdbcDiscordInvestigationCaseStore(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    InvestigationCase ensurePunishmentCase(PunishmentCaseDraft draft) {
        return JdbcTransactionSupport.execute(dataSource, "Unable to reconcile Discord punishment case", connection -> {
            Current current = byPunishment(connection, draft.punishmentId(), true);
            if (current == null) {
                return insertPunishmentCase(connection, draft);
            }
            requireSubject(current, draft.subjectId());
            if (draft.punishmentRevision() <= current.sourceRevision()) {
                return current.toDomain(true);
            }
            return updatePunishmentObservation(connection, current, draft);
        });
    }

    InvestigationCase createInvestigationCase(InvestigationCaseDraft draft) {
        return JdbcTransactionSupport.execute(dataSource, "Unable to create Discord investigation case", connection -> {
            Current replay = byOperation(connection, draft.operationKey(), true);
            if (replay != null) {
                requireInvestigationReplay(replay, draft);
                return replay.toDomain(true);
            }
            return insertInvestigationCase(connection, draft);
        });
    }

    Optional<InvestigationCase> findCase(CaseId caseId) {
        if (caseId == null) {
            throw new IllegalArgumentException("caseId must be present");
        }
        return JdbcTransactionSupport.execute(dataSource, "Unable to read Discord investigation case", connection ->
                Optional.ofNullable(byId(connection, caseId, false)).map(current -> current.toDomain(false)));
    }

    InvestigationCase touchCase(CaseActivity activity) {
        return JdbcTransactionSupport.execute(dataSource, "Unable to touch Discord investigation case", connection -> {
            Current current = requireById(connection, activity.caseId(), true);
            requireOpenRevision(current, activity.expectedRevision());
            Instant lastActivity = later(current.lastActivityAt(), activity.occurredAt());
            updateActivity(connection, current.caseId(), current.revision(), lastActivity);
            return requireById(connection, current.caseId(), false).toDomain(false);
        });
    }

    int closeInactiveCases(Instant inactivityCutoff, Instant now, int limit) {
        validateCloseRequest(inactivityCutoff, now, limit);
        return JdbcTransactionSupport.execute(dataSource, "Unable to close inactive Discord cases", connection -> {
            List<CaseId> eligible = eligibleForClose(connection, inactivityCutoff, limit);
            for (CaseId caseId : eligible) {
                closeCase(connection, caseId, now);
            }
            return eligible.size();
        });
    }

    private InvestigationCase insertPunishmentCase(Connection connection, PunishmentCaseDraft draft) throws SQLException {
        for (int attempt = 0; attempt < CASE_ID_ATTEMPTS; attempt++) {
            CaseId caseId = identifiers.newCaseId();
            try {
                insertCanonicalPunishmentCase(connection, caseId, draft);
                insertExtension(connection, caseId, draft);
                return requireById(connection, caseId, false).toDomain(false);
            } catch (SQLException exception) {
                InvestigationCase replay = duplicateReplay(connection, draft.operationKey(), exception);
                if (replay != null) {
                    return replay;
                }
            }
        }
        throw new SQLException("unable to allocate unique Discord punishment case id");
    }

    private InvestigationCase insertInvestigationCase(Connection connection, InvestigationCaseDraft draft)
            throws SQLException {
        for (int attempt = 0; attempt < CASE_ID_ATTEMPTS; attempt++) {
            CaseId caseId = identifiers.newCaseId();
            try {
                insertCanonicalInvestigationCase(connection, caseId, draft);
                insertExtension(connection, caseId, draft);
                return requireById(connection, caseId, false).toDomain(false);
            } catch (SQLException exception) {
                InvestigationCase replay = duplicateReplay(connection, draft.operationKey(), exception);
                if (replay != null) {
                    Current current = requireById(connection, replay.caseId(), false);
                    requireInvestigationReplay(current, draft);
                    return current.toDomain(true);
                }
            }
        }
        throw new SQLException("unable to allocate unique Discord investigation case id");
    }

    private InvestigationCase duplicateReplay(Connection connection, String operationKey, SQLException exception)
            throws SQLException {
        if (!JdbcSqlErrors.isDuplicateKey(exception)) {
            throw exception;
        }
        Current replay = byOperation(connection, operationKey, false);
        return replay == null ? null : replay.toDomain(true);
    }

    private static void insertCanonicalPunishmentCase(
            Connection connection,
            CaseId caseId,
            PunishmentCaseDraft draft
    ) throws SQLException {
        insertCanonicalCase(
                connection, caseId, draft.operationKey(), draft.subjectId(), draft.issuer(),
                truncate(draft.summary(), 160), "DISCORD_" + draft.consequenceType().name(),
                "DISCORD", draft.summary(), draft.observedAt()
        );
    }

    private static void insertCanonicalInvestigationCase(
            Connection connection,
            CaseId caseId,
            InvestigationCaseDraft draft
    ) throws SQLException {
        insertCanonicalCase(
                connection, caseId, draft.operationKey(), draft.subjectId(), draft.openedBy(),
                "Private Discord investigation", "DISCORD_INVESTIGATION", "DISCORD_INVESTIGATION",
                draft.summary(), draft.openedAt()
        );
    }

    private static void insertCanonicalCase(
            Connection connection,
            CaseId caseId,
            String operationKey,
            ModerationSubjectId subjectId,
            Actor actor,
            String publicReason,
            String exactReason,
            String family,
            String explanation,
            Instant issuedAt
    ) throws SQLException {
        UUID targetId = mainMinecraftTarget(connection, subjectId);
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO cases(
                    case_id, idempotency_key, target_id, subject_id, actor_id, actor_name, actor_rank,
                    public_reason, exact_reason_id, sanction_family, internal_explanation,
                    configuration_version, visibility, state, issued_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'PRIVATE', 'OPEN', ?)
                """)) {
            statement.setString(1, caseId.value());
            statement.setString(2, operationKey);
            setUuid(statement, 3, targetId);
            statement.setBytes(4, UuidBytes.toBytes(subjectId.value()));
            statement.setBytes(5, UuidBytes.toBytes(actor.id()));
            statement.setString(6, actor.displayName());
            statement.setString(7, actor.rank().name());
            statement.setString(8, publicReason);
            statement.setString(9, truncate(exactReason, 96));
            statement.setString(10, truncate(family, 64));
            statement.setString(11, explanation);
            statement.setString(12, CONFIGURATION_VERSION);
            statement.setTimestamp(13, Timestamp.from(issuedAt));
            JdbcTransactionSupport.requireSingleUpdate(statement.executeUpdate(), "authoritative case was not inserted");
        }
    }

    private static UUID mainMinecraftTarget(Connection connection, ModerationSubjectId subjectId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT player_id FROM moderation_subject_main_accounts WHERE subject_id = ?
                """)) {
            statement.setBytes(1, UuidBytes.toBytes(subjectId.value()));
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? UuidBytes.fromBytes(rows.getBytes("player_id")) : null;
            }
        }
    }

    private static void insertExtension(Connection connection, CaseId caseId, PunishmentCaseDraft draft)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO discord_investigation_cases(
                    case_id, operation_key, subject_id, source, punishment_id, last_activity_at,
                    closed_at, punishment_ended_at, source_revision, revision
                ) VALUES (?, ?, ?, 'DISCORD_PUNISHMENT', ?, ?, NULL, ?, ?, 0)
                """)) {
            statement.setString(1, caseId.value());
            statement.setString(2, draft.operationKey());
            statement.setBytes(3, UuidBytes.toBytes(draft.subjectId().value()));
            statement.setBytes(4, UuidBytes.toBytes(draft.punishmentId()));
            statement.setTimestamp(5, Timestamp.from(draft.observedAt()));
            setInstant(statement, 6, draft.state().terminal() ? Optional.of(draft.observedAt()) : Optional.empty());
            statement.setLong(7, draft.punishmentRevision());
            JdbcTransactionSupport.requireSingleUpdate(statement.executeUpdate(), "punishment case extension was not inserted");
        }
    }

    private static void insertExtension(Connection connection, CaseId caseId, InvestigationCaseDraft draft)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO discord_investigation_cases(
                    case_id, operation_key, subject_id, source, punishment_id, last_activity_at,
                    closed_at, punishment_ended_at, source_revision, revision
                ) VALUES (?, ?, ?, 'INVESTIGATION', NULL, ?, NULL, NULL, 0, 0)
                """)) {
            statement.setString(1, caseId.value());
            statement.setString(2, draft.operationKey());
            statement.setBytes(3, UuidBytes.toBytes(draft.subjectId().value()));
            statement.setTimestamp(4, Timestamp.from(draft.openedAt()));
            JdbcTransactionSupport.requireSingleUpdate(statement.executeUpdate(), "investigation case extension was not inserted");
        }
    }

    private InvestigationCase updatePunishmentObservation(
            Connection connection,
            Current current,
            PunishmentCaseDraft draft
    ) throws SQLException {
        Instant lastActivity = later(current.lastActivityAt(), draft.observedAt());
        Optional<Instant> punishmentEnded = draft.state().terminal()
                ? Optional.of(draft.observedAt()) : current.punishmentEndedAt();
        try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE discord_investigation_cases
                SET last_activity_at = ?, punishment_ended_at = ?, source_revision = ?, revision = revision + 1
                WHERE case_id = ? AND revision = ?
                """)) {
            statement.setTimestamp(1, Timestamp.from(lastActivity));
            setInstant(statement, 2, punishmentEnded);
            statement.setLong(3, draft.punishmentRevision());
            statement.setString(4, current.caseId().value());
            statement.setLong(5, current.revision());
            JdbcTransactionSupport.requireSingleUpdate(statement.executeUpdate(), "punishment case revision changed");
        }
        return requireById(connection, current.caseId(), false).toDomain(false);
    }

    private static void updateActivity(
            Connection connection,
            CaseId caseId,
            long revision,
            Instant lastActivity
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE discord_investigation_cases
                SET last_activity_at = ?, revision = revision + 1
                WHERE case_id = ? AND closed_at IS NULL AND revision = ?
                """)) {
            statement.setTimestamp(1, Timestamp.from(lastActivity));
            statement.setString(2, caseId.value());
            statement.setLong(3, revision);
            JdbcTransactionSupport.requireSingleUpdate(statement.executeUpdate(), "case activity revision changed");
        }
    }

    private static List<CaseId> eligibleForClose(Connection connection, Instant cutoff, int limit) throws SQLException {
        List<CaseId> cases = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT i.case_id
                FROM discord_investigation_cases i
                JOIN cases c ON c.case_id = i.case_id
                WHERE c.state = 'OPEN' AND i.closed_at IS NULL AND i.last_activity_at <= ?
                  AND (i.source = 'INVESTIGATION' OR i.punishment_ended_at IS NOT NULL)
                ORDER BY i.last_activity_at, i.case_id
                LIMIT ?
                FOR UPDATE
                """)) {
            statement.setTimestamp(1, Timestamp.from(cutoff));
            statement.setInt(2, limit);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    cases.add(new CaseId(rows.getString("case_id")));
                }
            }
        }
        return List.copyOf(cases);
    }

    private static void closeCase(Connection connection, CaseId caseId, Instant now) throws SQLException {
        try (PreparedStatement extension = connection.prepareStatement("""
                UPDATE discord_investigation_cases
                SET closed_at = ?, revision = revision + 1
                WHERE case_id = ? AND closed_at IS NULL
                """);
             PreparedStatement authority = connection.prepareStatement("""
                UPDATE cases SET state = 'CLOSED', revision = revision + 1
                WHERE case_id = ? AND state = 'OPEN'
                """)) {
            extension.setTimestamp(1, Timestamp.from(now));
            extension.setString(2, caseId.value());
            JdbcTransactionSupport.requireSingleUpdate(extension.executeUpdate(), "case lifecycle extension was not closed");
            authority.setString(1, caseId.value());
            JdbcTransactionSupport.requireSingleUpdate(authority.executeUpdate(), "authoritative case was not closed");
        }
    }

    private Current requireById(Connection connection, CaseId caseId, boolean lock) throws SQLException {
        Current current = byId(connection, caseId, lock);
        if (current == null) {
            throw new SQLException("Discord investigation case does not exist");
        }
        return current;
    }

    private Current byId(Connection connection, CaseId caseId, boolean lock) throws SQLException {
        return queryOne(connection, "i.case_id = ?", caseId.value(), lock);
    }

    private Current byPunishment(Connection connection, UUID punishmentId, boolean lock) throws SQLException {
        return queryOne(connection, "i.punishment_id = ?", UuidBytes.toBytes(punishmentId), lock);
    }

    private Current byOperation(Connection connection, String operationKey, boolean lock) throws SQLException {
        return queryOne(connection, "i.operation_key = ?", operationKey, lock);
    }

    private Current queryOne(Connection connection, String predicate, Object value, boolean lock) throws SQLException {
        String sql = """
                SELECT i.case_id, i.operation_key, i.subject_id, i.source, i.punishment_id,
                       c.internal_explanation AS summary, c.state, c.actor_id, c.issued_at,
                       i.last_activity_at, i.closed_at, i.punishment_ended_at,
                       i.source_revision, i.revision
                FROM discord_investigation_cases i
                JOIN cases c ON c.case_id = i.case_id
                WHERE %s%s
                """.formatted(predicate, lock ? " FOR UPDATE" : "");
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            if (value instanceof byte[] bytes) {
                statement.setBytes(1, bytes);
            } else {
                statement.setString(1, value.toString());
            }
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? read(rows) : null;
            }
        }
    }

    private static Current read(ResultSet rows) throws SQLException {
        byte[] punishmentBytes = rows.getBytes("punishment_id");
        Timestamp closedAt = rows.getTimestamp("closed_at");
        Timestamp punishmentEnded = rows.getTimestamp("punishment_ended_at");
        return new Current(
                new CaseId(rows.getString("case_id")),
                rows.getString("operation_key"),
                new ModerationSubjectId(UuidBytes.fromBytes(rows.getBytes("subject_id"))),
                InvestigationCase.Source.valueOf(rows.getString("source")),
                punishmentBytes == null ? Optional.empty() : Optional.of(UuidBytes.fromBytes(punishmentBytes)),
                rows.getString("summary"),
                "OPEN".equals(rows.getString("state")) ? InvestigationCase.State.OPEN : InvestigationCase.State.CLOSED,
                UuidBytes.fromBytes(rows.getBytes("actor_id")),
                rows.getTimestamp("issued_at").toInstant(),
                rows.getTimestamp("last_activity_at").toInstant(),
                closedAt == null ? Optional.empty() : Optional.of(closedAt.toInstant()),
                punishmentEnded == null ? Optional.empty() : Optional.of(punishmentEnded.toInstant()),
                rows.getLong("source_revision"),
                rows.getLong("revision")
        );
    }

    private static void requireSubject(Current current, ModerationSubjectId subjectId) throws SQLException {
        if (!current.subjectId().equals(subjectId)) {
            throw new SQLException("punishment case subject does not match persisted case");
        }
    }

    private static void requireInvestigationReplay(Current current, InvestigationCaseDraft draft) throws SQLException {
        if (current.source() != InvestigationCase.Source.INVESTIGATION
                || !current.subjectId().equals(draft.subjectId())
                || !current.summary().equals(draft.summary())
                || !current.openedBy().equals(draft.openedBy().id())) {
            throw new SQLException("investigation case operation key was reused for a different request");
        }
    }

    private static void requireOpenRevision(Current current, long expectedRevision) throws SQLException {
        if (current.state() != InvestigationCase.State.OPEN || current.closedAt().isPresent()) {
            throw new SQLException("Discord investigation case is closed");
        }
        if (current.revision() != expectedRevision) {
            throw new SQLException("Discord investigation case revision changed");
        }
    }

    private static void validateCloseRequest(Instant cutoff, Instant now, int limit) {
        if (cutoff == null || now == null || now.isBefore(cutoff) || limit < 1 || limit > 500) {
            throw new IllegalArgumentException("inactive case close request is invalid");
        }
    }

    private static Instant later(Instant first, Instant second) {
        return first.isAfter(second) ? first : second;
    }

    private static void setInstant(PreparedStatement statement, int index, Optional<Instant> value)
            throws SQLException {
        if (value.isPresent()) {
            statement.setTimestamp(index, Timestamp.from(value.orElseThrow()));
        } else {
            statement.setNull(index, Types.TIMESTAMP);
        }
    }

    private static void setUuid(PreparedStatement statement, int index, UUID value) throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.BINARY);
        } else {
            statement.setBytes(index, UuidBytes.toBytes(value));
        }
    }

    private static String truncate(String value, int limit) {
        return value.length() <= limit ? value : value.substring(0, limit);
    }

    private record Current(
            CaseId caseId,
            String operationKey,
            ModerationSubjectId subjectId,
            InvestigationCase.Source source,
            Optional<UUID> punishmentId,
            String summary,
            InvestigationCase.State state,
            UUID openedBy,
            Instant openedAt,
            Instant lastActivityAt,
            Optional<Instant> closedAt,
            Optional<Instant> punishmentEndedAt,
            long sourceRevision,
            long revision
    ) {
        InvestigationCase toDomain(boolean replayed) {
            return new InvestigationCase(
                    caseId, subjectId, source, punishmentId, summary, state,
                    openedBy, openedAt, lastActivityAt, closedAt, punishmentEndedAt,
                    sourceRevision, revision, replayed
            );
        }
    }
}
''')

# 6) Evidence repository stores canonical case IDs in both parent metadata and private payload.
evidence_store = "persistence/src/main/java/net/enthusia/staff/persistence/JdbcDiscordInvestigationEvidenceStore.java"
replace_once(evidence_store,
             "import javax.sql.DataSource;\nimport net.enthusia.staff.domain.investigation.InvestigationEvidence;",
             "import javax.sql.DataSource;\nimport net.enthusia.staff.common.CaseId;\nimport net.enthusia.staff.domain.investigation.InvestigationEvidence;")
replace_once(evidence_store,
             ") VALUES (?, ?, ?, NULL, ?, ?, ?, ?, ?, ?, ?, 'ACTIVE', 0)",
             ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'ACTIVE', 0)")
replace_once(evidence_store,
             "            statement.setBytes(3, UuidBytes.toBytes(capture.subjectId().value()));\n            statement.setBigDecimal(4, snowflake(focus.guildId()));\n            statement.setBigDecimal(5, snowflake(focus.channelId()));\n            statement.setBigDecimal(6, snowflake(focus.messageId()));\n            statement.setBigDecimal(7, snowflake(focus.authorUserId().value()));\n            statement.setTimestamp(8, Timestamp.from(capture.capturedAt()));\n            statement.setTimestamp(9, Timestamp.from(capture.capturedAt().plus(INVESTIGATION_RETENTION_WINDOW)));\n            statement.setString(10, codec.evidenceMetadata(capture));",
             "            statement.setBytes(3, UuidBytes.toBytes(capture.subjectId().value()));\n            statement.setString(4, capture.caseId().value());\n            statement.setBigDecimal(5, snowflake(focus.guildId()));\n            statement.setBigDecimal(6, snowflake(focus.channelId()));\n            statement.setBigDecimal(7, snowflake(focus.messageId()));\n            statement.setBigDecimal(8, snowflake(focus.authorUserId().value()));\n            statement.setTimestamp(9, Timestamp.from(capture.capturedAt()));\n            statement.setTimestamp(10, Timestamp.from(capture.capturedAt().plus(INVESTIGATION_RETENTION_WINDOW)));\n            statement.setString(11, codec.evidenceMetadata(capture));")
replace_once(evidence_store,
             "                    evidence_id, investigation_case_id, message_link, message_created_at,",
             "                    evidence_id, case_id, message_link, message_created_at,")
replace_once(evidence_store,
             "            statement.setBytes(2, UuidBytes.toBytes(capture.caseId()));",
             "            statement.setString(2, capture.caseId().value());")
replace_once(evidence_store,
             "    private static void touchCase(Connection connection, UUID caseId, Instant now) throws SQLException {",
             "    private static void touchCase(Connection connection, CaseId caseId, Instant now) throws SQLException {")
replace_once(evidence_store,
             "                WHERE case_id = ? AND state = 'OPEN'",
             "                WHERE case_id = ? AND closed_at IS NULL")
replace_once(evidence_store,
             "            statement.setBytes(2, UuidBytes.toBytes(caseId));",
             "            statement.setString(2, caseId.value());")
replace_once(evidence_store,
             "            UUID caseId,\n            ModerationSubjectId subjectId",
             "            CaseId caseId,\n            ModerationSubjectId subjectId")
replace_once(evidence_store,
             "                SELECT subject_id, state\n                FROM discord_investigation_cases\n                WHERE case_id = ?",
             "                SELECT i.subject_id, c.state\n                FROM discord_investigation_cases i\n                JOIN cases c ON c.case_id = i.case_id\n                WHERE i.case_id = ?")
replace_once(evidence_store,
             "            statement.setBytes(1, UuidBytes.toBytes(caseId));",
             "            statement.setString(1, caseId.value());")
replace_once(evidence_store,
             "                       m.captured_at, e.investigation_case_id, e.last_observed_at, e.revision",
             "                       m.captured_at, e.case_id, e.last_observed_at, e.revision")
# There are three SELECT projections with investigation_case_id; replace remaining occurrences.
content = read(evidence_store).replace("e.investigation_case_id", "e.case_id")
content = content.replace("UuidBytes.fromBytes(rows.getBytes(\"case_id\"))", "new CaseId(rows.getString(\"case_id\"))")
content = content.replace("JOIN discord_investigation_cases c ON c.case_id = e.case_id", "JOIN discord_investigation_cases i ON i.case_id = e.case_id\n                JOIN cases c ON c.case_id = i.case_id")
content = content.replace("c.source = 'DISCORD_PUNISHMENT'", "i.source = 'DISCORD_PUNISHMENT'")
content = content.replace("c.punishment_ended_at", "i.punishment_ended_at")
content = content.replace("c.source = 'INVESTIGATION'", "i.source = 'INVESTIGATION'")
content = content.replace("c.closed_at", "i.closed_at")
content = content.replace("COALESCE(c.punishment_ended_at, c.closed_at)", "COALESCE(i.punishment_ended_at, i.closed_at)")
content = content.replace("            UUID caseId,\n            ModerationSubjectId subjectId,", "            CaseId caseId,\n            ModerationSubjectId subjectId,")
write(evidence_store, content)

# 7) D07 observation preserves issuer and consequence type for canonical case provenance.
source = "persistence/src/main/java/net/enthusia/staff/persistence/JdbcDiscordInvestigationSource.java"
replace_once(source,
             "                punishment.issuer().id(),\n                punishment.intent().publicReason(),",
             "                punishment.issuer(),\n                punishment.intent().type(),\n                punishment.intent().publicReason(),")

# 8) Service returns/accepts canonical case IDs and passes full Actor metadata into persistence.
service = "staff-bot/src/main/java/net/enthusia/staff/discordbot/DiscordInvestigationService.java"
replace_once(service,
             "import java.util.UUID;\nimport java.util.function.BiFunction;",
             "import java.util.UUID;\nimport java.util.function.BiFunction;\nimport net.enthusia.staff.common.CaseId;")
replace_once(service, "    record CaseResult(UUID caseId, long revision, boolean replayed) {", "    record CaseResult(CaseId caseId, long revision, boolean replayed) {")
replace_once(service, "    record EvidenceResult(UUID evidenceId, UUID caseId, long revision, boolean replayed) {", "    record EvidenceResult(UUID evidenceId, CaseId caseId, long revision, boolean replayed) {")
replace_once(service,
             "        UUID caseId = deterministicId(operationKey);\n        InvestigationCase stored = store.createInvestigationCase(new DiscordInvestigationStore.InvestigationCaseDraft(\n                caseId, operationKey, context.subjectId(), Optional.empty(), summary,\n                context.actor().id(), clock.instant()\n        ));",
             "        InvestigationCase stored = store.createInvestigationCase(new DiscordInvestigationStore.InvestigationCaseDraft(\n                operationKey, context.subjectId(), summary, context.actor(), clock.instant()\n        ));")
replace_once(service,
             "                deterministicId(operationKey), operationKey, context.subjectId(), Optional.empty(),\n                \"Discord message-context moderation evidence\", context.actor().id(), now",
             "                operationKey, context.subjectId(),\n                \"Discord message-context moderation evidence\", context.actor(), now")
replace_once(service,
             "        UUID caseId = uuid(scopeValue, \"case note scope is invalid\");",
             "        CaseId caseId = caseId(scopeValue);")
replace_once(service,
             "        return new InvestigationNote.Scope(InvestigationNote.ScopeType.CASE, caseId.toString());",
             "        return new InvestigationNote.Scope(InvestigationNote.ScopeType.CASE, caseId.value());")
insert_after = "    private static UUID uuid(String value, String message) {\n        try {\n            return UUID.fromString(value);\n        } catch (RuntimeException exception) {\n            throw new IllegalArgumentException(message, exception);\n        }\n    }\n"
case_helper = insert_after + "\n    private static CaseId caseId(String value) {\n        try {\n            return new CaseId(value);\n        } catch (RuntimeException exception) {\n            throw new IllegalArgumentException(\"case note scope is invalid\", exception);\n        }\n    }\n"
replace_once(service, insert_after, case_helper)

# 9) Worker maps D07 observations with full provenance.
worker = "staff-bot/src/main/java/net/enthusia/staff/discordbot/DiscordInvestigationWorker.java"
replace_once(worker,
             "                    observation.issuerId(),\n                    observation.summary(),",
             "                    observation.issuer(),\n                    observation.consequenceType(),\n                    observation.summary(),")

# 10) Command wording no longer describes a case UUID.
controller = "staff-bot/src/main/java/net/enthusia/staff/discordbot/DiscordInvestigationCommandController.java"
replace_once(controller,
             'stringOption(SCOPE_ID, "Minecraft UUID or investigation case UUID when required", false)',
             'stringOption(SCOPE_ID, "Minecraft UUID or canonical case ID when required", false)')

# 11) Store facade uses CaseId.
facade = "persistence/src/main/java/net/enthusia/staff/persistence/JdbcDiscordInvestigationStore.java"
replace_once(facade,
             "import javax.sql.DataSource;\nimport net.enthusia.staff.domain.investigation.EvasionAlert;",
             "import javax.sql.DataSource;\nimport net.enthusia.staff.common.CaseId;\nimport net.enthusia.staff.domain.investigation.EvasionAlert;")
replace_once(facade, "    public Optional<InvestigationCase> findCase(UUID caseId) {", "    public Optional<InvestigationCase> findCase(CaseId caseId) {")

print("D09 authority phase 1 transformations applied")
