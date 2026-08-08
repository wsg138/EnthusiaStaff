package net.enthusia.staff.paper.tester;

import java.util.List;
import java.util.Locale;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

final class FakeBaseCommandRouter {
    private static final String CREATE = "create";
    private static final String EXTEND = "extend";
    private static final String CLEAR = "clear";
    private static final String TELEPORT = "teleport";
    private static final String STATUS = "status";
    private static final List<String> ACTIONS = List.of(CREATE, EXTEND, CLEAR, TELEPORT, STATUS);

    private final JavaPlugin plugin;
    private final FakeBaseManager manager;

    FakeBaseCommandRouter(JavaPlugin plugin, FakeBaseManager manager) {
        this.plugin = java.util.Objects.requireNonNull(plugin, "plugin");
        this.manager = java.util.Objects.requireNonNull(manager, "manager");
    }

    boolean handle(Player staff, String[] args) {
        if (!manager.authorized(staff)) {
            staff.sendMessage(Component.text(
                    "Fake-base controls require authorized active staff mode.", NamedTextColor.RED));
            return true;
        }
        if (args.length < 2) {
            return usage(staff);
        }
        String action = args[1].toLowerCase(Locale.ROOT);
        if (STATUS.equals(action)) {
            return status(staff, args);
        }
        return handleTargetAction(staff, args, action);
    }

    private boolean handleTargetAction(Player staff, String[] args, String action) {
        if (args.length != 3) {
            return usage(staff);
        }
        Player target = plugin.getServer().getPlayerExact(args[2]);
        if (target == null || !target.isOnline()) {
            staff.sendMessage(Component.text("That player is not online on this backend.", NamedTextColor.RED));
            return true;
        }
        return dispatch(staff, target, action);
    }

    private boolean dispatch(Player staff, Player target, String action) {
        switch (action) {
            case CREATE -> manager.create(staff, target);
            case EXTEND -> manager.extend(staff, target);
            case CLEAR -> manager.clear(staff, target);
            case TELEPORT -> manager.teleport(staff, target);
            default -> {
                return usage(staff);
            }
        }
        return true;
    }

    List<String> tabComplete(Player staff, String[] args) {
        if (!manager.authorized(staff)) {
            return List.of();
        }
        if (args.length == 2) {
            return CheatTesterCommand.filter(ACTIONS, args[1]);
        }
        if (args.length == 3 && !STATUS.equalsIgnoreCase(args[1])) {
            return CheatTesterCommand.filter(
                    plugin.getServer().getOnlinePlayers().stream().map(Player::getName).toList(), args[2]);
        }
        return List.of();
    }

    private boolean status(Player staff, String[] args) {
        if (args.length != 2) {
            return usage(staff);
        }
        List<String> lines = manager.statusLines(staff);
        if (lines.isEmpty()) {
            staff.sendMessage(Component.text("No controllable fake-base operations are active.", NamedTextColor.GRAY));
            return true;
        }
        staff.sendMessage(Component.text("Active fake-base operations:", NamedTextColor.GOLD));
        for (String line : lines) {
            staff.sendMessage(Component.text("• " + line, NamedTextColor.GRAY));
        }
        return true;
    }

    private static boolean usage(Player staff) {
        staff.sendMessage(Component.text(
                "Usage: /cheattester base <create|extend|clear|teleport|status> [player]", NamedTextColor.YELLOW));
        return true;
    }
}
