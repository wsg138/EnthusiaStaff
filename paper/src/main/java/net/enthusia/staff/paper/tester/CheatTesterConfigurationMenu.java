package net.enthusia.staff.paper.tester;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;
import net.enthusia.staff.domain.tester.CheatTesterType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/** Inventory-backed Cheat Tester selector. Server-owned holder state is authoritative for click routing. */
final class CheatTesterConfigurationMenu implements Listener {
    static final int CLOSE_SLOT = 26;
    static final int INFO_SLOT = 22;
    private static final int SIZE = 27;
    private static final Map<Integer, CheatTesterType> TYPES_BY_SLOT = Map.of(
            10, CheatTesterType.TOTEM_REFILL,
            11, CheatTesterType.NO_FALL,
            12, CheatTesterType.VELOCITY,
            14, CheatTesterType.AUTO_ARMOR,
            15, CheatTesterType.FAKE_ENTITY
    );

    private final CheatTesterControlState controls;
    private final CheatTesterSettings settings;
    private final BooleanSupplier fakeAvailable;
    private final IntSupplier activeCount;

    CheatTesterConfigurationMenu(
            CheatTesterControlState controls,
            CheatTesterSettings settings,
            BooleanSupplier fakeAvailable,
            IntSupplier activeCount
    ) {
        this.controls = java.util.Objects.requireNonNull(controls, "controls");
        this.settings = java.util.Objects.requireNonNull(settings, "settings");
        this.fakeAvailable = java.util.Objects.requireNonNull(fakeAvailable, "fakeAvailable");
        this.activeCount = java.util.Objects.requireNonNull(activeCount, "activeCount");
    }

    void open(Player viewer) {
        if (!controls.authorized(viewer)) {
            return;
        }
        viewer.openInventory(render(viewer));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player viewer)
                || !(event.getView().getTopInventory().getHolder(false) instanceof Holder holder)) {
            return;
        }
        event.setCancelled(true);
        if (!holder.viewerId.equals(viewer.getUniqueId()) || !topSlot(event)) {
            return;
        }
        if (!controls.authorized(viewer)) {
            viewer.closeInventory();
            return;
        }
        if (event.getRawSlot() == CLOSE_SLOT) {
            viewer.closeInventory();
            return;
        }
        select(viewer, typeAtSlot(event.getRawSlot()));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder(false) instanceof Holder) {
            event.setCancelled(true);
        }
    }

    private void select(Player viewer, CheatTesterType type) {
        if (type == null) {
            return;
        }
        if (controls.select(viewer, type, fakeAvailable.getAsBoolean())) {
            viewer.openInventory(render(viewer));
        }
    }

    private Inventory render(Player viewer) {
        Holder holder = new Holder(viewer.getUniqueId());
        Inventory inventory = Bukkit.createInventory(holder, SIZE, Component.text("Cheat Tester", NamedTextColor.DARK_AQUA));
        holder.attach(inventory);
        fill(inventory);
        CheatTesterType selected = controls.selected(viewer.getUniqueId());
        boolean fakeReady = fakeAvailable.getAsBoolean();
        TYPES_BY_SLOT.forEach((slot, type) -> inventory.setItem(slot, testerItem(type, selected, fakeReady)));
        inventory.setItem(INFO_SLOT, item(
                Material.PAPER,
                "Probe status",
                List.of(
                        Component.text("Active: " + activeCount.getAsInt() + "/" + settings.maximumActiveGlobal(), NamedTextColor.GRAY),
                        Component.text("Timeout: " + settings.sessionTimeout().toMillis() + " ms", NamedTextColor.GRAY),
                        Component.text("Left-click a player with the tester tool to run the selected probe.", NamedTextColor.DARK_GRAY)
                )
        ));
        inventory.setItem(CLOSE_SLOT, item(Material.BARRIER, "Close", List.of()));
        return inventory;
    }

    private static ItemStack testerItem(CheatTesterType type, CheatTesterType selected, boolean fakeReady) {
        boolean unavailable = type == CheatTesterType.FAKE_ENTITY && !fakeReady;
        List<Component> lore = unavailable
                ? List.of(Component.text("Unavailable: ProtocolLib packet support is not healthy.", NamedTextColor.RED))
                : List.of(Component.text(type == selected ? "Selected" : "Click to select", type == selected
                        ? NamedTextColor.GREEN : NamedTextColor.GRAY));
        return item(material(type), type.displayName(), lore);
    }

    private static Material material(CheatTesterType type) {
        return switch (type) {
            case TOTEM_REFILL -> Material.TOTEM_OF_UNDYING;
            case NO_FALL -> Material.FEATHER;
            case VELOCITY -> Material.SLIME_BALL;
            case AUTO_ARMOR -> Material.DIAMOND_CHESTPLATE;
            case FAKE_ENTITY -> Material.ARMOR_STAND;
        };
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

    private static boolean topSlot(InventoryClickEvent event) {
        return event.getRawSlot() >= 0 && event.getRawSlot() < event.getView().getTopInventory().getSize();
    }

    static CheatTesterType typeAtSlot(int slot) {
        return TYPES_BY_SLOT.get(slot);
    }

    private static final class Holder implements InventoryHolder {
        private final UUID viewerId;
        private Inventory inventory;

        private Holder(UUID viewerId) {
            this.viewerId = viewerId;
        }

        private void attach(Inventory inventory) {
            if (this.inventory != null) {
                throw new IllegalStateException("Cheat Tester configuration inventory may be attached once");
            }
            this.inventory = java.util.Objects.requireNonNull(inventory, "inventory");
        }

        @Override
        public Inventory getInventory() {
            if (inventory == null) {
                throw new IllegalStateException("Cheat Tester configuration inventory is not attached");
            }
            return inventory;
        }
    }
}
