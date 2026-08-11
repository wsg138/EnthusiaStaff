package net.enthusia.staff.paper.freeze;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.BiFunction;
import java.util.logging.Logger;
import net.enthusia.staff.domain.ports.FreezeStore;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

final class FreezeVerificationFailureTest {
    private static final String ACTIVE_METHOD = "active";
    private static final String EXECUTE_METHOD = "execute";
    private static final UUID PLAYER_ID = UUID.fromString("a321230f-30ce-499e-bd08-58126273d74c");
    private static final String PLAYER_NAME = "ReviewTarget";
    private static final Component PLAYER_MESSAGE = Component.text(
            "Your freeze status could not be verified. You remain restricted until staff review."
    );
    private static final List<String> SECURE_ORDER = List.of("leaveVehicle", "closeInventory", "sendMessage");

    @Test
    void unavailableStoreExplainsFailClosedRestrictionToPlayerAndStaff() {
        Harness harness = harness(() -> null, directExecutor());

        harness.manager().verify(PLAYER_ID, PLAYER_NAME);

        assertUnavailable(harness, "Freeze storage is unavailable while " + PLAYER_NAME + " is joining.");
    }

    @Test
    void failedLookupExplainsFailClosedRestrictionToPlayerAndStaff() {
        FreezeStore failing = proxy(FreezeStore.class, (method, arguments) -> {
            if (ACTIVE_METHOD.equals(method.getName())) {
                throw new IllegalStateException("database unavailable");
            }
            return defaultValue(method.getReturnType());
        });
        Harness harness = harness(() -> failing, directExecutor());

        harness.manager().verify(PLAYER_ID, PLAYER_NAME);

        assertUnavailable(harness, "Freeze lookup failed while " + PLAYER_NAME + " is joining.");
    }

    @Test
    void saturatedWorkerExplainsFailClosedRestrictionToPlayerAndStaff() {
        Harness harness = harness(() -> null, rejectedExecutor());

        harness.manager().verify(PLAYER_ID, PLAYER_NAME);

        assertUnavailable(harness, "Freeze verification could not run for " + PLAYER_NAME + '.');
    }

    private static void assertUnavailable(Harness harness, String staffPrefix) {
        assertTrue(harness.manager().isRestricted(PLAYER_ID));
        assertEquals(SECURE_ORDER, harness.playerInteractions());
        assertEquals(List.of(PLAYER_MESSAGE), harness.playerMessages());
        assertEquals(1, harness.staffAlerts().size());
        assertTrue(harness.staffAlerts().getFirst().startsWith(staffPrefix));
        assertTrue(harness.staffAlerts().getFirst().contains("The player remains restricted"));
    }

    private static Harness harness(
            java.util.function.Supplier<FreezeStore> store,
            ExecutorService workers
    ) {
        List<String> interactions = new ArrayList<>();
        List<Component> playerMessages = new ArrayList<>();
        List<String> staffAlerts = new ArrayList<>();
        Player player = player(interactions, playerMessages);
        FreezeManager.PlayerDispatcher dispatcher = (playerId, operation, unavailable) -> {
            assertEquals(PLAYER_ID, playerId);
            operation.accept(player);
        };
        Logger logger = Logger.getAnonymousLogger();
        logger.setUseParentHandlers(false);
        FreezeManager manager = new FreezeManager(
                null,
                Clock.systemUTC(),
                store,
                workers,
                dispatcher,
                Runnable::run,
                logger,
                staffAlerts::add
        );
        return new Harness(manager, interactions, playerMessages, staffAlerts);
    }

    private static ExecutorService directExecutor() {
        return proxy(ExecutorService.class, (method, arguments) -> {
            if (EXECUTE_METHOD.equals(method.getName())) {
                ((Runnable) arguments[0]).run();
            }
            return defaultValue(method.getReturnType());
        });
    }

    private static ExecutorService rejectedExecutor() {
        return proxy(ExecutorService.class, (method, arguments) -> {
            if (EXECUTE_METHOD.equals(method.getName())) {
                throw new RejectedExecutionException("queue full");
            }
            return defaultValue(method.getReturnType());
        });
    }

    private static Player player(List<String> interactions, List<Component> messages) {
        return proxy(Player.class, (method, arguments) -> switch (method.getName()) {
            case "getUniqueId" -> PLAYER_ID;
            case "leaveVehicle" -> {
                interactions.add("leaveVehicle");
                yield true;
            }
            case "closeInventory" -> {
                interactions.add("closeInventory");
                yield null;
            }
            case "sendMessage" -> {
                interactions.add("sendMessage");
                if (arguments[0] instanceof Component component) {
                    messages.add(component);
                }
                yield null;
            }
            default -> defaultValue(method.getReturnType());
        });
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, BiFunction<Method, Object[], Object> invocation) {
        return (T) Proxy.newProxyInstance(
                Thread.currentThread().getContextClassLoader(),
                new Class<?>[]{type},
                (instance, method, arguments) -> invocation.apply(method, arguments)
        );
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive() || type == void.class) {
            return null;
        }
        return Array.get(Array.newInstance(type, 1), 0);
    }

    private record Harness(
            FreezeManager manager,
            List<String> playerInteractions,
            List<Component> playerMessages,
            List<String> staffAlerts
    ) {
    }
}
