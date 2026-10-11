package net.enthusia.staff.paper.staff;

import java.util.Objects;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Cancels active staff-session inventory clicks that would transfer a protected staff tool,
 * including source items hidden behind number-key and inventory offhand swap actions.
 */
public final class StaffToolTransferListener implements Listener {
    private static final int HOTBAR_SIZE = 9;

    private final StaffModeManager staffMode;
    private final NamespacedKey staffToolKey;

    public StaffToolTransferListener(JavaPlugin plugin, StaffModeManager staffMode) {
        Objects.requireNonNull(plugin, "plugin");
        this.staffMode = Objects.requireNonNull(staffMode, "staffMode");
        this.staffToolKey = new NamespacedKey(plugin, "staff_tool");
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)
                || !staffMode.active(player.getUniqueId())) {
            return;
        }
        if (blocksToolTransfer(player, event)) {
            event.setCancelled(true);
        }
    }

    /**
     * Observe denied attempts after every cancellation handler. This does not
     * mutate the event; running at MONITOR also records transfers denied by
     * StaffModeManager when it runs before this listener at HIGHEST.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeniedToolTransferAudit(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)
                || !staffMode.active(player.getUniqueId())
                || !blocksToolTransfer(player, event)) {
            return;
        }
        // Only enum-valued metadata, never an ItemStack, NBT, or tool token.
        staffMode.logStaffAction(player, "staff-tool-transfer-denied",
                transferAuditDetail(event.getClick(), event.getView().getTopInventory().getType().name()));
    }

    static String transferAuditDetail(org.bukkit.event.inventory.ClickType click,
                                      String containerType) {
        return "click=" + click + " container=" + containerType;
    }

    private boolean blocksToolTransfer(Player player, InventoryClickEvent event) {
        int hotbarButton = event.getHotbarButton();
        boolean referencedHotbarTool = hotbarButton >= 0
                && hotbarButton < HOTBAR_SIZE
                && isStaffTool(player.getInventory().getItem(hotbarButton));
        return StaffModeAccessPolicy.blocksStaffToolTransfer(
                event.getClick(),
                isStaffTool(event.getCurrentItem()),
                isStaffTool(event.getCursor()),
                referencedHotbarTool,
                isStaffTool(player.getInventory().getItemInOffHand())
        );
    }

    private boolean isStaffTool(ItemStack item) {
        return item != null && item.hasItemMeta()
                && item.getItemMeta().getPersistentDataContainer().has(
                        staffToolKey,
                        PersistentDataType.STRING
                );
    }
}
