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
import net.enthusia.staff.domain.auth.StaffRank;
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
    private static final int ACTIVE_LOOKUP_LIMIT = 500;
    private static final int EXACT_RESTRICTION_MATCH_COUNT = 1;
    private static final long INVALID_SNOWFLAKE = 0L;

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
        DiscordUserId targetUserId = discordUser(targetDiscordId);
        StaffModerationReadService.Target target = reads.discordTarget(targetUserId);
        Actor actor = actor(actorDiscordId, actorName);
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
        Actor actor = actor(actorDiscordId, actorName);
        DiscordPunishmentConfirmationStore.Draft draft = confirmations.claimForActor(token, actor.id());
        if (draft.kind() != DiscordPunishmentConfirmationStore.Kind.ISSUE) {
            throw new IllegalArgumentException("confirmation does not issue a punishment");
        }
        StaffModerationReadService.Target target = reads.discordTarget(draft.targetUserId());
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
        requireGenericRemovable(type, termination);
        DiscordUserId targetUserId = discordUser(targetDiscordId);
        StoredPunishment active = newestInteractiveRemoval(targetUserId, type);
        return prepareRemovalConfirmation(
                actorDiscordId, actorName, targetUserId, active, type, termination
        );
    }

    Confirmation prepareRestrictionRemoval(
            long actorDiscordId,
            String actorName,
            long targetDiscordId,
            String scopeId,
            DiscordPunishmentTermination termination
    ) {
        requireInteractiveTermination(termination);
        DiscordUserId targetUserId = discordUser(targetDiscordId);
        StoredPunishment active = exactActiveRestriction(targetUserId, scopeId);
        return prepareRemovalConfirmation(
                actorDiscordId,
                actorName,
                targetUserId,
                active,
                DiscordConsequenceType.CHANNEL_RESTRICTION,
                termination
        );
    }

    private Confirmation prepareRemovalConfirmation(
            long actorDiscordId,
            String actorName,
            DiscordUserId targetUserId,
            StoredPunishment active,
            DiscordConsequenceType type,
            DiscordPunishmentTermination termination
    ) {
        StaffModerationReadService.Target target = reads.discordTarget(targetUserId);
        Actor actor = actor(actorDiscordId, actorName);
        DiscordAuthorizationSnapshot snapshot = authorization.captureMutation(
                actor, actors.targetStaff(target), operationFor(termination)
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
        Actor actor = actor(actorDiscordId, actorName);
        DiscordPunishmentConfirmationStore.Draft draft = confirmations.claimForActor(token, actor.id());
        if (draft.kind() != DiscordPunishmentConfirmationStore.Kind.REMOVE) {
            throw new IllegalArgumentException("confirmation does not remove a punishment");
        }
        StaffModerationReadService.Target target = reads.discordTarget(draft.targetUserId());
        authorization.reauthorize(draft.authorization(), actor, actors.targetStaff(target));
        UUID punishmentId = draft.punishmentId().orElseThrow();
        StoredPunishment current = punishments.find(punishmentId)
                .orElseThrow(() -> new IllegalStateException("punishment disappeared before confirmation"));
        if (current.punishment().state().terminal()) {
            return project(current);
        }
        requireInteractiveRemovalCandidate(current);
        Instant now = clock.instant();
        String operationKey = "d07:remove:" + punishmentId + ":" + UUID.randomUUID();
        DiscordPunishment replacement = current.punishment().requestRemoval(draft.termination(), operationKey);
        StoredPunishment stored = punishments.transition(
                current,
                replacement,
                operationKey,
                List.of(new WorkSchedule(WorkType.REMOVE, now)),
                now
        );
        return project(stored);
    }

    void requireConcreteApproval(
            long requesterDiscordId,
            String requesterName,
            long approverDiscordId,
            String approverName,
            long targetDiscordId,
            StaffRank requiredApprovalRank,
            DiscordPunishmentIntent intent
    ) {
        DiscordUserId targetUserId = discordUser(targetDiscordId);
        StaffModerationReadService.Target target = reads.discordTarget(targetUserId);
        Actor requester = actor(requesterDiscordId, requesterName);
        Actor approver = actor(approverDiscordId, approverName);
        authorization.requireApprovalOfConcreteSanction(
                requester,
                approver,
                actors.targetStaff(target),
                requiredApprovalRank,
                intent
        );
    }

    private StoredPunishment exactActiveRestriction(DiscordUserId targetUserId, String scopeId) {
        List<StoredPunishment> active = punishments.activeForTarget(
                guildId,
                targetUserId,
                DiscordConsequenceType.CHANNEL_RESTRICTION,
                ACTIVE_LOOKUP_LIMIT
        );
        return selectExactRestriction(active, scopeId);
    }

    static StoredPunishment selectExactRestriction(List<StoredPunishment> active, String scopeId) {
        if (active == null) {
            throw new IllegalArgumentException("active restrictions must be present");
        }
        String normalizedScope = normalizeSnowflake(scopeId);
        List<StoredPunishment> matches = active.stream()
                .filter(DiscordPunishmentService::interactiveRemovalCandidate)
                .filter(stored -> stored.punishment().intent().type() == DiscordConsequenceType.CHANNEL_RESTRICTION)
                .filter(stored -> stored.punishment().intent().restriction()
                        .map(restriction -> restriction.snowflake().equals(normalizedScope))
                        .orElse(false))
                .toList();
        if (matches.size() != EXACT_RESTRICTION_MATCH_COUNT) {
            throw new IllegalStateException("restriction scope does not resolve to exactly one removable punishment");
        }
        return matches.getFirst();
    }

    private StoredPunishment newestInteractiveRemoval(DiscordUserId targetUserId, DiscordConsequenceType type) {
        return punishments.activeForTarget(guildId, targetUserId, type, ACTIVE_LOOKUP_LIMIT).stream()
                .filter(DiscordPunishmentService::interactiveRemovalCandidate)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("no removable Discord punishment matches the target and type"));
    }

    static boolean interactiveRemovalCandidate(StoredPunishment stored) {
        if (stored == null) {
            return false;
        }
        DiscordPunishmentState state = stored.punishment().state();
        return state == DiscordPunishmentState.APPLIED || state == DiscordPunishmentState.FAILED_REMOVE;
    }

    private static void requireInteractiveRemovalCandidate(StoredPunishment stored) {
        if (!interactiveRemovalCandidate(stored)) {
            throw new IllegalStateException("punishment state changed before removal confirmation");
        }
    }

    private Actor actor(long discordId, String name) {
        return actors.invoker(discordUser(discordId), name);
    }

    private static DiscordUserId discordUser(long id) {
        return new DiscordUserId(Long.toUnsignedString(id));
    }

    private static void requireGenericRemovable(
            DiscordConsequenceType type,
            DiscordPunishmentTermination termination
    ) {
        requireInteractiveTermination(termination);
        if (type != DiscordConsequenceType.MUTE && type != DiscordConsequenceType.BAN) {
            throw new IllegalArgumentException("generic removal only supports mute or ban");
        }
    }

    private static void requireInteractiveTermination(DiscordPunishmentTermination termination) {
        if (termination == null || termination == DiscordPunishmentTermination.NONE
                || termination == DiscordPunishmentTermination.EXPIRE) {
            throw new IllegalArgumentException("interactive removal request is invalid");
        }
    }

    private static String normalizeSnowflake(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("restriction scope id is required");
        }
        try {
            long parsed = Long.parseUnsignedLong(value.trim());
            if (parsed == INVALID_SNOWFLAKE) {
                throw new IllegalArgumentException("restriction scope id must be positive");
            }
            return Long.toUnsignedString(parsed);
        } catch (NumberFormatException failure) {
            throw new IllegalArgumentException("restriction scope id must be an unsigned Discord snowflake", failure);
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
