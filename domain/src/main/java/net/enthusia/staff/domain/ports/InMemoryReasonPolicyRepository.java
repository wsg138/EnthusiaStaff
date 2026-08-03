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
    private final Map<String, ReasonPolicy> policies;
    private final Map<String, String> aliases;
    private final Map<String, RemovedReason> removedReasons;

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
        this.policies = indexPolicies(snapshot.policies());
        this.aliases = snapshot.aliases();
        this.removedReasons = indexRemovedReasons(snapshot.removedReasons());
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
        ReasonPolicy direct = policies.get(reasonId);
        if (direct != null) {
            return Optional.of(direct);
        }
        String canonical = aliases.get(reasonId);
        return canonical == null ? Optional.empty() : Optional.of(policies.get(canonical));
    }

    @Override
    public Optional<ReasonDescriptor> describe(String reasonId) {
        if (reasonId == null) {
            return Optional.empty();
        }
        ReasonPolicy direct = policies.get(reasonId);
        if (direct != null) {
            return Optional.of(descriptor(reasonId, direct, ReasonAvailability.ACTIVE));
        }
        String canonical = aliases.get(reasonId);
        if (canonical != null) {
            return Optional.of(descriptor(reasonId, policies.get(canonical), ReasonAvailability.ALIAS));
        }
        RemovedReason removed = removedReasons.get(reasonId);
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
        return policies.values();
    }

    @Override
    public Map<String, String> aliases() {
        return aliases;
    }

    @Override
    public Collection<RemovedReason> removedReasons() {
        return removedReasons.values();
    }

    @Override
    public String activeVersion() {
        return version;
    }
}
