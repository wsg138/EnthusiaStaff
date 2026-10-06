package net.enthusia.staff.discordbot;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.enthusia.staff.common.CaseId;
import net.enthusia.staff.common.IdempotencyKey;
import net.enthusia.staff.common.SecureIdentifiers;
import net.enthusia.staff.domain.application.CreatePunishmentRequest;
import net.enthusia.staff.domain.application.CrossPlatformPunishmentOutcome;
import net.enthusia.staff.domain.application.CrossPlatformPunishmentRequest;
import net.enthusia.staff.domain.application.CrossPlatformPunishmentService;
import net.enthusia.staff.domain.application.MinecraftPunishmentGateway;
import net.enthusia.staff.domain.application.PunishmentExpectation;
import net.enthusia.staff.domain.application.PunishmentPlan;
import net.enthusia.staff.domain.application.PunishmentPreparation;
import net.enthusia.staff.domain.application.PunishmentReasonOption;
import net.enthusia.staff.domain.auth.Actor;
import net.enthusia.staff.domain.discord.DiscordPunishment;
import net.enthusia.staff.domain.discord.DiscordPunishmentIntent;
import net.enthusia.staff.domain.moderation.DiscordGuildId;
import net.enthusia.staff.domain.moderation.DiscordUserId;
import net.enthusia.staff.domain.moderation.ModerationSubjectId;
import net.enthusia.staff.persistence.JdbcCrossPlatformPunishmentStatusReader;

/**
 * Browser-facing D08 coordinator. Preparation is non-mutating; confirmation creates the
 * Minecraft case and Discord enforcement intent atomically under one durable identity.
 */
final class CrossPlatformModerationActionService {
    private static final int MAX_DRAFTS = 1000;
    private static final Duration CONFIRMATION_TTL = Duration.ofMinutes(2);

    private final StaffModerationReadService reads;
    private final LinkedStaffActorResolver actors;
    private final MinecraftPunishmentGateway minecraft;
    private final CrossPlatformPunishmentService crossPlatform;
    private final DiscordPunishmentService discord;
    private final JdaDiscordPunishmentGateway discordGateway;
    private final JdbcCrossPlatformPunishmentStatusReader minecraftStatus;
    private final DiscordGuildId guildId;
    private final Clock clock;
    private final SecureIdentifiers identifiers;
    private final Map<UUID, Draft> drafts = new ConcurrentHashMap<>();
    private final Object confirmationLock = new Object();

    CrossPlatformModerationActionService(
            StaffModerationReadService reads,
            LinkedStaffActorResolver actors,
            MinecraftPunishmentGateway minecraft,
            CrossPlatformPunishmentService crossPlatform,
            DiscordPunishmentService discord,
            JdaDiscordPunishmentGateway discordGateway,
            JdbcCrossPlatformPunishmentStatusReader minecraftStatus,
            DiscordGuildId guildId,
            Clock clock
    ) {
        if (reads == null || actors == null || minecraft == null || crossPlatform == null || discord == null
                || discordGateway == null || minecraftStatus == null || guildId == null || clock == null) {
            throw new IllegalArgumentException("cross-platform action dependencies must be present");
        }
        this.reads = reads;
        this.actors = actors;
        this.minecraft = minecraft;
        this.crossPlatform = crossPlatform;
        this.discord = discord;
        this.discordGateway = discordGateway;
        this.minecraftStatus = minecraftStatus;
        this.guildId = guildId;
        this.clock = clock;
        this.identifiers = new SecureIdentifiers(new SecureRandom());
    }

    Prepared prepare(
            long actorDiscordId,
            String actorName,
            long targetDiscordId,
            UUID minecraftTargetId,
            String reasonId,
            String explanation,
            DiscordPunishmentIntent discordIntent
    ) {
        synchronized (confirmationLock) {
            Instant now = clock.instant();
            drafts.entrySet().removeIf(entry -> !now.isBefore(entry.getValue().expiresAt()));
            if (drafts.size() >= MAX_DRAFTS) {
                throw new IllegalStateException("cross-platform confirmation capacity is exhausted");
            }
            Actor actor = actor(actorDiscordId, actorName);
            DiscordUserId discordTarget = discordUser(targetDiscordId);
            ModerationSubjectId subjectId = requireSameSubject(discordTarget, minecraftTargetId);
            PunishmentReasonOption reason = reason(actor, reasonId);
            discordGateway.preflight(guildId, discordTarget, discordIntent);

            UUID confirmationId = UUID.randomUUID();
            CaseId caseId = identifiers.newCaseId();
            CreatePunishmentRequest minecraftRequest = minecraftRequest(
                    confirmationId, minecraftTargetId, actor, reason, explanation);
            PunishmentPreparation preparation = minecraft.prepareConfirmed(minecraftRequest, caseId);
            if (preparation instanceof PunishmentPreparation.Rejected rejected) {
                throw new IllegalArgumentException(rejected.code() + ": " + rejected.message());
            }
            PunishmentPlan plan = ((PunishmentPreparation.Prepared) preparation).plan();
            PunishmentExpectation expectation = PunishmentExpectation.from(plan);
            Instant expiresAt = now.plus(CONFIRMATION_TTL);
            Draft draft = new Draft(
                    confirmationId,
                    actor.id(),
                    caseId,
                    subjectId,
                    discordTarget,
                    minecraftTargetId,
                    reason,
                    explanation == null ? "" : explanation,
                    expectation,
                    discordIntent,
                    expiresAt
            );
            drafts.put(confirmationId, draft);
            return prepared(draft, plan);
        }
    }

    Status confirm(
            long actorDiscordId,
            String actorName,
            long targetDiscordId,
            UUID confirmationId
    ) {
        synchronized (confirmationLock) {
            Optional<Status> durable = durableStatus(actorDiscordId, actorName, targetDiscordId, confirmationId);
            if (durable.isPresent()) {
                return durable.orElseThrow();
            }
            Draft draft = requiredDraft(confirmationId);
            Actor actor = actor(actorDiscordId, actorName);
            if (!actor.id().equals(draft.actorId())) {
                throw new SecurityException("cross-platform confirmation belongs to another actor");
            }
            DiscordUserId requestedTarget = discordUser(targetDiscordId);
            if (!requestedTarget.equals(draft.discordTarget())) {
                throw new IllegalArgumentException("cross-platform confirmation target changed");
            }
            ModerationSubjectId subjectId = requireSameSubject(requestedTarget, draft.minecraftTargetId());
            if (!subjectId.equals(draft.subjectId())) {
                throw new IllegalArgumentException("linked moderation subject changed");
            }
            discordGateway.preflight(guildId, requestedTarget, draft.discordIntent());
            CreatePunishmentRequest currentMinecraft = minecraftRequest(
                    draft.confirmationId(),
                    draft.minecraftTargetId(),
                    actor,
                    draft.reason(),
                    draft.explanation()
            );
            CrossPlatformPunishmentOutcome outcome = crossPlatform.createBoth(new CrossPlatformPunishmentRequest(
                    draft.caseId(),
                    draft.confirmationId(),
                    operationKey(draft.confirmationId()),
                    draft.subjectId(),
                    draft.discordTarget(),
                    guildId,
                    currentMinecraft,
                    draft.expectation(),
                    draft.discordIntent(),
                    actors.targetStaff(reads.discordTarget(draft.discordTarget()))
            ));
            if (outcome instanceof CrossPlatformPunishmentOutcome.Rejected rejected) {
                throw new IllegalArgumentException(rejected.code() + ": " + rejected.message());
            }
            return durableStatus(actorDiscordId, actorName, targetDiscordId, confirmationId)
                    .orElseThrow(() -> new IllegalStateException("committed cross-platform punishment is unreadable"));
        }
    }

    Status status(long actorDiscordId, String actorName, long targetDiscordId, UUID confirmationId) {
        synchronized (confirmationLock) {
            Optional<Status> durable = durableStatus(actorDiscordId, actorName, targetDiscordId, confirmationId);
            if (durable.isPresent()) {
                return durable.orElseThrow();
            }
            Draft draft = requiredDraft(confirmationId);
            Actor actor = actor(actorDiscordId, actorName);
            if (!actor.id().equals(draft.actorId()) || !discordUser(targetDiscordId).equals(draft.discordTarget())) {
                throw new SecurityException("cross-platform confirmation does not belong to this request");
            }
            return new Status(
                    confirmationId,
                    "PREPARED",
                    draft.caseId().value(),
                    MinecraftState.PREPARED.name(),
                    0,
                    null,
                    "NOT_CREATED",
                    false,
                    "NOT_CREATED"
            );
        }
    }

    private Optional<Status> durableStatus(
            long actorDiscordId,
            String actorName,
            long targetDiscordId,
            UUID confirmationId
    ) {
        return discord.webPunishment(actorDiscordId, actorName, targetDiscordId, confirmationId)
                .map(stored -> project(confirmationId, stored.punishment()));
    }

    private Status project(UUID confirmationId, DiscordPunishment punishment) {
        CaseId caseId = punishment.caseId()
                .orElseThrow(() -> new IllegalStateException("cross-platform Discord intent has no shared case"));
        JdbcCrossPlatformPunishmentStatusReader.Status delivery = minecraftStatus.find(caseId).orElse(null);
        String minecraftState = delivery == null ? MinecraftState.UNKNOWN.name() : delivery.state().name();
        int attempts = delivery == null ? 0 : delivery.attempts();
        String error = delivery == null ? null : delivery.lastErrorCode();
        return new Status(
                confirmationId,
                combinedState(punishment, delivery),
                caseId.value(),
                minecraftState,
                attempts,
                error,
                punishment.state().name(),
                punishment.externalApplied(),
                punishment.dmOutcome().name()
        );
    }

    private static String combinedState(
            DiscordPunishment punishment,
            JdbcCrossPlatformPunishmentStatusReader.Status minecraft
    ) {
        if (minecraft != null && minecraft.state() == JdbcCrossPlatformPunishmentStatusReader.State.DEAD_LETTER) {
            return "PARTIAL_FAILURE";
        }
        return switch (punishment.state()) {
            case FAILED_APPLY, FAILED_REMOVE -> "PARTIAL_FAILURE";
            case APPLIED, COMPLETED -> minecraft != null
                    && minecraft.state() == JdbcCrossPlatformPunishmentStatusReader.State.ACKNOWLEDGED
                    ? "APPLIED"
                    : "PENDING";
            default -> "PENDING";
        };
    }

    private Prepared prepared(Draft draft, PunishmentPlan plan) {
        return new Prepared(
                draft.confirmationId(),
                draft.minecraftTargetId(),
                draft.reason().id(),
                draft.reason().label(),
                plan.sanctions().stream()
                        .map(spec -> new MinecraftConsequence(
                                spec.type().name(),
                                spec.length().kind().name(),
                                spec.length().temporary().map(Duration::toSeconds).orElse(null)))
                        .toList(),
                draft.discordIntent(),
                draft.expiresAt()
        );
    }

    private Draft requiredDraft(UUID confirmationId) {
        if (confirmationId == null) {
            throw new IllegalArgumentException("cross-platform confirmation is required");
        }
        Draft draft = drafts.get(confirmationId);
        if (draft == null || !clock.instant().isBefore(draft.expiresAt())) {
            drafts.remove(confirmationId);
            throw new IllegalArgumentException("cross-platform confirmation expired");
        }
        return draft;
    }

    private ModerationSubjectId requireSameSubject(DiscordUserId discordTarget, UUID minecraftTargetId) {
        StaffModerationReadService.Target discordRead = reads.discordTarget(discordTarget);
        StaffModerationReadService.Target minecraftRead = reads.minecraftTarget(minecraftTargetId);
        ModerationSubjectId discordSubject = discordRead.subject()
                .map(value -> value.subject().subjectId())
                .orElseThrow(() -> new IllegalArgumentException("Discord target is not linked to a moderation subject"));
        ModerationSubjectId minecraftSubject = minecraftRead.subject()
                .map(value -> value.subject().subjectId())
                .orElseThrow(() -> new IllegalArgumentException("Minecraft target is not linked to a moderation subject"));
        if (!discordSubject.equals(minecraftSubject)) {
            throw new IllegalArgumentException("Discord and Minecraft targets are not the same moderation subject");
        }
        return discordSubject;
    }

    private PunishmentReasonOption reason(Actor actor, String reasonId) {
        if (reasonId == null || reasonId.isBlank()) {
            throw new IllegalArgumentException("configured Minecraft reason is required");
        }
        return minecraft.availableReasons(actor).stream()
                .filter(value -> value.id().equals(reasonId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("configured Minecraft reason is unavailable"));
    }

    private static CreatePunishmentRequest minecraftRequest(
            UUID confirmationId,
            UUID targetId,
            Actor actor,
            PunishmentReasonOption reason,
            String explanation
    ) {
        return new CreatePunishmentRequest(
                new IdempotencyKey(operationKey(confirmationId)),
                targetId,
                actor,
                reason.id(),
                explanation == null ? "" : explanation,
                reason.defaultVisibility(),
                List.of()
        );
    }

    private Actor actor(long actorDiscordId, String actorName) {
        return actors.invoker(new DiscordUserId(Long.toUnsignedString(actorDiscordId)), actorName);
    }

    private static DiscordUserId discordUser(long targetDiscordId) {
        if (targetDiscordId == 0L) {
            throw new IllegalArgumentException("Discord target is required");
        }
        return new DiscordUserId(Long.toUnsignedString(targetDiscordId));
    }

    private static String operationKey(UUID confirmationId) {
        return "d08:both:" + confirmationId;
    }

    record Prepared(
            UUID confirmationId,
            UUID minecraftTargetId,
            String reasonId,
            String reason,
            List<MinecraftConsequence> minecraftConsequences,
            DiscordPunishmentIntent discordIntent,
            Instant expiresAt
    ) {
        Prepared {
            minecraftConsequences = List.copyOf(minecraftConsequences);
        }
    }

    record MinecraftConsequence(String type, String lengthKind, Long durationSeconds) {
    }

    record Status(
            UUID confirmationId,
            String state,
            String caseId,
            String minecraftState,
            int minecraftAttempts,
            String minecraftError,
            String discordState,
            boolean discordExternalApplied,
            String discordDmOutcome
    ) {
    }

    private record Draft(
            UUID confirmationId,
            UUID actorId,
            CaseId caseId,
            ModerationSubjectId subjectId,
            DiscordUserId discordTarget,
            UUID minecraftTargetId,
            PunishmentReasonOption reason,
            String explanation,
            PunishmentExpectation expectation,
            DiscordPunishmentIntent discordIntent,
            Instant expiresAt
    ) {
    }

    private enum MinecraftState {
        PREPARED,
        UNKNOWN
    }
}
