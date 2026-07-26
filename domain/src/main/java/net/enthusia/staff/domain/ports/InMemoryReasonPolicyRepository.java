package net.enthusia.staff.domain.ports;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import net.enthusia.staff.domain.escalation.ReasonPolicy;

public final class InMemoryReasonPolicyRepository implements ReasonPolicyRepository {
    private final String version;
    private final Map<String, ReasonPolicy> policies;

    public InMemoryReasonPolicyRepository(String version, Collection<ReasonPolicy> policies) {
        if (version == null || version.isBlank() || policies == null || policies.isEmpty()) {
            throw new IllegalArgumentException("version and policies must be present");
        }
        this.version = version;
        this.policies = policies.stream().collect(Collectors.toUnmodifiableMap(ReasonPolicy::id, Function.identity()));
    }

    @Override
    public Optional<ReasonPolicy> find(String reasonId) {
        return Optional.ofNullable(policies.get(reasonId));
    }

    @Override
    public Collection<ReasonPolicy> all() {
        return policies.values();
    }

    @Override
    public String activeVersion() {
        return version;
    }
}
