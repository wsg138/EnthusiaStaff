package net.enthusia.staff.paper.freeze;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.papermc.paper.threadedregions.scheduler.EntityScheduler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import net.enthusia.staff.domain.auth.Actor;
import net.enthusia.staff.domain.auth.StaffRank;
import net.enthusia.staff.domain.player.PlayerIdentity;
import net.enthusia.staff.domain.player.PlayerPlatform;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;

class FreezeStaffNotifierTest {
    private static final Instant NOW = Instant.parse("2026-09-23T12:00:00Z");

    @Test
    void freezeAlertNamesActorTargetReasonAndProvidesTeleportAction() {
        PlayerIdentity target = new PlayerIdentity(
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                Optional.of("Target"),
                PlayerPlatform.JAVA,
                NOW,
                NOW
        );
        Actor actor = new Actor(
                UUID.fromString("22222222-2222-2222-2222-222222222222"),
                "Moderator",
                StaffRank.MOD
        );

        Component message = FreezeStaffNotifier.render(target, actor, "screenshare", true);
        String plain = PlainTextComponentSerializer.plainText().serialize(message);

        assertTrue(plain.contains("Target frozen by Moderator"));
        assertTrue(plain.contains("screenshare"));
        Component teleport = message.children().getLast();
        assertEquals(ClickEvent.runCommand("/tp Target"), teleport.clickEvent());
    }

    @Test
    void offlineIdentityDoesNotOfferBrokenTeleportAction() {
        PlayerIdentity target = new PlayerIdentity(
                UUID.fromString("33333333-3333-3333-3333-333333333333"),
                Optional.empty(),
                PlayerPlatform.UNKNOWN,
                NOW,
                NOW
        );
        Actor actor = new Actor(
                UUID.fromString("44444444-4444-4444-4444-444444444444"),
                "Admin",
                StaffRank.ADMIN
        );

        Component message = FreezeStaffNotifier.render(target, actor, "release", false);

        assertTrue(message.children().stream().noneMatch(child -> child.clickEvent() != null));
    }

    @Test
    void staffCheckAndDeliveryRunOnlyOnRecipientEntityScheduler() {
        AtomicBoolean owned = new AtomicBoolean();
        AtomicBoolean permissionChecked = new AtomicBoolean();
        AtomicBoolean delivered = new AtomicBoolean();
        Runnable[] entityTask = new Runnable[1];
        EntityScheduler scheduler = scheduler((plugin, action, retired, delay) -> {
            assertEquals(1L, delay);
            entityTask[0] = action;
            return true;
        });
        Player player = player(scheduler, owned, permissionChecked, delivered);

        boolean scheduled = FreezeStaffNotifier.scheduleRecipient(
                plugin(),
                player,
                Component.text("freeze alert")
        );

        assertTrue(scheduled);
        assertFalse(permissionChecked.get());
        assertFalse(delivered.get());
        owned.set(true);
        entityTask[0].run();
        assertTrue(permissionChecked.get());
        assertTrue(delivered.get());
    }

    @Test
    void rejectedEntitySchedulingDoesNotTouchRecipientState() {
        AtomicBoolean permissionChecked = new AtomicBoolean();
        AtomicBoolean delivered = new AtomicBoolean();
        EntityScheduler scheduler = scheduler((plugin, action, retired, delay) -> false);
        Player player = player(scheduler, new AtomicBoolean(), permissionChecked, delivered);

        boolean scheduled = FreezeStaffNotifier.scheduleRecipient(
                plugin(),
                player,
                Component.text("freeze alert")
        );

        assertFalse(scheduled);
        assertFalse(permissionChecked.get());
        assertFalse(delivered.get());
    }

    private static Player player(
            EntityScheduler scheduler,
            AtomicBoolean owned,
            AtomicBoolean permissionChecked,
            AtomicBoolean delivered
    ) {
        return proxy(Player.class, (method, arguments) -> {
            if (method.getName().equals("getScheduler")) {
                return scheduler;
            }
            if (method.getName().equals("hasPermission")) {
                assertTrue(owned.get());
                permissionChecked.set(true);
                return true;
            }
            if (method.getName().equals("sendMessage")) {
                assertTrue(owned.get());
                delivered.set(true);
                return null;
            }
            return unexpected(method);
        });
    }

    private static Plugin plugin() {
        return proxy(Plugin.class, (method, arguments) -> unexpected(method));
    }

    private static EntityScheduler scheduler(SchedulerExecution execution) {
        return proxy(EntityScheduler.class, (method, arguments) -> {
            if (method.getName().equals("execute")) {
                return execution.execute(
                        (Plugin) arguments[0],
                        (Runnable) arguments[1],
                        (Runnable) arguments[2],
                        (Long) arguments[3]
                );
            }
            return unexpected(method);
        });
    }

    private static <T> T proxy(Class<T> type, Invocation invocation) {
        return type.cast(Proxy.newProxyInstance(
                Thread.currentThread().getContextClassLoader(),
                new Class<?>[]{type},
                (instance, method, arguments) -> invocation.invoke(method, arguments == null ? new Object[0] : arguments)
        ));
    }

    private static Object unexpected(Method method) {
        throw new AssertionError("Unexpected call: " + method.getName());
    }

    @FunctionalInterface
    private interface Invocation {
        Object invoke(Method method, Object[] arguments) throws Throwable;
    }

    @FunctionalInterface
    private interface SchedulerExecution {
        boolean execute(Plugin plugin, Runnable action, Runnable retired, long delay);
    }
}
