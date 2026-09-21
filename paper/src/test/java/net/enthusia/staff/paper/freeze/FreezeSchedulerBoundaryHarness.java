package net.enthusia.staff.paper.freeze;

import io.papermc.paper.threadedregions.scheduler.EntityScheduler;
import io.papermc.paper.threadedregions.scheduler.GlobalRegionScheduler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.time.Clock;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;
import net.enthusia.staff.domain.ports.FreezeStore;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.ServicesManager;

/** Production-boundary scheduler fixture shared by freeze reconciliation tests. */
public final class FreezeSchedulerBoundaryHarness {
    private static final Logger LOGGER = Logger.getLogger(FreezeSchedulerBoundaryHarness.class.getName());

    private final Deque<Dispatch> dispatches = new ArrayDeque<>();
    private final AtomicInteger globalDispatches = new AtomicInteger();
    private final AtomicInteger entityDispatches = new AtomicInteger();
    private final Plugin plugin = proxy(Plugin.class, this::pluginCall);
    private final EntityScheduler entityScheduler = proxyWithArguments(EntityScheduler.class, this::entitySchedulerCall);
    private final Player player = proxy(Player.class, this::playerCall);
    private final GlobalRegionScheduler globalScheduler = proxyWithArguments(GlobalRegionScheduler.class, this::globalSchedulerCall);
    private final Server server = proxy(Server.class, this::serverCall);

    private ServicesManager servicesManager;
    private Optional<Dispatch> activeDispatch = Optional.empty();

    public FreezeSchedulerBoundaryHarness absent() {
        return enqueue(Dispatch.absent());
    }

    public FreezeSchedulerBoundaryHarness owned() {
        return enqueue(Dispatch.entity(EntityOutcome.OWNED));
    }

    public FreezeSchedulerBoundaryHarness retired() {
        return enqueue(Dispatch.entity(EntityOutcome.RETIRED));
    }

    public FreezeSchedulerBoundaryHarness rejected() {
        return enqueue(Dispatch.entity(EntityOutcome.REJECTED));
    }

    public FreezeSchedulerBoundaryHarness entityException() {
        return enqueue(Dispatch.entity(EntityOutcome.EXCEPTION));
    }

    public FreezeSchedulerBoundaryHarness globalException() {
        return enqueue(Dispatch.globalDispatchFailure());
    }

    public int globalDispatchCount() {
        return globalDispatches.get();
    }

    public int entityDispatchCount() {
        return entityDispatches.get();
    }

    public void exposeService(FreezeNetworkReconciler reconciler, AtomicInteger loads) {
        servicesManager = proxyWithArguments(ServicesManager.class, (method, arguments) -> {
            if (method.getName().equals("load") && arguments[0] == FreezeNetworkReconciler.class) {
                loads.incrementAndGet();
                return reconciler;
            }
            return unexpected(method);
        });
    }

    public ServicesManager servicesManager() {
        return java.util.Objects.requireNonNull(servicesManager, "servicesManager");
    }

    public FreezeNetworkReconciler reconciler(
            Clock clock,
            FreezeStore store,
            AtomicBoolean restricted
    ) {
        return new FreezeNetworkReconciler(
                clock,
                () -> store,
                Runnable::run,
                restrictions(restricted),
                FreezeNetworkReconciler.localTargetRouter(server, plugin),
                LOGGER
        );
    }

    private FreezeSchedulerBoundaryHarness enqueue(Dispatch dispatch) {
        dispatches.addLast(dispatch);
        return this;
    }

    private Object globalSchedulerCall(Method method, Object[] arguments) {
        if (!method.getName().equals("execute")) {
            return unexpected(method);
        }
        globalDispatches.incrementAndGet();
        Dispatch dispatch = nextDispatch();
        if (dispatch.globalFailure()) {
            throw new IllegalStateException("global dispatch rejected");
        }
        assertPlugin(arguments[0]);
        activeDispatch = Optional.of(dispatch);
        try {
            ((Runnable) arguments[1]).run();
        } finally {
            activeDispatch = Optional.empty();
        }
        return null;
    }

    private Object serverCall(Method method) {
        return switch (method.getName()) {
            case "getGlobalRegionScheduler" -> globalScheduler;
            case "getPlayer" -> localPlayer();
            case "getLogger" -> LOGGER;
            default -> unexpected(method);
        };
    }

    private Player localPlayer() {
        if (active().present()) {
            return player;
        }
        return null;
    }

    private Object playerCall(Method method) {
        if (method.getName().equals("getScheduler")) {
            return entityScheduler;
        }
        return unexpected(method);
    }

    private Object entitySchedulerCall(Method method, Object[] arguments) {
        if (!method.getName().equals("execute")) {
            return unexpected(method);
        }
        entityDispatches.incrementAndGet();
        assertPlugin(arguments[0]);
        if (!Long.valueOf(1L).equals(arguments[3])) {
            throw new AssertionError("Expected entity scheduler delay 1");
        }
        Runnable owned = (Runnable) arguments[1];
        Runnable retired = (Runnable) arguments[2];
        return executeEntity(active().entityOutcome(), owned, retired);
    }

    private Object pluginCall(Method method) {
        if (method.getName().equals("getName")) {
            return "freeze-boundary-test";
        }
        return unexpected(method);
    }

    private boolean executeEntity(EntityOutcome outcome, Runnable owned, Runnable retired) {
        return switch (outcome) {
            case OWNED -> runAndReturn(owned, true);
            case RETIRED -> runAndReturn(retired, true);
            case REJECTED -> false;
            case EXCEPTION -> throw new IllegalStateException("entity dispatch rejected");
        };
    }

    private Dispatch nextDispatch() {
        Dispatch dispatch = dispatches.pollFirst();
        if (dispatch == null) {
            throw new AssertionError("No scheduler dispatch was configured");
        }
        return dispatch;
    }

    private Dispatch active() {
        return activeDispatch.orElseThrow(
                () -> new AssertionError("No active global scheduler dispatch")
        );
    }

    private void assertPlugin(Object seenPlugin) {
        if (seenPlugin != plugin) {
            throw new AssertionError("Unexpected scheduler plugin");
        }
    }

    private static boolean runAndReturn(Runnable action, boolean result) {
        action.run();
        return result;
    }

    private static FreezeNetworkReconciler.RestrictionController restrictions(AtomicBoolean restricted) {
        return new FreezeNetworkReconciler.RestrictionController() {
            @Override
            public boolean restricted(UUID playerId) {
                return restricted.get();
            }

            @Override
            public void apply(UUID playerId) {
                restricted.set(true);
            }

            @Override
            public void release(UUID playerId) {
                restricted.set(false);
            }
        };
    }

    private static Object unexpected(Method method) {
        throw new AssertionError("Unexpected call: " + method.getName());
    }

    private static <T> T proxy(Class<T> type, MethodInvocation invocation) {
        return proxyWithArguments(type, (method, arguments) -> invocation.invoke(method));
    }

    private static <T> T proxyWithArguments(Class<T> type, Invocation invocation) {
        return type.cast(Proxy.newProxyInstance(
                Thread.currentThread().getContextClassLoader(),
                new Class<?>[]{type},
                (instance, method, arguments) -> invokeProxy(instance, type, invocation, method, arguments)
        ));
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
            case "equals" -> sameReference(instance, arguments[0]);
            default -> unexpected(method);
        };
    }

    private static boolean sameReference(Object expected, Object actual) {
        IdentityHashMap<Object, Boolean> references = new IdentityHashMap<>();
        references.put(expected, Boolean.TRUE);
        return references.containsKey(actual);
    }

    @FunctionalInterface
    private interface MethodInvocation {
        Object invoke(Method method) throws Throwable;
    }

    @FunctionalInterface
    private interface Invocation {
        Object invoke(Method method, Object[] arguments) throws Throwable;
    }

    private enum EntityOutcome {
        OWNED,
        RETIRED,
        REJECTED,
        EXCEPTION
    }

    private record Dispatch(boolean present, boolean globalFailure, EntityOutcome entityOutcome) {
        private static Dispatch absent() {
            return new Dispatch(false, false, EntityOutcome.OWNED);
        }

        private static Dispatch entity(EntityOutcome outcome) {
            return new Dispatch(true, false, outcome);
        }

        private static Dispatch globalDispatchFailure() {
            return new Dispatch(false, true, EntityOutcome.OWNED);
        }
    }
}
