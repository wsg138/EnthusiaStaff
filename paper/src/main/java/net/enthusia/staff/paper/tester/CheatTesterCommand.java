package net.enthusia.staff.paper.tester;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import net.enthusia.staff.domain.tester.CheatTesterType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class CheatTesterCommand implements CommandExecutor, TabCompleter {
    private final JavaPlugin plugin;
    private final CheatTesterManager manager;

    public CheatTesterCommand(JavaPlugin plugin, CheatTesterManager manager) {
        this.plugin = java.util.Objects.requireNonNull(plugin, "plugin");
        this.manager = java.util.Objects.requireNonNull(manager, "manager");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Cheat Tester requires an in-game staff session.");
            return true;
        }
        if (args.length == 0 || "config".equalsIgnoreCase(args[0])) {
            manager.showConfiguration(player);
            return true;
        }
        return switch (args[0].toLowerCase(Locale.ROOT)) {
            case "select" -> select(player, args);
            case "run" -> run(player, args);
            case "cancel" -> cancel(player, args);
            case "status" -> status(player);
            default -> {
                player.sendMessage(Component.text(
                        "Usage: /cheattester <select|run|cancel|status|config>",
                        NamedTextColor.YELLOW
                ));
                yield true;
            }
        };
    }

    private boolean select(Player player, String[] args) {
        if (args.length != 2) {
            player.sendMessage(Component.text("Usage: /cheattester select <totem-refill|no-fall|velocity|auto-armor|fake-entity>"));
            return true;
        }
        CheatTesterType.fromId(args[1]).ifPresentOrElse(
                type -> manager.select(player, type),
                () -> player.sendMessage(Component.text("Unknown Cheat Tester type.", NamedTextColor.RED))
        );
        return true;
    }

    private boolean run(Player player, String[] args) {
        if (args.length < 2 || args.length > 3) {
            player.sendMessage(Component.text("Usage: /cheattester run <player> [type]"));
            return true;
        }
        Player target = plugin.getServer().getPlayerExact(args[1]);
        if (target == null) {
            player.sendMessage(Component.text("That player is not online on this backend.", NamedTextColor.RED));
            return true;
        }
        if (args.length == 2) {
            manager.runSelected(player, target);
            return true;
        }
        CheatTesterType.fromId(args[2]).ifPresentOrElse(
                type -> manager.run(player, target, type),
                () -> player.sendMessage(Component.text("Unknown Cheat Tester type.", NamedTextColor.RED))
        );
        return true;
    }

    private boolean cancel(Player player, String[] args) {
        if (args.length != 2) {
            player.sendMessage(Component.text("Usage: /cheattester cancel <player>"));
            return true;
        }
        Player target = plugin.getServer().getPlayerExact(args[1]);
        if (target == null) {
            player.sendMessage(Component.text("That player is not online on this backend.", NamedTextColor.RED));
            return true;
        }
        manager.cancel(player, target.getUniqueId());
        return true;
    }

    private boolean status(Player player) {
        boolean includeAll = player.hasPermission("enthusiastaff.cheattester.cancel-any");
        List<String> lines = manager.statusLines(player.getUniqueId(), includeAll);
        if (lines.isEmpty()) {
            player.sendMessage(Component.text("No matching Cheat Tester sessions are active."));
            return true;
        }
        player.sendMessage(Component.text("Active Cheat Tester sessions:", NamedTextColor.GOLD));
        for (String line : lines) {
            player.sendMessage(Component.text("• " + line, NamedTextColor.GRAY));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player player) || !player.hasPermission("enthusiastaff.cheattester")) {
            return List.of();
        }
        if (args.length == 1) {
            return filter(List.of("select", "run", "cancel", "status", "config"), args[0]);
        }
        if (args.length == 2 && "select".equalsIgnoreCase(args[0])) {
            return filter(Arrays.stream(CheatTesterType.values()).map(CheatTesterType::id).toList(), args[1]);
        }
        if (args.length == 2 && ("run".equalsIgnoreCase(args[0]) || "cancel".equalsIgnoreCase(args[0]))) {
            return filter(plugin.getServer().getOnlinePlayers().stream().map(Player::getName).toList(), args[1]);
        }
        if (args.length == 3 && "run".equalsIgnoreCase(args[0])) {
            return filter(Arrays.stream(CheatTesterType.values()).map(CheatTesterType::id).toList(), args[2]);
        }
        return List.of();
    }

    private static List<String> filter(List<String> values, String prefix) {
        String normalized = prefix == null ? "" : prefix.toLowerCase(Locale.ROOT);
        List<String> matches = new ArrayList<>();
        for (String value : values) {
            if (value.toLowerCase(Locale.ROOT).startsWith(normalized)) {
                matches.add(value);
            }
        }
        return List.copyOf(matches);
    }
}
