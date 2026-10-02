package net.enthusia.staff.discordbot;

import java.security.SecureRandom;
import java.time.Clock;
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
import net.enthusia.staff.domain.application.PunishmentExpectation;
import net.enthusia.staff.domain.application.PunishmentPreparation;
import net.enthusia.staff.domain.auth.Actor;
import net.enthusia.staff.domain.casefile.CaseVisibility;
import net.enthusia.staff.domain.discord.DiscordPunishmentIntent;
import net.enthusia.staff.domain.moderation.DiscordGuildId;
import net.enthusia.staff.domain.moderation.DiscordUserId;
import net.enthusia.staff.domain.moderation.ModerationSubjectId;
import net.enthusia.staff.domain.ports.CrossPlatformIdentityLookup;
import net.enthusia.staff.domain.sanction.SanctionSpec;

/** Browser-facing orchestration for an explicit atomic Both-platform punishment. */
final class CrossPlatformActionService {
    private static final int MAX_DRAFTS = 1000;
    private static final long TTL_SECONDS = 120;
    private final CrossPlatformPunishmentService crossPlatform;
    private final CrossPlatformIdentityLookup identities;
    private final DiscordPunishmentService discord;
    private final StaffModerationReadService reads;
    private final LinkedStaffActorResolver actors;
    private final DiscordGuildId guildId;
    private final Clock clock;
    private final SecureIdentifiers identifiers = new SecureIdentifiers(new SecureRandom());
    private final Map<UUID, Draft> drafts = new ConcurrentHashMap<>();

    record Consequence(String type, String duration) { }
    record Prepared(UUID confirmationId, String targetUserId, DiscordPunishmentIntent intent, Instant expiresAt,
            String caseId, String minecraftTargetId, String minecraftReason, List<Consequence> minecraftConsequences) { }
    record Status(UUID punishmentId, String state, boolean externalApplied, String dmOutcome,
            String caseId, boolean minecraftCommitted) { }

    private record Draft(String actorId, String guildId, String targetKey, String sessionBinding,
            long discordTarget, UUID minecraftTarget, String reasonId, String explanation,
            DiscordPunishmentIntent discordIntent, CaseId caseId, PunishmentExpectation expectation,
            ModerationSubjectId subjectId, Instant expiresAt) {
        boolean matches(ModerationActionApiService.Request request, long target, UUID minecraft, Instant now) {
            return actorId.equals(request.actorId()) && guildId.equals(request.guildId())
                    && targetKey.equals(request.targetKey()) && sessionBinding.equals(request.sessionBinding())
                    && discordTarget == target && minecraftTarget.equals(minecraft) && now.isBefore(expiresAt);
        }
    }

    CrossPlatformActionService(CrossPlatformPunishmentService crossPlatform, CrossPlatformIdentityLookup identities,
            DiscordPunishmentService discord, StaffModerationReadService reads, LinkedStaffActorResolver actors,
            DiscordGuildId guildId, Clock clock) {
        this.crossPlatform = java.util.Objects.requireNonNull(crossPlatform);
        this.identities = java.util.Objects.requireNonNull(identities);
        this.discord = java.util.Objects.requireNonNull(discord);
        this.reads = java.util.Objects.requireNonNull(reads);
        this.actors = java.util.Objects.requireNonNull(actors);
        this.guildId = java.util.Objects.requireNonNull(guildId);
        this.clock = java.util.Objects.requireNonNull(clock);
    }

    Prepared prepare(ModerationActionApiService.Request request, ModerationReadContext context, long discordTarget,
            UUID minecraftTarget, String reasonId, String explanation, DiscordPunishmentIntent discordIntent) {
        purgeExpired();
        if (drafts.size() >= MAX_DRAFTS) throw new IllegalArgumentException("too many prepared Both actions");
        DiscordUserId discordUser = new DiscordUserId(Long.toUnsignedString(discordTarget));
        ModerationSubjectId subject = linkedSubject(discordUser, minecraftTarget);
        discord.validateIssue(context.actorId(), context.actorMember().getEffectiveName(), discordTarget, discordIntent);
        Actor actor = currentActor(request, context);
        UUID confirmation = UUID.randomUUID();
        CaseId caseId = identifiers.newCaseId();
        CreatePunishmentRequest minecraft = minecraftRequest(confirmation, minecraftTarget, actor, reasonId, explanation);
        PunishmentPreparation prepared = crossPlatform.prepareMinecraft(minecraft, caseId);
        if (prepared instanceof PunishmentPreparation.Rejected rejected) {
            throw new IllegalArgumentException(rejected.code() + ": " + rejected.message());
        }
        var plan = ((PunishmentPreparation.Prepared) prepared).plan();
        Instant expires = clock.instant().plusSeconds(TTL_SECONDS);
        drafts.put(confirmation, new Draft(request.actorId(), request.guildId(), request.targetKey(),
                request.sessionBinding(), discordTarget, minecraftTarget, reasonId, explanation, discordIntent,
                caseId, PunishmentExpectation.from(plan), subject, expires));
        return new Prepared(confirmation, discordUser.value(), discordIntent, expires, caseId.value(),
                minecraftTarget.toString(), plan.publicReason(), consequences(plan.sanctions()));
    }

    Status confirm(ModerationActionApiService.Request request, ModerationReadContext context, long discordTarget,
            UUID minecraftTarget) {
        UUID id = request.confirmationId().orElseThrow();
        Optional<Status> replay = persistedStatus(context, discordTarget, id);
        if (replay.isPresent()) return replay.orElseThrow();
        Draft draft = drafts.get(id);
        if (draft == null || !draft.matches(request, discordTarget, minecraftTarget, clock.instant())) {
            throw new IllegalArgumentException("Both confirmation expired or belongs to another session");
        }
        discord.validateIssue(context.actorId(), context.actorMember().getEffectiveName(), discordTarget, draft.discordIntent());
        DiscordUserId discordUser = new DiscordUserId(Long.toUnsignedString(discordTarget));
        ModerationSubjectId subject = linkedSubject(discordUser, minecraftTarget);
        if (!subject.equals(draft.subjectId())) throw new IllegalArgumentException("linked identity changed");
        Actor actor = currentActor(request, context);
        CreatePunishmentRequest minecraft = minecraftRequest(
                id, minecraftTarget, actor, draft.reasonId(), draft.explanation());
        var outcome = crossPlatform.createBoth(new CrossPlatformPunishmentRequest(
                draft.caseId(), id, "d08:web:" + id, subject, discordUser, guildId, minecraft,
                draft.expectation(), draft.discordIntent(), targetStaff(discordUser)));
        if (outcome instanceof CrossPlatformPunishmentOutcome.Rejected rejected) {
            throw new IllegalArgumentException(rejected.code() + ": " + rejected.message());
        }
        drafts.remove(id, draft);
        return persistedStatus(context, discordTarget, id)
                .orElseThrow(() -> new IllegalStateException("committed Both punishment disappeared"));
    }

    Status status(ModerationActionApiService.Request request, ModerationReadContext context, long discordTarget,
            UUID minecraftTarget) {
        UUID id = request.confirmationId().orElseThrow();
        Optional<Status> stored = persistedStatus(context, discordTarget, id);
        if (stored.isPresent()) return stored.orElseThrow();
        Draft draft = drafts.get(id);
        if (draft == null || !draft.matches(request, discordTarget, minecraftTarget, clock.instant())) {
            throw new IllegalArgumentException("Both punishment not found");
        }
        return new Status(id, "PREPARED", false, "NOT_ATTEMPTED", draft.caseId().value(), false);
    }

    private Optional<Status> persistedStatus(ModerationReadContext context, long target, UUID id) {
        return discord.webPunishment(context.actorId(), context.actorMember().getEffectiveName(), target, id)
                .filter(stored -> stored.punishment().caseId().isPresent())
                .map(stored -> {
                    var punishment = stored.punishment();
                    return new Status(punishment.punishmentId(), punishment.state().name(),
                            punishment.externalApplied(), punishment.dmOutcome().name(),
                            punishment.caseId().orElseThrow().value(), true);
                });
    }

    private Actor currentActor(ModerationActionApiService.Request request, ModerationReadContext context) {
        return actors.invoker(new DiscordUserId(request.actorId()), context.actorMember().getEffectiveName());
    }

    private Optional<Actor> targetStaff(DiscordUserId userId) {
        return actors.targetStaff(reads.discordTarget(userId));
    }

    private ModerationSubjectId linkedSubject(DiscordUserId discordUser, UUID minecraftTarget) {
        ModerationSubjectId discordSubject = identities.subjectForDiscord(discordUser)
                .orElseThrow(() -> new IllegalArgumentException("Discord target is not linked to Minecraft"));
        ModerationSubjectId minecraftSubject = identities.subjectForMinecraft(minecraftTarget)
                .orElseThrow(() -> new IllegalArgumentException("Minecraft target is not linked to Discord"));
        if (!discordSubject.equals(minecraftSubject)) {
            throw new IllegalArgumentException("selected Discord and Minecraft accounts are not linked");
        }
        return discordSubject;
    }

    private static CreatePunishmentRequest minecraftRequest(UUID id, UUID target, Actor actor,
            String reasonId, String explanation) {
        return new CreatePunishmentRequest(new IdempotencyKey("d08:web:" + id), target, actor, reasonId,
                explanation == null ? "" : explanation, CaseVisibility.PRIVATE, List.of());
    }

    private static List<Consequence> consequences(List<SanctionSpec> sanctions) {
        return sanctions.stream().map(spec -> new Consequence(spec.type().name(), switch (spec.length().kind()) {
            case INSTANT -> "instant";
            case PERMANENT -> "permanent";
            case TEMPORARY -> spec.length().temporary().orElseThrow().toString();
        })).toList();
    }

    private void purgeExpired() {
        Instant now = clock.instant();
        drafts.entrySet().removeIf(entry -> !now.isBefore(entry.getValue().expiresAt()));
    }
}
