package net.enthusia.staff.domain.application;

import java.time.Clock;
import java.util.List;
import java.util.Objects;
import net.enthusia.staff.common.SecureIdentifiers;
import net.enthusia.staff.domain.OperationalMode;
import net.enthusia.staff.domain.auth.AuthorizationPolicy;
import net.enthusia.staff.domain.auth.ModerationAction;
import net.enthusia.staff.domain.auth.StaffRank;
import net.enthusia.staff.domain.escalation.EscalationDecision;
import net.enthusia.staff.domain.escalation.EscalationEngine;
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
        return createConfirmed(request, mode, null);
    }

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
                policies.activeVersion(),
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
        ReasonPolicy policy = policies.find(request.reasonId()).orElse(null);
        if (policy == null) {
            return new PunishmentEvaluation.Rejected("UNKNOWN_REASON", "The configured reason does not exist");
        }
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
        List<SanctionSpec> sanctions = selectedSanctions(request, decision);
        if (sanctions == null) {
            return new PunishmentEvaluation.Rejected(
                    "FORBIDDEN_OVERRIDE",
                    "The requested override exceeds the actor's authority"
            );
        }
        return new PunishmentEvaluation.Allowed(new PunishmentAssessment(policy, decision, sanctions));
    }

    private List<SanctionSpec> selectedSanctions(
            CreatePunishmentRequest request,
            EscalationDecision decision
    ) {
        if (!request.usesOverride()) {
            return decision.selectedStep().sanctions();
        }
        if (!authorization.permits(request.actor(), ModerationAction.USE_CUSTOM_COMBINATION)) {
            return null;
        }
        return request.overrideSanctions();
    }
}
