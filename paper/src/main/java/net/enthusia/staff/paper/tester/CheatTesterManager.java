package net.enthusia.staff.paper.tester;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Supplier;
import java.util.logging.Level;
import net.enthusia.staff.domain.ports.CheatTesterJournalStore;
import net.enthusia.staff.domain.tester.CheatTesterJournalRecord;
import net.enthusia.staff.domain.tester.CheatTesterJournalStart;
import net.enthusia.staff.domain.tester.CheatTesterSessionState;
import net.enthusia.staff.domain.tester.CheatTesterType;
import net.enthusia.staff.paper.inventory.InventoryCoordinator;
import net.enthusia.staff.paper.staff.StaffModeManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerAttemptPickupItemEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

/** Evidence-only cheat tester runtime with durable exact-state recovery. */
public final class CheatTesterManager implements Listener, AutoCloseable {
    private static final String PERMISSION = "enthusiastaff.cheattester";
    private static final String CANCEL_ANY_PERMISSION = "enthusiastaff.cheattester.cancel-any";
    private static final int MAX_RECOVERY_ROWS = 256;
    private static final int HOTBAR_SIZE = 9;
    private static final double MIN_DIRECTION_LENGTH_SQUARED = 0.0001D;
    private static final double MIN_AIM_VECTOR_LENGTH_SQUARED = 0.000001D;
    private static final double FALLING_VELOCITY_THRESHOLD = -0.05D;
    private static final float PREVIOUS_FALL_THRESHOLD = 2.0F;
    private static final float FALL_RESET_THRESHOLD = 0.25F;
    private static final String FAKE_SNAPSHOT = "{\"schemaVersion\":1,\"kind\":\"fake-entity\"}";
    private static final List<CheatTesterType> SELECTABLE = List.of(
            CheatTesterType.TOTEM_REFILL,
            CheatTesterType.NO_FALL,
            CheatTesterType.VELOCITY,
            CheatTesterType.AUTO_ARMOR,
            CheatTesterType.FAKE_ENTITY
    );

    private final JavaPlugin plugin;
    private final Clock clock;
    private final String serverId;
    private final StaffModeManager staffMode;
    private final InventoryCoordinator inventory;
    private final Supplier<CheatTesterJournalStore> store;
    private final ExecutorService workers;
    private final CheatTesterSettings settings;
    private final CheatTesterSnapshotCodec snapshots;
    private final CheatTesterEvidence evidence;
    private final ObjectMapper json = new ObjectMapper();
    private final Map<UUID, CheatTesterType> selections = new ConcurrentHashMap<>();
    private final Map<UUID, CheatTesterSession> activeByTarget = new ConcurrentHashMap<>();
    private final Map<Integer, UUID> fakeEntityTargets = new ConcurrentHashMap<>();
    private final java.util.concurrent.atomic.AtomicBoolean closed = new java.util.concurrent.atomic.AtomicBoolean();
    private final FakeEntityAdapter fakeEntities;
    private final Map<CheatTesterType, ProbeStarter> probeStarters;

    public CheatTesterManager(
            JavaPlugin plugin,
            Clock clock,
            String serverId,
            StaffModeManager staffMode,
            InventoryCoordinator inventory,
            Supplier<CheatTesterJournalStore> store,
            ExecutorService workers,
            CheatTesterSettings settings
    ) {
        this.plugin = java.util.Objects.requireNonNull(plugin, "plugin");
        this.clock = java.util.Objects.requireNonNull(clock, "clock");
        this.serverId = requireServerId(serverId);
        this.staffMode = java.util.Objects.requireNonNull(staffMode, "staffMode");
        this.inventory = java.util.Objects.requireNonNull(inventory, "inventory");
        this.store = java.util.Objects.requireNonNull(store, "store");
        this.workers = java.util.Objects.requireNonNull(workers, "workers");
        this.settings = java.util.Objects.requireNonNull(settings, "settings");
        this.snapshots = new CheatTesterSnapshotCodec(plugin);
        this.evidence = new CheatTesterEvidence(clock, settings);
        this.fakeEntities = installFakeEntityAdapter();
        this.probeStarters = createProbeStarters();
    }

    public boolean packetSupportAvailable() {
        return fakeEntities.available();
    }

    public CheatTesterType selected(UUID staffId) {
        return selections.getOrDefault(staffId, CheatTesterType.TOTEM_REFILL);
    }

    public void select(Player staff, CheatTesterType type) {
        if (!authorized(staff)) {
            return;
        }
        if (!providerAvailable(staff, type)) {
            return;
        }
        selections.put(staff.getUniqueId(), type);
        staff.sendMessage(Component.text("Cheat Tester selected: " + type.displayName(), NamedTextColor.AQUA));
    }

    public void cycleSelection(Player staff) {
        if (!authorized(staff)) {
            return;
        }
        int nextIndex = (SELECTABLE.indexOf(selected(staff.getUniqueId())) + 1) % SELECTABLE.size();
        for (int attempts = 0; attempts < SELECTABLE.size(); attempts++) {
            CheatTesterType next = SELECTABLE.get((nextIndex + attempts) % SELECTABLE.size());
            if (next != CheatTesterType.FAKE_ENTITY || fakeEntities.available()) {
                select(staff, next);
                return;
            }
        }
    }

    public void showConfiguration(Player staff) {
        if (!authorized(staff)) {
            return;
        }
        CheatTesterType selected = selected(staff.getUniqueId());
        Component message = Component.text("Cheat Tester: ", NamedTextColor.GRAY)
                .append(Component.text(selected.displayName(), NamedTextColor.AQUA))
                .append(Component.text(" | timeout " + settings.sessionTimeout().toMillis() + " ms", NamedTextColor.GRAY))
                .append(Component.text(" | active " + activeByTarget.size() + "/" + settings.maximumActiveGlobal(), NamedTextColor.GRAY))
                .append(Component.newline())
                .append(Component.text("Use /cheattester select <type>, then left-click a player to run. ", NamedTextColor.GRAY))
                .append(Component.text("[Status]", NamedTextColor.YELLOW)
                        .clickEvent(ClickEvent.runCommand("/cheattester status")));
        staff.sendMessage(message);
    }

    public void runSelected(Player staff, Player target) {
        run(staff, target, selected(staff.getUniqueId()));
    }

    public void run(Player staff, Player target, CheatTesterType type) {
        if (!canStart(staff, target, type)) {
            return;
        }
        boolean assetLock = type.mutatesTargetState();
        if (assetLock && !inventory.acquireExternalAssetLock(target.getUniqueId())) {
            staff.sendMessage(Component.text("The target inventory is busy with another durable moderation operation."));
            return;
        }
        CheatTesterSession session = new CheatTesterSession(
                UUID.randomUUID(),
                staff.getUniqueId(),
                target.getUniqueId(),
                type,
                assetLock,
                clock.instant()
        );
        if (!registerSession(staff, session)) {
            return;
        }
        schedulePreparation(staff, target, session);
    }

    private boolean canStart(Player staff, Player target, CheatTesterType type) {
        return validParticipants(staff, target, type)
                && providerAvailable(staff, type)
                && capacityAvailable(staff);
    }

    private boolean validParticipants(Player staff, Player target, CheatTesterType type) {
        if (!authorized(staff) || target == null || type == null || closed.get()) {
            return false;
        }
        if (staff.getUniqueId().equals(target.getUniqueId())) {
            staff.sendMessage(Component.text("Cheat Tester cannot target the controlling staff member."));
            return false;
        }
        if (!target.isOnline()) {
            staff.sendMessage(Component.text("The target must be online on this backend."));
            return false;
        }
        return true;
    }

    private boolean providerAvailable(Player staff, CheatTesterType type) {
        if (type != CheatTesterType.FAKE_ENTITY || fakeEntities.available()) {
            return true;
        }
        staff.sendMessage(Component.text(
                "Fake-entity testing is unavailable; ProtocolLib packet support failed closed.",
                NamedTextColor.RED
        ));
        return false;
    }

    private boolean capacityAvailable(Player staff) {
        if (activeByTarget.size() >= settings.maximumActiveGlobal()) {
            staff.sendMessage(Component.text("The global cheat-tester session limit is active."));
            return false;
        }
        if (activeForStaff(staff.getUniqueId()) >= settings.maximumActivePerStaff()) {
            staff.sendMessage(Component.text("You already control the maximum number of cheat-tester sessions."));
            return false;
        }
        return true;
    }

    private long activeForStaff(UUID staffId) {
        return activeByTarget.values().stream().filter(session -> session.staffId.equals(staffId)).count();
    }

    private boolean registerSession(Player staff, CheatTesterSession session) {
        CheatTesterSession competing = activeByTarget.putIfAbsent(session.targetId, session);
        if (competing == null) {
            return true;
        }
        releaseAssetLock(session);
        staff.sendMessage(Component.text("That target already has an active cheat-tester session."));
        return false;
    }

    private void schedulePreparation(Player staff, Player target, CheatTesterSession session) {
        boolean scheduled = target.getScheduler().execute(
                plugin,
                () -> prepareAndJournal(staff.getUniqueId(), target, session),
                () -> retireForRecovery(session, "Target retired before tester snapshot"),
                1L
        );
        if (!scheduled) {
            retireWithoutJournal(session);
            staff.sendMessage(Component.text("The target could not be scheduled for safe tester preparation."));
        }
    }

    public void cancel(Player staff, UUID targetId) {
        if (!authorized(staff)) {
            return;
        }
        CheatTesterSession session = activeByTarget.get(targetId);
        if (!controllable(staff, session)) {
            staff.sendMessage(Component.text("No controllable active tester exists for that target."));
            return;
        }
        finish(session, CheatTesterSessionState.CANCELLED, "cancelled by staff");
    }

    private static boolean controllable(Player staff, CheatTesterSession session) {
        return session != null
                && (session.staffId.equals(staff.getUniqueId()) || staff.hasPermission(CANCEL_ANY_PERMISSION));
    }

    public List<String> statusLines(UUID staffId, boolean includeAll) {
        List<String> lines = new ArrayList<>();
        for (CheatTesterSession session : activeByTarget.values()) {
            if (includeAll || session.staffId.equals(staffId)) {
                lines.add(session.type.id() + " target=" + session.targetId + " age="
                        + Math.max(0L, clock.instant().toEpochMilli() - session.startedAt.toEpochMilli()) + "ms");
            }
        }
        return List.copyOf(lines);
    }

    public void recoverOnlinePlayers() {
        if (closed.get()) {
            return;
        }
        submit(this::loadRecoverableSessions);
    }

    private void loadRecoverableSessions() {
        CheatTesterJournalStore loaded = store.get();
        if (loaded == null) {
            return;
        }
        try {
            List<CheatTesterJournalRecord> records = loaded.activeForServer(serverId, MAX_RECOVERY_ROWS);
            plugin.getServer().getGlobalRegionScheduler().execute(plugin, () -> scheduleRecoverableSessions(records));
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.SEVERE, "Cheat tester recovery scan failed", exception);
        }
    }

    private void scheduleRecoverableSessions(List<CheatTesterJournalRecord> records) {
        for (CheatTesterJournalRecord record : records) {
            Player target = plugin.getServer().getPlayer(record.targetId());
            if (target != null && target.isOnline()) {
                scheduleRecovery(target, record);
            }
        }
    }

    private void prepareAndJournal(UUID staffId, Player target, CheatTesterSession session) {
        if (!sessionCurrent(session) || closed.get() || session.finishing.get()) {
            retireWithoutJournal(session);
            return;
        }
        try {
            session.probe = prepareProbe(target, session.type);
            session.startPoint = CheatTesterSession.StartPoint.capture(target.getLocation());
            if (session.type == CheatTesterType.FAKE_ENTITY) {
                session.fakeHandle = fakeEntities.create();
            }
            session.snapshot = session.type.mutatesTargetState() ? snapshots.capture(target) : FAKE_SNAPSHOT;
            CheatTesterJournalStart start = journalStart(staffId, target, session);
            if (!submit(() -> persistStart(session, start))) {
                failBeforeMutation(session, "The bounded work queue is full; tester did not start.");
            }
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.WARNING, "Cheat tester preparation failed before durable mutation", exception);
            failBeforeMutation(session, safePreparationMessage(exception));
        }
    }

    private CheatTesterJournalStart journalStart(UUID staffId, Player target, CheatTesterSession session) {
        Instant now = clock.instant();
        return new CheatTesterJournalStart(
                session.sessionId,
                serverId,
                staffId,
                target.getUniqueId(),
                session.type,
                session.snapshot,
                evidence.configuration(session),
                now,
                now.plus(settings.sessionTimeout())
        );
    }

    private CheatTesterSession.PreparedProbe prepareProbe(Player target, CheatTesterType type) {
        if (target.getOpenInventory().getType() != InventoryType.CRAFTING) {
            target.closeInventory();
        }
        return switch (type) {
            case TOTEM_REFILL -> prepareTotemProbe(target.getInventory());
            case AUTO_ARMOR -> prepareArmorProbe(target.getInventory());
            case VELOCITY, NO_FALL, FAKE_ENTITY -> CheatTesterSession.PreparedProbe.NONE;
            default -> throw new IllegalArgumentException("Unsupported cheat tester type: " + type);
        };
    }

    private static CheatTesterSession.PreparedProbe prepareTotemProbe(PlayerInventory inventory) {
        int totemSlot = firstMaterial(inventory.getStorageContents(), Material.TOTEM_OF_UNDYING);
        if (totemSlot < 0) {
            throw new IllegalStateException("Target needs a totem in normal inventory for a no-injection refill probe");
        }
        return new CheatTesterSession.PreparedProbe(totemSlot, -1, -1);
    }

    private static CheatTesterSession.PreparedProbe prepareArmorProbe(PlayerInventory inventory) {
        int armorSlot = firstNonEmpty(inventory.getArmorContents());
        int storageSlot = firstEmpty(inventory.getStorageContents());
        if (armorSlot < 0 || storageSlot < 0) {
            throw new IllegalStateException(
                    "Target needs equipped armor and one empty inventory slot for an exact-restore armor probe"
            );
        }
        return new CheatTesterSession.PreparedProbe(-1, armorSlot, storageSlot);
    }

    private void persistStart(CheatTesterSession session, CheatTesterJournalStart start) {
        CheatTesterJournalStore loaded = store.get();
        if (loaded == null) {
            failBeforeMutation(session, "Tester storage is not ready; no target state was changed.");
            return;
        }
        try {
            CheatTesterJournalRecord record = loaded.start(start);
            if (!record.sessionId().equals(session.sessionId)) {
                failBeforeMutation(session,
                        "A durable tester session already exists for that target on " + record.serverId() + '.');
                return;
            }
            session.revision = record.revision();
            scheduleBegin(session);
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.SEVERE, "Cheat tester journal start failed", exception);
            failBeforeMutation(session, "Tester journal could not be committed; no target state was changed.");
        }
    }

    private void scheduleBegin(CheatTesterSession session) {
        plugin.getServer().getGlobalRegionScheduler().execute(plugin, () -> {
            Player target = plugin.getServer().getPlayer(session.targetId);
            if (target == null || !target.isOnline()) {
                retireForRecovery(session, "Target disconnected after journal commit");
                return;
            }
            boolean scheduled = target.getScheduler().execute(
                    plugin,
                    () -> beginProbe(target, session),
                    () -> retireForRecovery(session, "Target retired after journal commit"),
                    1L
            );
            if (!scheduled) {
                retireForRecovery(session, "Target probe could not be scheduled after journal commit");
            }
        });
    }

    private Map<CheatTesterType, ProbeStarter> createProbeStarters() {
        EnumMap<CheatTesterType, ProbeStarter> starters = new EnumMap<>(CheatTesterType.class);
        starters.put(CheatTesterType.TOTEM_REFILL, this::beginTotemProbe);
        starters.put(CheatTesterType.AUTO_ARMOR, this::beginArmorProbe);
        starters.put(CheatTesterType.VELOCITY, (target, ignored) -> beginVelocityProbe(target));
        starters.put(CheatTesterType.NO_FALL, this::beginNoFallProbe);
        starters.put(CheatTesterType.FAKE_ENTITY, this::beginFakeEntityProbe);
        return Map.copyOf(starters);
    }

    private void beginProbe(Player target, CheatTesterSession session) {
        if (!sessionCurrent(session) || session.finishing.get() || !target.isOnline()) {
            retireForRecovery(session, "Target unavailable or tester cancelled at probe start");
            return;
        }
        try {
            if (session.type.mutatesTargetState()) {
                target.setInvulnerable(true);
            }
            ProbeStarter starter = java.util.Objects.requireNonNull(
                    probeStarters.get(session.type),
                    "cheat tester probe starter"
            );
            starter.start(target, session);
            session.startedMutation.set(true);
            scheduleTimeout(session);
            message(session.staffId, Component.text(
                    "Started " + session.type.displayName() + " evidence probe for " + target.getName() + ".",
                    NamedTextColor.AQUA
            ));
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.WARNING, "Cheat tester probe start failed after durable journal commit", exception);
            finish(session, CheatTesterSessionState.FAILED, "probe setup failed");
        }
    }

    private void scheduleTimeout(CheatTesterSession session) {
        session.timeoutTask = plugin.getServer().getGlobalRegionScheduler().runDelayed(
                plugin,
                ignored -> finish(session, CheatTesterSessionState.RESTORED, "probe completed"),
                timeoutTicks()
        );
    }

    private void beginTotemProbe(Player target, CheatTesterSession session) {
        PlayerInventory inventory = target.getInventory();
        int source = session.probe.sourceSlot();
        ItemStack[] storage = inventory.getStorageContents();
        if (!validTotemSource(storage, source)) {
            throw new IllegalStateException("The prepared totem source changed before probe start");
        }
        inventory.setItemInOffHand(new ItemStack(Material.AIR));
        target.updateInventory();
    }

    private static boolean validTotemSource(ItemStack[] storage, int source) {
        return source >= 0 && source < storage.length && storage[source] != null
                && storage[source].getType() == Material.TOTEM_OF_UNDYING;
    }

    private void beginArmorProbe(Player target, CheatTesterSession session) {
        PlayerInventory inventory = target.getInventory();
        ItemStack[] armor = inventory.getArmorContents();
        ItemStack[] storage = inventory.getStorageContents();
        int armorSlot = session.probe.armorSlot();
        int storageSlot = session.probe.storageSlot();
        validateArmorSlots(armor, storage, armorSlot, storageSlot);
        storage[storageSlot] = armor[armorSlot].clone();
        armor[armorSlot] = new ItemStack(Material.AIR);
        inventory.setStorageContents(storage);
        inventory.setArmorContents(armor);
        target.updateInventory();
    }

    private static void validateArmorSlots(ItemStack[] armor, ItemStack[] storage, int armorSlot, int storageSlot) {
        if (armorSlot < 0 || armorSlot >= armor.length || storageSlot < 0 || storageSlot >= storage.length) {
            throw new IllegalStateException("The prepared armor slots changed before probe start");
        }
        if (armor[armorSlot] == null || armor[armorSlot].isEmpty()) {
            throw new IllegalStateException("The prepared armor item changed before probe start");
        }
        if (storage[storageSlot] != null && !storage[storageSlot].isEmpty()) {
            throw new IllegalStateException("The prepared armor destination changed before probe start");
        }
    }

    private void beginVelocityProbe(Player target) {
        Vector horizontal = target.getLocation().getDirection().setY(0.0D);
        if (horizontal.lengthSquared() < MIN_DIRECTION_LENGTH_SQUARED) {
            horizontal = new Vector(1.0D, 0.0D, 0.0D);
        } else {
            horizontal.normalize();
        }
        target.setVelocity(horizontal.multiply(settings.velocityHorizontal()).setY(settings.velocityVertical()));
    }

    private void beginNoFallProbe(Player target, CheatTesterSession session) {
        target.setFallDistance(0.0F);
        target.setVelocity(new Vector(0.0D, settings.noFallVertical(), 0.0D));
        session.sampleTask = target.getScheduler().runAtFixedRate(
                plugin,
                ignored -> sampleNoFall(target, session),
                () -> retireForRecovery(session, "Target retired during no-fall sampling"),
                1L,
                1L
        );
    }

    private void sampleNoFall(Player target, CheatTesterSession session) {
        if (!sampleActive(session)) {
            cancel(session.sampleTask);
            return;
        }
        float current = target.getFallDistance();
        session.maxFallDistance = Math.max(session.maxFallDistance, current);
        if (fallResetObserved(target, session, current)) {
            session.airborneFallResets.incrementAndGet();
        }
        session.previousFallDistance = current;
    }

    private boolean sampleActive(CheatTesterSession session) {
        return sessionCurrent(session) && !session.finishing.get();
    }

    private static boolean fallResetObserved(Player target, CheatTesterSession session, float current) {
        return target.getVelocity().getY() < FALLING_VELOCITY_THRESHOLD
                && session.previousFallDistance > PREVIOUS_FALL_THRESHOLD
                && current < FALL_RESET_THRESHOLD;
    }

    private void beginFakeEntityProbe(Player target, CheatTesterSession session) {
        if (!fakeEntities.available() || session.fakeHandle == null) {
            throw new IllegalStateException("ProtocolLib fake-entity support became unavailable");
        }
        Location location = fakeLocation(target);
        session.fakeLocation = CheatTesterSession.StartPoint.capture(location);
        fakeEntityTargets.put(session.fakeHandle.entityId(), session.targetId);
        fakeEntities.show(target, session.fakeHandle, location);
        scheduleShowForStaff(session, location);
        session.sampleTask = target.getScheduler().runAtFixedRate(
                plugin,
                ignored -> sampleFakeAim(target, session),
                () -> retireForRecovery(session, "Target retired during fake-entity aim sampling"),
                1L,
                1L
        );
    }

    private void sampleFakeAim(Player target, CheatTesterSession session) {
        if (!sampleActive(session)) {
            cancel(session.sampleTask);
            return;
        }
        CheatTesterSession.StartPoint fake = session.fakeLocation;
        if (fake == null || !target.getWorld().getUID().equals(fake.worldId())) {
            return;
        }
        Location eye = target.getEyeLocation();
        Vector toFake = new Vector(fake.x() - eye.getX(), fake.y() + 1.0D - eye.getY(), fake.z() - eye.getZ());
        if (toFake.lengthSquared() < MIN_AIM_VECTOR_LENGTH_SQUARED) {
            session.minimumAimAngleDegrees = 0.0D;
            return;
        }
        updateMinimumAimAngle(session, eye.getDirection(), toFake);
    }

    private static void updateMinimumAimAngle(CheatTesterSession session, Vector look, Vector toFake) {
        if (look.lengthSquared() < MIN_AIM_VECTOR_LENGTH_SQUARED) {
            return;
        }
        double dot = Math.max(-1.0D, Math.min(1.0D, look.normalize().dot(toFake.normalize())));
        session.minimumAimAngleDegrees = Math.min(session.minimumAimAngleDegrees, Math.toDegrees(Math.acos(dot)));
    }

    private void scheduleShowForStaff(CheatTesterSession session, Location location) {
        plugin.getServer().getGlobalRegionScheduler().execute(plugin, () -> {
            Player staff = plugin.getServer().getPlayer(session.staffId);
            if (staff == null || !staff.isOnline()) {
                return;
            }
            Location copy = location.clone();
            staff.getScheduler().execute(
                    plugin,
                    () -> showFakeToStaff(staff, session, copy),
                    null,
                    1L
            );
        });
    }

    private void showFakeToStaff(Player staff, CheatTesterSession session, Location location) {
        if (fakeEntities.available() && staff.getWorld().equals(location.getWorld())) {
            fakeEntities.show(staff, session.fakeHandle, location);
        }
    }

    private Location fakeLocation(Player target) {
        Location base = target.getLocation();
        Vector direction = base.getDirection().setY(0.0D);
        if (direction.lengthSquared() < MIN_DIRECTION_LENGTH_SQUARED) {
            direction = new Vector(0.0D, 0.0D, 1.0D);
        } else {
            direction.normalize();
        }
        return base.clone().add(direction.multiply(settings.fakeEntityDistance()));
    }

    private void finish(CheatTesterSession session, CheatTesterSessionState terminalState, String reason) {
        if (!beginFinish(session)) {
            return;
        }
        cancel(session.timeoutTask);
        cancel(session.sampleTask);
        plugin.getServer().getGlobalRegionScheduler().execute(
                plugin,
                () -> scheduleFinishOnTarget(session, terminalState, reason)
        );
    }

    private boolean beginFinish(CheatTesterSession session) {
        return session != null && sessionCurrent(session) && session.finishing.compareAndSet(false, true);
    }

    private void scheduleFinishOnTarget(
            CheatTesterSession session,
            CheatTesterSessionState terminalState,
            String reason
    ) {
        Player target = plugin.getServer().getPlayer(session.targetId);
        if (target == null || !target.isOnline()) {
            finishWithoutOnlineTarget(session, terminalState, reason);
            return;
        }
        boolean scheduled = target.getScheduler().execute(
                plugin,
                () -> finishOnTarget(target, session, terminalState, reason),
                () -> retireForRecovery(session, "Target retired before tester restoration"),
                1L
        );
        if (!scheduled) {
            retireForRecovery(session, "Target restoration could not be scheduled");
        }
    }

    private void finishWithoutOnlineTarget(
            CheatTesterSession session,
            CheatTesterSessionState terminalState,
            String reason
    ) {
        if (session.type == CheatTesterType.FAKE_ENTITY) {
            completeFakeWithoutTarget(session, terminalState, reason);
        } else {
            retireForRecovery(session, "Target offline before tester restoration");
        }
    }

    private void finishOnTarget(
            Player target,
            CheatTesterSession session,
            CheatTesterSessionState terminalState,
            String reason
    ) {
        String captured = evidence.capture(target, session, reason);
        if (session.type == CheatTesterType.FAKE_ENTITY) {
            hideFakeEntity(target, session);
            persistCompletion(session, terminalState, reason, captured, true);
            return;
        }
        checkpointEvidenceThenRestore(target, session, terminalState, reason, captured);
    }

    private void checkpointEvidenceThenRestore(
            Player target,
            CheatTesterSession session,
            CheatTesterSessionState terminalState,
            String reason,
            String captured
    ) {
        Runnable restore = () -> restoreTarget(target, session, terminalState, reason, captured);
        if (!submit(() -> checkpointThenScheduleRestore(session, captured, restore))) {
            restore.run();
        }
    }

    private void checkpointThenScheduleRestore(CheatTesterSession session, String captured, Runnable restore) {
        CheatTesterJournalStore loaded = store.get();
        if (loaded != null) {
            checkpointEvidence(loaded, session, captured);
        }
        scheduleTarget(
                session.targetId,
                restore,
                () -> retireForRecovery(session, "Target retired before exact restoration")
        );
    }

    private void checkpointEvidence(
            CheatTesterJournalStore loaded,
            CheatTesterSession session,
            String captured
    ) {
        try {
            loaded.checkpointEvidence(session.sessionId, session.revision, captured, clock.instant())
                    .ifPresent(record -> session.revision = record.revision());
        } catch (RuntimeException exception) {
            plugin.getLogger().log(
                    Level.WARNING,
                    "Tester evidence checkpoint failed; restoration still takes priority",
                    exception
            );
        }
    }

    private void restoreTarget(
            Player target,
            CheatTesterSession session,
            CheatTesterSessionState terminalState,
            String reason,
            String captured
    ) {
        snapshots.restore(
                target,
                session.snapshot,
                restoresMovement(session.type),
                () -> persistCompletion(session, terminalState, reason, captured, true),
                failure -> restorationFailed(session, failure)
        );
    }

    private static boolean restoresMovement(CheatTesterType type) {
        return type == CheatTesterType.VELOCITY || type == CheatTesterType.NO_FALL;
    }

    private void restorationFailed(CheatTesterSession session, Throwable failure) {
        plugin.getLogger().log(
                Level.SEVERE,
                "Cheat tester exact state restoration failed; durable recovery remains pending",
                failure
        );
        retireForRecovery(session, "Exact restoration failed and remains pending");
        message(session.staffId, Component.text(
                "Tester restoration did not verify. Durable recovery remains pending; do not rerun the target.",
                NamedTextColor.RED
        ));
    }

    private void persistCompletion(
            CheatTesterSession session,
            CheatTesterSessionState terminalState,
            String reason,
            String captured,
            boolean notify
    ) {
        if (!submit(() -> completeOnWorker(session, terminalState, reason, captured, notify))) {
            retireForRecovery(session, "Bounded work queue full during tester completion");
        }
    }

    private void completeOnWorker(
            CheatTesterSession session,
            CheatTesterSessionState terminalState,
            String reason,
            String captured,
            boolean notify
    ) {
        CheatTesterJournalStore loaded = store.get();
        if (loaded == null) {
            retireForRecovery(session, "Tester storage unavailable during completion");
            return;
        }
        try {
            if (!completeDurably(loaded, session, terminalState, reason, captured)) {
                throw new IllegalStateException("tester completion lost its durable state transition");
            }
            retireCompleted(session);
            if (notify) {
                message(session.staffId, Component.text(
                        "Cheat Tester finished: " + evidence.summary(session, captured),
                        NamedTextColor.GREEN
                ));
            }
        } catch (RuntimeException exception) {
            plugin.getLogger().log(
                    Level.SEVERE,
                    "Cheat tester state was restored but durable completion failed; recovery row remains authoritative",
                    exception
            );
            retireForRecovery(session, "Durable completion failed after restoration");
        }
    }

    private boolean completeDurably(
            CheatTesterJournalStore loaded,
            CheatTesterSession session,
            CheatTesterSessionState terminalState,
            String reason,
            String captured
    ) {
        String bounded = CheatTesterEvidence.boundedReason(reason);
        if (loaded.complete(session.sessionId, session.revision, terminalState, bounded, captured, clock.instant())) {
            return true;
        }
        Optional<CheatTesterJournalRecord> current = loaded.activeForTarget(session.targetId);
        if (current.isEmpty() || !current.orElseThrow().sessionId().equals(session.sessionId)) {
            return false;
        }
        session.revision = current.orElseThrow().revision();
        return loaded.complete(session.sessionId, session.revision, terminalState, bounded, captured, clock.instant());
    }

    private void completeFakeWithoutTarget(
            CheatTesterSession session,
            CheatTesterSessionState terminalState,
            String reason
    ) {
        scheduleHideForStaff(session);
        removeFakeTarget(session);
        persistCompletion(session, terminalState, reason, evidence.withoutTarget(session, reason), false);
    }

    private void hideFakeEntity(Player target, CheatTesterSession session) {
        FakeEntityAdapter.Handle handle = session.fakeHandle;
        if (handle == null) {
            return;
        }
        fakeEntityTargets.remove(handle.entityId());
        fakeEntities.destroy(target, handle);
        scheduleHideForStaff(session);
    }

    private void scheduleHideForStaff(CheatTesterSession session) {
        FakeEntityAdapter.Handle handle = session.fakeHandle;
        if (handle == null) {
            return;
        }
        plugin.getServer().getGlobalRegionScheduler().execute(plugin, () -> {
            Player staff = plugin.getServer().getPlayer(session.staffId);
            if (staff != null) {
                staff.getScheduler().execute(plugin, () -> fakeEntities.destroy(staff, handle), null, 1L);
            }
        });
    }

    private boolean recordFakeInteraction(UUID viewerId, int entityId, String action) {
        CheatTesterSession session = fakeSession(entityId);
        if (session == null) {
            return fakeEntityTargets.containsKey(entityId);
        }
        if (viewerId.equals(session.staffId)) {
            return true;
        }
        if (!viewerId.equals(session.targetId)) {
            return true;
        }
        session.fakeInteractions.incrementAndGet();
        if ("ATTACK".equals(action)) {
            session.fakeAttacks.incrementAndGet();
        }
        session.firstInteractionMillis.compareAndSet(
                -1L,
                Math.max(0L, clock.instant().toEpochMilli() - session.startedAt.toEpochMilli())
        );
        return true;
    }

    private CheatTesterSession fakeSession(int entityId) {
        UUID targetId = fakeEntityTargets.get(entityId);
        if (targetId == null) {
            return null;
        }
        CheatTesterSession session = activeByTarget.get(targetId);
        if (session == null || session.fakeHandle == null || session.fakeHandle.entityId() != entityId) {
            return null;
        }
        return session;
    }

    private void fakeAdapterFailed() {
        for (CheatTesterSession session : List.copyOf(activeByTarget.values())) {
            if (session.type == CheatTesterType.FAKE_ENTITY) {
                retireForRecovery(session, "ProtocolLib fake-entity adapter failed before cleanup verification");
            }
        }
    }

    private void scheduleRecovery(Player target, CheatTesterJournalRecord record) {
        if (closed.get()) {
            return;
        }
        CheatTesterSession recovered = CheatTesterSession.recovered(record);
        if (activeByTarget.putIfAbsent(record.targetId(), recovered) != null) {
            return;
        }
        if (!acquireRecoveryLock(record, recovered)) {
            return;
        }
        boolean scheduled = target.getScheduler().execute(
                plugin,
                () -> recoverOnTarget(target, recovered, record),
                () -> retireForRecovery(recovered, "Target retired during tester recovery"),
                1L
        );
        if (!scheduled) {
            retireForRecovery(recovered, "Tester recovery could not be scheduled");
        }
    }

    private boolean acquireRecoveryLock(CheatTesterJournalRecord record, CheatTesterSession recovered) {
        if (!record.testerType().mutatesTargetState()) {
            return true;
        }
        if (inventory.acquireExternalAssetLock(record.targetId())) {
            recovered.assetLock = true;
            return true;
        }
        activeByTarget.remove(record.targetId(), recovered);
        plugin.getLogger().warning(
                "Deferred cheat tester recovery because another inventory operation owns the target lock"
        );
        return false;
    }

    private void recoverOnTarget(Player target, CheatTesterSession session, CheatTesterJournalRecord record) {
        if (record.testerType() == CheatTesterType.FAKE_ENTITY) {
            recoverFakeEntity(target, session, record);
            return;
        }
        session.snapshot = record.snapshot();
        snapshots.restore(
                target,
                record.snapshot(),
                restoresMovement(record.testerType()),
                () -> persistCompletion(
                        session,
                        CheatTesterSessionState.RESTORED,
                        "recovered exact tester state after runtime interruption",
                        recoveredEvidence(session, record),
                        false
                ),
                failure -> recoveryFailed(session, failure)
        );
    }

    private void recoverFakeEntity(Player target, CheatTesterSession session, CheatTesterJournalRecord record) {
        session.fakeHandle = handleFromConfiguration(record.configuration()).orElse(null);
        if (session.fakeHandle == null || !fakeEntities.available()) {
            retireForRecovery(session, "Fake-entity recovery is waiting for healthy ProtocolLib support");
            return;
        }
        fakeEntities.destroy(target, session.fakeHandle);
        scheduleHideForStaff(session);
        persistCompletion(
                session,
                CheatTesterSessionState.CANCELLED,
                "recovered nonpersistent fake entity after runtime restart",
                recoveredEvidence(session, record),
                false
        );
    }

    private String recoveredEvidence(CheatTesterSession session, CheatTesterJournalRecord record) {
        return record.evidence() == null ? evidence.withoutTarget(session, "runtime recovery") : record.evidence();
    }

    private void recoveryFailed(CheatTesterSession session, Throwable failure) {
        plugin.getLogger().log(Level.SEVERE, "Cheat tester recovery failed; durable row remains active", failure);
        retireForRecovery(session, "Recovery attempt failed");
    }

    private Optional<FakeEntityAdapter.Handle> handleFromConfiguration(String configuration) {
        try {
            JsonNode node = json.readTree(configuration);
            JsonNode id = node.get("fakeEntityId");
            JsonNode uuid = node.get("fakeEntityUuid");
            if (id == null || uuid == null) {
                return Optional.empty();
            }
            return Optional.of(new FakeEntityAdapter.Handle(id.asInt(), UUID.fromString(uuid.asText())));
        } catch (RuntimeException | JsonProcessingException exception) {
            plugin.getLogger().log(Level.WARNING, "Unable to decode recovered fake-entity handle", exception);
            return Optional.empty();
        }
    }

    private void recover(Player player) {
        if (closed.get()) {
            return;
        }
        UUID playerId = player.getUniqueId();
        submit(() -> recoverById(playerId));
    }

    private void recoverById(UUID playerId) {
        CheatTesterJournalStore loaded = store.get();
        if (loaded == null) {
            return;
        }
        try {
            loaded.activeForTarget(serverId, playerId).ifPresent(this::scheduleRecoveredRecord);
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.SEVERE, "Cheat tester join recovery lookup failed", exception);
        }
    }

    private void scheduleRecoveredRecord(CheatTesterJournalRecord record) {
        plugin.getServer().getGlobalRegionScheduler().execute(plugin, () -> {
            Player current = plugin.getServer().getPlayer(record.targetId());
            if (current != null && current.isOnline()) {
                scheduleRecovery(current, record);
            }
        });
    }

    private void failBeforeMutation(CheatTesterSession session, String failureMessage) {
        if (session.startedMutation.get()) {
            finish(session, CheatTesterSessionState.FAILED, "failure after mutation");
            return;
        }
        if (activeByTarget.remove(session.targetId, session)) {
            releaseAssetLock(session);
            removeFakeTarget(session);
            message(session.staffId, Component.text(failureMessage, NamedTextColor.RED));
        }
    }

    private void retireWithoutJournal(CheatTesterSession session) {
        activeByTarget.remove(session.targetId, session);
        cancelSessionTasks(session);
        releaseAssetLock(session);
    }

    private void retireCompleted(CheatTesterSession session) {
        activeByTarget.remove(session.targetId, session);
        removeFakeTarget(session);
        cancelSessionTasks(session);
        releaseAssetLock(session);
    }

    private void retireForRecovery(CheatTesterSession session, String reason) {
        activeByTarget.remove(session.targetId, session);
        removeFakeTarget(session);
        cancelSessionTasks(session);
        releaseAssetLock(session);
        plugin.getLogger().warning(
                "Cheat tester session " + session.sessionId + " remains durably recoverable: " + reason
        );
    }

    private void removeFakeTarget(CheatTesterSession session) {
        if (session.fakeHandle != null) {
            fakeEntityTargets.remove(session.fakeHandle.entityId());
        }
        fakeEntityTargets.entrySet().removeIf(entry -> entry.getValue().equals(session.targetId));
    }

    private static void cancelSessionTasks(CheatTesterSession session) {
        cancel(session.timeoutTask);
        cancel(session.sampleTask);
    }

    private void releaseAssetLock(CheatTesterSession session) {
        if (session.assetLock) {
            session.assetLock = false;
            inventory.releaseExternalAssetLock(session.targetId);
        }
    }

    private boolean sessionCurrent(CheatTesterSession session) {
        return activeByTarget.get(session.targetId) == session;
    }

    private boolean authorized(Player staff) {
        if (staff == null || !staff.hasPermission(PERMISSION)) {
            if (staff != null) {
                staff.sendMessage(Component.text("You do not have permission to use Cheat Tester."));
            }
            return false;
        }
        if (!staffMode.active(staff.getUniqueId())) {
            staff.sendMessage(Component.text("Enter staff mode before using Cheat Tester."));
            return false;
        }
        return true;
    }

    private void scheduleTarget(UUID targetId, Runnable operation, Runnable retired) {
        plugin.getServer().getGlobalRegionScheduler().execute(plugin, () -> {
            Player target = plugin.getServer().getPlayer(targetId);
            if (target == null || !target.isOnline()
                    || !target.getScheduler().execute(plugin, operation, retired, 1L)) {
                retired.run();
            }
        });
    }

    private boolean submit(Runnable operation) {
        if (closed.get() || workers.isShutdown()) {
            return false;
        }
        try {
            workers.execute(operation);
            return true;
        } catch (RejectedExecutionException exception) {
            return false;
        }
    }

    private void message(UUID playerId, Component message) {
        plugin.getServer().getGlobalRegionScheduler().execute(plugin, () -> {
            Player player = plugin.getServer().getPlayer(playerId);
            if (player != null) {
                player.getScheduler().execute(plugin, () -> player.sendMessage(message), null, 1L);
            }
        });
    }

    private long timeoutTicks() {
        long byDuration = Math.max(1L, settings.sessionTimeout().toMillis() / 50L);
        return Math.max(1L, Math.min(settings.probeTicks(), byDuration));
    }

    private static int firstMaterial(ItemStack[] items, Material material) {
        for (int index = 0; index < items.length; index++) {
            ItemStack item = items[index];
            if (item != null && !item.isEmpty() && item.getType() == material) {
                return index;
            }
        }
        return -1;
    }

    private static int firstNonEmpty(ItemStack[] items) {
        for (int index = 0; index < items.length; index++) {
            if (items[index] != null && !items[index].isEmpty()) {
                return index;
            }
        }
        return -1;
    }

    private static int firstEmpty(ItemStack[] items) {
        for (int index = 0; index < items.length; index++) {
            if (items[index] == null || items[index].isEmpty()) {
                return index;
            }
        }
        return -1;
    }

    private static String safePreparationMessage(RuntimeException exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank()
                ? "Cheat Tester preparation failed before any target state changed."
                : message;
    }

    private static String requireServerId(String serverId) {
        if (serverId == null || !serverId.matches("[A-Za-z0-9_-]{1,64}")) {
            throw new IllegalArgumentException("serverId is invalid");
        }
        return serverId;
    }

    private FakeEntityAdapter installFakeEntityAdapter() {
        if (!plugin.getServer().getPluginManager().isPluginEnabled("ProtocolLib")) {
            plugin.getLogger().warning("ProtocolLib is unavailable; fake-entity Cheat Tester is disabled fail-closed");
            return FakeEntityAdapter.unavailable();
        }
        try {
            return ProtocolLibFakeEntityAdapter.install(plugin, this::recordFakeInteraction, this::fakeAdapterFailed);
        } catch (RuntimeException | LinkageError failure) {
            plugin.getLogger().log(
                    Level.SEVERE,
                    "ProtocolLib fake-entity adapter could not start; fake-entity Cheat Tester is disabled",
                    failure
            );
            return FakeEntityAdapter.unavailable();
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (event.getPlayer() instanceof Player player && mutating(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (mutating(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player && mutating(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onAttemptPickup(PlayerAttemptPickupItemEvent event) {
        if (mutating(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent event) {
        if (mutating(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (mutating(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onSwapHands(PlayerSwapHandItemsEvent event) {
        if (mutating(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (mutating(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDeath(PlayerDeathEvent event) {
        CheatTesterSession session = activeByTarget.get(event.getPlayer().getUniqueId());
        if (session == null || !session.type.mutatesTargetState()) {
            return;
        }
        event.setKeepInventory(true);
        event.getDrops().clear();
        retireForRecovery(session, "Target died during tester; exact restore deferred to respawn");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        player.getScheduler().execute(plugin, () -> recover(player), null, 1L);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onJoin(PlayerJoinEvent event) {
        recover(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        selections.remove(playerId);
        finishTargetDisconnect(playerId);
        finishStaffDisconnect(playerId);
    }

    private void finishTargetDisconnect(UUID playerId) {
        CheatTesterSession targetSession = activeByTarget.get(playerId);
        if (targetSession == null) {
            return;
        }
        if (targetSession.type == CheatTesterType.FAKE_ENTITY) {
            finish(targetSession, CheatTesterSessionState.CANCELLED, "target disconnected");
        } else {
            retireForRecovery(targetSession, "Target disconnected during tester");
        }
    }

    private void finishStaffDisconnect(UUID playerId) {
        for (CheatTesterSession session : List.copyOf(activeByTarget.values())) {
            if (session.staffId.equals(playerId)) {
                finish(session, CheatTesterSessionState.CANCELLED, "controlling staff disconnected");
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPluginDisable(PluginDisableEvent event) {
        if (event.getPlugin() == plugin) {
            close();
        } else if (event.getPlugin().getName().equals("ProtocolLib")) {
            fakeEntities.close();
            fakeAdapterFailed();
        }
    }

    private boolean mutating(UUID playerId) {
        CheatTesterSession session = activeByTarget.get(playerId);
        return session != null && session.startedMutation.get() && session.type.mutatesTargetState();
    }

    private static void cancel(ScheduledTask task) {
        if (task == null) {
            return;
        }
        try {
            task.cancel();
        } catch (RuntimeException ignored) {
            // Cleanup is idempotent; the durable journal remains authoritative.
        }
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        for (CheatTesterSession session : List.copyOf(activeByTarget.values())) {
            cancelSessionTasks(session);
            removeFakeTarget(session);
            releaseAssetLock(session);
        }
        activeByTarget.clear();
        selections.clear();
        fakeEntityTargets.clear();
        fakeEntities.close();
        // Durable ACTIVE rows remain authoritative until the next healthy runtime verifies cleanup/restoration.
    }

    @FunctionalInterface
    private interface ProbeStarter {
        void start(Player target, CheatTesterSession session);
    }
}
