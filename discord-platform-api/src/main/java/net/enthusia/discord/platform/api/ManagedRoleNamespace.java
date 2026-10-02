package net.enthusia.discord.platform.api;

import java.util.Objects;
import java.util.regex.Pattern;

/** Stable ownership namespace for one managed-role producer. */
public record ManagedRoleNamespace(String value) {
    private static final Pattern VALID = Pattern.compile("[a-z0-9][a-z0-9._-]{0,63}");

    public ManagedRoleNamespace {
        Objects.requireNonNull(value, "value");
        if (!VALID.matcher(value).matches()) {
            throw new IllegalArgumentException("Managed-role namespace must be 1-64 lowercase safe characters");
        }
    }
}
