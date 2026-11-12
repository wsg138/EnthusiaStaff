package proof.gui;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * PAPER-ONLY disposable protocol smoke. It cannot issue or approve a sanction.
 * Never load on production. No punishment policy/config/data files are changed.
 */
public final class PunishmentGuiSmoke extends JavaPlugin implements Listener {
    private static final Path PROOF = Path.of("punishment-gui-smoke.txt");
    private static final int EXPECTED_TOP_SLOTS = 54;
    private static final String EXPECTED_HOLDER = "PunishmentTargetPickerHolder";
    private static final int REFRESH_SLOT = 49;
    private static final int CLOSE_SLOT = 51;
    private static final int REFRESHED_PICKER_OPEN_COUNT = 2;
    private final AtomicBoolean started = new AtomicBoolean();
    private final AtomicBoolean staffAttempted = new AtomicBoolean();
    private final AtomicInteger pickerOpens = new AtomicInteger();
    private final AtomicBoolean closeControlClicked = new AtomicBoolean();
    private UUID targetId;

    @Override
    public void onEnable() {
        if (!disposableServerConfigured()) {
            getLogger().severe("Refusing to enable: disposable loopback offline server required");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        Plugin staff = getServer().getPluginManager().getPlugin("EnthusiaStaff");
        if (staff == null || !staff.isEnabled()) {
            getLogger().severe("EnthusiaStaff unavailable");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        try {
            Files.deleteIfExists(PROOF);
            marker("HARNESS_READY=true");
        } catch (IOException exception) {
            getLogger().severe("Failed to initialize evidence: " + exception);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        getServer().getPluginManager().registerEvents(this, this);
    }

    private boolean disposableServerConfigured() {
        return Boolean.getBoolean("enthusia.gui.smoke.isolated")
                && "127.0.0.1".equals(getServer().getIp())
                && !getServer().getOnlineMode()
                && getServer().getMaxPlayers() <= 2
                && "jdbc:mariadb://127.0.0.1:3307/enthusiastaff_gui_smoke"
                        .equals(System.getenv("ES_DATABASE_URL"));
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent event) {
        if (!started.compareAndSet(false, true)) {
            return;
        }
        Player player = event.getPlayer();
        targetId = player.getUniqueId();
        // Permission attachments apply ONLY to the disposable proof identity.
        player.addAttachment(this, "enthusiastaff.identity.owner", true);
        player.addAttachment(this, "enthusiastaff.rank.founder", true);
        player.addAttachment(this, "enthusiastaff.punish", true);
        player.addAttachment(this, "enthusiastaff.staffmode", true);
        marker("CLIENT_JOIN=true");
        // Schedule the same five tests in order; defer state checks to their tasks.
        Bukkit.getScheduler().runTaskLater(this, () -> attemptOffDutyPunish(player), 35L);
        Bukkit.getScheduler().runTaskLater(this, () -> confirmOffDutyBlocked(player), 75L);
        Bukkit.getScheduler().runTaskLater(this, () -> enterStaffMode(player), 100L);
        Bukkit.getScheduler().runTaskLater(this, () -> attemptOnDutyPunish(player), 200L);
        Bukkit.getScheduler().runTaskLater(this, () -> verifyPickerOpen(player), 310L);
    }

    private void attemptOffDutyPunish(Player player) {
        if (!player.isOnline()) {
            marker("FAIL=proof_player_left_before_denial");
            return;
        }
        // performCommand() reports command handling, not authorization.
        marker("OFF_DUTY_PUNISH_HANDLED=" + player.performCommand("punish"));
    }

    private void confirmOffDutyBlocked(Player player) {
        if (!player.isOnline() || expected(player.getOpenInventory().getTopInventory())) {
            marker("FAIL=off_duty_picker_open_or_player_left");
            return;
        }
        marker("OFF_DUTY_PICKER_BLOCKED=true");
    }

    private void enterStaffMode(Player player) {
        if (player.isOnline()) {
            staffAttempted.set(true);
            marker("STAFF_COMMAND_ACCEPTED=" + player.performCommand("staff"));
        }
    }

    private void attemptOnDutyPunish(Player player) {
        if (!player.isOnline()) {
            marker("FAIL=proof_player_left");
            return;
        }
        boolean accepted = player.performCommand("punish");
        marker("PUNISH_COMMAND_ACCEPTED=" + accepted);
    }

    private void verifyPickerOpen(Player player) {
        if (!player.isOnline()) {
            marker("FAIL=proof_player_disconnected");
            return;
        }
        Inventory top = player.getOpenInventory().getTopInventory();
        if (!expected(top)) {
            marker("FAIL=expected_target_picker_not_open");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player player) || !player.getUniqueId().equals(targetId)) {
            return;
        }
        Inventory top = event.getView().getTopInventory();
        String title = PlainTextComponentSerializer.plainText().serialize(event.getView().title());
        marker("INVENTORY_OPEN|title=" + title.replace('\n', ' ') + "|size=" + top.getSize()
                + "|holder=" + holderName(top));
        if (expected(top)) {
            if (!staffAttempted.get()) {
                marker("FAIL=off_duty_picker_open");
                return;
            }
            marker("PICKER_OPEN=true");
            if (pickerOpens.incrementAndGet() == REFRESHED_PICKER_OPEN_COUNT) {
                marker("PICKER_REFRESH_OPEN=true");
            }
        }
    }

    /** Observe only the two non-sanctioning picker controls. Do not cancel or inject clicks. */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onPickerControl(InventoryClickEvent event) {
        if (!isProofPickerControl(event)) {
            return;
        }
        int slot = event.getRawSlot();
        if (slot == REFRESH_SLOT) {
            recordRefreshControl(event);
        } else if (slot == CLOSE_SLOT) {
            recordCloseControl(event);
        } else {
            marker("FAIL=unapproved_picker_slot_clicked");
        }
    }

    private boolean isProofPickerControl(InventoryClickEvent event) {
        return event.getWhoClicked() instanceof Player player
                && player.getUniqueId().equals(targetId)
                && expected(event.getView().getTopInventory());
    }

    private static boolean matchesControl(InventoryClickEvent event, Material expectedMaterial) {
        return event.getCurrentItem() != null
                && event.getCurrentItem().getType() == expectedMaterial;
    }

    private void recordRefreshControl(InventoryClickEvent event) {
        if (!matchesControl(event, Material.CLOCK)) {
            marker("FAIL=refresh_control_material_changed");
            return;
        }
        marker("REFRESH_CLICK_OBSERVED=true");
    }

    private void recordCloseControl(InventoryClickEvent event) {
        if (!matchesControl(event, Material.BARRIER)) {
            marker("FAIL=close_control_material_changed");
            return;
        }
        closeControlClicked.set(true);
        marker("CLOSE_CLICK_OBSERVED=true");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPickerClose(InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player player
                && player.getUniqueId().equals(targetId)
                && expected(event.getView().getTopInventory())
                && closeControlClicked.get()) {
            marker("PICKER_CLOSED_AFTER_CONTROL=true");
        }
    }

    private static boolean expected(Inventory inventory) {
        return inventory.getSize() == EXPECTED_TOP_SLOTS
                && EXPECTED_HOLDER.equals(holderName(inventory));
    }

    private static String holderName(Inventory inventory) {
        InventoryHolder holder = inventory.getHolder(false);
        return holder == null ? "none" : holder.getClass().getSimpleName();
    }

    private void marker(String line) {
        getLogger().info("GUI_SMOKE|" + line);
        try {
            Files.writeString(PROOF, line + "\n", StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException exception) {
            getLogger().severe("Cannot write GUI smoke evidence: " + exception);
        }
    }
}
