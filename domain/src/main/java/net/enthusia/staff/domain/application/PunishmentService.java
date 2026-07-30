package net.enthusia.staff.domain.application;

import java.time.Clock;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.enthusia.staff.common.SecureIdentifiers;
import net.enthusia.staff.domain.OperationalMode;
import net.enthusia.staff.domain.auth.Actor;
import net.enthusia.staff.domain.auth.AuthorizationPolicy;
import net.enthusia.staff.domain.auth.ModerationAction;
import net.enthusia.staff.domain.auth.StaffRank;
import net.enthusia.staff.domain.escalation.EscalationDecision;
import net.enthusia.staff.domain.escalation.EscalationEngine;
import net.enthusia.staff.domain.escalation.PunishmentStep;
import net.enthusia.staff.domain.escalation.ReasonPolicy;
import net.enthusia.staff.domain.ports.ModerationStore;
import net.enthusia.staff.domain.ports.ReasonPolicyRepository;
import net.enthusia.staff.domain.sanction.SanctionSpec;

public final class PunishmentService {
    private final Clock clock;
    private final SecureIdentifiers identifiers;
    private final AuthorizationPolicy authorization;
    private final ReasonPolicyRepository policies;
    private final ModerationStore store;
    private final EscalationEngine escalation;

    public PunishmentService(
            Clock clock,
            SecureIdentifiers identifiers,
            AuthorizationPolicy authorization,
            ReasonPolicyRepository policies,
            ModerationStore store,
            EscalationEngine escalation
    ) {
        this.clock = Objects.requireNonNull(clock);
        this.identifiers = Objects.requireNonNull(identifiers);
        this.authorization = Objects.requireNonNull(authorization);
        this.policies = Objects.requireNonNull(policies);
        this.store = Objects.requireNonNull(store);
        this.escalation = Objects.requireNonNull(escalation);
    }

    public PunishmentResult create(CreatePunishmentRequest request, OperationalMode mode) {
        return createConfirmed(request, mode, (PunishmentExpectation) null);
    }

    public PunishmentResult createConfirmed(
            CreatePunishmentRequest request,
            OperationalMode mode,
            PunishmentExpectation expectation
    ) {
        PunishmentEvaluation evaluation = evaluate(request, mode);
        if (evaluation instanceof PunishmentEvaluation.Rejected rejected) {
            return new PunishmentResult.Rejected(rejected.code(), rejected.message());
        }
        PunishmentAssessment assessment = ((PunishmentEvaluation.Allowed) evaluation).assessment();
        if (expectation != null && !expectation.matches(assessment)) {
            return recommendationChanged();
        }
        return createEvaluated(request, assessment);
    }

    /**
     * Compatibility overload for callers that have not yet adopted complete recommendation snapshots.
     */
    @Deprecated(forRemoval = false)
    public PunishmentResult createConfirmed(
            CreatePunishmentRequest request,
            OperationalMode mode,
            String expectedStepLabel
    ) {
        PunishmentEvaluation evaluation = evaluate(request, mode);
        if (evaluation instanceof PunishmentEvaluation.Rejected rejected) {
            return new PunishmentResult.Rejected(rejected.code(), rejected.message());
        }
        PunishmentAssessment assessment = ((PunishmentEvaluation.Allowed) evaluation).assessment();
        if (expectedStepLabel != null
                && !assessment.escalation().selectedStep().label().equals(expectedStepLabel)) {
            return recommendationChanged();
        }
        return createEvaluated(request, assessment);
    }

    private static PunishmentResult.Rejected recommendationChanged() {
        return new PunishmentResult.Rejected(
                "RECOMMENDATION_CHANGED",
                "The authoritative recommendation changed; review again before confirming"
        );
    }

    private PunishmentResult createEvaluated(
            CreatePunishmentRequest request,
            PunishmentAssessment assessment
    ) {
        ReasonPolicy policy = assessment.policy();
        PunishmentPlan plan = new PunishmentPlan(
                identifiers.newCaseId(),
                request.idempotencyKey(),
                request.targetId(),
                request.actor(),
                policy.id(),
                policy.family(),
                policy.publicReason(),
                request.internalExplanation(),
                assessment.configurationVersion(),
                request.visibility(),
                clock.instant(),
                assessment.escalation(),
                assessment.sanctions()
        );
        return store.createPunishment(plan);
    }

    public PunishmentEvaluation evaluate(CreatePunishmentRequest request, OperationalMode mode) {
        PunishmentEvaluation evaluation = evaluate(
                request,
                mode,
                ModerationAction.ISSUE_POLICY_SANCTION,
                true
        );
        if (!(evaluation instanceof PunishmentEvaluation.Allowed allowed)) {
            return evaluation;
        }
        if (requiresApproval(request.actor(), allowed.assessment())) {
            return new PunishmentEvaluation.Rejected(
                    "APPROVAL_REQUIRED",
                    "This punishment must be approved by a moderator or higher before it can be applied"
            );
        }
        return allowed;
    }

    public boolean requiresApproval(Actor actor, PunishmentAssessment assessment) {
        Objects.requireNonNull(actor);
        Objects.requireNonNull(assessment);
        if (actor.rank() == StaffRank.DEVELOPER) {
            return true;
        }
        return actor.rank() == StaffRank.HELPER && assessment.sanctions().stream()
                .anyMatch(spec -> spec.length().isPermanent());
    }

    private PunishmentEvaluation evaluate(
            CreatePunishmentRequest request,
            OperationalMode mode,
            ModerationAction authority,
            boolean enforceReasonRank
    ) {
        Objects.requireNonNull(request);
        PunishmentEvaluation.Rejected accessIssue = accessIssue(request, mode, authority);
        if (accessIssue != null) {
            return accessIssue;
        }
        ReasonPolicyRepository.VersionedReasonPolicy resolved = policies.resolve(request.reasonId()).orElse(null);
        if (resolved == null) {
            return new PunishmentEvaluation.Rejected("UNKNOWN_REASON", "The configured reason does not exist");
        }
        PunishmentEvaluation.Rejected policyIssue = policyIssue(request, resolved.policy(), enforceReasonRank);
        if (policyIssue != null) {
            return policyIssue;
        }
        return assess(request, resolved);
    }

    private PunishmentEvaluation.Rejected accessIssue(
            CreatePunishmentRequest request,
            OperationalMode mode,
            ModerationAction authority
    ) {
        if (mode != OperationalMode.ACTIVE) {
            return new PunishmentEvaluation.Rejected("MODE_BLOCKED", "New punishments are disabled in " + mode);
        }
        if (!authorization.permits(request.actor(), authority)) {
            return new PunishmentEvaluation.Rejected("FORBIDDEN", "The actor is not permitted to perform this action");
        }
        return null;
    }

    private static PunishmentEvaluation.Rejected policyIssue(
            CreatePunishmentRequest request,
            ReasonPolicy policy,
            boolean enforceReasonRank
    ) {
        StaffRank actorRank = request.actor().rank();
        if (actorRank == StaffRank.SYSTEM && !policy.automaticDetectionAllowed()) {
            return new PunishmentEvaluation.Rejected(
                    "AUTOMATION_NOT_ALLOWED",
                    "The reason is not approved for automatic enforcement"
            );
        }
        if (enforceReasonRank && actorRank != StaffRank.SYSTEM
                && !meetsReasonRank(actorRank, policy.requiredRank())) {
            return new PunishmentEvaluation.Rejected(
                    "RANK_REQUIRED",
                    policy.requiredRank() + " is required for this reason"
            );
        }
        return null;
    }

    private PunishmentEvaluation assess(
            CreatePunishmentRequest request,
            ReasonPolicyRepository.VersionedReasonPolicy resolved
    ) {
        ReasonPolicy policy = resolved.policy();
        EscalationDecision decision = escalation.decide(
                policy,
                store.relatedHistory(request.targetId(), policy.family()),
                clock.instant()
        );
        List<SanctionSpec> sanctions = selectedSanctions(request, policy, decision);
        if (sanctions == null) {
            return new PunishmentEvaluation.Rejected(
                    "FORBIDDEN_OVERRIDE",
                    "The requested override exceeds the actor's authority"
            );
        }
        return new PunishmentEvaluation.Allowed(new PunishmentAssessment(
                resolved.version(), policy, decision, sanctions
        ));
    }

    private static boolean meetsReasonRank(StaffRank actorRank, StaffRank requiredRank) {
        if (actorRank == StaffRank.HELPER && requiredRank == StaffRank.MOD) {
            return true;
        }
        return actorRank.atLeast(requiredRank);
    }

    private List<SanctionSpec> selectedSanctions(
            CreatePunishmentRequest request,
            ReasonPolicy policy,
            EscalationDecision decision
    ) {
        if (!request.usesOverride()) {
            return decision.selectedStep().sanctions();
        }

        List<SanctionSpec> requested = request.overrideSanctions();
        List<PunishmentStep> exactSteps = policy.steps().stream()
                .filter(step -> step.sanctions().equals(requested))
                .toList();
        if (!exactSteps.isEmpty()) {
            int selectedOrdinal = decision.selectedStep().ordinal();
            if (exactSteps.stream().anyMatch(step -> step.ordinal() == selectedOrdinal)) {
                return requested;
            }
            ModerationAction action = exactSteps.stream().anyMatch(step -> step.ordinal() < selectedOrdinal)
                    ? ModerationAction.LOWER_RECOMMENDATION
                    : ModerationAction.RAISE_RECOMMENDATION;
            return authorization.permits(request.actor(), action) ? requested : null;
        }

        boolean configuredTypes = policy.steps().stream()
                .map(PunishmentStep::sanctions)
                .anyMatch(configured -> sameTypeShape(configured, requested));
        ModerationAction action = configuredTypes
                ? ModerationAction.USE_CUSTOM_DURATION
                : ModerationAction.USE_CUSTOM_COMBINATION;
        return authorization.permits(request.actor(), action) ? requested : null;
    }

    private static boolean sameTypeShape(List<SanctionSpec> left, List<SanctionSpec> right) {
        return typeCounts(left).equals(typeCounts(right));
    }

    private static Map<net.enthusia.staff.domain.sanction.SanctionType, Integer> typeCounts(
            List<SanctionSpec> sanctions
    ) {
        Map<net.enthusia.staff.domain.sanction.SanctionType, Integer> counts =
                new EnumMap<>(net.enthusia.staff.domain.sanction.SanctionType.class);
        sanctions.forEach(spec -> counts.merge(spec.type(), 1, Integer::sum));
        return counts;
    }
}
