package net.enthusia.staff.domain.moderation;

public record EnforcementTarget(ModerationIdentity identity, EnforcementScope scope) {
    public EnforcementTarget {
        if (identity == null || scope == null) {
            throw new IllegalArgumentException("enforcement target fields must be present");
        }
        if (identity.platform() != scope.platform()) {
            throw new IllegalArgumentException("enforcement identity and scope must use the same platform");
        }
    }

    public String stableKey() {
        return identity.stableKey() + "@" + scope.stableKey();
    }
}
