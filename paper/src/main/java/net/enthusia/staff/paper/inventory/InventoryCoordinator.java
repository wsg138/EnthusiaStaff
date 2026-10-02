package net.enthusia.staff.paper.inventory;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import java.time.Clock;
import java.time.Duration;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import java.util.logging.Level;
import net.enthusia.staff.common.IdempotencyKey;
import net.enthusia.staff.domain.OperationalMode;
import net.enthusia.staff.domain.inventory.InventoryCursorJournal;
import net.enthusia.staff.domain.inventory.InventoryCursorPhase;
import net.enthusia.staff.domain.inventory.InventoryCursorTransfer;
import net.enthusia.staff.domain.inventory.InventoryFinalizeResult;
import net.enthusia.staff.domain.inventory.InventoryObservation;
import net.enthusia.staff.domain.inventory.InventoryOperationState;
import net.enthusia.staff.domain.inventory.InventoryPatch;
import net.enthusia.staff.domain.inventory.InventoryPatchDecision;
import net.enthusia.staff.domain.inventory.InventoryPreparation;
import net.enthusia.staff.domain.inventory.InventoryPrepareRequest;
import net.enthusia.staff.domain.player.PlayerIdentity;
import net.enthusia.staff.domain.player.PlayerPresence;
import net.enthusia.staff.domain.ports.InventoryJournalStore;
import net.enthusia.staff.domain.ports.PlayerDirectory;
import net.enthusia.staff.paper.api.InventoryLockService;
import net.enthusia.staff.paper.presentation.StaffMessageStyle;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerAttemptPickupItemEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

public final class InventoryCoordinator implements Listener, InventoryLockService, AutoCloseable {
    private static final Duration APPLY_LEASE = Duration.ofSeconds(30);
    private static final int MAX_LOGIN_APPLY_ATTEMPTS = 5;
    private static final int MAX_PENDING_PATCHES_PER_PLAYER = 1;
    private static final int PENDING_PATCH_LOOKAHEAD = 2;
    private static final long RECONCILIATION_INITIAL_DELAY_TICKS = 5L;
    private static final long RECONCILIATION_PERIOD_TICKS = 40L;
    private static final String LIVE_CURSOR_PREFIX = "ONLINE_CURSOR_";
    private static final String RECOVERY_STORAGE_UNAVAILABLE = "Inventory recovery storage is unavailable.";

    private final JavaPlugin plugin;
    private final Clock clock;
    private final String scopeId;
    private final String serverId;
    private final Supplier<OperationalMode> mode;
    private final Supplier<InventoryJournalStore> store;
    private final Supplier<PlayerDirectory> directory;
    private final ExecutorService workers;
    private final InventoryEditAuthorityGate editAuthority;
    private final InventoryImageCodec codec = new InventoryImageCodec();
    private final LiveCursorEscrow cursorEscrow;
    private final Map<UUID, LiveSession> liveSessions = new ConcurrentHashMap<>();
    private final Map<UUID, LiveInventoryTransferExecution> viewerTransfers = new ConcurrentHashMap<>();
    private final Map<UUID, InventoryPatch> preloadedPatches = new ConcurrentHashMap<>();
    private final Map<UUID, InventoryCursorJournal> cursorRecoveries = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> recoveryAttempts = new ConcurrentHashMap<>();
    private final Set<UUID> recoveryInFlight = ConcurrentHashMap.newKeySet();
    private final Set<UUID> assetLocks = ConcurrentHashMap.newKeySet();
    private final Set<UUID> loginBlocks = ConcurrentHashMap.newKeySet();
    private final ScheduledTask reconciliationTask;

    public InventoryCoordinator(
            JavaPlugin plugin,
            InventoryOperationContext context,
            Supplier<OperationalMode> mode,
            Supplier<InventoryJournalStore> store,
            Supplier<PlayerDirectory> directory,
            ExecutorService workers
    ) {
        this.plugin = java.util.Objects.requireNonNull(plugin, "plugin");
        InventoryOperationContext operationContext = java.util.Objects.requireNonNull(context, "context");
        this.clock = operationContext.clock();
        this.scopeId = operationContext.scopeId();
        this.serverId = operationContext.serverId();
        this.mode = java.util.Objects.requireNonNull(mode, "mode");
        this.store = java.util.Objects.requireNonNull(store, "store");
        this.directory = java.util.Objects.requireNonNull(directory, "directory");
        this.workers = java.util.Objects.requireNonNull(workers, "workers");
        this.editAuthority = new InventoryEditAuthorityGate(plugin);
        this.cursorEscrow = new LiveCursorEscrow(plugin);
        this.reconciliationTask = plugin.getServer().getGlobalRegionScheduler().runAtFixedRate(
                plugin,
                ignored -> reconcileViewedTargets(),
                RECONCILIATION_INITIAL_DELAY_TICKS,
                RECONCILIATION_PERIOD_TICKS
        );
    }

    public void open(Player viewer, PlayerIdentity target, boolean enderChest) {
        if (viewer == null || target == null) {
            throw new IllegalArgumentException("viewer and target must be present");
        }
        if (mode.get() != OperationalMode.ACTIVE) {
            viewer.sendMessage(StaffMessageStyle.style(Component.text(
                    "Inventory editing is available only while moderation is ACTIVE."
            )));
            return;
        }
        Player online = plugin.getServer().getPlayer(target.playerId());
        ModerationInventoryHolder.Kind kind = enderChest
                ? ModerationInventoryHolder.Kind.ENDER_CHEST
                : ModerationInventoryHolder.Kind.PLAYER;
        if (online != null) {
            openLive(viewer, target, online, kind);
        } else {
            openOffline(viewer, target, kind);
        }
    }

    @Override
    public boolean isLocked(UUID playerId) {
        return playerId != null && (assetLocks.contains(playerId) || loginBlocks.contains(playerId));
    }

    public boolean acquireExternalAssetLock(UUID playerId) {
        if (playerId == null) {
            throw new IllegalArgumentException("playerId must be present");
        }
        return assetLocks.add(playerId);
    }

    public void releaseExternalAssetLock(UUID playerId) {
        if (playerId != null) {
            assetLocks.remove(playerId);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        InventoryJournalStore loaded = store.get();
        if (loaded == null) {
            denyIfActive(event, "Inventory safety verification is temporarily unavailable. Please retry.");
            return;
        }
        try {
            loaded.cancelAbandonedConfiscations(event.getUniqueId(), scopeId, serverId, clock.instant());
            if (!preloadTargetPatch(event, loaded)) {
                return;
            }
            preloadActorCursorRecovery(event, loaded);
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.SEVERE, "Inventory pre-login recovery lookup failed", exception);
            denyIfActive(event, "Inventory safety verification failed. Please retry.");
        }
    }

    private boolean preloadTargetPatch(AsyncPlayerPreLoginEvent event, InventoryJournalStore loaded) {
        List<InventoryPatch> patches = loaded.pending(
                event.getUniqueId(), scopeId, serverId, PENDING_PATCH_LOOKAHEAD
        );
        if (patches.size() > MAX_PENDING_PATCHES_PER_PLAYER) {
            loginBlocks.add(event.getUniqueId());
            event.disallow(
                    AsyncPlayerPreLoginEvent.Result.KICK_OTHER,
                    Component.text("Multiple inventory recovery operations require staff review.")
            );
            return false;
        }
        if (patches.isEmpty()) {
            return true;
        }
        InventoryPatch patch = patches.getFirst();
        preloadedPatches.put(event.getUniqueId(), patch);
        loginBlocks.add(event.getUniqueId());
        if (isLiveCursorPatch(patch)) {
            loaded.cursorTransfer(patch.operationId()).ifPresent(this::rememberCursorRecovery);
        }
        return true;
    }

    private void preloadActorCursorRecovery(
            AsyncPlayerPreLoginEvent event,
            InventoryJournalStore loaded
    ) {
        List<InventoryCursorJournal> recoveries = loaded.pendingCursorTransfersByActor(
                event.getUniqueId(), serverId, PENDING_PATCH_LOOKAHEAD
        );
        if (recoveries.size() > MAX_PENDING_PATCHES_PER_PLAYER) {
            loginBlocks.add(event.getUniqueId());
            event.disallow(
                    AsyncPlayerPreLoginEvent.Result.KICK_OTHER,
                    Component.text("Multiple cursor transfer recoveries require staff review.")
            );
            return;
        }
        if (recoveries.isEmpty()) {
            return;
        }
        InventoryCursorJournal recovery = recoveries.getFirst();
        if (!serverId.equals(recovery.patch().owningServerId())) {
            event.disallow(
                    AsyncPlayerPreLoginEvent.Result.KICK_OTHER,
                    Component.text("An inventory transfer must recover on its owning backend. Please retry shortly.")
            );
            return;
        }
        rememberCursorRecovery(recovery);
        loginBlocks.add(event.getUniqueId());
        loginBlocks.add(recovery.patch().playerId());
    }

    private void denyIfActive(AsyncPlayerPreLoginEvent event, String detail) {
        if (mode.get() == OperationalMode.ACTIVE) {
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, Component.text(detail));
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        InventoryPatch patch = preloadedPatches.remove(player.getUniqueId());
        if (patch != null && !isLiveCursorPatch(patch)) {
            applyPendingOnLogin(player, patch, 1);
            return;
        }
        if (patch != null) {
            loadOrResolveLiveRecovery(player, patch);
        }
        attemptMatchingRecoveries(player.getUniqueId());
        if (patch == null && !hasCursorRecoveryFor(player.getUniqueId())) {
            loginBlocks.remove(player.getUniqueId());
            observe(player);
        }
    }

    private void loadOrResolveLiveRecovery(Player target, InventoryPatch patch) {
        if (cursorRecoveries.containsKey(patch.operationId())) {
            attemptCursorRecovery(cursorRecoveries.get(patch.operationId()));
            return;
        }
        if (!submit(() -> loadLiveRecoveryMetadata(target, patch))) {
            retryLivePatchLookup(target, patch, "Inventory recovery worker queue is busy.");
        }
    }

    private void loadLiveRecoveryMetadata(Player target, InventoryPatch patch) {
        InventoryJournalStore loaded = store.get();
        if (loaded == null) {
            retryLivePatchLookup(target, patch, RECOVERY_STORAGE_UNAVAILABLE);
            return;
        }
        try {
            Optional<InventoryCursorJournal> recovery = loaded.cursorTransfer(patch.operationId());
            if (recovery.isPresent()) {
                recoveryAttempts.remove(patch.operationId());
                rememberCursorRecovery(recovery.orElseThrow());
                attemptCursorRecovery(recovery.orElseThrow());
            } else {
                resolveMissingCursorMetadata(target, patch);
            }
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.SEVERE, "Unable to load cursor recovery metadata", exception);
            retryLivePatchLookup(target, patch, "Cursor recovery metadata lookup failed.");
        }
    }

    private void retryLivePatchLookup(Player target, InventoryPatch patch, String detail) {
        target.getScheduler().runDelayed(
                plugin,
                ignored -> retryLivePatchLookupOnTarget(target, patch, detail),
                () -> recoveryAttempts.remove(patch.operationId()),
                20L
        );
    }

    private void retryLivePatchLookupOnTarget(Player target, InventoryPatch patch, String detail) {
        int attempt = recoveryAttempts.merge(patch.operationId(), 1, Integer::sum);
        LiveInventoryRecoveryPolicy.RetryDecision decision = LiveInventoryRecoveryPolicy.metadataRetry(
                target.isOnline(), attempt, MAX_LOGIN_APPLY_ATTEMPTS
        );
        if (decision == LiveInventoryRecoveryPolicy.RetryDecision.STOP_OFFLINE) {
            recoveryAttempts.remove(patch.operationId());
            return;
        }
        if (decision == LiveInventoryRecoveryPolicy.RetryDecision.EXHAUSTED) {
            keepRecoveryBlocked(
                    patch.playerId(), patch.actorId(), patch.operationId(),
                    detail + " Automatic metadata recovery attempts are exhausted."
            );
            return;
        }
        loginBlocks.add(patch.playerId());
        message(target, detail);
        loadOrResolveLiveRecovery(target, patch);
    }

    private void resolveMissingCursorMetadata(Player target, InventoryPatch original) {
        InventoryJournalStore loaded = store.get();
        if (loaded == null) {
            retryLivePatchLookup(target, original, RECOVERY_STORAGE_UNAVAILABLE);
            return;
        }
        InventoryPatch claimed = loaded.claimForApply(
                original.patchId(), original.operationId(), APPLY_LEASE, clock.instant()
        ).orElse(null);
        if (claimed == null) {
            retryLivePatchLookup(target, original, "Inventory recovery lease is busy.");
            return;
        }
        if (claimed.state() == InventoryOperationState.APPLIED) {
            recoveryAttempts.remove(original.operationId());
            loginBlocks.remove(target.getUniqueId());
            return;
        }
        onEntity(target, () -> verifyMissingMetadataBeforeState(target, claimed));
    }

    private void verifyMissingMetadataBeforeState(Player target, InventoryPatch patch) {
        InventoryImageCodec.EncodedImage current = codec.encodeWithChecksum(codec.capture(target));
        if (!current.checksum().equals(patch.expectedChecksum())) {
            keepRecoveryBlocked(
                    patch.playerId(),
                    patch.actorId(),
                    patch.operationId(),
                    "Live cursor metadata is missing and target state is not the prepared before-state."
            );
            return;
        }
        if (!submit(() -> resolveMetadataFreeRollback(target, patch, current))) {
            retryLivePatchLookup(target, patch, "Metadata-free rollback worker queue is busy.");
        }
    }

    private void resolveMetadataFreeRollback(
            Player target,
            InventoryPatch patch,
            InventoryImageCodec.EncodedImage current
    ) {
        InventoryJournalStore loaded = store.get();
        if (loaded != null && loaded.resolveCursorRollback(
                patch.patchId(), patch.operationId(), patch.fencingToken(), clock.instant()
        )) {
            recoveryAttempts.remove(patch.operationId());
            loginBlocks.remove(target.getUniqueId());
            observeEncoded(target.getUniqueId(), current);
        } else {
            keepRecoveryBlocked(
                    patch.playerId(), patch.actorId(), patch.operationId(),
                    "Unable to resolve a metadata-free live cursor preparation."
            );
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        handleTargetDeparture(player);
        handleViewerDeparture(player);
        closeTargetViews(player.getUniqueId(), "The target left this backend.");
        if (!isLocked(player.getUniqueId())) {
            observe(player);
        }
        if (!assetLocks.contains(player.getUniqueId()) && !hasCursorRecoveryFor(player.getUniqueId())) {
            loginBlocks.remove(player.getUniqueId());
        }
        preloadedPatches.remove(player.getUniqueId());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player viewer)) {
            return;
        }
        if (interactionRestricted(viewer.getUniqueId())) {
            event.setCancelled(true);
            return;
        }
        if (!(event.getView().getTopInventory().getHolder(false) instanceof ModerationInventoryHolder holder)) {
            return;
        }
        if (!holder.viewerId().equals(viewer.getUniqueId())) {
            event.setCancelled(true);
            return;
        }
        int topSize = event.getView().getTopInventory().getSize();
        if (event.getRawSlot() >= 0 && event.getRawSlot() < topSize) {
            event.setCancelled(true);
            editClickedSlot(event, viewer, holder);
            return;
        }
        if (event.getRawSlot() >= topSize && !safeLowerInventoryAction(event.getAction())) {
            event.setCancelled(true);
        }
    }

    private void editClickedSlot(
            InventoryClickEvent event,
            Player viewer,
            ModerationInventoryHolder holder
    ) {
        if (!viewer.hasPermission(InventoryEditAuthorityGate.EDIT_PERMISSION)) {
            viewer.sendMessage(StaffMessageStyle.style(Component.text(
                    "You may inspect this inventory but not edit it."
            )));
            return;
        }
        int logicalSlot = holder.logicalSlot(event.getRawSlot());
        if (logicalSlot < 0) {
            return;
        }
        if (holder.offline()) {
            applyOfflineClickedEdit(event, holder, logicalSlot);
            return;
        }
        applyLiveClickedEdit(event, viewer, holder, logicalSlot);
    }

    private void applyLiveClickedEdit(
            InventoryClickEvent event,
            Player viewer,
            ModerationInventoryHolder holder,
            int logicalSlot
    ) {
        Optional<LiveInventoryTransferDecision.Click> supported = supportedClick(event.getClick());
        if (supported.isEmpty()) {
            return;
        }
        LiveInventoryTransferDecision.Click click = supported.orElseThrow();
        LiveSession session = liveSessions.get(holder.targetId());
        if (session == null) {
            message(viewer, "That live inventory session ended; reopen the view.");
            return;
        }
        InventoryImage before = session.image();
        ItemStack authoritative = before.item(logicalSlot);
        if (!LiveInventoryTransferDecision.same(authoritative, event.getCurrentItem())) {
            renderHolderFromSession(holder, session);
            return;
        }
        LiveInventoryTransferDecision.Decision decision = LiveInventoryTransferDecision.decide(
                authoritative, event.getCursor(), click
        );
        if (!decision.changed()) {
            return;
        }
        InventoryImage replacement = before.withItem(logicalSlot, decision.targetAfter());
        startLiveTransfer(viewer, session, new LiveInventoryTransferExecution(
                new LiveInventoryTransferExecution.Identity(
                        UUID.randomUUID(), viewer.getUniqueId(), holder.targetId()
                ),
                new LiveInventoryTransferExecution.TargetMutation(
                        holder.kind(), logicalSlot, before, replacement
                ),
                new LiveInventoryTransferExecution.CursorMutation(
                        event.getCursor(), decision.cursorAfter(), decision.action()
                )
        ));
    }

    private void applyOfflineClickedEdit(
            InventoryClickEvent event,
            ModerationInventoryHolder holder,
            int logicalSlot
    ) {
        if (event.isShiftClick()) {
            return;
        }
        ItemStack replacement = offlineReplacement(
                holder.image().item(logicalSlot),
                event.getCursor(),
                event.isLeftClick(),
                event.isRightClick()
        );
        if (replacement == EditRejected.ITEM) {
            return;
        }
        holder.image(holder.image().withItem(logicalSlot, replacement), true);
        render(holder, holder.image());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player viewer)) {
            return;
        }
        if (interactionRestricted(viewer.getUniqueId())) {
            event.setCancelled(true);
            return;
        }
        if (!(event.getView().getTopInventory().getHolder(false) instanceof ModerationInventoryHolder)) {
            return;
        }
        int topSize = event.getView().getTopInventory().getSize();
        if (event.getRawSlots().stream().anyMatch(slot -> slot < topSize)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTargetInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        scheduleTargetRefresh(player, ModerationInventoryHolder.Kind.PLAYER);
        if (event.getView().getTopInventory().getType() == InventoryType.ENDER_CHEST) {
            scheduleTargetRefresh(player, ModerationInventoryHolder.Kind.ENDER_CHEST);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTargetInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        scheduleTargetRefresh(player, ModerationInventoryHolder.Kind.PLAYER);
        if (event.getView().getTopInventory().getType() == InventoryType.ENDER_CHEST) {
            scheduleTargetRefresh(player, ModerationInventoryHolder.Kind.ENDER_CHEST);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player viewer)) {
            return;
        }
        if (event.getInventory().getHolder(false) instanceof ModerationInventoryHolder holder
                && holder.closeOnce()) {
            closeModerationView(viewer, holder);
        }
        scheduleTargetRefresh(viewer, ModerationInventoryHolder.Kind.PLAYER);
        if (event.getInventory().getType() == InventoryType.ENDER_CHEST) {
            scheduleTargetRefresh(viewer, ModerationInventoryHolder.Kind.ENDER_CHEST);
        }
    }

    private void closeModerationView(Player viewer, ModerationInventoryHolder holder) {
        if (holder.offline()) {
            queueOfflineEdit(viewer, holder);
            return;
        }
        LiveSession session = liveSessions.get(holder.targetId());
        if (session == null) {
            return;
        }
        session.removeViewer(holder.viewerId());
        removeSessionIfRemovable(session);
    }

    private void finishSessionTransfer(
            LiveSession session,
            LiveInventoryTransferExecution transfer
    ) {
        session.finishTransfer(transfer);
        removeSessionIfRemovable(session);
    }

    private void finishSessionWork(LiveSession session) {
        session.finishWork();
        removeSessionIfRemovable(session);
    }

    private void removeSessionIfRemovable(LiveSession session) {
        if (session.removable()) {
            liveSessions.remove(session.targetId(), session);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onLockedOpen(InventoryOpenEvent event) {
        if (event.getPlayer() instanceof Player player && interactionRestricted(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onLockedDrop(PlayerDropItemEvent event) {
        if (interactionRestricted(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
            return;
        }
        scheduleTargetRefresh(event.getPlayer(), ModerationInventoryHolder.Kind.PLAYER);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onLockedPickup(PlayerAttemptPickupItemEvent event) {
        if (interactionRestricted(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
            return;
        }
        scheduleTargetRefresh(event.getPlayer(), ModerationInventoryHolder.Kind.PLAYER);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onLockedEntityPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (interactionRestricted(player.getUniqueId())) {
            event.setCancelled(true);
            return;
        }
        scheduleTargetRefresh(player, ModerationInventoryHolder.Kind.PLAYER);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onLockedSwap(PlayerSwapHandItemsEvent event) {
        if (interactionRestricted(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
            return;
        }
        scheduleTargetRefresh(event.getPlayer(), ModerationInventoryHolder.Kind.PLAYER);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onLockedHeldSlot(PlayerItemHeldEvent event) {
        if (interactionRestricted(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
            return;
        }
        scheduleTargetRefresh(event.getPlayer(), ModerationInventoryHolder.Kind.PLAYER);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onLockedInteract(PlayerInteractEvent event) {
        if (interactionRestricted(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
            return;
        }
        scheduleTargetRefresh(event.getPlayer(), ModerationInventoryHolder.Kind.PLAYER);
    }

    private void openLive(
            Player viewer,
            PlayerIdentity target,
            Player online,
            ModerationInventoryHolder.Kind kind
    ) {
        LiveSession existing = liveSessions.get(target.playerId());
        if (existing != null) {
            openView(viewer, target, kind, false, existing.observation(), existing.image(), existing);
            return;
        }
        online.getScheduler().execute(plugin, () -> {
            InventoryImage image = codec.capture(online);
            InventoryImageCodec.EncodedImage encoded = codec.encodeWithChecksum(image);
            submit(() -> createLiveSession(viewer, target, kind, image, encoded));
        }, () -> openOffline(viewer, target, kind), 1L);
    }

    private void createLiveSession(
            Player viewer,
            PlayerIdentity target,
            ModerationInventoryHolder.Kind kind,
            InventoryImage image,
            InventoryImageCodec.EncodedImage encoded
    ) {
        InventoryJournalStore loaded = store.get();
        if (loaded == null) {
            message(viewer, "Inventory storage is not ready; the view was not opened.");
            return;
        }
        try {
            InventoryObservation observation = loaded.recordObservation(
                    target.playerId(), scopeId, serverId, encoded.checksum(), encoded.bytes(), clock.instant()
            );
            LiveSession session = liveSessions.compute(target.playerId(), (ignored, current) -> {
                LiveSession selected = current == null ? new LiveSession(target.playerId()) : current;
                if (!selected.working()) {
                    selected.observed(observation, image);
                }
                return selected;
            });
            openView(viewer, target, kind, false, session.observation(), session.image(), session);
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.SEVERE, "Unable to prepare a live inventory view", exception);
            message(viewer, "The live inventory could not be journaled; no view was opened.");
        }
    }

    private void openOffline(
            Player viewer,
            PlayerIdentity target,
            ModerationInventoryHolder.Kind kind
    ) {
        submit(() -> {
            InventoryJournalStore loaded = store.get();
            PlayerDirectory players = directory.get();
            if (loaded == null || players == null) {
                message(viewer, "Inventory storage is not ready; no view was opened.");
                return;
            }
            try {
                PlayerPresence presence = players.presence(target.playerId()).orElse(null);
                if (presence == null || presence.online()) {
                    message(viewer, "The player is online network-wide; retry to open the live inventory.");
                    return;
                }
                InventoryObservation observation = loaded.latest(target.playerId(), scopeId).orElse(null);
                if (observation == null || !observation.owningServerId().equals(serverId)) {
                    message(viewer, "This backend has no authoritative offline snapshot for that inventory scope.");
                    return;
                }
                openView(viewer, target, kind, true, observation, codec.decode(observation.snapshot()), null);
            } catch (RuntimeException exception) {
                plugin.getLogger().log(Level.SEVERE, "Unable to load an offline inventory view", exception);
                message(viewer, "The offline inventory could not be loaded safely.");
            }
        });
    }

    private void openView(
            Player viewer,
            PlayerIdentity target,
            ModerationInventoryHolder.Kind kind,
            boolean offline,
            InventoryObservation observation,
            InventoryImage image,
            LiveSession session
    ) {
        onEntity(viewer, () -> {
            if (!viewer.isOnline()) {
                return;
            }
            ModerationInventoryHolder holder = new ModerationInventoryHolder(
                    viewer.getUniqueId(),
                    target.playerId(),
                    target.currentUsername().orElse(target.playerId().toString()),
                    kind,
                    offline,
                    observation,
                    image
            );
            int size = kind == ModerationInventoryHolder.Kind.ENDER_CHEST ? 27 : 54;
            String suffix = offline ? " [offline, queued]" : " [live]";
            Inventory inventory = Bukkit.createInventory(
                    holder,
                    size,
                    Component.text((kind == ModerationInventoryHolder.Kind.ENDER_CHEST ? "Ender: " : "Inventory: ")
                            + holder.targetName() + suffix)
            );
            holder.attach(inventory);
            if (session != null) {
                session.addViewer(holder);
            }
            render(holder, image);
            viewer.openInventory(inventory);
        });
    }

    private void startLiveTransfer(
            Player viewer,
            LiveSession session,
            LiveInventoryTransferExecution transfer
    ) {
        if (!session.beginEdit(transfer)) {
            renderHolderFromSession(findHolder(session, viewer.getUniqueId()), session);
            return;
        }
        if (!assetLocks.add(transfer.targetId())) {
            finishSessionTransfer(session, transfer);
            message(viewer, "Another asset operation already owns this player.");
            return;
        }
        if (viewerTransfers.putIfAbsent(transfer.viewerId(), transfer) != null) {
            assetLocks.remove(transfer.targetId());
            finishSessionTransfer(session, transfer);
            message(viewer, "Your previous inventory transfer is still finishing.");
            return;
        }
        Player target = plugin.getServer().getPlayer(transfer.targetId());
        if (target == null) {
            finishLiveFailure(viewer, session, transfer, "The target left before the transfer started.");
            return;
        }
        InventoryPrepareRequest request = livePrepareRequest(transfer, session.observation());
        if (!submit(() -> prepareAndApplyLive(viewer, target, session, request, transfer))) {
            finishLiveFailure(viewer, session, transfer, "The inventory worker queue is busy; retry the transfer.");
        }
    }

    private InventoryPrepareRequest livePrepareRequest(
            LiveInventoryTransferExecution transfer,
            InventoryObservation before
    ) {
        InventoryImageCodec.EncodedImage replacementBytes = codec.encodeWithChecksum(transfer.replacementImage());
        InventoryCursorTransfer cursor = cursorEscrow.durableTransfer(
                transfer.expectedCursor(), transfer.resultingCursor()
        );
        return new InventoryPrepareRequest(
                transfer.operationId(),
                new IdempotencyKey("inventory:live-cursor:" + transfer.operationId()).value(),
                transfer.targetId(),
                scopeId,
                serverId,
                transfer.viewerId(),
                Optional.empty(),
                LIVE_CURSOR_PREFIX + transfer.action().name(),
                before.revision(),
                before.checksum(),
                before.snapshot(),
                replacementBytes.checksum(),
                replacementBytes.bytes(),
                List.of(transfer.logicalSlot()),
                false,
                Optional.of(cursor)
        );
    }

    private void prepareAndApplyLive(
            Player viewer,
            Player target,
            LiveSession session,
            InventoryPrepareRequest request,
            LiveInventoryTransferExecution transfer
    ) {
        if (!editAuthority.current(viewer)) {
            finishLiveFailure(
                    viewer, session, transfer,
                    "Your inventory edit authority changed; no durable transfer was prepared."
            );
            return;
        }
        InventoryJournalStore loaded = store.get();
        if (loaded == null) {
            finishLiveFailure(viewer, session, transfer, "Inventory storage became unavailable.");
            return;
        }
        try {
            InventoryPatch patch = prepareAndClaimLivePatch(loaded, viewer, session, request, transfer);
            if (patch == null) {
                return;
            }
            if (patch.state() == InventoryOperationState.APPLIED) {
                finishLiveFailure(viewer, session, transfer, "This durable transfer was already resolved.");
                reconcile(session);
                return;
            }
            transfer.patch(patch);
            scheduleSourceEscrow(viewer, target, session, patch, transfer);
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.SEVERE, "Live inventory operation preparation failed", exception);
            quarantineTransfer(transfer, "LIVE_PREPARE_FAILED", "Live cursor transfer preparation failed");
            finishLiveFailure(viewer, session, transfer, "The inventory transfer failed before target state changed.");
        }
    }

    private InventoryPatch prepareAndClaimLivePatch(
            InventoryJournalStore loaded,
            Player viewer,
            LiveSession session,
            InventoryPrepareRequest request,
            LiveInventoryTransferExecution transfer
    ) {
        InventoryPreparation preparation = loaded.prepare(request, APPLY_LEASE, clock.instant());
        if (preparation.patch().isEmpty()) {
            finishLiveFailure(viewer, session, transfer, preparation.detail());
            reconcile(session);
            return null;
        }
        InventoryPatch prepared = preparation.patch().orElseThrow();
        transfer.patch(prepared);
        InventoryPatch patch = loaded.claimForApply(
                prepared.patchId(), request.operationId(), APPLY_LEASE, clock.instant()
        ).orElse(null);
        if (patch == null) {
            quarantineTransfer(transfer, "LIVE_CLAIM_FAILED", "Prepared live cursor transfer could not be claimed");
            finishLiveFailure(viewer, session, transfer, "The prepared inventory transfer lease could not be claimed.");
        }
        return patch;
    }

    private void scheduleSourceEscrow(
            Player viewer,
            Player target,
            LiveSession session,
            InventoryPatch patch,
            LiveInventoryTransferExecution transfer
    ) {
        viewer.getScheduler().execute(
                plugin,
                () -> escrowSourceOnViewer(viewer, target, session, patch, transfer),
                () -> failClosedTransfer(
                        viewer, session, transfer,
                        "The Staff viewer left before cursor escrow could be verified."
                ),
                1L
        );
    }

    private void escrowSourceOnViewer(
            Player viewer,
            Player target,
            LiveSession session,
            InventoryPatch patch,
            LiveInventoryTransferExecution transfer
    ) {
        if (!viewer.isOnline() || !cursorEscrow.escrowSource(viewer, transfer)) {
            restoreSourceThenResolve(
                    viewer, session, transfer,
                    "Your cursor changed before the transfer started."
            );
            return;
        }
        if (!submit(() -> advanceSourceEscrowPhase(viewer, target, session, patch, transfer))) {
            failClosedTransfer(
                    viewer, session, transfer,
                    "Cursor escrow was prepared, but the inventory worker queue became unavailable."
            );
        }
    }

    private void advanceSourceEscrowPhase(
            Player viewer,
            Player target,
            LiveSession session,
            InventoryPatch patch,
            LiveInventoryTransferExecution transfer
    ) {
        InventoryJournalStore loaded = store.get();
        if (loaded == null || !loaded.advanceCursorPhase(
                patch.patchId(), patch.operationId(), patch.fencingToken(),
                InventoryCursorPhase.PREPARED, InventoryCursorPhase.SOURCE_ESCROWED, clock.instant()
        )) {
            restoreSourceThenResolve(
                    viewer, session, transfer,
                    "Durable cursor escrow could not be fenced."
            );
            return;
        }
        scheduleLiveApplication(viewer, target, session, patch, transfer);
    }

    private void scheduleLiveApplication(
            Player viewer,
            Player target,
            LiveSession session,
            InventoryPatch patch,
            LiveInventoryTransferExecution transfer
    ) {
        target.getScheduler().execute(
                plugin,
                () -> applyLiveOnTarget(viewer, target, session, patch, transfer),
                () -> handleRetiredTarget(viewer, session, transfer),
                1L
        );
    }

    private void applyLiveOnTarget(
            Player viewer,
            Player target,
            LiveSession session,
            InventoryPatch patch,
            LiveInventoryTransferExecution transfer
    ) {
        AtomicBoolean unsafeMutation = new AtomicBoolean();
        boolean applied = transfer.applyTarget(() -> applyTargetSlot(target, patch, transfer, unsafeMutation));
        if (!applied) {
            if (unsafeMutation.get()) {
                failClosedTransfer(viewer, session, transfer, "Target rollback could not be verified.");
            } else {
                restoreSourceThenResolve(
                        viewer, session, transfer,
                        "Target inventory changed before the cursor transfer could commit."
                );
            }
            return;
        }
        if (!submit(() -> advanceTargetAppliedPhase(viewer, target, session, patch, transfer))) {
            failClosedTransfer(
                    viewer, session, transfer,
                    "The target changed, but durable target-phase fencing was deferred."
            );
        }
    }

    private boolean applyTargetSlot(
            Player target,
            InventoryPatch patch,
            LiveInventoryTransferExecution transfer,
            AtomicBoolean unsafeMutation
    ) {
        InventoryImageCodec.EncodedImage current = codec.encodeWithChecksum(codec.capture(target));
        if (!current.checksum().equals(patch.expectedChecksum())) {
            return false;
        }
        try {
            codec.applySlots(target, transfer.replacementImage(), List.of(transfer.logicalSlot()));
            InventoryImageCodec.EncodedImage applied = codec.encodeWithChecksum(codec.capture(target));
            if (applied.checksum().equals(patch.replacementChecksum())) {
                return true;
            }
            unsafeMutation.set(!restoreTargetSlot(target, patch.expectedChecksum(), transfer));
            return false;
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.SEVERE, "Live target inventory mutation failed", exception);
            unsafeMutation.set(!restoreTargetSlot(target, patch.expectedChecksum(), transfer));
            return false;
        }
    }

    private void advanceTargetAppliedPhase(
            Player viewer,
            Player target,
            LiveSession session,
            InventoryPatch patch,
            LiveInventoryTransferExecution transfer
    ) {
        InventoryJournalStore loaded = store.get();
        if (loaded == null || !loaded.advanceCursorPhase(
                patch.patchId(), patch.operationId(), patch.fencingToken(),
                InventoryCursorPhase.SOURCE_ESCROWED, InventoryCursorPhase.TARGET_APPLIED, clock.instant()
        )) {
            rollbackTargetThenRestore(viewer, target, session, transfer, "Target phase fencing failed.");
            return;
        }
        scheduleCursorApplication(viewer, target, session, patch, transfer);
    }

    private void scheduleCursorApplication(
            Player viewer,
            Player target,
            LiveSession session,
            InventoryPatch patch,
            LiveInventoryTransferExecution transfer
    ) {
        viewer.getScheduler().execute(
                plugin,
                () -> applyCursorOnViewer(viewer, target, session, patch, transfer),
                () -> failClosedTransfer(
                        viewer, session, transfer,
                        "The Staff viewer disconnected after the target change was fenced."
                ),
                1L
        );
    }

    private void applyCursorOnViewer(
            Player viewer,
            Player target,
            LiveSession session,
            InventoryPatch patch,
            LiveInventoryTransferExecution transfer
    ) {
        boolean cursorApplied = transfer.applyCursor(() -> cursorEscrow.applyResult(viewer, transfer));
        if (!cursorApplied) {
            rollbackTargetThenRestore(
                    viewer, target, session, transfer,
                    "Your cursor changed while the transfer was committing."
            );
            return;
        }
        session.imageOnly(transfer.replacementImage());
        renderSession(session, EnumSet.of(transfer.kind()));
        if (!submit(() -> advanceCursorAppliedPhase(viewer, session, patch, transfer))) {
            failClosedTransfer(
                    viewer, session, transfer,
                    "Both physical sides changed, but durable cursor-phase fencing was deferred."
            );
        }
    }

    private void advanceCursorAppliedPhase(
            Player viewer,
            LiveSession session,
            InventoryPatch patch,
            LiveInventoryTransferExecution transfer
    ) {
        InventoryJournalStore loaded = store.get();
        if (loaded == null || !loaded.advanceCursorPhase(
                patch.patchId(), patch.operationId(), patch.fencingToken(),
                InventoryCursorPhase.TARGET_APPLIED, InventoryCursorPhase.CURSOR_APPLIED, clock.instant()
        )) {
            failClosedTransfer(
                    viewer, session, transfer,
                    "Both inventories changed, but durable cursor completion could not be fenced."
            );
            return;
        }
        onEntity(viewer, () -> settleCursorThenFinalize(viewer, session, patch, transfer));
    }

    private void settleCursorThenFinalize(
            Player viewer,
            LiveSession session,
            InventoryPatch patch,
            LiveInventoryTransferExecution transfer
    ) {
        if (!cursorEscrow.settleResult(viewer, transfer)) {
            failClosedTransfer(
                    viewer, session, transfer,
                    "The Staff cursor result could not be verified after durable fencing."
            );
            return;
        }
        InventoryImageCodec.EncodedImage applied = codec.encodeWithChecksum(transfer.replacementImage());
        if (!submit(() -> finalizeLive(viewer, session, patch, transfer, applied))) {
            failClosedTransfer(
                    viewer, session, transfer,
                    "The transfer completed physically but durable finalization is pending."
            );
        }
    }

    private void finalizeLive(
            Player viewer,
            LiveSession session,
            InventoryPatch patch,
            LiveInventoryTransferExecution transfer,
            InventoryImageCodec.EncodedImage appliedBytes
    ) {
        try {
            InventoryJournalStore loaded = store.get();
            if (loaded == null) {
                failClosedTransfer(viewer, session, transfer, "Durable inventory finalization is unavailable.");
                return;
            }
            InventoryFinalizeResult result = loaded.finalizeApplied(
                    patch.patchId(), patch.operationId(), patch.fencingToken(),
                    appliedBytes.checksum(), appliedBytes.bytes(), clock.instant()
            );
            if (!committed(result)) {
                failClosedTransfer(viewer, session, transfer, result.detail());
                return;
            }
            InventoryObservation next = new InventoryObservation(
                    patch.profileId(), patch.playerId(), patch.scopeId(), patch.owningServerId(),
                    result.resultingRevision(), appliedBytes.checksum(), appliedBytes.bytes(), clock.instant()
            );
            session.observed(next, transfer.replacementImage());
            transfer.committed();
            renderSession(session, EnumSet.of(transfer.kind()));
            releaseLiveTransfer(session, transfer);
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.SEVERE, "Live cursor transfer finalization failed", exception);
            failClosedTransfer(
                    viewer, session, transfer,
                    "The transfer reached both inventories but final verification is pending."
            );
        }
    }

    private void rollbackTargetThenRestore(
            Player viewer,
            Player target,
            LiveSession session,
            LiveInventoryTransferExecution transfer,
            String detail
    ) {
        LiveInventoryTransferExecution.AbortResult abort = transfer.abort();
        if (abort == LiveInventoryTransferExecution.AbortResult.PHYSICAL_TRANSFER_COMPLETE) {
            failClosedTransfer(viewer, session, transfer, detail);
            return;
        }
        if (abort == LiveInventoryTransferExecution.AbortResult.NO_TARGET_CHANGE) {
            restoreSourceThenResolve(viewer, session, transfer, detail);
            return;
        }
        target.getScheduler().execute(
                plugin,
                () -> rollbackTargetOnEntity(viewer, target, session, transfer, detail),
                () -> failClosedTransfer(viewer, session, transfer, detail),
                1L
        );
    }

    private void rollbackTargetOnEntity(
            Player viewer,
            Player target,
            LiveSession session,
            LiveInventoryTransferExecution transfer,
            String detail
    ) {
        InventoryPatch patch = transfer.patch();
        if (patch != null && rollbackTargetNow(target, patch.expectedChecksum(), transfer)) {
            transfer.rolledBack();
            restoreSourceThenResolve(viewer, session, transfer, detail);
            return;
        }
        failClosedTransfer(viewer, session, transfer, detail);
    }

    private boolean rollbackTargetNow(
            Player target,
            String expectedChecksum,
            LiveInventoryTransferExecution transfer
    ) {
        InventoryImageCodec.EncodedImage current = codec.encodeWithChecksum(codec.capture(target));
        if (current.checksum().equals(expectedChecksum)) {
            return true;
        }
        InventoryImageCodec.EncodedImage replacement = codec.encodeWithChecksum(transfer.replacementImage());
        if (!current.checksum().equals(replacement.checksum())) {
            return false;
        }
        return restoreTargetSlot(target, expectedChecksum, transfer);
    }

    private boolean restoreTargetSlot(
            Player target,
            String expectedChecksum,
            LiveInventoryTransferExecution transfer
    ) {
        try {
            codec.applySlots(target, transfer.beforeImage(), List.of(transfer.logicalSlot()));
            return codec.encodeWithChecksum(codec.capture(target)).checksum().equals(expectedChecksum);
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.SEVERE, "Live cursor transfer rollback failed", exception);
            return false;
        }
    }

    private void restoreSourceThenResolve(
            Player viewer,
            LiveSession session,
            LiveInventoryTransferExecution transfer,
            String detail
    ) {
        onEntity(viewer, () -> {
            if (!sourceRestored(viewer, transfer)) {
                failClosedTransfer(viewer, session, transfer, detail);
                return;
            }
            InventoryPatch patch = transfer.patch();
            if (patch == null) {
                finishLiveFailure(viewer, session, transfer, detail);
                return;
            }
            if (!submit(() -> resolveLiveRollback(viewer, session, transfer, detail))) {
                failClosedTransfer(
                        viewer, session, transfer,
                        detail + " Durable rollback resolution was deferred."
                );
            }
        });
    }

    private boolean sourceRestored(Player viewer, LiveInventoryTransferExecution transfer) {
        if (LiveInventoryTransferDecision.same(viewer.getItemOnCursor(), transfer.expectedCursor())) {
            return true;
        }
        return cursorEscrow.restoreSource(viewer, transfer);
    }

    private void resolveLiveRollback(
            Player viewer,
            LiveSession session,
            LiveInventoryTransferExecution transfer,
            String detail
    ) {
        InventoryPatch patch = transfer.patch();
        InventoryJournalStore loaded = store.get();
        if (patch != null && loaded != null && loaded.resolveCursorRollback(
                patch.patchId(), patch.operationId(), patch.fencingToken(), clock.instant()
        )) {
            releaseLiveTransfer(session, transfer);
            message(viewer, detail + " Both inventories were restored.");
            reconcile(session);
            return;
        }
        failClosedTransfer(viewer, session, transfer, detail);
    }

    private void handleTargetDeparture(Player target) {
        LiveSession session = liveSessions.get(target.getUniqueId());
        if (session == null) {
            return;
        }
        LiveInventoryTransferExecution transfer = session.activeTransfer();
        if (transfer == null) {
            return;
        }
        Player viewer = plugin.getServer().getPlayer(transfer.viewerId());
        if (transfer.physicalTransferComplete()) {
            scheduleRecoveryForTransfer(viewer, session, transfer, "Target left during durable finalization.");
            return;
        }
        if (transfer.targetAppliedWithoutCursor()) {
            LiveInventoryTransferExecution.AbortResult abort = transfer.abort();
            InventoryPatch patch = transfer.patch();
            if (abort == LiveInventoryTransferExecution.AbortResult.ROLLBACK_TARGET
                    && patch != null
                    && rollbackTargetNow(target, patch.expectedChecksum(), transfer)) {
                transfer.rolledBack();
                restoreSourceThenResolve(
                        viewer, session, transfer,
                        "The target left; the in-flight transfer was rolled back."
                );
                return;
            }
            failClosedTransfer(viewer, session, transfer, "Target departure rollback was not verifiable.");
            return;
        }
        restoreSourceThenResolve(viewer, session, transfer, "The target left before target mutation.");
    }

    private void handleViewerDeparture(Player viewer) {
        LiveInventoryTransferExecution transfer = viewerTransfers.get(viewer.getUniqueId());
        if (transfer == null) {
            return;
        }
        LiveSession session = liveSessions.get(transfer.targetId());
        if (session == null) {
            return;
        }
        if (transfer.targetAppliedWithoutCursor() || transfer.physicalTransferComplete()) {
            scheduleRecoveryForTransfer(
                    viewer, session, transfer,
                    "The Staff viewer disconnected during a fenced cursor transfer."
            );
            return;
        }
        restoreSourceThenResolve(
                viewer, session, transfer,
                "The Staff viewer disconnected before target mutation."
        );
    }

    private void handleRetiredTarget(
            Player viewer,
            LiveSession session,
            LiveInventoryTransferExecution transfer
    ) {
        scheduleRecoveryForTransfer(viewer, session, transfer, "The target entity retired during transfer.");
    }

    private void scheduleRecoveryForTransfer(
            Player viewer,
            LiveSession session,
            LiveInventoryTransferExecution transfer,
            String detail
    ) {
        failClosedTransfer(viewer, session, transfer, detail);
    }

    private void failClosedTransfer(
            Player viewer,
            LiveSession session,
            LiveInventoryTransferExecution transfer,
            String detail
    ) {
        loginBlocks.add(transfer.targetId());
        loginBlocks.add(transfer.viewerId());
        viewerTransfers.remove(transfer.viewerId(), transfer);
        finishSessionTransfer(session, transfer);
        message(viewer, detail + " Both participants are interaction-blocked for inventory recovery.");
        alertStaff("Inventory safety blocked transfer " + transfer.operationId() + ": " + detail);
        closeTargetViews(transfer.targetId(), "Inventory safety verification requires recovery.");
        InventoryPatch patch = transfer.patch();
        if (patch != null) {
            scheduleCursorRecoveryLookup(patch);
        }
    }

    private void scheduleCursorRecoveryLookup(InventoryPatch patch) {
        if (!submit(() -> loadCursorRecovery(patch))) {
            plugin.getServer().getGlobalRegionScheduler().runDelayed(
                    plugin,
                    ignored -> scheduleCursorRecoveryLookup(patch),
                    20L
            );
        }
    }

    private void loadCursorRecovery(InventoryPatch patch) {
        InventoryJournalStore loaded = store.get();
        if (loaded == null) {
            plugin.getServer().getGlobalRegionScheduler().runDelayed(
                    plugin,
                    ignored -> scheduleCursorRecoveryLookup(patch),
                    20L
            );
            return;
        }
        loaded.cursorTransfer(patch.operationId()).ifPresent(recovery -> {
            rememberCursorRecovery(recovery);
            attemptCursorRecovery(recovery);
        });
    }

    private void finishLiveFailure(
            Player viewer,
            LiveSession session,
            LiveInventoryTransferExecution transfer,
            String detail
    ) {
        releaseLiveTransfer(session, transfer);
        message(viewer, detail);
    }

    private void releaseLiveTransfer(LiveSession session, LiveInventoryTransferExecution transfer) {
        assetLocks.remove(transfer.targetId());
        viewerTransfers.remove(transfer.viewerId(), transfer);
        if (!hasCursorRecoveryFor(transfer.targetId())) {
            loginBlocks.remove(transfer.targetId());
        }
        if (!hasCursorRecoveryFor(transfer.viewerId())) {
            loginBlocks.remove(transfer.viewerId());
        }
        finishSessionTransfer(session, transfer);
    }

    private void quarantineTransfer(
            LiveInventoryTransferExecution transfer,
            String reasonCode,
            String detail
    ) {
        InventoryPatch patch = transfer.patch();
        if (patch != null) {
            quarantine(patch, reasonCode, detail);
        }
    }

    private void rememberCursorRecovery(InventoryCursorJournal recovery) {
        cursorRecoveries.put(recovery.patch().operationId(), recovery);
    }

    private boolean hasCursorRecoveryFor(UUID playerId) {
        return cursorRecoveries.values().stream().anyMatch(recovery ->
                recovery.patch().playerId().equals(playerId) || recovery.patch().actorId().equals(playerId));
    }

    private void attemptMatchingRecoveries(UUID playerId) {
        for (InventoryCursorJournal recovery : List.copyOf(cursorRecoveries.values())) {
            if (recovery.patch().playerId().equals(playerId) || recovery.patch().actorId().equals(playerId)) {
                attemptCursorRecovery(recovery);
            }
        }
    }

    private void attemptCursorRecovery(InventoryCursorJournal recovery) {
        InventoryPatch patch = recovery.patch();
        if (!serverId.equals(patch.owningServerId())) {
            return;
        }
        Player target = plugin.getServer().getPlayer(patch.playerId());
        Player actor = plugin.getServer().getPlayer(patch.actorId());
        if (target == null || actor == null || !recoveryInFlight.add(patch.operationId())) {
            return;
        }
        loginBlocks.add(patch.playerId());
        loginBlocks.add(patch.actorId());
        if (!submit(() -> claimCursorRecovery(target, actor, recovery))) {
            recoveryInFlight.remove(patch.operationId());
            retryCursorRecovery(recovery, "Cursor recovery worker queue is busy.");
        }
    }

    private void claimCursorRecovery(
            Player target,
            Player actor,
            InventoryCursorJournal recovery
    ) {
        InventoryJournalStore loaded = store.get();
        if (loaded == null) {
            retryCursorRecovery(recovery, RECOVERY_STORAGE_UNAVAILABLE);
            return;
        }
        try {
            InventoryPatch patch = recovery.patch();
            InventoryPatch claimed = loaded.claimForApply(
                    patch.patchId(), patch.operationId(), APPLY_LEASE, clock.instant()
            ).orElse(null);
            if (claimed == null) {
                retryCursorRecovery(recovery, "Cursor recovery lease is busy.");
                return;
            }
            if (claimed.state() == InventoryOperationState.APPLIED) {
                completeCursorRecovery(recovery);
                return;
            }
            InventoryCursorJournal refreshed = loaded.cursorTransfer(patch.operationId()).orElse(null);
            if (refreshed == null) {
                keepRecoveryBlocked(
                        patch.playerId(), patch.actorId(), patch.operationId(),
                        "Cursor recovery metadata disappeared after the transfer had started."
                );
                return;
            }
            cursorRecoveries.put(patch.operationId(), refreshed);
            target.getScheduler().execute(
                    plugin,
                    () -> inspectRecoveryTarget(target, actor, refreshed),
                    () -> retryCursorRecovery(refreshed, "Target left before recovery state inspection."),
                    1L
            );
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.SEVERE, "Unable to claim live cursor recovery", exception);
            retryCursorRecovery(recovery, "Cursor recovery claim failed.");
        }
    }

    private void inspectRecoveryTarget(
            Player target,
            Player actor,
            InventoryCursorJournal recovery
    ) {
        InventoryImageCodec.EncodedImage current = codec.encodeWithChecksum(codec.capture(target));
        InventoryPatch patch = recovery.patch();
        if (current.checksum().equals(patch.expectedChecksum())) {
            scheduleActorRecovery(actor, recovery, false);
            return;
        }
        if (current.checksum().equals(patch.replacementChecksum())) {
            scheduleActorRecovery(actor, recovery, true);
            return;
        }
        quarantine(patch, "LIVE_CURSOR_RECOVERY_CONFLICT", "Target is neither prepared before nor replacement state");
        keepRecoveryBlocked(
                patch.playerId(), patch.actorId(), patch.operationId(),
                "Target inventory conflicts with both durable cursor transfer states."
        );
    }

    private void scheduleActorRecovery(
            Player actor,
            InventoryCursorJournal recovery,
            boolean targetApplied
    ) {
        actor.getScheduler().execute(
                plugin,
                () -> recoverActorCursor(actor, recovery, targetApplied),
                () -> retryCursorRecovery(recovery, "Staff viewer left before cursor recovery."),
                1L
        );
    }

    private void recoverActorCursor(
            Player actor,
            InventoryCursorJournal recovery,
            boolean targetApplied
    ) {
        LiveCursorEscrow.RecoveryResult result = targetApplied
                ? cursorEscrow.recoverResult(actor, recovery)
                : cursorEscrow.recoverSource(actor, recovery);
        if (result == LiveCursorEscrow.RecoveryResult.CONFLICT) {
            keepRecoveryBlocked(
                    recovery.patch().playerId(),
                    recovery.patch().actorId(),
                    recovery.patch().operationId(),
                    "Staff cursor conflicts with durable transfer recovery state."
            );
            return;
        }
        if (!targetApplied) {
            if (!submit(() -> resolveRecoveredRollback(recovery))) {
                retryCursorRecovery(recovery, "Recovered rollback worker queue is busy.");
            }
            return;
        }
        boolean marked = result == LiveCursorEscrow.RecoveryResult.RESULT_MARKED;
        if (!submit(() -> advanceRecoveredCursor(actor, recovery, marked))) {
            retryCursorRecovery(recovery, "Recovered cursor-phase worker queue is busy.");
        }
    }

    private void resolveRecoveredRollback(InventoryCursorJournal recovery) {
        InventoryPatch patch = recovery.patch();
        InventoryJournalStore loaded = store.get();
        if (loaded != null && loaded.resolveCursorRollback(
                patch.patchId(), patch.operationId(), patch.fencingToken(), clock.instant()
        )) {
            completeCursorRecovery(recovery);
            return;
        }
        retryCursorRecovery(recovery, "Recovered before-state could not be durably resolved.");
    }

    private void advanceRecoveredCursor(
            Player actor,
            InventoryCursorJournal recovery,
            boolean resultMarked
    ) {
        if (!advanceRecoveryPhases(recovery)) {
            retryCursorRecovery(recovery, "Recovered cursor phases could not be durably fenced.");
            return;
        }
        if (!resultMarked) {
            finalizeRecoveredCursor(recovery);
            return;
        }
        actor.getScheduler().execute(
                plugin,
                () -> {
                    if (cursorEscrow.settleRecoveredResult(actor, recovery)) {
                        if (!submit(() -> finalizeRecoveredCursor(recovery))) {
                            retryCursorRecovery(recovery, "Recovered finalization worker queue is busy.");
                        }
                    } else {
                        keepRecoveryBlocked(
                                recovery.patch().playerId(), recovery.patch().actorId(),
                                recovery.patch().operationId(),
                                "Recovered cursor marker could not be settled safely."
                        );
                    }
                },
                () -> retryCursorRecovery(recovery, "Staff viewer left before recovered cursor settlement."),
                1L
        );
    }

    private boolean advanceRecoveryPhases(InventoryCursorJournal recovery) {
        InventoryJournalStore loaded = store.get();
        if (loaded == null) {
            return false;
        }
        InventoryPatch patch = recovery.patch();
        InventoryCursorPhase phase = recovery.phase();
        if (phase == InventoryCursorPhase.PREPARED) {
            if (!advancePhase(loaded, patch, phase, InventoryCursorPhase.SOURCE_ESCROWED)) {
                return false;
            }
            phase = InventoryCursorPhase.SOURCE_ESCROWED;
        }
        if (phase == InventoryCursorPhase.SOURCE_ESCROWED) {
            if (!advancePhase(loaded, patch, phase, InventoryCursorPhase.TARGET_APPLIED)) {
                return false;
            }
            phase = InventoryCursorPhase.TARGET_APPLIED;
        }
        return phase == InventoryCursorPhase.CURSOR_APPLIED
                || advancePhase(loaded, patch, phase, InventoryCursorPhase.CURSOR_APPLIED);
    }

    private boolean advancePhase(
            InventoryJournalStore loaded,
            InventoryPatch patch,
            InventoryCursorPhase expected,
            InventoryCursorPhase next
    ) {
        return loaded.advanceCursorPhase(
                patch.patchId(), patch.operationId(), patch.fencingToken(), expected, next, clock.instant()
        );
    }

    private void finalizeRecoveredCursor(InventoryCursorJournal recovery) {
        InventoryPatch patch = recovery.patch();
        InventoryJournalStore loaded = store.get();
        if (loaded == null) {
            retryCursorRecovery(recovery, "Inventory recovery storage is unavailable during finalization.");
            return;
        }
        try {
            InventoryFinalizeResult result = loaded.finalizeApplied(
                    patch.patchId(), patch.operationId(), patch.fencingToken(),
                    patch.replacementChecksum(), patch.replacementSnapshot(), clock.instant()
            );
            if (committed(result)) {
                completeCursorRecovery(recovery);
            } else {
                retryCursorRecovery(recovery, result.detail());
            }
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.SEVERE, "Recovered cursor transfer finalization failed", exception);
            retryCursorRecovery(recovery, "Recovered cursor transfer finalization failed.");
        }
    }

    private static boolean committed(InventoryFinalizeResult result) {
        return result.status() == InventoryFinalizeResult.Status.COMMITTED
                || result.status() == InventoryFinalizeResult.Status.REPLAYED;
    }

    private void completeCursorRecovery(InventoryCursorJournal recovery) {
        InventoryPatch patch = recovery.patch();
        cursorRecoveries.remove(patch.operationId());
        recoveryAttempts.remove(patch.operationId());
        recoveryInFlight.remove(patch.operationId());
        assetLocks.remove(patch.playerId());
        loginBlocks.remove(patch.playerId());
        loginBlocks.remove(patch.actorId());
        viewerTransfers.remove(patch.actorId());
        LiveSession session = liveSessions.get(patch.playerId());
        if (session != null) {
            finishSessionWork(session);
            reconcile(session);
        }
    }

    private void retryCursorRecovery(InventoryCursorJournal recovery, String detail) {
        UUID operationId = recovery.patch().operationId();
        recoveryInFlight.remove(operationId);
        int attempt = recoveryAttempts.merge(operationId, 1, Integer::sum);
        if (attempt >= MAX_LOGIN_APPLY_ATTEMPTS) {
            keepRecoveryBlocked(
                    recovery.patch().playerId(), recovery.patch().actorId(), operationId,
                    detail + " Automatic recovery attempts are exhausted."
            );
            return;
        }
        plugin.getServer().getGlobalRegionScheduler().runDelayed(
                plugin,
                ignored -> {
                    InventoryCursorJournal current = cursorRecoveries.get(operationId);
                    if (current != null) {
                        attemptCursorRecovery(current);
                    }
                },
                20L
        );
    }

    private void keepRecoveryBlocked(UUID targetId, UUID actorId, UUID operationId, String detail) {
        recoveryInFlight.remove(operationId);
        loginBlocks.add(targetId);
        loginBlocks.add(actorId);
        alertStaff("Live inventory recovery " + operationId + " remains blocked: " + detail);
    }

    private void queueOfflineEdit(Player viewer, ModerationInventoryHolder holder) {
        if (!holder.dirty()) {
            return;
        }
        InventoryImage before = codec.decode(holder.base().snapshot());
        InventoryImage replacement = holder.image();
        List<Integer> changedSlots = before.changedSlots(replacement);
        if (changedSlots.isEmpty()) {
            return;
        }
        InventoryImageCodec.EncodedImage encoded = codec.encodeWithChecksum(replacement);
        UUID operationId = UUID.randomUUID();
        InventoryPrepareRequest request = new InventoryPrepareRequest(
                operationId,
                new IdempotencyKey("inventory:offline:" + operationId).value(),
                holder.targetId(),
                scopeId,
                serverId,
                viewer.getUniqueId(),
                Optional.empty(),
                "OFFLINE_EDIT",
                holder.base().revision(),
                holder.base().checksum(),
                holder.base().snapshot(),
                encoded.checksum(),
                encoded.bytes(),
                changedSlots,
                true,
                Optional.empty()
        );
        submit(() -> prepareOfflineEdit(viewer, request));
    }

    private void prepareOfflineEdit(Player viewer, InventoryPrepareRequest request) {
        if (!editAuthority.current(viewer)) {
            message(viewer, "Your inventory edit authority changed; no offline patch was queued.");
            return;
        }
        InventoryJournalStore loaded = store.get();
        if (loaded == null) {
            message(viewer, "Offline inventory storage is unavailable; no patch was queued.");
            return;
        }
        try {
            InventoryPreparation result = loaded.prepare(request, APPLY_LEASE, clock.instant());
            if (result.patch().isPresent()) {
                message(viewer, "Offline inventory patch committed; it will apply before the player can interact.");
            } else {
                message(viewer, "Offline edit rejected: " + result.detail());
            }
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.SEVERE, "Offline inventory patch preparation failed", exception);
            message(viewer, "Offline inventory edit failed before any player data changed.");
        }
    }

    private void applyPendingOnLogin(Player player, InventoryPatch original, int attempt) {
        submit(() -> {
            InventoryJournalStore loaded = store.get();
            if (loaded == null) {
                retryLoginApply(player, original, attempt, RECOVERY_STORAGE_UNAVAILABLE);
                return;
            }
            try {
                InventoryPatch claimed = loaded.claimForApply(
                        original.patchId(), original.operationId(), APPLY_LEASE, clock.instant()
                ).orElse(null);
                if (claimed == null) {
                    retryLoginApply(player, original, attempt, "Inventory recovery lease is busy.");
                    return;
                }
                if (claimed.state() == InventoryOperationState.APPLIED) {
                    loginBlocks.remove(player.getUniqueId());
                    return;
                }
                onEntity(player, () -> applyPendingOnPlayer(player, claimed, attempt));
            } catch (RuntimeException exception) {
                plugin.getLogger().log(Level.SEVERE, "Unable to claim a login inventory patch", exception);
                retryLoginApply(player, original, attempt, "Inventory recovery claim failed.");
            }
        });
    }

    private void applyPendingOnPlayer(Player player, InventoryPatch patch, int attempt) {
        InventoryImageCodec.EncodedImage current = codec.encodeWithChecksum(codec.capture(player));
        InventoryImageCodec.EncodedImage applied = applyPendingDecision(player, patch, current);
        if (applied != null) {
            submit(() -> finalizePendingOnLogin(player, patch, attempt, applied));
        }
    }

    private InventoryImageCodec.EncodedImage applyPendingDecision(
            Player player,
            InventoryPatch patch,
            InventoryImageCodec.EncodedImage current
    ) {
        if (isLiveCursorPatch(patch)) {
            keepRecoveryBlocked(
                    patch.playerId(), patch.actorId(), patch.operationId(),
                    "A live cursor transfer reached generic login recovery and was not applied target-only."
            );
            return null;
        }
        return switch (InventoryPatchDecision.decide(
                current.checksum(), patch.expectedChecksum(), patch.replacementChecksum()
        )) {
            case APPLY_REPLACEMENT -> {
                codec.apply(player, codec.decode(patch.replacementSnapshot()));
                yield codec.encodeWithChecksum(codec.capture(player));
            }
            case FINALIZE_ALREADY_APPLIED -> current;
            case QUARANTINE_CONFLICT -> {
                queuePendingConflict(player, patch, current);
                yield null;
            }
            default -> throw new IllegalStateException("Unsupported inventory patch decision");
        };
    }

    private void queuePendingConflict(
            Player player,
            InventoryPatch patch,
            InventoryImageCodec.EncodedImage conflicting
    ) {
        submit(() -> {
            quarantine(patch, "LOGIN_STATE_CONFLICT", "Loaded player data does not match before or replacement");
            loginBlocks.remove(player.getUniqueId());
            alertStaff("Inventory recovery was quarantined for " + player.getName() + " after a state conflict.");
            observeEncoded(player.getUniqueId(), conflicting);
        });
    }

    private void finalizePendingOnLogin(
            Player player,
            InventoryPatch patch,
            int attempt,
            InventoryImageCodec.EncodedImage applied
    ) {
        try {
            InventoryFinalizeResult result = store.get().finalizeApplied(
                    patch.patchId(), patch.operationId(), patch.fencingToken(),
                    applied.checksum(), applied.bytes(), clock.instant()
            );
            if (committed(result)) {
                loginBlocks.remove(player.getUniqueId());
                alertStaff("A queued inventory correction was applied and verified for " + player.getName() + '.');
                return;
            }
            retryLoginApply(player, patch, attempt, result.detail());
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.SEVERE, "Login inventory patch finalization failed", exception);
            retryLoginApply(player, patch, attempt, "Inventory recovery finalization failed.");
        }
    }

    private void retryLoginApply(Player player, InventoryPatch patch, int attempt, String detail) {
        if (!player.isOnline()) {
            return;
        }
        if (attempt >= MAX_LOGIN_APPLY_ATTEMPTS) {
            loginBlocks.add(player.getUniqueId());
            message(player, "Inventory recovery is still pending; reconnect or contact staff.");
            alertStaff("Inventory recovery remains blocked for " + player.getName() + ": " + detail);
            return;
        }
        player.getScheduler().runDelayed(
                plugin,
                ignored -> applyPendingOnLogin(player, patch, attempt + 1),
                () -> {
                },
                20L
        );
    }

    private void observe(Player player) {
        InventoryImageCodec.EncodedImage encoded = codec.encodeWithChecksum(codec.capture(player));
        observeEncoded(player.getUniqueId(), encoded);
    }

    private void observeEncoded(UUID playerId, InventoryImageCodec.EncodedImage encoded) {
        submit(() -> {
            InventoryJournalStore loaded = store.get();
            if (loaded == null) {
                return;
            }
            try {
                loaded.recordObservation(
                        playerId, scopeId, serverId, encoded.checksum(), encoded.bytes(), clock.instant()
                );
            } catch (RuntimeException exception) {
                plugin.getLogger().log(Level.SEVERE, "Unable to record an inventory observation", exception);
            }
        });
    }

    private void scheduleTargetRefresh(Player target, ModerationInventoryHolder.Kind kind) {
        LiveSession session = liveSessions.get(target.getUniqueId());
        if (session == null || !session.hasViewerKind(kind)) {
            return;
        }
        target.getScheduler().runDelayed(
                plugin,
                ignored -> reconcile(session),
                () -> closeTargetViews(target.getUniqueId(), "The target is no longer on this backend."),
                1L
        );
    }

    private void reconcileViewedTargets() {
        for (LiveSession session : liveSessions.values()) {
            reconcile(session);
        }
    }

    private void reconcile(LiveSession session) {
        if (!session.beginReconcile()) {
            return;
        }
        Player target = plugin.getServer().getPlayer(session.targetId());
        if (target == null) {
            finishSessionWork(session);
            closeTargetViews(session.targetId(), "The target is no longer on this backend.");
            return;
        }
        target.getScheduler().execute(
                plugin,
                () -> captureReconciliation(target, session),
                () -> finishSessionWork(session),
                1L
        );
    }

    private void captureReconciliation(Player target, LiveSession session) {
        InventoryImage image = codec.capture(target);
        InventoryImageCodec.EncodedImage encoded = codec.encodeWithChecksum(image);
        if (encoded.checksum().equals(session.observation().checksum())) {
            finishSessionWork(session);
            return;
        }
        EnumSet<ModerationInventoryHolder.Kind> changedKinds = changedKinds(session.image(), image);
        if (!submit(() -> recordReconciliation(target, session, image, encoded, changedKinds))) {
            finishSessionWork(session);
        }
    }

    private void recordReconciliation(
            Player target,
            LiveSession session,
            InventoryImage image,
            InventoryImageCodec.EncodedImage encoded,
            EnumSet<ModerationInventoryHolder.Kind> changedKinds
    ) {
        try {
            InventoryJournalStore loaded = store.get();
            if (loaded != null) {
                InventoryObservation observation = loaded.recordObservation(
                        target.getUniqueId(), scopeId, serverId,
                        encoded.checksum(), encoded.bytes(), clock.instant()
                );
                session.observed(observation, image);
                renderSession(session, changedKinds);
            }
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.SEVERE, "Live inventory reconciliation failed", exception);
        } finally {
            finishSessionWork(session);
        }
    }

    private static EnumSet<ModerationInventoryHolder.Kind> changedKinds(
            InventoryImage previous,
            InventoryImage replacement
    ) {
        return kindsForChangedSlots(previous.changedSlots(replacement));
    }

    static EnumSet<ModerationInventoryHolder.Kind> kindsForChangedSlots(List<Integer> changedSlots) {
        EnumSet<ModerationInventoryHolder.Kind> kinds = EnumSet.noneOf(ModerationInventoryHolder.Kind.class);
        for (int slot : changedSlots) {
            kinds.add(slot >= InventoryImage.ENDER_OFFSET
                    ? ModerationInventoryHolder.Kind.ENDER_CHEST
                    : ModerationInventoryHolder.Kind.PLAYER);
        }
        return kinds;
    }

    private void renderSession(LiveSession session, Set<ModerationInventoryHolder.Kind> kinds) {
        if (kinds.isEmpty()) {
            return;
        }
        InventoryImage image = session.image();
        for (ModerationInventoryHolder holder : session.viewers(kinds)) {
            Player viewer = plugin.getServer().getPlayer(holder.viewerId());
            if (viewer != null) {
                onEntity(viewer, () -> render(holder, image));
            }
        }
    }

    private void renderHolderFromSession(ModerationInventoryHolder holder, LiveSession session) {
        if (holder == null) {
            return;
        }
        Player viewer = plugin.getServer().getPlayer(holder.viewerId());
        if (viewer != null) {
            onEntity(viewer, () -> render(holder, session.image()));
        }
    }

    private static ModerationInventoryHolder findHolder(LiveSession session, UUID viewerId) {
        for (ModerationInventoryHolder holder : session.viewers()) {
            if (holder.viewerId().equals(viewerId)) {
                return holder;
            }
        }
        return null;
    }

    private void render(ModerationInventoryHolder holder, InventoryImage image) {
        Inventory inventory = holder.getInventory();
        for (int guiSlot = 0; guiSlot < inventory.getSize(); guiSlot++) {
            int logical = holder.logicalSlot(guiSlot);
            if (logical < 0) {
                inventory.clear(guiSlot);
            } else {
                inventory.setItem(guiSlot, image.item(logical));
            }
        }
        holder.image(image, false);
    }

    private static Optional<LiveInventoryTransferDecision.Click> supportedClick(ClickType click) {
        return switch (click) {
            case LEFT -> Optional.of(LiveInventoryTransferDecision.Click.LEFT);
            case RIGHT -> Optional.of(LiveInventoryTransferDecision.Click.RIGHT);
            default -> Optional.empty();
        };
    }

    private static boolean safeLowerInventoryAction(InventoryAction action) {
        return switch (action) {
            case NOTHING,
                    PICKUP_ALL,
                    PICKUP_HALF,
                    PICKUP_ONE,
                    PICKUP_SOME,
                    PLACE_ALL,
                    PLACE_ONE,
                    PLACE_SOME,
                    SWAP_WITH_CURSOR,
                    DROP_ALL_SLOT,
                    DROP_ONE_SLOT -> true;
            default -> false;
        };
    }

    private static ItemStack offlineReplacement(
            ItemStack current,
            ItemStack cursor,
            boolean leftClick,
            boolean rightClick
    ) {
        ItemStack template = usable(cursor) ? cursor.clone() : null;
        if (leftClick) {
            return template;
        }
        if (!rightClick) {
            return EditRejected.ITEM;
        }
        return offlineRightClickReplacement(current, template);
    }

    private static ItemStack offlineRightClickReplacement(ItemStack current, ItemStack template) {
        if (template == null) {
            return removeOne(current);
        }
        if (!usable(current) || !current.isSimilar(template)) {
            template.setAmount(1);
            return template;
        }
        ItemStack increased = current.clone();
        increased.setAmount(Math.min(increased.getMaxStackSize(), increased.getAmount() + 1));
        return increased;
    }

    private static ItemStack removeOne(ItemStack current) {
        if (!usable(current) || current.getAmount() == 1) {
            return null;
        }
        ItemStack reduced = current.clone();
        reduced.setAmount(reduced.getAmount() - 1);
        return reduced;
    }

    private static boolean usable(ItemStack item) {
        return item != null && !item.isEmpty() && item.getType() != Material.AIR;
    }

    private static boolean isLiveCursorPatch(InventoryPatch patch) {
        return patch.operationType().startsWith(LIVE_CURSOR_PREFIX);
    }

    private void quarantine(InventoryPatch patch, String reasonCode, String detail) {
        InventoryJournalStore loaded = store.get();
        if (loaded != null) {
            loaded.quarantine(
                    patch.patchId(), patch.operationId(), patch.fencingToken(),
                    reasonCode, detail, clock.instant()
            );
        }
    }

    private void closeTargetViews(UUID targetId, String reason) {
        LiveSession session = liveSessions.remove(targetId);
        if (session == null) {
            return;
        }
        for (ModerationInventoryHolder holder : session.viewers()) {
            Player viewer = plugin.getServer().getPlayer(holder.viewerId());
            if (viewer != null) {
                onEntity(viewer, () -> {
                    if (viewer.getOpenInventory().getTopInventory().getHolder(false) == holder) {
                        viewer.closeInventory();
                        viewer.sendMessage(StaffMessageStyle.style(Component.text(reason)));
                    }
                });
            }
        }
    }

    private boolean interactionRestricted(UUID playerId) {
        return restricted(playerId) || viewerTransfers.containsKey(playerId);
    }

    private boolean restricted(UUID playerId) {
        return assetLocks.contains(playerId) || loginBlocks.contains(playerId);
    }

    private boolean submit(Runnable operation) {
        try {
            workers.execute(operation);
            return true;
        } catch (RejectedExecutionException exception) {
            plugin.getLogger().warning("Inventory operation skipped because the bounded worker queue is full");
            return false;
        }
    }

    private void onEntity(Player player, Runnable operation) {
        if (player != null) {
            player.getScheduler().execute(plugin, operation, null, 1L);
        }
    }

    private void message(Player player, String body) {
        if (player != null) {
            onEntity(player, () -> player.sendMessage(StaffMessageStyle.style(Component.text(body))));
        }
    }

    private void alertStaff(String body) {
        plugin.getServer().getGlobalRegionScheduler().execute(plugin, () ->
                plugin.getServer().getOnlinePlayers().stream()
                        .filter(player -> player.hasPermission("enthusiastaff.alerts"))
                        .forEach(player -> player.sendMessage(StaffMessageStyle.style(Component.text(body)))));
    }

    @Override
    public void close() {
        reconciliationTask.cancel();
        liveSessions.clear();
        viewerTransfers.clear();
        preloadedPatches.clear();
        cursorRecoveries.clear();
        recoveryAttempts.clear();
        recoveryInFlight.clear();
        assetLocks.clear();
        loginBlocks.clear();
    }

    static final class LiveSession {
        private final UUID targetId;
        private final Map<UUID, ModerationInventoryHolder> viewers = new ConcurrentHashMap<>();
        private final LiveInventorySessionGate gate = new LiveInventorySessionGate();
        private volatile InventoryObservation observation;
        private volatile InventoryImage image;

        LiveSession(UUID targetId) {
            this.targetId = targetId;
        }

        UUID targetId() {
            return targetId;
        }

        void observed(InventoryObservation nextObservation, InventoryImage nextImage) {
            observation = java.util.Objects.requireNonNull(nextObservation);
            image = java.util.Objects.requireNonNull(nextImage);
        }

        void imageOnly(InventoryImage nextImage) {
            image = java.util.Objects.requireNonNull(nextImage);
        }

        InventoryObservation observation() {
            return java.util.Objects.requireNonNull(observation, "observation");
        }

        InventoryImage image() {
            return java.util.Objects.requireNonNull(image, "image");
        }

        void addViewer(ModerationInventoryHolder holder) {
            viewers.put(holder.viewerId(), holder);
        }

        void removeViewer(UUID viewerId) {
            viewers.remove(viewerId);
        }

        List<ModerationInventoryHolder> viewers() {
            return List.copyOf(viewers.values());
        }

        List<ModerationInventoryHolder> viewers(Set<ModerationInventoryHolder.Kind> kinds) {
            return viewers.values().stream()
                    .filter(holder -> kinds.contains(holder.kind()))
                    .toList();
        }

        boolean hasViewerKind(ModerationInventoryHolder.Kind kind) {
            return viewers.values().stream().anyMatch(holder -> holder.kind() == kind);
        }

        boolean beginEdit(LiveInventoryTransferExecution transfer) {
            return !viewers.isEmpty() && gate.beginEdit(transfer);
        }

        boolean beginReconcile() {
            return !viewers.isEmpty() && gate.beginWork();
        }

        void finishTransfer(LiveInventoryTransferExecution transfer) {
            gate.finishTransfer(transfer);
        }

        void finishWork() {
            gate.finishWork();
        }

        LiveInventoryTransferExecution activeTransfer() {
            return gate.activeTransfer();
        }

        boolean working() {
            return gate.working();
        }

        boolean removable() {
            return viewers.isEmpty() && !gate.working();
        }
    }

    private static final class EditRejected {
        private static final ItemStack ITEM = ItemStack.of(Material.BARRIER);

        private EditRejected() {
        }
    }
}
