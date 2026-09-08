package net.enthusia.staff.paper.command;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Array;
import java.lang.reflect.Proxy;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.TimeUnit;
import net.enthusia.staff.domain.OperationalMode;
import net.enthusia.staff.domain.player.PlayerIdentity;
import net.enthusia.staff.domain.player.PlayerPlatform;
import net.enthusia.staff.domain.player.PlayerResolution;
import net.enthusia.staff.domain.ports.FreezeStore;
import net.enthusia.staff.domain.ports.PlayerDirectory;
import net.kyori.adventure.text.Component;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.Test;

final class FreezeCommandReadTest {
    private static final Instant NOW = Instant.parse("2026-09-08T12:30:00Z");
    private static final UUID PLAYER_ID = UUID.fromString("41000000-0000-0000-0000-000000000001");

    @Test
    void statusRemainsAvailableWhenFreezeChangesAreDisabled() {
        List<Component> messages = new ArrayList<>();
        PlayerIdentity player = identity(PLAYER_ID, "FrozenPlayer");
        PlayerDirectory directory = proxy(PlayerDirectory.class, (method, arguments) -> switch (method.getName()) {
            case "resolve" -> new PlayerResolution.Resolved(
                    player,
                    PlayerResolution.MatchKind.CURRENT_USERNAME
            );
            default -> defaultValue(method.getReturnType());
        });
        FreezeStore store = proxy(FreezeStore.class, (method, arguments) -> switch (method.getName()) {
            case "readActive" -> Optional.empty();
            default -> defaultValue(method.getReturnType());
        });
        FreezeCommand handler = handler(OperationalMode.READ_ONLY_FAILURE, directory, store, messages);

        handler.onCommand(
                sender(messages),
                command("freeze"),
                "freeze",
                new String[]{"status", "FrozenPlayer"}
        );

        assertEquals(List.of(Component.text(
                "FrozenPlayer (" + PLAYER_ID + ") is not currently frozen."
        )), messages);
    }

    @Test
    void mutationRemainsBlockedWhenFreezeChangesAreDisabled() {
        List<Component> messages = new ArrayList<>();
        FreezeCommand handler = handler(OperationalMode.READ_ONLY_FAILURE, null, null, messages);

        handler.onCommand(
                sender(messages),
                command("freeze"),
                "freeze",
                new String[]{"FrozenPlayer", "Investigation"}
        );

        assertEquals(List.of(Component.text(
                "Freeze changes are disabled while moderation is READ_ONLY_FAILURE."
        )), messages);
    }

    private static FreezeCommand handler(
            OperationalMode mode,
            PlayerDirectory directory,
            FreezeStore store,
            List<Component> messages
    ) {
        return new FreezeCommand(
                null,
                Clock.fixed(NOW, ZoneOffset.UTC),
                () -> mode,
                () -> directory,
                () -> store,
                null,
                new DirectExecutorService(),
                (sender, responses) -> messages.addAll(responses)
        );
    }

    private static PlayerIdentity identity(UUID id, String name) {
        return new PlayerIdentity(id, Optional.of(name), PlayerPlatform.JAVA, NOW.minusSeconds(60), NOW);
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
        return proxy(CommandSender.class, (method, arguments) -> switch (method.getName()) {
            case "hasPermission" -> true;
            case "sendMessage" -> {
                if (arguments != null && arguments.length > 0 && arguments[0] instanceof Component component) {
                    messages.add(component);
                }
                yield null;
            }
            default -> defaultValue(method.getReturnType());
        });
    }

    private static <T> T proxy(Class<T> type, Invocation invocation) {
        return type.cast(Proxy.newProxyInstance(
                Thread.currentThread().getContextClassLoader(),
                new Class<?>[]{type},
                (instance, method, arguments) -> invocation.invoke(method, arguments)
        ));
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

    private static final class DirectExecutorService extends AbstractExecutorService {
        private boolean shutdown;

        @Override
        public void shutdown() {
            shutdown = true;
        }

        @Override
        public List<Runnable> shutdownNow() {
            shutdown = true;
            return List.of();
        }

        @Override
        public boolean isShutdown() {
            return shutdown;
        }

        @Override
        public boolean isTerminated() {
            return shutdown;
        }

        @Override
        public boolean awaitTermination(long timeout, TimeUnit unit) {
            return shutdown;
        }

        @Override
        public void execute(Runnable command) {
            command.run();
        }
    }
}
