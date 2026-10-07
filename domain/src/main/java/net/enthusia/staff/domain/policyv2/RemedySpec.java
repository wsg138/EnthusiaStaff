package net.enthusia.staff.domain.policyv2;

import java.util.Optional;
import net.enthusia.staff.domain.policyv2.enforcement.PolicyV2EnforcementPolicy;

public record RemedySpec(
        String id,
        Type type,
        String description,
        Optional<PolicyV2RemedyBindingSpec> enforcementBinding
) {
    public enum Type { REMOVE_CONTENT, CONFISCATE, ACCESS_RESTRICTION, CORRECT_PROFILE, OTHER }

    public RemedySpec(String id, Type type, String description) {
        this(id, type, description, Optional.empty());
    }

    public RemedySpec {
        id = PolicyIds.require(id, "remedy id");
        if (type == null || description == null || description.isBlank()) {
            throw new IllegalArgumentException("remedy type and description must be present");
        }
        description = description.trim();
        enforcementBinding = enforcementBinding == null ? Optional.empty() : enforcementBinding;
        enforcementBinding.ifPresent(binding ->
                PolicyV2EnforcementPolicy.requireBindingSpec(type, binding));
    }
}
