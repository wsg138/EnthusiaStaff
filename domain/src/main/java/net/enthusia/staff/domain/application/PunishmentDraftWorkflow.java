package net.enthusia.staff.domain.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import net.enthusia.staff.common.IdempotencyKey;
import net.enthusia.staff.domain.OperationalMode;
import net.enthusia.staff.domain.auth.Actor;
import net.enthusia.staff.domain.auth.StaffRank;
import net.enthusia.staff.domain.ports.PunishmentDraftStore;

public final class PunishmentDraftWorkflow {
    private static final Duration MAXIMUM_LIFETIME = Duration.ofDays(7);

    private final Clock clock;
    private final Duration lifetime;
    private final Supplier<UUID> identifiers;
    private final PunishmentService punishments;
    private final PunishmentRequestService punishmentRequests;
    private final PunishmentDraftStore drafts;

    public PunishmentDraftWorkflow(
            Clock clock,
            Duration lifetime,
            PunishmentService punishments,
            PunishmentDraftStore drafts
    ) {
        this(clock, lifetime, UUID::randomUUID, punishments, null, drafts);
    }

    public PunishmentDraftWorkflow(
            Clock clock,
            Duration lifetime,
            PunishmentService punishments,
            PunishmentRequestService punishmentRequests,
            PunishmentDraftStore drafts
    ) {
        this(clock, lifetime, UUID::randomUUID, punishments, punishmentRequests, drafts);
    }

    PunishmentDraftWorkflow(
            Clock clock,
            Duration lifetime,
            Supplier<UUID> identifiers,
            PunishmentService punishments,
            PunishmentDraftStore drafts
    ) {
        this(clock, lifetime, identifiers, punishments, null, drafts);
    }

    PunishmentDraftWorkflow(
            Clock clock,
            Duration lifetime,
            Supplier<UUID> identifiers,
            PunishmentService punishments,
            PunishmentRequestService punishmentRequests,
            PunishmentDraftStore drafts
    ) {
        this.clock = Objects.requireNonNull(clock);
        this.lifetime = Objects.requireNonNull(lifetime);
        this.identifiers = Objects.requireNonNull(identifiers);
        this.punishments = Objects.requireNonNull(punishments);
        this.punishmentRequests = punishmentRequests;
        this.drafts = Objects.requireNonNull(drafts);
        if (lifetime.isZero() || lifetime.isNegative() || lifetime.compareTo(MAXIMUM_LIFETIME) > 0) {
            throw new IllegalArgumentException("draft lifetime must be positive and at most seven days");
        }
    }

    public PunishmentDraftEvaluation prepare(
            PreparePunishmentDraftRequest request,
            OperationalMode mode
    ) {
        Objects.requireNonNull(request);
        UUID draftId = Objects.requireNonNull(identifiers.get(), "generated draft identifier");
        CreatePunishmentRequest punishmentRequest = punishmentRequest(
                draftId,
                request.targetId(),
                request.actor(),
                request.reasonId(),
                request.internalExplanation(),
                request.visibility()
        );
        PunishmentEvaluation evaluation = punishments.evaluateRequestProposal(punishmentRequest, mode);
        if (evaluation instanceof PunishmentEvaluation.Rejected rejected) {
            return new PunishmentDraftEvaluation.Rejected(rejected.code(), rejected.message());
        }
        PunishmentAssessment assessment = ((PunishmentEvaluation.Allowed) evaluation).assessment();
        Instant createdAt = clock.instant();
        PunishmentDraft draft = new PunishmentDraft(
                draftId,
                request.actor().id(),
                request.targetId(),
                request.reasonId(),
                request.internalExplanation(),
                request.visibility(),
                request.commandName(),
                PunishmentExpectation.from(assessment),
                createdAt,
                createdAt.plus(lifetime)
        );
        drafts.save(draft);
        return new PunishmentDraftEvaluation.Prepared(draft, assessment);
    }

    public Optional<PunishmentDraft> resume(UUID actorId, UUID targetId) {
        return drafts.findLatest(actorId, targetId, clock.instant());
    }

    public Optional<PunishmentDraft> find(UUID draftId, UUID actorId) {
        return drafts.find(draftId, actorId, clock.instant());
    }

    public PunishmentResult confirm(UUID draftId, Actor actor, OperationalMode mode) {
        Objects.requireNonNull(actor);
        PunishmentDraft draft = drafts.find(draftId, actor.id(), clock.instant()).orElse(null);
        if (draft == null) {
            return draftNotFound();
        }
        CreatePunishmentRequest request = requestFor(draft, actor);
        PunishmentResult result = punishments.createConfirmed(request, mode, draft.expectation());
        if (result instanceof PunishmentResult.Accepted accepted) {
            deleteAppliedDraft(draft, actor, accepted);
        }
        return result;
    }

    public PunishmentDraftConfirmation confirmRouted(UUID draftId, Actor actor, OperationalMode mode) {
        Objects.requireNonNull(actor);
        PunishmentDraft draft = drafts.find(draftId, actor.id(), clock.instant()).orElse(null);
        if (draft == null) {
            PunishmentResult.Rejected rejected = draftNotFound();
            return new PunishmentDraftConfirmation.Rejected(rejected.code(), rejected.message());
        }
        CreatePunishmentRequest request = requestFor(draft, actor);
        if (requiresRequest(actor, draft.expectation())) {
            return submitRequest(draft, actor, request, mode);
        }
        PunishmentResult result = punishments.createConfirmed(request, mode, draft.expectation());
        if (result instanceof PunishmentResult.Accepted accepted) {
            deleteAppliedDraft(draft, actor, accepted);
            return new PunishmentDraftConfirmation.Applied(accepted);
        }
        PunishmentResult.Rejected rejected = (PunishmentResult.Rejected) result;
        return new PunishmentDraftConfirmation.Rejected(rejected.code(), rejected.message());
    }

    public boolean discard(UUID draftId, UUID actorId) {
        return drafts.delete(draftId, actorId);
    }

    public int deleteExpired() {
        return drafts.deleteExpired(clock.instant());
    }

    private PunishmentDraftConfirmation submitRequest(
            PunishmentDraft draft,
            Actor actor,
            CreatePunishmentRequest request,
            OperationalMode mode
    ) {
        if (punishmentRequests == null) {
            return new PunishmentDraftConfirmation.Rejected(
                    "REQUEST_WORKFLOW_UNAVAILABLE",
                    "Durable punishment request storage is not available"
            );
        }
        PunishmentRequestResult result = punishmentRequests.submitConfirmed(
                request,
                mode,
                draft.expectation()
        );
        if (result instanceof PunishmentRequestResult.Submitted submitted) {
            try {
                drafts.delete(draft.draftId(), actor.id());
            } catch (RuntimeException exception) {
                throw new PunishmentRequestDraftCleanupException(submitted, exception);
            }
            return new PunishmentDraftConfirmation.Requested(submitted);
        }
        PunishmentRequestResult.Rejected rejected = (PunishmentRequestResult.Rejected) result;
        return new PunishmentDraftConfirmation.Rejected(rejected.code(), rejected.message());
    }

    private void deleteAppliedDraft(
            PunishmentDraft draft,
            Actor actor,
            PunishmentResult.Accepted accepted
    ) {
        try {
            drafts.delete(draft.draftId(), actor.id());
        } catch (RuntimeException exception) {
            throw new PunishmentDraftCleanupException(accepted, exception);
        }
    }

    private static boolean requiresRequest(Actor actor, PunishmentExpectation expectation) {
        if (actor.rank() == StaffRank.DEVELOPER) {
            return true;
        }
        return actor.rank() == StaffRank.HELPER && expectation.sanctions().stream()
                .anyMatch(specification -> specification.length().isPermanent());
    }

    private static PunishmentResult.Rejected draftNotFound() {
        return new PunishmentResult.Rejected(
                "DRAFT_NOT_FOUND",
                "The punishment draft is missing, expired, or belongs to another actor"
        );
    }

    private static CreatePunishmentRequest requestFor(PunishmentDraft draft, Actor actor) {
        return punishmentRequest(
                draft.draftId(),
                draft.targetId(),
                actor,
                draft.reasonId(),
                draft.internalExplanation(),
                draft.visibility()
        );
    }

    private static CreatePunishmentRequest punishmentRequest(
            UUID draftId,
            UUID targetId,
            Actor actor,
            String reasonId,
            String explanation,
            net.enthusia.staff.domain.casefile.CaseVisibility visibility
    ) {
        return new CreatePunishmentRequest(
                new IdempotencyKey("punishment-draft:" + draftId),
                targetId,
                actor,
                reasonId,
                explanation,
                visibility,
                List.of()
        );
    }
}
