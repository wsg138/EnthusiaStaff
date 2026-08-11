package net.enthusia.staff.paper.command;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Array;
import java.lang.reflect.Proxy;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import net.enthusia.staff.domain.OperationalMode;
import net.kyori.adventure.text.Component;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.Test;

final class FreezeCommandRouteTest {
    @Test
    void registeredUnfreezeCommandCannotBeReclassifiedByItsTypedLabel() {
        List<Component> messages = new ArrayList<>();
        FreezeCommand handler = handler();

        handler.onCommand(sender(messages), command("unfreeze"), "enthusiastaff:freeze", new String[0]);

        assertEquals(List.of(Component.text("Usage: /unfreeze <player> <reason> CONFIRM")), messages);
    }

    @Test
    void registeredFreezeCommandCannotBeReclassifiedByItsTypedLabel() {
        List<Component> messages = new ArrayList<>();
        FreezeCommand handler = handler();

        handler.onCommand(sender(messages), command("freeze"), "unfreeze", new String[0]);

        assertEquals(List.of(Component.text(
                "Usage: /freeze <player> <reason> | /freeze keep <player> <reason> CONFIRM"
        )), messages);
    }

    private static FreezeCommand handler() {
        return new FreezeCommand(
                null,
                Clock.systemUTC(),
                () -> OperationalMode.ACTIVE,
                () -> null,
                () -> null,
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

    private static CommandSender sender(List<Component> messages) {
        return (CommandSender) Proxy.newProxyInstance(
                Thread.currentThread().getContextClassLoader(),
                new Class<?>[]{CommandSender.class},
                (instance, method, arguments) -> switch (method.getName()) {
                    case "hasPermission" -> true;
                    case "sendMessage" -> {
                        if (arguments != null && arguments.length > 0 && arguments[0] instanceof Component component) {
                            messages.add(component);
                        }
                        yield null;
                    }
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
