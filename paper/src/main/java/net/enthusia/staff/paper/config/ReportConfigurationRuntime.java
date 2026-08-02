package net.enthusia.staff.paper.config;

import java.nio.file.Path;
import java.util.Objects;
import net.enthusia.staff.domain.report.ReportPolicyRuntime;
import net.enthusia.staff.paper.config.reload.ConfigurationReloadAction;
import net.enthusia.staff.paper.config.reload.ReportConfigurationReloadAction;
import org.bukkit.plugin.java.JavaPlugin;

public final class ReportConfigurationRuntime {
    private static AtomicReportConfiguration active;

    private ReportConfigurationRuntime() {
    }

    public static synchronized ConfigurationReloadAction initialize(
            JavaPlugin plugin,
            ConfigurationReloadAction delegate
    ) {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(delegate, "delegate");
        if (active == null) {
            plugin.saveResource("reports.yml", false);
            plugin.saveResource("gui/reports.yml", false);
            ReportConfigurationLoader loader = new ReportConfigurationLoader();
            try {
                active = new AtomicReportConfiguration(loader.load(policyFile(plugin), guiFile(plugin)));
            } catch (ConfigurationValidationException exception) {
                plugin.getLogger().severe("EnthusiaStaff report configuration is invalid; startup cannot continue");
                plugin.getLogger().severe("Report configuration error: " + sanitized(exception.getMessage()));
                throw exception;
            }
            ReportPolicyRuntime.install(() -> snapshot().policy());
            plugin.getLogger().info(
                    "Loaded report configuration policy " + snapshot().policyVersion()
                            + " and GUI " + snapshot().guiVersion()
            );
        }
        ReportConfigurationLoader loader = new ReportConfigurationLoader();
        return new ReportConfigurationReloadAction(
                delegate,
                () -> loader.load(policyFile(plugin), guiFile(plugin)),
                active,
                details -> {
                    plugin.getLogger().warning("EnthusiaStaff report configuration reload was rejected");
                    details.forEach(detail -> plugin.getLogger().warning("Reload detail: " + detail));
                }
        );
    }

    public static synchronized ReportConfigurationSnapshot snapshot() {
        if (active == null) {
            throw new IllegalStateException("report configuration runtime has not been initialized");
        }
        return active.snapshot();
    }

    private static Path policyFile(JavaPlugin plugin) {
        return plugin.getDataFolder().toPath().toAbsolutePath().normalize().resolve("reports.yml");
    }

    private static Path guiFile(JavaPlugin plugin) {
        return plugin.getDataFolder().toPath().toAbsolutePath().normalize().resolve("gui/reports.yml");
    }

    private static String sanitized(String message) {
        return message == null || message.isBlank() ? "Report configuration is invalid" : message;
    }
}
