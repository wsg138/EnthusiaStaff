package net.enthusia.staff.paper.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicInteger;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.junit.jupiter.api.Test;

class EstaffCommandPermissionTest {
    private static final String STATUS_PERMISSION = "enthusiastaff.status";

    @Test
    void localConsoleIsAllowedWithoutAnExplicitPermissionGrant() {
        AtomicInteger messages = new AtomicInteger();
        CommandSender console = sender(ConsoleCommandSender.class, false, messages);

        assertTrue(EstaffCommand.requirePermission(console, STATUS_PERMISSION, "Denied"));
        assertEquals(0, messages.get());
    }

    @Test
    void ordinarySenderWithoutPermissionRemainsDenied() {
        AtomicInteger messages = new AtomicInteger();
        CommandSender sender = sender(CommandSender.class, false, messages);

        assertFalse(EstaffCommand.requirePermission(sender, STATUS_PERMISSION, "Denied"));
        assertEquals(1, messages.get());
    }

    @Test
    void ordinarySenderWithPermissionRemainsAllowed() {
        AtomicInteger messages = new AtomicInteger();
        CommandSender sender = sender(CommandSender.class, true, messages);

        assertTrue(EstaffCommand.requirePermission(sender, STATUS_PERMISSION, "Denied"));
        assertEquals(0, messages.get());
    }

    @Test
    void consoleBypassStillFailsClosedForMissingOrBlankPermissions() {
        for (String permission : new String[]{null, "", "   "}) {
            AtomicInteger messages = new AtomicInteger();
            CommandSender console = sender(ConsoleCommandSender.class, true, messages);

            assertFalse(EstaffCommand.requirePermission(console, permission, "Denied"));
            assertEquals(1, messages.get());
        }
    }

    private static CommandSender sender(
            Class<? extends CommandSender> senderType,
            boolean allowed,
            AtomicInteger messages
    ) {
        return (CommandSender) Proxy.newProxyInstance(
                Thread.currentThread().getContextClassLoader(),
                new Class<?>[]{senderType},
                (proxy, method, arguments) -> switch (method.getName()) {
                    case "hasPermission" -> allowed;
                    case "sendMessage" -> {
                        messages.incrementAndGet();
                        yield null;
                    }
                    case "getName" -> "EstaffCommandPermissionTest";
                    default -> defaultValue(method.getReturnType());
                }
        );
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) {
            return null;
        }
        if (type == boolean.class) {
            return false;
        }
        if (type == char.class) {
            return '\0';
        }
        return 0;
    }
}
