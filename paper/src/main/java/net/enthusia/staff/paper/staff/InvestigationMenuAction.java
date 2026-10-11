package net.enthusia.staff.paper.staff;

import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;
import org.bukkit.Material;

/** Read shortcuts and explicit workflow entry points; never direct moderation mutations. */
enum InvestigationMenuAction {
    OVERVIEW(10, Material.PLAYER_HEAD, "Player overview", "Identity, reports, freeze and available actions",
            "enthusiastaff.inspect", "enthusiastaff:inspect %s"),
    HISTORY(12, Material.BOOK, "Moderation history", "Review previous moderation decisions",
            "enthusiastaff.history.view", "history %s"),
    FLAGS(14, Material.ORANGE_BANNER, "Flags and notes", "Review active flags and permitted note summaries",
            "enthusiastaff.investigation.view", "staffflags list %s"),
    CLIENT(16, Material.SPYGLASS, "Client evidence", "Review a live snapshot; nothing is saved automatically",
            "enthusiastaff.client", "client %s"),
    INVENTORY(28, Material.CHEST, "View inventory", "Open the existing inventory inspection workflow",
            "enthusiastaff.inventory.view", "enthusiastaff:inspect inventory %s"),
    ENDER_CHEST(30, Material.ENDER_CHEST, "View ender chest", "Open the existing ender chest inspection workflow",
            "enthusiastaff.inventory.view", "enthusiastaff:inspect ender %s"),
    PUNISHMENT(34, Material.ANVIL, "Review punishment options", "Choose a reason and review before confirming",
            "enthusiastaff.punish", "punish %s");

    private final int slot;
    private final Material material;
    private final String label;
    private final String description;
    private final String permission;
    private final String command;

    InvestigationMenuAction(int slot, Material material, String label, String description,
            String permission, String command) {
        this.slot = slot;
        this.material = material;
        this.label = label;
        this.description = description;
        this.permission = permission;
        this.command = command;
    }

    int slot() { return slot; }
    Material material() { return material; }
    String label() { return label; }
    String description() { return description; }
    String permission() { return permission; }
    String command(String playerName) { return command.formatted(playerName); }

    static List<InvestigationMenuAction> available(Predicate<String> permission) {
        return Arrays.stream(values()).filter(action -> permission.test(action.permission)).toList();
    }

    static InvestigationMenuAction atSlot(int slot) {
        return Arrays.stream(values()).filter(action -> action.slot == slot).findFirst().orElse(null);
    }
}
