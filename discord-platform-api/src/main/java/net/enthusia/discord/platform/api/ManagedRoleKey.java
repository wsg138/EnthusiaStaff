package net.enthusia.discord.platform.api;

import java.util.Objects;
import java.util.regex.Pattern;

/** Durable provider-neutral identifier for one managed Discord role. */
public record ManagedRoleKey(ManagedRoleNamespace namespace, String localKey) {
    private static final Pattern VALID_KEY = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._:-]{0,127}");

    public ManagedRoleKey {
        Objects.requireNonNull(namespace, "namespace");
        Objects.requireNonNull(localKey, "localKey");
        if (!VALID_KEY.matcher(localKey).matches()) {
            throw new IllegalArgumentException("Managed-role local key must be 1-128 safe characters");
        }
    }
}
