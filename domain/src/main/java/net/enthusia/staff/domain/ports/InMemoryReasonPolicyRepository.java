package net.enthusia.staff.domain.ports;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.enthusia.staff.domain.escalation.ReasonPolicy;
import net.enthusia.staff.domain.escalation.RemovedReason;

public final class InMemoryReasonPolicyRepository implements ReasonPolicyRepository {
    private final String version;
    private final Map<String, ReasonPolicy> policyIndex;
    private final Map<String, String> aliasIndex;
    private final Map<String, RemovedReason> removedReasonIndex;

    public InMemoryReasonPolicyRepository(String version, Collection<ReasonPolicy> policies) {
        this(version, policies, Map.of(), List.of());
    }

    public InMemoryReasonPolicyRepository(
            String version,
            Collection<ReasonPolicy> policies,
            Map<String, String> aliases,
            Collection<RemovedReason> removedReasons
    ) {
        AtomicReasonPolicyRepository.PolicySnapshot snapshot = new AtomicReasonPolicyRepository(
                version,
                policies,
                aliases,
                removedReasons
        ).snapshot();
        this.version = snapshot.version();
        this.policyIndex = indexPolicies(snapshot.policies());
        this.aliasIndex = snapshot.aliases();
        this.removedReasonIndex = indexRemovedReasons(snapshot.removedReasons());
    }

    private static Map<String, ReasonPolicy> indexPolicies(Collection<ReasonPolicy> policies) {
        Map<String, ReasonPolicy> indexed = new LinkedHashMap<>();
        policies.forEach(policy -> indexed.put(policy.id(), policy));
        return Map.copyOf(indexed);
    }

    private static Map<String, RemovedReason> indexRemovedReasons(Collection<RemovedReason> removedReasons) {
        Map<String, RemovedReason> indexed = new LinkedHashMap<>();
        removedReasons.forEach(reason -> indexed.put(reason.id(), reason));
        return Map.copyOf(indexed);
    }

    @Override
    public Optional<ReasonPolicy> find(String reasonId) {
        if (reasonId == null) {
            return Optional.empty();
        }
        ReasonPolicy direct = policyIndex.get(reasonId);
        if (direct != null) {
            return Optional.of(direct);
        }
        String canonical = aliasIndex.get(reasonId);
        return canonical == null ? Optional.empty() : Optional.of(policyIndex.get(canonical));
    }

    @Override
    public Optional<ReasonDescriptor> describe(String reasonId) {
        if (reasonId == null) {
            return Optional.empty();
        }
        ReasonPolicy direct = policyIndex.get(reasonId);
        if (direct != null) {
            return Optional.of(descriptor(reasonId, direct, ReasonAvailability.ACTIVE));
        }
        String canonical = aliasIndex.get(reasonId);
        if (canonical != null) {
            return Optional.of(descriptor(reasonId, policyIndex.get(canonical), ReasonAvailability.ALIAS));
        }
        RemovedReason removed = removedReasonIndex.get(reasonId);
        return removed == null ? Optional.empty() : Optional.of(removedDescriptor(removed));
    }

    private static ReasonDescriptor descriptor(
            String requestedId,
            ReasonPolicy policy,
            ReasonAvailability availability
    ) {
        return new ReasonDescriptor(
                requestedId,
                policy.id(),
                policy.family(),
                policy.publicReason(),
                availability
        );
    }

    private static ReasonDescriptor removedDescriptor(RemovedReason removed) {
        return new ReasonDescriptor(
                removed.id(),
                removed.id(),
                removed.family(),
                removed.publicReason(),
                ReasonAvailability.REMOVED
        );
    }

    @Override
    public Collection<ReasonPolicy> all() {
        return policyIndex.values();
    }

    @Override
    public Map<String, String> aliases() {
        return aliasIndex;
    }

    @Override
    public Collection<RemovedReason> removedReasons() {
        return removedReasonIndex.values();
    }

    @Override
    public String activeVersion() {
        return version;
    }
}
