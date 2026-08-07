package net.enthusia.staff.paper.tester;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
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
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

/** Evidence-only cheat tester runtime with durable exact-state recovery. */
public final class CheatTesterManager implements Listener, AutoCloseable {
    private static final int MAX_RECOVERY_ROWS = 256;
    private static final String FAKE_SNAPSHOT = "{\"schemaVersion\":1,\"kind\":\"fake-entity\"}";

    private final JavaPlugin plugin;
    private final Clock clock;
    private final String serverId;
    private final InventoryCoordinator inventory;
    private final Supplier<CheatTesterJournalStore> store;
    private final CheatTesterSettings settings;
    private final CheatTesterSnapshotCodec snapshots;
    private final CheatTesterEvidence evidence;
    private final ObjectMapper json = new ObjectMapper();
    private final Map<UUID, CheatTesterSession> activeByTarget = new ConcurrentHashMap<>();
    private final java.util.concurrent.atomic.AtomicBoolean closed = new java.util.concurrent.atomic.AtomicBoolean();
    private final CheatTesterFakeEntityState fakeEntityState;
    private final FakeEntityAdapter fakeEntities;
    private final CheatTesterProbeEngine probes;
    private final CheatTesterControlState controls;
    private final CheatTesterRuntimeSupport runtime;

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
        this.serverId = CheatTesterRuntimeSupport.requireServerId(serverId);
        this.inventory = java.util.Objects.requireNonNull(inventory, "inventory");
        this.store = java.util.Objects.requireNonNull(store, "store");
        this.settings = java.util.Objects.requireNonNull(settings, "settings");
        this.runtime = new CheatTesterRuntimeSupport(plugin, workers, settings, closed);
        this.snapshots = new CheatTesterSnapshotCodec(plugin);
        this.evidence = new CheatTesterEvidence(clock, settings);
        this.fakeEntityState = new CheatTesterFakeEntityState(clock, activeByTarget);
        this.fakeEntities = installFakeEntityAdapter();
        this.controls = new CheatTesterControlState(clock, staffMode::active, settings, activeByTarget);
        this.probes = new CheatTesterProbeEngine(
                plugin,
                settings,
                fakeEntities,
                fakeEntityState,
                this::sampleActive,
                this::retireForRecovery
        );
        plugin.getServer().getPluginManager().registerEvents(new CheatTesterMutationGuard(this), plugin);
        plugin.getServer().getPluginManager().registerEvents(new CheatTesterLifecycleListener(plugin, this), plugin);
    }

    public boolean packetSupportAvailable() {
        return fakeEntities.available();
    }

    public CheatTesterType selected(UUID staffId) {
        return controls.selected(staffId);
    }

    public void select(Player staff, CheatTesterType type) {
        controls.select(staff, type, fakeEntities.available());
    }

    public void cycleSelection(Player staff) {
        controls.cycle(staff, fakeEntities.available());
    }

    public void showConfiguration(Player staff) {
        if (!controls.authorized(staff)) {
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
        if (!controls.canStart(staff, target, type, fakeEntities.available(), closed.get())) {
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
        if (registerSession(staff, session)) {
            schedulePreparation(staff, target, session);
        }
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
        if (!controls.authorized(staff)) {
            return;
        }
        CheatTesterSession session = activeByTarget.get(targetId);
        if (!controls.controllable(staff, session)) {
            staff.sendMessage(Component.text("No controllable active tester exists for that target."));
            return;
        }
        finish(session, CheatTesterSessionState.CANCELLED, "cancelled by staff");
    }

    public List<String> statusLines(UUID staffId, boolean includeAll) {
        return controls.statusLines(staffId, includeAll);
    }

    public void recoverOnlinePlayers() {
        if (!closed.get()) {
            runtime.submit(this::loadRecoverableSessions);
        }
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
        if (!readyForPreparation(session)) {
            retireWithoutJournal(session);
            return;
        }
        try {
            CheatTesterJournalStart start = prepareJournalStart(staffId, target, session);
            submitJournalStart(session, start);
        } catch (RuntimeException exception) {
            session.cancelJournalSubmission();
            plugin.getLogger().log(Level.WARNING, "Cheat tester preparation failed before durable mutation", exception);
            failBeforeMutation(session, CheatTesterRuntimeSupport.safePreparationMessage(exception));
        }
    }

    private boolean readyForPreparation(CheatTesterSession session) {
        return sessionCurrent(session) && !closed.get() && !session.finishing.get();
    }

    private CheatTesterJournalStart prepareJournalStart(
            UUID staffId,
            Player target,
            CheatTesterSession session
    ) {
        session.probe = probes.prepare(target, session.type);
        session.startPoint = CheatTesterSession.StartPoint.capture(target.getLocation());
        if (session.type == CheatTesterType.FAKE_ENTITY) {
            session.fakeHandle = fakeEntities.create();
        }
        session.snapshot = session.type.mutatesTargetState() ? snapshots.capture(target) : FAKE_SNAPSHOT;
        return journalStart(staffId, target, session);
    }

    private void submitJournalStart(CheatTesterSession session, CheatTesterJournalStart start) {
        if (!session.beginJournalSubmission()) {
            retireWithoutJournal(session);
            return;
        }
        if (!runtime.submit(() -> persistStart(session, start))) {
            session.cancelJournalSubmission();
            failBeforeMutation(session, "The bounded work queue is full; tester did not start.");
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

    private void persistStart(CheatTesterSession session, CheatTesterJournalStart start) {
        CheatTesterJournalStore loaded = store.get();
        if (loaded == null) {
            session.cancelJournalSubmission();
            failBeforeMutation(session, "Tester storage is not ready; no target state was changed.");
            return;
        }
        try {
            CheatTesterJournalRecord record = loaded.start(start);
            if (!record.sessionId().equals(session.sessionId)) {
                session.cancelJournalSubmission();
                failBeforeMutation(session,
                        "A durable tester session already exists for that target on " + record.serverId() + '.');
                return;
            }
            session.revision = record.revision();
            boolean shouldBegin = session.markJournaledAndShouldBegin();
            if (!shouldBegin || !sessionCurrent(session)) {
                completeOnWorker(
                        session,
                        CheatTesterSessionState.CANCELLED,
                        "tester ended before probe mutation",
                        evidence.withoutProbe(session, "tester ended before probe mutation"),
                        false
                );
                return;
            }
            scheduleBegin(session);
        } catch (RuntimeException exception) {
            session.cancelJournalSubmission();
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

    private void beginProbe(Player target, CheatTesterSession session) {
        if (!sessionCurrent(session) || !target.isOnline()) {
            retireForRecovery(session, "Target unavailable at probe start");
            return;
        }
        if (session.finishing.get()) {
            return;
        }
        try {
            if (session.type.mutatesTargetState()) {
                target.setInvulnerable(true);
            }
            probes.begin(target, session);
            session.startedMutation.set(true);
            scheduleTimeout(session);
            runtime.message(session.staffId, Component.text(
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
                runtime.timeoutTicks()
        );
    }

    private boolean sampleActive(CheatTesterSession session) {
        return sessionCurrent(session) && !session.finishing.get();
    }

    void finish(CheatTesterSession session, CheatTesterSessionState terminalState, String reason) {
        if (session == null) {
            return;
        }
        CheatTesterSession.FinishDisposition disposition = session.beginFinishing();
        if (disposition == CheatTesterSession.FinishDisposition.ALREADY_FINISHING) {
            return;
        }
        CheatTesterRuntimeSupport.cancel(session.timeoutTask);
        CheatTesterRuntimeSupport.cancel(session.sampleTask);
        if (disposition == CheatTesterSession.FinishDisposition.NO_JOURNAL) {
            retireWithoutJournal(session);
            return;
        }
        if (disposition == CheatTesterSession.FinishDisposition.WAIT_FOR_JOURNAL) {
            return;
        }
        plugin.getServer().getGlobalRegionScheduler().execute(
                plugin,
                () -> scheduleFinishOnTarget(session, terminalState, reason)
        );
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
            probes.hideFake(target, session);
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
        if (!runtime.submit(() -> checkpointThenScheduleRestore(session, captured, restore))) {
            restore.run();
        }
    }

    private void checkpointThenScheduleRestore(CheatTesterSession session, String captured, Runnable restore) {
        CheatTesterJournalStore loaded = store.get();
        if (loaded != null) {
            checkpointEvidence(loaded, session, captured);
        }
        runtime.scheduleTarget(
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
        runtime.message(session.staffId, Component.text(
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
        if (!runtime.submit(() -> completeOnWorker(session, terminalState, reason, captured, notify))) {
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
                runtime.message(session.staffId, Component.text(
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
        probes.scheduleHideForStaff(session);
        fakeEntityState.remove(session);
        persistCompletion(session, terminalState, reason, evidence.withoutTarget(session, reason), false);
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
        if (activeByTarget.putIfAbsent(record.targetId(), recovered) != null || !acquireRecoveryLock(record, recovered)) {
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
        probes.scheduleHideForStaff(session);
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

    void recover(Player player) {
        if (!closed.get()) {
            runtime.submit(() -> recoverById(player.getUniqueId()));
        }
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
            fakeEntityState.remove(session);
            runtime.message(session.staffId, Component.text(failureMessage, NamedTextColor.RED));
        }
    }

    private void retireWithoutJournal(CheatTesterSession session) {
        activeByTarget.remove(session.targetId, session);
        cancelSessionTasks(session);
        releaseAssetLock(session);
    }

    private void retireCompleted(CheatTesterSession session) {
        activeByTarget.remove(session.targetId, session);
        fakeEntityState.remove(session);
        cancelSessionTasks(session);
        releaseAssetLock(session);
    }

    void retireForRecovery(CheatTesterSession session, String reason) {
        activeByTarget.remove(session.targetId, session);
        fakeEntityState.remove(session);
        cancelSessionTasks(session);
        releaseAssetLock(session);
        plugin.getLogger().warning(
                "Cheat tester session " + session.sessionId + " remains durably recoverable: " + reason
        );
    }

    private static void cancelSessionTasks(CheatTesterSession session) {
        CheatTesterRuntimeSupport.cancel(session.timeoutTask);
        CheatTesterRuntimeSupport.cancel(session.sampleTask);
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

    private FakeEntityAdapter installFakeEntityAdapter() {
        if (!plugin.getServer().getPluginManager().isPluginEnabled("ProtocolLib")) {
            plugin.getLogger().warning("ProtocolLib is unavailable; fake-entity Cheat Tester is disabled fail-closed");
            return FakeEntityAdapter.unavailable();
        }
        try {
            return ProtocolLibFakeEntityAdapter.install(plugin, fakeEntityState::recordInteraction, this::fakeAdapterFailed);
        } catch (RuntimeException | LinkageError failure) {
            plugin.getLogger().log(
                    Level.SEVERE,
                    "ProtocolLib fake-entity adapter could not start; fake-entity Cheat Tester is disabled",
                    failure
            );
            return FakeEntityAdapter.unavailable();
        }
    }

    CheatTesterSession activeSession(UUID playerId) {
        return activeByTarget.get(playerId);
    }

    List<CheatTesterSession> activeSessions() {
        return List.copyOf(activeByTarget.values());
    }

    void clearSelection(UUID playerId) {
        controls.clearSelection(playerId);
    }

    void protocolLibDisabled() {
        fakeEntities.close();
        fakeAdapterFailed();
    }

    boolean mutating(UUID playerId) {
        CheatTesterSession session = activeByTarget.get(playerId);
        return session != null && session.startedMutation.get() && session.type.mutatesTargetState();
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        for (CheatTesterSession session : List.copyOf(activeByTarget.values())) {
            cancelSessionTasks(session);
            fakeEntityState.remove(session);
            releaseAssetLock(session);
        }
        activeByTarget.clear();
        controls.clear();
        fakeEntityState.clear();
        fakeEntities.close();
        // Durable ACTIVE rows remain authoritative until the next healthy runtime verifies cleanup/restoration.
    }
}
