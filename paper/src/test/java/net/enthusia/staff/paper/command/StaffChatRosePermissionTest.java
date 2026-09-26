package net.enthusia.staff.paper.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

class StaffChatRosePermissionTest {
    private static final Map<Class<?>, Object> PRIMITIVE_DEFAULTS = Map.of(
            boolean.class, false,
            byte.class, (byte) 0,
            short.class, (short) 0,
            int.class, 0,
            long.class, 0L,
            float.class, 0F,
            double.class, 0D,
            char.class, '\0'
    );

    @Test
    void missingRoseChatPermissionIsReportedBeforeIntegrationLookup() {
        List<Object> messages = new ArrayList<>();
        AtomicInteger lookups = new AtomicInteger();
        Player player = player(messages);
        StaffChatCommand command = new StaffChatCommand(() -> {
            lookups.incrementAndGet();
            return null;
        });

        assertTrue(command.onCommand(player, null, "staffchat", new String[0]));
        assertEquals(0, lookups.get());
        assertEquals(
                List.of(Component.text("You do not have permission to use the RoseChat staff channel.")),
                messages
        );
    }

    private static Player player(List<Object> messages) {
        return (Player) Proxy.newProxyInstance(
                Thread.currentThread().getContextClassLoader(),
                new Class<?>[]{Player.class},
                (proxy, method, arguments) -> switch (method.getName()) {
                    case "hasPermission" -> "enthusiastaff.staffchat".equals(arguments[0]);
                    case "sendMessage" -> {
                        messages.add(arguments[0]);
                        yield null;
                    }
                    default -> defaultValue(method.getReturnType());
                }
        );
    }

    private static Object defaultValue(Class<?> type) {
        if (type == void.class || !type.isPrimitive()) {
            return null;
        }
        return PRIMITIVE_DEFAULTS.get(type);
    }
}
