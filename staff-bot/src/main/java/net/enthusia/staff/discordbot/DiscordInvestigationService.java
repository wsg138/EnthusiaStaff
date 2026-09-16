package net.enthusia.staff.discordbot;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiFunction;
import net.enthusia.staff.common.CaseId;
import net.enthusia.staff.domain.auth.Actor;
import net.enthusia.staff.domain.auth.DiscordModerationOperation;
import net.enthusia.staff.domain.investigation.EvasionAlert;
import net.enthusia.staff.domain.investigation.InvestigationCase;
import net.enthusia.staff.domain.investigation.InvestigationEvidence;
import net.enthusia.staff.domain.investigation.InvestigationNote;
import net.enthusia.staff.domain.moderation.DiscordUserId;
import net.enthusia.staff.domain.moderation.ModerationSubjectId;
import net.enthusia.staff.domain.ports.DiscordInvestigationStore;

/** D09 application orchestration; persistence, acquisition, authorization, and projections stay separate. */
final class DiscordInvestigationService {
    private static final int MAX_CASE_SUMMARY = 512;
    private static final int MAX_OPERATION_TOKEN = 64;
    private static final long INVALID_DISCORD_USER_ID = 0L;

    record CaseResult(CaseId caseId, long revision, boolean replayed) {
    }

    record NoteResult(UUID noteId, long revision, InvestigationNote.Visibility visibility, boolean replayed) {
    }

    record EvidenceResult(UUID evidenceId, CaseId caseId, long revision, boolean replayed) {
    }

    record AlertResult(UUID alertId, long revision, boolean replayed) {
    }

    private final DiscordInvestigationStore store;
    private final StaffModerationReadService reads;
    private final LinkedStaffActorResolver actors;
    private final DiscordInvestigationAuthorization authorization;
    private final BiFunction<DiscordUserId, Instant, ModerationSubjectId> subjects;
    private final Clock clock;

    DiscordInvestigationService(
            DiscordInvestigationStore store,
            StaffModerationReadService reads,
            LinkedStaffActorResolver actors,
            DiscordInvestigationAuthorization authorization,
            BiFunction<DiscordUserId, Instant, ModerationSubjectId> subjects,
            Clock clock
    ) {
        if (store == null || reads == null || actors == null || authorization == null || subjects == null || clock == null) {
            throw new IllegalArgumentException("investigation service dependencies must be present");
        }
        this.store = store;
        this.reads = reads;
        this.actors = actors;
        this.authorization = authorization;
        this.subjects = subjects;
        this.clock = clock;
    }

    CaseResult createCase(
            long actorDiscordId,
            String actorName,
            long targetDiscordId,
            String summary,
            String operationToken
    ) {
        requireSummary(summary);
        TargetContext context = authorize(actorDiscordId, actorName, targetDiscordId,
                DiscordModerationOperation.CREATE_INVESTIGATION_CASE);
        String operationKey = operation("case", operationToken);
        InvestigationCase stored = store.createInvestigationCase(new DiscordInvestigationStore.InvestigationCaseDraft(
                operationKey, context.subjectId(), summary, context.actor(), clock.instant()
        ));
        return project(stored);
    }

    NoteResult addNote(
            long actorDiscordId,
            String actorName,
            long targetDiscordId,
            InvestigationNote.ScopeType scopeType,
            String scopeValue,
            InvestigationNote.Visibility visibility,
            String text,
            String operationToken
    ) {
        TargetContext context = authorize(actorDiscordId, actorName, targetDiscordId, DiscordModerationOperation.ADD_NOTE);
        authorization.requireVisibility(context.actor(), visibility);
        InvestigationNote.Scope scope = noteScope(context, targetDiscordId, scopeType, scopeValue);
        String operationKey = operation("note", operationToken);
        InvestigationNote note = store.createNote(new DiscordInvestigationStore.NoteDraft(
                deterministicId(operationKey), operationKey, context.subjectId(), scope, visibility,
                text, context.actor().id(), clock.instant()
        ));
        return project(note);
    }

    NoteResult editNote(
            long actorDiscordId,
            String actorName,
            long targetDiscordId,
            UUID noteId,
            long expectedRevision,
            String text,
            String operationToken
    ) {
        if (noteId == null) {
            throw new IllegalArgumentException("noteId must be present");
        }
        TargetContext context = authorize(actorDiscordId, actorName, targetDiscordId, DiscordModerationOperation.EDIT_NOTE);
        InvestigationNote current = store.findNote(noteId)
                .orElseThrow(() -> new IllegalStateException("private note does not exist"));
        requireSubject(current.subjectId(), context.subjectId(), "private note");
        authorization.requireVisibility(context.actor(), current.visibility());
        InvestigationNote note = store.editNote(new DiscordInvestigationStore.NoteEdit(
                noteId, operation("note-edit", operationToken), context.subjectId(), expectedRevision,
                text, context.actor().id(), clock.instant()
        ));
        return project(note);
    }

    EvidenceResult captureMessage(
            long actorDiscordId,
            String actorName,
            long targetDiscordId,
            JdaDiscordEvidenceCollector.Initial initial,
            String operationToken
    ) {
        if (initial == null || !initial.focus().authorUserId().value().equals(Long.toUnsignedString(targetDiscordId))) {
            throw new IllegalArgumentException("message evidence target does not match the focus author");
        }
        TargetContext context = authorize(actorDiscordId, actorName, targetDiscordId,
                DiscordModerationOperation.CAPTURE_EVIDENCE);
        Instant now = clock.instant();
        InvestigationCase investigationCase = ensureMessageCase(context, operationToken, now);
        String operationKey = operation("evidence", operationToken);
        InvestigationEvidence.Stored evidence = store.captureEvidence(new InvestigationEvidence.Capture(
                deterministicId(operationKey), operationKey, context.subjectId(), investigationCase.caseId(),
                initial.focus(), initial.before(), initial.after(), context.actor().id(), "MESSAGE_CONTEXT", now
        ));
        return project(evidence);
    }

    int captureMoreContext(
            long actorDiscordId,
            String actorName,
            long targetDiscordId,
            InvestigationEvidence.Message focus,
            List<InvestigationEvidence.Message> additional,
            String operationToken
    ) {
        if (focus == null || additional == null) {
            throw new IllegalArgumentException("evidence context inputs must be present");
        }
        if (additional.isEmpty()) {
            return 0;
        }
        TargetContext context = authorize(actorDiscordId, actorName, targetDiscordId,
                DiscordModerationOperation.CAPTURE_EVIDENCE);
        InvestigationEvidence.Stored evidence = store.findEvidenceByMessage(
                focus.guildId(), focus.channelId(), focus.messageId()
        ).orElseThrow(() -> new IllegalStateException("captured message evidence does not exist"));
        requireSubject(evidence.subjectId(), context.subjectId(), "message evidence");
        return store.captureMoreContext(new InvestigationEvidence.ContextBatch(
                evidence.evidenceId(), operation("context", operationToken), additional, clock.instant()
        ));
    }

    boolean recordCapturedMessageEdit(InvestigationEvidence.Message message) {
        if (message == null) {
            throw new IllegalArgumentException("edited message must be present");
        }
        Optional<InvestigationEvidence.Stored> stored = store.findEvidenceByMessage(
                message.guildId(), message.channelId(), message.messageId()
        );
        if (stored.isEmpty()) {
            return false;
        }
        InvestigationEvidence.Stored evidence = stored.orElseThrow();
        String operationKey = "d09:edit:" + deterministicId(editFingerprint(message));
        store.recordEvidenceEdit(new InvestigationEvidence.Edit(
                evidence.evidenceId(), operationKey, message, clock.instant()
        ));
        return true;
    }

    AlertResult resolveAlert(
            long actorDiscordId,
            String actorName,
            long targetDiscordId,
            UUID alertId,
            long expectedRevision
    ) {
        TargetContext context = authorizeAlert(actorDiscordId, actorName, targetDiscordId, alertId);
        return resolve(context, alertId, expectedRevision);
    }

    AlertResult resolveAlert(
            long actorDiscordId,
            String actorName,
            long targetDiscordId,
            UUID alertId
    ) {
        TargetContext context = authorizeAlert(actorDiscordId, actorName, targetDiscordId, alertId);
        EvasionAlert current = requireAlert(context, alertId);
        return resolve(context, alertId, current.revision());
    }

    private TargetContext authorizeAlert(
            long actorDiscordId,
            String actorName,
            long targetDiscordId,
            UUID alertId
    ) {
        if (alertId == null) {
            throw new IllegalArgumentException("alertId must be present");
        }
        return authorize(actorDiscordId, actorName, targetDiscordId,
                DiscordModerationOperation.RESOLVE_EVASION_ALERT);
    }

    private AlertResult resolve(TargetContext context, UUID alertId, long expectedRevision) {
        EvasionAlert alert = requireAlert(context, alertId);
        EvasionAlert resolved = store.resolveEvasionAlert(alertId, expectedRevision, clock.instant());
        return new AlertResult(resolved.alertId(), resolved.revision(), resolved.replayed());
    }

    private EvasionAlert requireAlert(TargetContext context, UUID alertId) {
        EvasionAlert alert = store.findEvasionAlert(alertId)
                .orElseThrow(() -> new IllegalStateException("linked-alt alert does not exist"));
        requireSubject(alert.subjectId(), context.subjectId(), "linked-alt alert");
        return alert;
    }

    private InvestigationCase ensureMessageCase(TargetContext context, String operationToken, Instant now) {
        String operationKey = operation("message-case", operationToken);
        return store.createInvestigationCase(new DiscordInvestigationStore.InvestigationCaseDraft(
                operationKey, context.subjectId(),
                "Discord message-context moderation evidence", context.actor(), now
        ));
    }

    private TargetContext authorize(
            long actorDiscordId,
            String actorName,
            long targetDiscordId,
            DiscordModerationOperation operation
    ) {
        DiscordUserId targetUser = discordUser(targetDiscordId);
        StaffModerationReadService.Target target = reads.discordTarget(targetUser);
        Actor actor = actors.invoker(discordUser(actorDiscordId), actorName);
        authorization.require(actor, actors.targetStaff(target), operation);
        ModerationSubjectId subjectId = target.subject()
                .map(value -> value.subject().subjectId())
                .orElseGet(() -> subjects.apply(targetUser, clock.instant()));
        return new TargetContext(actor, target, subjectId);
    }

    private InvestigationNote.Scope noteScope(
            TargetContext context,
            long targetDiscordId,
            InvestigationNote.ScopeType scopeType,
            String scopeValue
    ) {
        if (scopeType == null) {
            throw new IllegalArgumentException("note scope type must be present");
        }
        return switch (scopeType) {
            case SUBJECT -> new InvestigationNote.Scope(scopeType, context.subjectId().value().toString());
            case DISCORD_USER -> new InvestigationNote.Scope(scopeType, Long.toUnsignedString(targetDiscordId));
            case MINECRAFT_PLAYER -> minecraftScope(context, scopeValue);
            case CASE -> caseScope(context.subjectId(), scopeValue);
        };
    }

    private static InvestigationNote.Scope minecraftScope(TargetContext context, String scopeValue) {
        UUID playerId = uuid(scopeValue, "Minecraft note scope is invalid");
        boolean linked = context.target().subject().stream()
                .flatMap(value -> value.subject().minecraftAccountIds().stream())
                .anyMatch(playerId::equals);
        if (!linked) {
            throw new IllegalArgumentException("Minecraft note scope is not linked to the target");
        }
        return new InvestigationNote.Scope(InvestigationNote.ScopeType.MINECRAFT_PLAYER, playerId.toString());
    }

    private InvestigationNote.Scope caseScope(ModerationSubjectId subjectId, String scopeValue) {
        CaseId caseId = caseId(scopeValue);
        InvestigationCase investigationCase = store.findCase(caseId)
                .orElseThrow(() -> new IllegalStateException("investigation case does not exist"));
        requireSubject(investigationCase.subjectId(), subjectId, "investigation case");
        return new InvestigationNote.Scope(InvestigationNote.ScopeType.CASE, caseId.value());
    }

    private static UUID uuid(String value, String message) {
        try {
            return UUID.fromString(value);
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException(message, exception);
        }
    }

    private static CaseId caseId(String value) {
        try {
            return new CaseId(value);
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("case note scope is invalid", exception);
        }
    }

    private static void requireSubject(ModerationSubjectId actual, ModerationSubjectId expected, String resource) {
        if (!actual.equals(expected)) {
            throw new IllegalStateException(resource + " does not belong to the requested moderation subject");
        }
    }

    private static String operation(String kind, String token) {
        if (token == null || token.isBlank() || token.length() > MAX_OPERATION_TOKEN) {
            throw new IllegalArgumentException("investigation operation token is invalid");
        }
        return "d09:" + kind + ':' + token;
    }

    private static void requireSummary(String summary) {
        if (summary == null || summary.isBlank() || summary.length() > MAX_CASE_SUMMARY) {
            throw new IllegalArgumentException("case summary must be nonblank and at most 512 characters");
        }
    }

    private static DiscordUserId discordUser(long id) {
        if (id == INVALID_DISCORD_USER_ID) {
            throw new IllegalArgumentException("Discord user id must be positive");
        }
        return new DiscordUserId(Long.toUnsignedString(id));
    }

    private static String editFingerprint(InvestigationEvidence.Message message) {
        return message.guildId() + '\n' + message.channelId() + '\n' + message.messageId() + '\n'
                + message.editedAt().map(Instant::toString).orElse("unedited") + '\n' + message.content();
    }

    private static UUID deterministicId(String value) {
        return UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8));
    }

    private static CaseResult project(InvestigationCase value) {
        return new CaseResult(value.caseId(), value.revision(), value.replayed());
    }

    private static NoteResult project(InvestigationNote value) {
        return new NoteResult(value.noteId(), value.revision(), value.visibility(), value.replayed());
    }

    private static EvidenceResult project(InvestigationEvidence.Stored value) {
        return new EvidenceResult(value.evidenceId(), value.caseId(), value.revision(), value.replayed());
    }

    private record TargetContext(
            Actor actor,
            StaffModerationReadService.Target target,
            ModerationSubjectId subjectId
    ) {
    }
}
