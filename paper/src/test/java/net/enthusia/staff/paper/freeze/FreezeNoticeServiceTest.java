package net.enthusia.staff.paper.freeze;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Array;
import java.lang.reflect.Proxy;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;
import net.enthusia.staff.domain.freeze.FreezeRecord;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

class FreezeNoticeServiceTest {
    private static final UUID PLAYER_ID = UUID.fromString("91000000-0000-0000-0000-000000000001");

    @Test
    void releasedThenRefrozenPlayerRejectsNoticeFromOlderGeneration() {
        FreezeManager manager = manager();
        List<Component> messages = new ArrayList<>();
        Player player = player(messages);
        FreezeRecord record = record();

        long staleGeneration = manager.applyOnline(PLAYER_ID);
        manager.releaseOnline(PLAYER_ID);
        long currentGeneration = manager.applyOnline(PLAYER_ID);

        FreezeNoticeService.deliverIfCurrent(
                manager, record, "OldModerator", staleGeneration, player
        );
        assertTrue(messages.isEmpty());

        FreezeNoticeService.deliverIfCurrent(
                manager, record, "CurrentModerator", currentGeneration, player
        );
        assertFalse(messages.isEmpty());
    }

    private static FreezeManager manager() {
        return new FreezeManager(
                null, Clock.systemUTC(), () -> null, null,
                (playerId, operation, unavailable) -> {
                },
                Runnable::run, Logger.getLogger(FreezeNoticeServiceTest.class.getName()), null
        );
    }

    private static FreezeRecord record() {
        return new FreezeRecord(
                PLAYER_ID,
                UUID.fromString("91000000-0000-0000-0000-000000000002"),
                "verification",
                Instant.parse("2026-09-23T12:00:00Z"),
                Optional.empty(),
                false,
                1L
        );
    }

    private static Player player(List<Component> messages) {
        return proxy(Player.class, (method, arguments) -> {
            if (method.getName().equals("sendMessage")
                    && arguments != null && arguments.length > 0 && arguments[0] instanceof Component component) {
                messages.add(component);
            }
            return defaultValue(method.getReturnType());
        });
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, Invocation invocation) {
        return (T) Proxy.newProxyInstance(
                Thread.currentThread().getContextClassLoader(),
                new Class<?>[]{type},
                (instance, method, arguments) -> invocation.invoke(method, arguments)
        );
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive() || type == void.class) {
            return null;
        }
        return Array.get(Array.newInstance(type, 1), 0);
    }

    @FunctionalInterface
    private interface Invocation {
        Object invoke(java.lang.reflect.Method method, Object[] arguments);
    }
}
