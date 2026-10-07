package net.enthusia.staff.domain.policyv2.enforcement;

import java.util.Objects;
import java.util.UUID;
import net.enthusia.staff.domain.policyv2.RemedySpec;
import net.enthusia.staff.domain.policyv2.enforcement.PolicyV2RemedyEnforcement.ConditionType;
import net.enthusia.staff.domain.policyv2.enforcement.PolicyV2RemedyEnforcement.Scope;

public final class PolicyV2RemedyActions {
    private PolicyV2RemedyActions() {
    }

    public static PolicyV2RemedyService.EnforcementAction contentRemoval(
            ContentRemovalGateway gateway,
            String contentReference
    ) {
        Objects.requireNonNull(gateway, "gateway");
        String checkedReference = PolicyV2RemedyEnforcement.requireText(
                contentReference, "content reference", 512);
        return (enforcement, operationId) -> {
            requireTypeAndScope(enforcement, RemedySpec.Type.REMOVE_CONTENT, Scope.CONTENT);
            gateway.remove(operationId, enforcement.caseId(), enforcement.subjectId(), checkedReference);
        };
    }

    public static PolicyV2RemedyService.EnforcementAction restriction(
            Scope scope,
            RestrictionGateway gateway
    ) {
        requireRestrictionScope(scope);
        Objects.requireNonNull(gateway, "gateway");
        return (enforcement, operationId) -> {
            requireManualRestriction(enforcement, scope);
            gateway.apply(operationId, enforcement.caseId(), enforcement.subjectId());
        };
    }

    public static PolicyV2RemedyService.CompletionAction restrictionRemoval(
            Scope scope,
            RestrictionGateway gateway
    ) {
        requireRestrictionScope(scope);
        Objects.requireNonNull(gateway, "gateway");
        return (enforcement, operationId) -> {
            requireManualRestriction(enforcement, scope);
            gateway.remove(operationId, enforcement.caseId(), enforcement.subjectId());
        };
    }

    private static void requireRestrictionScope(Scope scope) {
        Objects.requireNonNull(scope, "scope");
        if (scope != Scope.REPORT_SUBMISSION
                && scope != Scope.MARKET_ACCESS
                && scope != Scope.REPUTATION_ACCESS) {
            throw new IllegalArgumentException("scope is not an externally enforced restriction");
        }
    }

    private static void requireManualRestriction(PolicyV2RemedyEnforcement enforcement, Scope scope) {
        requireTypeAndScope(enforcement, RemedySpec.Type.ACCESS_RESTRICTION, scope);
        if (enforcement.condition().type() != ConditionType.MANUAL) {
            throw new IllegalArgumentException("external restrictions require a manual condition");
        }
    }

    private static void requireTypeAndScope(
            PolicyV2RemedyEnforcement enforcement,
            RemedySpec.Type type,
            Scope scope
    ) {
        Objects.requireNonNull(enforcement, "enforcement");
        if (enforcement.remedyType() != type || enforcement.scope() != scope) {
            throw new IllegalArgumentException("enforcement record does not match the requested remedy action");
        }
    }

    @FunctionalInterface
    public interface ContentRemovalGateway {
        void remove(UUID operationId, String caseId, UUID subjectId, String contentReference);
    }

    public interface RestrictionGateway {
        void apply(UUID operationId, String caseId, UUID subjectId);

        void remove(UUID operationId, String caseId, UUID subjectId);
    }
}
