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
import java.util.concurrent.atomic.AtomicInteger;
import net.enthusia.staff.domain.OperationalMode;
import net.enthusia.staff.domain.player.PlayerIdentity;
import net.enthusia.staff.domain.player.PlayerPlatform;
import net.enthusia.staff.domain.ports.FreezeStore;
import net.enthusia.staff.domain.ports.PlayerDirectory;
import net.enthusia.staff.paper.auth.StaffTargetGuard;
import net.enthusia.staff.paper.freeze.FreezeAlertSink;
import net.enthusia.staff.paper.freeze.FreezeNoticeSink;
import net.kyori.adventure.text.Component;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

class FreezeCommandHierarchyTest {
    private static final Instant NOW = Instant.parse("2026-09-23T16:00:00Z");
    private static final UUID ACTOR_ID = UUID.fromString("61000000-0000-0000-0000-000000000001");
    private static final UUID TARGET_ID = UUID.fromString("61000000-0000-0000-0000-000000000002");

    @Test
    void protectedTargetIsRejectedBeforeFreezePersistence() {
        AtomicInteger writes = new AtomicInteger();
        List<Component> messages = new ArrayList<>();
        PlayerIdentity target = new PlayerIdentity(
                TARGET_ID, Optional.of("Admin"), PlayerPlatform.JAVA, NOW.minusSeconds(60), NOW
        );
        PlayerDirectory directory = proxy(PlayerDirectory.class, (method, arguments) -> switch (method.getName()) {
            case "find" -> Optional.of(target);
            default -> defaultValue(method.getReturnType());
        });
        FreezeStore store = proxy(FreezeStore.class, (method, arguments) -> {
            if ("apply".equals(method.getName())) {
                writes.incrementAndGet();
            }
            return defaultValue(method.getReturnType());
        });
        StaffTargetGuard denied = (actor, targetId, systemActor) ->
                StaffTargetGuard.Result.deny("protected target");
        FreezeCommand command = new FreezeCommand(
                null,
                Clock.fixed(NOW, ZoneOffset.UTC),
                () -> OperationalMode.ACTIVE,
                () -> directory,
                () -> store,
                null,
                new DirectExecutorService(),
                new FreezeCommand.RuntimeHooks(
                        denied,
                        FreezeAlertSink.noOp(),
                        FreezeNoticeSink.noOp(),
                        (sender, responses) -> messages.addAll(responses)
                )
        );

        command.onCommand(
                playerSender(),
                command("freeze"),
                "freeze",
                new String[]{"Admin", "screenshare"}
        );

        assertEquals(0, writes.get());
        assertEquals(List.of(Component.text("protected target")), messages);
    }

    private static Player playerSender() {
        return proxy(Player.class, (method, arguments) -> switch (method.getName()) {
            case "getUniqueId" -> ACTOR_ID;
            case "getName" -> "Moderator";
            case "hasPermission" -> {
                String permission = (String) arguments[0];
                yield permission.equals("enthusiastaff.freeze") || permission.equals("enthusiastaff.rank.mod");
            }
            default -> defaultValue(method.getReturnType());
        });
    }

    private static Command command(String name) {
        return new Command(name) {
            @Override
            public boolean execute(CommandSender sender, String label, String[] arguments) {
                return true;
            }
        };
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
        @Override
        public void shutdown() {
        }

        @Override
        public List<Runnable> shutdownNow() {
            return List.of();
        }

        @Override
        public boolean isShutdown() {
            return false;
        }

        @Override
        public boolean isTerminated() {
            return false;
        }

        @Override
        public boolean awaitTermination(long timeout, TimeUnit unit) {
            return false;
        }

        @Override
        public void execute(Runnable command) {
            command.run();
        }
    }
}
