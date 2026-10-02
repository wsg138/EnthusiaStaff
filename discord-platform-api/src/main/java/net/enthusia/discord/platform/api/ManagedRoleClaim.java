package net.enthusia.discord.platform.api;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Complete desired state for one platform-owned managed role. */
public record ManagedRoleClaim(
        ManagedRoleKey key,
        String displayName,
        Set<UUID> desiredMinecraftAccounts) {
    public static final int MAX_DESIRED_ACCOUNTS = 10_000;
    public static final int MAX_DISPLAY_NAME_LENGTH = 100;

    public ManagedRoleClaim(
            ManagedRoleKey key,
            String displayName,
            Set<UUID> desiredMinecraftAccounts) {
        this.key = Objects.requireNonNull(key, "key");
        this.displayName = validateDisplayName(displayName);
        this.desiredMinecraftAccounts = validateDesiredAccounts(desiredMinecraftAccounts);
    }

    private static String validateDisplayName(String displayName) {
        Objects.requireNonNull(displayName, "displayName");
        if (displayName.isBlank() || displayName.length() > MAX_DISPLAY_NAME_LENGTH || hasControl(displayName)) {
            throw new IllegalArgumentException("Managed-role display name must be 1-100 printable characters");
        }
        return displayName;
    }

    private static Set<UUID> validateDesiredAccounts(Set<UUID> desiredMinecraftAccounts) {
        Objects.requireNonNull(desiredMinecraftAccounts, "desiredMinecraftAccounts");
        if (desiredMinecraftAccounts.size() > MAX_DESIRED_ACCOUNTS) {
            throw new IllegalArgumentException("Managed-role desired account set is too large");
        }
        for (UUID accountId : desiredMinecraftAccounts) {
            if (accountId == null) {
                throw new IllegalArgumentException("Managed-role desired accounts must not contain null");
            }
        }
        return Set.copyOf(desiredMinecraftAccounts);
    }

    private static boolean hasControl(String value) {
        return value.codePoints().anyMatch(Character::isISOControl);
    }
}
