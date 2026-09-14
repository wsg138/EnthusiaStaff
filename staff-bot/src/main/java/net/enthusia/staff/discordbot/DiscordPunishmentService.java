package net.enthusia.staff.discordbot;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiFunction;
import net.enthusia.staff.domain.auth.Actor;
import net.enthusia.staff.domain.auth.DiscordAuthorizationSnapshot;
import net.enthusia.staff.domain.auth.DiscordConsequenceType;
import net.enthusia.staff.domain.auth.DiscordModerationOperation;
import net.enthusia.staff.domain.discord.DiscordPunishment;
import net.enthusia.staff.domain.discord.DiscordPunishmentIntent;
import net.enthusia.staff.domain.discord.DiscordPunishmentState;
import net.enthusia.staff.domain.discord.DiscordPunishmentTermination;
import net.enthusia.staff.domain.moderation.DiscordGuildId;
import net.enthusia.staff.domain.moderation.DiscordUserId;
import net.enthusia.staff.domain.moderation.ModerationSubjectId;
import net.enthusia.staff.domain.ports.DiscordPunishmentRepository;
import net.enthusia.staff.domain.ports.DiscordPunishmentRepository.StoredPunishment;
import net.enthusia.staff.domain.ports.DiscordPunishmentRepository.WorkSchedule;
import net.enthusia.staff.domain.ports.DiscordPunishmentRepository.WorkType;

/** D07 orchestration: validation/authorization first, durable intent second, Discord effects only in the worker. */
final class DiscordPunishmentService {
    private static final int ACTIVE_LOOKUP_LIMIT = 20;

    record Confirmation(UUID token, DiscordConsequenceType type, String targetUserId, String duration) {
    }

    record MutationResult(UUID punishmentId, DiscordPunishmentState state, boolean replayed) {
    }

    private final StaffModerationReadService reads;
    private final LinkedStaffActorResolver actors;
    private final DiscordPunishmentAuthorization authorization;
    private final DiscordPunishmentConfirmationStore confirmations;
    private final DiscordPunishmentRepository punishments;
    private final BiFunction<DiscordUserId, Instant, ModerationSubjectId> subjects;
    private final DiscordPunishmentGateway gateway;
    private final DiscordGuildId guildId;
    private final Clock clock;

    DiscordPunishmentService(
            StaffModerationReadService reads,
            LinkedStaffActorResolver actors,
            DiscordPunishmentAuthorization authorization,
            DiscordPunishmentConfirmationStore confirmations,
            DiscordPunishmentRepository punishments,
            BiFunction<DiscordUserId, Instant, ModerationSubjectId> subjects,
            DiscordPunishmentGateway gateway,
            DiscordGuildId guildId,
            Clock clock
    ) {
        if (reads == null || actors == null || authorization == null || confirmations == null || punishments == null
                || subjects == null || gateway == null || guildId == null || clock == null) {
            throw new IllegalArgumentException("Discord punishment service dependencies must be present");
        }
        this.reads = reads;
        this.actors = actors;
        this.authorization = authorization;
        this.confirmations = confirmations;
        this.punishments = punishments;
        this.subjects = subjects;
        this.gateway = gateway;
        this.guildId = guildId;
        this.clock = clock;
    }

    Confirmation prepareIssue(long actorDiscordId, String actorName, long targetDiscordId, DiscordPunishmentIntent intent) {
        DiscordUserId targetUserId = new DiscordUserId(Long.toUnsignedString(targetDiscordId));
        StaffModerationReadService.Target target = reads.discordTarget(targetUserId);
        Actor actor = actors.invoker(new DiscordUserId(Long.toUnsignedString(actorDiscordId)), actorName);
        Optional<Actor> targetStaff = actors.targetStaff(target);
        DiscordAuthorizationSnapshot snapshot = authorization.captureIssue(actor, targetStaff, intent);
        gateway.preflight(guildId, targetUserId, intent);
        UUID token = confirmations.put(expires -> new DiscordPunishmentConfirmationStore.Draft(
                DiscordPunishmentConfirmationStore.Kind.ISSUE,
                targetUserId,
                Optional.empty(),
                Optional.of(intent),
                Optional.empty(),
                DiscordPunishmentTermination.NONE,
                snapshot,
                expires
        ));
        return new Confirmation(token, intent.type(), targetUserId.value(), durationLabel(intent));
    }

    MutationResult confirmIssue(long actorDiscordId, String actorName, UUID token) {
        DiscordPunishmentConfirmationStore.Draft draft = confirmations.claim(token);
        if (draft.kind() != DiscordPunishmentConfirmationStore.Kind.ISSUE) {
            throw new IllegalArgumentException("confirmation does not issue a punishment");
        }
        StaffModerationReadService.Target target = reads.discordTarget(draft.targetUserId());
        Actor actor = actors.invoker(new DiscordUserId(Long.toUnsignedString(actorDiscordId)), actorName);
        Optional<Actor> targetStaff = actors.targetStaff(target);
        authorization.reauthorize(draft.authorization(), actor, targetStaff);
        DiscordPunishmentIntent intent = draft.intent().orElseThrow();
        gateway.preflight(guildId, draft.targetUserId(), intent);
        Instant now = clock.instant();
        ModerationSubjectId subjectId = subjects.apply(draft.targetUserId(), now);
        UUID punishmentId = UUID.randomUUID();
        String operationKey = "d07:issue:" + punishmentId;
        DiscordPunishment punishment = DiscordPunishment.pending(
                punishmentId, subjectId, draft.targetUserId(), guildId, actor, intent, now, operationKey
        );
        StoredPunishment stored = punishments.create(punishment, operationKey, now);
        return project(stored);
    }

    Confirmation prepareRemoval(
            long actorDiscordId,
            String actorName,
            long targetDiscordId,
            DiscordConsequenceType type,
            DiscordPunishmentTermination termination
    ) {
        requireRemovable(type, termination);
        DiscordUserId targetUserId = new DiscordUserId(Long.toUnsignedString(targetDiscordId));
        StoredPunishment active = newestActive(targetUserId, type);
        StaffModerationReadService.Target target = reads.discordTarget(targetUserId);
        Actor actor = actors.invoker(new DiscordUserId(Long.toUnsignedString(actorDiscordId)), actorName);
        Optional<Actor> targetStaff = actors.targetStaff(target);
        DiscordAuthorizationSnapshot snapshot = authorization.captureMutation(
                actor, targetStaff, operationFor(termination)
        );
        UUID token = confirmations.put(expires -> new DiscordPunishmentConfirmationStore.Draft(
                DiscordPunishmentConfirmationStore.Kind.REMOVE,
                targetUserId,
                Optional.of(active.punishment().punishmentId()),
                Optional.empty(),
                Optional.of(type),
                termination,
                snapshot,
                expires
        ));
        return new Confirmation(token, type, targetUserId.value(), "remove");
    }

    MutationResult confirmRemoval(long actorDiscordId, String actorName, UUID token) {
        DiscordPunishmentConfirmationStore.Draft draft = confirmations.claim(token);
        if (draft.kind() != DiscordPunishmentConfirmationStore.Kind.REMOVE) {
            throw new IllegalArgumentException("confirmation does not remove a punishment");
        }
        StaffModerationReadService.Target target = reads.discordTarget(draft.targetUserId());
        Actor actor = actors.invoker(new DiscordUserId(Long.toUnsignedString(actorDiscordId)), actorName);
        authorization.reauthorize(draft.authorization(), actor, actors.targetStaff(target));
        UUID punishmentId = draft.punishmentId().orElseThrow();
        StoredPunishment current = punishments.find(punishmentId)
                .orElseThrow(() -> new IllegalStateException("punishment disappeared before confirmation"));
        if (current.punishment().state().terminal()) {
            return project(current);
        }
        String operationKey = "d07:remove:" + punishmentId + ":" + UUID.randomUUID();
        DiscordPunishment replacement = current.punishment().requestRemoval(draft.termination(), operationKey);
        StoredPunishment stored = punishments.transition(
                current,
                replacement,
                operationKey,
                List.of(new WorkSchedule(WorkType.REMOVE, clock.instant())),
                clock.instant()
        );
        return project(stored);
    }

    void requireConcreteApproval(
            long approverDiscordId,
            String approverName,
            long targetDiscordId,
            DiscordPunishmentIntent intent
    ) {
        DiscordUserId targetUserId = new DiscordUserId(Long.toUnsignedString(targetDiscordId));
        StaffModerationReadService.Target target = reads.discordTarget(targetUserId);
        Actor approver = actors.invoker(new DiscordUserId(Long.toUnsignedString(approverDiscordId)), approverName);
        authorization.requireApprovalOfConcreteSanction(approver, actors.targetStaff(target), intent);
    }

    private StoredPunishment newestActive(DiscordUserId targetUserId, DiscordConsequenceType type) {
        return punishments.activeForTarget(guildId, targetUserId, type, ACTIVE_LOOKUP_LIMIT).stream()
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("no active Discord punishment matches the target and type"));
    }

    private static void requireRemovable(
            DiscordConsequenceType type,
            DiscordPunishmentTermination termination
    ) {
        if (type == null || termination == null || termination == DiscordPunishmentTermination.NONE
                || termination == DiscordPunishmentTermination.EXPIRE
                || (type != DiscordConsequenceType.MUTE
                    && type != DiscordConsequenceType.BAN
                    && type != DiscordConsequenceType.CHANNEL_RESTRICTION)) {
            throw new IllegalArgumentException("interactive removal request is invalid");
        }
    }

    private static DiscordModerationOperation operationFor(DiscordPunishmentTermination termination) {
        return switch (termination) {
            case END -> DiscordModerationOperation.END_SANCTION;
            case REVOKE -> DiscordModerationOperation.REVOKE_SANCTION;
            case OVERTURN -> DiscordModerationOperation.FULL_OVERTURN;
            case NONE, EXPIRE -> throw new IllegalArgumentException("termination has no interactive operation");
        };
    }

    private static MutationResult project(StoredPunishment stored) {
        return new MutationResult(
                stored.punishment().punishmentId(),
                stored.punishment().state(),
                stored.replayed()
        );
    }

    private static String durationLabel(DiscordPunishmentIntent intent) {
        return switch (intent.length().kind()) {
            case INSTANT -> "instant";
            case PERMANENT -> "permanent";
            case TEMPORARY -> intent.length().temporary().orElseThrow().toString();
        };
    }
}
