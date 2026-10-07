package net.enthusia.staff.paper.punishment.policyv2;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.logging.Level;
import net.enthusia.staff.domain.policyv2.enforcement.PolicyV2AccessEvaluator;
import net.enthusia.staff.domain.policyv2.enforcement.PolicyV2RemedyEnforcement.Scope;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Passive Policy v2 observations attached to real Paper runtime events.
 *
 * <p>The listener never cancels an event, kicks a player, or changes a v1
 * authorization result. It only submits W5B-gated shadow observations.</p>
 */
final class PolicyV2ShadowComplianceListener implements Listener {
    private static final String REPORT_COMMAND = "/report";

    private final JavaPlugin plugin;
    private final ExecutorService workers;
    private final PolicyV2ShadowEnforcementRuntime runtime;

    PolicyV2ShadowComplianceListener(
            JavaPlugin plugin,
            ExecutorService workers,
            PolicyV2ShadowEnforcementRuntime runtime
    ) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.workers = Objects.requireNonNull(workers, "workers");
        this.runtime = Objects.requireNonNull(runtime, "runtime");
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        UUID subjectId = event.getPlayer().getUniqueId();
        String username = event.getPlayer().getName();
        submit(() -> runtime.observeAccess(new PolicyV2AccessEvaluator.Observation(
                subjectId,
                username,
                PolicyV2AccessEvaluator.VpnState.UNKNOWN,
                Map.of()
        )));
    }

    @EventHandler(ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        if (!isReport(event.getMessage())) {
            return;
        }
        UUID subjectId = event.getPlayer().getUniqueId();
        submit(() -> runtime.observeCapability(subjectId, Scope.REPORT_SUBMISSION));
    }

    private void submit(Runnable observation) {
        try {
            workers.execute(() -> observeSafely(observation));
        } catch (RejectedExecutionException exception) {
            logFine("Policy v2 shadow observation queue is full", exception);
        }
    }

    private void observeSafely(Runnable observation) {
        try {
            observation.run();
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.WARNING, "Policy v2 shadow observation failed", exception);
        }
    }

    private static boolean isReport(String commandLine) {
        if (commandLine == null) {
            return false;
        }
        String normalized = commandLine.stripLeading().toLowerCase(java.util.Locale.ROOT);
        return normalized.equals(REPORT_COMMAND) || normalized.startsWith(REPORT_COMMAND + " ");
    }

    private void logFine(String message, RuntimeException exception) {
        if (plugin.getLogger().isLoggable(Level.FINE)) {
            plugin.getLogger().log(Level.FINE, message, exception);
        }
    }
}
