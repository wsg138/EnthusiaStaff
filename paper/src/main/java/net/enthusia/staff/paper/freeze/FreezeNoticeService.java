package net.enthusia.staff.paper.freeze;

import java.time.Clock;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Supplier;
import java.util.logging.Level;
import net.enthusia.staff.domain.freeze.FreezeRecord;
import net.enthusia.staff.domain.player.PlayerIdentity;
import net.enthusia.staff.domain.ports.FreezeStore;
import net.enthusia.staff.domain.ports.PlayerDirectory;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

public final class FreezeNoticeService implements Listener, FreezeNoticeSink {
    private final JavaPlugin plugin;
    private final Clock clock;
    private final Supplier<FreezeStore> freezes;
    private final Supplier<PlayerDirectory> players;
    private final ExecutorService workers;
    private final FreezeManager manager;

    public FreezeNoticeService(
            JavaPlugin plugin,
            Clock clock,
            Supplier<FreezeStore> freezes,
            Supplier<PlayerDirectory> players,
            ExecutorService workers,
            FreezeManager manager
    ) {
        this.plugin = java.util.Objects.requireNonNull(plugin, "plugin");
        this.clock = java.util.Objects.requireNonNull(clock, "clock");
        this.freezes = java.util.Objects.requireNonNull(freezes, "freezes");
        this.players = java.util.Objects.requireNonNull(players, "players");
        this.workers = java.util.Objects.requireNonNull(workers, "workers");
        this.manager = java.util.Objects.requireNonNull(manager, "manager");
    }

    @Override
    public void show(FreezeRecord record, String actorName) {
        if (record == null) {
            return;
        }
        onEntity(record.playerId(), player -> deliverIfRestricted(manager, record, actorName, player));
    }

    static void deliverIfRestricted(
            FreezeManager manager,
            FreezeRecord record,
            String actorName,
            Player player
    ) {
        if (!manager.isRestricted(record.playerId())) {
            return;
        }
        FreezeNoticePresentation.render(record, actorName).forEach(player::sendMessage);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        submit(() -> showStoredNotice(playerId));
    }

    private void showStoredNotice(UUID playerId) {
        FreezeStore store = freezes.get();
        if (store == null) {
            return;
        }
        try {
            FreezeRecord record = store.active(playerId, clock.instant()).orElse(null);
            if (record != null) {
                show(record, actorName(record.frozenBy()));
            }
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.WARNING, "Freeze notice lookup failed", exception);
        }
    }

    private String actorName(UUID actorId) {
        PlayerDirectory directory = players.get();
        if (directory == null) {
            return actorId.toString();
        }
        try {
            PlayerIdentity actor = directory.find(actorId.toString()).orElse(null);
            return actor == null ? actorId.toString() : actor.currentUsername().orElse(actorId.toString());
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.FINE, "Freeze actor name lookup failed", exception);
            return actorId.toString();
        }
    }

    private void submit(Runnable operation) {
        try {
            workers.execute(operation);
        } catch (RejectedExecutionException exception) {
            plugin.getLogger().warning("Freeze notice lookup skipped because the bounded queue is full");
        }
    }

    private void onEntity(UUID playerId, java.util.function.Consumer<Player> operation) {
        plugin.getServer().getGlobalRegionScheduler().execute(plugin, () -> {
            Player player = plugin.getServer().getPlayer(playerId);
            if (player != null) {
                player.getScheduler().execute(plugin, () -> operation.accept(player), null, 1L);
            }
        });
    }
}
