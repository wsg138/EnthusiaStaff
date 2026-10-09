package net.enthusia.staff.paper.command;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/** Player argument positions only: reason, case, and draft-only inputs stay delegated. */
public final class PlayerArgumentRoutes {
    private static final String BASE_ACTION = "base";
    private static final String CHEAT_TESTER_COMMAND = "cheattester";
    private static final String PUNISH_COMMAND = "punish";
    private static final String PUNISH_PERMISSION = "enthusiastaff.punish";
    private static final String REMOVE_PERMISSION = "enthusiastaff.remove";
    private static final String INVENTORY_PERMISSION = "enthusiastaff.inventory.view";
    private static final String FREEZE_PERMISSION = "enthusiastaff.freeze";
    private static final int SECOND_ARGUMENT_POSITION = 2;
    private static final int THIRD_ARGUMENT = 3;
    private static final Map<String, String> DIRECT = Map.ofEntries(
            Map.entry(PUNISH_COMMAND, PUNISH_PERMISSION), Map.entry("ban", PUNISH_PERMISSION),
            Map.entry("mute", PUNISH_PERMISSION), Map.entry("warn", PUNISH_PERMISSION),
            Map.entry("kick", PUNISH_PERMISSION), Map.entry("ipban", "enthusiastaff.punish.ip"),
            Map.entry("history", "enthusiastaff.history.view"), Map.entry("client", "enthusiastaff.client"),
            Map.entry("inspect", "enthusiastaff.inspect"), Map.entry("invsee", INVENTORY_PERMISSION),
            Map.entry("endersee", INVENTORY_PERMISSION), Map.entry("freeze", FREEZE_PERMISSION),
            Map.entry("unfreeze", FREEZE_PERMISSION), Map.entry("report", ""),
            Map.entry("removepunishment", REMOVE_PERMISSION), Map.entry("unban", REMOVE_PERMISSION),
            Map.entry("unmute", REMOVE_PERMISSION), Map.entry("unwarn", REMOVE_PERMISSION),
            Map.entry("removewarning", REMOVE_PERMISSION));
    private static final Set<String> FAKE_BASE_TARGETS = Set.of("create", "extend", "clear", "teleport");

    private PlayerArgumentRoutes() { }

    public static boolean supports(String command) {
        return DIRECT.containsKey(command) || Set.of("stafftools", "staffflags", "staff", "staffapi",
                CHEAT_TESTER_COMMAND, "fakebase").contains(command);
    }

    public static Route find(String command, String[] args) {
        String name = command.toLowerCase(Locale.ROOT);
        if (args.length == 0) { return null; }
        String first = args[0].toLowerCase(Locale.ROOT);
        if (args.length == 1 && DIRECT.containsKey(name)) {
            return new Route(DIRECT.get(name), false, rootKeywords(name));
        }
        return nested(name, first, args);
    }

    private static Route nested(String name, String first, String[] args) {
        if (args.length == SECOND_ARGUMENT_POSITION) {
            return secondArgument(name, first);
        }
        if (args.length == THIRD_ARGUMENT && name.equals(CHEAT_TESTER_COMMAND) && first.equals(BASE_ACTION)
                && FAKE_BASE_TARGETS.contains(args[1].toLowerCase(Locale.ROOT))) {
            return new Route("enthusiastaff.cheattester.fake-base", false, List.of());
        }
        return null;
    }

    private static final Map<String, Map<String, Route>> SECOND_ARGUMENT = Map.ofEntries(
            Map.entry("inspect", Map.of(
                    "inventory", route(INVENTORY_PERMISSION),
                    "ender", route(INVENTORY_PERMISSION),
                    "economy", route("enthusiastaff.confiscate.economy"),
                    "items", route("enthusiastaff.confiscate.items"))),
            Map.entry("freeze", Map.of("keep", route(FREEZE_PERMISSION),
                    "status", route(FREEZE_PERMISSION))),
            Map.entry(PUNISH_COMMAND, Map.of("resume", route(PUNISH_PERMISSION),
                    "confirm", route(PUNISH_PERMISSION))),
            Map.entry("stafftools", Map.of("follow", route("enthusiastaff.stafftools.spectate"),
                    "spectate", route("enthusiastaff.stafftools.spectate"))),
            Map.entry("staffflags", Map.of("list", route("enthusiastaff.investigation.view"),
                    "add", route("enthusiastaff.investigation.edit"))),
            Map.entry("staff", Map.of("recover", new Route("enthusiastaff.staffmode", true, List.of()))),
            Map.entry("staffapi", Map.of(PUNISH_COMMAND, new Route("", true, List.of()))),
            Map.entry(CHEAT_TESTER_COMMAND, Map.of("run", route("enthusiastaff.cheattester"),
                    "cancel", route("enthusiastaff.cheattester"))));

    private static Route route(String permission) {
        return new Route(permission, false, List.of());
    }

    private static Route secondArgument(String name, String first) {
        if (name.equals("fakebase") && FAKE_BASE_TARGETS.contains(first)) {
            return route("enthusiastaff.cheattester.fake-base");
        }
        return SECOND_ARGUMENT.getOrDefault(name, Map.of()).get(first);
    }

    private static List<String> rootKeywords(String name) {
        return switch (name) {
            case "inspect" -> List.of("inventory", "ender", "economy", "items");
            case "freeze" -> List.of("keep", "list", "status");
            case PUNISH_COMMAND -> List.of("confirm", "resume", "requests", "review", "approve", "deny");
            default -> List.of();
        };
    }

    public record Route(String permission, boolean consoleOnly, List<String> keywords) {
        public boolean allowed(Predicate<String> permissions, boolean console) {
            return (!consoleOnly || console) && (permission.isEmpty() || console || permissions.test(permission));
        }
    }
}
