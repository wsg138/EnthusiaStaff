package net.enthusia.staff.domain.ports;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
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

    Optional<InvestigationCase> findCase(UUID caseId);

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
            UUID issuerId,
            String summary,
            DiscordPunishmentState state,
            Optional<Instant> expiresAt,
            long punishmentRevision,
            Instant observedAt
    ) {
        public PunishmentCaseDraft {
            if (blank(operationKey) || operationKey.length() > 128 || punishmentId == null || subjectId == null
                    || issuerId == null || blank(summary) || state == null || expiresAt == null
                    || punishmentRevision < 0 || observedAt == null) {
                throw new IllegalArgumentException("punishment case draft fields are invalid");
            }
        }
    }

    record InvestigationCaseDraft(
            UUID caseId,
            String operationKey,
            ModerationSubjectId subjectId,
            Optional<String> legacyCaseId,
            String summary,
            UUID openedBy,
            Instant openedAt
    ) {
        public InvestigationCaseDraft {
            if (caseId == null || blank(operationKey) || operationKey.length() > 128 || subjectId == null
                    || legacyCaseId == null || blank(summary) || openedBy == null || openedAt == null) {
                throw new IllegalArgumentException("investigation case draft fields are invalid");
            }
        }
    }

    record CaseActivity(UUID caseId, long expectedRevision, Instant occurredAt) {
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
            UUID issuerId,
            String summary,
            DiscordPunishmentState state,
            Optional<Instant> expiresAt,
            long revision,
            Instant observedAt
    ) {
        public PunishmentObservation {
            if (punishmentId == null || subjectId == null || issuerId == null || blank(summary) || state == null
                    || expiresAt == null || revision < 0 || observedAt == null) {
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
