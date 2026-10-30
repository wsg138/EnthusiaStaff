package net.enthusia.staff.paper.alert;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.logging.Level;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Staff-alert fanout for callbacks that may originate on storage workers.
 * The global region enumerates players only. Live permission/online checks
 * and message delivery must run on the recipient's entity owner.
 */
public final class StaffAlertOwnerDispatch {
    private StaffAlertOwnerDispatch() {
    }

    @FunctionalInterface
    interface OwnerScheduler {
        boolean submit(Player player, Runnable delivery);
    }

    static void dispatch(
            Iterable<? extends Player> recipients,
            OwnerScheduler ownerScheduler,
            Predicate<Player> currentlyAuthorized,
            Consumer<Player> deliver,
            Consumer<RuntimeException> onFailure
    ) {
        Objects.requireNonNull(recipients, "recipients");
        Objects.requireNonNull(ownerScheduler, "ownerScheduler");
        Objects.requireNonNull(currentlyAuthorized, "currentlyAuthorized");
        Objects.requireNonNull(deliver, "deliver");
        Objects.requireNonNull(onFailure, "onFailure");
        for (Player recipient : recipients) {
            try {
                ownerScheduler.submit(recipient, () -> {
                    try {
                        // Do not cache permissions while a callback waits for
                        // its owning region. Revoked or departed staff are skipped.
                        if (currentlyAuthorized.test(recipient)) {
                            deliver.accept(recipient);
                        }
                    } catch (RuntimeException failure) {
                        onFailure.accept(failure);
                    }
                });
            } catch (RuntimeException failure) {
                // One retired region must not suppress other staff alerts.
                onFailure.accept(failure);
            }
        }
    }

    /**
     * May be called from any thread. Never executes a Player operation on the
     * global region or falls back to global-thread delivery on rejection.
     */
    public static void alert(JavaPlugin plugin, Component message, Predicate<Player> currentAuthority) {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(message, "message");
        Objects.requireNonNull(currentAuthority, "currentAuthority");
        try {
            plugin.getServer().getGlobalRegionScheduler().execute(plugin, () -> {
                try {
                    dispatch(
                            plugin.getServer().getOnlinePlayers(),
                            (player, delivery) -> player.getScheduler().execute(plugin, delivery, null, 1L),
                            player -> player.isOnline() && currentAuthority.test(player),
                            player -> player.sendMessage(message),
                            failure -> plugin.getLogger().log(
                                    Level.WARNING, "Staff alert recipient delivery failed", failure)
                    );
                } catch (RuntimeException failure) {
                    plugin.getLogger().log(Level.WARNING, "Staff alert enumeration failed", failure);
                }
            });
        } catch (RuntimeException failure) {
            plugin.getLogger().log(Level.WARNING, "Staff alert global dispatch failed", failure);
        }
    }
}
