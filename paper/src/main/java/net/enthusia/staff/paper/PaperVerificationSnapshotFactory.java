package net.enthusia.staff.paper;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import net.enthusia.staff.paper.client.ClientEvidenceCollector;
import net.enthusia.staff.protocol.BackendVerificationReport;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

final class PaperVerificationSnapshotFactory {
    private static final Map<String, String> HEALTH_KEYS = Map.of(
            "EnthusiaCurrency", "currency",
            "EnthusiaMarket", "market",
            "EnthusiaCommend", "reputation",
            "RoseChat", "rosechat",
            "CombatLogX", "combatlogx"
    );

    private final JavaPlugin plugin;
    private final RuntimeHealth health;
    private final BooleanSupplier storageReady;
    private final BooleanSupplier channelConnected;
    private final String backendId;
    private final ClientEvidenceCollector clientEvidence;

    PaperVerificationSnapshotFactory(
            JavaPlugin plugin,
            RuntimeHealth health,
            BooleanSupplier storageReady,
            BooleanSupplier channelConnected,
            String backendId,
            ClientEvidenceCollector clientEvidence
    ) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.health = Objects.requireNonNull(health, "health");
        this.storageReady = Objects.requireNonNull(storageReady, "storageReady");
        this.channelConnected = Objects.requireNonNull(channelConnected, "channelConnected");
        this.backendId = Objects.requireNonNull(backendId, "backendId");
        this.clientEvidence = Objects.requireNonNull(clientEvidence, "clientEvidence");
    }

    BackendVerificationReport snapshot() {
        RuntimeHealth.Snapshot runtime = health.snapshot();
        return new BackendVerificationReport(
                backendId,
                runtime.mode().name(),
                storageReady.getAsBoolean(),
                channelConnected.getAsBoolean(),
                integrationChecks(runtime.issues()),
                runtime.issues()
        );
    }

    private Map<String, BackendVerificationReport.Check> integrationChecks(Map<String, String> issues) {
        Map<String, BackendVerificationReport.Check> checks = new LinkedHashMap<>();
        PluginManager plugins = plugin.getServer().getPluginManager();
        plugin.getDescription().getSoftDepend().stream().sorted().forEach(name ->
                checks.put(name, pluginCheck(plugins, issues, name)));
        clientEvidence.issues().forEach((name, issue) -> checks.put(
                name,
                new BackendVerificationReport.Check(
                        BackendVerificationReport.State.WARNING,
                        issue
                )
        ));
        return Map.copyOf(checks);
    }

    private static BackendVerificationReport.Check pluginCheck(
            PluginManager plugins,
            Map<String, String> issues,
            String name
    ) {
        Plugin provider = plugins.getPlugin(name);
        if (provider == null) {
            return new BackendVerificationReport.Check(
                    BackendVerificationReport.State.DISABLED,
                    "optional provider is not installed"
            );
        }
        if (!provider.isEnabled()) {
            return new BackendVerificationReport.Check(
                    BackendVerificationReport.State.WARNING,
                    "provider is installed but disabled"
            );
        }
        String healthKey = HEALTH_KEYS.get(name);
        String issue = healthKey == null ? null : issues.get(healthKey);
        if (issue != null) {
            return new BackendVerificationReport.Check(BackendVerificationReport.State.WARNING, issue);
        }
        return new BackendVerificationReport.Check(
                BackendVerificationReport.State.PASS,
                healthKey == null ? "enabled" : "enabled; Staff API discovery has no published issue"
        );
    }
}
