package net.enthusia.staff.paper.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.Test;

class CommandPermissionGateTest {
    private static final String STAFF_MODE = "enthusiastaff.staffmode";

    @Test
    void exactPermissionGrantIsRequired() {
        Set<String> permissions = Set.of(STAFF_MODE);

        assertTrue(CommandPermissionGate.allows(permissions::contains, STAFF_MODE));
        assertFalse(CommandPermissionGate.allows(permissions::contains, "enthusiastaff.vanish"));
    }

    @Test
    void missingOrBlankPermissionFailsClosedWithoutCallingThePermissionProvider() {
        AtomicInteger checks = new AtomicInteger();

        assertFalse(CommandPermissionGate.allows(permission -> {
            checks.incrementAndGet();
            return true;
        }, null));
        assertFalse(CommandPermissionGate.allows(permission -> {
            checks.incrementAndGet();
            return true;
        }, ""));
        assertFalse(CommandPermissionGate.allows(permission -> {
            checks.incrementAndGet();
            return true;
        }, "   "));
        assertEquals(0, checks.get());
    }

    @Test
    void nullPermissionProviderIsRejected() {
        assertThrows(NullPointerException.class, () -> CommandPermissionGate.allows(null, STAFF_MODE));
    }

    @Test
    void requireRejectsANullSender() {
        assertThrows(NullPointerException.class, () -> CommandPermissionGate.require(null, STAFF_MODE, "Denied"));
    }

    @Test
    void requireSendsOneDenialAndReturnsFalse() {
        AtomicInteger messages = new AtomicInteger();
        CommandSender sender = sender(false, messages);

        assertFalse(CommandPermissionGate.require(sender, STAFF_MODE, "Denied"));
        assertEquals(1, messages.get());
    }

    @Test
    void requireAllowsWithoutSendingADenial() {
        AtomicInteger messages = new AtomicInteger();
        CommandSender sender = sender(true, messages);

        assertTrue(CommandPermissionGate.require(sender, STAFF_MODE, "Denied"));
        assertEquals(0, messages.get());
    }

    private static CommandSender sender(boolean allowed, AtomicInteger messages) {
        return (CommandSender) Proxy.newProxyInstance(
                Thread.currentThread().getContextClassLoader(),
                new Class<?>[]{CommandSender.class},
                (proxy, method, arguments) -> switch (method.getName()) {
                    case "hasPermission" -> allowed;
                    case "sendMessage" -> {
                        messages.incrementAndGet();
                        yield null;
                    }
                    case "getName" -> "PermissionGateTest";
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
