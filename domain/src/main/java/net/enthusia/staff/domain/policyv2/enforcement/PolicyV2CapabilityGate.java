package net.enthusia.staff.domain.policyv2.enforcement;

import java.util.Objects;
import java.util.UUID;
import net.enthusia.staff.domain.policyv2.enforcement.PolicyV2RemedyEnforcement.Scope;

public final class PolicyV2CapabilityGate {
    private final PolicyV2EnforcementStore store;

    public PolicyV2CapabilityGate(PolicyV2EnforcementStore store) {
        this.store = Objects.requireNonNull(store, "store");
    }

    public boolean permits(UUID subjectId, Scope scope) {
        Objects.requireNonNull(subjectId, "subjectId");
        Objects.requireNonNull(scope, "scope");
        return store.activeFor(subjectId).stream().noneMatch(record -> record.scope() == scope);
    }
}
