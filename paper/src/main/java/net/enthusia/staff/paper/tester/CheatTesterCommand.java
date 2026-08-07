package net.enthusia.staff.paper.tester;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
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
    private static final int NO_ARGUMENTS = 0;
    private static final int ACTION_ARGUMENT = 1;
    private static final int TARGET_ARGUMENT = 2;
    private static final int TYPE_ARGUMENT = 3;
    private static final String PERMISSION = "enthusiastaff.cheattester";
    private static final String CANCEL_ANY_PERMISSION = "enthusiastaff.cheattester.cancel-any";
    private static final String SELECT = "select";
    private static final String RUN = "run";
    private static final String CANCEL = "cancel";
    private static final String STATUS = "status";
    private static final String CONFIG = "config";
    private static final List<String> ROOT_COMPLETIONS = List.of(SELECT, RUN, CANCEL, STATUS, CONFIG);
    private static final List<String> TYPE_COMPLETIONS =
            Arrays.stream(CheatTesterType.values()).map(CheatTesterType::id).toList();

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
        if (args.length == NO_ARGUMENTS || CONFIG.equalsIgnoreCase(args[0])) {
            manager.showConfiguration(player);
            return true;
        }
        return dispatch(player, args);
    }

    private boolean dispatch(Player player, String[] args) {
        return switch (args[0].toLowerCase(Locale.ROOT)) {
            case SELECT -> select(player, args);
            case RUN -> run(player, args);
            case CANCEL -> cancel(player, args);
            case STATUS -> status(player);
            default -> usage(player);
        };
    }

    private boolean usage(Player player) {
        player.sendMessage(Component.text(
                "Usage: /cheattester <select|run|cancel|status|config>",
                NamedTextColor.YELLOW
        ));
        return true;
    }

    private boolean select(Player player, String[] args) {
        if (args.length != TARGET_ARGUMENT) {
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
        if (args.length < TARGET_ARGUMENT || args.length > TYPE_ARGUMENT) {
            player.sendMessage(Component.text("Usage: /cheattester run <player> [type]"));
            return true;
        }
        Player target = onlineTarget(player, args[1]);
        if (target == null) {
            return true;
        }
        if (args.length == TARGET_ARGUMENT) {
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
        if (args.length != TARGET_ARGUMENT) {
            player.sendMessage(Component.text("Usage: /cheattester cancel <player>"));
            return true;
        }
        Player target = onlineTarget(player, args[1]);
        if (target != null) {
            manager.cancel(player, target.getUniqueId());
        }
        return true;
    }

    private Player onlineTarget(Player player, String name) {
        Player target = plugin.getServer().getPlayerExact(name);
        if (target == null) {
            player.sendMessage(Component.text("That player is not online on this backend.", NamedTextColor.RED));
        }
        return target;
    }

    private boolean status(Player player) {
        if (!player.hasPermission(PERMISSION)) {
            player.sendMessage(Component.text("You do not have permission to use Cheat Tester.", NamedTextColor.RED));
            return true;
        }
        boolean includeAll = player.hasPermission(CANCEL_ANY_PERMISSION);
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
        if (!(sender instanceof Player player) || !player.hasPermission(PERMISSION)) {
            return List.of();
        }
        return switch (args.length) {
            case ACTION_ARGUMENT -> filter(ROOT_COMPLETIONS, args[0]);
            case TARGET_ARGUMENT -> secondArgumentCompletions(args);
            case TYPE_ARGUMENT -> thirdArgumentCompletions(args);
            default -> List.of();
        };
    }

    private List<String> secondArgumentCompletions(String[] args) {
        if (SELECT.equalsIgnoreCase(args[0])) {
            return filter(TYPE_COMPLETIONS, args[1]);
        }
        if (RUN.equalsIgnoreCase(args[0]) || CANCEL.equalsIgnoreCase(args[0])) {
            return filter(plugin.getServer().getOnlinePlayers().stream().map(Player::getName).toList(), args[1]);
        }
        return List.of();
    }

    private static List<String> thirdArgumentCompletions(String[] args) {
        return RUN.equalsIgnoreCase(args[0]) ? filter(TYPE_COMPLETIONS, args[2]) : List.of();
    }

    static List<String> filter(List<String> values, String prefix) {
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
