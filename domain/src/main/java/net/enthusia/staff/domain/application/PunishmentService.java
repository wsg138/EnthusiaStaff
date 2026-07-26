package net.enthusia.staff.domain.application;

import java.time.Clock;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.enthusia.staff.common.SecureIdentifiers;
import net.enthusia.staff.domain.OperationalMode;
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
            return new PunishmentResult.Rejected(
                    "RECOMMENDATION_CHANGED",
                    "The authoritative recommendation changed; review again before confirming"
            );
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
            return new PunishmentResult.Rejected(
                    "RECOMMENDATION_CHANGED",
                    "The authoritative recommendation changed; review again before confirming"
            );
        }
        return createEvaluated(request, assessment);
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
        Objects.requireNonNull(request);
        if (mode != OperationalMode.ACTIVE) {
            return new PunishmentEvaluation.Rejected("MODE_BLOCKED", "New punishments are disabled in " + mode);
        }
        if (!authorization.permits(request.actor(), ModerationAction.ISSUE_POLICY_SANCTION)) {
            return new PunishmentEvaluation.Rejected("FORBIDDEN", "The actor is not permitted to issue sanctions");
        }
        ReasonPolicyRepository.VersionedReasonPolicy resolved = policies.resolve(request.reasonId()).orElse(null);
        if (resolved == null) {
            return new PunishmentEvaluation.Rejected("UNKNOWN_REASON", "The configured reason does not exist");
        }
        ReasonPolicy policy = resolved.policy();
        if (request.actor().rank() == StaffRank.SYSTEM && !policy.automaticDetectionAllowed()) {
            return new PunishmentEvaluation.Rejected(
                    "AUTOMATION_NOT_ALLOWED",
                    "The reason is not approved for automatic enforcement"
            );
        }
        if (request.actor().rank() != StaffRank.SYSTEM && !request.actor().rank().atLeast(policy.requiredRank())) {
            return new PunishmentEvaluation.Rejected(
                    "RANK_REQUIRED",
                    policy.requiredRank() + " is required for this reason"
            );
        }

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
