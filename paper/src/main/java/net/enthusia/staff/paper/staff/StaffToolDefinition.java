package net.enthusia.staff.paper.staff;

import java.util.Arrays;
import java.util.Optional;
import net.enthusia.staff.domain.auth.StaffRank;
import org.bukkit.Material;

/** Canonical staff-mode hotbar definitions. PDC values are routing labels, never authority by themselves. */
enum StaffToolDefinition {
    RANDOM_TELEPORT(
            "random-teleport",
            Material.COMPASS,
            "Random Player Teleport",
            0,
            "enthusiastaff.stafftools.teleport",
            false,
            false,
            CooldownClass.RANDOM,
            "Right-click to teleport to a random online player."
    ),
    PLAYER_INSPECTOR(
            "player-inspector",
            Material.PLAYER_HEAD,
            "Player Inspector",
            1,
            "enthusiastaff.inspect",
            true,
            false,
            CooldownClass.TARGET,
            "Right-click a player to inspect their inventory and ender chest."
    ),
    FREEZE(
            "freeze",
            Material.PACKED_ICE,
            "Freeze",
            2,
            "enthusiastaff.freeze",
            true,
            false,
            CooldownClass.TARGET,
            "Right-click a player to freeze them for investigation."
    ),
    REPORTS(
            "reports",
            Material.BOOK,
            "Reports",
            3,
            "enthusiastaff.reports.manage",
            false,
            false,
            CooldownClass.TOGGLE,
            "Right-click to open the report queue and review player reports."
    ),
    CHEAT_TESTER(
            "cheat-tester",
            Material.BLAZE_ROD,
            "Cheat Tester",
            4,
            "enthusiastaff.cheattester",
            false,
            true,
            CooldownClass.TOGGLE,
            "Right-click to cycle the probe. Sneak + right-click for options. Hit a player to run the selected probe."
    ),
    SPECTATE(
            "spectate",
            Material.SPYGLASS,
            "Follow or Spectate",
            5,
            "enthusiastaff.stafftools.spectate",
            true,
            false,
            CooldownClass.TARGET,
            "Right-click a player to follow them in spectator mode."
    ),
    VANISH(
            "vanish",
            Material.ENDER_EYE,
            "Vanish",
            6,
            "enthusiastaff.vanish",
            false,
            false,
            CooldownClass.TOGGLE,
            "Right-click to toggle vanish. Hidden from players and the tab list."
    ),
    STAFF_CHAT(
            "staff-chat",
            Material.ECHO_SHARD,
            "Staff Chat",
            7,
            "enthusiastaff.staffchat",
            false,
            false,
            CooldownClass.TOGGLE,
            "Right-click to toggle staff chat. Messages go only to staff."
    ),
    STAFF_TOOLS(
            "staff-tools",
            Material.NETHER_STAR,
            "Staff Tools Menu",
            8,
            "enthusiastaff.stafftools.menu",
            false,
            false,
            CooldownClass.MENU,
            "Right-click to open the staff tools menu."
    );

    enum CooldownClass {
        RANDOM,
        TARGET,
        TOGGLE,
        MENU
    }

    private final String id;
    private final Material material;
    private final String displayName;
    private final int slot;
    private final String permission;
    private final boolean targetRequired;
    private final boolean advancedOnly;
    private final CooldownClass cooldownClass;
    private final String description;

    StaffToolDefinition(
            String id,
            Material material,
            String displayName,
            int slot,
            String permission,
            boolean targetRequired,
            boolean advancedOnly,
            CooldownClass cooldownClass,
            String description
    ) {
        this.id = id;
        this.material = material;
        this.displayName = displayName;
        this.slot = slot;
        this.permission = permission;
        this.targetRequired = targetRequired;
        this.advancedOnly = advancedOnly;
        this.cooldownClass = cooldownClass;
        this.description = description;
    }

    String id() {
        return id;
    }

    Material material() {
        return material;
    }

    String displayName() {
        return displayName;
    }

    String description() {
        return description;
    }

    int slot() {
        return slot;
    }

    String permission() {
        return permission;
    }

    boolean targetRequired() {
        return targetRequired;
    }

    CooldownClass cooldownClass() {
        return cooldownClass;
    }

    boolean availableFor(StaffRank rank) {
        if (rank == null || rank == StaffRank.SYSTEM) {
            return false;
        }
        return !advancedOnly || StaffModeAccessPolicy.hasAdvancedStaffTools(rank);
    }

    static Optional<StaffToolDefinition> fromId(String id) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        return Arrays.stream(values()).filter(tool -> tool.id.equals(id)).findFirst();
    }
}
