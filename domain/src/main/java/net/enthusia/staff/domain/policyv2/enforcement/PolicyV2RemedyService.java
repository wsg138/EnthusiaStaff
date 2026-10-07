package net.enthusia.staff.domain.policyv2.enforcement;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import net.enthusia.staff.domain.auth.Actor;
import net.enthusia.staff.domain.auth.AuthorizationPolicy;
import net.enthusia.staff.domain.auth.ModerationAction;
import net.enthusia.staff.domain.policyv2.enforcement.PolicyV2EnforcementStore.RegisterRequest;
import net.enthusia.staff.domain.policyv2.enforcement.PolicyV2EnforcementStore.TransitionRequest;
import net.enthusia.staff.domain.policyv2.enforcement.PolicyV2RemedyEnforcement.Condition;
import net.enthusia.staff.domain.policyv2.enforcement.PolicyV2RemedyEnforcement.Lifecycle;
import net.enthusia.staff.domain.policyv2.enforcement.PolicyV2RemedyEnforcement.Scope;
import net.enthusia.staff.domain.policyv2.persistence.PolicyV2Store;

public final class PolicyV2RemedyService {
    private final PolicyV2Store canonical;
    private final PolicyV2EnforcementStore enforcement;
    private final AuthorizationPolicy authorization;
    private final UUID systemActorId;

    public PolicyV2RemedyService(
            PolicyV2Store canonical,
            PolicyV2EnforcementStore enforcement,
            AuthorizationPolicy authorization,
            UUID systemActorId
    ) {
        this.canonical = Objects.requireNonNull(canonical, "canonical");
        this.enforcement = Objects.requireNonNull(enforcement, "enforcement");
        this.authorization = Objects.requireNonNull(authorization, "authorization");
        this.systemActorId = Objects.requireNonNull(systemActorId, "systemActorId");
    }

    public PolicyV2RemedyEnforcement register(Actor actor, RegisterCommand command) {
        requireAuthorized(actor, ModerationAction.ENFORCE_POLICY_REMEDY);
        Objects.requireNonNull(command, "command");
        PolicyV2Store.CaseRecord policyCase = requireCase(command.caseId());
        PolicyV2Store.RemedyRecord remedy = requireRemedy(policyCase, command.remedyId());
        requireRegisterable(policyCase, remedy, command);
        return enforcement.register(new RegisterRequest(
                command.caseId(),
                command.remedyId(),
                command.subjectId(),
                remedy.remedy().type(),
                command.scope(),
                command.condition(),
                actor.id(),
                internalOperationKey("register", command.operationKey()),
                command.occurredAt()
        ));
    }

    public PolicyV2RemedyEnforcement enforce(
            Actor actor,
            String caseId,
            String remedyId,
            long expectedRevision,
            EnforcementAction action,
            String operationKey,
            Instant occurredAt
    ) {
        requireAuthorized(actor, ModerationAction.ENFORCE_POLICY_REMEDY);
        Objects.requireNonNull(action, "action");
        PolicyV2RemedyEnforcement current = requireEnforcement(caseId, remedyId);
        PolicyV2RemedyEnforcement replay = completedEnforcementReplay(current, expectedRevision);
        if (replay != null) {
            return replay;
        }
        requireEnforcementFence(current, expectedRevision);
        UUID providerOperation = operationId("enforce", operationKey, caseId, remedyId);
        action.apply(current, providerOperation);
        return transition(
                current,
                expectedRevision,
                Lifecycle.ENFORCED,
                actor.id(),
                "Required remedy enforcement completed",
                internalOperationKey("enforce", operationKey),
                occurredAt
        );
    }

    public PolicyV2RemedyEnforcement satisfy(
            Actor actor,
            String caseId,
            String remedyId,
            long expectedRevision,
            String reason,
            String operationKey,
            Instant occurredAt
    ) {
        requireAuthorized(actor, ModerationAction.SATISFY_POLICY_REMEDY);
        return finish(new CompletionRequest(
                caseId,
                remedyId,
                expectedRevision,
                new CompletionDetails(
                        Lifecycle.SATISFIED,
                        PolicyV2Store.RemedyStatus.SATISFIED,
                        actor.id(),
                        reason,
                        operationKey,
                        occurredAt,
                        null
                )
        ));
    }

    public PolicyV2RemedyEnforcement satisfyWithCleanup(
            Actor actor,
            String caseId,
            String remedyId,
            long expectedRevision,
            CompletionAction cleanup,
            String reason,
            String operationKey,
            Instant occurredAt
    ) {
        requireAuthorized(actor, ModerationAction.SATISFY_POLICY_REMEDY);
        return finish(new CompletionRequest(
                caseId,
                remedyId,
                expectedRevision,
                new CompletionDetails(
                        Lifecycle.SATISFIED,
                        PolicyV2Store.RemedyStatus.SATISFIED,
                        actor.id(),
                        reason,
                        operationKey,
                        occurredAt,
                        Objects.requireNonNull(cleanup, "cleanup")
                )
        ));
    }

    public PolicyV2RemedyEnforcement waive(
            Actor actor,
            String caseId,
            String remedyId,
            long expectedRevision,
            String reason,
            String operationKey,
            Instant occurredAt
    ) {
        requireAuthorized(actor, ModerationAction.WAIVE_POLICY_REMEDY);
        return finish(new CompletionRequest(
                caseId,
                remedyId,
                expectedRevision,
                new CompletionDetails(
                        Lifecycle.WAIVED,
                        PolicyV2Store.RemedyStatus.WAIVED,
                        actor.id(),
                        reason,
                        operationKey,
                        occurredAt,
                        null
                )
        ));
    }

    public PolicyV2RemedyEnforcement waiveWithCleanup(
            Actor actor,
            String caseId,
            String remedyId,
            long expectedRevision,
            CompletionAction cleanup,
            String reason,
            String operationKey,
            Instant occurredAt
    ) {
        requireAuthorized(actor, ModerationAction.WAIVE_POLICY_REMEDY);
        return finish(new CompletionRequest(
                caseId,
                remedyId,
                expectedRevision,
                new CompletionDetails(
                        Lifecycle.WAIVED,
                        PolicyV2Store.RemedyStatus.WAIVED,
                        actor.id(),
                        reason,
                        operationKey,
                        occurredAt,
                        Objects.requireNonNull(cleanup, "cleanup")
                )
        ));
    }

    public PolicyV2RemedyEnforcement satisfyAutomatically(
            String caseId,
            String remedyId,
            long expectedRevision,
            String reason,
            String operationKey,
            Instant occurredAt
    ) {
        return finish(new CompletionRequest(
                caseId,
                remedyId,
                expectedRevision,
                new CompletionDetails(
                        Lifecycle.SATISFIED,
                        PolicyV2Store.RemedyStatus.SATISFIED,
                        systemActorId,
                        reason,
                        operationKey,
                        occurredAt,
                        null
                )
        ));
    }

    private PolicyV2RemedyEnforcement finish(CompletionRequest request) {
        CompletionDetails details = request.details();
        PolicyV2RemedyEnforcement current = requireEnforcement(request.caseId(), request.remedyId());
        if (current.lifecycle() == details.lifecycle()) {
            return current;
        }
        requireCompletionFence(current, request.expectedRevision());
        runCleanup(current, request);
        updateCanonical(
                request.caseId(),
                request.remedyId(),
                details.canonicalStatus(),
                details.actorId(),
                details.reason(),
                details.operationKey(),
                details.occurredAt()
        );
        return transition(
                current,
                request.expectedRevision(),
                details.lifecycle(),
                details.actorId(),
                details.reason(),
                internalOperationKey("projection", details.operationKey()),
                details.occurredAt()
        );
    }

    private static void runCleanup(
            PolicyV2RemedyEnforcement current,
            CompletionRequest request
    ) {
        CompletionAction cleanup = request.details().cleanup();
        if (cleanup != null) {
            cleanup.apply(
                    current,
                    operationId(
                            "complete",
                            request.details().operationKey(),
                            request.caseId(),
                            request.remedyId()
                    )
            );
        }
    }

    private void updateCanonical(
            String caseId,
            String remedyId,
            PolicyV2Store.RemedyStatus target,
            UUID actorId,
            String reason,
            String operationKey,
            Instant occurredAt
    ) {
        PolicyV2Store.RemedyRecord remedy = requireRemedy(requireCase(caseId), remedyId);
        if (remedy.status() == target) {
            return;
        }
        if (remedy.status() != PolicyV2Store.RemedyStatus.REQUIRED) {
            throw new PolicyV2Store.Conflict("Policy v2 remedy already has a different terminal status");
        }
        canonical.updateRemedy(new PolicyV2Store.RemedyUpdateRequest(
                caseId,
                remedyId,
                remedy.revision(),
                target,
                actorId,
                reason,
                internalOperationKey("canonical", operationKey),
                occurredAt
        ));
    }

    private PolicyV2RemedyEnforcement transition(
            PolicyV2RemedyEnforcement current,
            long expectedRevision,
            Lifecycle lifecycle,
            UUID actorId,
            String reason,
            String operationKey,
            Instant occurredAt
    ) {
        return enforcement.transition(new TransitionRequest(
                current.caseId(),
                current.remedyId(),
                expectedRevision,
                lifecycle,
                actorId,
                reason,
                operationKey,
                occurredAt
        ));
    }

    private void requireRegisterable(
            PolicyV2Store.CaseRecord policyCase,
            PolicyV2Store.RemedyRecord remedy,
            RegisterCommand command
    ) {
        PolicyV2EnforcementPolicy.requireSafeOutcome(
                policyCase.resolution().offenseId(),
                policyCase.currentSanctions().sanctions()
        );
        PolicyV2EnforcementPolicy.requireBinding(remedy.remedy(), command.scope(), command.condition());
        if (remedy.status() != PolicyV2Store.RemedyStatus.REQUIRED) {
            throw new PolicyV2Store.Conflict("Only a required remedy can enter enforcement");
        }
    }

    private static PolicyV2RemedyEnforcement completedEnforcementReplay(
            PolicyV2RemedyEnforcement current,
            long expectedRevision
    ) {
        if (current.lifecycle() == Lifecycle.ENFORCED && current.revision() == expectedRevision + 1L) {
            return current;
        }
        return null;
    }

    private static void requireEnforcementFence(PolicyV2RemedyEnforcement current, long expectedRevision) {
        if (current.lifecycle() != Lifecycle.REQUIRED || current.revision() != expectedRevision) {
            throw new PolicyV2Store.Conflict("Policy v2 enforcement command is stale");
        }
    }

    private static void requireCompletionFence(PolicyV2RemedyEnforcement current, long expectedRevision) {
        if (!current.active() || current.revision() != expectedRevision) {
            throw new PolicyV2Store.Conflict("Policy v2 remedy completion command is stale");
        }
    }

    private PolicyV2Store.CaseRecord requireCase(String caseId) {
        return canonical.findCase(caseId)
                .orElseThrow(() -> new PolicyV2Store.MissingRecord("Policy v2 case does not exist"));
    }

    private static PolicyV2Store.RemedyRecord requireRemedy(
            PolicyV2Store.CaseRecord policyCase,
            String remedyId
    ) {
        return policyCase.remedies().stream()
                .filter(record -> record.remedy().id().equals(remedyId))
                .findFirst()
                .orElseThrow(() -> new PolicyV2Store.MissingRecord("Policy v2 remedy does not exist"));
    }

    private PolicyV2RemedyEnforcement requireEnforcement(String caseId, String remedyId) {
        return enforcement.find(caseId, remedyId)
                .orElseThrow(() -> new PolicyV2Store.MissingRecord("Policy v2 enforcement record does not exist"));
    }

    private void requireAuthorized(Actor actor, ModerationAction action) {
        if (actor == null || !authorization.permits(actor, action)) {
            throw new SecurityException("Actor cannot perform Policy v2 remedy lifecycle action " + action);
        }
    }

    private static String internalOperationKey(String phase, String operationKey) {
        String checked = PolicyV2RemedyEnforcement.requireText(operationKey, "operation key", 512);
        return "p2:" + phase + ':' + UUID.nameUUIDFromBytes(checked.getBytes(StandardCharsets.UTF_8));
    }

    private static UUID operationId(String phase, String operationKey, String caseId, String remedyId) {
        String value = phase + '|' + operationKey + '|' + caseId + '|' + remedyId;
        return UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8));
    }

    private record CompletionRequest(
            String caseId,
            String remedyId,
            long expectedRevision,
            CompletionDetails details
    ) {
    }

    private record CompletionDetails(
            Lifecycle lifecycle,
            PolicyV2Store.RemedyStatus canonicalStatus,
            UUID actorId,
            String reason,
            String operationKey,
            Instant occurredAt,
            CompletionAction cleanup
    ) {
    }

    @FunctionalInterface
    public interface EnforcementAction {
        /**
         * Applies one idempotent external enforcement action. The operation ID is stable across retries.
         */
        void apply(PolicyV2RemedyEnforcement enforcement, UUID operationId);
    }

    @FunctionalInterface
    public interface CompletionAction {
        /**
         * Applies idempotent cleanup/restoration before a terminal lifecycle change.
         */
        void apply(PolicyV2RemedyEnforcement enforcement, UUID operationId);
    }

    public record RegisterCommand(
            String caseId,
            String remedyId,
            UUID subjectId,
            Scope scope,
            Condition condition,
            String operationKey,
            Instant occurredAt
    ) {
        public RegisterCommand {
            caseId = PolicyV2RemedyEnforcement.requireText(caseId, "case id", 64);
            remedyId = PolicyV2RemedyEnforcement.requireText(remedyId, "remedy id", 96);
            operationKey = PolicyV2RemedyEnforcement.requireText(operationKey, "operation key", 512);
            if (subjectId == null || scope == null || condition == null || occurredAt == null) {
                throw new IllegalArgumentException("remedy registration command fields must be present");
            }
        }
    }
}
