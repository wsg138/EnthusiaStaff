package net.enthusia.staff.paper.freeze;

import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Supplier;
import java.util.logging.Level;
import net.enthusia.staff.domain.freeze.FreezeRecord;
import net.enthusia.staff.domain.player.PlayerIdentity;
import net.enthusia.staff.domain.ports.PlayerDirectory;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class FreezeNoticeService implements FreezeNoticeSink {
    private final JavaPlugin plugin;
    private final Supplier<PlayerDirectory> players;
    private final ExecutorService workers;
    private final FreezeManager manager;

    public FreezeNoticeService(
            JavaPlugin plugin,
            Supplier<PlayerDirectory> players,
            ExecutorService workers,
            FreezeManager manager
    ) {
        this.plugin = java.util.Objects.requireNonNull(plugin, "plugin");
        this.players = java.util.Objects.requireNonNull(players, "players");
        this.workers = java.util.Objects.requireNonNull(workers, "workers");
        this.manager = java.util.Objects.requireNonNull(manager, "manager");
    }

    @Override
    public void show(FreezeRecord record, String actorName, long generation) {
        if (record == null) {
            return;
        }
        String supplied = actorName == null ? "" : actorName.trim();
        if (!supplied.isEmpty()) {
            schedule(record, supplied, generation);
            return;
        }
        submit(() -> schedule(record, actorName(record.frozenBy()), generation));
    }

    private void schedule(FreezeRecord record, String actorName, long generation) {
        onEntity(record.playerId(), player -> deliverIfCurrent(manager, record, actorName, generation, player));
    }

    static void deliverIfCurrent(
            FreezeManager manager,
            FreezeRecord record,
            String actorName,
            long generation,
            Player player
    ) {
        if (!manager.isCurrentFrozen(record.playerId(), generation)) {
            return;
        }
        FreezeNoticePresentation.render(record, actorName).forEach(player::sendMessage);
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
