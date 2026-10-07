package net.enthusia.staff.domain.policyv2.enforcement;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.enthusia.staff.domain.policyv2.RemedySpec;
import net.enthusia.staff.domain.policyv2.enforcement.PolicyV2RemedyEnforcement.Condition;
import net.enthusia.staff.domain.policyv2.enforcement.PolicyV2RemedyEnforcement.Lifecycle;
import net.enthusia.staff.domain.policyv2.enforcement.PolicyV2RemedyEnforcement.Scope;

public interface PolicyV2EnforcementStore {
    PolicyV2RemedyEnforcement register(RegisterRequest request);

    Optional<PolicyV2RemedyEnforcement> find(String caseId, String remedyId);

    List<PolicyV2RemedyEnforcement> activeFor(UUID subjectId);

    PolicyV2RemedyEnforcement transition(TransitionRequest request);

    record RegisterRequest(
            String caseId,
            String remedyId,
            UUID subjectId,
            RemedySpec.Type remedyType,
            Scope scope,
            Condition condition,
            UUID actorId,
            String operationKey,
            Instant occurredAt
    ) {
        public RegisterRequest {
            caseId = PolicyV2RemedyEnforcement.requireText(caseId, "case id", 64);
            remedyId = PolicyV2RemedyEnforcement.requireText(remedyId, "remedy id", 96);
            operationKey = PolicyV2RemedyEnforcement.requireText(operationKey, "operation key", 128);
            if (subjectId == null || remedyType == null || scope == null || condition == null
                    || actorId == null || occurredAt == null) {
                throw new IllegalArgumentException("enforcement registration fields must be present");
            }
        }
    }

    record TransitionRequest(
            String caseId,
            String remedyId,
            long expectedRevision,
            Lifecycle lifecycle,
            UUID actorId,
            String reason,
            String operationKey,
            Instant occurredAt
    ) {
        public TransitionRequest {
            caseId = PolicyV2RemedyEnforcement.requireText(caseId, "case id", 64);
            remedyId = PolicyV2RemedyEnforcement.requireText(remedyId, "remedy id", 96);
            reason = PolicyV2RemedyEnforcement.requireText(reason, "transition reason", 1_000);
            operationKey = PolicyV2RemedyEnforcement.requireText(operationKey, "operation key", 128);
            if (expectedRevision < 0L || lifecycle == null || lifecycle == Lifecycle.REQUIRED
                    || actorId == null || occurredAt == null) {
                throw new IllegalArgumentException("enforcement transition fields are invalid");
            }
        }
    }
}
