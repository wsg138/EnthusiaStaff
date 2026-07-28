package net.enthusia.staff.domain.ports;

import java.util.Collection;
import java.util.Optional;
import net.enthusia.staff.domain.escalation.ReasonPolicy;

public interface ReasonPolicyRepository {
    Optional<ReasonPolicy> find(String reasonId);

    default Optional<VersionedReasonPolicy> resolve(String reasonId) {
        String version = activeVersion();
        return find(reasonId).map(policy -> new VersionedReasonPolicy(version, policy));
    }

    Collection<ReasonPolicy> all();

    String activeVersion();

    record VersionedReasonPolicy(String version, ReasonPolicy policy) {
        public VersionedReasonPolicy {
            if (version == null || version.isBlank() || policy == null) {
                throw new IllegalArgumentException("versioned reason policy fields must be present");
            }
            version = version.trim();
        }
    }
}
