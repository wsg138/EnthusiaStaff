package net.enthusia.staff.paper.staff;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.papermc.paper.threadedregions.scheduler.EntityScheduler;
import io.papermc.paper.threadedregions.scheduler.GlobalRegionScheduler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import net.kyori.adventure.text.Component;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;

class StaffToolDispatcherSpectateFlowTest {
    @Test
    void visibleNonExemptTargetSucceeds() {
        Harness harness = new Harness();

        harness.finishSuccessfulFlow();

        assertEquals(1, harness.actor.attachments);
        assertSame(harness.target.player, harness.actor.lastSpectator);
        assertEquals(1, harness.actor.teleports);
    }

    @Test
    void targetAlreadyVanishedIsRejected() {
        Harness harness = new Harness();
        harness.vanished.add(harness.targetId);

        harness.beginAndInspectTarget();

        harness.assertRejectedBeforeTeleport();
    }

    @Test
    void targetAlreadySpectateExemptIsRejected() {
        Harness harness = new Harness();
        harness.target.exempt.set(true);

        harness.beginAndInspectTarget();

        harness.assertRejectedBeforeTeleport();
    }

    @Test
    void targetBecomesVanishedBeforeTeleportIsRejected() {
        Harness harness = new Harness();
        harness.reachPreTeleportTargetHandoff();
        harness.vanished.add(harness.targetId);

        harness.runOwned(harness.target);

        harness.assertRejectedBeforeTeleport();
    }

    @Test
    void targetBecomesExemptBeforeTeleportIsRejected() {
        Harness harness = new Harness();
        harness.reachPreTeleportTargetHandoff();
        harness.target.exempt.set(true);

        harness.runOwned(harness.target);

        harness.assertRejectedBeforeTeleport();
    }

    @Test
    void targetBecomesVanishedAfterTeleportBeforeAttachmentIsRejected() {
        Harness harness = new Harness();
        harness.reachFinalTargetHandoff();
        harness.vanished.add(harness.targetId);

        harness.runOwned(harness.target);

        harness.assertNoAttachment();
    }

    @Test
    void targetBecomesExemptAfterTeleportBeforeAttachmentIsRejected() {
        Harness harness = new Harness();
        harness.reachFinalTargetHandoff();
        harness.target.exempt.set(true);

        harness.runOwned(harness.target);

        harness.assertNoAttachment();
    }

    @Test
    void targetDisconnectBeforeTeleportIsRejected() {
        Harness harness = new Harness();
        harness.reachPreTeleportTargetHandoff();
        harness.disconnectTarget();

        harness.runNextGlobal();

        harness.assertRejectedBeforeTeleport();
    }

    @Test
    void targetSchedulerRetirementBeforeTeleportIsRejected() {
        Harness harness = new Harness();
        harness.reachPreTeleportTargetHandoff();
        harness.target.retireNext();

        harness.runNextGlobal();

        harness.assertRejectedBeforeTeleport();
    }

    @Test
    void targetDisconnectBeforeAttachmentIsRejected() {
        Harness harness = new Harness();
        harness.reachFinalTargetHandoff();
        harness.disconnectTarget();

        harness.runNextGlobal();

        harness.assertNoAttachment();
    }

    @Test
    void targetSchedulerRetirementBeforeAttachmentIsRejected() {
        Harness harness = new Harness();
        harness.reachFinalTargetHandoff();
        harness.target.retireNext();

        harness.runNextGlobal();

        harness.assertNoAttachment();
    }

    @Test
    void sameUuidReconnectCannotAttachStalePlayerObject() {
        Harness harness = new Harness();
        harness.reachFinalActorHandoff();
        Handle staleTarget = harness.target;
        Handle replacement = harness.reconnectTarget();

        harness.runOwned(harness.actor);

        assertEquals(0, harness.actor.attachments);
        assertNull(harness.actor.lastSpectator);
        assertTrue(!staleTarget.online.get());
        assertTrue(replacement.online.get());
    }

    @Test
    void actorLosesStaffSessionBeforeTeleportIsRejected() {
        Harness harness = new Harness();
        harness.reachPreTeleportActorHandoff();
        harness.actorSession.set(false);

        harness.runOwned(harness.actor);

        harness.assertRejectedBeforeTeleport();
    }

    @Test
    void actorLosesSpectatePermissionBeforeTeleportIsRejected() {
        Harness harness = new Harness();
        harness.reachPreTeleportActorHandoff();
        harness.actor.permission.set(false);

        harness.runOwned(harness.actor);

        harness.assertRejectedBeforeTeleport();
    }

    @Test
    void actorLosesAuthorityBeforeFinalAttachmentIsRejected() {
        Harness harness = new Harness();
        harness.reachFinalActorHandoff();
        harness.actorAuthority.set(false);

        harness.runOwned(harness.actor);

        harness.assertNoAttachment();
    }

    @Test
    void entitySchedulerExecuteFalseFailsClosed() {
        Harness harness = new Harness();
        harness.reachPreTeleportTargetHandoff();
        harness.target.rejectNext();

        harness.runNextGlobal();

        harness.assertRejectedBeforeTeleport();
    }

    @Test
    void entitySchedulerSubmissionExceptionFailsClosed() {
        Harness harness = new Harness();
        harness.reachPreTeleportTargetHandoff();
        harness.target.throwNext();

        harness.runNextGlobal();

        harness.assertRejectedBeforeTeleport();
    }

    @Test
    void globalSchedulerSubmissionExceptionFailsClosed() {
        Harness harness = new Harness();
        harness.failNextGlobal.set(true);

        harness.flow.begin(harness.actor.player, harness.targetId);

        harness.assertRejectedBeforeTeleport();
    }

    @Test
    void teleportAsyncSynchronousExceptionFailsClosed() {
        Harness harness = new Harness();
        harness.reachPreTeleportActorHandoff();
        harness.actor.teleportThrows.set(true);

        harness.runOwned(harness.actor);

        harness.assertRejectedBeforeAttachment();
    }

    @Test
    void teleportAsyncFalseFailsClosed() {
        Harness harness = new Harness();
        harness.reachTeleportRequest();

        harness.actor.teleport.complete(false);

        harness.assertRejectedBeforeAttachment();
    }

    @Test
    void teleportAsyncExceptionalCompletionFailsClosed() {
        Harness harness = new Harness();
        harness.reachTeleportRequest();

        harness.actor.teleport.completeExceptionally(new IllegalStateException("teleport failed"));

        harness.assertRejectedBeforeAttachment();
    }

    @Test
    void teleportAsyncCancellationFailsClosed() {
        Harness harness = new Harness();
        harness.reachTeleportRequest();

        assertTrue(harness.actor.teleport.cancel(true));

        harness.assertRejectedBeforeAttachment();
    }

    @Test
    void duplicateSchedulerCallbackCannotAttachTwice() {
        Harness harness = new Harness();
        harness.reachFinalActorHandoff();
        harness.runNextGlobal();

        harness.actor.runOwnedTwice();

        assertEquals(1, harness.actor.attachments);
        assertSame(harness.target.player, harness.actor.lastSpectator);
    }

    private enum ScheduleOutcome {
        ACCEPT,
        RETIRE,
        REJECT,
        THROW
    }

    private static final class Harness {
        private static final String SPECTATE_PERMISSION = "enthusiastaff.stafftools.spectate";
        private static final String SPECTATE_EXEMPT = "enthusiastaff.stafftools.spectate-exempt";

        private final UUID actorId = UUID.randomUUID();
        private final UUID targetId = UUID.randomUUID();
        private final Deque<Runnable> globalTasks = new ArrayDeque<>();
        private final Map<UUID, Handle> current = new HashMap<>();
        private final Set<UUID> vanished = java.util.concurrent.ConcurrentHashMap.newKeySet();
        private final AtomicBoolean actorSession = new AtomicBoolean(true);
        private final AtomicBoolean actorAuthority = new AtomicBoolean(true);
        private final AtomicBoolean failNextGlobal = new AtomicBoolean();
        private final GlobalRegionScheduler globalScheduler = proxy(
                GlobalRegionScheduler.class,
                this::globalSchedulerCall
        );
        private final Server server = proxy(Server.class, this::serverCall);
        private final Plugin plugin = proxy(Plugin.class, this::pluginCall);
        private final Handle actor = new Handle(actorId, "Actor", true);
        private Handle target = new Handle(targetId, "Target", false);
        private final StaffToolSpectateFlow flow;

        private Harness() {
            current.put(actorId, actor);
            current.put(targetId, target);
            flow = StaffToolDispatcher.createSpectateFlow(
                    plugin,
                    player -> actorSession.get()
                            && actorAuthority.get()
                            && player.hasPermission(SPECTATE_PERMISSION),
                    vanished::contains
            );
        }

        private void beginAndInspectTarget() {
            flow.begin(actor.player, targetId);
            runOwned(target);
        }

        private void reachPreTeleportTargetHandoff() {
            beginAndInspectTarget();
            runOwned(actor);
        }

        private void reachPreTeleportActorHandoff() {
            reachPreTeleportTargetHandoff();
            runOwned(target);
        }

        private void reachTeleportRequest() {
            reachPreTeleportActorHandoff();
            runOwned(actor);
            assertEquals(1, actor.teleports);
        }

        private void reachFinalTargetHandoff() {
            reachTeleportRequest();
            actor.teleport.complete(true);
            runOwned(actor);
        }

        private void reachFinalActorHandoff() {
            reachFinalTargetHandoff();
            runOwned(target);
        }

        private void finishSuccessfulFlow() {
            reachFinalActorHandoff();
            runOwned(actor);
        }

        private void runOwned(Handle handle) {
            runNextGlobal();
            handle.runOwned();
        }

        private void runNextGlobal() {
            Runnable task = globalTasks.pollFirst();
            if (task == null) {
                throw new AssertionError("No global scheduler task was pending");
            }
            task.run();
        }

        private void disconnectTarget() {
            target.online.set(false);
            current.remove(targetId);
        }

        private Handle reconnectTarget() {
            target.online.set(false);
            Handle replacement = new Handle(targetId, "Target", false);
            target = replacement;
            current.put(targetId, replacement);
            return replacement;
        }

        private void assertRejectedBeforeTeleport() {
            assertEquals(0, actor.teleports);
            assertNoAttachment();
        }

        private void assertRejectedBeforeAttachment() {
            assertEquals(1, actor.teleports);
            assertNoAttachment();
        }

        private void assertNoAttachment() {
            assertEquals(0, actor.attachments);
            assertNull(actor.lastSpectator);
        }

        private Object globalSchedulerCall(Method method, Object[] arguments) {
            if (!method.getName().equals("execute")) {
                return unexpected(method);
            }
            if (failNextGlobal.compareAndSet(true, false)) {
                throw new IllegalStateException("global scheduling rejected");
            }
            assertSame(plugin, arguments[0]);
            globalTasks.addLast((Runnable) arguments[1]);
            return null;
        }

        private Object serverCall(Method method, Object[] arguments) {
            return switch (method.getName()) {
                case "getGlobalRegionScheduler" -> globalScheduler;
                case "getPlayer" -> {
                    Handle handle = current.get(arguments[0]);
                    yield handle == null ? null : handle.player;
                }
                default -> unexpected(method);
            };
        }

        private Object pluginCall(Method method, Object[] arguments) {
            return switch (method.getName()) {
                case "getServer" -> server;
                case "getName" -> "spectate-flow-test";
                default -> unexpected(method);
            };
        }

        private final class Handle {
            private final UUID id;
            private final String name;
            private final boolean actorHandle;
            private final AtomicBoolean online = new AtomicBoolean(true);
            private final AtomicBoolean permission = new AtomicBoolean(true);
            private final AtomicBoolean exempt = new AtomicBoolean();
            private final AtomicBoolean teleportThrows = new AtomicBoolean();
            private final CompletableFuture<Boolean> teleport = new CompletableFuture<>();
            private final Deque<ScheduleOutcome> outcomes = new ArrayDeque<>();
            private final Deque<Pending> pending = new ArrayDeque<>();
            private final List<Component> messages = new ArrayList<>();
            private final EntityScheduler scheduler = proxy(EntityScheduler.class, this::schedulerCall);
            private final Player player = proxy(Player.class, this::playerCall);
            private int teleports;
            private int attachments;
            private Player lastSpectator;

            private Handle(UUID id, String name, boolean actorHandle) {
                this.id = id;
                this.name = name;
                this.actorHandle = actorHandle;
            }

            private void retireNext() {
                outcomes.addLast(ScheduleOutcome.RETIRE);
            }

            private void rejectNext() {
                outcomes.addLast(ScheduleOutcome.REJECT);
            }

            private void throwNext() {
                outcomes.addLast(ScheduleOutcome.THROW);
            }

            private void runOwned() {
                Pending task = pending.pollFirst();
                if (task == null) {
                    throw new AssertionError("No entity scheduler task was pending for " + name);
                }
                task.owned().run();
            }

            private void runOwnedTwice() {
                Pending task = pending.pollFirst();
                if (task == null) {
                    throw new AssertionError("No entity scheduler task was pending for " + name);
                }
                task.owned().run();
                task.owned().run();
            }

            private Object schedulerCall(Method method, Object[] arguments) {
                if (!method.getName().equals("execute")) {
                    return unexpected(method);
                }
                assertSame(plugin, arguments[0]);
                assertEquals(1L, arguments[3]);
                Runnable owned = (Runnable) arguments[1];
                Runnable retired = (Runnable) arguments[2];
                ScheduleOutcome outcome = outcomes.isEmpty() ? ScheduleOutcome.ACCEPT : outcomes.removeFirst();
                return switch (outcome) {
                    case ACCEPT -> accept(owned, retired);
                    case RETIRE -> retire(retired);
                    case REJECT -> false;
                    case THROW -> throw new IllegalStateException("entity scheduling rejected");
                };
            }

            private boolean accept(Runnable owned, Runnable retired) {
                pending.addLast(new Pending(owned, retired));
                return true;
            }

            private boolean retire(Runnable retired) {
                retired.run();
                return true;
            }

            private Object playerCall(Method method, Object[] arguments) {
                return switch (method.getName()) {
                    case "getUniqueId" -> id;
                    case "getName" -> name;
                    case "getLocation" -> new Location(null, 10.0, 64.0, -5.0);
                    case "getScheduler" -> scheduler;
                    case "isOnline" -> online.get();
                    case "hasPermission" -> permission((String) arguments[0]);
                    case "getGameMode" -> actorHandle ? GameMode.SPECTATOR : GameMode.SURVIVAL;
                    case "teleportAsync" -> teleport((Location) arguments[0]);
                    case "setSpectatorTarget" -> setSpectatorTarget((Player) arguments[0]);
                    case "sendMessage" -> sendMessage(arguments[0]);
                    default -> unexpected(method);
                };
            }

            private boolean permission(String permissionName) {
                if (actorHandle && permissionName.equals(SPECTATE_PERMISSION)) {
                    return permission.get();
                }
                return !actorHandle && permissionName.equals(SPECTATE_EXEMPT) && exempt.get();
            }

            private CompletableFuture<Boolean> teleport(Location ignored) {
                if (!actorHandle) {
                    throw new AssertionError("Only the actor may teleport");
                }
                teleports++;
                if (teleportThrows.get()) {
                    throw new IllegalStateException("teleport submission rejected");
                }
                return teleport;
            }

            private Object setSpectatorTarget(Player targetPlayer) {
                if (!actorHandle) {
                    throw new AssertionError("Only the actor may attach a spectator target");
                }
                attachments++;
                lastSpectator = targetPlayer;
                return null;
            }

            private Object sendMessage(Object value) {
                if (value instanceof Component component) {
                    messages.add(component);
                }
                return null;
            }
        }
    }

    private record Pending(Runnable owned, Runnable retired) {
    }

    private static Object unexpected(Method method) {
        throw new AssertionError("Unexpected call: " + method.getDeclaringClass().getSimpleName() + '.' + method.getName());
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, Invocation invocation) {
        return (T) Proxy.newProxyInstance(
                Thread.currentThread().getContextClassLoader(),
                new Class<?>[]{type},
                (instance, method, arguments) -> invokeProxy(instance, type, invocation, method, arguments)
        );
    }

    private static Object invokeProxy(
            Object instance,
            Class<?> type,
            Invocation invocation,
            Method method,
            Object[] arguments
    ) throws Throwable {
        if (method.getDeclaringClass() == Object.class) {
            return objectMethod(instance, type, method, arguments);
        }
        return invocation.invoke(method, arguments == null ? new Object[0] : arguments);
    }

    private static Object objectMethod(Object instance, Class<?> type, Method method, Object[] arguments) {
        return switch (method.getName()) {
            case "toString" -> type.getSimpleName() + "Proxy";
            case "hashCode" -> System.identityHashCode(instance);
            case "equals" -> instance == arguments[0];
            default -> unexpected(method);
        };
    }

    @FunctionalInterface
    private interface Invocation {
        Object invoke(Method method, Object[] arguments) throws Throwable;
    }
}
