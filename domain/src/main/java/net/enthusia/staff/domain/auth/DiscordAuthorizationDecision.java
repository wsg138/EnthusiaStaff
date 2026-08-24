package net.enthusia.staff.domain.auth;

import java.util.Set;

public record DiscordAuthorizationDecision(
        boolean permitted,
        DiscordAuthorizationDenial denial,
        Set<DiscordEnforcementPrecondition> requiredPreconditions
) {
    public DiscordAuthorizationDecision {
        if (denial == null || requiredPreconditions == null) {
            throw new IllegalArgumentException("denial and requiredPreconditions must be present");
        }
        requiredPreconditions = Set.copyOf(requiredPreconditions);
        if (permitted != (denial == DiscordAuthorizationDenial.NONE)) {
            throw new IllegalArgumentException("permitted decisions must use NONE and denied decisions must have a reason");
        }
        if (!permitted && !requiredPreconditions.isEmpty()) {
            throw new IllegalArgumentException("external preconditions cannot accompany a denied domain decision");
        }
    }

    public static DiscordAuthorizationDecision allow(Set<DiscordEnforcementPrecondition> preconditions) {
        return new DiscordAuthorizationDecision(true, DiscordAuthorizationDenial.NONE, preconditions);
    }

    public static DiscordAuthorizationDecision deny(DiscordAuthorizationDenial denial) {
        if (denial == null || denial == DiscordAuthorizationDenial.NONE) {
            throw new IllegalArgumentException("a concrete denial reason is required");
        }
        return new DiscordAuthorizationDecision(false, denial, Set.of());
    }
}
