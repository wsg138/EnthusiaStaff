package net.enthusia.staff.paper.staff;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import io.papermc.paper.threadedregions.scheduler.EntityScheduler;
import io.papermc.paper.threadedregions.scheduler.GlobalRegionScheduler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import net.kyori.adventure.text.Component;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

final class StaffToolSpectateFlowHarness {
    private static final String SPECTATE_PERMISSION = "enthusiastaff.stafftools.spectate";
    private static final String SPECTATE_EXEMPT = "enthusiastaff.stafftools.spectate-exempt";

    final UUID actorId = UUID.randomUUID();
    final UUID targetId = UUID.randomUUID();
    final Deque<Runnable> globalTasks = new ArrayDeque<>();
    final Map<UUID, Handle> current = new ConcurrentHashMap<>();
    final Set<UUID> vanished = ConcurrentHashMap.newKeySet();
    final AtomicBoolean actorSession = new AtomicBoolean(true);
    final AtomicBoolean actorAuthority = new AtomicBoolean(true);
    final AtomicBoolean failNextGlobal = new AtomicBoolean();
    final GlobalRegionScheduler globalScheduler = proxy(GlobalRegionScheduler.class, this::globalSchedulerCall);
    final Server server = proxy(Server.class, this::serverCall);
    final Plugin plugin = proxy(Plugin.class, (method, ignored) -> pluginCall(method));
    int nextEntityId = 100;
    final Handle actor = new Handle(actorId, "Actor", true);
    Handle target = new Handle(targetId, "Target", false);
    final StaffToolSpectateFlow flow;

    StaffToolSpectateFlowHarness() {
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

    void beginAndInspectTarget() {
        flow.begin(actor.player, targetId);
        runOwned(target);
    }

    void reachPreTeleportTargetHandoff() {
        beginAndInspectTarget();
        runOwned(actor);
    }

    void reachPreTeleportActorHandoff() {
        reachPreTeleportTargetHandoff();
        runOwned(target);
    }

    void reachTeleportRequest() {
        reachPreTeleportActorHandoff();
        runOwned(actor);
        assertEquals(1, actor.teleports);
    }

    void reachFinalTargetHandoff() {
        reachTeleportRequest();
        actor.teleport.complete(true);
        runOwned(actor);
    }

    void reachFinalActorHandoff() {
        reachFinalTargetHandoff();
        runOwned(target);
    }

    void finishSuccessfulFlow() {
        reachFinalActorHandoff();
        runOwned(actor);
    }

    void runOwned(Handle handle) {
        runNextGlobal();
        handle.runOwned();
    }

    void runNextGlobal() {
        Runnable task = globalTasks.pollFirst();
        if (task == null) {
            throw new AssertionError("No global scheduler task was pending");
        }
        task.run();
    }

    void disconnectTarget() {
        target.online.set(false);
        current.remove(targetId);
    }

    Handle reconnectTarget() {
        target.online.set(false);
        Handle replacement = new Handle(targetId, "Target", false);
        target = replacement;
        current.put(targetId, replacement);
        return replacement;
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
            case "getPlayer" -> playerById(arguments[0]);
            default -> unexpected(method);
        };
    }

    private Player playerById(Object id) {
        Handle handle = current.get(id);
        return handle == null ? null : handle.player;
    }

    private Object pluginCall(Method method) {
        return switch (method.getName()) {
            case "getServer" -> server;
            case "getName" -> "spectate-flow-test";
            default -> unexpected(method);
        };
    }

    enum ScheduleOutcome {
        ACCEPT,
        RETIRE,
        REJECT,
        THROW,
        RETIRE_AND_REJECT
    }

    final class Handle {
        final UUID id;
        final String name;
        final boolean actorHandle;
        final int entityId;
        final AtomicBoolean online = new AtomicBoolean(true);
        final AtomicBoolean permission = new AtomicBoolean(true);
        final AtomicBoolean exempt = new AtomicBoolean();
        final AtomicBoolean teleportThrows = new AtomicBoolean();
        final CompletableFuture<Boolean> teleport = new CompletableFuture<>();
        final Deque<ScheduleOutcome> outcomes = new ArrayDeque<>();
        final Deque<Pending> pending = new ArrayDeque<>();
        final List<Component> messages = new ArrayList<>();
        final EntityScheduler scheduler = proxy(EntityScheduler.class, this::schedulerCall);
        final Player player = proxy(Player.class, this::playerCall);
        int teleports;
        int attachments;
        Player lastSpectator;

        Handle(UUID id, String name, boolean actorHandle) {
            this.id = id;
            this.name = name;
            this.actorHandle = actorHandle;
            this.entityId = nextEntityId++;
        }

        void retireNext() {
            outcomes.addLast(ScheduleOutcome.RETIRE);
        }

        void rejectNext() {
            outcomes.addLast(ScheduleOutcome.REJECT);
        }

        void throwNext() {
            outcomes.addLast(ScheduleOutcome.THROW);
        }

        void retireAndRejectNext() {
            outcomes.addLast(ScheduleOutcome.RETIRE_AND_REJECT);
        }

        void runOwned() {
            Pending task = pending.pollFirst();
            if (task == null) {
                throw new AssertionError("No entity scheduler task was pending for " + name);
            }
            task.owned().run();
        }

        void runOwnedTwice() {
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
            return executeOutcome(outcome, owned, retired);
        }

        private Object executeOutcome(ScheduleOutcome outcome, Runnable owned, Runnable retired) {
            return switch (outcome) {
                case ACCEPT -> accept(owned, retired);
                case RETIRE -> retire(retired);
                case REJECT -> false;
                case THROW -> throw new IllegalStateException("entity scheduling rejected");
                case RETIRE_AND_REJECT -> retireAndReject(retired);
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

        private boolean retireAndReject(Runnable retired) {
            retired.run();
            return false;
        }

        private Object playerCall(Method method, Object[] arguments) {
            return switch (method.getName()) {
                case "getUniqueId", "getName", "getLocation", "getEntityId", "getScheduler", "isOnline"
                        -> playerStateCall(method);
                default -> playerActionCall(method, arguments);
            };
        }

        private Object playerStateCall(Method method) {
            return switch (method.getName()) {
                case "getUniqueId" -> id;
                case "getName" -> name;
                case "getLocation" -> new Location(null, 10.0, 64.0, -5.0);
                case "getEntityId" -> entityId;
                case "getScheduler" -> scheduler;
                case "isOnline" -> online.get();
                default -> unexpected(method);
            };
        }

        private Object playerActionCall(Method method, Object[] arguments) {
            return switch (method.getName()) {
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

        private CompletableFuture<Boolean> teleport(Location destination) {
            if (!actorHandle) {
                throw new AssertionError("Only the actor may teleport");
            }
            if (destination == null) {
                throw new AssertionError("Teleport destination must be present");
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

    record Pending(Runnable owned, Runnable retired) {
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
            return objectMethod(instance, type, method);
        }
        return invocation.invoke(method, arguments == null ? new Object[0] : arguments);
    }

    private static Object objectMethod(Object instance, Class<?> type, Method method) {
        return switch (method.getName()) {
            case "toString" -> type.getSimpleName() + "Proxy";
            case "hashCode" -> System.identityHashCode(instance);
            default -> unexpected(method);
        };
    }

    @FunctionalInterface
    private interface Invocation {
        Object invoke(Method method, Object[] arguments) throws Throwable;
    }
}
