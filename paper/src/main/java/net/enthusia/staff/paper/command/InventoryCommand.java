package net.enthusia.staff.paper.command;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Supplier;
import net.enthusia.staff.domain.player.PlayerIdentity;
import net.enthusia.staff.domain.ports.PlayerDirectory;
import net.enthusia.staff.paper.inventory.InventoryCoordinator;
import net.kyori.adventure.text.Component;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class InventoryCommand implements CommandExecutor, TabCompleter {
    private static final String PERMISSION = "enthusiastaff.inventory.view";
    private static final Duration SUGGESTION_TTL = Duration.ofSeconds(30);
    private static final int MAX_PREFIX_CACHE = 2_048;

    private final JavaPlugin plugin;
    private final Clock clock;
    private final Supplier<PlayerDirectory> directory;
    private final InventoryCoordinator inventories;
    private final ExecutorService workers;
    private final Map<String, CachedSuggestions> suggestions = new ConcurrentHashMap<>();

    public InventoryCommand(
            JavaPlugin plugin,
            Clock clock,
            Supplier<PlayerDirectory> directory,
            InventoryCoordinator inventories,
            ExecutorService workers
    ) {
        this.plugin = java.util.Objects.requireNonNull(plugin, "plugin");
        this.clock = java.util.Objects.requireNonNull(clock, "clock");
        this.directory = java.util.Objects.requireNonNull(directory, "directory");
        this.inventories = java.util.Objects.requireNonNull(inventories, "inventories");
        this.workers = java.util.Objects.requireNonNull(workers, "workers");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] arguments) {
        if (!CommandPermissionGate.require(sender, PERMISSION, "You do not have permission to inspect inventories.")) {
            return true;
        }
        if (!(sender instanceof Player viewer)) {
            sender.sendMessage("This inventory viewer requires an in-game staff viewer.");
            return true;
        }
        if (arguments.length != 1) {
            viewer.sendMessage(Component.text("Usage: /" + label + " <player|uuid>"));
            return true;
        }
        if (!submit(() -> resolveAndOpen(viewer, arguments[0], label.equalsIgnoreCase("endersee")))) {
            viewer.sendMessage(Component.text("The moderation work queue is full; no inventory was opened."));
        }
        return true;
    }

    private void resolveAndOpen(Player viewer, String targetInput, boolean enderChest) {
        PlayerDirectory loaded = directory.get();
        if (loaded == null) {
            message(viewer, "Player directory storage is not ready; no inventory was opened.");
            return;
        }
        try {
            PlayerIdentity target = loaded.find(targetInput).orElse(null);
            if (target == null) {
                message(viewer, "That player is absent from the authoritative directory.");
                return;
            }
            viewer.getScheduler().execute(plugin, () -> inventories.open(viewer, target, enderChest), null, 1L);
        } catch (RuntimeException exception) {
            plugin.getLogger().log(java.util.logging.Level.SEVERE, "Inventory target lookup failed", exception);
            message(viewer, "The player directory lookup failed; no inventory was opened.");
        }
    }

    @Override
    public List<String> onTabComplete(
            CommandSender sender,
            Command command,
            String alias,
            String[] arguments
    ) {
        if (!CommandPermissionGate.allows(sender::hasPermission, PERMISSION) || arguments.length != 1) {
            return List.of();
        }
        String prefix = arguments[0].toLowerCase(Locale.ROOT);
        if (!prefix.matches("[a-z0-9_]{0,32}")) {
            return List.of();
        }
        CachedSuggestions cached = suggestions.get(prefix);
        Instant now = clock.instant();
        if (cached == null || cached.expiresAt().isBefore(now)) {
            requestSuggestions(prefix, now);
            return cached == null ? onlineSuggestions(prefix) : cached.values();
        }
        return cached.values();
    }

    private void requestSuggestions(String prefix, Instant now) {
        if (suggestions.size() >= MAX_PREFIX_CACHE) {
            suggestions.entrySet().removeIf(entry -> entry.getValue().expiresAt().isBefore(now));
            if (suggestions.size() >= MAX_PREFIX_CACHE) {
                return;
            }
        }
        suggestions.putIfAbsent(prefix, new CachedSuggestions(onlineSuggestions(prefix), now.plusSeconds(2)));
        submit(() -> {
            PlayerDirectory loaded = directory.get();
            if (loaded == null) {
                return;
            }
            try {
                List<String> values = loaded.search(prefix, 50).stream()
                        .map(PlayerIdentity::currentUsername)
                        .flatMap(java.util.Optional::stream)
                        .distinct()
                        .sorted(String.CASE_INSENSITIVE_ORDER)
                        .toList();
                suggestions.put(prefix, new CachedSuggestions(values, clock.instant().plus(SUGGESTION_TTL)));
            } catch (RuntimeException exception) {
                plugin.getLogger().log(java.util.logging.Level.WARNING,
                        "Offline inventory tab-completion refresh failed", exception);
            }
        });
    }

    private List<String> onlineSuggestions(String prefix) {
        return plugin.getServer().getOnlinePlayers().stream()
                .map(Player::getName)
                .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(prefix))
                .sorted(Comparator.naturalOrder())
                .limit(50)
                .toList();
    }

    private boolean submit(Runnable operation) {
        try {
            workers.execute(operation);
            return true;
        } catch (RejectedExecutionException exception) {
            return false;
        }
    }

    private void message(Player player, String body) {
        player.getScheduler().execute(plugin, () -> player.sendMessage(Component.text(body)), null, 1L);
    }

    private record CachedSuggestions(List<String> values, Instant expiresAt) {
        private CachedSuggestions {
            values = List.copyOf(values);
        }
    }
}
