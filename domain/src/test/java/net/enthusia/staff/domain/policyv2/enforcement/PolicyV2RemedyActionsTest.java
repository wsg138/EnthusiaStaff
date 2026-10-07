package net.enthusia.staff.domain.policyv2.enforcement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.enthusia.staff.domain.policyv2.RemedySpec;
import net.enthusia.staff.domain.policyv2.enforcement.PolicyV2RemedyEnforcement.Condition;
import net.enthusia.staff.domain.policyv2.enforcement.PolicyV2RemedyEnforcement.Lifecycle;
import net.enthusia.staff.domain.policyv2.enforcement.PolicyV2RemedyEnforcement.Scope;
import org.junit.jupiter.api.Test;

class PolicyV2RemedyActionsTest {
    private static final Instant NOW = Instant.parse("2026-10-07T02:00:00Z");

    @Test
    void contentRemovalPassesOnlyAllowlistedFieldsToGateway() {
        UUID subject = UUID.randomUUID();
        UUID operation = UUID.randomUUID();
        List<String> calls = new ArrayList<>();
        var action = PolicyV2RemedyActions.contentRemoval(
                (operationId, caseId, subjectId, reference) ->
                        calls.add(operationId + "|" + caseId + "|" + subjectId + "|" + reference),
                "message:123"
        );

        action.apply(content(subject), operation);

        assertEquals(List.of(
                operation + "|CASE000000000010|" + subject + "|message:123"
        ), calls);
    }

    @Test
    void restrictionApplyAndRemovalUseTheSameTypedBoundary() {
        UUID subject = UUID.randomUUID();
        UUID applyOperation = UUID.randomUUID();
        UUID removeOperation = UUID.randomUUID();
        List<String> calls = new ArrayList<>();
        PolicyV2RemedyActions.RestrictionGateway gateway = new PolicyV2RemedyActions.RestrictionGateway() {
            @Override
            public void apply(UUID operationId, String caseId, UUID subjectId) {
                calls.add("apply|" + operationId + "|" + caseId + "|" + subjectId);
            }

            @Override
            public void remove(UUID operationId, String caseId, UUID subjectId) {
                calls.add("remove|" + operationId + "|" + caseId + "|" + subjectId);
            }
        };

        PolicyV2RemedyEnforcement enforcement = restriction(subject, Scope.MARKET_ACCESS);
        PolicyV2RemedyActions.restriction(Scope.MARKET_ACCESS, gateway)
                .apply(enforcement, applyOperation);
        PolicyV2RemedyActions.restrictionRemoval(Scope.MARKET_ACCESS, gateway)
                .apply(enforcement, removeOperation);

        assertEquals(List.of(
                "apply|" + applyOperation + "|CASE000000000011|" + subject,
                "remove|" + removeOperation + "|CASE000000000011|" + subject
        ), calls);
    }

    @Test
    void mismatchedScopesFailBeforeProviderMutation() {
        PolicyV2RemedyEnforcement enforcement = restriction(UUID.randomUUID(), Scope.REPORT_SUBMISSION);
        PolicyV2RemedyActions.RestrictionGateway gateway = new NoOpRestrictionGateway();

        assertThrows(IllegalArgumentException.class, () ->
                PolicyV2RemedyActions.restriction(Scope.MARKET_ACCESS, gateway)
                        .apply(enforcement, UUID.randomUUID()));
        assertThrows(IllegalArgumentException.class, () ->
                PolicyV2RemedyActions.restriction(Scope.NETWORK_ACCESS, gateway));
    }

    private static PolicyV2RemedyEnforcement content(UUID subject) {
        return new PolicyV2RemedyEnforcement(
                "CASE000000000010",
                "remove-content",
                subject,
                RemedySpec.Type.REMOVE_CONTENT,
                Scope.CONTENT,
                Condition.manual(),
                Lifecycle.REQUIRED,
                0L,
                NOW
        );
    }

    private static PolicyV2RemedyEnforcement restriction(UUID subject, Scope scope) {
        return new PolicyV2RemedyEnforcement(
                "CASE000000000011",
                "restrict",
                subject,
                RemedySpec.Type.ACCESS_RESTRICTION,
                scope,
                Condition.manual(),
                Lifecycle.REQUIRED,
                0L,
                NOW
        );
    }

    private static final class NoOpRestrictionGateway implements PolicyV2RemedyActions.RestrictionGateway {
        @Override
        public void apply(UUID operationId, String caseId, UUID subjectId) {
        }

        @Override
        public void remove(UUID operationId, String caseId, UUID subjectId) {
        }
    }
}
