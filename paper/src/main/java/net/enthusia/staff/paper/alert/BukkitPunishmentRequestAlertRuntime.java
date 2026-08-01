package net.enthusia.staff.paper.alert;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.logging.Logger;
import net.enthusia.staff.domain.auth.StaffRank;
import net.enthusia.staff.paper.auth.PaperStaffRankResolver;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

final class BukkitPunishmentRequestAlertRuntime implements PunishmentRequestAlertRuntime {
    private final JavaPlugin plugin;

    BukkitPunishmentRequestAlertRuntime(JavaPlugin plugin) {
        if (plugin == null) {
            throw new IllegalArgumentException("plugin must be present");
        }
        this.plugin = plugin;
    }

    @Override
    public List<PunishmentRequestAlertRecipient> onlineRecipients(int limit) {
        if (limit < 1) {
            throw new IllegalArgumentException("recipient limit must be positive");
        }
        return plugin.getServer().getOnlinePlayers().stream()
                .limit(limit)
                .map(this::snapshot)
                .toList();
    }

    @Override
    public Optional<PunishmentRequestAlertRecipient> currentRecipient(UUID playerId) {
        Player player = plugin.getServer().getPlayer(playerId);
        if (player == null || !player.isOnline()) {
            return Optional.empty();
        }
        return Optional.of(snapshot(player));
    }

    @Override
    public boolean present(
            PunishmentRequestAlertRecipient recipient,
            PunishmentRequestAlertPresentation presentation
    ) {
        Player player = plugin.getServer().getPlayer(recipient.playerId());
        if (player == null || !player.isOnline()) {
            return false;
        }
        player.sendMessage(presentation.message());
        return true;
    }

    @Override
    public AutoCloseable registerJoinListener(Consumer<UUID> listener) {
        JoinListener registered = new JoinListener(listener);
        plugin.getServer().getPluginManager().registerEvents(registered, plugin);
        return () -> HandlerList.unregisterAll(registered);
    }

    @Override
    public Cancellable scheduleSynchronousRepeating(
            Runnable action,
            Duration initialDelay,
            Duration interval
    ) {
        ScheduledTask task = plugin.getServer().getGlobalRegionScheduler().runAtFixedRate(
                plugin,
                ignored -> action.run(),
                ticks(initialDelay),
                ticks(interval)
        );
        return task::cancel;
    }

    @Override
    public Cancellable scheduleSynchronousDelayed(Runnable action, Duration delay) {
        ScheduledTask task = plugin.getServer().getGlobalRegionScheduler().runDelayed(
                plugin,
                ignored -> action.run(),
                ticks(delay)
        );
        return task::cancel;
    }

    @Override
    public Cancellable scheduleAsynchronousRepeating(
            Runnable action,
            Duration initialDelay,
            Duration interval
    ) {
        ScheduledTask task = plugin.getServer().getAsyncScheduler().runAtFixedRate(
                plugin,
                ignored -> action.run(),
                initialDelay.toMillis(),
                interval.toMillis(),
                TimeUnit.MILLISECONDS
        );
        return task::cancel;
    }

    @Override
    public void executeSynchronously(Runnable action) {
        plugin.getServer().getGlobalRegionScheduler().execute(plugin, action);
    }

    @Override
    public Logger logger() {
        return plugin.getLogger();
    }

    private PunishmentRequestAlertRecipient snapshot(Player player) {
        StaffRank rank = PaperStaffRankResolver.resolve(player::hasPermission).orElse(null);
        return new PunishmentRequestAlertRecipient(player.getUniqueId(), player.getName(), rank);
    }

    private static long ticks(Duration duration) {
        long millis = Math.max(1L, duration.toMillis());
        long ticks = millis / 50L;
        if (millis % 50L != 0L) {
            ticks++;
        }
        return Math.max(1L, ticks);
    }

    private static final class JoinListener implements Listener {
        private final Consumer<UUID> listener;

        private JoinListener(Consumer<UUID> listener) {
            if (listener == null) {
                throw new IllegalArgumentException("join listener must be present");
            }
            this.listener = listener;
        }

        @EventHandler
        public void onJoin(PlayerJoinEvent event) {
            listener.accept(event.getPlayer().getUniqueId());
        }
    }
}
