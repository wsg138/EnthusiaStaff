package net.enthusia.staff.paper.tester;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
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
import org.bukkit.event.player.PlayerAttemptPickupItemEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.inventory.InventoryType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

/**
 * Evidence-only cheat tester runtime. Temporary player state is durably journaled before mutation and
 * restored exactly on every normal termination path; incomplete journal rows are recovered after restart.
 */
public final class CheatTesterManager implements Listener, AutoCloseable {
    private static final String PERMISSION = "enthusiastaff.cheattester";
    private static final int MAX_RECOVERY_ROWS = 256;
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
    private final ObjectMapper json = new ObjectMapper();
    private final Map<UUID, CheatTesterType> selections = new ConcurrentHashMap<>();
    private final Map<UUID, ActiveSession> activeByTarget = new ConcurrentHashMap<>();
    private final Map<Integer, UUID> fakeEntityTargets = new ConcurrentHashMap<>();
    private final AtomicBoolean closed = new AtomicBoolean();
    private final FakeEntityAdapter fakeEntities;

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
        this.fakeEntities = installFakeEntityAdapter();
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
        if (type == CheatTesterType.FAKE_ENTITY && !fakeEntities.available()) {
            staff.sendMessage(Component.text("Fake-entity testing is unavailable because ProtocolLib packet support is not healthy."));
            return;
        }
        selections.put(staff.getUniqueId(), type);
        staff.sendMessage(Component.text("Cheat Tester selected: " + type.displayName(), NamedTextColor.AQUA));
    }

    public void cycleSelection(Player staff) {
        if (!authorized(staff)) {
            return;
        }
        CheatTesterType current = selected(staff.getUniqueId());
        int nextIndex = (SELECTABLE.indexOf(current) + 1) % SELECTABLE.size();
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
        if (!authorized(staff) || target == null || type == null || closed.get()) {
            return;
        }
        if (staff.getUniqueId().equals(target.getUniqueId())) {
            staff.sendMessage(Component.text("Cheat Tester cannot target the controlling staff member."));
            return;
        }
        if (!target.isOnline()) {
            staff.sendMessage(Component.text("The target must be online on this backend."));
            return;
        }
        if (type == CheatTesterType.FAKE_ENTITY && !fakeEntities.available()) {
            staff.sendMessage(Component.text("Fake-entity testing is unavailable; ProtocolLib packet support failed closed."));
            return;
        }
        if (activeByTarget.size() >= settings.maximumActiveGlobal()) {
            staff.sendMessage(Component.text("The global cheat-tester session limit is active."));
            return;
        }
        long staffActive = activeByTarget.values().stream()
                .filter(session -> session.staffId.equals(staff.getUniqueId()))
                .count();
        if (staffActive >= settings.maximumActivePerStaff()) {
            staff.sendMessage(Component.text("You already control the maximum number of cheat-tester sessions."));
            return;
        }
        boolean assetLocked = false;
        if (type.mutatesTargetState()) {
            assetLocked = inventory.acquireExternalAssetLock(target.getUniqueId());
            if (!assetLocked) {
                staff.sendMessage(Component.text("The target inventory is busy with another durable moderation operation."));
                return;
            }
        }
        UUID sessionId = UUID.randomUUID();
        ActiveSession session = new ActiveSession(
                sessionId,
                staff.getUniqueId(),
                target.getUniqueId(),
                type,
                assetLocked,
                clock.instant()
        );
        ActiveSession competing = activeByTarget.putIfAbsent(target.getUniqueId(), session);
        if (competing != null) {
            releaseAssetLock(session);
            staff.sendMessage(Component.text("That target already has an active cheat-tester session."));
            return;
        }
        if (!target.getScheduler().execute(
                plugin,
                () -> prepareAndJournal(staff.getUniqueId(), target, session),
                () -> retireForRecovery(session, "Target retired before tester snapshot"),
                1L
        )) {
            retireWithoutJournal(session);
            staff.sendMessage(Component.text("The target could not be scheduled for safe tester preparation."));
        }
    }

    public void cancel(Player staff, UUID targetId) {
        if (!authorized(staff)) {
            return;
        }
        ActiveSession session = activeByTarget.get(targetId);
        if (session == null || (!session.staffId.equals(staff.getUniqueId()) && !staff.hasPermission("enthusiastaff.cheattester.cancel-any"))) {
            staff.sendMessage(Component.text("No controllable active tester exists for that target."));
            return;
        }
        finish(session, CheatTesterSessionState.CANCELLED, "cancelled by staff");
    }

    public List<String> statusLines(UUID staffId, boolean includeAll) {
        List<String> lines = new ArrayList<>();
        for (ActiveSession session : activeByTarget.values()) {
            if (includeAll || session.staffId.equals(staffId)) {
                lines.add(session.type.id() + " target=" + session.targetId + " age="
                        + Math.max(0, clock.instant().toEpochMilli() - session.startedAt.toEpochMilli()) + "ms");
            }
        }
        return List.copyOf(lines);
    }

    public void recoverOnlinePlayers() {
        if (closed.get()) {
            return;
        }
        submit(() -> {
            CheatTesterJournalStore loaded = store.get();
            if (loaded == null) {
                return;
            }
            try {
                for (CheatTesterJournalRecord record : loaded.activeForServer(serverId, MAX_RECOVERY_ROWS)) {
                    Player target = plugin.getServer().getPlayer(record.targetId());
                    if (target != null && target.isOnline()) {
                        scheduleRecovery(target, record);
                    }
                }
            } catch (RuntimeException exception) {
                plugin.getLogger().log(Level.SEVERE, "Cheat tester recovery scan failed", exception);
            }
        });
    }

    private void prepareAndJournal(UUID staffId, Player target, ActiveSession session) {
        if (!sessionCurrent(session) || closed.get()) {
            retireWithoutJournal(session);
            return;
        }
        try {
            PreparedProbe probe = prepareProbe(target, session.type);
            session.probe = probe;
            session.startPoint = StartPoint.capture(target.getLocation());
            if (session.type == CheatTesterType.FAKE_ENTITY) {
                session.fakeHandle = fakeEntities.create();
            }
            String snapshot = session.type.mutatesTargetState() ? snapshots.capture(target) : FAKE_SNAPSHOT;
            session.snapshot = snapshot;
            String configuration = configuration(session);
            Instant now = clock.instant();
            CheatTesterJournalStart start = new CheatTesterJournalStart(
                    session.sessionId,
                    serverId,
                    staffId,
                    target.getUniqueId(),
                    session.type,
                    snapshot,
                    configuration,
                    now,
                    now.plus(settings.sessionTimeout())
            );
            if (!submit(() -> persistStart(session, start))) {
                failBeforeMutation(session, "The bounded work queue is full; tester did not start.");
            }
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.WARNING, "Cheat tester preparation failed before durable mutation", exception);
            failBeforeMutation(session, safePreparationMessage(exception));
        }
    }

    private PreparedProbe prepareProbe(Player target, CheatTesterType type) {
        if (target.getOpenInventory().getType() != InventoryType.CRAFTING) {
            target.closeInventory();
        }
        PlayerInventory inventory = target.getInventory();
        return switch (type) {
            case TOTEM_REFILL -> {
                int totemSlot = firstMaterial(inventory.getStorageContents(), Material.TOTEM_OF_UNDYING);
                if (totemSlot < 0) {
                    throw new IllegalStateException("Target needs a totem in normal inventory for a no-injection refill probe");
                }
                yield new PreparedProbe(totemSlot, -1, -1);
            }
            case AUTO_ARMOR -> {
                ItemStack[] armor = inventory.getArmorContents();
                int armorSlot = firstNonEmpty(armor);
                int storageSlot = firstEmpty(inventory.getStorageContents());
                if (armorSlot < 0 || storageSlot < 0) {
                    throw new IllegalStateException("Target needs equipped armor and one empty inventory slot for an exact-restore armor probe");
                }
                yield new PreparedProbe(-1, armorSlot, storageSlot);
            }
            case VELOCITY, NO_FALL, FAKE_ENTITY -> PreparedProbe.NONE;
        };
    }

    private void persistStart(ActiveSession session, CheatTesterJournalStart start) {
        CheatTesterJournalStore loaded = store.get();
        if (loaded == null) {
            failBeforeMutation(session, "Tester storage is not ready; no target state was changed.");
            return;
        }
        try {
            CheatTesterJournalRecord record = loaded.start(start);
            if (!record.sessionId().equals(session.sessionId)) {
                failBeforeMutation(session, "A durable tester session already exists for that target.");
                return;
            }
            session.revision = record.revision();
            scheduleBegin(session);
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.SEVERE, "Cheat tester journal start failed", exception);
            failBeforeMutation(session, "Tester journal could not be committed; no target state was changed.");
        }
    }

    private void scheduleBegin(ActiveSession session) {
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
    }

    private void beginProbe(Player target, ActiveSession session) {
        if (!sessionCurrent(session) || !target.isOnline()) {
            retireForRecovery(session, "Target unavailable at probe start");
            return;
        }
        try {
            if (session.type.mutatesTargetState()) {
                target.setInvulnerable(true);
            }
            switch (session.type) {
                case TOTEM_REFILL -> beginTotemProbe(target, session);
                case AUTO_ARMOR -> beginArmorProbe(target, session);
                case VELOCITY -> beginVelocityProbe(target, session);
                case NO_FALL -> beginNoFallProbe(target, session);
                case FAKE_ENTITY -> beginFakeEntityProbe(target, session);
            }
            session.startedMutation.set(true);
            session.timeoutTask = plugin.getServer().getGlobalRegionScheduler().runDelayed(
                    plugin,
                    ignored -> finish(session, CheatTesterSessionState.RESTORED, "probe completed"),
                    timeoutTicks()
            );
            message(session.staffId, Component.text(
                    "Started " + session.type.displayName() + " evidence probe for " + target.getName() + ".",
                    NamedTextColor.AQUA
            ));
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.WARNING, "Cheat tester probe start failed after durable journal commit", exception);
            finish(session, CheatTesterSessionState.FAILED, "probe setup failed");
        }
    }

    private void beginTotemProbe(Player target, ActiveSession session) {
        PlayerInventory inventory = target.getInventory();
        int source = session.probe.sourceSlot();
        ItemStack[] storage = inventory.getStorageContents();
        if (source < 0 || source >= storage.length || storage[source] == null
                || storage[source].getType() != Material.TOTEM_OF_UNDYING) {
            throw new IllegalStateException("The prepared totem source changed before probe start");
        }
        inventory.setItemInOffHand(null);
        target.updateInventory();
    }

    private void beginArmorProbe(Player target, ActiveSession session) {
        PlayerInventory inventory = target.getInventory();
        ItemStack[] armor = inventory.getArmorContents();
        ItemStack[] storage = inventory.getStorageContents();
        int armorSlot = session.probe.armorSlot();
        int storageSlot = session.probe.storageSlot();
        if (armorSlot < 0 || armorSlot >= armor.length || storageSlot < 0 || storageSlot >= storage.length
                || armor[armorSlot] == null || armor[armorSlot].isEmpty()
                || (storage[storageSlot] != null && !storage[storageSlot].isEmpty())) {
            throw new IllegalStateException("The prepared armor slots changed before probe start");
        }
        storage[storageSlot] = armor[armorSlot].clone();
        armor[armorSlot] = null;
        inventory.setStorageContents(storage);
        inventory.setArmorContents(armor);
        target.updateInventory();
    }

    private void beginVelocityProbe(Player target, ActiveSession session) {
        Vector horizontal = target.getLocation().getDirection().setY(0.0D);
        if (horizontal.lengthSquared() < 0.0001D) {
            horizontal = new Vector(1.0D, 0.0D, 0.0D);
        } else {
            horizontal.normalize();
        }
        target.setVelocity(horizontal.multiply(settings.velocityHorizontal()).setY(settings.velocityVertical()));
    }

    private void beginNoFallProbe(Player target, ActiveSession session) {
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

    private void sampleNoFall(Player target, ActiveSession session) {
        if (!sessionCurrent(session) || session.finishing.get()) {
            cancel(session.sampleTask);
            return;
        }
        float current = target.getFallDistance();
        session.maxFallDistance = Math.max(session.maxFallDistance, current);
        if (!target.isOnGround() && session.previousFallDistance > 2.0F && current < 0.25F) {
            session.airborneFallResets.incrementAndGet();
        }
        session.previousFallDistance = current;
    }

    private void beginFakeEntityProbe(Player target, ActiveSession session) {
        if (!fakeEntities.available() || session.fakeHandle == null) {
            throw new IllegalStateException("ProtocolLib fake-entity support became unavailable");
        }
        Location location = fakeLocation(target);
        session.fakeLocation = StartPoint.capture(location);
        fakeEntityTargets.put(session.fakeHandle.entityId(), session.targetId);
        fakeEntities.show(target, session.fakeHandle, location);
        scheduleShowForStaff(session, location);
    }

    private void scheduleShowForStaff(ActiveSession session, Location location) {
        Player staff = plugin.getServer().getPlayer(session.staffId);
        if (staff == null || !staff.isOnline()) {
            return;
        }
        Location copy = location.clone();
        staff.getScheduler().execute(
                plugin,
                () -> {
                    if (fakeEntities.available() && staff.getWorld().equals(copy.getWorld())) {
                        fakeEntities.show(staff, session.fakeHandle, copy);
                    }
                },
                null,
                1L
        );
    }

    private Location fakeLocation(Player target) {
        Location base = target.getLocation();
        Vector direction = base.getDirection().setY(0.0D);
        if (direction.lengthSquared() < 0.0001D) {
            direction = new Vector(0.0D, 0.0D, 1.0D);
        } else {
            direction.normalize();
        }
        return base.clone().add(direction.multiply(settings.fakeEntityDistance()));
    }

    private void finish(ActiveSession session, CheatTesterSessionState terminalState, String reason) {
        if (session == null || !sessionCurrent(session) || !session.finishing.compareAndSet(false, true)) {
            return;
        }
        cancel(session.timeoutTask);
        cancel(session.sampleTask);
        Player target = plugin.getServer().getPlayer(session.targetId);
        if (target == null || !target.isOnline()) {
            if (session.type == CheatTesterType.FAKE_ENTITY) {
                completeFakeWithoutTarget(session, terminalState, reason);
            } else {
                retireForRecovery(session, "Target offline before tester restoration");
            }
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

    private void finishOnTarget(
            Player target,
            ActiveSession session,
            CheatTesterSessionState terminalState,
            String reason
    ) {
        String evidence = evidence(target, session, reason);
        if (session.type == CheatTesterType.FAKE_ENTITY) {
            hideFakeEntity(target, session);
            persistCompletion(session, terminalState, reason, evidence, true);
            return;
        }
        checkpointEvidenceThenRestore(target, session, terminalState, reason, evidence);
    }

    private void checkpointEvidenceThenRestore(
            Player target,
            ActiveSession session,
            CheatTesterSessionState terminalState,
            String reason,
            String evidence
    ) {
        Runnable restore = () -> restoreTarget(target, session, terminalState, reason, evidence);
        if (!submit(() -> {
            CheatTesterJournalStore loaded = store.get();
            if (loaded != null) {
                try {
                    Optional<CheatTesterJournalRecord> checkpoint = loaded.checkpointEvidence(
                            session.sessionId,
                            session.revision,
                            evidence,
                            clock.instant()
                    );
                    checkpoint.ifPresent(record -> session.revision = record.revision());
                } catch (RuntimeException exception) {
                    plugin.getLogger().log(Level.WARNING,
                            "Tester evidence checkpoint failed; restoration still takes priority", exception);
                }
            }
            scheduleTarget(session.targetId, restore,
                    () -> retireForRecovery(session, "Target retired before exact restoration"));
        })) {
            restore.run();
        }
    }

    private void restoreTarget(
            Player target,
            ActiveSession session,
            CheatTesterSessionState terminalState,
            String reason,
            String evidence
    ) {
        boolean restoreMovement = session.type == CheatTesterType.VELOCITY || session.type == CheatTesterType.NO_FALL;
        snapshots.restore(
                target,
                session.snapshot,
                restoreMovement,
                () -> persistCompletion(session, terminalState, reason, evidence, true),
                failure -> {
                    plugin.getLogger().log(Level.SEVERE,
                            "Cheat tester exact state restoration failed; durable recovery remains pending", failure);
                    retireForRecovery(session, "Exact restoration failed and remains pending");
                    message(session.staffId, Component.text(
                            "Tester restoration did not verify. Durable recovery remains pending; do not rerun the target.",
                            NamedTextColor.RED
                    ));
                }
        );
    }

    private void persistCompletion(
            ActiveSession session,
            CheatTesterSessionState terminalState,
            String reason,
            String evidence,
            boolean notify
    ) {
        if (!submit(() -> {
            CheatTesterJournalStore loaded = store.get();
            if (loaded == null) {
                retireForRecovery(session, "Tester storage unavailable during completion");
                return;
            }
            try {
                boolean completed = loaded.complete(
                        session.sessionId,
                        session.revision,
                        terminalState,
                        boundedReason(reason),
                        evidence,
                        clock.instant()
                );
                if (!completed) {
                    Optional<CheatTesterJournalRecord> current = loaded.activeForTarget(serverId, session.targetId);
                    if (current.isPresent() && current.orElseThrow().sessionId().equals(session.sessionId)) {
                        session.revision = current.orElseThrow().revision();
                        completed = loaded.complete(
                                session.sessionId,
                                session.revision,
                                terminalState,
                                boundedReason(reason),
                                evidence,
                                clock.instant()
                        );
                    }
                }
                if (!completed) {
                    throw new IllegalStateException("tester completion lost its durable state transition");
                }
                retireCompleted(session);
                if (notify) {
                    message(session.staffId, Component.text(
                            "Cheat Tester finished: " + summary(session, evidence),
                            NamedTextColor.GREEN
                    ));
                }
            } catch (RuntimeException exception) {
                plugin.getLogger().log(Level.SEVERE,
                        "Cheat tester state was restored but durable completion failed; recovery row remains authoritative",
                        exception);
                retireForRecovery(session, "Durable completion failed after restoration");
            }
        })) {
            retireForRecovery(session, "Bounded work queue full during tester completion");
        }
    }

    private void completeFakeWithoutTarget(
            ActiveSession session,
            CheatTesterSessionState terminalState,
            String reason
    ) {
        scheduleHideForStaff(session);
        fakeEntityTargets.remove(session.fakeHandle == null ? Integer.MIN_VALUE : session.fakeHandle.entityId());
        String evidence = evidenceWithoutTarget(session, reason);
        persistCompletion(session, terminalState, reason, evidence, false);
    }

    private void hideFakeEntity(Player target, ActiveSession session) {
        FakeEntityAdapter.Handle handle = session.fakeHandle;
        if (handle == null) {
            return;
        }
        fakeEntityTargets.remove(handle.entityId());
        fakeEntities.destroy(target, handle);
        scheduleHideForStaff(session);
    }

    private void scheduleHideForStaff(ActiveSession session) {
        Player staff = plugin.getServer().getPlayer(session.staffId);
        FakeEntityAdapter.Handle handle = session.fakeHandle;
        if (staff == null || handle == null) {
            return;
        }
        staff.getScheduler().execute(plugin, () -> fakeEntities.destroy(staff, handle), null, 1L);
    }

    private boolean recordFakeInteraction(UUID viewerId, int entityId, String action) {
        UUID targetId = fakeEntityTargets.get(entityId);
        if (targetId == null) {
            return false;
        }
        ActiveSession session = activeByTarget.get(targetId);
        if (session == null || session.fakeHandle == null || session.fakeHandle.entityId() != entityId) {
            return true;
        }
        if (!viewerId.equals(session.targetId) && !viewerId.equals(session.staffId)) {
            return true;
        }
        session.fakeInteractions.incrementAndGet();
        if ("ATTACK".equals(action)) {
            session.fakeAttacks.incrementAndGet();
        }
        session.firstInteractionMillis.compareAndSet(-1L,
                Math.max(0L, clock.instant().toEpochMilli() - session.startedAt.toEpochMilli()));
        return true;
    }

    private void fakeAdapterFailed() {
        for (ActiveSession session : List.copyOf(activeByTarget.values())) {
            if (session.type == CheatTesterType.FAKE_ENTITY) {
                finish(session, CheatTesterSessionState.FAILED, "ProtocolLib fake-entity adapter failed");
            }
        }
    }

    private String evidence(Player target, ActiveSession session, String reason) {
        Map<String, Object> evidence = baseEvidence(session, reason);
        switch (session.type) {
            case TOTEM_REFILL -> evidence.put("offhandTotemObserved",
                    target.getInventory().getItemInOffHand().getType() == Material.TOTEM_OF_UNDYING);
            case AUTO_ARMOR -> {
                ItemStack[] armor = target.getInventory().getArmorContents();
                int slot = session.probe.armorSlot();
                evidence.put("armorReequippedObserved", slot >= 0 && slot < armor.length
                        && armor[slot] != null && !armor[slot].isEmpty());
            }
            case VELOCITY -> evidence.put("displacement", displacement(target.getLocation(), session.startPoint));
            case NO_FALL -> {
                evidence.put("maximumFallDistance", session.maxFallDistance);
                evidence.put("airborneFallResets", session.airborneFallResets.get());
                evidence.put("displacement", displacement(target.getLocation(), session.startPoint));
            }
            case FAKE_ENTITY -> addFakeEvidence(evidence, session);
        }
        return serializeEvidence(evidence);
    }

    private String evidenceWithoutTarget(ActiveSession session, String reason) {
        Map<String, Object> evidence = baseEvidence(session, reason);
        addFakeEvidence(evidence, session);
        evidence.put("targetOnlineAtFinish", false);
        return serializeEvidence(evidence);
    }

    private Map<String, Object> baseEvidence(ActiveSession session, String reason) {
        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("tester", session.type.id());
        evidence.put("sessionId", session.sessionId.toString());
        evidence.put("durationMillis", Math.max(0L, clock.instant().toEpochMilli() - session.startedAt.toEpochMilli()));
        evidence.put("termination", boundedReason(reason));
        evidence.put("automaticPunishment", false);
        return evidence;
    }

    private void addFakeEvidence(Map<String, Object> evidence, ActiveSession session) {
        evidence.put("interactions", session.fakeInteractions.get());
        evidence.put("attacks", session.fakeAttacks.get());
        evidence.put("firstInteractionMillis", session.firstInteractionMillis.get());
    }

    private String serializeEvidence(Map<String, Object> evidence) {
        try {
            String serialized = json.writeValueAsString(evidence);
            if (serialized.length() > 32 * 1024) {
                throw new IllegalArgumentException("tester evidence exceeded safety limit");
            }
            return serialized;
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize tester evidence", exception);
        }
    }

    private String configuration(ActiveSession session) {
        Map<String, Object> configuration = new LinkedHashMap<>();
        configuration.put("schemaVersion", 1);
        configuration.put("tester", session.type.id());
        configuration.put("timeoutMillis", settings.sessionTimeout().toMillis());
        configuration.put("probeTicks", settings.probeTicks());
        if (session.fakeHandle != null) {
            configuration.put("fakeEntityId", session.fakeHandle.entityId());
            configuration.put("fakeEntityUuid", session.fakeHandle.entityUuid().toString());
        }
        try {
            return json.writeValueAsString(configuration);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize tester configuration", exception);
        }
    }

    private void scheduleRecovery(Player target, CheatTesterJournalRecord record) {
        if (closed.get()) {
            return;
        }
        ActiveSession recovered = ActiveSession.recovered(record);
        ActiveSession existing = activeByTarget.putIfAbsent(record.targetId(), recovered);
        if (existing != null) {
            return;
        }
        if (record.testerType().mutatesTargetState() && !inventory.acquireExternalAssetLock(record.targetId())) {
            activeByTarget.remove(record.targetId(), recovered);
            plugin.getLogger().warning("Deferred cheat tester recovery because another inventory operation owns the target lock");
            return;
        }
        recovered.assetLock = record.testerType().mutatesTargetState();
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

    private void recoverOnTarget(Player target, ActiveSession session, CheatTesterJournalRecord record) {
        if (record.testerType() == CheatTesterType.FAKE_ENTITY) {
            session.fakeHandle = handleFromConfiguration(record.configuration()).orElse(null);
            if (session.fakeHandle != null) {
                fakeEntities.destroy(target, session.fakeHandle);
                scheduleHideForStaff(session);
            }
            persistCompletion(
                    session,
                    CheatTesterSessionState.CANCELLED,
                    "recovered nonpersistent fake entity after runtime restart",
                    record.evidence() == null ? evidenceWithoutTarget(session, "runtime recovery") : record.evidence(),
                    false
            );
            return;
        }
        session.snapshot = record.snapshot();
        boolean restoreMovement = record.testerType() == CheatTesterType.VELOCITY
                || record.testerType() == CheatTesterType.NO_FALL;
        snapshots.restore(
                target,
                record.snapshot(),
                restoreMovement,
                () -> persistCompletion(
                        session,
                        CheatTesterSessionState.RESTORED,
                        "recovered exact tester state after runtime interruption",
                        record.evidence() == null ? serializeEvidence(baseEvidence(session, "runtime recovery")) : record.evidence(),
                        false
                ),
                failure -> {
                    plugin.getLogger().log(Level.SEVERE, "Cheat tester recovery failed; durable row remains active", failure);
                    retireForRecovery(session, "Recovery attempt failed");
                }
        );
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
        submit(() -> {
            CheatTesterJournalStore loaded = store.get();
            if (loaded == null) {
                return;
            }
            try {
                loaded.activeForTarget(serverId, player.getUniqueId())
                        .ifPresent(record -> scheduleRecovery(player, record));
            } catch (RuntimeException exception) {
                plugin.getLogger().log(Level.SEVERE, "Cheat tester join recovery lookup failed", exception);
            }
        });
    }

    private void failBeforeMutation(ActiveSession session, String message) {
        if (session.startedMutation.get()) {
            finish(session, CheatTesterSessionState.FAILED, "failure after mutation");
            return;
        }
        ActiveSession removed = activeByTarget.remove(session.targetId);
        if (removed == session) {
            releaseAssetLock(session);
            if (session.fakeHandle != null) {
                fakeEntityTargets.remove(session.fakeHandle.entityId());
            }
            message(session.staffId, Component.text(message, NamedTextColor.RED));
        }
    }

    private void retireWithoutJournal(ActiveSession session) {
        activeByTarget.remove(session.targetId, session);
        cancel(session.timeoutTask);
        cancel(session.sampleTask);
        releaseAssetLock(session);
    }

    private void retireCompleted(ActiveSession session) {
        activeByTarget.remove(session.targetId, session);
        fakeEntityTargets.entrySet().removeIf(entry -> entry.getValue().equals(session.targetId));
        cancel(session.timeoutTask);
        cancel(session.sampleTask);
        releaseAssetLock(session);
    }

    private void retireForRecovery(ActiveSession session, String reason) {
        activeByTarget.remove(session.targetId, session);
        fakeEntityTargets.entrySet().removeIf(entry -> entry.getValue().equals(session.targetId));
        cancel(session.timeoutTask);
        cancel(session.sampleTask);
        releaseAssetLock(session);
        plugin.getLogger().warning("Cheat tester session " + session.sessionId + " remains durably recoverable: " + reason);
    }

    private void releaseAssetLock(ActiveSession session) {
        if (session.assetLock) {
            session.assetLock = false;
            inventory.releaseExternalAssetLock(session.targetId);
        }
    }

    private boolean sessionCurrent(ActiveSession session) {
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
        Player target = plugin.getServer().getPlayer(targetId);
        if (target == null || !target.isOnline()
                || !target.getScheduler().execute(plugin, operation, retired, 1L)) {
            retired.run();
        }
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
        Player player = plugin.getServer().getPlayer(playerId);
        if (player == null) {
            return;
        }
        player.getScheduler().execute(plugin, () -> player.sendMessage(message), null, 1L);
    }

    private long timeoutTicks() {
        long byDuration = Math.max(1L, settings.sessionTimeout().toMillis() / 50L);
        return Math.max(1L, Math.min(settings.probeTicks(), byDuration));
    }

    private String summary(ActiveSession session, String evidence) {
        try {
            JsonNode node = json.readTree(evidence);
            return switch (session.type) {
                case TOTEM_REFILL -> "offhand refill observed=" + node.path("offhandTotemObserved").asBoolean(false);
                case AUTO_ARMOR -> "armor re-equip observed=" + node.path("armorReequippedObserved").asBoolean(false);
                case VELOCITY -> "displacement=" + String.format(java.util.Locale.ROOT, "%.2f", node.path("displacement").asDouble());
                case NO_FALL -> "airborne resets=" + node.path("airborneFallResets").asInt()
                        + ", max fall distance=" + String.format(java.util.Locale.ROOT, "%.2f", node.path("maximumFallDistance").asDouble());
                case FAKE_ENTITY -> "interactions=" + node.path("interactions").asInt()
                        + ", attacks=" + node.path("attacks").asInt();
            };
        } catch (JsonProcessingException exception) {
            return session.type.displayName() + " evidence saved";
        }
    }

    private static double displacement(Location current, StartPoint start) {
        if (start == null || current == null || current.getWorld() == null
                || !current.getWorld().getUID().equals(start.worldId())) {
            return -1.0D;
        }
        double dx = current.getX() - start.x();
        double dy = current.getY() - start.y();
        double dz = current.getZ() - start.z();
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
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

    private static String boundedReason(String reason) {
        if (reason == null || reason.isBlank()) {
            return "unspecified tester termination";
        }
        return reason.length() <= 255 ? reason : reason.substring(0, 255);
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
            plugin.getLogger().log(Level.SEVERE,
                    "ProtocolLib fake-entity adapter could not start; fake-entity Cheat Tester is disabled", failure);
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
    public void onPlace(BlockPlaceEvent event) {
        if (mutating(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDeath(PlayerDeathEvent event) {
        ActiveSession session = activeByTarget.get(event.getPlayer().getUniqueId());
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
        ActiveSession targetSession = activeByTarget.get(playerId);
        if (targetSession != null) {
            if (targetSession.type == CheatTesterType.FAKE_ENTITY) {
                finish(targetSession, CheatTesterSessionState.CANCELLED, "target disconnected");
            } else {
                retireForRecovery(targetSession, "Target disconnected during tester");
            }
        }
        for (ActiveSession session : List.copyOf(activeByTarget.values())) {
            if (session.staffId.equals(playerId)) {
                finish(session, CheatTesterSessionState.CANCELLED, "controlling staff disconnected");
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPluginDisable(PluginDisableEvent event) {
        if (event.getPlugin() != plugin && event.getPlugin().getName().equals("ProtocolLib")) {
            fakeEntities.close();
            fakeAdapterFailed();
        }
    }

    private boolean mutating(UUID playerId) {
        ActiveSession session = activeByTarget.get(playerId);
        return session != null && session.startedMutation.get() && session.type.mutatesTargetState();
    }

    private static void cancel(ScheduledTask task) {
        if (task != null) {
            try {
                task.cancel();
            } catch (RuntimeException ignored) {
                // Cleanup remains idempotent; durable journal is authoritative.
            }
        }
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        for (ActiveSession session : List.copyOf(activeByTarget.values())) {
            cancel(session.timeoutTask);
            cancel(session.sampleTask);
            if (session.fakeHandle != null) {
                fakeEntityTargets.remove(session.fakeHandle.entityId());
                Player target = plugin.getServer().getPlayer(session.targetId);
                if (target != null) {
                    target.getScheduler().execute(plugin,
                            () -> fakeEntities.destroy(target, session.fakeHandle), null, 1L);
                }
                scheduleHideForStaff(session);
            }
            releaseAssetLock(session);
        }
        activeByTarget.clear();
        selections.clear();
        fakeEntityTargets.clear();
        fakeEntities.close();
        // Active durable rows intentionally remain ACTIVE unless exact restoration and completion already verified.
        // They are recovered on the next storage bootstrap/login, including hot-reload fake-entity cleanup by journaled ID.
    }

    private record PreparedProbe(int sourceSlot, int armorSlot, int storageSlot) {
        private static final PreparedProbe NONE = new PreparedProbe(-1, -1, -1);
    }

    private record StartPoint(UUID worldId, double x, double y, double z) {
        private static StartPoint capture(Location location) {
            return new StartPoint(location.getWorld().getUID(), location.getX(), location.getY(), location.getZ());
        }
    }

    private static final class ActiveSession {
        private final UUID sessionId;
        private final UUID staffId;
        private final UUID targetId;
        private final CheatTesterType type;
        private final Instant startedAt;
        private final AtomicBoolean finishing = new AtomicBoolean();
        private final AtomicBoolean startedMutation = new AtomicBoolean();
        private final AtomicInteger airborneFallResets = new AtomicInteger();
        private final AtomicInteger fakeInteractions = new AtomicInteger();
        private final AtomicInteger fakeAttacks = new AtomicInteger();
        private final java.util.concurrent.atomic.AtomicLong firstInteractionMillis =
                new java.util.concurrent.atomic.AtomicLong(-1L);
        private volatile boolean assetLock;
        private volatile long revision;
        private volatile String snapshot;
        private volatile PreparedProbe probe = PreparedProbe.NONE;
        private volatile StartPoint startPoint;
        private volatile StartPoint fakeLocation;
        private volatile FakeEntityAdapter.Handle fakeHandle;
        private volatile ScheduledTask timeoutTask;
        private volatile ScheduledTask sampleTask;
        private volatile float previousFallDistance;
        private volatile float maxFallDistance;

        private ActiveSession(
                UUID sessionId,
                UUID staffId,
                UUID targetId,
                CheatTesterType type,
                boolean assetLock,
                Instant startedAt
        ) {
            this.sessionId = sessionId;
            this.staffId = staffId;
            this.targetId = targetId;
            this.type = type;
            this.assetLock = assetLock;
            this.startedAt = startedAt;
        }

        private static ActiveSession recovered(CheatTesterJournalRecord record) {
            ActiveSession session = new ActiveSession(
                    record.sessionId(),
                    record.staffId(),
                    record.targetId(),
                    record.testerType(),
                    false,
                    record.startedAt()
            );
            session.revision = record.revision();
            session.snapshot = record.snapshot();
            session.startedMutation.set(record.testerType().mutatesTargetState());
            session.finishing.set(true);
            return session;
        }
    }
}
