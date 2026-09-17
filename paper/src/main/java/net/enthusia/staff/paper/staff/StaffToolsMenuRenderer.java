package net.enthusia.staff.paper.staff;

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

    private static final int HEADER_SLOT = 4;
    private static final List<Integer> ROOT_TOOL_SLOTS = List.of(10, 11, 12, 13, 14, 15, 16, 22);

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
                "Staff Tools",
                List.of(Component.text("Choose an action for your active staff session.", NamedTextColor.GRAY))
        ));
        for (int index = 0; index < root.tools().size() && index < ROOT_TOOL_SLOTS.size(); index++) {
            StaffToolDefinition tool = root.tools().get(index);
            inventory.setItem(ROOT_TOOL_SLOTS.get(index), item(
                    tool.material(),
                    tool.displayName(),
                    toolLore(tool)
            ));
        }
        inventory.setItem(INFO_SLOT, item(
                Material.PAPER,
                "Staff commands",
                List.of(Component.text("Commands remain available when a menu is not convenient.", NamedTextColor.GRAY))
        ));
        inventory.setItem(CLOSE_SLOT, item(
                Material.BARRIER,
                "Exit Staff Mode",
                List.of(Component.text("Restores your saved state through the normal staff-mode exit.", NamedTextColor.RED))
        ));
    }

    private static List<Component> toolLore(StaffToolDefinition tool) {
        if (tool.targetRequired()) {
            return List.of(
                    Component.text("Choose an online player.", NamedTextColor.GRAY),
                    Component.text("The target is checked again before the action runs.", NamedTextColor.DARK_GRAY)
            );
        }
        return List.of(Component.text("Click to use this staff action.", NamedTextColor.GRAY));
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
                "Back to Staff Tools",
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
        if (view instanceof StaffToolsMenuView.TargetPicker picker) {
            return Component.text("Select Player · " + picker.tool().displayName(), NamedTextColor.DARK_AQUA);
        }
        if (view instanceof StaffToolsMenuView.Loading loading) {
            return Component.text("Loading · " + loading.tool().displayName(), NamedTextColor.DARK_AQUA);
        }
        return Component.text("Staff Tools", NamedTextColor.DARK_AQUA);
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
