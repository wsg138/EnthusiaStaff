package net.enthusia.staff.paper.visibility;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.destroystokyo.paper.event.entity.EntityAddToWorldEvent;
import io.papermc.paper.threadedregions.scheduler.EntityScheduler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.ShulkerBullet;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;

class VanishTargetingEntityLifecycleTest {
    private static final UUID TARGET_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private static final UUID OTHER_ID = UUID.fromString("55555555-5555-5555-5555-555555555555");

    @Test
    void loadedBulletTargetingVanishedPlayerIsRemoved() {
        Player target = player(TARGET_ID);
        BulletState state = new BulletState(target);
        ShulkerBullet bullet = bullet(state);

        guard(TARGET_ID::equals).onEntityAdded(new EntityAddToWorldEvent(bullet, world()));

        assertTrue(state.removed.get());
    }

    @Test
    void loadedVisibleTargetIsTrackedForLaterVanishReconciliation() {
        AtomicBoolean hidden = new AtomicBoolean(false);
        Player target = player(TARGET_ID);
        BulletState state = new BulletState(target);
        ShulkerBullet bullet = bullet(state);
        VanishTargetingGuard guard = guard(id -> TARGET_ID.equals(id) && hidden.get());
        guard.onEntityAdded(new EntityAddToWorldEvent(bullet, world()));
        hidden.set(true);

        guard.reconcile(player(TARGET_ID));

        assertTrue(state.removed.get());
    }

    @Test
    void loadedBulletForDifferentPlayerIsPreserved() {
        Player other = player(OTHER_ID);
        BulletState state = new BulletState(other);
        ShulkerBullet bullet = bullet(state);

        guard(TARGET_ID::equals).onEntityAdded(new EntityAddToWorldEvent(bullet, world()));

        assertFalse(state.removed.get());
        assertSame(other, state.target.get());
    }

    private static VanishTargetingGuard guard(java.util.function.Predicate<UUID> vanished) {
        return new VanishTargetingGuard(plugin(), vanished);
    }

    private static Plugin plugin() {
        return proxy(Plugin.class, (method, arguments) -> unexpected(method));
    }

    private static World world() {
        return proxy(World.class, (method, arguments) -> unexpected(method));
    }

    private static Player player(UUID id) {
        return proxy(Player.class, (method, arguments) -> switch (method.getName()) {
            case "getUniqueId" -> id;
            case "getNearbyEntities" -> List.of();
            default -> unexpected(method);
        });
    }

    private static ShulkerBullet bullet(BulletState state) {
        UUID id = UUID.randomUUID();
        EntityScheduler scheduler = immediateScheduler();
        return proxy(ShulkerBullet.class, (method, arguments) -> switch (method.getName()) {
            case "getUniqueId" -> id;
            case "getScheduler" -> scheduler;
            case "getTarget" -> state.target.get();
            case "remove" -> markRemoved(state);
            default -> unexpected(method);
        });
    }

    private static EntityScheduler immediateScheduler() {
        return proxy(EntityScheduler.class, (method, arguments) -> {
            if (method.getName().equals("execute")) {
                ((Runnable) arguments[1]).run();
                return true;
            }
            return unexpected(method);
        });
    }

    private static Object markRemoved(BulletState state) {
        state.removed.set(true);
        return null;
    }

    private static <T> T proxy(Class<T> type, Invocation invocation) {
        return type.cast(Proxy.newProxyInstance(
                Thread.currentThread().getContextClassLoader(),
                new Class<?>[]{type},
                (instance, method, arguments) -> invocation.invoke(
                        method, arguments == null ? new Object[0] : arguments)
        ));
    }

    private static Object unexpected(Method method) {
        throw new AssertionError("Unexpected call: " + method.getName());
    }

    private record BulletState(AtomicReference<Entity> target, AtomicBoolean removed) {
        private BulletState(Entity target) {
            this(new AtomicReference<>(target), new AtomicBoolean());
        }
    }

    @FunctionalInterface
    private interface Invocation {
        Object invoke(Method method, Object[] arguments) throws Throwable;
    }
}
