package net.enthusia.staff.paper.config;

/**
 * Typed preview IDs for the rank dimension of Staff Mode authorization.
 * This enum is not consulted by live authorization or command execution.
 */
public enum StaffCapability {
    STAFF_MODE_GAMEMODE_SURVIVAL,
    STAFF_MODE_GAMEMODE_SPECTATOR,
    STAFF_MODE_GAMEMODE_CREATIVE,
    STAFF_MODE_GAMEMODE_ADVENTURE,
    STAFF_MODE_ADVANCED_TOOLS,
    STAFF_MODE_INVENTORY_EDIT,
    STAFF_MODE_ENDER_CHEST_OPEN,
    STAFF_MODE_ENDER_CHEST_EDIT,
    STAFF_MODE_COMBAT_TEST,
    VANISH
}
