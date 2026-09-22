package net.enthusia.staff.paper.scheduler;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/** Schedules a player-owned action once, falling back if the player retires first. */
public final class PlayerEntityScheduler {
    private PlayerEntityScheduler() {
    }

    public static boolean execute(Plugin plugin, Player player, Runnable action, Runnable retired) {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(retired, "retired");
        AtomicBoolean retiredOnce = new AtomicBoolean();
        Runnable finishRetired = () -> {
            if (retiredOnce.compareAndSet(false, true)) {
                retired.run();
            }
        };
        try {
            boolean scheduled = player.getScheduler().execute(plugin, action, finishRetired, 1L);
            if (!scheduled) {
                finishRetired.run();
            }
            return scheduled;
        } catch (RuntimeException exception) {
            finishRetired.run();
            return false;
        }
    }
}
