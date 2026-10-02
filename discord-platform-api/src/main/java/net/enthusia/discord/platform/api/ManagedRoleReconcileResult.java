package net.enthusia.discord.platform.api;

import java.util.Objects;

/** Safe aggregate result; never exposes Discord user IDs or persistence records. */
public record ManagedRoleReconcileResult(
        ManagedRoleReconcileStatus status,
        int desiredMinecraftAccounts,
        int resolvedDiscordAccounts,
        int rolesAdded,
        int rolesRemoved) {
    public ManagedRoleReconcileResult {
        Objects.requireNonNull(status, "status");
        requireNonNegative(desiredMinecraftAccounts, "desiredMinecraftAccounts");
        requireNonNegative(resolvedDiscordAccounts, "resolvedDiscordAccounts");
        requireNonNegative(rolesAdded, "rolesAdded");
        requireNonNegative(rolesRemoved, "rolesRemoved");
        if (resolvedDiscordAccounts > desiredMinecraftAccounts) {
            throw new IllegalArgumentException("Resolved Discord accounts cannot exceed desired Minecraft accounts");
        }
    }

    private static void requireNonNegative(int value, String name) {
        if (value < 0) {
            throw new IllegalArgumentException(name + " must be non-negative");
        }
    }
}
