package net.enthusia.staff.paper.inventory;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.logging.Level;
import net.enthusia.staff.common.CaseId;
import net.enthusia.staff.common.IdempotencyKey;
import net.enthusia.staff.domain.OperationalMode;
import net.enthusia.staff.domain.auth.Actor;
import net.enthusia.staff.domain.auth.AuthorizationPolicy;
import net.enthusia.staff.domain.auth.ModerationAction;
import net.enthusia.staff.domain.inventory.ConfiscatedAssetReservation;
import net.enthusia.staff.domain.inventory.ConfiscatedAssetSnapshot;
import net.enthusia.staff.domain.inventory.InventoryConfiscationCommitRequest;
import net.enthusia.staff.domain.inventory.InventoryConfiscationSession;
import net.enthusia.staff.domain.inventory.InventoryConfiscationStart;
import net.enthusia.staff.domain.inventory.InventoryConfiscationStartRequest;
import net.enthusia.staff.domain.inventory.InventoryFinalizeResult;
import net.enthusia.staff.domain.inventory.InventoryObservation;
import net.enthusia.staff.domain.inventory.InventoryPatch;
import net.enthusia.staff.domain.inventory.InventoryPreparation;
import net.enthusia.staff.domain.inventory.InventoryPrepareRequest;
import net.enthusia.staff.domain.ports.InventoryJournalStore;
import net.enthusia.staff.paper.auth.PaperActorResolver;
import net.enthusia.staff.paper.economy.CurrencyGateway;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

public final class ConfiscationCoordinator implements Listener, AutoCloseable {
    private static final Duration LEASE_DURATION = Duration.ofMinutes(2);
    private static final long RENEWAL_TICKS = 20L * 30L;
    private static final long SELECTION_TIMEOUT_TICKS = 20L * 60L * 5L;
    private static final int CONTENT_SLOTS = 45;
    private static final int BACK_SLOT = 45;
    private static final int PREVIOUS_SLOT = 47;
    private static final int CONFIRM_SLOT = 49;
    private static final int NEXT_SLOT = 51;
    private static final int CANCEL_SLOT = 53;

    private final JavaPlugin plugin;
    private final Clock clock;
    private final String scopeId;
    private final String serverId;
    private final Supplier<OperationalMode> mode;
    private final AuthorizationPolicy authorization;
    private final Supplier<InventoryJournalStore> store;
    private final ExecutorService workers;
    private final InventoryCoordinator inventories;
    private final CurrencyGateway currency;
    private final InventoryImageCodec imageCodec = new InventoryImageCodec();
    private final ConfiscatedAssetsCodec assetsCodec = new ConfiscatedAssetsCodec();
    private final Map<UUID, SelectionSession> sessions = new ConcurrentHashMap<>();
    private final Map<UUID, RestorationContext> restorations = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> viewerOperations = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> targetOperations = new ConcurrentHashMap<>();
    private final AtomicBoolean closed = new AtomicBoolean();

    public ConfiscationCoordinator(
            JavaPlugin plugin,
            Clock clock,
            String scopeId,
            String serverId,
            Supplier<OperationalMode> mode,
            AuthorizationPolicy authorization,
            Supplier<InventoryJournalStore> store,
            ExecutorService workers,
            InventoryCoordinator inventories,
            CurrencyGateway currency
    ) {
        this.plugin = java.util.Objects.requireNonNull(plugin, "plugin");
        this.clock = java.util.Objects.requireNonNull(clock, "clock");
        this.scopeId = requireIdentifier(scopeId, "scopeId");
        this.serverId = requireIdentifier(serverId, "serverId");
        this.mode = java.util.Objects.requireNonNull(mode, "mode");
        this.authorization = java.util.Objects.requireNonNull(authorization, "authorization");
        this.store = java.util.Objects.requireNonNull(store, "store");
        this.workers = java.util.Objects.requireNonNull(workers, "workers");
        this.inventories = java.util.Objects.requireNonNull(inventories, "inventories");
        this.currency = java.util.Objects.requireNonNull(currency, "currency");
    }

    public void open(Player viewer, Player target, CaseId caseId) {
        if (viewer == null || target == null || caseId == null) {
            throw new IllegalArgumentException("viewer, target, and caseId must be present");
        }
        if (!authorize(viewer, ModerationAction.APPLY_CASE_CONFISCATION)) {
            viewer.sendMessage(Component.text("You do not have case confiscation authority."));
            return;
        }
        if (mode.get() != OperationalMode.ACTIVE) {
            viewer.sendMessage(Component.text(
                    "Item confiscation is available only while moderation is ACTIVE."
            ));
            return;
        }
        if (!target.isOnline()) {
            viewer.sendMessage(Component.text(
                    "Item confiscation selection requires the target on this backend."
            ));
            return;
        }
        UUID operationId = UUID.randomUUID();
        if (viewerOperations.putIfAbsent(viewer.getUniqueId(), operationId) != null
                || targetOperations.putIfAbsent(target.getUniqueId(), operationId) != null) {
            viewerOperations.remove(viewer.getUniqueId(), operationId);
            targetOperations.remove(target.getUniqueId(), operationId);
            viewer.sendMessage(Component.text(
                    "The viewer or target already has an active confiscation selection."
            ));
            return;
        }
        onEntity(
                target,
                () -> lockAndCapture(viewer, target, caseId, operationId),
                () -> {
                    clearReservation(viewer.getUniqueId(), target.getUniqueId(), operationId);
                    message(viewer, "The target left before confiscation could acquire its locks.");
                }
        );
    }

    public void restore(Player viewer, Player target, CaseId caseId) {
        if (viewer == null || target == null || caseId == null) {
            throw new IllegalArgumentException("viewer, target, and caseId must be present");
        }
        if (!authorize(viewer, ModerationAction.RESTORE_ASSETS)) {
            viewer.sendMessage(Component.text("Only the Founder may restore confiscated assets."));
            return;
        }
        if (mode.get() != OperationalMode.ACTIVE) {
            viewer.sendMessage(Component.text(
                    "Item restoration is available only while moderation is ACTIVE."
            ));
            return;
        }
        if (!target.isOnline()) {
            viewer.sendMessage(Component.text(
                    "Confiscated-item restoration requires the target on this backend."
            ));
            return;
        }
        UUID operationId = UUID.randomUUID();
        if (viewerOperations.putIfAbsent(viewer.getUniqueId(), operationId) != null
                || targetOperations.putIfAbsent(target.getUniqueId(), operationId) != null) {
            viewerOperations.remove(viewer.getUniqueId(), operationId);
            targetOperations.remove(target.getUniqueId(), operationId);
            viewer.sendMessage(Component.text(
                    "The viewer or target already has an active asset operation."
            ));
            return;
        }
        RestorationContext context = new RestorationContext(viewer, target, caseId, operationId);
        restorations.put(operationId, context);
        onEntity(
                target,
                () -> lockAndCaptureRestoration(context),
                () -> failRestorationBeforePatch(
                        context,
                        "The target left before restoration could acquire its locks."
                )
        );
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player viewer)
                || !(event.getView().getTopInventory().getHolder(false)
                instanceof ConfiscationInventoryHolder holder)) {
            return;
        }
        event.setCancelled(true);
        SelectionSession session = sessions.get(holder.operationId());
        if (session == null || !holder.viewerId().equals(viewer.getUniqueId())
                || !session.selecting()) {
            return;
        }
        int rawSlot = event.getRawSlot();
        if (rawSlot < 0 || rawSlot >= event.getView().getTopInventory().getSize()) {
            return;
        }
        if (rawSlot == CANCEL_SLOT) {
            cancel(session, "VIEWER_CANCELLED", "Staff cancelled confiscation selection", true);
            return;
        }
        if (rawSlot == CONFIRM_SLOT) {
            confirm(session);
            return;
        }
        if (rawSlot == BACK_SLOT && holder.containerPath().isPresent()) {
            ItemPath current = holder.containerPath().orElseThrow();
            Optional<ItemPath> parent = current.nestedSlots().isEmpty()
                    ? Optional.empty()
                    : Optional.of(current.parent());
            openView(session, parent, 0);
            return;
        }
        if (rawSlot == PREVIOUS_SLOT && holder.page() > 0) {
            openView(session, holder.containerPath(), holder.page() - 1);
            return;
        }
        List<ViewEntry> entries = viewEntries(session, holder.containerPath());
        int index = holder.page() * CONTENT_SLOTS + rawSlot;
        if (rawSlot == NEXT_SLOT && (holder.page() + 1) * CONTENT_SLOTS < entries.size()) {
            openView(session, holder.containerPath(), holder.page() + 1);
            return;
        }
        if (rawSlot >= CONTENT_SLOTS || index < 0 || index >= entries.size()) {
            return;
        }
        ViewEntry selected = entries.get(index);
        if (selected.item() == null || selected.item().isEmpty()) {
            return;
        }
        if (session.selectedExact(selected.path())) {
            session.deselect(selected.path());
            openView(session, holder.containerPath(), holder.page());
            return;
        }
        if (session.coveredByAncestor(selected.path())) {
            viewer.sendMessage(Component.text(
                    "Deselect the selected parent container before changing a child."
            ));
            return;
        }
        if (NestedInventorySelection.isContainer(selected.item()) && !event.isShiftClick()) {
            openView(session, Optional.of(selected.path()), 0);
            return;
        }
        session.select(selected.path(), selected.item());
        openView(session, holder.containerPath(), holder.page());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder(false)
                instanceof ConfiscationInventoryHolder) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player viewer)
                || !(event.getInventory().getHolder(false)
                instanceof ConfiscationInventoryHolder holder)) {
            return;
        }
        SelectionSession session = sessions.get(holder.operationId());
        if (session == null || session.consumeSuppressedClose()) {
            return;
        }
        if (session.selecting()) {
            cancel(
                    session,
                    "VIEWER_CLOSED",
                    "Staff closed the confiscation selector before confirmation",
                    false
            );
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        UUID viewerOperation = viewerOperations.get(event.getPlayer().getUniqueId());
        if (viewerOperation != null) {
            SelectionSession session = sessions.get(viewerOperation);
            if (session != null && session.selecting()) {
                cancel(
                        session,
                        "VIEWER_DISCONNECTED",
                        "Staff disconnected before confirming confiscation",
                        false
                );
            }
        }
        UUID targetOperation = targetOperations.get(event.getPlayer().getUniqueId());
        if (targetOperation != null) {
            RestorationContext restoration = restorations.get(targetOperation);
            if (restoration != null) {
                if (restoration.beforePatch()) {
                    failRestorationBeforePatch(
                            restoration,
                            "Target disconnected before the restoration patch was durable."
                    );
                } else if (restoration.patchPreparationInFlight()) {
                    restoration.targetDeparted();
                    if (restoration.localLocksWereAcquired()) {
                        releaseLocalLocks(
                                restoration.targetId(),
                                restoration.operationId()
                        );
                    }
                } else {
                    pendingRestorationAfterTargetDeparture(restoration);
                }
                return;
            }
            SelectionSession session = sessions.get(targetOperation);
            if (session != null && session.selecting()) {
                cancel(
                        session,
                        "TARGET_DISCONNECTED",
                        "Target disconnected during confiscation selection",
                        true
                );
            } else if (session != null) {
                session.stopTasks();
                releaseLocalLocks(session.targetId(), session.operationId());
                removeSession(session);
                message(
                        session.viewer(),
                        "The target left; the durable confiscation patch will recover before interaction."
                );
            }
        }
    }

    private void lockAndCaptureRestoration(RestorationContext context) {
        if (!inventories.acquireExternalAssetLock(context.targetId())) {
            failRestorationBeforePatch(context, "Another inventory operation owns this target.");
            return;
        }
        if (!currency.acquireMovementLock(
                context.targetId(),
                context.operationId(),
                LEASE_DURATION
        )) {
            inventories.releaseExternalAssetLock(context.targetId());
            failRestorationBeforePatch(context, "Currency movement lock could not be acquired.");
            return;
        }
        context.localLocksAcquired();
        context.target().closeInventory();
        try {
            InventoryImage current = imageCodec.capture(context.target());
            InventoryImageCodec.EncodedImage encoded = imageCodec.encodeWithChecksum(current);
            if (!submit(() -> reserveAndObserveRestoration(context, current, encoded))) {
                failRestorationBeforePatch(context, "Restoration queue is full.");
            }
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.SEVERE, "Restoration before snapshot failed", exception);
            failRestorationBeforePatch(context, "Restoration snapshot failed; no assets changed.");
        }
    }

    private void reserveAndObserveRestoration(
            RestorationContext context,
            InventoryImage current,
            InventoryImageCodec.EncodedImage encoded
    ) {
        InventoryJournalStore loaded = store.get();
        if (loaded == null) {
            failRestorationBeforePatch(context, "Inventory storage is unavailable.");
            return;
        }
        try {
            ConfiscatedAssetReservation reservation = loaded.reserveRestoration(
                    context.caseId(),
                    context.operationId(),
                    clock.instant()
            );
            if (reservation.snapshots().isEmpty()) {
                failRestorationBeforePatch(context, reservation.detail());
                return;
            }
            if (!context.reserved()) {
                loaded.cancelRestoration(
                        context.caseId(),
                        context.operationId(),
                        clock.instant()
                );
                releaseRestoration(
                        context,
                        "Restoration was cancelled while its reservation was opening."
                );
                return;
            }
            InventoryObservation observation = loaded.recordObservation(
                    context.targetId(),
                    scopeId,
                    serverId,
                    encoded.checksum(),
                    encoded.bytes(),
                    clock.instant()
            );
            onEntity(
                    context.target(),
                    () -> buildRestoration(context, current, observation, reservation.snapshots()),
                    () -> failRestorationBeforePatch(
                            context,
                            "Target left before the restoration patch was built."
                    )
            );
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.SEVERE, "Confiscated asset reservation failed", exception);
            failRestorationBeforePatch(
                    context,
                    "Confiscated assets could not be reserved; no assets changed."
            );
        }
    }

    private void buildRestoration(
            RestorationContext context,
            InventoryImage captured,
            InventoryObservation observation,
            List<ConfiscatedAssetSnapshot> snapshots
    ) {
        try {
            InventoryImage current = imageCodec.capture(context.target());
            InventoryImageCodec.EncodedImage currentBytes = imageCodec.encodeWithChecksum(current);
            if (!currentBytes.checksum().equals(observation.checksum())) {
                failRestorationBeforePatch(
                        context,
                        "Target inventory changed while restoration was being reserved."
                );
                return;
            }
            List<ConfiscatedAssetEntry> entries = new ArrayList<>();
            for (ConfiscatedAssetSnapshot snapshot : snapshots) {
                if (!assetsCodec.checksum(snapshot.assets()).equals(snapshot.checksum())) {
                    throw new IllegalArgumentException(
                            "confiscated asset snapshot checksum does not match its bytes"
                    );
                }
                entries.addAll(assetsCodec.decode(snapshot.assets()));
            }
            NestedInventorySelection.RestorationResult restoration =
                    NestedInventorySelection.restore(captured, entries);
            InventoryImageCodec.EncodedImage replacement =
                    imageCodec.encodeWithChecksum(restoration.replacement());
            InventoryPrepareRequest request = new InventoryPrepareRequest(
                    context.operationId(),
                    new IdempotencyKey("inventory:restore:" + context.operationId()).value(),
                    context.targetId(),
                    scopeId,
                    serverId,
                    context.viewerId(),
                    Optional.of(context.caseId().value()),
                    "RESTORE_CONFISCATED",
                    observation.revision(),
                    observation.checksum(),
                    observation.snapshot(),
                    replacement.checksum(),
                    replacement.bytes(),
                    restoration.changedRootSlots(),
                    false
            );
            context.preparingPatch();
            if (!submit(() -> prepareRestorationPatch(
                    context,
                    request,
                    restoration.replacement()
            ))) {
                failRestorationBeforePatch(context, "Restoration patch queue is full.");
            }
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.SEVERE, "Confiscated asset restoration planning failed", exception);
            failRestorationBeforePatch(
                    context,
                    "Confiscated assets cannot fit safely; no assets changed."
            );
        }
    }

    private void prepareRestorationPatch(
            RestorationContext context,
            InventoryPrepareRequest request,
            InventoryImage replacement
    ) {
        InventoryJournalStore loaded = store.get();
        if (loaded == null) {
            failRestorationBeforePatch(context, "Inventory storage became unavailable.");
            return;
        }
        InventoryPatch patch = null;
        try {
            InventoryPreparation prepared = loaded.prepare(request, LEASE_DURATION, clock.instant());
            patch = prepared.patch().orElse(null);
            if (patch == null) {
                failRestorationBeforePatch(context, prepared.detail());
                return;
            }
            context.patchPrepared();
            if (context.targetHasDeparted() || !context.target().isOnline()) {
                pendingRestorationAfterTargetDeparture(context);
                return;
            }
            InventoryPatch claimed = loaded.claimForApply(
                    patch.patchId(),
                    patch.operationId(),
                    LEASE_DURATION,
                    clock.instant()
            ).orElse(null);
            if (claimed == null) {
                quarantineRestoration(
                        context,
                        patch,
                        "RESTORATION_CLAIM_FAILED",
                        "Prepared restoration patch lost its lease"
                );
                return;
            }
            context.applying();
            onEntity(
                    context.target(),
                    () -> applyRestorationPatch(context, claimed, replacement),
                    () -> pendingRestorationAfterTargetDeparture(context)
            );
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.SEVERE, "Restoration patch preparation failed", exception);
            if (patch == null) {
                failRestorationBeforePatch(
                        context,
                        "Restoration patch could not be prepared; no assets changed."
                );
            } else {
                quarantineRestoration(
                        context,
                        patch,
                        "RESTORATION_PREPARE_EXCEPTION",
                        "Prepared restoration patch could not be claimed safely"
                );
            }
        }
    }

    private void applyRestorationPatch(
            RestorationContext context,
            InventoryPatch patch,
            InventoryImage replacement
    ) {
        try {
            InventoryImage current = imageCodec.capture(context.target());
            InventoryImageCodec.EncodedImage currentBytes = imageCodec.encodeWithChecksum(current);
            if (!currentBytes.checksum().equals(patch.expectedChecksum())) {
                quarantineRestoration(
                        context,
                        patch,
                        "RESTORATION_STATE_CHANGED",
                        "Target inventory changed after restoration was prepared"
                );
                return;
            }
            imageCodec.apply(context.target(), replacement);
            InventoryImage applied = imageCodec.capture(context.target());
            InventoryImageCodec.EncodedImage appliedBytes = imageCodec.encodeWithChecksum(applied);
            if (!submit(() -> finalizeRestorationPatch(context, patch, appliedBytes))) {
                alertStaff(
                        "Restoration reached the target for " + patch.operationId()
                                + " but finalization queue is full."
                );
            }
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.SEVERE, "Restoration inventory application failed", exception);
            quarantineRestoration(
                    context,
                    patch,
                    "RESTORATION_APPLY_FAILED",
                    "Inventory restoration application failed"
            );
        }
    }

    private void finalizeRestorationPatch(
            RestorationContext context,
            InventoryPatch patch,
            InventoryImageCodec.EncodedImage applied
    ) {
        try {
            InventoryJournalStore loaded = store.get();
            if (loaded == null) {
                throw new IllegalStateException("inventory storage became unavailable");
            }
            InventoryFinalizeResult result = loaded.finalizeApplied(
                    patch.patchId(),
                    patch.operationId(),
                    patch.fencingToken(),
                    applied.checksum(),
                    applied.bytes(),
                    clock.instant()
            );
            if (result.status() != InventoryFinalizeResult.Status.COMMITTED
                    && result.status() != InventoryFinalizeResult.Status.REPLAYED) {
                message(
                        context.viewer(),
                        "Restoration reached an ambiguous durable state; do not repeat it."
                );
                alertStaff(
                        "Restoration operation " + context.operationId()
                                + " requires recovery: " + result.detail()
                );
                return;
            }
            boolean marked = loaded.finalizeRestoration(
                    context.caseId(),
                    context.operationId(),
                    applied.checksum(),
                    clock.instant()
            );
            releaseRestoration(
                    context,
                    marked
                            ? "Confiscated items restored and verified for case "
                                    + context.caseId() + '.'
                            : "Items restored and verified; the case marker will reconcile automatically."
            );
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.SEVERE, "Restoration finalization failed", exception);
            message(
                    context.viewer(),
                    "Restoration reached the target but final verification is pending; do not repeat it."
            );
        }
    }

    private void quarantineRestoration(
            RestorationContext context,
            InventoryPatch patch,
            String reasonCode,
            String detail
    ) {
        if (!submit(() -> {
            try {
                InventoryJournalStore loaded = store.get();
                if (loaded != null) {
                    loaded.quarantine(
                            patch.patchId(),
                            patch.operationId(),
                            patch.fencingToken(),
                            reasonCode,
                            detail,
                            clock.instant()
                    );
                }
            } catch (RuntimeException exception) {
                plugin.getLogger().log(Level.SEVERE, "Restoration quarantine failed", exception);
            }
            message(
                    context.viewer(),
                    "Item restoration was quarantined for staff recovery; do not repeat it."
            );
            alertStaff(
                    "Restoration operation " + context.operationId()
                            + " was quarantined: " + detail
            );
        })) {
            alertStaff("Restoration quarantine queue is full for " + context.operationId() + '.');
        }
    }

    private void failRestorationBeforePatch(RestorationContext context, String detail) {
        if (!context.failBeforePatch()) {
            return;
        }
        Runnable cleanup = () -> {
            try {
                InventoryJournalStore loaded = store.get();
                if (loaded != null && context.reservationMade()) {
                    loaded.cancelRestoration(
                            context.caseId(),
                            context.operationId(),
                            clock.instant()
                    );
                }
            } catch (RuntimeException exception) {
                plugin.getLogger().log(Level.SEVERE, "Restoration reservation cleanup failed", exception);
            } finally {
                releaseRestoration(context, detail + " No assets changed.");
            }
        };
        if (!submit(cleanup)) {
            releaseRestoration(
                    context,
                    detail + " No assets changed; the reservation requires recovery."
            );
        }
    }

    private void pendingRestorationAfterTargetDeparture(RestorationContext context) {
        if (!context.finish()) {
            return;
        }
        releaseLocalLocks(context.targetId(), context.operationId());
        removeRestoration(context);
        message(
                context.viewer(),
                "Target left; the durable restoration patch will apply before interaction."
        );
    }

    private void releaseRestoration(RestorationContext context, String detail) {
        if (!context.finish()) {
            return;
        }
        if (context.localLocksWereAcquired()) {
            releaseLocalLocks(context.targetId(), context.operationId());
        }
        removeRestoration(context);
        message(context.viewer(), detail);
    }

    private void removeRestoration(RestorationContext context) {
        restorations.remove(context.operationId(), context);
        clearReservation(context.viewerId(), context.targetId(), context.operationId());
    }

    private void lockAndCapture(
            Player viewer,
            Player target,
            CaseId caseId,
            UUID operationId
    ) {
        if (!inventories.acquireExternalAssetLock(target.getUniqueId())) {
            clearReservation(viewer.getUniqueId(), target.getUniqueId(), operationId);
            viewer.sendMessage(Component.text("Another inventory operation owns this target."));
            return;
        }
        if (!currency.acquireMovementLock(
                target.getUniqueId(),
                operationId,
                LEASE_DURATION
        )) {
            inventories.releaseExternalAssetLock(target.getUniqueId());
            clearReservation(viewer.getUniqueId(), target.getUniqueId(), operationId);
            viewer.sendMessage(Component.text("Currency movement lock could not be acquired."));
            return;
        }
        target.closeInventory();
        InventoryImage before;
        InventoryImageCodec.EncodedImage encoded;
        try {
            before = imageCodec.capture(target);
            encoded = imageCodec.encodeWithChecksum(before);
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.SEVERE, "Confiscation before snapshot failed", exception);
            releaseLocalLocks(target.getUniqueId(), operationId);
            clearReservation(viewer.getUniqueId(), target.getUniqueId(), operationId);
            viewer.sendMessage(Component.text("Confiscation snapshot failed; no assets changed."));
            return;
        }
        InventoryConfiscationStartRequest request = new InventoryConfiscationStartRequest(
                operationId,
                new IdempotencyKey("inventory:confiscation:" + operationId).value(),
                target.getUniqueId(),
                scopeId,
                serverId,
                viewer.getUniqueId(),
                caseId,
                encoded.checksum(),
                encoded.bytes(),
                clock.instant()
        );
        if (!submit(() -> beginDurableSelection(viewer, target, before, request))) {
            releaseLocalLocks(target.getUniqueId(), operationId);
            clearReservation(viewer.getUniqueId(), target.getUniqueId(), operationId);
            viewer.sendMessage(Component.text(
                    "Confiscation queue is full; no durable operation was created."
            ));
        }
    }

    private void beginDurableSelection(
            Player viewer,
            Player target,
            InventoryImage before,
            InventoryConfiscationStartRequest request
    ) {
        InventoryJournalStore loaded = store.get();
        if (loaded == null) {
            failStart(viewer, target, request.operationId(), "Inventory storage is unavailable.");
            return;
        }
        try {
            InventoryConfiscationStart start = loaded.beginConfiscation(
                    request,
                    LEASE_DURATION,
                    clock.instant()
            );
            InventoryConfiscationSession durable = start.session().orElse(null);
            if (durable == null) {
                failStart(viewer, target, request.operationId(), start.detail());
                return;
            }
            if (!viewer.isOnline() || !target.isOnline()) {
                loaded.cancelConfiscation(
                        durable.operationId(),
                        durable.fencingToken(),
                        "PARTICIPANT_LEFT_DURING_START",
                        "Viewer or target left while the durable selection was starting",
                        clock.instant()
                );
                failStart(
                        viewer,
                        target,
                        request.operationId(),
                        "Viewer or target left while confiscation was starting."
                );
                return;
            }
            SelectionSession session = new SelectionSession(viewer, target, durable, before);
            sessions.put(durable.operationId(), session);
            scheduleTasks(session);
            openView(session, Optional.empty(), 0);
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.SEVERE, "Durable confiscation selection start failed", exception);
            failStart(
                    viewer,
                    target,
                    request.operationId(),
                    "Confiscation could not commit its before snapshot."
            );
        }
    }

    private void failStart(Player viewer, Player target, UUID operationId, String detail) {
        releaseLocalLocks(target.getUniqueId(), operationId);
        clearReservation(viewer.getUniqueId(), target.getUniqueId(), operationId);
        message(viewer, detail + " No assets changed.");
    }

    private void scheduleTasks(SelectionSession session) {
        session.renewalTask(plugin.getServer().getGlobalRegionScheduler().runAtFixedRate(
                plugin,
                ignored -> renew(session),
                RENEWAL_TICKS,
                RENEWAL_TICKS
        ));
        session.timeoutTask(plugin.getServer().getGlobalRegionScheduler().runDelayed(
                plugin,
                ignored -> cancel(
                        session,
                        "SELECTION_TIMEOUT",
                        "Confiscation selection exceeded five minutes",
                        true
                ),
                SELECTION_TIMEOUT_TICKS
        ));
    }

    private void renew(SelectionSession session) {
        if (!session.selecting()) {
            return;
        }
        if (!submit(() -> {
            InventoryJournalStore loaded = store.get();
            if (loaded == null || loaded.renewConfiscation(
                    session.operationId(),
                    session.fencingToken(),
                    LEASE_DURATION,
                    clock.instant()
            ).isEmpty()) {
                cancel(
                        session,
                        "DURABLE_LEASE_LOST",
                        "Confiscation selection lost its durable inventory lease",
                        true
                );
                return;
            }
            onEntity(
                    session.target(),
                    () -> {
                        if (!currency.renewMovementLock(
                                session.targetId(),
                                session.operationId(),
                                LEASE_DURATION
                        )) {
                            cancel(
                                    session,
                                    "CURRENCY_LEASE_LOST",
                                    "Confiscation selection lost its Currency movement lock",
                                    true
                            );
                        }
                    },
                    () -> cancel(
                            session,
                            "TARGET_LEFT_DURING_RENEWAL",
                            "Target left during confiscation lease renewal",
                            true
                    )
            );
        })) {
            cancel(
                    session,
                    "RENEWAL_QUEUE_FULL",
                    "Confiscation lease renewal queue is full",
                    true
            );
        }
    }

    private void openView(
            SelectionSession session,
            Optional<ItemPath> containerPath,
            int requestedPage
    ) {
        onEntity(session.viewer(), () -> {
            if (!session.selecting() || !session.viewer().isOnline()) {
                return;
            }
            List<ViewEntry> entries;
            try {
                entries = viewEntries(session, containerPath);
            } catch (RuntimeException exception) {
                plugin.getLogger().log(Level.WARNING, "Confiscation container view failed", exception);
                session.viewer().sendMessage(Component.text("That nested container is no longer readable."));
                return;
            }
            int maximumPage = entries.isEmpty() ? 0 : (entries.size() - 1) / CONTENT_SLOTS;
            int page = Math.max(0, Math.min(requestedPage, maximumPage));
            ConfiscationInventoryHolder holder = new ConfiscationInventoryHolder(
                    session.operationId(),
                    session.viewerId(),
                    containerPath,
                    page
            );
            Inventory inventory = Bukkit.createInventory(
                    holder,
                    54,
                    Component.text("Confiscate: " + session.target().getName()
                            + " [" + (page + 1) + '/' + (maximumPage + 1) + ']')
            );
            holder.attach(inventory);
            render(session, holder, entries);
            if (session.viewer().getOpenInventory().getTopInventory().getHolder(false)
                    instanceof ConfiscationInventoryHolder current
                    && current.operationId().equals(session.operationId())) {
                session.suppressNextClose();
            }
            session.viewer().openInventory(inventory);
        }, () -> cancel(
                session,
                "VIEWER_LEFT",
                "Staff left during confiscation selection",
                false
        ));
    }

    private List<ViewEntry> viewEntries(
            SelectionSession session,
            Optional<ItemPath> containerPath
    ) {
        List<ViewEntry> entries = new ArrayList<>();
        if (containerPath.isEmpty()) {
            for (int slot = 0; slot < InventoryImage.TOTAL_SLOTS; slot++) {
                ItemPath path = ItemPath.root(slot);
                entries.add(new ViewEntry(path, session.before().item(slot)));
            }
            return List.copyOf(entries);
        }
        ItemPath parent = containerPath.orElseThrow();
        ItemStack container = NestedInventorySelection.item(session.before(), parent);
        List<ItemStack> children = NestedInventorySelection.children(container);
        for (int slot = 0; slot < children.size(); slot++) {
            entries.add(new ViewEntry(parent.child(slot), children.get(slot)));
        }
        return List.copyOf(entries);
    }

    private void render(
            SelectionSession session,
            ConfiscationInventoryHolder holder,
            List<ViewEntry> entries
    ) {
        Inventory inventory = holder.getInventory();
        inventory.clear();
        int offset = holder.page() * CONTENT_SLOTS;
        for (int guiSlot = 0; guiSlot < CONTENT_SLOTS; guiSlot++) {
            int entryIndex = offset + guiSlot;
            if (entryIndex >= entries.size()) {
                break;
            }
            ViewEntry entry = entries.get(entryIndex);
            if (entry.item() == null || entry.item().isEmpty()) {
                continue;
            }
            inventory.setItem(
                    guiSlot,
                    session.covered(entry.path())
                            ? selectedRepresentation(entry.item())
                            : entry.item().clone()
            );
        }
        ItemStack filler = named(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int slot = CONTENT_SLOTS; slot < inventory.getSize(); slot++) {
            inventory.setItem(slot, filler);
        }
        if (holder.containerPath().isPresent()) {
            inventory.setItem(BACK_SLOT, named(Material.ARROW, "Back"));
        }
        if (holder.page() > 0) {
            inventory.setItem(PREVIOUS_SLOT, named(Material.ARROW, "Previous page"));
        }
        if ((holder.page() + 1) * CONTENT_SLOTS < entries.size()) {
            inventory.setItem(NEXT_SLOT, named(Material.ARROW, "Next page"));
        }
        inventory.setItem(
                CONFIRM_SLOT,
                named(
                        Material.LIME_CONCRETE,
                        "Confirm " + session.selectionCount() + " selected path(s)"
                )
        );
        inventory.setItem(CANCEL_SLOT, named(Material.BARRIER, "Cancel without changing assets"));
    }

    private void confirm(SelectionSession session) {
        if (!authorize(session.viewer(), ModerationAction.APPLY_CASE_CONFISCATION)) {
            cancel(
                    session,
                    "ACTOR_AUTHORITY_REVOKED",
                    "Staff punishment authority changed before confirmation",
                    true
            );
            return;
        }
        if (session.selectionCount() == 0) {
            session.viewer().sendMessage(Component.text("Select at least one item or container."));
            return;
        }
        if (!session.beginPreparing()) {
            return;
        }
        session.stopTasks();
        session.suppressNextClose();
        session.viewer().closeInventory();
        onEntity(
                session.target(),
                () -> buildCommit(session),
                () -> cancelPreparedTargetDeparture(session)
        );
    }

    private void buildCommit(SelectionSession session) {
        try {
            InventoryImage current = imageCodec.capture(session.target());
            InventoryImageCodec.EncodedImage currentBytes = imageCodec.encodeWithChecksum(current);
            if (!currentBytes.checksum().equals(session.beforeChecksum())) {
                cancelBeforePatch(
                        session,
                        "SELECTION_STALE",
                        "Target inventory changed during confiscation selection"
                );
                return;
            }
            for (Map.Entry<ItemPath, String> selection : session.selections().entrySet()) {
                ItemStack item = NestedInventorySelection.item(current, selection.getKey());
                if (item == null || item.isEmpty()
                        || !NestedInventorySelection.fingerprint(item).equals(selection.getValue())) {
                    cancelBeforePatch(
                            session,
                            "SELECTION_FINGERPRINT_STALE",
                            "A selected nested item changed and requires reselection"
                    );
                    return;
                }
            }
            NestedInventorySelection.SelectionResult removal =
                    NestedInventorySelection.remove(current, session.selections().keySet());
            InventoryImageCodec.EncodedImage replacement =
                    imageCodec.encodeWithChecksum(removal.replacement());
            ConfiscatedAssetsCodec.EncodedAssets assets = assetsCodec.encode(removal.entries());
            InventoryConfiscationCommitRequest request = new InventoryConfiscationCommitRequest(
                    session.operationId(),
                    session.fencingToken(),
                    session.durable().expectedRevision(),
                    currentBytes.checksum(),
                    replacement.checksum(),
                    replacement.bytes(),
                    removal.changedRootSlots(),
                    assets.checksum(),
                    assets.bytes(),
                    removal.entries().stream()
                            .map(ConfiscatedAssetEntry::path)
                            .map(ItemPath::encoded)
                            .toList()
            );
            if (!submit(() -> preparePatch(session, request, removal.replacement()))) {
                cancelBeforePatch(
                        session,
                        "COMMIT_QUEUE_FULL",
                        "Confiscation commit queue is full"
                );
            }
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.SEVERE, "Confiscation selection validation failed", exception);
            cancelBeforePatch(
                    session,
                    "SELECTION_VALIDATION_FAILED",
                    "Confiscation selection could not be validated"
            );
        }
    }

    private void preparePatch(
            SelectionSession session,
            InventoryConfiscationCommitRequest request,
            InventoryImage replacement
    ) {
        InventoryJournalStore loaded = store.get();
        if (loaded == null) {
            cancelBeforePatch(session, "STORAGE_UNAVAILABLE", "Inventory storage became unavailable");
            return;
        }
        InventoryPatch preparedPatch = null;
        try {
            InventoryPreparation prepared = loaded.prepareConfiscation(request, clock.instant());
            InventoryPatch patch = prepared.patch().orElse(null);
            if (patch == null) {
                cancelBeforePatch(session, "PATCH_REJECTED", prepared.detail());
                return;
            }
            preparedPatch = patch;
            session.applying();
            InventoryPatch claimed = loaded.claimForApply(
                    patch.patchId(),
                    patch.operationId(),
                    LEASE_DURATION,
                    clock.instant()
            ).orElse(null);
            if (claimed == null) {
                quarantine(session, patch, "PATCH_CLAIM_FAILED", "Prepared confiscation patch lost its lease");
                return;
            }
            onEntity(
                    session.target(),
                    () -> applyPatch(session, claimed, replacement),
                    () -> pendingAfterTargetDeparture(session)
            );
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.SEVERE, "Confiscation patch preparation failed", exception);
            if (preparedPatch == null) {
                cancelBeforePatch(
                        session,
                        "PATCH_PREPARATION_FAILED",
                        "Confiscation patch could not be prepared"
                );
            } else {
                quarantine(
                        session,
                        preparedPatch,
                        "PATCH_CLAIM_EXCEPTION",
                        "Prepared confiscation patch could not be claimed safely"
                );
            }
        }
    }

    private void applyPatch(
            SelectionSession session,
            InventoryPatch patch,
            InventoryImage replacement
    ) {
        try {
            InventoryImage current = imageCodec.capture(session.target());
            InventoryImageCodec.EncodedImage currentBytes = imageCodec.encodeWithChecksum(current);
            if (!currentBytes.checksum().equals(patch.expectedChecksum())) {
                quarantine(
                        session,
                        patch,
                        "LIVE_STATE_CHANGED",
                        "Target inventory changed after confiscation was prepared"
                );
                return;
            }
            imageCodec.apply(session.target(), replacement);
            InventoryImage applied = imageCodec.capture(session.target());
            InventoryImageCodec.EncodedImage appliedBytes = imageCodec.encodeWithChecksum(applied);
            if (!submit(() -> finalizePatch(session, patch, appliedBytes))) {
                alertStaff(
                        "Confiscation reached the target for " + patch.operationId()
                                + " but finalization queue is full."
                );
            }
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.SEVERE, "Confiscation inventory application failed", exception);
            try {
                imageCodec.apply(session.target(), session.before());
            } catch (RuntimeException rollback) {
                exception.addSuppressed(rollback);
            }
            quarantine(
                    session,
                    patch,
                    "BUKKIT_APPLY_FAILED",
                    "Inventory application failed; exact rollback could not be durably proven"
            );
        }
    }

    private void finalizePatch(
            SelectionSession session,
            InventoryPatch patch,
            InventoryImageCodec.EncodedImage applied
    ) {
        try {
            InventoryFinalizeResult result = store.get().finalizeApplied(
                    patch.patchId(),
                    patch.operationId(),
                    patch.fencingToken(),
                    applied.checksum(),
                    applied.bytes(),
                    clock.instant()
            );
            if (result.status() == InventoryFinalizeResult.Status.COMMITTED
                    || result.status() == InventoryFinalizeResult.Status.REPLAYED) {
                releaseAfterCompletion(
                        session,
                        "Confiscation committed to case " + session.durable().caseId() + '.'
                );
                return;
            }
            message(
                    session.viewer(),
                    "Confiscation reached an ambiguous durable state; do not repeat it."
            );
            alertStaff(
                    "Confiscation operation " + session.operationId()
                            + " requires recovery: " + result.detail()
            );
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.SEVERE, "Confiscation finalization failed", exception);
            message(
                    session.viewer(),
                    "Confiscation reached the target but final verification is pending; do not repeat it."
            );
        }
    }

    private void quarantine(
            SelectionSession session,
            InventoryPatch patch,
            String reasonCode,
            String detail
    ) {
        Runnable work = () -> {
            try {
                InventoryJournalStore loaded = store.get();
                if (loaded != null) {
                    loaded.quarantine(
                            patch.patchId(),
                            patch.operationId(),
                            patch.fencingToken(),
                            reasonCode,
                            detail,
                            clock.instant()
                    );
                }
            } catch (RuntimeException exception) {
                plugin.getLogger().log(Level.SEVERE, "Confiscation quarantine failed", exception);
            }
            message(
                    session.viewer(),
                    "Confiscation was quarantined for staff recovery; do not repeat it."
            );
            alertStaff(
                    "Confiscation operation " + session.operationId()
                            + " was quarantined: " + detail
            );
        };
        if (!submit(work)) {
            alertStaff("Confiscation quarantine queue is full for " + session.operationId() + '.');
        }
    }

    private void cancel(
            SelectionSession session,
            String reasonCode,
            String detail,
            boolean closeViewer
    ) {
        if (!session.beginCancelling()) {
            return;
        }
        session.stopTasks();
        if (closeViewer) {
            session.suppressNextClose();
            onEntity(session.viewer(), session.viewer()::closeInventory, () -> {
            });
        }
        if (!submit(() -> cancelDurable(session, reasonCode, detail))) {
            message(
                    session.viewer(),
                    "No assets changed, but the abandoned confiscation fence requires login recovery."
            );
            removeSession(session);
            releaseLocalLocks(session.targetId(), session.operationId());
        }
    }

    private void cancelBeforePatch(
            SelectionSession session,
            String reasonCode,
            String detail
    ) {
        if (session.beginCancellingFromPreparing()) {
            if (!submit(() -> cancelDurable(session, reasonCode, detail))) {
                message(
                        session.viewer(),
                        "No assets changed, but the confiscation fence requires login recovery."
                );
                removeSession(session);
                releaseLocalLocks(session.targetId(), session.operationId());
            }
        }
    }

    private void cancelDurable(
            SelectionSession session,
            String reasonCode,
            String detail
    ) {
        try {
            InventoryJournalStore loaded = store.get();
            boolean cancelled = loaded != null && loaded.cancelConfiscation(
                    session.operationId(),
                    session.fencingToken(),
                    reasonCode,
                    detail,
                    clock.instant()
            );
            if (cancelled) {
                message(session.viewer(), detail + "; no assets changed.");
            } else {
                message(
                        session.viewer(),
                        "No assets changed, but the confiscation fence requires recovery."
                );
            }
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.SEVERE, "Confiscation cancellation failed", exception);
            message(
                    session.viewer(),
                    "No assets changed, but the confiscation fence requires recovery."
            );
        } finally {
            releaseLocalLocks(session.targetId(), session.operationId());
            removeSession(session);
        }
    }

    private void cancelPreparedTargetDeparture(SelectionSession session) {
        cancelBeforePatch(
                session,
                "TARGET_LEFT_BEFORE_PATCH",
                "Target left before confiscation patch preparation"
        );
    }

    private void pendingAfterTargetDeparture(SelectionSession session) {
        session.stopTasks();
        releaseLocalLocks(session.targetId(), session.operationId());
        removeSession(session);
        message(
                session.viewer(),
                "Target left; the durable confiscation patch will apply before their next interaction."
        );
    }

    private void releaseAfterCompletion(SelectionSession session, String detail) {
        session.stopTasks();
        releaseLocalLocks(session.targetId(), session.operationId());
        removeSession(session);
        message(session.viewer(), detail);
    }

    private void releaseLocalLocks(UUID targetId, UUID operationId) {
        inventories.releaseExternalAssetLock(targetId);
        Player target = plugin.getServer().getPlayer(targetId);
        Runnable release = () -> currency.releaseMovementLock(targetId, operationId);
        if (target != null && target.isOnline()) {
            onEntity(
                    target,
                    release,
                    () -> plugin.getServer().getGlobalRegionScheduler().execute(plugin, release)
            );
        } else {
            plugin.getServer().getGlobalRegionScheduler().execute(plugin, release);
        }
    }

    private void removeSession(SelectionSession session) {
        sessions.remove(session.operationId(), session);
        clearReservation(session.viewerId(), session.targetId(), session.operationId());
    }

    private void clearReservation(UUID viewerId, UUID targetId, UUID operationId) {
        viewerOperations.remove(viewerId, operationId);
        targetOperations.remove(targetId, operationId);
    }

    private static ItemStack selectedRepresentation(ItemStack original) {
        ItemStack selected = ItemStack.of(Material.RED_STAINED_GLASS_PANE);
        selected.editMeta(meta -> {
            meta.displayName(Component.text("Selected: ").append(original.effectiveName()));
            meta.lore(List.of(
                    Component.text("Click to deselect."),
                    Component.text("The exact original item is retained in the recovery snapshot.")
            ));
        });
        return selected;
    }

    private static ItemStack named(Material material, String name) {
        ItemStack item = ItemStack.of(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(name));
        item.setItemMeta(meta);
        return item;
    }

    private boolean submit(Runnable operation) {
        if (closed.get()) {
            return false;
        }
        try {
            workers.execute(operation);
            return true;
        } catch (RejectedExecutionException exception) {
            plugin.getLogger().warning(
                    "Confiscation operation skipped because the bounded worker queue is full"
            );
            return false;
        }
    }

    private void onEntity(Player player, Runnable operation, Runnable retired) {
        player.getScheduler().execute(plugin, operation, retired, 1L);
    }

    private void message(Player player, String body) {
        if (player != null) {
            onEntity(player, () -> player.sendMessage(Component.text(body)), () -> {
            });
        }
    }

    private void alertStaff(String body) {
        plugin.getServer().getGlobalRegionScheduler().execute(plugin, () ->
                plugin.getServer().getOnlinePlayers().stream()
                        .filter(player -> player.hasPermission("enthusiastaff.alerts"))
                        .forEach(player -> player.sendMessage(Component.text(body))));
    }

    private static String requireIdentifier(String value, String field) {
        if (value == null || value.isBlank() || value.length() > 64) {
            throw new IllegalArgumentException(field + " must contain 1-64 characters");
        }
        return value;
    }

    private boolean authorize(Player player, ModerationAction action) {
        Actor actor = PaperActorResolver.resolve(player).orElse(null);
        return actor != null && authorization.permits(actor, action);
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        sessions.values().forEach(session -> {
            session.stopTasks();
            releaseLocalLocks(session.targetId(), session.operationId());
        });
        restorations.values().forEach(context -> {
            if (context.localLocksWereAcquired()) {
                releaseLocalLocks(context.targetId(), context.operationId());
            }
        });
        sessions.clear();
        restorations.clear();
        viewerOperations.clear();
        targetOperations.clear();
    }

    private record ViewEntry(ItemPath path, ItemStack item) {
        private ViewEntry {
            java.util.Objects.requireNonNull(path, "path");
            item = item == null || item.isEmpty() ? null : item.clone();
        }

        @Override
        public ItemStack item() {
            return item == null ? null : item.clone();
        }
    }

    private static final class RestorationContext {
        private final Player viewer;
        private final Player target;
        private final CaseId caseId;
        private final UUID operationId;
        private final AtomicReference<Phase> phase = new AtomicReference<>(Phase.STARTING);
        private final AtomicBoolean reservation = new AtomicBoolean();
        private final AtomicBoolean localLocks = new AtomicBoolean();
        private final AtomicBoolean targetDeparted = new AtomicBoolean();

        private RestorationContext(
                Player viewer,
                Player target,
                CaseId caseId,
                UUID operationId
        ) {
            this.viewer = java.util.Objects.requireNonNull(viewer, "viewer");
            this.target = java.util.Objects.requireNonNull(target, "target");
            this.caseId = java.util.Objects.requireNonNull(caseId, "caseId");
            this.operationId = java.util.Objects.requireNonNull(operationId, "operationId");
        }

        Player viewer() {
            return viewer;
        }

        Player target() {
            return target;
        }

        UUID viewerId() {
            return viewer.getUniqueId();
        }

        UUID targetId() {
            return target.getUniqueId();
        }

        CaseId caseId() {
            return caseId;
        }

        UUID operationId() {
            return operationId;
        }

        void localLocksAcquired() {
            localLocks.set(true);
        }

        boolean localLocksWereAcquired() {
            return localLocks.get();
        }

        boolean reserved() {
            reservation.set(true);
            return phase.compareAndSet(Phase.STARTING, Phase.RESERVED);
        }

        boolean reservationMade() {
            return reservation.get();
        }

        void patchPrepared() {
            if (!phase.compareAndSet(Phase.PATCH_PREPARING, Phase.PATCH_PREPARED)) {
                throw new IllegalStateException("restoration left its reservable phase");
            }
        }

        void preparingPatch() {
            if (!phase.compareAndSet(Phase.RESERVED, Phase.PATCH_PREPARING)) {
                throw new IllegalStateException("restoration left its planning phase");
            }
        }

        void applying() {
            if (!phase.compareAndSet(Phase.PATCH_PREPARED, Phase.APPLYING)) {
                throw new IllegalStateException("restoration patch was not prepared");
            }
        }

        boolean beforePatch() {
            Phase current = phase.get();
            return current == Phase.STARTING || current == Phase.RESERVED;
        }

        boolean patchPreparationInFlight() {
            return phase.get() == Phase.PATCH_PREPARING;
        }

        void targetDeparted() {
            targetDeparted.set(true);
        }

        boolean targetHasDeparted() {
            return targetDeparted.get();
        }

        boolean failBeforePatch() {
            while (true) {
                Phase current = phase.get();
                if (current != Phase.STARTING && current != Phase.RESERVED
                        && current != Phase.PATCH_PREPARING) {
                    return false;
                }
                if (phase.compareAndSet(current, Phase.FAILED)) {
                    return true;
                }
            }
        }

        boolean finish() {
            while (true) {
                Phase current = phase.get();
                if (current == Phase.FINISHED) {
                    return false;
                }
                if (phase.compareAndSet(current, Phase.FINISHED)) {
                    return true;
                }
            }
        }

        private enum Phase {
            STARTING,
            RESERVED,
            PATCH_PREPARING,
            PATCH_PREPARED,
            APPLYING,
            FAILED,
            FINISHED
        }
    }

    private static final class SelectionSession {
        private final Player viewer;
        private final Player target;
        private final InventoryConfiscationSession durable;
        private final InventoryImage before;
        private final Map<ItemPath, String> selections = new LinkedHashMap<>();
        private final AtomicReference<State> state = new AtomicReference<>(State.SELECTING);
        private final AtomicBoolean suppressClose = new AtomicBoolean();
        private volatile ScheduledTask renewalTask;
        private volatile ScheduledTask timeoutTask;

        private SelectionSession(
                Player viewer,
                Player target,
                InventoryConfiscationSession durable,
                InventoryImage before
        ) {
            this.viewer = viewer;
            this.target = target;
            this.durable = durable;
            this.before = before;
        }

        Player viewer() {
            return viewer;
        }

        Player target() {
            return target;
        }

        UUID viewerId() {
            return viewer.getUniqueId();
        }

        UUID targetId() {
            return target.getUniqueId();
        }

        UUID operationId() {
            return durable.operationId();
        }

        long fencingToken() {
            return durable.fencingToken();
        }

        InventoryConfiscationSession durable() {
            return durable;
        }

        InventoryImage before() {
            return before;
        }

        String beforeChecksum() {
            return durable.beforeChecksum();
        }

        synchronized Map<ItemPath, String> selections() {
            return Map.copyOf(selections);
        }

        synchronized int selectionCount() {
            return selections.size();
        }

        synchronized boolean selectedExact(ItemPath path) {
            return selections.containsKey(path);
        }

        synchronized boolean coveredByAncestor(ItemPath path) {
            return selections.keySet().stream()
                    .anyMatch(selected -> !selected.equals(path) && selected.ancestorOf(path));
        }

        synchronized boolean covered(ItemPath path) {
            return selections.keySet().stream().anyMatch(selected -> selected.ancestorOf(path));
        }

        synchronized void select(ItemPath path, ItemStack item) {
            selections.keySet().removeIf(path::ancestorOf);
            selections.put(path, NestedInventorySelection.fingerprint(item));
        }

        synchronized void deselect(ItemPath path) {
            selections.remove(path);
        }

        boolean selecting() {
            return state.get() == State.SELECTING;
        }

        boolean beginPreparing() {
            return state.compareAndSet(State.SELECTING, State.PREPARING);
        }

        void applying() {
            state.compareAndSet(State.PREPARING, State.APPLYING);
        }

        boolean beginCancelling() {
            return state.compareAndSet(State.SELECTING, State.CANCELLING);
        }

        boolean beginCancellingFromPreparing() {
            return state.compareAndSet(State.PREPARING, State.CANCELLING);
        }

        void suppressNextClose() {
            suppressClose.set(true);
        }

        boolean consumeSuppressedClose() {
            return suppressClose.compareAndSet(true, false);
        }

        void renewalTask(ScheduledTask task) {
            renewalTask = task;
        }

        void timeoutTask(ScheduledTask task) {
            timeoutTask = task;
        }

        void stopTasks() {
            ScheduledTask renewal = renewalTask;
            if (renewal != null) {
                renewal.cancel();
            }
            ScheduledTask timeout = timeoutTask;
            if (timeout != null) {
                timeout.cancel();
            }
        }

        private enum State {
            SELECTING,
            PREPARING,
            APPLYING,
            CANCELLING
        }
    }
}
