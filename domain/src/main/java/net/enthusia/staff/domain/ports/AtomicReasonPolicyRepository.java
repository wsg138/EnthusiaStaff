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
    private static final String POLAR_TEMPLATE_ID = "cheating.polar.template";
    private static final String POLAR_PREFIX = "cheating.polar.";

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

    public PolicySnapshot snapshot() {
        State snapshot = state.get();
        return new PolicySnapshot(
                snapshot.version(),
                List.copyOf(snapshot.policies().values()),
                snapshot.aliases(),
                List.copyOf(snapshot.removedReasons().values())
        );
    }

    @Override
    public Optional<ReasonPolicy> find(String reasonId) {
        return policy(state.get(), reasonId);
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
        Optional<ReasonDescriptor> active = describeActive(snapshot, reasonId);
        if (active.isPresent()) {
            return active;
        }
        RemovedReason removed = snapshot.removedReasons().get(reasonId);
        if (removed != null) {
            return Optional.of(removedDescriptor(removed));
        }
        return polarPolicy(snapshot.policies(), reasonId)
                .map(policy -> descriptor(reasonId, policy, ReasonAvailability.ACTIVE));
    }

    private static Optional<ReasonDescriptor> describeActive(State state, String reasonId) {
        ReasonPolicy direct = state.policies().get(reasonId);
        if (direct != null) {
            return Optional.of(descriptor(reasonId, direct, ReasonAvailability.ACTIVE));
        }
        String target = state.aliases().get(reasonId);
        if (target == null) {
            return Optional.empty();
        }
        return Optional.of(descriptor(reasonId, state.policies().get(target), ReasonAvailability.ALIAS));
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
        if (state.removedReasons().containsKey(reasonId)) {
            return Optional.empty();
        }
        return polarPolicy(state.policies(), reasonId);
    }

    private static Optional<ReasonPolicy> polarPolicy(Map<String, ReasonPolicy> policies, String reasonId) {
        if (!POLAR_REASON.matcher(reasonId).matches()) {
            return Optional.empty();
        }
        ReasonPolicy template = policies.get(POLAR_TEMPLATE_ID);
        if (template == null) {
            return Optional.empty();
        }
        return Optional.of(new ReasonPolicy(
                reasonId,
                reasonId,
                "Polar detection: " + reasonId.substring(POLAR_PREFIX.length()),
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
        validateInputs(version, policies, aliases, removedReasons);
        Map<String, ReasonPolicy> indexedPolicies = indexPolicies(policies);
        Map<String, RemovedReason> indexedRemoved = indexRemovedReasons(removedReasons, indexedPolicies);
        Map<String, String> indexedAliases = indexAliases(aliases, indexedPolicies, indexedRemoved);
        return new State(
                version.trim(),
                Map.copyOf(indexedPolicies),
                Map.copyOf(indexedAliases),
                Map.copyOf(indexedRemoved)
        );
    }

    private static void validateInputs(
            String version,
            Collection<ReasonPolicy> policies,
            Map<String, String> aliases,
            Collection<RemovedReason> removedReasons
    ) {
        if (version == null || version.isBlank() || policies == null || policies.isEmpty()
                || aliases == null || removedReasons == null) {
            throw new IllegalArgumentException("version and reason policy metadata must be present");
        }
    }

    private static Map<String, ReasonPolicy> indexPolicies(Collection<ReasonPolicy> policies) {
        return policies.stream().collect(Collectors.toMap(
                ReasonPolicy::id,
                Function.identity(),
                (left, right) -> {
                    throw new IllegalArgumentException("duplicate reason policy: " + left.id());
                },
                LinkedHashMap::new
        ));
    }

    private static Map<String, RemovedReason> indexRemovedReasons(
            Collection<RemovedReason> removedReasons,
            Map<String, ReasonPolicy> policies
    ) {
        Map<String, RemovedReason> indexed = new LinkedHashMap<>();
        for (RemovedReason reason : removedReasons) {
            addRemovedReason(indexed, policies, reason);
        }
        return indexed;
    }

    private static void addRemovedReason(
            Map<String, RemovedReason> removedReasons,
            Map<String, ReasonPolicy> policies,
            RemovedReason reason
    ) {
        if (reason == null || removedReasons.putIfAbsent(reason.id(), reason) != null) {
            throw new IllegalArgumentException("duplicate or null removed reason");
        }
        if (policies.containsKey(reason.id())) {
            throw new IllegalArgumentException("removed reason overlaps active policy: " + reason.id());
        }
    }

    private static Map<String, String> indexAliases(
            Map<String, String> aliases,
            Map<String, ReasonPolicy> policies,
            Map<String, RemovedReason> removedReasons
    ) {
        Map<String, String> indexed = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : aliases.entrySet()) {
            Alias alias = validatedAlias(entry, policies, removedReasons);
            if (indexed.putIfAbsent(alias.source(), alias.target()) != null) {
                throw new IllegalArgumentException("duplicate reason alias: " + alias.source());
            }
        }
        return indexed;
    }

    private static Alias validatedAlias(
            Map.Entry<String, String> entry,
            Map<String, ReasonPolicy> policies,
            Map<String, RemovedReason> removedReasons
    ) {
        String source = stable(entry.getKey(), "alias source");
        String target = stable(entry.getValue(), "alias target");
        if (source.equals(target)) {
            throw new IllegalArgumentException("reason alias cannot target itself: " + source);
        }
        if (policies.containsKey(source)) {
            throw new IllegalArgumentException("reason alias overlaps active policy: " + source);
        }
        if (removedReasons.containsKey(source)) {
            throw new IllegalArgumentException("reason alias overlaps removed reason: " + source);
        }
        if (!policies.containsKey(target)) {
            throw new IllegalArgumentException("reason alias target is not an active policy: " + target);
        }
        return new Alias(source, target);
    }

    private static String stable(String value, String name) {
        if (value == null) {
            throw new IllegalArgumentException(name + " must be a stable lowercase identifier");
        }
        String normalized = value.trim();
        if (!STABLE_ID.matcher(normalized).matches()) {
            throw new IllegalArgumentException(name + " must be a stable lowercase identifier");
        }
        return normalized;
    }

    public record PolicySnapshot(
            String version,
            List<ReasonPolicy> policies,
            Map<String, String> aliases,
            List<RemovedReason> removedReasons
    ) {
        public PolicySnapshot {
            if (version == null || version.isBlank() || policies == null || aliases == null || removedReasons == null) {
                throw new IllegalArgumentException("policy snapshot fields must be present");
            }
            version = version.trim();
            policies = List.copyOf(policies);
            aliases = Map.copyOf(aliases);
            removedReasons = List.copyOf(removedReasons);
        }
    }

    private record Alias(String source, String target) {
    }

    private record State(
            String version,
            Map<String, ReasonPolicy> policies,
            Map<String, String> aliases,
            Map<String, RemovedReason> removedReasons
    ) {
    }
}
