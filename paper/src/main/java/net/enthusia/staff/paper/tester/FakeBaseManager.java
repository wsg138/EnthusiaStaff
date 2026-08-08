package net.enthusia.staff.paper.tester;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import java.util.logging.Level;
import net.enthusia.staff.domain.ports.FakeBaseAuditStore;
import net.enthusia.staff.domain.tester.FakeBaseAuditAction;
import net.enthusia.staff.domain.tester.FakeBaseAuditEvent;
import net.enthusia.staff.paper.staff.StaffModeManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.plugin.java.JavaPlugin;

/** Bounded client-only fake-base runtime. This class never writes a world block. */
public final class FakeBaseManager implements Listener, AutoCloseable {
    static final String PERMISSION = "enthusiastaff.cheattester.fake-base";
    static final String MANAGE_ANY_PERMISSION = "enthusiastaff.cheattester.fake-base.manage-any";
    static final Duration LIFETIME = Duration.ofMinutes(5);
    static final Duration WARNING_LEAD = Duration.ofMinutes(1);
    static final double MAX_DISTANCE = 48.0D;
    static final int MAX_ACTIVE_GLOBAL = 8;
    static final int MAX_ACTIVE_PER_STAFF = 2;

    private final JavaPlugin plugin;
    private final Clock clock;
    private final String serverId;
    private final StaffModeManager staffMode;
    private final Supplier<FakeBaseAuditStore> auditStore;
    private final ExecutorService workers;
    private final FakeBaseTemplate template = FakeBaseTemplate.standard();
    private final FakeBasePlacementPlanner planner = new FakeBasePlacementPlanner();
    private final FakeBaseRenderer renderer;
    private final Map<UUID, FakeBaseOperation> activeByTarget = new ConcurrentHashMap<>();
    private final Object registryLock = new Object();
    private final AtomicBoolean closed = new AtomicBoolean();

    public FakeBaseManager(
            JavaPlugin plugin,
            Clock clock,
            String serverId,
            StaffModeManager staffMode,
            Supplier<FakeBaseAuditStore> auditStore,
            ExecutorService workers
    ) {
        this.plugin = java.util.Objects.requireNonNull(plugin, "plugin");
        this.clock = java.util.Objects.requireNonNull(clock, "clock");
        this.serverId = CheatTesterRuntimeSupport.requireServerId(serverId);
        this.staffMode = java.util.Objects.requireNonNull(staffMode, "staffMode");
        this.auditStore = java.util.Objects.requireNonNull(auditStore, "auditStore");
        this.workers = java.util.Objects.requireNonNull(workers, "workers");
        this.renderer = new FakeBaseRenderer(plugin, template);
    }

    boolean authorized(Player staff) {
        return staff != null && !closed.get() && staffMode.active(staff.getUniqueId()) && staff.hasPermission(PERMISSION);
    }

    void create(Player staff, Player target) {
        if (!authorized(staff) || target == null || !target.isOnline()) {
            return;
        }
        if (loadedAuditStore() == null) {
            staff.sendMessage(Component.text("Fake-base audit storage is unavailable; nothing was shown.", NamedTextColor.RED));
            return;
        }
        if (activeByTarget.containsKey(target.getUniqueId())) {
            staff.sendMessage(Component.text("That player already has an active fake base.", NamedTextColor.RED));
            return;
        }
        if (activeByTarget.size() >= MAX_ACTIVE_GLOBAL || activeForStaff(staff.getUniqueId()) >= MAX_ACTIVE_PER_STAFF) {
            staff.sendMessage(Component.text("The bounded fake-base concurrency limit is reached.", NamedTextColor.RED));
            return;
        }
        UUID staffId = staff.getUniqueId();
        boolean scheduled = target.getScheduler().execute(
                plugin,
                () -> prepareCreate(staffId, target),
                () -> message(staffId, Component.text("Target retired before fake-base preparation.")),
                1L
        );
        if (!scheduled) {
            staff.sendMessage(Component.text("Target could not be scheduled for fake-base preparation.", NamedTextColor.RED));
        }
    }

    void extend(Player staff, Player target) {
        FakeBaseOperation operation = controllable(staff, target);
        if (operation == null) {
            return;
        }
        auditThen(
                operation,
                staff.getUniqueId(),
                FakeBaseAuditAction.EXTENDED,
                "ACCEPTED",
                "STAFF_EXTEND",
                () -> {
                    if (operation.extend(clock.instant(), LIFETIME)) {
                        message(staff.getUniqueId(), controls(
                                "Fake base extended for 5 minutes.", target.getName(), NamedTextColor.GREEN));
                    }
                }
        );
    }

    void teleport(Player staff, Player target) {
        FakeBaseOperation operation = controllable(staff, target);
        if (operation == null) {
            return;
        }
        auditThen(
                operation,
                staff.getUniqueId(),
                FakeBaseAuditAction.TELEPORTED,
                "ACCEPTED",
                "STAFF_TELEPORT",
                () -> onEntity(staff.getUniqueId(), current -> teleportViewer(current, operation))
        );
    }

    void clear(Player staff, Player target) {
        FakeBaseOperation operation = controllable(staff, target);
        if (operation != null) {
            closeOperation(operation, staff.getUniqueId(), "STAFF_CLEAR");
        }
    }

    List<String> statusLines(Player staff) {
        if (!authorized(staff)) {
            return List.of();
        }
        boolean includeAll = staff.hasPermission(MANAGE_ANY_PERMISSION);
        Instant now = clock.instant();
        return activeByTarget.values().stream()
                .filter(FakeBaseOperation::open)
                .filter(operation -> includeAll || operation.staffId.equals(staff.getUniqueId()))
                .map(operation -> operation.targetId + " | " + operation.remainingSeconds(now) + "s remaining")
                .sorted()
                .toList();
    }

    private void prepareCreate(UUID staffId, Player target) {
        if (closed.get() || !target.isOnline() || !staffMode.active(staffId)) {
            return;
        }
        World world = target.getWorld();
        FakeBasePlacementPlanner.BlockView blocks = new WorldBlockView(world);
        var placement = planner.find(
                target.getLocation().getBlockX(),
                target.getLocation().getBlockY(),
                target.getLocation().getBlockZ(),
                blocks,
                template
        );
        if (placement.isEmpty()) {
            UUID operationId = UUID.randomUUID();
            auditBestEffort(event(
                    operationId,
                    staffId,
                    target.getUniqueId(),
                    FakeBaseAuditAction.CREATE_REJECTED,
                    "REJECTED",
                    "NO_SAFE_PLACEMENT"
            ));
            message(staffId, Component.text(
                    "No loaded, conflict-free fake-base placement exists near that player.",
                    NamedTextColor.RED
            ));
            return;
        }
        Instant now = clock.instant();
        FakeBaseOperation operation = new FakeBaseOperation(
                UUID.randomUUID(),
                staffId,
                target.getUniqueId(),
                world.getUID(),
                placement.orElseThrow(),
                now,
                LIFETIME
        );
        if (!register(operation)) {
            message(staffId, Component.text("Fake-base target or concurrency limit is already occupied.", NamedTextColor.RED));
            return;
        }
        auditThen(
                operation,
                staffId,
                FakeBaseAuditAction.CREATED,
                "ACCEPTED",
                "CREATE",
                () -> onEntity(operation.targetId, current -> activate(current, operation))
        );
    }

    private boolean register(FakeBaseOperation operation) {
        synchronized (registryLock) {
            if (closed.get() || activeByTarget.containsKey(operation.targetId)
                    || activeByTarget.size() >= MAX_ACTIVE_GLOBAL
                    || activeForStaff(operation.staffId) >= MAX_ACTIVE_PER_STAFF) {
                return false;
            }
            activeByTarget.put(operation.targetId, operation);
            return true;
        }
    }

    private void activate(Player target, FakeBaseOperation operation) {
        if (!current(operation) || !target.isOnline() || !target.getWorld().getUID().equals(operation.worldId)) {
            closeOperation(operation, operation.staffId, "TARGET_UNAVAILABLE");
            return;
        }
        WorldBlockView blocks = new WorldBlockView(target.getWorld());
        if (!planner.safe(operation.anchor, blocks, template)) {
            auditBestEffort(event(
                    operation.operationId,
                    operation.staffId,
                    operation.targetId,
                    FakeBaseAuditAction.CREATE_REJECTED,
                    "REJECTED",
                    "PLACEMENT_CHANGED"
            ));
            closeWithoutClear(operation);
            message(operation.staffId, Component.text(
                    "Real blocks changed before rendering; fake base was cancelled safely.",
                    NamedTextColor.RED
            ));
            return;
        }
        if (!operation.addViewerIfOpen(target.getUniqueId())) {
            return;
        }
        renderer.show(target, operation.worldId, operation.anchor);
        auditBestEffort(event(
                operation.operationId,
                operation.staffId,
                operation.targetId,
                FakeBaseAuditAction.CREATED,
                "COMMITTED",
                "VIRTUAL_RENDERED"
        ));
        operation.setLifecycleTask(target.getScheduler().runAtFixedRate(
                plugin,
                ignored -> tick(target, operation),
                () -> closeOperation(operation, operation.staffId, "TARGET_RETIRED"),
                20L,
                20L
        ));
        message(operation.staffId, controls(
                "Fake base shown to " + target.getName() + " for 5 minutes.",
                target.getName(),
                NamedTextColor.AQUA
        ));
    }

    private void tick(Player target, FakeBaseOperation operation) {
        if (!current(operation)) {
            operation.cancelTask();
            return;
        }
        if (!staffMode.active(operation.staffId)) {
            closeOperation(operation, operation.staffId, "STAFF_MODE_EXITED");
            return;
        }
        if (!target.isOnline() || !target.getWorld().getUID().equals(operation.worldId)) {
            closeOperation(operation, operation.staffId, "WORLD_OR_SERVER_CHANGED");
            return;
        }
        if (distanceSquared(target.getLocation(), operation.anchor) > MAX_DISTANCE * MAX_DISTANCE) {
            closeOperation(operation, operation.staffId, "DISTANCE_LIMIT");
            return;
        }
        Instant now = clock.instant();
        if (operation.expired(now)) {
            closeOperation(operation, operation.staffId, "TIMEOUT");
            return;
        }
        if (operation.markWarningIfDue(now, WARNING_LEAD)) {
            auditBestEffort(event(
                    operation.operationId,
                    operation.staffId,
                    operation.targetId,
                    FakeBaseAuditAction.WARNING_SENT,
                    "COMMITTED",
                    "FOUR_MINUTE_WARNING"
            ));
            message(operation.staffId, controls(
                    "Fake base expires in about 1 minute.",
                    target.getName(),
                    NamedTextColor.YELLOW
            ));
        }
    }

    private void teleportViewer(Player staff, FakeBaseOperation operation) {
        if (!current(operation) || !authorized(staff)) {
            return;
        }
        World world = plugin.getServer().getWorld(operation.worldId);
        if (world == null) {
            staff.sendMessage(Component.text("The fake-base world is no longer available.", NamedTextColor.RED));
            return;
        }
        Location destination = new Location(
                world,
                operation.anchor.x() + 0.5D,
                operation.anchor.y(),
                operation.anchor.z() + 0.5D
        );
        staff.teleportAsync(destination).whenComplete((moved, failure) -> onEntity(staff.getUniqueId(), current -> {
            if (failure != null || !Boolean.TRUE.equals(moved) || !current(operation)
                    || !current.getWorld().getUID().equals(operation.worldId)) {
                current.sendMessage(Component.text("Fake-base teleport failed safely.", NamedTextColor.RED));
                return;
            }
            if (operation.addViewerIfOpen(current.getUniqueId())) {
                renderer.show(current, operation.worldId, operation.anchor);
                current.sendMessage(Component.text("Viewing the target's client-only fake base.", NamedTextColor.AQUA));
            }
        }));
    }

    private FakeBaseOperation controllable(Player staff, Player target) {
        if (!authorized(staff) || target == null) {
            return null;
        }
        FakeBaseOperation operation = activeByTarget.get(target.getUniqueId());
        if (operation == null || !operation.open()) {
            staff.sendMessage(Component.text("That player has no active fake base.", NamedTextColor.RED));
            return null;
        }
        if (!operation.staffId.equals(staff.getUniqueId()) && !staff.hasPermission(MANAGE_ANY_PERMISSION)) {
            staff.sendMessage(Component.text("You do not control that fake-base operation.", NamedTextColor.RED));
            return null;
        }
        return operation;
    }

    private void closeOperation(FakeBaseOperation operation, UUID actorId, String reason) {
        if (!operation.close()) {
            return;
        }
        unregister(operation);
        for (UUID viewerId : operation.viewersSnapshot()) {
            onEntity(viewerId, viewer -> renderer.clear(viewer, operation.worldId, operation.anchor));
        }
        auditBestEffort(event(
                operation.operationId,
                actorId,
                operation.targetId,
                FakeBaseAuditAction.CLEARED,
                "COMMITTED",
                reason
        ));
        if (!closed.get()) {
            message(operation.staffId, Component.text("Fake base cleared: " + reason, NamedTextColor.GRAY));
        }
    }

    private void closeWithoutClear(FakeBaseOperation operation) {
        if (operation.close()) {
            unregister(operation);
        }
    }

    private void unregister(FakeBaseOperation operation) {
        synchronized (registryLock) {
            activeByTarget.remove(operation.targetId, operation);
        }
    }

    private void auditThen(
            FakeBaseOperation operation,
            UUID actorId,
            FakeBaseAuditAction action,
            String outcome,
            String reason,
            Runnable success
    ) {
        FakeBaseAuditStore loaded = loadedAuditStore();
        if (loaded == null) {
            if (action == FakeBaseAuditAction.CREATED) {
                closeWithoutClear(operation);
            }
            message(actorId, Component.text("Fake-base audit storage is unavailable; action rejected.", NamedTextColor.RED));
            return;
        }
        FakeBaseAuditEvent event = event(operation.operationId, actorId, operation.targetId, action, outcome, reason);
        if (!submit(() -> {
            try {
                loaded.record(event);
                success.run();
            } catch (RuntimeException exception) {
                plugin.getLogger().log(Level.SEVERE, "Fake-base durable audit failed", exception);
                if (action == FakeBaseAuditAction.CREATED) {
                    closeWithoutClear(operation);
                }
                message(actorId, Component.text("Fake-base audit failed; action was not applied.", NamedTextColor.RED));
            }
        })) {
            if (action == FakeBaseAuditAction.CREATED) {
                closeWithoutClear(operation);
            }
            message(actorId, Component.text("The bounded work queue is full; fake-base action rejected.", NamedTextColor.RED));
        }
    }

    private void auditBestEffort(FakeBaseAuditEvent event) {
        FakeBaseAuditStore loaded = loadedAuditStore();
        if (loaded == null || !submit(() -> {
            try {
                loaded.record(event);
            } catch (RuntimeException exception) {
                plugin.getLogger().log(Level.SEVERE, "Fake-base cleanup/lifecycle audit failed", exception);
            }
        })) {
            plugin.getLogger().warning("Fake-base lifecycle audit could not be queued; safety cleanup remains authoritative");
        }
    }

    private FakeBaseAuditStore loadedAuditStore() {
        try {
            return auditStore.get();
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.SEVERE, "Fake-base audit storage lookup failed", exception);
            return null;
        }
    }

    private FakeBaseAuditEvent event(
            UUID operationId,
            UUID actorId,
            UUID targetId,
            FakeBaseAuditAction action,
            String outcome,
            String reason
    ) {
        return new FakeBaseAuditEvent(
                UUID.randomUUID(), operationId, serverId, actorId, targetId, action, outcome, reason, clock.instant()
        );
    }

    private boolean submit(Runnable operation) {
        if (workers.isShutdown()) {
            return false;
        }
        try {
            workers.execute(operation);
            return true;
        } catch (RejectedExecutionException exception) {
            return false;
        }
    }

    private void onEntity(UUID playerId, java.util.function.Consumer<Player> operation) {
        try {
            plugin.getServer().getGlobalRegionScheduler().execute(plugin, () -> {
                Player player = plugin.getServer().getPlayer(playerId);
                if (player != null && player.isOnline()) {
                    player.getScheduler().execute(plugin, () -> operation.accept(player), null, 1L);
                }
            });
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.FINE, "Fake-base entity scheduling retired during shutdown", exception);
        }
    }

    private void message(UUID playerId, Component message) {
        onEntity(playerId, player -> player.sendMessage(message));
    }

    private int activeForStaff(UUID staffId) {
        return (int) activeByTarget.values().stream()
                .filter(FakeBaseOperation::open)
                .filter(operation -> operation.staffId.equals(staffId))
                .count();
    }

    private boolean current(FakeBaseOperation operation) {
        return !closed.get() && operation.open() && activeByTarget.get(operation.targetId) == operation;
    }

    private static double distanceSquared(Location location, FakeBasePlacementPlanner.Anchor anchor) {
        double dx = location.getX() - (anchor.x() + 0.5D);
        double dy = location.getY() - anchor.y();
        double dz = location.getZ() - (anchor.z() + 0.5D);
        return dx * dx + dy * dy + dz * dz;
    }

    private static Component controls(String prefix, String targetName, NamedTextColor color) {
        return Component.text(prefix + " ", color)
                .append(Component.text("[Extend]", NamedTextColor.GREEN)
                        .clickEvent(ClickEvent.runCommand("/cheattester base extend " + targetName)))
                .append(Component.space())
                .append(Component.text("[Clear]", NamedTextColor.RED)
                        .clickEvent(ClickEvent.runCommand("/cheattester base clear " + targetName)))
                .append(Component.space())
                .append(Component.text("[Teleport]", NamedTextColor.AQUA)
                        .clickEvent(ClickEvent.runCommand("/cheattester base teleport " + targetName)));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        FakeBaseOperation targetOperation = activeByTarget.get(playerId);
        if (targetOperation != null) {
            closeOperation(targetOperation, targetOperation.staffId, "TARGET_DISCONNECTED");
        }
        for (FakeBaseOperation operation : List.copyOf(activeByTarget.values())) {
            if (operation.staffId.equals(playerId)) {
                closeOperation(operation, operation.staffId, "CONTROLLING_STAFF_DISCONNECTED");
            } else {
                operation.removeViewer(playerId);
            }
        }
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        FakeBaseOperation targetOperation = activeByTarget.get(playerId);
        if (targetOperation != null) {
            closeOperation(targetOperation, targetOperation.staffId, "TARGET_WORLD_CHANGED");
        }
        for (FakeBaseOperation operation : activeByTarget.values()) {
            operation.removeViewer(playerId);
        }
    }

    @EventHandler
    public void onPluginDisable(PluginDisableEvent event) {
        if (event.getPlugin() == plugin) {
            close();
        }
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        for (FakeBaseOperation operation : List.copyOf(activeByTarget.values())) {
            closeOperation(operation, operation.staffId, "PLUGIN_DISABLE_OR_RELOAD");
        }
        synchronized (registryLock) {
            activeByTarget.clear();
        }
    }

    private static final class WorldBlockView implements FakeBasePlacementPlanner.BlockView {
        private final World world;

        private WorldBlockView(World world) {
            this.world = world;
        }

        @Override
        public int minHeight() {
            return world.getMinHeight();
        }

        @Override
        public int maxHeight() {
            return world.getMaxHeight();
        }

        @Override
        public boolean isChunkLoaded(int chunkX, int chunkZ) {
            return world.isChunkLoaded(chunkX, chunkZ);
        }

        @Override
        public boolean isAir(int x, int y, int z) {
            return world.getBlockAt(x, y, z).getType().isAir();
        }

        @Override
        public boolean isSafeFloor(int x, int y, int z) {
            Material material = world.getBlockAt(x, y, z).getType();
            return material.isSolid() && material != Material.MAGMA_BLOCK
                    && material != Material.CAMPFIRE && material != Material.SOUL_CAMPFIRE
                    && material != Material.CACTUS && material != Material.POWDER_SNOW;
        }
    }
}
