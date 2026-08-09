package net.enthusia.staff.paper.tester;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

final class CheatTesterRuntimeSupport {
    private final JavaPlugin plugin;
    private final ExecutorService workers;
    private final CheatTesterSettings settings;
    private final java.util.concurrent.atomic.AtomicBoolean closed;

    CheatTesterRuntimeSupport(
            JavaPlugin plugin,
            ExecutorService workers,
            CheatTesterSettings settings,
            java.util.concurrent.atomic.AtomicBoolean closed
    ) {
        this.plugin = java.util.Objects.requireNonNull(plugin, "plugin");
        this.workers = java.util.Objects.requireNonNull(workers, "workers");
        this.settings = java.util.Objects.requireNonNull(settings, "settings");
        this.closed = java.util.Objects.requireNonNull(closed, "closed");
    }

    void scheduleTarget(UUID targetId, Runnable operation, Runnable retired) {
        plugin.getServer().getGlobalRegionScheduler().execute(plugin, () -> {
            Player target = plugin.getServer().getPlayer(targetId);
            if (target == null || !target.isOnline()
                    || !target.getScheduler().execute(plugin, operation, retired, 1L)) {
                retired.run();
            }
        });
    }

    boolean submit(Runnable operation) {
        if (closed.get() || workers.isShutdown()) {
            return false;
        }
        try {
            workers.execute(operation);
            return true;
        } catch (RejectedExecutionException exception) {
            return false;
        }
    }

    void message(UUID playerId, Component message) {
        plugin.getServer().getGlobalRegionScheduler().execute(plugin, () -> {
            Player player = plugin.getServer().getPlayer(playerId);
            if (player != null) {
                player.getScheduler().execute(plugin, () -> player.sendMessage(message), null, 1L);
            }
        });
    }

    long timeoutTicks() {
        long byDuration = Math.max(1L, settings.sessionTimeout().toMillis() / 50L);
        return Math.max(1L, Math.min(settings.probeTicks(), byDuration));
    }

    static void cancel(ScheduledTask task) {
        if (task == null) {
            return;
        }
        try {
            task.cancel();
        } catch (RuntimeException ignored) {
            // Cleanup is idempotent; the durable journal remains authoritative.
        }
    }

    static String safePreparationMessage(RuntimeException exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank()
                ? "Cheat Tester preparation failed before any target state changed."
                : message;
    }

    static String requireServerId(String serverId) {
        if (serverId == null || !serverId.matches("[A-Za-z0-9_-]{1,64}")) {
            throw new IllegalArgumentException("serverId is invalid");
        }
        return serverId;
    }
}
