package net.enthusia.staff.paper.config.reload;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.enthusia.staff.domain.escalation.ReasonPolicy;
import net.enthusia.staff.domain.escalation.RemovedReason;
import net.enthusia.staff.paper.config.ReasonPolicyConfigurationLoader;

public interface ReasonPolicyPublisher {
    Snapshot snapshot();

    void publish(ReasonPolicyConfigurationLoader.LoadedPolicies policies);

    void restore(Snapshot snapshot);

    record Snapshot(
            String version,
            List<ReasonPolicy> policies,
            Map<String, String> aliases,
            List<RemovedReason> removedReasons
    ) {
        public Snapshot {
            if (version == null || version.isBlank() || policies == null || policies.isEmpty()
                    || aliases == null || removedReasons == null) {
                throw new IllegalArgumentException("reason-policy snapshot must be complete");
            }
            version = version.trim();
            policies = List.copyOf(policies);
            aliases = Map.copyOf(aliases);
            removedReasons = List.copyOf(removedReasons);
            index(policies);
            removedIndex(removedReasons);
        }

        public Snapshot(String version, List<ReasonPolicy> policies) {
            this(version, policies, Map.of(), List.of());
        }

        public boolean matches(ReasonPolicyConfigurationLoader.LoadedPolicies candidate) {
            Objects.requireNonNull(candidate, "candidate");
            return version.equals(candidate.version())
                    && index(policies).equals(index(candidate.policies()))
                    && aliases.equals(candidate.aliases())
                    && removedIndex(removedReasons).equals(removedIndex(candidate.removedReasons()));
        }

        public boolean sameAs(Snapshot other) {
            return other != null
                    && version.equals(other.version())
                    && index(policies).equals(index(other.policies()))
                    && aliases.equals(other.aliases())
                    && removedIndex(removedReasons).equals(removedIndex(other.removedReasons()));
        }

        private static Map<String, ReasonPolicy> index(Collection<ReasonPolicy> values) {
            Map<String, ReasonPolicy> indexed = new LinkedHashMap<>();
            for (ReasonPolicy policy : values) {
                if (policy == null || indexed.putIfAbsent(policy.id(), policy) != null) {
                    throw new IllegalArgumentException("reason-policy snapshot contains invalid duplicates");
                }
            }
            return Map.copyOf(indexed);
        }

        private static Map<String, RemovedReason> removedIndex(Collection<RemovedReason> values) {
            Map<String, RemovedReason> indexed = new LinkedHashMap<>();
            for (RemovedReason reason : values) {
                if (reason == null || indexed.putIfAbsent(reason.id(), reason) != null) {
                    throw new IllegalArgumentException("reason-policy snapshot contains invalid removed reasons");
                }
            }
            return Map.copyOf(indexed);
        }
    }
}
