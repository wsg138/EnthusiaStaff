package net.enthusia.staff.paper.staff;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.papermc.paper.threadedregions.scheduler.EntityScheduler;
import io.papermc.paper.threadedregions.scheduler.GlobalRegionScheduler;
import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;
import java.util.logging.Logger;
import net.enthusia.staff.domain.ports.StaffSessionStore;
import net.enthusia.staff.domain.staff.StaffSessionSnapshot;
import net.enthusia.staff.domain.staff.StaffSessionState;
import org.bukkit.NamespacedKey;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.Test;

class StaffOrphanToolRecoveryTest {
    private static final UUID PLAYER_ID = UUID.fromString("72000000-0000-0000-0000-000000000041");
    private static final Instant NOW = Instant.parse("2026-10-20T12:00:00Z");
    private static final NamespacedKey TOOL_KEY = new NamespacedKey("enthusiastaff", "staff_tool");

    @Test
    void noDurableSessionRemovesOnlyTaggedToolsIncludingOffhandAndCursor() {
        Fixture fixture = new Fixture();
        fixture.manager.recover(PLAYER_ID, null);
        fixture.workers.runNext();
        fixture.runEntityTasks();

        assertNull(fixture.contents[0]);
        assertNull(fixture.contents[39]);
        assertNull(fixture.contents[40]);
        assertNull(fixture.cursor);
        assertSame(fixture.ordinaryItem, fixture.contents[1]);
        assertFalse(fixture.manager.transitioning(PLAYER_ID));
    }

    @Test
    void inactiveRecoveryKeepsFenceUntilEntityOwnedCleanupFinishes() {
        Fixture fixture = new Fixture();
        fixture.manager.recover(PLAYER_ID, null);
        fixture.workers.runNext();

        assertTrue(fixture.manager.transitioning(PLAYER_ID));
        assertSame(fixture.tool, fixture.contents[0]);
        fixture.runEntityTasks();
        assertFalse(fixture.manager.transitioning(PLAYER_ID));
    }

    @Test
    void unavailableStorageRetainsInventoryAndRecoveryFence() {
        Fixture fixture = new Fixture();
        fixture.storageAvailable = false;
        fixture.manager.recover(PLAYER_ID, null);
        fixture.workers.runNext();
        fixture.runEntityTasks();

        assertSame(fixture.tool, fixture.contents[0]);
        assertSame(fixture.tool, fixture.cursor);
        assertTrue(fixture.manager.transitioning(PLAYER_ID));
    }

    @Test
    void failedStorageReadDoesNotTreatUnknownSessionAsInactive() {
        Fixture fixture = new Fixture();
        fixture.failRead = true;
        fixture.manager.recover(PLAYER_ID, null);
        fixture.workers.runNext();
        fixture.runEntityTasks();

        assertSame(fixture.tool, fixture.contents[0]);
        assertTrue(fixture.manager.transitioning(PLAYER_ID));
    }

    @Test
    void newActiveSessionIsNotCleanedByAnOlderInactiveRead() throws Exception {
        Fixture fixture = new Fixture();
        fixture.manager.recover(PLAYER_ID, null);
        fixture.workers.runNext();
        fixture.activeSessions().put(PLAYER_ID, fixture.session(UUID.randomUUID()));
        fixture.runEntityTasks();

        assertSame(fixture.tool, fixture.contents[0]);
        assertTrue(fixture.manager.active(PLAYER_ID));
    }

    @Test
    void verifiedClosedSnapshotToolsAreRemovedWithoutChangingSavedBytes() throws Exception {
        Fixture fixture = new Fixture();
        StaffSessionSnapshot session = fixture.startExit();
        byte[] savedBytes = session.snapshot();
        fixture.completeVerifiedExit(session);

        assertTrue(fixture.closed);
        assertTrue(fixture.manager.transitioning(PLAYER_ID));
        assertSame(fixture.tool, fixture.contents[0]);
        fixture.runEntityTasks();

        assertNull(fixture.contents[0]);
        assertNull(fixture.cursor);
        assertSame(fixture.ordinaryItem, fixture.contents[1]);
        assertArrayEquals(savedBytes, session.snapshot());
        assertFalse(fixture.manager.transitioning(PLAYER_ID));
        assertFalse(fixture.manager.active(PLAYER_ID));
    }

    @Test
    void rejectedDurableClosureRetainsSnapshotToolsForRecovery() throws Exception {
        Fixture fixture = new Fixture();
        StaffSessionSnapshot session = fixture.startExit();
        fixture.closeAccepted = false;
        fixture.completeVerifiedExit(session);
        fixture.runEntityTasks();

        assertFalse(fixture.closed);
        assertSame(fixture.tool, fixture.contents[0]);
        assertTrue(fixture.manager.transitioning(PLAYER_ID));
    }

    @Test
    void lateClosedSessionCallbackCannotRemoveANewerSessionsTools() throws Exception {
        Fixture fixture = new Fixture();
        StaffSessionSnapshot old = fixture.startExit();
        fixture.completeVerifiedExit(old);
        StaffSessionSnapshot replacement = fixture.session(UUID.randomUUID());
        fixture.activeSessions().put(PLAYER_ID, replacement);
        fixture.runEntityTasks();

        assertSame(fixture.tool, fixture.contents[0]);
        assertSame(replacement, fixture.activeSessions().get(PLAYER_ID));
    }

    @Test
    void ordinaryCursorIsPreservedEvenWhenItUsesAStaffToolMaterial() {
        Fixture fixture = new Fixture();
        fixture.cursor = fixture.ordinaryItem;
        fixture.manager.recover(PLAYER_ID, null);
        fixture.workers.runNext();
        fixture.runEntityTasks();

        assertSame(fixture.ordinaryItem, fixture.cursor);
    }

    @Test
    void retiredCleanupRemainsRetryableWithoutReplayingClosedSnapshot() throws Exception {
        Fixture fixture = new Fixture();
        StaffSessionSnapshot session = fixture.startExit();
        fixture.rejectEntity = true;
        fixture.completeVerifiedExit(session);
        fixture.runEntityTasks();

        assertTrue(fixture.closed);
        assertTrue(fixture.manager.transitioning(PLAYER_ID));
        assertTrue(fixture.manager.active(PLAYER_ID));
        assertSame(fixture.tool, fixture.contents[0]);

        fixture.rejectEntity = false;
        fixture.manager.recover(PLAYER_ID, null);
        fixture.workers.runNext();
        fixture.runEntityTasks();
        assertNull(fixture.contents[0]);
        assertFalse(fixture.manager.transitioning(PLAYER_ID));
        assertFalse(fixture.manager.active(PLAYER_ID));
    }

    @Test
    void throwingEntitySchedulerKeepsInactiveCleanupFencedAndRetryable() {
        Fixture fixture = new Fixture();
        fixture.throwEntity = true;
        fixture.manager.recover(PLAYER_ID, null);
        fixture.workers.runNext();
        fixture.runEntityTasks();

        assertTrue(fixture.manager.transitioning(PLAYER_ID));
        assertSame(fixture.tool, fixture.contents[0]);
        fixture.throwEntity = false;
        fixture.manager.recover(PLAYER_ID, null);
        fixture.workers.runNext();
        fixture.runEntityTasks();
        assertNull(fixture.contents[0]);
        assertFalse(fixture.manager.transitioning(PLAYER_ID));
    }

    @Test
    void throwingEntitySchedulerKeepsClosedCleanupFencedAndRetryable() throws Exception {
        Fixture fixture = new Fixture();
        StaffSessionSnapshot session = fixture.startExit();
        fixture.throwEntity = true;
        fixture.completeVerifiedExit(session);
        fixture.runEntityTasks();

        assertTrue(fixture.closed);
        assertTrue(fixture.manager.transitioning(PLAYER_ID));
        assertTrue(fixture.manager.active(PLAYER_ID));
        assertSame(fixture.tool, fixture.contents[0]);
        fixture.throwEntity = false;
        fixture.manager.recover(PLAYER_ID, null);
        fixture.workers.runNext();
        fixture.runEntityTasks();
        assertNull(fixture.contents[0]);
        assertFalse(fixture.manager.transitioning(PLAYER_ID));
        assertFalse(fixture.manager.active(PLAYER_ID));
    }

    @Test
    void delayedCompletionCannotOwnAReplacementRecoveryAttempt() throws Exception {
        Fixture fixture = new Fixture();
        StaffSessionSnapshot old = fixture.startExit();
        StaffModeRecoveryGate gate = fixture.field("recoveryGate", StaffModeRecoveryGate.class);
        gate.clear(PLAYER_ID);
        Object replacementAttempt = gate.beginAttempt(PLAYER_ID);
        StaffSessionSnapshot replacement = fixture.session(UUID.randomUUID());
        fixture.activeSessions().put(PLAYER_ID, replacement);

        fixture.completeVerifiedExit(old);
        fixture.runEntityTasks();

        assertTrue(fixture.closed);
        assertSame(fixture.tool, fixture.contents[0]);
        assertSame(replacement, fixture.activeSessions().get(PLAYER_ID));
        assertTrue(gate.owns(PLAYER_ID, replacementAttempt));
    }

    @Test
    void lateSchedulerFailureCannotInvalidateAReplacementRecoveryAttempt() throws Exception {
        Fixture fixture = new Fixture();
        StaffSessionSnapshot old = fixture.startExit();
        fixture.completeVerifiedExit(old);
        StaffModeRecoveryGate gate = fixture.field("recoveryGate", StaffModeRecoveryGate.class);
        gate.clear(PLAYER_ID);
        Object replacementAttempt = gate.beginAttempt(PLAYER_ID);
        StaffSessionSnapshot replacement = fixture.session(UUID.randomUUID());
        fixture.activeSessions().put(PLAYER_ID, replacement);
        fixture.throwEntity = true;

        fixture.runEntityTasks();

        assertSame(fixture.tool, fixture.contents[0]);
        assertSame(replacement, fixture.activeSessions().get(PLAYER_ID));
        assertTrue(gate.owns(PLAYER_ID, replacementAttempt));
    }

    @Test
    void exitNotificationCanBeginRecoveryWithoutLosingItsNewFence() throws Exception {
        Fixture fixture = new Fixture();
        StaffSessionSnapshot session = fixture.startExit();
        fixture.manager.setExitListener(playerId -> fixture.manager.recover(playerId, null));
        fixture.completeVerifiedExit(session);
        fixture.runEntityTasks();

        assertTrue(fixture.manager.transitioning(PLAYER_ID));
        fixture.workers.runNext();
        fixture.runEntityTasks();
        assertFalse(fixture.manager.transitioning(PLAYER_ID));
        assertFalse(fixture.manager.active(PLAYER_ID));
    }

    private static final class Fixture {
        private final QueuedWorkers workers = new QueuedWorkers();
        private final ArrayDeque<Runnable> globalTasks = new ArrayDeque<>();
        private final ArrayDeque<Runnable> entityTasks = new ArrayDeque<>();
        private final ItemStack tool = new TaggedItem(true);
        private final ItemStack ordinaryItem = new TaggedItem(false);
        private final ItemStack[] contents = new ItemStack[41];
        private ItemStack cursor = tool;
        private boolean storageAvailable = true;
        private boolean failRead;
        private boolean closeAccepted = true;
        private boolean closed;
        private boolean entityOwned;
        private boolean rejectEntity;
        private boolean throwEntity;
        private Object exitAttempt;
        private final StaffSessionStore store = proxy(StaffSessionStore.class, this::storeCall);
        private final PlayerInventory inventory = proxy(PlayerInventory.class, this::inventoryCall);
        private final EntityScheduler entity = proxy(EntityScheduler.class, (method, args) -> {
            if (method.getName().equals("execute")) {
                if (throwEntity) {
                    throw new IllegalStateException("injected entity scheduler failure");
                }
                if (rejectEntity) {
                    return false;
                }
                entityTasks.addLast((Runnable) args[1]);
                return true;
            }
            throw new AssertionError(method);
        });
        private final Player player = proxy(Player.class, this::playerCall);
        private final GlobalRegionScheduler global = proxy(GlobalRegionScheduler.class, (method, args) -> {
            globalTasks.addLast((Runnable) args[1]);
            return null;
        });
        private final PluginManager plugins = proxy(PluginManager.class, (method, args) -> null);
        private final Server server = proxy(Server.class, (method, args) -> switch (method.getName()) {
            case "getGlobalRegionScheduler" -> global;
            case "getPlayer" -> player;
            case "getPluginManager" -> plugins;
            default -> throw new AssertionError(method);
        });
        private final Plugin plugin = proxy(Plugin.class, (method, args) -> switch (method.getName()) {
            case "getName" -> "EnthusiaStaff";
            case "namespace" -> "enthusiastaff";
            case "getServer" -> server;
            case "getLogger" -> Logger.getLogger("orphan-tool-regression");
            case "getConfig" -> new YamlConfiguration();
            default -> throw new AssertionError(method);
        });
        private final StaffModeManager manager = new StaffModeManager(plugin,
                Clock.fixed(NOW, ZoneOffset.UTC), "TEST", () -> storageAvailable ? store : null,
                () -> null, workers);

        private Fixture() {
            contents[0] = tool;
            contents[1] = ordinaryItem;
            contents[39] = tool;
            contents[40] = tool;
        }

        private Object storeCall(Method method, Object[] args) {
            return switch (method.getName()) {
                case "active" -> {
                    assertEquals(PLAYER_ID, args[0], "durable absence must be checked for the affected player");
                    if (failRead) {
                        throw new IllegalStateException("injected unavailable storage");
                    }
                    yield Optional.empty();
                }
                case "completeExit" -> {
                    assertFalse(entityOwned, "durable closure must stay on the persistence worker");
                    assertSame(tool, contents[0], "cleanup must wait for durable closure");
                    closed = closeAccepted;
                    yield closeAccepted;
                }
                default -> throw new AssertionError(method);
            };
        }

        private Object inventoryCall(Method method, Object[] args) {
            assertTrue(entityOwned, "player inventory must be accessed on its entity scheduler");
            return switch (method.getName()) {
                case "getContents" -> contents.clone();
                case "setItem" -> {
                    contents[(Integer) args[0]] = (ItemStack) args[1];
                    yield null;
                }
                default -> throw new AssertionError(method);
            };
        }

        private Object playerCall(Method method, Object[] args) {
            return switch (method.getName()) {
                case "getUniqueId" -> PLAYER_ID;
                case "getScheduler" -> entity;
                case "getInventory" -> inventory;
                case "getItemOnCursor" -> cursor;
                case "setItemOnCursor" -> {
                    assertTrue(entityOwned);
                    cursor = (ItemStack) args[0];
                    yield null;
                }
                case "updateInventory", "sendMessage" -> null;
                default -> throw new AssertionError(method);
            };
        }

        private void runEntityTasks() {
            while (!globalTasks.isEmpty() || !entityTasks.isEmpty()) {
                while (!globalTasks.isEmpty()) {
                    globalTasks.removeFirst().run();
                }
                while (!entityTasks.isEmpty()) {
                    entityOwned = true;
                    try {
                        entityTasks.removeFirst().run();
                    } finally {
                        entityOwned = false;
                    }
                }
            }
        }

        private StaffSessionSnapshot session(UUID sessionId) {
            return new StaffSessionSnapshot(sessionId, PLAYER_ID, "TEST", StaffSessionState.EXITING,
                    false, 1, "a".repeat(64), new byte[]{1, 2, 3}, NOW, 1);
        }

        @SuppressWarnings("unchecked")
        private StaffSessionSnapshot startExit() throws Exception {
            StaffSessionSnapshot session = session(UUID.randomUUID());
            activeSessions().put(PLAYER_ID, session);
            field("transitions", Set.class).add(PLAYER_ID);
            exitAttempt = field("recoveryGate", StaffModeRecoveryGate.class).ensureAttempt(PLAYER_ID);
            return session;
        }

        // Fixed manager completion boundary in a synthetic unit fixture, with no runtime input.
        @SuppressWarnings("PMD.AvoidAccessibilityAlteration")
        private void completeVerifiedExit(StaffSessionSnapshot session) throws Exception {
            for (Method method : StaffModeManager.class.getDeclaredMethods()) {
                if (method.getName().equals("completeRestoration")) {
                    method.setAccessible(true);
                    method.invoke(manager, PLAYER_ID, session, store, session.checksum(), null, "test", exitAttempt);
                    return;
                }
            }
            throw new AssertionError("completion boundary not found");
        }

        @SuppressWarnings("unchecked")
        private Map<UUID, StaffSessionSnapshot> activeSessions() throws Exception {
            return field("active", Map.class);
        }

        // Test-only access to known manager state avoids exposing mutable production internals.
        @SuppressWarnings("PMD.AvoidAccessibilityAlteration")
        private <T> T field(String name, Class<T> type) throws Exception {
            var field = StaffModeManager.class.getDeclaredField(name);
            field.setAccessible(true);
            return type.cast(field.get(manager));
        }
    }

    private static final class TaggedItem extends ItemStack {
        private final boolean tagged;

        private TaggedItem(boolean tagged) {
            super();
            this.tagged = tagged;
        }

        @Override public boolean hasItemMeta() { return tagged; }

        @Override public Material getType() { return Material.COMPASS; }

        @Override public ItemMeta getItemMeta() {
            PersistentDataContainer tags = proxy(PersistentDataContainer.class, (method, args) ->
                    method.getName().equals("has") && args.length == 2
                            && TOOL_KEY.equals(args[0]) && args[1] == PersistentDataType.STRING);
            return proxy(ItemMeta.class, (method, args) -> method.getName().equals("getPersistentDataContainer")
                    ? tags : defaultValue(method.getReturnType()));
        }
    }

    private static final class QueuedWorkers extends AbstractExecutorService {
        private final ArrayDeque<Runnable> queued = new ArrayDeque<>();
        @Override public void execute(Runnable command) { queued.addLast(command); }
        private void runNext() { queued.removeFirst().run(); }
        @Override public void shutdown() { }
        @Override public List<Runnable> shutdownNow() { return List.of(); }
        @Override public boolean isShutdown() { return false; }
        @Override public boolean isTerminated() { return false; }
        @Override public boolean awaitTermination(long timeout, TimeUnit unit) { return false; }
    }

    // Paper/JUnit is not J2EE; the interface's defining loader guarantees proxy visibility.
    @SuppressWarnings("PMD.UseProperClassLoader")
    private static <T> T proxy(Class<T> type, BiFunction<Method, Object[], Object> call) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},
                (instance, method, args) -> call.apply(method, args)));
    }

    private static Object defaultValue(Class<?> type) {
        return type.isPrimitive() && type != void.class ? Array.get(Array.newInstance(type, 1), 0) : null;
    }
}
