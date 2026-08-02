package net.enthusia.staff.paper.config.reload;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.enthusia.staff.domain.escalation.ReasonPolicy;
import net.enthusia.staff.paper.config.ReasonPolicyConfigurationLoader;

public interface ReasonPolicyPublisher {
    Snapshot snapshot();

    void publish(ReasonPolicyConfigurationLoader.LoadedPolicies policies);

    void restore(Snapshot snapshot);

    record Snapshot(String version, List<ReasonPolicy> policies) {
        public Snapshot {
            if (version == null || version.isBlank() || policies == null || policies.isEmpty()) {
                throw new IllegalArgumentException("reason-policy snapshot must be complete");
            }
            version = version.trim();
            policies = List.copyOf(policies);
            index(policies);
        }

        public boolean matches(ReasonPolicyConfigurationLoader.LoadedPolicies candidate) {
            Objects.requireNonNull(candidate, "candidate");
            return version.equals(candidate.version())
                    && index(policies).equals(index(candidate.policies()));
        }

        public boolean sameAs(Snapshot other) {
            return other != null
                    && version.equals(other.version())
                    && index(policies).equals(index(other.policies()));
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
    }
}
