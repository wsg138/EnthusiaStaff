package net.enthusia.staff.domain.ports;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import net.enthusia.staff.domain.escalation.ReasonPolicy;
import net.enthusia.staff.domain.escalation.RemovedReason;

public final class AtomicReasonPolicyRepository implements ReasonPolicyRepository {
    private static final Pattern STABLE_ID = Pattern.compile("[a-z0-9]+(?:[.-][a-z0-9]+)*");
    private static final Pattern POLAR_REASON = Pattern.compile("cheating\\.polar\\.[a-z0-9]+(?:-[a-z0-9]+)*");

    private final AtomicReference<State> state;

    public AtomicReasonPolicyRepository(String version, Collection<ReasonPolicy> policies) {
        this(version, policies, Map.of(), List.of());
    }

    public AtomicReasonPolicyRepository(
            String version,
            Collection<ReasonPolicy> policies,
            Map<String, String> aliases,
            Collection<RemovedReason> removedReasons
    ) {
        state = new AtomicReference<>(validated(version, policies, aliases, removedReasons));
    }

    public void replace(String version, Collection<ReasonPolicy> policies) {
        replace(version, policies, Map.of(), List.of());
    }

    public void replace(
            String version,
            Collection<ReasonPolicy> policies,
            Map<String, String> aliases,
            Collection<RemovedReason> removedReasons
    ) {
        state.set(validated(version, policies, aliases, removedReasons));
    }

    @Override
    public Optional<ReasonPolicy> find(String reasonId) {
        State snapshot = state.get();
        return policy(snapshot, reasonId);
    }

    @Override
    public Optional<VersionedReasonPolicy> resolve(String reasonId) {
        State snapshot = state.get();
        return policy(snapshot, reasonId)
                .map(policy -> new VersionedReasonPolicy(snapshot.version(), policy));
    }

    @Override
    public Optional<ReasonDescriptor> describe(String reasonId) {
        if (reasonId == null) {
            return Optional.empty();
        }
        State snapshot = state.get();
        ReasonPolicy direct = snapshot.policies().get(reasonId);
        if (direct != null) {
            return Optional.of(descriptor(reasonId, direct, ReasonAvailability.ACTIVE));
        }
        String target = snapshot.aliases().get(reasonId);
        if (target != null) {
            ReasonPolicy policy = snapshot.policies().get(target);
            return Optional.of(descriptor(reasonId, policy, ReasonAvailability.ALIAS));
        }
        RemovedReason removed = snapshot.removedReasons().get(reasonId);
        if (removed != null) {
            return Optional.of(new ReasonDescriptor(
                    removed.id(),
                    removed.id(),
                    removed.family(),
                    removed.publicReason(),
                    ReasonAvailability.REMOVED
            ));
        }
        return polarPolicy(snapshot.policies(), reasonId)
                .map(policy -> descriptor(reasonId, policy, ReasonAvailability.ACTIVE));
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

    private static Optional<ReasonPolicy> policy(State state, String reasonId) {
        if (reasonId == null) {
            return Optional.empty();
        }
        ReasonPolicy direct = state.policies().get(reasonId);
        if (direct != null) {
            return Optional.of(direct);
        }
        String target = state.aliases().get(reasonId);
        if (target != null) {
            return Optional.of(state.policies().get(target));
        }
        return polarPolicy(state.policies(), reasonId);
    }

    private static Optional<ReasonPolicy> polarPolicy(Map<String, ReasonPolicy> policies, String reasonId) {
        if (!POLAR_REASON.matcher(reasonId).matches()) {
            return Optional.empty();
        }
        ReasonPolicy template = policies.get("cheating.polar.template");
        if (template == null) {
            return Optional.empty();
        }
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

    @Override
    public Collection<ReasonPolicy> all() {
        return state.get().policies().values();
    }

    @Override
    public Map<String, String> aliases() {
        return state.get().aliases();
    }

    @Override
    public Collection<RemovedReason> removedReasons() {
        return state.get().removedReasons().values();
    }

    @Override
    public String activeVersion() {
        return state.get().version();
    }

    private static State validated(
            String version,
            Collection<ReasonPolicy> policies,
            Map<String, String> aliases,
            Collection<RemovedReason> removedReasons
    ) {
        if (version == null || version.isBlank() || policies == null || policies.isEmpty()
                || aliases == null || removedReasons == null) {
            throw new IllegalArgumentException("version and reason policy metadata must be present");
        }
        Map<String, ReasonPolicy> indexed = policies.stream().collect(Collectors.toMap(
                ReasonPolicy::id,
                Function.identity(),
                (left, right) -> {
                    throw new IllegalArgumentException("duplicate reason policy: " + left.id());
                },
                LinkedHashMap::new
        ));
        Map<String, RemovedReason> removed = new LinkedHashMap<>();
        for (RemovedReason reason : removedReasons) {
            if (reason == null || removed.putIfAbsent(reason.id(), reason) != null) {
                throw new IllegalArgumentException("duplicate or null removed reason");
            }
            if (indexed.containsKey(reason.id())) {
                throw new IllegalArgumentException("removed reason overlaps active policy: " + reason.id());
            }
        }
        Map<String, String> validatedAliases = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : aliases.entrySet()) {
            String source = stable(entry.getKey(), "alias source");
            String target = stable(entry.getValue(), "alias target");
            if (source.equals(target)) {
                throw new IllegalArgumentException("reason alias cannot target itself: " + source);
            }
            if (indexed.containsKey(source)) {
                throw new IllegalArgumentException("reason alias overlaps active policy: " + source);
            }
            if (removed.containsKey(source)) {
                throw new IllegalArgumentException("reason alias overlaps removed reason: " + source);
            }
            if (!indexed.containsKey(target)) {
                throw new IllegalArgumentException("reason alias target is not an active policy: " + target);
            }
            if (validatedAliases.putIfAbsent(source, target) != null) {
                throw new IllegalArgumentException("duplicate reason alias: " + source);
            }
        }
        return new State(
                version.trim(),
                Map.copyOf(indexed),
                Map.copyOf(validatedAliases),
                Map.copyOf(removed)
        );
    }

    private static String stable(String value, String name) {
        if (value == null || !STABLE_ID.matcher(value.trim()).matches()) {
            throw new IllegalArgumentException(name + " must be a stable lowercase identifier");
        }
        return value.trim();
    }

    private record State(
            String version,
            Map<String, ReasonPolicy> policies,
            Map<String, String> aliases,
            Map<String, RemovedReason> removedReasons
    ) {
    }
}
