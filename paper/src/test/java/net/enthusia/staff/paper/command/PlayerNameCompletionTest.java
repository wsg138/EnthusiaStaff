package net.enthusia.staff.paper.command;

import static org.junit.jupiter.api.Assertions.*;
import java.lang.reflect.Proxy;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.IntStream;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

// Arrays deliberately exercise each argument position; proxy creation uses the thread context class loader.
@SuppressWarnings({"PMD.AvoidInstantiatingObjectsInLoops"})
class PlayerNameCompletionTest {
    private static final String REPORT_NAME = "report";
    private static final String PUNISH_NAME = "punish";
    private static final String HISTORY_NAME = "history";
    private static final String INSPECT_NAME = "inspect";
    private static final String CHEATTESTER_NAME = "cheattester";
    private static final String FAKEBASE_NAME = "fakebase";
    private static final String HIDDEN_NAME = "Hidden";
    private static final String ALICE_NAME = "Alice";
    @Test void allDirectPlayerCommandsHaveRoutesAndDoNotCompleteReasons() {
        for (String name : List.of(PUNISH_NAME, "ban", "mute", "warn", "kick", "ipban", HISTORY_NAME, "client",
                INSPECT_NAME, "invsee", "endersee", "freeze", "unfreeze", REPORT_NAME, "removepunishment",
                "unban", "unmute", "unwarn", "removewarning")) {
            assertNotNull(PlayerArgumentRoutes.find(name, new String[]{"P"}), name);
            assertNull(PlayerArgumentRoutes.find(name, new String[]{"Player", "reason"}), name);
        }
    }

    @Test void nestedPlayerPositionsAreRecognizedAndNonPlayerPositionsStayDelegated() {
        for (String[] route : List.of(new String[]{INSPECT_NAME, "inventory"}, new String[]{INSPECT_NAME, "ender"},
                new String[]{INSPECT_NAME, "economy"}, new String[]{INSPECT_NAME, "items"},
                new String[]{"freeze", "keep"}, new String[]{"freeze", "status"},
                new String[]{PUNISH_NAME, "resume"}, new String[]{"stafftools", "follow"},
                new String[]{"stafftools", "spectate"}, new String[]{"staffflags", "list"},
                new String[]{"staffflags", "add"}, new String[]{"staff", "recover"},
                new String[]{"staffapi", PUNISH_NAME}, new String[]{CHEATTESTER_NAME, "run"},
                new String[]{CHEATTESTER_NAME, "cancel"}, new String[]{FAKEBASE_NAME, "create"},
                new String[]{FAKEBASE_NAME, "extend"}, new String[]{FAKEBASE_NAME, "clear"},
                new String[]{FAKEBASE_NAME, "teleport"})) {
            assertNotNull(PlayerArgumentRoutes.find(route[0], new String[]{route[1], ""}));
        }
        assertNotNull(PlayerArgumentRoutes.find(CHEATTESTER_NAME, new String[]{"base", "create", ""}));
        assertNull(PlayerArgumentRoutes.find(CHEATTESTER_NAME, new String[]{"base", "status", ""}));
        assertNull(PlayerArgumentRoutes.find("staffflags", new String[]{"resolve", ""}));
        assertNull(PlayerArgumentRoutes.find(PUNISH_NAME, new String[]{"approve", ""}));
        assertNull(PlayerArgumentRoutes.find("case", new String[]{""}));
    }

    @Test void permissionRevocationAndHiddenPlayersApplyToEveryCompletion() {
        UUID hidden = UUID.randomUUID();
        Set<String> permissions = new HashSet<>(Set.of("enthusiastaff.history.view"));
        Player viewer = player(UUID.randomUUID(), "Viewer", permissions);
        var completion = new PlayerNameCompletion((v, target) -> !hidden.equals(target), null);
        completion.remember(player(hidden, HIDDEN_NAME, Set.of()));
        completion.remember(player(UUID.randomUUID(), ALICE_NAME, Set.of()));
        assertEquals(List.of(ALICE_NAME), completion.complete(viewer, command(HISTORY_NAME), HISTORY_NAME,
                new String[]{"a"}, null));
        assertEquals(List.of(ALICE_NAME), completion.complete(viewer, command(HISTORY_NAME), HISTORY_NAME,
                new String[]{""}, (s, c, a, args) -> List.of(HIDDEN_NAME)));
        permissions.clear();
        assertEquals(List.of(), completion.complete(viewer, command(HISTORY_NAME), HISTORY_NAME,
                new String[]{""}, null));
    }

    @Test void publicReportsAndPermissionFilteredInspectorSubcommandsRemainUsable() {
        Player viewer = player(UUID.randomUUID(), "Viewer", Set.of("enthusiastaff.inspect"));
        var completion = new PlayerNameCompletion((v, target) -> true, null);
        completion.remember(player(UUID.randomUUID(), ALICE_NAME, Set.of()));
        assertEquals(List.of(ALICE_NAME), completion.complete(viewer, command(REPORT_NAME), REPORT_NAME,
                new String[]{"A"}, null));
        assertEquals(List.of(ALICE_NAME), completion.complete(viewer, command(INSPECT_NAME), INSPECT_NAME,
                new String[]{""}, null));
        assertEquals(List.of("spam"), completion.complete(viewer, command(REPORT_NAME), REPORT_NAME,
                new String[]{ALICE_NAME, "s"}, (s, c, a, args) -> List.of("spam")));
    }

    @Test void inventoryOfflineCacheSurvivesButHiddenNamesAreFiltered() {
        UUID hidden = UUID.randomUUID();
        Player viewer = player(UUID.randomUUID(), "Viewer", Set.of("enthusiastaff.inventory.view"));
        var completion = new PlayerNameCompletion((v, target) -> !target.equals(hidden), null);
        completion.remember(player(hidden, HIDDEN_NAME, Set.of()));
        assertEquals(List.of("Offline"), completion.complete(viewer, command("invsee"), "invsee",
                new String[]{""}, (s, c, a, args) -> List.of(HIDDEN_NAME, "Offline")));
    }

    @Test void consoleOnlyRoutesAndBoundedCaseInsensitiveMatchesAreEnforced() {
        assertFalse(PlayerArgumentRoutes.find("staffapi", new String[]{PUNISH_NAME, ""})
                .allowed(ignored -> true, false));
        assertTrue(PlayerArgumentRoutes.find("staff", new String[]{"recover", ""})
                .allowed(ignored -> false, true));
        var names = IntStream.range(0, 100).mapToObj(i -> "Player%03d".formatted(i)).toList();
        assertEquals(50, PlayerNameCompletion.matches(names, "pL").size());
        assertEquals(List.of(ALICE_NAME), PlayerNameCompletion.matches(List.of(ALICE_NAME, ALICE_NAME, "Bob"), "a"));
    }

    @Test void ownerNameConfirmationRemainsDiscoverable() {
        Player viewer = player(UUID.randomUUID(), "Viewer", Set.of("enthusiastaff.punish"));
        var completion = new PlayerNameCompletion((v, target) -> true, null);
        assertEquals(List.of("confirm"), completion.complete(viewer, command(PUNISH_NAME), PUNISH_NAME,
                new String[]{"conf"}, (s, c, a, args) -> List.of("confirm")));
    }

    @Test void ownerConfirmationNamesUseVisibilityAndPermissionFilters() {
        UUID hidden = UUID.randomUUID();
        Set<String> permissions = new HashSet<>(Set.of("enthusiastaff.punish"));
        Player viewer = player(UUID.randomUUID(), "Viewer", permissions);
        var completion = new PlayerNameCompletion((v, target) -> !hidden.equals(target), null);
        completion.remember(player(hidden, HIDDEN_NAME, Set.of()));
        completion.remember(player(UUID.randomUUID(), ALICE_NAME, Set.of()));
        assertEquals(List.of(ALICE_NAME), completion.complete(viewer, command(PUNISH_NAME), PUNISH_NAME,
                new String[]{"confirm", ""}, (s, c, a, args) -> List.of(HIDDEN_NAME, ALICE_NAME)));
        permissions.clear();
        assertEquals(List.of(), completion.complete(viewer, command(PUNISH_NAME), PUNISH_NAME,
                new String[]{"confirm", ""}, (s, c, a, args) -> List.of(ALICE_NAME)));
    }

    private static Command command(String name) {
        return new Command(name) {
            @Override public boolean execute(CommandSender sender, String label, String[] args) { return false; }
        };
    }

    private static Player player(UUID id, String name, Set<String> permissions) {
        return (Player) Proxy.newProxyInstance(Thread.currentThread().getContextClassLoader(), new Class<?>[]{Player.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getUniqueId" -> id;
                    case "getName" -> name;
                    case "hasPermission" -> permissions.contains(args[0]);
                    case "isOnline" -> true;
                    default -> null;
                });
    }
}
