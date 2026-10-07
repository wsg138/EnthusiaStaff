package net.enthusia.staff.domain.policyv2.appeal;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.enthusia.staff.domain.auth.Actor;
import net.enthusia.staff.domain.auth.AuthorizationPolicy;
import net.enthusia.staff.domain.auth.ModerationAction;
import net.enthusia.staff.domain.policyv2.BehavioralHistoryEntry;
import net.enthusia.staff.domain.policyv2.CaseRevision;
import net.enthusia.staff.domain.policyv2.enforcement.PolicyV2EnforcementStore;
import net.enthusia.staff.domain.policyv2.enforcement.PolicyV2RemedyEnforcement;
import net.enthusia.staff.domain.policyv2.enforcement.PolicyV2RemedyEnforcement.Lifecycle;
import net.enthusia.staff.domain.policyv2.enforcement.PolicyV2RemedyEnforcement.Scope;
import net.enthusia.staff.domain.policyv2.persistence.PolicyV2Store;
import net.enthusia.staff.domain.policyv2.persistence.PolicyV2Store.AppealEventType;
import net.enthusia.staff.domain.policyv2.persistence.PolicyV2Store.RemedyStatus;
import net.enthusia.staff.domain.policyv2.appeal.PolicyV2FullOverturnStore.Operation;
import net.enthusia.staff.domain.policyv2.appeal.PolicyV2FullOverturnStore.Plan;
import net.enthusia.staff.domain.policyv2.appeal.PolicyV2FullOverturnStore.RemedyTarget;
import net.enthusia.staff.domain.policyv2.appeal.PolicyV2FullOverturnStore.Stage;
import net.enthusia.staff.domain.sanction.SanctionSpec;

/**
 * Durable saga for a factual full overturn. Leniency and reclassification remain
 * separate PolicyV2Store operations and are never routed through this class.
 */
public final class PolicyV2FullOverturnOrchestrator {
    private final PolicyV2Store canonical;
    private final PolicyV2EnforcementStore enforcement;
    private final PolicyV2FullOverturnStore operations;
    private final AuthorizationPolicy authorization;
    private final SanctionTerminationProvider sanctions;
    private final RemedyCleanupProvider remedies;
    private final FailureProbe failures;

    public PolicyV2FullOverturnOrchestrator(
            PolicyV2Store canonical,
            PolicyV2EnforcementStore enforcement,
            PolicyV2FullOverturnStore operations,
            AuthorizationPolicy authorization,
            SanctionTerminationProvider sanctions,
            RemedyCleanupProvider remedies
    ) {
        this(canonical, enforcement, operations, authorization, sanctions, remedies, checkpoint -> { });
    }

    public PolicyV2FullOverturnOrchestrator(
            PolicyV2Store canonical,
            PolicyV2EnforcementStore enforcement,
            PolicyV2FullOverturnStore operations,
            AuthorizationPolicy authorization,
            SanctionTerminationProvider sanctions,
            RemedyCleanupProvider remedies,
            FailureProbe failures
    ) {
        this.canonical = Objects.requireNonNull(canonical, "canonical");
        this.enforcement = Objects.requireNonNull(enforcement, "enforcement");
        this.operations = Objects.requireNonNull(operations, "operations");
        this.authorization = Objects.requireNonNull(authorization, "authorization");
        this.sanctions = Objects.requireNonNull(sanctions, "sanctions");
        this.remedies = Objects.requireNonNull(remedies, "remedies");
        this.failures = Objects.requireNonNull(failures, "failures");
    }

    public Operation execute(Command command) {
        Objects.requireNonNull(command, "command");
        Optional<Operation> existing = operations.find(command.operationId());
        Operation operation = existing.map(value -> requireReplay(value, command))
                .orElseGet(() -> begin(command));
        return run(operation);
    }

    public Operation resume(UUID operationId) {
        Objects.requireNonNull(operationId, "operationId");
        Operation operation = operations.find(operationId)
                .orElseThrow(() -> new PolicyV2Store.MissingRecord("Full-overturn operation does not exist"));
        return run(operation);
    }

    private Operation run(Operation operation) {
        Operation current = operation;
        while (!current.completed()) {
            current = switch (current.stage()) {
                case STARTED -> overturnFinding(current);
                case FINDING_OVERTURNED -> terminateSanctions(current);
                case SANCTIONS_TERMINATED -> cleanRemedies(current);
                case REMEDIES_CLEANED -> completeAppeal(current);
                case COMPLETED -> current;
            };
        }
        return current;
    }

    private Operation begin(Command command) {
        requireAuthorized(command.actor(), ModerationAction.FULL_OVERTURN);
        PolicyV2Store.CaseRecord policyCase = requireCase(command.caseId());
        Plan plan = plan(policyCase, command.actor());
        return operations.begin(new PolicyV2FullOverturnStore.BeginRequest(
                command.operationId(),
                command.caseId(),
                command.appealReference(),
                command.actor().id(),
                command.reason(),
                plan,
                command.occurredAt()
        ));
    }

    private Plan plan(PolicyV2Store.CaseRecord policyCase, Actor actor) {
        List<RemedyTarget> targets = new ArrayList<>();
        for (PolicyV2Store.RemedyRecord remedy : policyCase.remedies()) {
            Optional<PolicyV2RemedyEnforcement> current =
                    enforcement.find(policyCase.caseId(), remedy.remedy().id());
            if (remedy.status() == RemedyStatus.REQUIRED || active(current)) {
                targets.add(remedyTarget(remedy, current, actor));
            }
        }
        return new Plan(
                policyCase.effectiveFinding().map(finding -> finding.offenseId()),
                policyCase.findingRevision(),
                policyCase.sanctionRevision(),
                policyCase.currentSanctions().sanctions(),
                targets
        );
    }

    private RemedyTarget remedyTarget(
            PolicyV2Store.RemedyRecord remedy,
            Optional<PolicyV2RemedyEnforcement> current,
            Actor actor
    ) {
        boolean cleanup = current.map(value -> value.lifecycle() == Lifecycle.ENFORCED).orElse(false);
        if (cleanup) {
            requireCleanupAuthority(actor, current.orElseThrow().scope());
        }
        return new RemedyTarget(
                remedy.remedy().id(),
                remedy.revision(),
                current.map(PolicyV2RemedyEnforcement::revision),
                cleanup
        );
    }

    private static boolean active(Optional<PolicyV2RemedyEnforcement> enforcement) {
        return enforcement.map(value -> !value.lifecycle().terminal()).orElse(false);
    }

    private Operation overturnFinding(Operation operation) {
        failures.hit(Checkpoint.BEFORE_FINDING_OVERTURN);
        Optional<String> offenseId = operation.plan().findingOffenseId();
        PolicyV2Store.CaseRecord current = requireCase(operation.caseId());
        if (offenseId.isPresent() && current.findingState() != BehavioralHistoryEntry.FindingState.OVERTURNED) {
            canonical.reviseFinding(new PolicyV2Store.FindingRevisionRequest(
                    operation.caseId(),
                    operation.plan().findingRevision(),
                    new CaseRevision.FindingOverturn(offenseId.orElseThrow(), operation.reason()),
                    Optional.empty(),
                    operation.actorId(),
                    Optional.of(operation.appealReference()),
                    operationKey(operation, "finding", null),
                    operation.createdAt()
            ));
        }
        failures.hit(Checkpoint.AFTER_FINDING_OVERTURN);
        return advance(operation, Stage.FINDING_OVERTURNED);
    }

    private Operation terminateSanctions(Operation operation) {
        failures.hit(Checkpoint.BEFORE_SANCTION_TERMINATION);
        List<SanctionSpec> planned = operation.plan().sanctions();
        PolicyV2Store.CaseRecord current = requireCase(operation.caseId());
        if (!planned.isEmpty() && !current.currentSanctions().sanctions().isEmpty()) {
            if (current.sanctionRevision() != operation.plan().sanctionRevision()) {
                throw new PolicyV2Store.Conflict("Full-overturn sanction fence is stale");
            }
            sanctions.terminate(providerId(operation, "sanctions", null), operation.caseId(), planned);
            failures.hit(Checkpoint.AFTER_SANCTION_TERMINATION);
            canonical.reviseSanctions(new PolicyV2Store.SanctionRevisionRequest(
                    operation.caseId(),
                    operation.plan().sanctionRevision(),
                    PolicyV2Store.SanctionChangeKind.OTHER,
                    new CaseRevision.SanctionRevision(operation.reason(), List.of()),
                    operation.actorId(),
                    Optional.of(operation.appealReference()),
                    operationKey(operation, "sanctions", null),
                    operation.createdAt()
            ));
        }
        return advance(operation, Stage.SANCTIONS_TERMINATED);
    }

    private Operation cleanRemedies(Operation operation) {
        failures.hit(Checkpoint.BEFORE_REMEDY_CLEANUP);
        for (RemedyTarget target : operation.plan().remedies()) {
            cleanRemedy(operation, target);
        }
        return advance(operation, Stage.REMEDIES_CLEANED);
    }

    private void cleanRemedy(Operation operation, RemedyTarget target) {
        PolicyV2Store.RemedyRecord currentCanonical = currentRemedy(operation.caseId(), target.remedyId());
        Optional<PolicyV2RemedyEnforcement> currentEnforcement =
                enforcement.find(operation.caseId(), target.remedyId());
        requireCanonicalFence(target, currentCanonical);
        requireEnforcementFence(target, currentEnforcement);
        applyExternalCleanup(operation, target, currentEnforcement);
        failures.hit(Checkpoint.AFTER_REMEDY_CLEANUP);
        waiveCanonical(operation, target);
        waiveEnforcement(operation, target);
    }

    private void applyExternalCleanup(
            Operation operation,
            RemedyTarget target,
            Optional<PolicyV2RemedyEnforcement> current
    ) {
        if (!target.cleanupRequired() || current.isEmpty() || current.orElseThrow().lifecycle().terminal()) {
            return;
        }
        PolicyV2RemedyEnforcement enforcementRecord = current.orElseThrow();
        remedies.cleanup(providerId(operation, "remedy", target.remedyId()), enforcementRecord);
    }

    private void waiveCanonical(Operation operation, RemedyTarget target) {
        PolicyV2Store.RemedyRecord current = currentRemedy(operation.caseId(), target.remedyId());
        if (current.status() != RemedyStatus.REQUIRED) {
            return;
        }
        if (current.revision() != target.canonicalRevision()) {
            throw new PolicyV2Store.Conflict("Full-overturn remedy fence is stale");
        }
        canonical.updateRemedy(new PolicyV2Store.RemedyUpdateRequest(
                operation.caseId(),
                target.remedyId(),
                target.canonicalRevision(),
                RemedyStatus.WAIVED,
                operation.actorId(),
                operation.reason(),
                operationKey(operation, "canonical-remedy", target.remedyId()),
                operation.createdAt()
        ));
    }

    private void waiveEnforcement(Operation operation, RemedyTarget target) {
        Optional<PolicyV2RemedyEnforcement> current = enforcement.find(operation.caseId(), target.remedyId());
        if (current.isEmpty() || current.orElseThrow().lifecycle().terminal()) {
            return;
        }
        enforcement.transition(new PolicyV2EnforcementStore.TransitionRequest(
                operation.caseId(),
                target.remedyId(),
                target.enforcementRevision().orElseThrow(),
                Lifecycle.WAIVED,
                operation.actorId(),
                operation.reason(),
                operationKey(operation, "enforcement-remedy", target.remedyId()),
                operation.createdAt()
        ));
    }

    private Operation completeAppeal(Operation operation) {
        failures.hit(Checkpoint.BEFORE_FINAL_APPEAL_COMPLETION);
        appendAppeal(operation, AppealEventType.APPROVED, "Full overturn approved");
        appendAppeal(operation, AppealEventType.REVISION_APPLIED, "Full overturn applied");
        return advance(operation, Stage.COMPLETED);
    }

    private void appendAppeal(Operation operation, AppealEventType type, String note) {
        canonical.appendAppealEvent(new PolicyV2Store.AppealEventRequest(
                operation.caseId(),
                operation.appealReference(),
                type,
                Optional.of(operation.actorId()),
                note + ": " + operation.reason(),
                operationKey(operation, "appeal-" + type.name(), null),
                operation.createdAt()
        ));
    }

    private Operation advance(Operation operation, Stage next) {
        return operations.advance(
                operation.operationId(),
                operation.revision(),
                operation.stage(),
                next,
                operation.createdAt()
        );
    }

    private PolicyV2Store.CaseRecord requireCase(String caseId) {
        return canonical.findCase(caseId)
                .orElseThrow(() -> new PolicyV2Store.MissingRecord("Policy v2 case does not exist"));
    }

    private PolicyV2Store.RemedyRecord currentRemedy(String caseId, String remedyId) {
        return requireCase(caseId).remedies().stream()
                .filter(record -> record.remedy().id().equals(remedyId))
                .findFirst()
                .orElseThrow(() -> new PolicyV2Store.MissingRecord("Policy v2 remedy does not exist"));
    }

    private static void requireCanonicalFence(RemedyTarget target, PolicyV2Store.RemedyRecord current) {
        if (current.status() == RemedyStatus.REQUIRED && current.revision() != target.canonicalRevision()) {
            throw new PolicyV2Store.Conflict("Full-overturn remedy fence is stale");
        }
    }

    private static void requireEnforcementFence(
            RemedyTarget target,
            Optional<PolicyV2RemedyEnforcement> current
    ) {
        if (current.isEmpty()) {
            if (target.enforcementRevision().isPresent()) {
                throw new PolicyV2Store.Conflict("Full-overturn enforcement record disappeared");
            }
            return;
        }
        PolicyV2RemedyEnforcement value = current.orElseThrow();
        if (value.lifecycle().terminal()) {
            return;
        }
        if (target.enforcementRevision().isEmpty()
                || value.revision() != target.enforcementRevision().orElseThrow()) {
            throw new PolicyV2Store.Conflict("Full-overturn enforcement fence is stale");
        }
    }

    private void requireCleanupAuthority(Actor actor, Scope scope) {
        ModerationAction action = switch (scope) {
            case MARKET_ACCESS -> ModerationAction.MODIFY_MARKET_RESTRICTION;
            case REPUTATION_ACCESS -> ModerationAction.MODIFY_REPUTATION_RESTRICTION;
            case ASSET -> ModerationAction.RESTORE_ASSETS;
            case NETWORK_ACCESS, REPORT_SUBMISSION, CONTENT -> null;
        };
        if (action != null) {
            requireAuthorized(actor, action);
        }
    }

    private void requireAuthorized(Actor actor, ModerationAction action) {
        if (actor == null || !authorization.permits(actor, action)) {
            throw new SecurityException("Actor cannot perform Policy v2 full overturn action " + action);
        }
    }

    private static Operation requireReplay(Operation operation, Command command) {
        boolean matches = operation.caseId().equals(command.caseId())
                && operation.appealReference().equals(command.appealReference())
                && operation.actorId().equals(command.actor().id())
                && operation.reason().equals(command.reason())
                && operation.createdAt().equals(command.occurredAt());
        if (!matches) {
            throw new PolicyV2Store.Conflict("Full-overturn operation ID was reused for a different request");
        }
        return operation;
    }

    private static String operationKey(Operation operation, String phase, String remedyId) {
        return "p2:fo:" + stableId(operation.operationId(), phase, remedyId);
    }

    private static UUID providerId(Operation operation, String phase, String remedyId) {
        return stableId(operation.operationId(), phase, remedyId);
    }

    private static UUID stableId(UUID operationId, String phase, String remedyId) {
        String input = operationId + "|" + phase + "|" + (remedyId == null ? "" : remedyId);
        return UUID.nameUUIDFromBytes(input.getBytes(StandardCharsets.UTF_8));
    }

    public enum Checkpoint {
        BEFORE_FINDING_OVERTURN,
        AFTER_FINDING_OVERTURN,
        BEFORE_SANCTION_TERMINATION,
        AFTER_SANCTION_TERMINATION,
        BEFORE_REMEDY_CLEANUP,
        AFTER_REMEDY_CLEANUP,
        BEFORE_FINAL_APPEAL_COMPLETION
    }

    @FunctionalInterface
    public interface FailureProbe {
        void hit(Checkpoint checkpoint);
    }

    @FunctionalInterface
    public interface SanctionTerminationProvider {
        void terminate(UUID operationId, String caseId, List<SanctionSpec> sanctions);
    }

    @FunctionalInterface
    public interface RemedyCleanupProvider {
        void cleanup(UUID operationId, PolicyV2RemedyEnforcement enforcement);
    }

    public record Command(
            UUID operationId,
            String caseId,
            String appealReference,
            Actor actor,
            String reason,
            Instant occurredAt
    ) {
        public Command {
            if (operationId == null || actor == null || occurredAt == null) {
                throw new IllegalArgumentException("full-overturn command fields must be present");
            }
            caseId = requireText(caseId, "case id", 64);
            appealReference = requireText(appealReference, "appeal reference", 128);
            reason = requireText(reason, "full-overturn reason", 1_000);
        }

        private static String requireText(String value, String field, int maximumLength) {
            if (value == null || value.isBlank() || value.length() > maximumLength) {
                throw new IllegalArgumentException(field + " is blank or exceeds " + maximumLength);
            }
            return value.trim();
        }
    }
}
