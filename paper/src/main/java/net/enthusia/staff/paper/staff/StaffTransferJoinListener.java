package net.enthusia.staff.paper.staff;

import java.util.Objects;
import java.util.Optional;
import java.util.logging.Level;
import net.enthusia.staff.domain.staff.StaffTransferSnapshot;
import net.enthusia.staff.paper.visibility.VanishManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Applies cross-server transfer snapshots on arrival.
 *
 * <p>Runs at {@link EventPriority#LOW}, after {@link StaffModeManager}'s LOWEST handoff-resume
 * capture but before {@link VanishManager}'s HIGHEST join handler. This preserves the
 * destination backend's native pre-staff game mode/inventory snapshot before transferred vanish
 * state is applied, while still suppressing join presence for vanished staff.</p>
 */
public final class StaffTransferJoinListener implements Listener {
    private final JavaPlugin plugin;
    private final StaffTransferSnapshotCoordinator snapshots;
    private final VanishManager vanish;

    public StaffTransferJoinListener(
            JavaPlugin plugin,
            StaffTransferSnapshotCoordinator snapshots,
            VanishManager vanish
    ) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.snapshots = Objects.requireNonNull(snapshots, "snapshots");
        this.vanish = Objects.requireNonNull(vanish, "vanish");
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        Optional<StaffTransferSnapshot> pending = snapshots.consume(player.getUniqueId());
        if (pending.isEmpty()) {
            return;
        }
        StaffTransferSnapshot snapshot = pending.orElseThrow();
        if (!snapshot.vanished()) {
            return;
        }
        try {
            vanish.applyTransferSnapshot(player, snapshot);
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.WARNING,
                    "Cross-server vanish snapshot apply failed for " + player.getUniqueId()
                            + "; the asynchronous database fallback will reconcile the state",
                    exception);
        }
    }
}
