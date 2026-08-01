package net.enthusia.staff.paper.alert;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
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
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

final class BukkitPunishmentRequestAlertRuntime implements PunishmentRequestAlertRuntime {
    private final JavaPlugin plugin;
    private final Map<UUID, Player> onlinePlayers = new ConcurrentHashMap<>();
    private final Map<UUID, PunishmentRequestAlertRecipient> recipientSnapshots =
            new ConcurrentHashMap<>();

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
        List<Player> online = plugin.getServer().getOnlinePlayers().stream()
                .limit(limit)
                .toList();
        List<PunishmentRequestAlertRecipient> cached = new ArrayList<>(online.size());
        for (Player player : online) {
            UUID playerId = player.getUniqueId();
            onlinePlayers.put(playerId, player);
            refreshSnapshot(player, playerId);
            PunishmentRequestAlertRecipient snapshot = recipientSnapshots.get(playerId);
            cached.add(snapshot == null
                    ? new PunishmentRequestAlertRecipient(playerId, playerId.toString(), null)
                    : snapshot);
        }
        return List.copyOf(cached);
    }

    @Override
    public Optional<PunishmentRequestAlertRecipient> snapshotRecipient(UUID playerId) {
        return Optional.ofNullable(recipientSnapshots.get(playerId));
    }

    @Override
    public Optional<PunishmentRequestAlertRecipient> currentRecipient(UUID playerId) {
        Player player = onlinePlayers.get(playerId);
        if (player == null || !player.isOnline()) {
            forget(playerId);
            return Optional.empty();
        }
        PunishmentRequestAlertRecipient snapshot = snapshot(player);
        recipientSnapshots.put(playerId, snapshot);
        return Optional.of(snapshot);
    }

    @Override
    public boolean present(
            PunishmentRequestAlertRecipient recipient,
            PunishmentRequestAlertPresentation presentation
    ) {
        Player player = onlinePlayers.get(recipient.playerId());
        if (player == null || !player.isOnline()) {
            forget(recipient.playerId());
            return false;
        }
        player.sendMessage(presentation.message());
        return true;
    }

    @Override
    public AutoCloseable registerJoinListener(Consumer<UUID> listener) {
        JoinListener registered = new JoinListener(
                player -> {
                    UUID playerId = player.getUniqueId();
                    onlinePlayers.put(playerId, player);
                    recipientSnapshots.put(playerId, snapshot(player));
                    listener.accept(playerId);
                },
                this::forget
        );
        plugin.getServer().getPluginManager().registerEvents(registered, plugin);
        return () -> {
            HandlerList.unregisterAll(registered);
            onlinePlayers.clear();
            recipientSnapshots.clear();
        };
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
    public boolean executeForRecipient(
            UUID playerId,
            Runnable action,
            Runnable retired
    ) {
        Player player = onlinePlayers.get(playerId);
        if (player == null) {
            recipientSnapshots.remove(playerId);
            return false;
        }
        return player.getScheduler().execute(
                plugin,
                action,
                () -> {
                    forget(playerId);
                    retired.run();
                },
                1L
        );
    }

    @Override
    public void executeSynchronously(Runnable action) {
        plugin.getServer().getGlobalRegionScheduler().execute(plugin, action);
    }

    @Override
    public Logger logger() {
        return plugin.getLogger();
    }

    private void refreshSnapshot(Player player, UUID playerId) {
        boolean scheduled = player.getScheduler().execute(
                plugin,
                () -> {
                    if (player.isOnline()) {
                        recipientSnapshots.put(playerId, snapshot(player));
                    } else {
                        forget(playerId);
                    }
                },
                () -> forget(playerId),
                1L
        );
        if (!scheduled) {
            forget(playerId);
        }
    }

    private void forget(UUID playerId) {
        onlinePlayers.remove(playerId);
        recipientSnapshots.remove(playerId);
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
        private final Consumer<Player> joined;
        private final Consumer<UUID> quit;

        private JoinListener(Consumer<Player> joined, Consumer<UUID> quit) {
            if (joined == null || quit == null) {
                throw new IllegalArgumentException("join and quit listeners must be present");
            }
            this.joined = joined;
            this.quit = quit;
        }

        @EventHandler
        public void onJoin(PlayerJoinEvent event) {
            joined.accept(event.getPlayer());
        }

        @EventHandler
        public void onQuit(PlayerQuitEvent event) {
            quit.accept(event.getPlayer().getUniqueId());
        }
    }
}
