package net.enthusia.staff.paper.command;

import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import net.enthusia.staff.domain.OperationalMode;
import net.enthusia.staff.paper.RuntimeHealth;
import net.enthusia.staff.paper.presentation.StaffMessageStyle;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

/** Produces a compact, non-destructive local Paper runtime diagnostic. */
final class FullRuntimeVerifier {
    private static final String ESTAFF_COMMAND = "estaff";
    private static final int MAX_ISSUES_SHOWN = 5;
    private static final Map<String, String> PROVIDERS = Map.of(
            "RoseChat", "rosechat",
            "EnthusiaCurrency", "currency",
            "EnthusiaMarket", "market",
            "EnthusiaCommend", "reputation",
            "CombatLogX", "combatlogx"
    );

    private final JavaPlugin plugin;
    private final RuntimeHealth health;
    private final BooleanSupplier storagePublished;

    FullRuntimeVerifier(JavaPlugin plugin, RuntimeHealth health, BooleanSupplier storagePublished) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.health = Objects.requireNonNull(health, "health");
        this.storagePublished = Objects.requireNonNull(storagePublished, "storagePublished");
    }

    List<Component> verify() {
        RuntimeHealth.Snapshot snapshot = health.snapshot();
        List<Component> lines = new ArrayList<>();
        lines.add(StaffMessageStyle.header("EnthusiaStaff • Local Verify"));
        lines.add(StaffMessageStyle.statusRow(
                "Mode",
                StaffMessageStyle.displayMode(snapshot.mode()),
                "local Paper runtime",
                modeTone(snapshot.mode())
        ));
        appendCore(lines);
        appendProviders(lines, snapshot.issues());
        appendIssues(lines, snapshot);
        appendConclusion(lines, snapshot.issues());
        return List.copyOf(lines);
    }

    private void appendCore(List<Component> lines) {
        lines.add(StaffMessageStyle.section("Core"));
        lines.add(check(storagePublished.getAsBoolean(), "Storage", "Connected", "Not ready"));
        lines.add(check(commandRegistered(), "Command", "Owned", "/estaff ownership is wrong"));
        lines.add(check(artifactReadable(), "Artifact", "Readable", "Loaded JAR cannot be read"));
    }

    private void appendProviders(List<Component> lines, Map<String, String> issues) {
        lines.add(StaffMessageStyle.section("Provider APIs"));
        PluginManager manager = plugin.getServer().getPluginManager();
        PROVIDERS.forEach((provider, issueKey) -> lines.add(providerLine(manager, issues, provider, issueKey)));
        lines.add(clientProviderSummary(manager));
    }

    private Component providerLine(
            PluginManager manager,
            Map<String, String> issues,
            String providerName,
            String issueKey
    ) {
        Plugin provider = manager.getPlugin(providerName);
        if (provider == null) {
            return disabled(providerName, "Not installed");
        }
        if (!provider.isEnabled()) {
            return warning(providerName, "Disabled", "Installed but disabled");
        }
        String issue = issues.get(issueKey);
        return issue == null
                ? pass(providerName, "Healthy", "Enabled / API healthy")
                : warning(providerName, "Warning", shortText(issue));
    }

    private Component clientProviderSummary(PluginManager manager) {
        List<String> names = List.of("ViaVersion", "floodgate", "Geyser-Spigot", "EnthusiaServerAutoClicker");
        long enabled = names.stream().filter(manager::isPluginEnabled).count();
        return StaffMessageStyle.statusRow(
                "Client APIs",
                enabled + "/" + names.size(),
                "optional providers enabled",
                StaffMessageStyle.Tone.MUTED
        );
    }

    private void appendIssues(List<Component> lines, RuntimeHealth.Snapshot snapshot) {
        if (snapshot.issues().isEmpty()) {
            return;
        }
        lines.add(StaffMessageStyle.section("Active Issues"));
        snapshot.issues().entrySet().stream().limit(MAX_ISSUES_SHOWN).forEach(issue -> lines.add(
                StaffMessageStyle.statusRow(
                        issue.getKey(),
                        "Disabled",
                        shortText(issue.getValue()),
                        StaffMessageStyle.issueTone(issue.getKey(), issue.getValue(), snapshot.mode())
                )
        ));
        int hidden = snapshot.issues().size() - MAX_ISSUES_SHOWN;
        if (hidden > 0) {
            lines.add(Component.text("  … " + hidden + " more; see sanitized server log.", NamedTextColor.DARK_GRAY));
        }
    }

    private void appendConclusion(List<Component> lines, Map<String, String> issues) {
        boolean blocked = isBlocked(issues);
        lines.add(Component.text("────────────────────────", NamedTextColor.DARK_GRAY));
        lines.add(blocked
                ? StaffMessageStyle.error("✖ LOCAL BACKEND HAS BLOCKERS")
                : StaffMessageStyle.success("✔ LOCAL BACKEND HEALTHY"));
        lines.add(Component.text("Network-wide: run ", NamedTextColor.DARK_GRAY)
                .append(StaffMessageStyle.command("/estaff verify full"))
                .append(Component.text(" on Velocity.", NamedTextColor.DARK_GRAY)));
    }

    private boolean commandRegistered() {
        PluginCommand command = plugin.getCommand(ESTAFF_COMMAND);
        return command != null
                && command.getExecutor() instanceof EstaffCommand
                && command.getTabCompleter() instanceof EstaffCommand;
    }

    private boolean artifactReadable() {
        try {
            Path artifact = Path.of(plugin.getClass().getProtectionDomain().getCodeSource().getLocation().toURI());
            return Files.isRegularFile(artifact) && Files.isReadable(artifact);
        } catch (URISyntaxException | RuntimeException exception) {
            return false;
        }
    }

    private boolean isBlocked(Map<String, String> issues) {
        if (!storagePublished.getAsBoolean() || !commandRegistered() || !artifactReadable()) {
            return true;
        }
        return issues.keySet().stream().anyMatch(key ->
                key.equals("mariadb") || key.equals("channel") || key.equals("configuration")
                        || key.equals("operational-state") || key.equals("cutover"));
    }

    private static Component check(boolean passed, String label, String passDetail, String failDetail) {
        return passed ? pass(label, passDetail, "") : critical(label, failDetail);
    }

    private static Component pass(String label, String status, String detail) {
        return StaffMessageStyle.statusRow(label, status, detail, StaffMessageStyle.Tone.SUCCESS);
    }

    private static Component warning(String label, String status, String detail) {
        return StaffMessageStyle.statusRow(label, status, detail, StaffMessageStyle.Tone.WARNING);
    }

    private static Component disabled(String label, String detail) {
        return StaffMessageStyle.statusRow(label, "Optional", detail, StaffMessageStyle.Tone.MUTED);
    }

    private static Component critical(String label, String detail) {
        return StaffMessageStyle.statusRow(label, "Failed", detail, StaffMessageStyle.Tone.ERROR);
    }

    private static StaffMessageStyle.Tone modeTone(OperationalMode mode) {
        return switch (mode) {
            case ACTIVE -> StaffMessageStyle.Tone.SUCCESS;
            case SHADOW_MIGRATION -> StaffMessageStyle.Tone.WARNING;
            case DEGRADED, READ_ONLY_FAILURE -> StaffMessageStyle.Tone.ERROR;
            default -> StaffMessageStyle.Tone.WARNING;
        };
    }

    private static String shortText(String value) {
        if (value == null || value.isBlank()) {
            return "no detail";
        }
        String singleLine = value.replace('\n', ' ').trim();
        return singleLine.length() <= 96 ? singleLine : singleLine.substring(0, 93) + "...";
    }
}
