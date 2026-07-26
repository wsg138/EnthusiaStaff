package net.enthusia.staff.domain.ports;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Collectors;
import net.enthusia.staff.domain.escalation.ReasonPolicy;

public final class AtomicReasonPolicyRepository implements ReasonPolicyRepository {
    private final AtomicReference<State> state;

    public AtomicReasonPolicyRepository(String version, Collection<ReasonPolicy> policies) {
        state = new AtomicReference<>(validated(version, policies));
    }

    public void replace(String version, Collection<ReasonPolicy> policies) {
        state.set(validated(version, policies));
    }

    @Override
    public Optional<ReasonPolicy> find(String reasonId) {
        return policy(state.get().policies(), reasonId);
    }

    @Override
    public Optional<VersionedReasonPolicy> resolve(String reasonId) {
        State snapshot = state.get();
        return policy(snapshot.policies(), reasonId)
                .map(policy -> new VersionedReasonPolicy(snapshot.version(), policy));
    }

    private static Optional<ReasonPolicy> policy(Map<String, ReasonPolicy> policies, String reasonId) {
        ReasonPolicy direct = policies.get(reasonId);
        if (direct != null) {
            return Optional.of(direct);
        }
        if (reasonId != null && reasonId.matches("cheating\\.polar\\.[a-z0-9]+(?:-[a-z0-9]+)*")) {
            ReasonPolicy template = policies.get("cheating.polar.template");
            if (template != null) {
                return Optional.of(new ReasonPolicy(
                        reasonId,
                        reasonId,
                        "Polar detection: " + reasonId.substring("cheating.polar.".length()),
                        template.severity(),
                        template.decayEnabled(),
                        template.steps(),
                        template.examples(),
                        template.publicByDefault(),
                        template.reportable(),
                        template.confiscationAllowed(),
                        template.requiredRank(),
                        template.automaticDetectionAllowed(),
                        template.altInheritance()
                ));
            }
        }
        return Optional.empty();
    }

    @Override
    public Collection<ReasonPolicy> all() {
        return state.get().policies().values();
    }

    @Override
    public String activeVersion() {
        return state.get().version();
    }

    private static State validated(String version, Collection<ReasonPolicy> policies) {
        if (version == null || version.isBlank() || policies == null || policies.isEmpty()) {
            throw new IllegalArgumentException("version and policies must be present");
        }
        Map<String, ReasonPolicy> indexed = policies.stream().collect(Collectors.toMap(
                ReasonPolicy::id,
                Function.identity(),
                (left, right) -> {
                    throw new IllegalArgumentException("duplicate reason policy: " + left.id());
                },
                LinkedHashMap::new
        ));
        return new State(version.trim(), Map.copyOf(indexed));
    }

    private record State(String version, Map<String, ReasonPolicy> policies) {
    }
}
