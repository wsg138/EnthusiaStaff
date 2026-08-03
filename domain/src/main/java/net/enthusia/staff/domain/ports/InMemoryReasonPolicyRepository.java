package net.enthusia.staff.domain.ports;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
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
        AtomicReasonPolicyRepository validated = new AtomicReasonPolicyRepository(
                version,
                policies,
                aliases,
                removedReasons
        );
        this.version = validated.activeVersion();
        this.policies = validated.all().stream().collect(Collectors.toUnmodifiableMap(
                ReasonPolicy::id,
                Function.identity(),
                (left, right) -> left,
                LinkedHashMap::new
        ));
        this.aliases = Map.copyOf(validated.aliases());
        this.removedReasons = validated.removedReasons().stream().collect(Collectors.toUnmodifiableMap(
                RemovedReason::id,
                Function.identity(),
                (left, right) -> left,
                LinkedHashMap::new
        ));
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
        if (removed == null) {
            return Optional.empty();
        }
        return Optional.of(new ReasonDescriptor(
                removed.id(),
                removed.id(),
                removed.family(),
                removed.publicReason(),
                ReasonAvailability.REMOVED
        ));
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
