package net.enthusia.staff.domain.ports;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.enthusia.staff.domain.escalation.ReasonPolicy;
import net.enthusia.staff.domain.escalation.RemovedReason;

public interface ReasonPolicyRepository {
    Optional<ReasonPolicy> find(String reasonId);

    default Optional<VersionedReasonPolicy> resolve(String reasonId) {
        String version = activeVersion();
        return find(reasonId).map(policy -> new VersionedReasonPolicy(version, policy));
    }

    default Optional<ReasonDescriptor> describe(String reasonId) {
        return find(reasonId).map(policy -> new ReasonDescriptor(
                reasonId,
                policy.id(),
                policy.family(),
                policy.publicReason(),
                ReasonAvailability.ACTIVE
        ));
    }

    Collection<ReasonPolicy> all();

    default Map<String, String> aliases() {
        return Map.of();
    }

    default Collection<RemovedReason> removedReasons() {
        return List.of();
    }

    String activeVersion();

    enum ReasonAvailability {
        ACTIVE,
        ALIAS,
        REMOVED
    }

    record ReasonDescriptor(
            String requestedId,
            String canonicalId,
            String family,
            String publicReason,
            ReasonAvailability availability
    ) {
        public ReasonDescriptor {
            if (requestedId == null || requestedId.isBlank()
                    || canonicalId == null || canonicalId.isBlank()
                    || family == null || family.isBlank()
                    || publicReason == null || publicReason.isBlank()
                    || availability == null) {
                throw new IllegalArgumentException("reason descriptor fields must be present");
            }
            requestedId = requestedId.trim();
            canonicalId = canonicalId.trim();
            family = family.trim();
            publicReason = publicReason.trim();
        }

        public boolean resolvesToActivePolicy() {
            return availability != ReasonAvailability.REMOVED;
        }
    }

    record VersionedReasonPolicy(String version, ReasonPolicy policy) {
        public VersionedReasonPolicy {
            if (version == null || version.isBlank() || policy == null) {
                throw new IllegalArgumentException("versioned reason policy fields must be present");
            }
            version = version.trim();
        }
    }
}
