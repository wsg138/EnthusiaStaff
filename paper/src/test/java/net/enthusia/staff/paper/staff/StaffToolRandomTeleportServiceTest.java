package net.enthusia.staff.paper.staff;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

final class StaffToolRandomTeleportServiceTest {
    private static final UUID ACTOR_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID FIRST_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID SECOND_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");

    @Test
    void onlyEligibleTargetIsRevalidatedAndTeleportedFromItsCurrentLocation() {
        Harness harness = new Harness();
        TestTarget rejected = harness.addTarget(FIRST_ID, "rejected", 10.0, false);
        TestTarget accepted = harness.addTarget(SECOND_ID, "accepted", 20.0, true);
        harness.teleportFuture.set(CompletableFuture.completedFuture(true));

        harness.startCollection();
        assertEquals(1, harness.platform.pendingGlobal());

        harness.platform.runNextGlobal();
        assertEquals(1, harness.platform.pendingGlobal());
        harness.platform.runNextGlobal();

        assertEquals(1, harness.teleports.size());
        assertEquals(20.0, harness.teleports.getFirst().getX());
        assertEquals(0, rejected.locationReads().get());
        assertEquals(1, accepted.locationReads().get());
        harness.platform.runNextGlobal();
        assertEquals(0, harness.platform.pendingGlobal());
    }

    @Test
    void targetBecomingInvalidAfterDiscoveryRetriesTheNextCandidate() {
        Harness harness = new Harness();
        TestTarget stale = harness.addTarget(FIRST_ID, "stale", 10.0, true);
        TestTarget fallback = harness.addTarget(SECOND_ID, "fallback", 20.0, true);
        harness.teleportFuture.set(CompletableFuture.completedFuture(true));

        harness.startCollection();
        harness.setEligible(stale, false);
        harness.platform.runNextGlobal();
        assertEquals(1, harness.platform.pendingGlobal());

        harness.platform.runNextGlobal();
        harness.platform.runNextGlobal();

        assertEquals(1, harness.teleports.size());
        assertEquals(20.0, harness.teleports.getFirst().getX());
        assertEquals(0, stale.locationReads().get());
        assertEquals(1, fallback.locationReads().get());
    }

    @Test
    void disconnectedCandidateRetriesAnotherCandidateSafely() {
        Harness harness = new Harness();
        TestTarget disconnected = harness.addTarget(FIRST_ID, "gone", 10.0, true);
        harness.addTarget(SECOND_ID, "fallback", 20.0, true);
        harness.teleportFuture.set(CompletableFuture.completedFuture(true));

        harness.startCollection();
        harness.platform.disconnect(disconnected.id());
        harness.platform.runNextGlobal();
        assertEquals(1, harness.platform.pendingGlobal());

        harness.platform.runNextGlobal();
        harness.platform.runNextGlobal();

        assertEquals(20.0, harness.teleports.getFirst().getX());
        assertEquals(0, disconnected.locationReads().get());
    }

    @Test
    void sameUuidReconnectReResolvesPlayerAndDoesNotReuseStaleLocation() {
        Harness harness = new Harness();
        TestTarget oldPlayer = harness.addTarget(FIRST_ID, "old", 10.0, true);

        harness.startCollection();
        TestTarget reconnected = harness.reconnect(FIRST_ID, "new", 44.0, true);
        harness.teleportFuture.set(CompletableFuture.completedFuture(true));
        harness.platform.runNextGlobal();
        harness.platform.runNextGlobal();

        assertEquals(1, harness.teleports.size());
        assertEquals(44.0, harness.teleports.getFirst().getX());
        assertEquals(0, oldPlayer.locationReads().get());
        assertEquals(1, reconnected.locationReads().get());
        assertSame(reconnected.player(), harness.platform.lastResolved(FIRST_ID));
    }

    @Test
    void finalSchedulerRetirementRejectionExceptionAndDuplicateCallbacksRetryOnce() {
        for (EntityBehavior behavior : List.of(
                EntityBehavior.RETIRE,
                EntityBehavior.REJECT,
                EntityBehavior.THROW,
                EntityBehavior.RETIRE_AND_REJECT,
                EntityBehavior.RETIRE_THEN_ACTION
        )) {
            Harness harness = new Harness();
            TestTarget first = harness.addTarget(FIRST_ID, "first", 10.0, true);
            harness.addTarget(SECOND_ID, "second", 20.0, true);
            harness.teleportFuture.set(CompletableFuture.completedFuture(true));
            harness.startCollection();

            harness.platform.setBehavior(first.player(), behavior);
            harness.platform.runNextGlobal();
            assertEquals(1, harness.platform.pendingGlobal(), behavior.name());

            harness.platform.runNextGlobal();
            harness.platform.runNextGlobal();
            assertEquals(1, harness.teleports.size(), behavior.name());
            assertEquals(20.0, harness.teleports.getFirst().getX(), behavior.name());
            assertEquals(0, first.locationReads().get(), behavior.name());
        }
    }

    @Test
    void collectionSchedulerFailuresSettleCandidateExactlyOnce() {
        for (EntityBehavior behavior : List.of(
                EntityBehavior.RETIRE,
                EntityBehavior.REJECT,
                EntityBehavior.THROW,
                EntityBehavior.RETIRE_AND_REJECT,
                EntityBehavior.RETIRE_THEN_ACTION
        )) {
            Harness harness = new Harness();
            TestTarget target = harness.addTarget(FIRST_ID, "target", 10.0, true);
            harness.platform.setBehavior(target.player(), behavior);

            harness.startCollection();
            assertEquals(1, harness.platform.pendingGlobal(), behavior.name());
            assertEquals(0, harness.eligibilityReads.get(), behavior.name());

            harness.platform.runNextGlobal();
            assertEquals(1, harness.messages.size(), behavior.name());
            assertEquals(0, harness.platform.pendingGlobal(), behavior.name());
        }
    }

    @Test
    void actorAuthorityIsRevalidatedImmediatelyBeforeTeleport() {
        Harness harness = new Harness();
        harness.addTarget(FIRST_ID, "target", 10.0, true);

        harness.startCollection();
        harness.platform.runNextGlobal();
        harness.authorized.set(false);
        harness.platform.runNextGlobal();

        assertTrue(harness.teleports.isEmpty());
        assertEquals(1, harness.messages.size());
        assertEquals(0, harness.platform.pendingGlobal());
    }

    @Test
    void exhaustionReportsCleanlyWhenNoCandidateRemainsValid() {
        Harness harness = new Harness();
        harness.addTarget(FIRST_ID, "invalid", 10.0, false);

        harness.startCollection();
        assertEquals(1, harness.platform.pendingGlobal());
        harness.platform.runNextGlobal();

        assertEquals(1, harness.messages.size());
        assertTrue(harness.teleports.isEmpty());
        assertEquals(0, harness.platform.pendingGlobal());
    }

    @Test
    void teleportFailureDoesNotRetryOrLeaveStaleWork() {
        assertTeleportCompletionSettles(false, false);
    }

    @Test
    void teleportCancellationDoesNotRetryOrLeaveStaleWork() {
        assertTeleportCompletionSettles(false, true);
    }

    private static void assertTeleportCompletionSettles(boolean result, boolean cancel) {
        Harness harness = new Harness();
        harness.addTarget(FIRST_ID, "target", 10.0, true);
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        harness.teleportFuture.set(future);

        harness.startCollection();
        harness.platform.runNextGlobal();
        harness.platform.runNextGlobal();
        assertEquals(1, harness.teleports.size());
        assertEquals(0, harness.platform.pendingGlobal());

        if (cancel) {
            assertTrue(future.cancel(false));
        } else {
            assertTrue(future.complete(result));
        }
        assertEquals(1, harness.platform.pendingGlobal());

        harness.platform.runNextGlobal();
        assertEquals(1, harness.messages.size());
        assertEquals(0, harness.platform.pendingGlobal());
        assertEquals(1, harness.teleports.size());
    }

    private static final class Harness {
        private final FakePlatform platform = new FakePlatform();
        private final IdentityHashMap<Player, Boolean> eligibility = new IdentityHashMap<>();
        private final AtomicBoolean authorized = new AtomicBoolean(true);
        private final AtomicInteger eligibilityReads = new AtomicInteger();
        private final AtomicReference<CompletableFuture<Boolean>> teleportFuture =
                new AtomicReference<>(new CompletableFuture<>());
        private final List<Location> teleports = new ArrayList<>();
        private final List<Object> messages = new ArrayList<>();
        private final Player actor = actor();
        private final StaffToolRandomTeleportService service = new StaffToolRandomTeleportService(
                platform,
                "test",
                ignored -> true,
                this::eligible,
                ignored -> {
                    platform.requireEntityOwner();
                    return authorized.get();
                },
                ignored -> {
                }
        );

        private Harness() {
            platform.setCurrent(ACTOR_ID, actor);
        }

        private TestTarget addTarget(UUID id, String name, double x, boolean eligible) {
            TestTarget target = target(id, name, x);
            eligibility.put(target.player(), eligible);
            platform.addOnline(target);
            return target;
        }

        private TestTarget reconnect(UUID id, String name, double x, boolean eligible) {
            TestTarget target = target(id, name, x);
            eligibility.put(target.player(), eligible);
            platform.setCurrent(id, target.player());
            return target;
        }

        private void setEligible(TestTarget target, boolean value) {
            eligibility.put(target.player(), value);
        }

        private void startCollection() {
            service.begin(actor);
            assertEquals(1, platform.pendingGlobal());
            platform.runNextGlobal();
        }

        private boolean eligible(UUID actorId, Player target) {
            platform.requireEntityOwner();
            assertEquals(ACTOR_ID, actorId);
            eligibilityReads.incrementAndGet();
            return Boolean.TRUE.equals(eligibility.get(target));
        }

        private Player actor() {
            return proxy(Player.class, (method, arguments) -> switch (method.getName()) {
                case "getUniqueId" -> ACTOR_ID;
                case "teleportAsync" -> {
                    platform.requireEntityOwner();
                    teleports.add((Location) arguments[0]);
                    yield teleportFuture.get();
                }
                case "sendMessage" -> {
                    platform.requireEntityOwner();
                    messages.add(arguments[0]);
                    yield null;
                }
                default -> unexpected(method);
            });
        }

        private TestTarget target(UUID id, String name, double x) {
            AtomicInteger locationReads = new AtomicInteger();
            Location location = new Location(null, x, 64.0, 0.0);
            Player player = proxy(Player.class, (method, arguments) -> switch (method.getName()) {
                case "getUniqueId" -> {
                    platform.requireEntityOwner();
                    yield id;
                }
                case "getName" -> {
                    platform.requireEntityOwner();
                    yield name;
                }
                case "getLocation" -> {
                    platform.requireEntityOwner();
                    locationReads.incrementAndGet();
                    yield location;
                }
                default -> unexpected(method);
            });
            return new TestTarget(id, player, locationReads);
        }
    }

    private static final class FakePlatform implements StaffToolRandomTeleportService.Platform {
        private final Deque<Runnable> globalTasks = new ArrayDeque<>();
        private final List<Player> online = new ArrayList<>();
        private final Map<UUID, Player> current = new HashMap<>();
        private final Map<UUID, Player> lastResolved = new HashMap<>();
        private final IdentityHashMap<Player, EntityBehavior> behaviors = new IdentityHashMap<>();
        private boolean entityOwner;

        @Override
        public Collection<? extends Player> onlinePlayers() {
            assertFalse(entityOwner);
            return List.copyOf(online);
        }

        @Override
        public Player player(UUID playerId) {
            assertFalse(entityOwner);
            Player player = current.get(playerId);
            lastResolved.put(playerId, player);
            return player;
        }

        @Override
        public void executeGlobal(Runnable operation) {
            globalTasks.addLast(operation);
        }

        @Override
        public boolean executeEntity(Player player, Runnable operation, Runnable retired) {
            return switch (behaviors.getOrDefault(player, EntityBehavior.NORMAL)) {
                case NORMAL -> runOwned(operation, true);
                case RETIRE -> runOwned(retired, true);
                case REJECT -> false;
                case THROW -> throw new IllegalStateException("scheduler rejected");
                case RETIRE_AND_REJECT -> runOwned(retired, false);
                case RETIRE_THEN_ACTION -> {
                    runOwned(retired);
                    yield runOwned(operation, true);
                }
            };
        }

        private void addOnline(TestTarget target) {
            online.add(target.player());
            current.put(target.id(), target.player());
        }

        private void setCurrent(UUID id, Player player) {
            current.put(id, player);
        }

        private void disconnect(UUID id) {
            current.remove(id);
        }

        private void setBehavior(Player player, EntityBehavior behavior) {
            behaviors.put(player, behavior);
        }

        private Player lastResolved(UUID id) {
            return lastResolved.get(id);
        }

        private int pendingGlobal() {
            return globalTasks.size();
        }

        private void runNextGlobal() {
            assertFalse(globalTasks.isEmpty(), "No queued global task");
            globalTasks.removeFirst().run();
        }

        private boolean runOwned(Runnable operation, boolean result) {
            runOwned(operation);
            return result;
        }

        private void runOwned(Runnable operation) {
            boolean previous = entityOwner;
            entityOwner = true;
            try {
                operation.run();
            } finally {
                entityOwner = previous;
            }
        }

        private void requireEntityOwner() {
            assertTrue(entityOwner, "Player state was read outside entity ownership");
        }
    }

    private enum EntityBehavior {
        NORMAL,
        RETIRE,
        REJECT,
        THROW,
        RETIRE_AND_REJECT,
        RETIRE_THEN_ACTION
    }

    private record TestTarget(UUID id, Player player, AtomicInteger locationReads) {
    }

    private static <T> T proxy(Class<T> type, Invocation invocation) {
        return type.cast(Proxy.newProxyInstance(
                Thread.currentThread().getContextClassLoader(),
                new Class<?>[]{type},
                (instance, method, arguments) -> invocation.invoke(
                        method,
                        arguments == null ? new Object[0] : arguments
                )
        ));
    }

    private static Object unexpected(Method method) {
        throw new AssertionError("Unexpected call: " + method.getName());
    }

    @FunctionalInterface
    private interface Invocation {
        Object invoke(Method method, Object[] arguments) throws Throwable;
    }
}
