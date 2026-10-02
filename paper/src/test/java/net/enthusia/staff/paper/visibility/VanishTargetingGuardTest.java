package net.enthusia.staff.paper.visibility;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.papermc.paper.threadedregions.scheduler.EntityScheduler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.ShulkerBullet;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;

class VanishTargetingGuardTest {
    private static final UUID TARGET_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID OTHER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Test
    void visiblePlayerMayStillBeTargeted() {
        Player target = player(TARGET_ID, List.of());
        VanishTargetingGuard guard = guard(id -> false);
        EntityTargetEvent event = targetEvent(target);

        guard.onTargetAcquisition(event);

        assertFalse(event.isCancelled());
        assertSame(target, event.getTarget());
    }

    @Test
    void vanishedPlayerTargetAttemptIsCancelledAndCleared() {
        Player target = player(TARGET_ID, List.of());
        VanishTargetingGuard guard = guard(TARGET_ID::equals);
        EntityTargetEvent event = targetEvent(target);

        guard.onTargetAcquisition(event);

        assertTrue(event.isCancelled());
        assertNull(event.getTarget());
    }

    @Test
    void unvanishedPlayerBecomesEligibleAgain() {
        AtomicBoolean hidden = new AtomicBoolean(true);
        Player target = player(TARGET_ID, List.of());
        VanishTargetingGuard guard = guard(id -> TARGET_ID.equals(id) && hidden.get());
        EntityTargetEvent hiddenAttempt = targetEvent(target);
        guard.onTargetAcquisition(hiddenAttempt);
        hidden.set(false);
        EntityTargetEvent visibleAttempt = targetEvent(target);

        guard.onTargetAcquisition(visibleAttempt);

        assertTrue(hiddenAttempt.isCancelled());
        assertFalse(visibleAttempt.isCancelled());
        assertSame(target, visibleAttempt.getTarget());
    }

    @Test
    void viewerRankOrPermissionDoesNotAffectMobEligibility() {
        Player target = player(TARGET_ID, List.of(), true);
        VanishTargetingGuard guard = guard(TARGET_ID::equals);
        EntityTargetEvent event = targetEvent(target);

        guard.onTargetAcquisition(event);

        assertTrue(event.isCancelled());
    }

    @Test
    void existingMobTargetDropsWhenVanishIsReconciled() {
        Player target = player(TARGET_ID, List.of());
        AtomicReference<LivingEntity> mobTarget = new AtomicReference<>(target);
        Mob mob = mob(mobTarget);
        Player owner = player(TARGET_ID, List.of(mob));

        guard(TARGET_ID::equals).reconcile(owner);

        assertNull(mobTarget.get());
    }

    @Test
    void targetedShulkerBulletIsRemovedAndUnrelatedBulletIsPreserved() {
        Player target = player(TARGET_ID, List.of());
        Player other = player(OTHER_ID, List.of());
        BulletState targetedState = new BulletState(target);
        BulletState unrelatedState = new BulletState(other);
        ShulkerBullet targeted = bullet(targetedState);
        ShulkerBullet unrelated = bullet(unrelatedState);
        Player owner = player(TARGET_ID, List.of(targeted, unrelated));

        guard(TARGET_ID::equals).reconcile(owner);

        assertTrue(targetedState.removed.get());
        assertFalse(unrelatedState.removed.get());
        assertSame(other, unrelatedState.target.get());
    }

    @Test
    void newShulkerBulletCannotLaunchAtVanishedPlayer() {
        Player target = player(TARGET_ID, List.of());
        BulletState state = new BulletState(target);
        ShulkerBullet bullet = bullet(state);
        ProjectileLaunchEvent event = new ProjectileLaunchEvent(bullet);

        guard(TARGET_ID::equals).onShulkerBulletLaunch(event);

        assertTrue(event.isCancelled());
        assertNull(state.target.get());
        assertFalse(state.removed.get());
    }

    @Test
    void trackedBulletIsCleanedEvenAfterLeavingNearbyScan() {
        AtomicBoolean hidden = new AtomicBoolean(false);
        Player target = player(TARGET_ID, List.of());
        BulletState state = new BulletState(target);
        ShulkerBullet bullet = bullet(state);
        VanishTargetingGuard guard = guard(id -> TARGET_ID.equals(id) && hidden.get());
        guard.onShulkerBulletLaunched(new ProjectileLaunchEvent(bullet));
        hidden.set(true);

        guard.reconcile(player(TARGET_ID, List.of()));

        assertTrue(state.removed.get());
    }

    @Test
    void restoredVanishUsesTheSameProtectedReconciliationState() {
        Player target = player(TARGET_ID, List.of());
        AtomicReference<LivingEntity> mobTarget = new AtomicReference<>(target);
        Mob mob = mob(mobTarget);

        guard(TARGET_ID::equals).reconcile(player(TARGET_ID, List.of(mob)));

        assertNull(mobTarget.get());
    }

    private static EntityTargetEvent targetEvent(Player target) {
        return new EntityTargetEvent(
                mob(new AtomicReference<>()),
                target,
                EntityTargetEvent.TargetReason.CLOSEST_PLAYER
        );
    }

    private static VanishTargetingGuard guard(java.util.function.Predicate<UUID> vanished) {
        return new VanishTargetingGuard(plugin(), vanished);
    }

    private static Plugin plugin() {
        return proxy(Plugin.class, (method, arguments) -> unexpected(method));
    }

    private static Player player(UUID id, List<Entity> nearby) {
        return player(id, nearby, false);
    }

    private static Player player(UUID id, List<Entity> nearby, boolean rejectPermissionChecks) {
        return proxy(Player.class, (method, arguments) -> switch (method.getName()) {
            case "getUniqueId" -> id;
            case "getNearbyEntities" -> nearby;
            case "hasPermission" -> rejectPermissionChecks ? unexpected(method) : false;
            default -> unexpected(method);
        });
    }

    private static Mob mob(AtomicReference<LivingEntity> target) {
        UUID id = UUID.randomUUID();
        EntityScheduler scheduler = immediateScheduler();
        return proxy(Mob.class, (method, arguments) -> switch (method.getName()) {
            case "getUniqueId" -> id;
            case "getScheduler" -> scheduler;
            case "getTarget" -> target.get();
            case "setTarget" -> setMobTarget(target, arguments[0]);
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
            case "setTarget" -> setBulletTarget(state, arguments[0]);
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

    private static Object setMobTarget(AtomicReference<LivingEntity> target, Object value) {
        target.set((LivingEntity) value);
        return null;
    }

    private static Object setBulletTarget(BulletState state, Object value) {
        state.target.set((Entity) value);
        return null;
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
