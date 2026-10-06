package net.enthusia.staff.paper.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

class StaffDutyContextCalculatorTest {
    private static final UUID PLAYER_ID = UUID.fromString("30000000-0000-0000-0000-000000000001");
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
    void activeStaffSessionPublishesDutyContext() {
        StaffDutyContextCalculator calculator = new StaffDutyContextCalculator(PLAYER_ID::equals);
        Map<String, String> contexts = new HashMap<>();

        calculator.calculate(player(), contexts::put);

        assertEquals(
                StaffDutyContextCalculator.ACTIVE_VALUE,
                contexts.get(StaffDutyContextCalculator.CONTEXT_KEY)
        );
    }

    @Test
    void unrestrictedPlayerPublishesDutyContextWithoutActiveSession() {
        StaffDutyContextCalculator calculator = new StaffDutyContextCalculator(
                ignored -> false,
                ignored -> true
        );
        Map<String, String> contexts = new HashMap<>();

        calculator.calculate(player(), contexts::put);

        assertEquals(
                StaffDutyContextCalculator.ACTIVE_VALUE,
                contexts.get(StaffDutyContextCalculator.CONTEXT_KEY)
        );
    }

    @Test
    void inactivePlayerGetsNoDutyContext() {
        StaffDutyContextCalculator calculator = new StaffDutyContextCalculator(
                ignored -> false,
                ignored -> false
        );
        Map<String, String> contexts = new HashMap<>();

        calculator.calculate(player(), contexts::put);

        assertTrue(contexts.isEmpty());
    }

    private static Player player() {
        return (Player) Proxy.newProxyInstance(
                Thread.currentThread().getContextClassLoader(),
                new Class<?>[]{Player.class},
                (proxy, method, arguments) -> switch (method.getName()) {
                    case "getUniqueId" -> PLAYER_ID;
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
