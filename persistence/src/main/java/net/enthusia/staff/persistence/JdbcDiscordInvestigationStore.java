package net.enthusia.staff.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;
import net.enthusia.staff.common.CaseId;
import net.enthusia.staff.domain.investigation.EvasionAlert;
import net.enthusia.staff.domain.investigation.InvestigationCase;
import net.enthusia.staff.domain.investigation.InvestigationEvidence;
import net.enthusia.staff.domain.investigation.InvestigationNote;
import net.enthusia.staff.domain.ports.DiscordInvestigationStore;

/** V22-backed authoritative D09 investigation repository. */
public final class JdbcDiscordInvestigationStore implements DiscordInvestigationStore {
    private final JdbcDiscordInvestigationCaseStore cases;
    private final JdbcDiscordInvestigationNoteStore notes;
    private final JdbcDiscordInvestigationEvidenceStore evidence;
    private final JdbcDiscordEvasionAlertStore alerts;
    private final JdbcDiscordInvestigationSource sources;

    public JdbcDiscordInvestigationStore(DataSource dataSource) {
        if (dataSource == null) {
            throw new IllegalArgumentException("dataSource must be present");
        }
        this.cases = new JdbcDiscordInvestigationCaseStore(dataSource);
        this.notes = new JdbcDiscordInvestigationNoteStore(dataSource);
        this.evidence = new JdbcDiscordInvestigationEvidenceStore(dataSource);
        this.alerts = new JdbcDiscordEvasionAlertStore(dataSource);
        this.sources = new JdbcDiscordInvestigationSource(dataSource);
    }

    @Override
    public InvestigationCase ensurePunishmentCase(PunishmentCaseDraft draft) {
        require(draft, "punishment case draft");
        return cases.ensurePunishmentCase(draft);
    }

    @Override
    public InvestigationCase createInvestigationCase(InvestigationCaseDraft draft) {
        require(draft, "investigation case draft");
        return cases.createInvestigationCase(draft);
    }

    @Override
    public Optional<InvestigationCase> findCase(CaseId caseId) {
        return cases.findCase(caseId);
    }

    @Override
    public InvestigationCase touchCase(CaseActivity activity) {
        require(activity, "case activity");
        return cases.touchCase(activity);
    }

    @Override
    public int closeInactiveCases(Instant inactivityCutoff, Instant now, int limit) {
        return cases.closeInactiveCases(inactivityCutoff, now, limit);
    }

    @Override
    public InvestigationNote createNote(NoteDraft draft) {
        require(draft, "note draft");
        return notes.create(draft);
    }

    @Override
    public InvestigationNote editNote(NoteEdit edit) {
        require(edit, "note edit");
        return notes.edit(edit);
    }

    @Override
    public Optional<InvestigationNote> findNote(UUID noteId) {
        return notes.find(noteId);
    }

    @Override
    public List<InvestigationNote.Version> noteHistory(UUID noteId, int limit) {
        return notes.history(noteId, limit);
    }

    @Override
    public InvestigationEvidence.Stored captureEvidence(InvestigationEvidence.Capture capture) {
        require(capture, "evidence capture");
        return evidence.capture(capture);
    }

    @Override
    public InvestigationEvidence.Stored recordEvidenceEdit(InvestigationEvidence.Edit edit) {
        require(edit, "evidence edit");
        return evidence.recordEdit(edit);
    }

    @Override
    public int captureMoreContext(InvestigationEvidence.ContextBatch batch) {
        require(batch, "context batch");
        return evidence.captureMoreContext(batch);
    }

    @Override
    public Optional<InvestigationEvidence.Stored> findEvidenceByMessage(
            String guildId,
            String channelId,
            String messageId
    ) {
        return evidence.findByMessage(guildId, channelId, messageId);
    }

    @Override
    public int purgeEligibleEvidence(Instant now, int limit) {
        return evidence.purgeEligible(now, limit);
    }

    @Override
    public EvasionAlert createEvasionAlert(EvasionAlertDraft draft) {
        require(draft, "evasion alert draft");
        return alerts.create(draft);
    }

    @Override
    public Optional<EvasionAlert> findEvasionAlert(UUID alertId) {
        return alerts.find(alertId);
    }

    @Override
    public List<EvasionAlert> pendingEvasionAlerts(Instant now, int limit) {
        return alerts.pending(now, limit);
    }

    @Override
    public EvasionAlert updateEvasionDelivery(EvasionDeliveryUpdate update) {
        require(update, "evasion delivery update");
        return alerts.updateDelivery(update);
    }

    @Override
    public EvasionAlert resolveEvasionAlert(UUID alertId, long expectedRevision, Instant now) {
        return alerts.resolve(alertId, expectedRevision, now);
    }

    @Override
    public List<PunishmentObservation> punishmentObservations(int limit) {
        return sources.punishmentObservations(limit);
    }

    @Override
    public List<EvasionCandidate> evasionCandidates(int limit) {
        return sources.evasionCandidates(limit);
    }

    private static void require(Object value, String field) {
        if (value == null) {
            throw new IllegalArgumentException(field + " must be present");
        }
    }
}
