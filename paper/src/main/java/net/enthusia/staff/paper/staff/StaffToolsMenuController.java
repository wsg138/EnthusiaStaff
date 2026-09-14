package net.enthusia.staff.paper.staff;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import net.enthusia.staff.paper.visibility.VanishManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Inventory UI for active staff tools. The holder state owns every routing identifier; rendered items are
 * presentation only, and the dispatcher remains the sole authority for actual staff-tool execution.
 */
final class StaffToolsMenuController implements Listener {
    private final JavaPlugin plugin;
    private final StaffToolDispatcher dispatcher;
    private final VanishManager vanish;
    private final StaffToolsMenuRenderer renderer = new StaffToolsMenuRenderer();
    private final Map<UUID, UUID> pendingTargetLoads = new ConcurrentHashMap<>();
    private final Map<UUID, Inventory> loadingInventories = new ConcurrentHashMap<>();

    StaffToolsMenuController(JavaPlugin plugin, StaffToolDispatcher dispatcher, VanishManager vanish) {
        if (plugin == null || dispatcher == null || vanish == null) {
            throw new IllegalArgumentException("staff tools menu dependencies must be present");
        }
        this.plugin = plugin;
        this.dispatcher = dispatcher;
        this.vanish = vanish;
    }

    void open(Player viewer) {
        if (viewer == null) {
            return;
        }
        onEntity(viewer.getUniqueId(), this::openRoot);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player viewer)
                || !(event.getView().getTopInventory().getHolder(false) instanceof StaffToolsMenuHolder holder)) {
            return;
        }
        event.setCancelled(true);
        StaffToolsMenuView view = holder.view();
        if (!view.viewerId().equals(viewer.getUniqueId())) {
            return;
        }
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= event.getView().getTopInventory().getSize()) {
            return;
        }
        if (!dispatcher.menuAuthorized(viewer)) {
            cancelTargetLoad(viewer.getUniqueId());
            viewer.closeInventory();
            return;
        }
        if (view instanceof StaffToolsMenuView.Root root) {
            rootClick(viewer, root, slot);
        } else if (view instanceof StaffToolsMenuView.Loading loading) {
            loadingClick(viewer, loading, slot);
        } else if (view instanceof StaffToolsMenuView.TargetPicker picker) {
            targetPickerClick(viewer, picker, slot);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder(false) instanceof StaffToolsMenuHolder) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player viewer)
                || !(event.getInventory().getHolder(false) instanceof StaffToolsMenuHolder holder)
                || !holder.view().viewerId().equals(viewer.getUniqueId())) {
            return;
        }
        UUID viewerId = viewer.getUniqueId();
        if (loadingInventories.remove(viewerId, event.getInventory())) {
            pendingTargetLoads.remove(viewerId);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        cancelTargetLoad(event.getPlayer().getUniqueId());
    }

    private void rootClick(Player viewer, StaffToolsMenuView.Root root, int slot) {
        if (slot == StaffToolsMenuRenderer.CLOSE_SLOT) {
            viewer.closeInventory();
            dispatcher.exitStaffMode(viewer);
            return;
        }
        StaffToolDefinition tool = root.toolAt(StaffToolsMenuRenderer.rootToolIndex(slot));
        if (tool == null) {
            return;
        }
        if (!dispatcher.menuToolAvailable(viewer, tool)) {
            openRoot(viewer);
            return;
        }
        if (tool.targetRequired()) {
            requestTargetPicker(viewer, tool);
            return;
        }
        viewer.closeInventory();
        dispatcher.dispatchFromMenu(viewer, tool, null);
    }

    private void loadingClick(Player viewer, StaffToolsMenuView.Loading loading, int slot) {
        if (slot == StaffToolsMenuRenderer.CLOSE_SLOT) {
            cancelTargetLoad(viewer.getUniqueId());
            viewer.closeInventory();
        }
    }

    private void targetPickerClick(Player viewer, StaffToolsMenuView.TargetPicker picker, int slot) {
        if (slot == StaffToolsMenuRenderer.BACK_SLOT) {
            openRoot(viewer);
            return;
        }
        if (slot == StaffToolsMenuRenderer.REFRESH_SLOT) {
            requestTargetPicker(viewer, picker.tool());
            return;
        }
        if (slot == StaffToolsMenuRenderer.PREVIOUS_SLOT && picker.hasPreviousPage()) {
            openPicker(viewer, new StaffToolsMenuView.TargetPicker(
                    picker.viewerId(),
                    picker.tool(),
                    picker.targets(),
                    picker.page() - 1,
                    picker.truncated()
            ));
            return;
        }
        if (slot == StaffToolsMenuRenderer.NEXT_SLOT && picker.hasNextPage()) {
            openPicker(viewer, new StaffToolsMenuView.TargetPicker(
                    picker.viewerId(),
                    picker.tool(),
                    picker.targets(),
                    picker.page() + 1,
                    picker.truncated()
            ));
            return;
        }
        if (slot == StaffToolsMenuRenderer.CLOSE_SLOT) {
            viewer.closeInventory();
            return;
        }
        StaffToolsMenuView.TargetEntry target = picker.targetAtPageIndex(
                StaffToolsMenuRenderer.targetContentIndex(slot)
        );
        if (target == null) {
            return;
        }
        if (!dispatcher.menuToolAvailable(viewer, picker.tool())) {
            openRoot(viewer);
            return;
        }
        if (vanish.isVanished(target.playerId())) {
            viewer.sendMessage(Component.text("That player is no longer available in the staff-tools menu.",
                    NamedTextColor.YELLOW));
            requestTargetPicker(viewer, picker.tool());
            return;
        }
        viewer.closeInventory();
        dispatcher.dispatchFromMenu(viewer, picker.tool(), target.playerId());
    }

    private void openRoot(Player viewer) {
        UUID viewerId = viewer.getUniqueId();
        cancelTargetLoad(viewerId);
        if (!dispatcher.menuAuthorized(viewer)) {
            viewer.closeInventory();
            return;
        }
        List<StaffToolDefinition> tools = dispatcher.availableMenuTools(viewer);
        if (tools.isEmpty()) {
            viewer.closeInventory();
            viewer.sendMessage(Component.text("No staff tools are currently available for this session.",
                    NamedTextColor.YELLOW));
            return;
        }
        viewer.openInventory(renderer.render(StaffToolsMenuView.root(viewerId, tools)));
    }

    private void requestTargetPicker(Player viewer, StaffToolDefinition tool) {
        UUID viewerId = viewer.getUniqueId();
        UUID loadId = UUID.randomUUID();
        cancelTargetLoad(viewerId);
        if (!dispatcher.menuAuthorized(viewer) || !dispatcher.menuToolAvailable(viewer, tool)) {
            openRoot(viewer);
            return;
        }
        Inventory loading = renderer.render(new StaffToolsMenuView.Loading(viewerId, tool));
        viewer.openInventory(loading);
        pendingTargetLoads.put(viewerId, loadId);
        loadingInventories.put(viewerId, loading);
        plugin.getServer().getGlobalRegionScheduler().execute(
                plugin,
                () -> loadTargetPicker(viewerId, tool, loadId)
        );
    }

    private void loadTargetPicker(UUID viewerId, StaffToolDefinition tool, UUID loadId) {
        List<StaffToolsMenuView.TargetEntry> candidates = plugin.getServer().getOnlinePlayers().stream()
                .filter(player -> !viewerId.equals(player.getUniqueId()))
                .filter(player -> !vanish.isVanished(player.getUniqueId()))
                .map(player -> new StaffToolsMenuView.TargetEntry(player.getUniqueId(), player.getName()))
                .toList();
        StaffToolsMenuView.TargetPicker picker = StaffToolsMenuView.TargetPicker.fromCandidates(
                viewerId,
                tool,
                candidates,
                0
        );
        onEntity(
                viewerId,
                viewer -> openLoadedTargetPicker(viewer, picker, loadId),
                () -> cancelTargetLoad(viewerId)
        );
    }

    private void openLoadedTargetPicker(
            Player viewer,
            StaffToolsMenuView.TargetPicker picker,
            UUID loadId
    ) {
        UUID viewerId = viewer.getUniqueId();
        if (!pendingTargetLoads.remove(viewerId, loadId)) {
            return;
        }
        loadingInventories.remove(viewerId);
        if (!dispatcher.menuAuthorized(viewer) || !dispatcher.menuToolAvailable(viewer, picker.tool())) {
            viewer.closeInventory();
            return;
        }
        openPicker(viewer, picker);
    }

    private void openPicker(Player viewer, StaffToolsMenuView.TargetPicker picker) {
        cancelTargetLoad(viewer.getUniqueId());
        if (!dispatcher.menuAuthorized(viewer) || !dispatcher.menuToolAvailable(viewer, picker.tool())) {
            viewer.closeInventory();
            return;
        }
        viewer.openInventory(renderer.render(picker));
    }

    private void cancelTargetLoad(UUID viewerId) {
        pendingTargetLoads.remove(viewerId);
        loadingInventories.remove(viewerId);
    }

    private void onEntity(UUID playerId, Consumer<Player> operation) {
        onEntity(playerId, operation, () -> {
        });
    }

    private void onEntity(UUID playerId, Consumer<Player> operation, Runnable retired) {
        plugin.getServer().getGlobalRegionScheduler().execute(plugin, () -> {
            Player player = plugin.getServer().getPlayer(playerId);
            if (player == null) {
                retired.run();
                return;
            }
            boolean scheduled = player.getScheduler().execute(
                    plugin,
                    () -> operation.accept(player),
                    retired,
                    1L
            );
            if (!scheduled) {
                retired.run();
            }
        });
    }
}
