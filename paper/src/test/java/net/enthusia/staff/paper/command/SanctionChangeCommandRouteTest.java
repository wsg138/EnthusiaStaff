package net.enthusia.staff.paper.command;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Array;
import java.lang.reflect.Proxy;
import java.util.List;
import net.enthusia.staff.domain.OperationalMode;
import net.enthusia.staff.domain.auth.DefaultAuthorizationPolicy;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.Test;

final class SanctionChangeCommandRouteTest {
    @Test
    void registeredCentralCommandKeepsCentralCompletionWhenAliasIsMisleading() {
        SanctionChangeCommand handler = handler();

        List<String> completions = handler.onTabComplete(
                sender(), command("removepunishment"), "enthusiastaff:unwarn", new String[]{"target", ""}
        );

        assertFalse(completions.isEmpty());
    }

    @Test
    void aliasCannotTurnRegisteredShortcutIntoCentralCommand() {
        SanctionChangeCommand handler = handler();

        List<String> completions = handler.onTabComplete(
                sender(), command("unwarn"), "removepunishment", new String[]{"target", ""}
        );

        assertTrue(completions.isEmpty());
    }

    private static SanctionChangeCommand handler() {
        return new SanctionChangeCommand(
                null,
                () -> OperationalMode.ACTIVE,
                () -> null,
                () -> null,
                () -> null,
                new DefaultAuthorizationPolicy(),
                null,
                null
        );
    }

    private static Command command(String name) {
        return new Command(name) {
            @Override
            public boolean execute(CommandSender sender, String label, String[] arguments) {
                return true;
            }
        };
    }

    private static CommandSender sender() {
        return (CommandSender) Proxy.newProxyInstance(
                Thread.currentThread().getContextClassLoader(),
                new Class<?>[]{CommandSender.class},
                (instance, method, arguments) -> switch (method.getName()) {
                    case "getName" -> "Console";
                    case "hasPermission" -> true;
                    default -> defaultValue(method.getReturnType());
                }
        );
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive() || type == void.class) {
            return null;
        }
        return Array.get(Array.newInstance(type, 1), 0);
    }
}
