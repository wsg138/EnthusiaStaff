package net.enthusia.staff.paper.integration;

import java.lang.reflect.InvocationTargetException;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.enthusia.staff.domain.auth.StaffRank;
import net.enthusia.staff.paper.auth.PaperStaffRankResolver;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Optional Polar compatibility boundary.
 *
 * <p>Polar's loader callback is prepared during plugin load, while Bukkit/Folia player state is
 * mirrored into immutable snapshots from normal player lifecycle work after Staff enables.
 * The Polar callback reads only those snapshots and never touches Bukkit player state.</p>
 */
public final class PolarSpectatorPhaseCompatibility {
    private static final String FEATURE_KEY = "polar-spectator-phase";
    private static final String POLAR_LOADER = "PolarLoader";
    private static final String UNRESTRICTED_PERMISSION = "enthusiastaff.identity.unrestricted";
    private static final String HOOK_CLASS =
            "net.enthusia.staff.paper.integration.PolarSpectatorPhaseHook";
    private static final Map<UUID, EligibilitySnapshot> PLAYER_STATES = new ConcurrentHashMap<>();

    private PolarSpectatorPhaseCompatibility() {
    }

    public static void prepareOnLoad(JavaPlugin plugin, Map<String, String> featureIssues) {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(featureIssues, "featureIssues");
        if (plugin.getServer().getPluginManager().getPlugin(POLAR_LOADER) == null) {
            featureIssues.remove(FEATURE_KEY);
            return;
        }
        try {
            Class<?> hook = Class.forName(HOOK_CLASS);
            hook.getMethod("registerEnableCallback", JavaPlugin.class).invoke(null, plugin);
            featureIssues.remove(FEATURE_KEY);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException failure) {
            featureIssues.put(
                    FEATURE_KEY,
                    "Polar is present but its Spectator phase compatibility callback could not be prepared"
            );
            plugin.getLogger().log(
                    Level.WARNING,
                    "Polar Spectator phase compatibility callback could not be prepared",
                    unwrap(failure)
            );
        }
    }

    public static void installEligibilityTracking(JavaPlugin plugin) {
        Objects.requireNonNull(plugin, "plugin");
        PLAYER_STATES.clear();
        if (!plugin.getServer().getPluginManager().isPluginEnabled(POLAR_LOADER)) {
            return;
        }
        EligibilityListener listener = new EligibilityListener(plugin.getLogger());
        plugin.getServer().getPluginManager().registerEvents(listener, plugin);
        reconcileOnlinePlayers(plugin, listener);
        plugin.getServer().getGlobalRegionScheduler().runAtFixedRate(
                plugin,
                ignored -> reconcileOnlinePlayers(plugin, listener),
                1L,
                2L
        );
    }

    private static void reconcileOnlinePlayers(JavaPlugin plugin, EligibilityListener listener) {
        Set<UUID> online = ConcurrentHashMap.newKeySet();
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            online.add(player.getUniqueId());
            listener.refresh(player, player.getGameMode());
        }
        PLAYER_STATES.keySet().retainAll(online);
    }

    static boolean eligible(UUID playerId) {
        EligibilitySnapshot snapshot = snapshot(playerId);
        return snapshot != null && snapshot.eligible();
    }

    static EligibilitySnapshot snapshot(UUID playerId) {
        return playerId == null ? null : PLAYER_STATES.get(playerId);
    }

    public static void close(JavaPlugin plugin) {
        PLAYER_STATES.clear();
        if (plugin == null || plugin.getServer().getPluginManager().getPlugin(POLAR_LOADER) == null) {
            return;
        }
        try {
            Class<?> hook = Class.forName(HOOK_CLASS);
            hook.getMethod("close").invoke(null);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException failure) {
            plugin.getLogger().log(Level.FINE, "Polar Spectator compatibility cleanup failed", unwrap(failure));
        }
    }

    private static Throwable unwrap(Throwable failure) {
        if (failure instanceof InvocationTargetException invocation && invocation.getCause() != null) {
            return invocation.getCause();
        }
        return failure;
    }

    static record EligibilitySnapshot(
            GameMode gameMode,
            StaffRank resolvedRank,
            StaffRank identityRank,
            StaffRank legacyRank,
            boolean unrestricted,
            boolean eligible,
            long refreshedAtNanos
    ) {
        EligibilitySnapshot {
            Objects.requireNonNull(gameMode, "gameMode");
        }

        boolean hasStaffSignal() {
            return resolvedRank != null || identityRank != null || legacyRank != null || unrestricted;
        }

        boolean sameSemanticState(EligibilitySnapshot other) {
            return other != null
                    && gameMode == other.gameMode
                    && resolvedRank == other.resolvedRank
                    && identityRank == other.identityRank
                    && legacyRank == other.legacyRank
                    && unrestricted == other.unrestricted
                    && eligible == other.eligible;
        }

        long ageMillis(long nowNanos) {
            return Math.max(0L, nowNanos - refreshedAtNanos) / 1_000_000L;
        }

        String summary() {
            return "gameMode=" + gameMode
                    + " resolvedRank=" + resolvedRank
                    + " identityRank=" + identityRank
                    + " legacyRank=" + legacyRank
                    + " unrestricted=" + unrestricted
                    + " eligible=" + eligible;
        }
    }

    private static final class EligibilityListener implements Listener {
        private final Logger logger;

        private EligibilityListener(Logger logger) {
            this.logger = Objects.requireNonNull(logger, "logger");
        }

        @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
        public void onGameModeChange(PlayerGameModeChangeEvent event) {
            refresh(event.getPlayer(), event.getNewGameMode());
        }

        @EventHandler
        public void onJoin(PlayerJoinEvent event) {
            refresh(event.getPlayer(), event.getPlayer().getGameMode());
        }

        @EventHandler
        public void onQuit(PlayerQuitEvent event) {
            PLAYER_STATES.remove(event.getPlayer().getUniqueId());
        }

        private void refresh(Player player, GameMode gameMode) {
            StaffRank identityRank = PaperStaffRankResolver.resolveIdentity(player::hasPermission).orElse(null);
            StaffRank legacyRank = PaperStaffRankResolver.resolveLegacyRank(player::hasPermission).orElse(null);
            StaffRank resolvedRank = identityRank != null ? identityRank : legacyRank;
            boolean unrestricted = player.hasPermission(UNRESTRICTED_PERMISSION);
            boolean eligible = PolarSpectatorPhasePolicy.eligibleSpectator(gameMode, resolvedRank);
            EligibilitySnapshot next = new EligibilitySnapshot(
                    gameMode,
                    resolvedRank,
                    identityRank,
                    legacyRank,
                    unrestricted,
                    eligible,
                    System.nanoTime()
            );
            EligibilitySnapshot previous = PLAYER_STATES.put(player.getUniqueId(), next);
            if (shouldLogTransition(previous, next) && logger.isLoggable(Level.INFO)) {
                logger.info(
                        "Polar Spectator eligibility state: player=" + player.getUniqueId() + " " + next.summary()
                );
            }
        }

        private static boolean shouldLogTransition(
                EligibilitySnapshot previous,
                EligibilitySnapshot next
        ) {
            return !next.sameSemanticState(previous)
                    && (spectatorOrEligible(next) || spectatorOrEligible(previous));
        }

        private static boolean spectatorOrEligible(EligibilitySnapshot snapshot) {
            return snapshot != null
                    && (snapshot.gameMode() == GameMode.SPECTATOR || snapshot.eligible());
        }
    }
}
