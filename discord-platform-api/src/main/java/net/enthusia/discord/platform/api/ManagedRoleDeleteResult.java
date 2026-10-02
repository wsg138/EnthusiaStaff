package net.enthusia.discord.platform.api;

public enum ManagedRoleDeleteResult {
    DELETED,
    ABSENT,
    RETRY_SCHEDULED,
    REJECTED,
    UNAVAILABLE
}
