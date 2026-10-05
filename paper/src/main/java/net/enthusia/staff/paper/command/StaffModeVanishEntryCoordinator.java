package net.enthusia.staff.paper.command;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Level;
import net.enthusia.staff.domain.OperationalMode;
import net.enthusia.staff.domain.auth.StaffRank;
import net.enthusia.staff.domain.ports.VanishStore;
import net.enthusia.staff.paper.auth.PaperStaffRankResolver;
import net.enthusia.staff.paper.presentation.StaffMessageStyle;
import net.enthusia.staff.paper.staff.StaffModeManager;
import net.enthusia.staff.paper.visibility.VanishManager;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Resolves the durable vanish preference before Staff Mode activation, then stages that resolved
 * choice for the normal Staff Mode entry listener. This avoids a post-activation preference lookup
 * window and keeps the live/durable visibility mutation serialized through VanishManager.
 */
public final class StaffModeVanishEntryCoordinator {
    private static final boolean FIRST_ENTRY_VANISHED = true;
    private static final int ACTIVATION_CHECKS = 200;
    private static final long ACTIVATION_POLL_TICKS = 1L;

    private final JavaPlugin plugin;
    private final Supplier<OperationalMode> mode;
    private final StaffModeManager staffMode;
    private final VanishManager vanish;
    private final Supplier<VanishStore> store;
    private final ExecutorService workers;
    private final Set<UUID> pendingEntries = ConcurrentHashMap.newKeySet();

    public StaffModeVanishEntryCoordinator(
            JavaPlugin plugin,
            Supplier<OperationalMode> mode,
            StaffModeManager staffMode,
            VanishManager vanish,
            Supplier<VanishStore> store,
            ExecutorService workers
    ) {
        this.plugin = java.util.Objects.requireNonNull(plugin, "plugin");
        this.mode = java.util.Objects.requireNonNull(mode, "mode");
        this.staffMode = java.util.Objects.requireNonNull(staffMode, "staffMode");
        this.vanish = java.util.Objects.requireNonNull(vanish, "vanish");
        this.store = java.util.Objects.requireNonNull(store, "store");
        this.workers = java.util.Objects.requireNonNull(workers, "workers");
    }

    public void enter(Player player, StaffModeVanishEntryOption option) {
        UUID playerId = player.getUniqueId();
        if (!pendingEntries.add(playerId)) {
            player.sendMessage(StaffMessageStyle.style(Component.text(
                    "A staff-mode entry is already in progress."
            )));
            return;
        }
        if (!submit(() -> resolvePreference(playerId, player, option))) {
            pendingEntries.remove(playerId);
            player.sendMessage(StaffMessageStyle.style(Component.text(
                    "The bounded work queue is full; staff mode was not entered."
            )));
        }
    }

    static EntryChoice resolveChoice(
            StaffModeVanishEntryOption option,
            Optional<Boolean> remembered
    ) {
        boolean desired = option.override().orElseGet(() -> remembered.orElse(FIRST_ENTRY_VANISHED));
        return new EntryChoice(desired, option.explicit() || remembered.isEmpty());
    }

    private void resolvePreference(
            UUID playerId,
            Player expectedPlayer,
            StaffModeVanishEntryOption option
    ) {
        try {
            VanishStore loaded = store.get();
            if (loaded == null) {
                throw new IllegalStateException("vanish storage is not ready");
            }
            EntryChoice choice = resolveChoice(option, loaded.preferred(playerId));
            onEntity(
                    playerId,
                    expectedPlayer,
                    player -> beginEntry(player, choice),
                    () -> pendingEntries.remove(playerId)
            );
        } catch (RuntimeException exception) {
            pendingEntries.remove(playerId);
            plugin.getLogger().log(Level.SEVERE, "Staff-mode vanish preference lookup failed", exception);
            message(
                    playerId,
                    expectedPlayer,
                    "Staff mode was not entered because your vanish preference could not be loaded."
            );
        }
    }

    private void beginEntry(Player player, EntryChoice choice) {
        UUID playerId = player.getUniqueId();
        if (staffMode.active(playerId)) {
            pendingEntries.remove(playerId);
            player.sendMessage(StaffMessageStyle.style(Component.text(
                    "Staff mode is already active. Use /vanish to change visibility."
            )));
            return;
        }
        OperationalMode currentMode = mode.get();
        if (!StaffOperationalModeGate.staffModeTransitionAllowed(currentMode, false)) {
            pendingEntries.remove(playerId);
            player.sendMessage(StaffMessageStyle.style(Component.text(
                    "Staff-mode entry is disabled while moderation is " + currentMode + '.'
            )));
            return;
        }
        StaffRank rank = PaperStaffRankResolver.resolve(player::hasPermission).orElse(null);
        if (rank == null) {
            pendingEntries.remove(playerId);
            player.sendMessage(StaffMessageStyle.style(Component.text(
                    "An explicit EnthusiaStaff rank is required before entering staff mode."
            )));
            return;
        }

        UUID preferenceToken = vanish.prepareStaffModeEntry(
                playerId,
                choice.desired(),
                choice.rememberChoice()
        );
        staffMode.enter(player, rank);
        scheduleActivationCheck(player, preferenceToken, ACTIVATION_CHECKS);
    }

    private void scheduleActivationCheck(
            Player player,
            UUID preferenceToken,
            int checksRemaining
    ) {
        UUID playerId = player.getUniqueId();
        if (staffMode.authorityActive(playerId)) {
            pendingEntries.remove(playerId);
            return;
        }
        if (checksRemaining < ACTIVATION_CHECKS - 2
                && !staffMode.active(playerId)
                && !staffMode.transitioning(playerId)) {
            vanish.cancelPreparedStaffModeEntry(playerId, preferenceToken);
            pendingEntries.remove(playerId);
            return;
        }
        if (checksRemaining == 0) {
            vanish.cancelPreparedStaffModeEntry(playerId, preferenceToken);
            pendingEntries.remove(playerId);
            if (staffMode.active(playerId)) {
                player.sendMessage(StaffMessageStyle.style(Component.text(
                        "Staff mode is still transitioning; your staged visibility choice was cancelled."
                )));
            }
            return;
        }
        boolean scheduled = player.getScheduler().execute(
                plugin,
                () -> scheduleActivationCheck(player, preferenceToken, checksRemaining - 1),
                () -> {
                    vanish.cancelPreparedStaffModeEntry(playerId, preferenceToken);
                    pendingEntries.remove(playerId);
                },
                ACTIVATION_POLL_TICKS
        );
        if (!scheduled) {
            vanish.cancelPreparedStaffModeEntry(playerId, preferenceToken);
            pendingEntries.remove(playerId);
        }
    }

    private boolean submit(Runnable operation) {
        try {
            workers.execute(operation);
            return true;
        } catch (RejectedExecutionException exception) {
            plugin.getLogger().warning("Staff-mode vanish operation skipped because the bounded queue is full");
            return false;
        }
    }

    private void message(UUID playerId, Player expectedPlayer, String text) {
        onEntity(
                playerId,
                expectedPlayer,
                player -> player.sendMessage(StaffMessageStyle.style(Component.text(text))),
                () -> {
                }
        );
    }

    private void onEntity(
            UUID playerId,
            Player expectedPlayer,
            Consumer<Player> operation,
            Runnable retired
    ) {
        plugin.getServer().getGlobalRegionScheduler().execute(plugin, () -> {
            Player player = plugin.getServer().getPlayer(playerId);
            if (player == null || player != expectedPlayer) {
                retired.run();
                return;
            }
            boolean scheduled = player.getScheduler().execute(
                    plugin,
                    () -> operation.accept(player),
                    retired,
                    ACTIVATION_POLL_TICKS
            );
            if (!scheduled) {
                retired.run();
            }
        });
    }

    record EntryChoice(boolean desired, boolean rememberChoice) {
    }
}
