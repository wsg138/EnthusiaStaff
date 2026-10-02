package net.enthusia.discord.platform.api;

public enum ManagedRoleReconcileStatus {
    APPLIED,
    UNCHANGED,
    RETRY_SCHEDULED,
    REJECTED,
    UNAVAILABLE
}
