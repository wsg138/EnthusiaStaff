package net.enthusia.staff.paper.auth;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Supplier;
import net.enthusia.staff.domain.OperationalMode;
import net.enthusia.staff.domain.application.PreparePunishmentDraftRequest;
import net.enthusia.staff.domain.application.PunishmentDraftConfirmation;
import net.enthusia.staff.domain.application.PunishmentDraftEvaluation;
import net.enthusia.staff.domain.application.PunishmentDraftWorkflow;
import net.enthusia.staff.domain.auth.Actor;
import net.enthusia.staff.domain.auth.AuthorizationPolicy;
import net.enthusia.staff.domain.auth.ModerationAction;
import net.enthusia.staff.domain.auth.StaffRank;
import net.enthusia.staff.domain.auth.StaffTargetHierarchyPolicy;
import net.enthusia.staff.domain.casefile.CaseVisibility;
import net.enthusia.staff.domain.escalation.ReasonPolicy;
import net.enthusia.staff.domain.player.PlayerIdentity;
import net.enthusia.staff.domain.ports.PlayerDirectory;
import net.enthusia.staff.domain.ports.ReasonPolicyRepository;
import net.enthusia.staff.domain.sanction.SanctionSpec;
import net.enthusia.staff.domain.sanction.SanctionType;

/** Private website requests use the same durable configured drafts as the in-game GUI. */
public final class StaffWebPunishmentService {
    private static final int CAPACITY = 1000;
    private static final Duration CONFIRMATION_LIFETIME = Duration.ofMinutes(2);
    private static final String CAPABILITIES = "capabilities";
    private static final String PREPARE = "prepare";
    private static final String CONFIRM = "confirm";
    private static final String STATUS = "status";
    private static final Set<SanctionType> SUPPORTED = Set.of(
            SanctionType.WARNING, SanctionType.KICK, SanctionType.MUTE, SanctionType.PUBLIC_MUTE,
            SanctionType.BAN, SanctionType.NETWORK_BAN, SanctionType.NETWORK_IDENTITY_BAN);

    private final Dependencies dependencies;
    private final Function<UUID, Actor> actors;
    private final Function<UUID, Optional<StaffRank>> targetRanks;
    private final Map<UUID, Binding> confirmations = new ConcurrentHashMap<>();
    private final Object confirmationLock = new Object();

    public record Dependencies(Clock clock, Supplier<OperationalMode> mode,
            Supplier<PunishmentDraftWorkflow> workflows, Supplier<PlayerDirectory> players,
            ReasonPolicyRepository policies, AuthorizationPolicy authorization) {
        public Dependencies {
            java.util.Objects.requireNonNull(clock);
            java.util.Objects.requireNonNull(mode);
            java.util.Objects.requireNonNull(workflows);
            java.util.Objects.requireNonNull(players);
            java.util.Objects.requireNonNull(policies);
            java.util.Objects.requireNonNull(authorization);
        }
    }

    public record Request(UUID actorId, UUID targetId, String sessionBinding,
            String reasonId, String explanation, UUID confirmationId) {
        public Request {
            if (actorId == null || sessionBinding == null || !sessionBinding.matches("[a-f0-9]{64}")) {
                throw new IllegalArgumentException("invalid staff action binding");
            }
        }
    }

    public record Reason(String id, String family, String label) { }
    public record Consequence(String type, String duration) { }
    public record Prepared(UUID confirmationId, UUID targetId, String targetName, String reasonId,
            String reason, String explanation, List<Consequence> consequences, String expiresAt) { }
    public record Status(UUID confirmationId, String state, String caseId, String requestId) { }

    private static final class Binding {
        private final UUID actor;
        private final UUID target;
        private final String session;
        private final Instant expires;
        private Status result;

        Binding(Request request, Instant expires) {
            actor = request.actorId();
            target = request.targetId();
            session = request.sessionBinding();
            this.expires = expires;
        }

        boolean matches(Request request, Instant now) {
            return actor.equals(request.actorId()) && target.equals(request.targetId())
                    && session.equals(request.sessionBinding()) && now.isBefore(expires);
        }
    }

    public StaffWebPunishmentService(Dependencies dependencies, Function<UUID, Actor> actors,
            Function<UUID, Optional<StaffRank>> targetRanks) {
        this.dependencies = java.util.Objects.requireNonNull(dependencies);
        this.actors = java.util.Objects.requireNonNull(actors);
        this.targetRanks = java.util.Objects.requireNonNull(targetRanks);
    }

    public Object execute(String operation, Request request) {
        synchronized (confirmationLock) {
            return executeGuarded(operation, request);
        }
    }

    private Object executeGuarded(String operation, Request request) {
        Actor actor = authorizedActor(request);
        if (CAPABILITIES.equals(operation)) {
            return capabilities(request, actor);
        }
        PlayerIdentity target = authorizedTarget(request, actor);
        PunishmentDraftWorkflow workflow = requiredWorkflow();
        return switch (operation) {
            case PREPARE -> prepare(request, actor, workflow,
                    target.currentUsername().orElse(request.targetId().toString()));
            case CONFIRM, STATUS -> confirmOrStatus(operation, request, actor, workflow);
            default -> throw new IllegalArgumentException("unknown Minecraft punishment operation");
        };
    }

    private Actor authorizedActor(Request request) {
        Actor actor = actors.apply(request.actorId());
        if (actor == null || !actor.id().equals(request.actorId()) || actor.rank() == StaffRank.SYSTEM
                || !hasPunishmentAuthority(actor)) {
            throw new SecurityException("current staff punishment authority is required");
        }
        return actor;
    }

    private boolean hasPunishmentAuthority(Actor actor) {
        return dependencies.authorization().permits(actor, ModerationAction.ISSUE_POLICY_SANCTION)
                || dependencies.authorization().permits(actor, ModerationAction.REQUEST_POLICY_SANCTION);
    }

    private Object capabilities(Request request, Actor actor) {
        requireNoIntent(request);
        if (request.confirmationId() != null) {
            throw new IllegalArgumentException("unexpected confirmation");
        }
        return Map.of("enabled", dependencies.mode().get() == OperationalMode.ACTIVE
                        && dependencies.workflows().get() != null,
                "reasons", dependencies.policies().all().stream().filter(StaffWebPunishmentService::supported)
                        .filter(policy -> visibleAtRank(actor, policy))
                        .sorted(Comparator.comparing(ReasonPolicy::family).thenComparing(ReasonPolicy::id))
                        .map(policy -> new Reason(policy.id(), policy.family(), policy.publicReason())).toList());
    }

    private PlayerIdentity authorizedTarget(Request request, Actor actor) {
        if (request.targetId() == null) {
            throw new IllegalArgumentException("a Minecraft target is required");
        }
        PlayerDirectory players = dependencies.players().get();
        if (players == null) {
            throw new IllegalStateException("player directory is unavailable");
        }
        PlayerIdentity target = players.find(request.targetId().toString())
                .orElseThrow(() -> new IllegalArgumentException("Minecraft player is not known to the network"));
        if (!target.playerId().equals(request.targetId())) {
            throw new IllegalStateException("player identity mismatch");
        }
        if (!new StaffTargetHierarchyPolicy().permits(actor.rank(), targetRanks.apply(request.targetId()).orElse(null))) {
            throw new SecurityException("staff hierarchy protects this Minecraft player");
        }
        return target;
    }

    private PunishmentDraftWorkflow requiredWorkflow() {
        PunishmentDraftWorkflow workflow = dependencies.workflows().get();
        if (workflow == null) {
            throw new IllegalStateException("punishment storage is unavailable");
        }
        return workflow;
    }

    private Prepared prepare(Request request, Actor actor, PunishmentDraftWorkflow workflow, String targetName) {
        Instant now = dependencies.clock().instant();
        confirmations.entrySet().removeIf(entry -> !now.isBefore(entry.getValue().expires));
        validatePreparation(request);
        ReasonPolicy policy = preparationPolicy(request);
        PunishmentDraftEvaluation.Prepared prepared = preparedDraft(request, actor, workflow, policy);
        Instant expires = now.plus(CONFIRMATION_LIFETIME);
        confirmations.put(prepared.draft().draftId(), new Binding(request, expires));
        return new Prepared(prepared.draft().draftId(), request.targetId(), targetName, policy.id(),
                policy.publicReason(), prepared.draft().internalExplanation(),
                prepared.assessment().sanctions().stream().map(StaffWebPunishmentService::consequence).toList(),
                expires.toString());
    }

    private void validatePreparation(Request request) {
        if (request.confirmationId() != null || confirmations.size() >= CAPACITY) {
            throw new IllegalArgumentException("cannot prepare this punishment");
        }
        if (request.reasonId() == null || !request.reasonId().matches("[a-z0-9]+(?:[.-][a-z0-9]+)*")
                || request.reasonId().length() > 96 || request.explanation() == null
                || request.explanation().length() > 4000) {
            throw new IllegalArgumentException("configured reason and bounded explanation are required");
        }
    }

    private ReasonPolicy preparationPolicy(Request request) {
        return dependencies.policies().find(request.reasonId())
                .filter(StaffWebPunishmentService::supported)
                .orElseThrow(() -> new IllegalArgumentException("select a supported configured Minecraft reason"));
    }

    private PunishmentDraftEvaluation.Prepared preparedDraft(
            Request request, Actor actor, PunishmentDraftWorkflow workflow, ReasonPolicy policy) {
        PunishmentDraftEvaluation evaluation = workflow.prepare(new PreparePunishmentDraftRequest(
                request.targetId(), actor, policy.id(), request.explanation(),
                policy.publicByDefault() ? CaseVisibility.PUBLIC : CaseVisibility.PRIVATE, "punish"),
                dependencies.mode().get());
        if (evaluation instanceof PunishmentDraftEvaluation.Rejected rejected) {
            throw new IllegalArgumentException(rejected.code() + ": " + rejected.message());
        }
        PunishmentDraftEvaluation.Prepared prepared = (PunishmentDraftEvaluation.Prepared) evaluation;
        if (prepared.assessment().sanctions().stream().anyMatch(spec -> !SUPPORTED.contains(spec.type()))) {
            workflow.discard(prepared.draft().draftId(), actor.id());
            throw new IllegalArgumentException("this consequence requires the in-game workflow");
        }
        return prepared;
    }

    private Status confirmOrStatus(String operation, Request request, Actor actor, PunishmentDraftWorkflow workflow) {
        requireNoIntent(request);
        Binding binding = requiredBinding(request);
        if (binding.result != null) {
            return binding.result;
        }
        if (STATUS.equals(operation)) {
            return new Status(request.confirmationId(), "PREPARED", null, null);
        }
        validateCurrentDraft(request, actor, workflow);
        binding.result = confirmedStatus(request, actor, workflow);
        return binding.result;
    }

    private Binding requiredBinding(Request request) {
        UUID confirmationId = request.confirmationId();
        if (confirmationId == null) {
            throw new IllegalArgumentException("confirmation expired or belongs to another player or session");
        }
        Binding binding = confirmations.get(confirmationId);
        if (binding == null || !binding.matches(request, dependencies.clock().instant())) {
            throw new IllegalArgumentException("confirmation expired or belongs to another player or session");
        }
        return binding;
    }

    private void validateCurrentDraft(Request request, Actor actor, PunishmentDraftWorkflow workflow) {
        var draft = workflow.find(request.confirmationId(), actor.id())
                .orElseThrow(() -> new IllegalArgumentException("punishment draft expired"));
        if (!draft.targetId().equals(request.targetId())
                || dependencies.policies().find(draft.reasonId()).filter(StaffWebPunishmentService::supported).isEmpty()) {
            throw new IllegalArgumentException("prepared punishment no longer matches current policy");
        }
    }

    private Status confirmedStatus(Request request, Actor actor, PunishmentDraftWorkflow workflow) {
        PunishmentDraftConfirmation confirmed = confirmRouted(request, actor, workflow);
        if (confirmed instanceof PunishmentDraftConfirmation.Applied applied) {
            return new Status(request.confirmationId(), "APPLIED", applied.accepted().caseId().value(), null);
        }
        if (confirmed instanceof PunishmentDraftConfirmation.Requested requested) {
            return new Status(request.confirmationId(), "REQUESTED", null,
                    requested.submitted().request().requestId().toString());
        }
        PunishmentDraftConfirmation.Rejected rejected = (PunishmentDraftConfirmation.Rejected) confirmed;
        throw new IllegalArgumentException(rejected.code() + ": " + rejected.message());
    }

    private PunishmentDraftConfirmation confirmRouted(
            Request request, Actor actor, PunishmentDraftWorkflow workflow) {
        try {
            return workflow.confirmRouted(request.confirmationId(), actor, dependencies.mode().get());
        } catch (net.enthusia.staff.domain.application.PunishmentDraftCleanupException exception) {
            return new PunishmentDraftConfirmation.Applied(exception.accepted());
        } catch (net.enthusia.staff.domain.application.PunishmentRequestDraftCleanupException exception) {
            return new PunishmentDraftConfirmation.Requested(exception.submitted());
        }
    }

    private static void requireNoIntent(Request request) {
        if (request.reasonId() != null || request.explanation() != null) {
            throw new IllegalArgumentException("this operation cannot change the prepared intent");
        }
    }

    private static boolean supported(ReasonPolicy policy) {
        return policy.steps().stream().flatMap(step -> step.sanctions().stream())
                .allMatch(spec -> SUPPORTED.contains(spec.type()));
    }

    private boolean visibleAtRank(Actor actor, ReasonPolicy policy) {
        if (actor.rank() == StaffRank.DEVELOPER) {
            return dependencies.authorization().permits(actor, ModerationAction.REQUEST_POLICY_SANCTION);
        }
        return actor.rank() == StaffRank.HELPER && policy.requiredRank() == StaffRank.MOD
                || actor.rank().atLeast(policy.requiredRank());
    }

    private static Consequence consequence(SanctionSpec spec) {
        return new Consequence(spec.type().name(), switch (spec.length().kind()) {
            case INSTANT -> "instant";
            case PERMANENT -> "permanent";
            case TEMPORARY -> durationLabel(spec.length().temporary().orElseThrow());
        });
    }

    private static String durationLabel(Duration duration) {
        long seconds = duration.getSeconds();
        if (seconds % 86400 == 0) return unitLabel(seconds / 86400, "day");
        if (seconds % 3600 == 0) return unitLabel(seconds / 3600, "hour");
        if (seconds % 60 == 0) return unitLabel(seconds / 60, "minute");
        return unitLabel(seconds, "second");
    }

    private static String unitLabel(long amount, String unit) {
        return amount + " " + unit + (amount == 1 ? "" : "s");
    }
}
