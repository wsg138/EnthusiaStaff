package net.enthusia.staff.paper.staff;

import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/** Renders the server-owned staff-tools inventory views without using item metadata for routing. */
final class StaffToolsMenuRenderer {
    static final int INVENTORY_SIZE = 54;
    static final int TARGET_CONTENT_SIZE = 45;
    static final int BACK_SLOT = 45;
    static final int PREVIOUS_SLOT = 46;
    static final int INFO_SLOT = 48;
    static final int REFRESH_SLOT = 49;
    static final int NEXT_SLOT = 52;
    static final int CLOSE_SLOT = 53;
    static final int EXIT_SLOT = 51;
    static final int LAUNCH_SLOT = 50;
    static final int CONFIRM_EXIT_SLOT = 24;
    static final int CANCEL_EXIT_SLOT = 20;

    private static final int HEADER_SLOT = 4;
    // Canonical tool order maps to fixed positions, independent of permission filtering.
    private static final List<Integer> ROOT_TOOL_SLOTS = List.of(28, 10, 12, 14, 16, 30, 34, 32);

    Inventory render(StaffToolsMenuView view) {
        StaffToolsMenuHolder holder = new StaffToolsMenuHolder(view);
        Inventory inventory = Bukkit.createInventory(holder, INVENTORY_SIZE, title(view));
        holder.attach(inventory);
        fill(inventory);
        if (view instanceof StaffToolsMenuView.Root root) {
            renderRoot(inventory, root);
        } else if (view instanceof StaffToolsMenuView.Loading loading) {
            renderLoading(inventory, loading);
        } else if (view instanceof StaffToolsMenuView.TargetPicker picker) {
            renderTargetPicker(inventory, picker);
        } else if (view instanceof StaffToolsMenuView.Investigation investigation) {
            renderInvestigation(inventory, investigation);
        } else if (view instanceof StaffToolsMenuView.ExitConfirmation) {
            renderExitConfirmation(inventory);
        }
        return inventory;
    }

    static int rootToolIndex(int slot) {
        return ROOT_TOOL_SLOTS.indexOf(slot);
    }

    static int targetContentIndex(int slot) {
        return slot >= 0 && slot < TARGET_CONTENT_SIZE ? slot : -1;
    }

    private static void renderRoot(Inventory inventory, StaffToolsMenuView.Root root) {
        inventory.setItem(HEADER_SLOT, item(
                Material.NETHER_STAR,
                "Staff Dashboard",
                List.of(Component.text("Investigate above. Patrol and session tools below.", NamedTextColor.GRAY))
        ));
        for (StaffToolDefinition tool : root.tools()) {
            int index = tool.ordinal();
            inventory.setItem(ROOT_TOOL_SLOTS.get(index), item(
                    tool.material(),
                    tool == StaffToolDefinition.PLAYER_INSPECTOR ? "Investigate a player" : tool.displayName(),
                    toolLore(tool)
            ));
        }
        inventory.setItem(INFO_SLOT, item(
                Material.PAPER,
                "Staff commands",
                List.of(Component.text("Click for your available commands and shortcuts.", NamedTextColor.GRAY))
        ));
        inventory.setItem(LAUNCH_SLOT, item(
                Material.FIREWORK_ROCKET,
                "Launch Forward",
                List.of(Component.text("Propel yourself in the direction you are facing.", NamedTextColor.GRAY),
                        Component.text("Shortcut: sneak + right-click the Staff Tools star.", NamedTextColor.AQUA))
        ));
        inventory.setItem(EXIT_SLOT, item(
                Material.BARRIER,
                "Exit Staff Mode",
                List.of(Component.text("Leaves staff mode and restores your saved inventory, location, and game state.", NamedTextColor.RED))
        ));
        inventory.setItem(CLOSE_SLOT, item(Material.BARRIER, "Close menu",
                List.of(Component.text("Keep your current Staff Mode session.", NamedTextColor.GRAY))));
    }

    private static List<Component> toolLore(StaffToolDefinition tool) {
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(menuInstruction(tool),
                NamedTextColor.GRAY));
        if (tool == StaffToolDefinition.PLAYER_INSPECTOR) {
            lore.add(Component.text("Overview, history, flags, evidence and inventories.", NamedTextColor.GRAY));
        }
        if (tool.targetRequired()) {
            lore.add(Component.text("The target is checked again before the action runs.", NamedTextColor.DARK_GRAY));
        }
        return List.copyOf(lore);
    }

    private static String menuInstruction(StaffToolDefinition tool) {
        return switch (tool) {
            case RANDOM_TELEPORT -> "Click to patrol a random available player.";
            case REPORTS -> "Click to review the report queue.";
            case CHEAT_TESTER -> "Click to cycle your selected evidence probe.";
            case VANISH -> "Click to toggle your visibility.";
            case STAFF_CHAT -> "Click to toggle staff chat.";
            default -> "Click to choose a player.";
        };
    }

    private static void renderLoading(Inventory inventory, StaffToolsMenuView.Loading loading) {
        inventory.setItem(22, item(
                Material.CLOCK,
                "Loading players",
                List.of(Component.text(
                        "Preparing the " + loading.tool().displayName() + " player list.",
                        NamedTextColor.GRAY
                ))
        ));
        inventory.setItem(BACK_SLOT, item(Material.ARROW, "Back to Staff Dashboard", List.of()));
        inventory.setItem(CLOSE_SLOT, item(
                Material.BARRIER,
                "Close",
                List.of(Component.text("No action has been started.", NamedTextColor.GRAY))
        ));
    }

    private static void renderTargetPicker(Inventory inventory, StaffToolsMenuView.TargetPicker picker) {
        List<StaffToolsMenuView.TargetEntry> targets = picker.pageTargets();
        for (int index = 0; index < targets.size(); index++) {
            StaffToolsMenuView.TargetEntry target = targets.get(index);
            inventory.setItem(index, item(
                    Material.PLAYER_HEAD,
                    target.playerName(),
                    List.of(
                            Component.text("Select for " + picker.tool().displayName() + '.', NamedTextColor.GRAY),
                            Component.text("The player is checked again before the action runs.", NamedTextColor.DARK_GRAY)
                    )
            ));
        }
        if (targets.isEmpty()) {
            inventory.setItem(22, item(
                    Material.BARRIER,
                    "No available players",
                    List.of(Component.text("Use the documented command fallback if you need an offline lookup.",
                            NamedTextColor.GRAY))
            ));
        }
        inventory.setItem(BACK_SLOT, item(
                Material.ARROW,
                "Back to Staff Dashboard",
                List.of()
        ));
        if (picker.hasPreviousPage()) {
            inventory.setItem(PREVIOUS_SLOT, item(Material.ARROW, "Previous page", List.of()));
        }
        if (picker.hasNextPage()) {
            inventory.setItem(NEXT_SLOT, item(Material.ARROW, "Next page", List.of()));
        }
        inventory.setItem(REFRESH_SLOT, item(
                Material.SUNFLOWER,
                "Refresh players",
                List.of(Component.text("Reload the online player list.", NamedTextColor.GRAY))
        ));
        inventory.setItem(INFO_SLOT, item(
                Material.PAPER,
                "Players " + (picker.page() + 1) + " of " + picker.totalPages(),
                picker.truncated()
                        ? List.of(Component.text("Showing the first " + StaffToolsMenuView.MAX_TARGETS
                                + " available players. Use a command for another target.", NamedTextColor.YELLOW))
                        : List.of(Component.text("Vanished players are not included here.", NamedTextColor.GRAY))
        ));
        inventory.setItem(CLOSE_SLOT, item(Material.BARRIER, "Close", List.of()));
    }

    private static Component title(StaffToolsMenuView view) {
        if (view instanceof StaffToolsMenuView.Investigation investigation) {
            return Component.text("Investigate · " + investigation.target().playerName(), NamedTextColor.DARK_AQUA);
        }
        if (view instanceof StaffToolsMenuView.ExitConfirmation) {
            return Component.text("Leave Staff Mode?", NamedTextColor.DARK_AQUA);
        }
        if (view instanceof StaffToolsMenuView.TargetPicker picker) {
            return Component.text("Select Player · " + picker.tool().displayName(), NamedTextColor.DARK_AQUA);
        }
        if (view instanceof StaffToolsMenuView.Loading loading) {
            return Component.text("Loading · " + loading.tool().displayName(), NamedTextColor.DARK_AQUA);
        }
        return Component.text("Staff Dashboard", NamedTextColor.DARK_AQUA);
    }

    private static void renderInvestigation(Inventory inventory, StaffToolsMenuView.Investigation view) {
        inventory.setItem(HEADER_SLOT, item(Material.PLAYER_HEAD, view.target().playerName(),
                List.of(Component.text("Choose what to review for this player.", NamedTextColor.GRAY))));
        for (InvestigationMenuAction action : view.actions()) {
            inventory.setItem(action.slot(), item(action.material(), action.label(), List.of(
                    Component.text(action.description(), NamedTextColor.GRAY),
                    Component.text('/' + action.command(view.target().playerName()), NamedTextColor.DARK_GRAY))));
        }
        inventory.setItem(BACK_SLOT, item(Material.ARROW, "Choose another player", List.of()));
        inventory.setItem(INFO_SLOT, item(Material.PAPER, "Player command shortcuts", List.of()));
        inventory.setItem(CLOSE_SLOT, item(Material.BARRIER, "Close menu", List.of()));
    }

    private static void renderExitConfirmation(Inventory inventory) {
        inventory.setItem(HEADER_SLOT, item(Material.NETHER_STAR, "Leave Staff Mode?", List.of(
                Component.text("Restore your saved inventory, location and game state.", NamedTextColor.GRAY))));
        inventory.setItem(CANCEL_EXIT_SLOT, item(Material.ARROW, "Keep Staff Mode", List.of()));
        inventory.setItem(CONFIRM_EXIT_SLOT, item(Material.RED_CONCRETE, "Confirm leaving Staff Mode", List.of()));
        inventory.setItem(CLOSE_SLOT, item(Material.BARRIER, "Close menu", List.of()));
    }

    private static void fill(Inventory inventory) {
        ItemStack filler = item(Material.GRAY_STAINED_GLASS_PANE, " ", List.of());
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            inventory.setItem(slot, filler);
        }
    }

    private static ItemStack item(Material material, String name, List<Component> lore) {
        ItemStack item = ItemStack.of(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(name, NamedTextColor.WHITE));
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }
}
