package net.enthusia.staff.domain.moderation;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

public record ScopeSelection(Set<EnforcementScope> scopes) {
    public ScopeSelection {
        if (scopes == null || scopes.isEmpty() || scopes.stream().anyMatch(java.util.Objects::isNull)) {
            throw new IllegalArgumentException("at least one enforcement scope is required");
        }
        scopes = Set.copyOf(scopes);
    }

    public static ScopeSelection of(EnforcementScope... scopes) {
        if (scopes == null) {
            throw new IllegalArgumentException("scopes must be present");
        }
        return new ScopeSelection(Set.copyOf(Arrays.asList(scopes)));
    }

    public boolean includesPlatform(ModerationPlatform platform) {
        if (platform == null) {
            throw new IllegalArgumentException("platform must be present");
        }
        return scopes.stream().anyMatch(scope -> scope.platform() == platform);
    }

    public boolean crossPlatform() {
        return scopes.stream().map(EnforcementScope::platform).collect(Collectors.toSet()).size() > 1;
    }
}
