package net.enthusia.staff.domain.policyv2.enforcement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.enthusia.staff.domain.policyv2.RemedySpec;
import net.enthusia.staff.domain.policyv2.enforcement.PolicyV2AccessEvaluator.Observation;
import net.enthusia.staff.domain.policyv2.enforcement.PolicyV2AccessEvaluator.VpnState;
import net.enthusia.staff.domain.policyv2.enforcement.PolicyV2RemedyEnforcement.Condition;
import net.enthusia.staff.domain.policyv2.enforcement.PolicyV2RemedyEnforcement.Lifecycle;
import net.enthusia.staff.domain.policyv2.enforcement.PolicyV2RemedyEnforcement.Scope;
import org.junit.jupiter.api.Test;

class PolicyV2AccessEvaluatorTest {
    private static final Instant NOW = Instant.parse("2026-10-07T01:00:00Z");

    @Test
    void usernameRemainsDeniedUntilCorrectionIsDurablyCleared() {
        UUID subject = UUID.randomUUID();
        MemoryStore store = new MemoryStore(List.of(record(
                "CASE000000000001",
                subject,
                RemedySpec.Type.CORRECT_PROFILE,
                Condition.username("BadName"),
                Scope.NETWORK_ACCESS
        )));
        PolicyV2AccessEvaluator evaluator = new PolicyV2AccessEvaluator(store);

        var prohibited = evaluator.evaluate(new Observation(subject, "BadName", VpnState.CLEAR, Map.of()));
        var corrected = evaluator.evaluate(new Observation(subject, "GoodName", VpnState.CLEAR, Map.of()));

        assertFalse(prohibited.allowed());
        assertTrue(prohibited.corrections().isEmpty());
        assertFalse(corrected.allowed());
        assertEquals(1, corrected.corrections().size());

        store.records.clear();
        assertTrue(evaluator.evaluate(new Observation(
                subject, "GoodName", VpnState.CLEAR, Map.of())).allowed());
    }

    @Test
    void vpnAndProfileChecksFailClosedWhenEvidenceIsNotReliable() {
        UUID subject = UUID.randomUUID();
        MemoryStore store = new MemoryStore(List.of(
                record(
                        "CASE000000000002",
                        subject,
                        RemedySpec.Type.ACCESS_RESTRICTION,
                        Condition.vpnApproval(),
                        Scope.NETWORK_ACCESS
                ),
                record(
                        "CASE000000000003",
                        subject,
                        RemedySpec.Type.CORRECT_PROFILE,
                        Condition.profileComponent("skinHash", "blocked-hash"),
                        Scope.NETWORK_ACCESS
                )
        ));
        PolicyV2AccessEvaluator evaluator = new PolicyV2AccessEvaluator(store);

        var unknown = evaluator.evaluate(new Observation(subject, "Player", VpnState.UNKNOWN, Map.of()));
        assertFalse(unknown.allowed());
        assertTrue(unknown.corrections().isEmpty());

        var corrected = evaluator.evaluate(new Observation(
                subject,
                "Player",
                VpnState.APPROVED,
                Map.of("skinHash", "clean-hash")
        ));
        assertFalse(corrected.allowed());
        assertEquals(2, corrected.corrections().size());
    }

    @Test
    void capabilityReadsDoNotWriteAuditOrLifecycleState() {
        UUID subject = UUID.randomUUID();
        MemoryStore store = new MemoryStore(List.of(record(
                "CASE000000000004",
                subject,
                RemedySpec.Type.ACCESS_RESTRICTION,
                Condition.manual(),
                Scope.REPORT_SUBMISSION
        )));
        PolicyV2CapabilityGate gate = new PolicyV2CapabilityGate(store);

        assertFalse(gate.permits(subject, Scope.REPORT_SUBMISSION));
        assertTrue(gate.permits(subject, Scope.MARKET_ACCESS));
        assertFalse(gate.permits(subject, Scope.REPORT_SUBMISSION));

        assertEquals(3, store.reads);
        assertEquals(0, store.writes);
    }

    private static PolicyV2RemedyEnforcement record(
            String caseId,
            UUID subject,
            RemedySpec.Type type,
            Condition condition,
            Scope scope
    ) {
        return new PolicyV2RemedyEnforcement(
                caseId,
                "remedy",
                subject,
                type,
                scope,
                condition,
                Lifecycle.ENFORCED,
                1L,
                NOW
        );
    }

    private static final class MemoryStore implements PolicyV2EnforcementStore {
        private final List<PolicyV2RemedyEnforcement> records = new ArrayList<>();
        private int reads;
        private int writes;

        private MemoryStore(List<PolicyV2RemedyEnforcement> records) {
            this.records.addAll(records);
        }

        @Override
        public PolicyV2RemedyEnforcement register(RegisterRequest request) {
            writes++;
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<PolicyV2RemedyEnforcement> find(String caseId, String remedyId) {
            reads++;
            return records.stream()
                    .filter(record -> record.caseId().equals(caseId) && record.remedyId().equals(remedyId))
                    .findFirst();
        }

        @Override
        public List<PolicyV2RemedyEnforcement> activeFor(UUID subjectId) {
            reads++;
            return records.stream()
                    .filter(record -> record.subjectId().equals(subjectId) && record.active())
                    .toList();
        }

        @Override
        public PolicyV2RemedyEnforcement transition(TransitionRequest request) {
            writes++;
            throw new UnsupportedOperationException();
        }
    }
}
