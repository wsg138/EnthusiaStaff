package net.enthusia.staff.paper.command;

import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import net.enthusia.staff.paper.RuntimeHealth;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Produces the non-destructive portion of the operator full-runtime diagnostic.
 *
 * <p>The verifier deliberately observes published runtime state rather than
 * sending provider commands, creating database connections, or mutating data.
 * Categories that cannot be proved through those safe observations are reported
 * as warnings instead of being presented as passing.</p>
 */
final class FullRuntimeVerifier {
    private static final String ESTAFF_COMMAND = "estaff";

    private final JavaPlugin plugin;
    private final RuntimeHealth health;
    private final BooleanSupplier storagePublished;

    FullRuntimeVerifier(JavaPlugin plugin, RuntimeHealth health, BooleanSupplier storagePublished) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.health = Objects.requireNonNull(health, "health");
        this.storagePublished = Objects.requireNonNull(storagePublished, "storagePublished");
    }

    List<String> verify() {
        List<String> messages = new ArrayList<>();
        RuntimeHealth.Snapshot snapshot = health.snapshot();
        messages.add("Full verification: WARNING (non-destructive observations)");
        messages.add("Runtime mode: " + snapshot.mode());
        appendRuntimeHealth(snapshot, messages);
        appendStorageAndMigrations(messages);
        appendCommandRegistration(messages);
        appendArtifact(messages);
        appendIntegrations(messages);
        messages.add(
                "WARNING command conflicts: Bukkit's supported API cannot inspect the effective command map; "
                        + "verify /help estaff on each backend."
        );
        messages.add(
                "WARNING provider compatibility: no provider operation, backend message, or database mutation was run; "
                        + "use staged compatibility checks."
        );
        return List.copyOf(messages);
    }

    private static void appendRuntimeHealth(RuntimeHealth.Snapshot snapshot, List<String> messages) {
        if (snapshot.issues().isEmpty()) {
            messages.add("PASS runtime health: no active issue is published.");
            return;
        }
        snapshot.issues().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(issue -> messages.add("WARNING runtime " + issue.getKey() + ": " + issue.getValue()));
    }

    private void appendStorageAndMigrations(List<String> messages) {
        if (storagePublished.getAsBoolean()) {
            messages.add(
                    "PASS storage/migrations: the published runtime completed bootstrap; no new database query was run."
            );
            return;
        }
        messages.add(
                "DISABLED storage/migrations: no published storage runtime is available; no migration state was queried."
        );
    }

    private void appendCommandRegistration(List<String> messages) {
        PluginCommand command = plugin.getCommand(ESTAFF_COMMAND);
        if (command != null && command.getExecutor() instanceof EstaffCommand
                && command.getTabCompleter() instanceof EstaffCommand) {
            messages.add("PASS command registration: /estaff is bound to the EnthusiaStaff executor.");
            return;
        }
        messages.add("CRITICAL command registration: /estaff is not bound to the EnthusiaStaff executor.");
    }

    private void appendArtifact(List<String> messages) {
        try {
            Path artifact = Path.of(
                    plugin.getClass().getProtectionDomain().getCodeSource().getLocation().toURI()
            );
            if (Files.isRegularFile(artifact) && Files.isReadable(artifact)) {
                messages.add("PASS runtime artifact: the loaded plugin archive is readable.");
                return;
            }
            messages.add("CRITICAL runtime artifact: the loaded plugin archive is not readable.");
        } catch (URISyntaxException | RuntimeException exception) {
            messages.add("WARNING runtime artifact: archive readability could not be inspected safely.");
        }
    }

    @SuppressWarnings("deprecation")
    private void appendIntegrations(List<String> messages) {
        List<String> dependencies = new ArrayList<>(plugin.getDescription().getSoftDepend());
        dependencies.sort(Comparator.naturalOrder());
        if (dependencies.isEmpty()) {
            messages.add("PASS integrations: no optional providers are declared.");
            return;
        }
        PluginManager pluginManager = plugin.getServer().getPluginManager();
        for (String dependency : dependencies) {
            Plugin provider = pluginManager.getPlugin(dependency);
            if (provider == null) {
                messages.add("DISABLED integration " + dependency + ": optional provider is not installed.");
            } else if (!provider.isEnabled()) {
                messages.add("WARNING integration " + dependency + ": provider is installed but disabled.");
            } else {
                messages.add(
                        "WARNING integration " + dependency
                                + ": provider is enabled; capability compatibility is represented only by runtime health."
                );
            }
        }
    }
}
