package net.enthusia.staff.paper.command;

import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import net.enthusia.staff.paper.RuntimeHealth;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

/** Produces a compact, non-destructive local Paper runtime diagnostic. */
final class FullRuntimeVerifier {
    private static final String ESTAFF_COMMAND = "estaff";
    private static final String DETAIL_SEPARATOR = " — ";
    private static final int MAX_ISSUES_SHOWN = 5;
    private static final String RESET = "§r";
    private static final String RED = "§c";
    private static final String GREEN = "§a";
    private static final String YELLOW = "§e";
    private static final String GOLD = "§6";
    private static final String AQUA = "§b";
    private static final String WHITE = "§f";
    private static final String GRAY = "§7";
    private static final String DARK_GRAY = "§8";
    private static final String BOLD = "§l";
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

    List<String> verify() {
        RuntimeHealth.Snapshot snapshot = health.snapshot();
        List<String> lines = new ArrayList<>();
        lines.add(header("EnthusiaStaff • Local Verify"));
        lines.add(label("Mode", modeColor(snapshot.mode().name()) + snapshot.mode().name()));
        appendCore(lines);
        appendProviders(lines, snapshot.issues());
        appendIssues(lines, snapshot.issues());
        boolean blocked = isBlocked(snapshot.issues());
        lines.add(separator());
        lines.add(blocked ? RED + "✖ LOCAL BACKEND HAS BLOCKERS" : GREEN + "✔ LOCAL BACKEND HEALTHY");
        lines.add(DARK_GRAY + "Network-wide: run " + AQUA + "/estaff verify full" + DARK_GRAY + " on Velocity.");
        return List.copyOf(lines);
    }

    private void appendCore(List<String> lines) {
        lines.add(section("Core"));
        lines.add(check(storagePublished.getAsBoolean(), "Storage", "connected", "not published"));
        lines.add(check(commandRegistered(), "Command", "/estaff owned", "/estaff ownership is wrong"));
        lines.add(check(artifactReadable(), "Artifact", "JAR readable", "loaded JAR cannot be read"));
    }

    private void appendProviders(List<String> lines, Map<String, String> issues) {
        lines.add(section("Provider APIs"));
        PluginManager manager = plugin.getServer().getPluginManager();
        PROVIDERS.forEach((provider, issueKey) -> lines.add(providerLine(manager, issues, provider, issueKey)));
        lines.add(clientProviderSummary(manager));
    }

    private String providerLine(
            PluginManager manager,
            Map<String, String> issues,
            String providerName,
            String issueKey
    ) {
        Plugin provider = manager.getPlugin(providerName);
        if (provider == null) {
            return disabled(providerName, "not installed");
        }
        if (!provider.isEnabled()) {
            return warning(providerName, "installed but disabled");
        }
        String issue = issues.get(issueKey);
        return issue == null ? pass(providerName, "enabled / API healthy") : warning(providerName, shortText(issue));
    }

    private String clientProviderSummary(PluginManager manager) {
        List<String> names = List.of("ViaVersion", "floodgate", "Geyser-Spigot", "EnthusiaServerAutoClicker");
        long enabled = names.stream().filter(manager::isPluginEnabled).count();
        return GRAY + "  • Client APIs: " + WHITE + enabled + "/" + names.size()
                + DARK_GRAY + " optional providers enabled";
    }

    private void appendIssues(List<String> lines, Map<String, String> issues) {
        if (issues.isEmpty()) {
            return;
        }
        lines.add(section("Active Issues"));
        issues.entrySet().stream().limit(MAX_ISSUES_SHOWN).forEach(issue ->
                lines.add(warning(issue.getKey(), shortText(issue.getValue()))));
        int hidden = issues.size() - MAX_ISSUES_SHOWN;
        if (hidden > 0) {
            lines.add(DARK_GRAY + "  … " + hidden + " more; see sanitized server log.");
        }
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

    private static String check(boolean passed, String label, String passDetail, String failDetail) {
        return passed ? pass(label, passDetail) : critical(label, failDetail);
    }

    private static String header(String title) {
        return DARK_GRAY + "──────── " + AQUA + BOLD + title + RESET + DARK_GRAY + " ────────";
    }

    private static String separator() {
        return DARK_GRAY + "────────────────────────";
    }

    private static String section(String title) {
        return GOLD + "▸ " + YELLOW + BOLD + title;
    }

    private static String label(String label, String value) {
        return GRAY + label + ": " + value;
    }

    private static String pass(String label, String detail) {
        return GREEN + "  ✔ " + WHITE + label + DARK_GRAY + DETAIL_SEPARATOR + GRAY + detail;
    }

    private static String warning(String label, String detail) {
        return YELLOW + "  ⚠ " + WHITE + label + DARK_GRAY + DETAIL_SEPARATOR + GRAY + detail;
    }

    private static String disabled(String label, String detail) {
        return DARK_GRAY + "  ○ " + GRAY + label + DETAIL_SEPARATOR + detail;
    }

    private static String critical(String label, String detail) {
        return RED + "  ✖ " + WHITE + label + DARK_GRAY + DETAIL_SEPARATOR + RED + detail;
    }

    private static String modeColor(String mode) {
        return switch (mode) {
            case "ACTIVE" -> GREEN;
            case "SHADOW_MIGRATION" -> YELLOW;
            case "DEGRADED", "READ_ONLY_FAILURE" -> RED;
            default -> GOLD;
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
