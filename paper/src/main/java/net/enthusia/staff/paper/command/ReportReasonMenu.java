package net.enthusia.staff.paper.command;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import net.enthusia.staff.domain.escalation.ReasonPolicy;
import net.enthusia.staff.domain.ports.ReasonPolicyRepository;
import net.enthusia.staff.domain.report.ReportReasonLabel;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
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
import org.bukkit.plugin.java.JavaPlugin;

/** Player-owned, paginated reason picker; never trusts item names or user-written NBT. */
final class ReportReasonMenu implements Listener {
    private static final int PAGE_SIZE = 36;
    private static final int INVENTORY_SIZE = 54;
    private final JavaPlugin plugin;
    private final ReasonPolicyRepository policies;
    private final Selected selected;

    @FunctionalInterface interface Selected {
        void accept(Player reporter, String target, String reasonId);
    }

    ReportReasonMenu(JavaPlugin plugin, ReasonPolicyRepository policies, Selected selected) {
        this.plugin = java.util.Objects.requireNonNull(plugin);
        this.policies = java.util.Objects.requireNonNull(policies);
        this.selected = java.util.Objects.requireNonNull(selected);
    }

    void open(Player reporter, String target, int page) {
        List<ReasonPolicy> available = policies.all().stream()
                .filter(ReasonPolicy::reportable)
                .sorted(Comparator.comparing(ReasonPolicy::id))
                .toList();
        if (available.isEmpty()) {
            reporter.sendMessage(Component.text("No report reasons are currently available.", NamedTextColor.RED));
            return;
        }
        int maxPage = (available.size() - 1) / PAGE_SIZE;
        int current = Math.max(0, Math.min(page, maxPage));
        List<String> ids = available.stream().map(ReasonPolicy::id).toList();
        Holder holder = new Holder(reporter.getUniqueId(), target, ids, current);
        Inventory inventory = Bukkit.createInventory(holder, INVENTORY_SIZE,
                Component.text("Report ").color(NamedTextColor.DARK_RED)
                        .append(Component.text(target, NamedTextColor.WHITE)));
        holder.attach(inventory);
        for (int index = 0; index < PAGE_SIZE && index + current * PAGE_SIZE < available.size(); index++) {
            ReasonPolicy policy = available.get(index + current * PAGE_SIZE);
            inventory.setItem(index, item(icon(policy.id()), ReportReasonLabel.display(policy.id()),
                    List.of(
                            Component.text(policy.publicReason(), NamedTextColor.GRAY),
                            Component.text("Click to select this reason", NamedTextColor.YELLOW))));
        }
        // Dedicated control rows keep page navigation separate from reason choices.
        ItemStack border = item(Material.GRAY_STAINED_GLASS_PANE, " ", List.of());
        for (int slot = PAGE_SIZE; slot < INVENTORY_SIZE; slot++) {
            inventory.setItem(slot, border);
        }
        inventory.setItem(40, item(Material.PLAYER_HEAD, "Reporting " + target, List.of(
                Component.text("Choose the reason for this player", NamedTextColor.WHITE),
                Component.text("The target will not be notified", NamedTextColor.GRAY))));
        inventory.setItem(41, item(Material.BOOK, "How to report", List.of(
                Component.text("1. Select a reason above", NamedTextColor.WHITE),
                Component.text("2. Describe what happened in chat", NamedTextColor.WHITE),
                Component.text("A report is not sent until step 2", NamedTextColor.YELLOW))));
        inventory.setItem(48, item(Material.CLOCK, "Page " + (current + 1) + " / " + (maxPage + 1),
                List.of(Component.text(available.size() + " available reasons", NamedTextColor.GRAY))));
        if (current > 0) {
            inventory.setItem(45, item(Material.ARROW, "Previous Page", List.of()));
        }
        inventory.setItem(49, item(Material.BARRIER, "Cancel", List.of(
                Component.text("No report has been submitted", NamedTextColor.GRAY))));
        if (current < maxPage) {
            inventory.setItem(53, item(Material.ARROW, "Next Page", List.of()));
        }
        reporter.openInventory(inventory);
    }

    static Material icon(String id) {
        String key = id.toLowerCase(java.util.Locale.ROOT);
        if (key.contains("combat") || key.contains("cheat") || key.contains("hack")) return Material.DIAMOND_SWORD;
        if (key.contains("chat") || key.contains("spam")) return Material.WRITABLE_BOOK;
        if (key.contains("grief") || key.contains("steal")) return Material.TNT;
        if (key.contains("exploit") || key.contains("dupe")) return Material.REDSTONE;
        if (key.contains("harass") || key.contains("threat")) return Material.SHIELD;
        return Material.PAPER;
    }

    private static ItemStack item(Material material, String name, List<Component> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(name, NamedTextColor.GOLD).decorate(TextDecoration.BOLD)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(lore.stream().map(c -> c.decoration(TextDecoration.ITALIC, false)).toList());
        item.setItemMeta(meta);
        return item;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder(false) instanceof Holder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player reporter)
                || !holder.viewer.equals(reporter.getUniqueId())) return;
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= INVENTORY_SIZE) return;
        if (slot == 49) {
            defer(reporter, holder, reporter::closeInventory);
            return;
        }
        if (slot == 45 && holder.page > 0) {
            defer(reporter, holder, () -> open(reporter, holder.target, holder.page - 1));
            return;
        }
        if (slot == 53 && (holder.page + 1) * PAGE_SIZE < holder.ids.size()) {
            defer(reporter, holder, () -> open(reporter, holder.target, holder.page + 1));
            return;
        }
        int index = holder.page * PAGE_SIZE + slot;
        if (slot >= PAGE_SIZE || index >= holder.ids.size()) return;
        String id = holder.ids.get(index);
        if (policies.find(id).filter(ReasonPolicy::reportable).isEmpty()) {
            defer(reporter, holder, () -> {
                reporter.closeInventory();
                reporter.sendMessage(Component.text(
                        "That report reason is no longer available.", NamedTextColor.RED));
            });
            return;
        }
        defer(reporter, holder, () -> {
            reporter.closeInventory();
            selected.accept(reporter, holder.target, id);
        });
    }

    private void defer(Player reporter, Holder holder, Runnable action) {
        // InventoryClickEvent does not permit safe synchronous open/close; use
        // the owning entity scheduler for Folia and abandon stale menu actions.
        reporter.getScheduler().execute(plugin, () -> {
            if (reporter.getOpenInventory().getTopInventory().getHolder(false) == holder) {
                action.run();
            }
        }, null, 1L);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder(false) instanceof Holder) event.setCancelled(true);
    }

    private static final class Holder implements InventoryHolder {
        private final UUID viewer;
        private final String target;
        private final List<String> ids;
        private final int page;
        private Inventory inventory;

        Holder(UUID viewer, String target, List<String> ids, int page) {
            this.viewer = viewer;
            this.target = target;
            this.ids = List.copyOf(ids);
            this.page = page;
        }
        void attach(Inventory inventory) { this.inventory = inventory; }
        @Override public Inventory getInventory() { return inventory; }
    }
}
