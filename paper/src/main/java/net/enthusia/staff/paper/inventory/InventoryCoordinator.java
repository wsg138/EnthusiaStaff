package net.enthusia.staff.paper.inventory;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import java.util.logging.Level;
import net.enthusia.staff.common.IdempotencyKey;
import net.enthusia.staff.domain.OperationalMode;
import net.enthusia.staff.domain.inventory.InventoryFinalizeResult;
import net.enthusia.staff.domain.inventory.InventoryObservation;
import net.enthusia.staff.domain.inventory.InventoryPatch;
import net.enthusia.staff.domain.inventory.InventoryPatchDecision;
import net.enthusia.staff.domain.inventory.InventoryPreparation;
import net.enthusia.staff.domain.inventory.InventoryPrepareRequest;
import net.enthusia.staff.domain.player.PlayerIdentity;
import net.enthusia.staff.domain.player.PlayerPresence;
import net.enthusia.staff.domain.ports.InventoryJournalStore;
import net.enthusia.staff.domain.ports.PlayerDirectory;
import net.enthusia.staff.paper.api.InventoryLockService;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
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
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

public final class InventoryCoordinator implements Listener, InventoryLockService, AutoCloseable {
    private static final Duration APPLY_LEASE = Duration.ofSeconds(30);
    private static final int MAX_LOGIN_APPLY_ATTEMPTS = 5;

    private final JavaPlugin plugin;
    private final Clock clock;
    private final String scopeId;
    private final String serverId;
    private final Supplier<OperationalMode> mode;
    private final Supplier<InventoryJournalStore> store;
    private final Supplier<PlayerDirectory> directory;
    private final ExecutorService workers;
    private final InventoryImageCodec codec = new InventoryImageCodec();
    private final Map<UUID, LiveSession> liveSessions = new ConcurrentHashMap<>();
    private final Map<UUID, InventoryPatch> preloadedPatches = new ConcurrentHashMap<>();
    private final java.util.Set<UUID> assetLocks = ConcurrentHashMap.newKeySet();
    private final java.util.Set<UUID> loginBlocks = ConcurrentHashMap.newKeySet();
    private final ScheduledTask reconciliationTask;

    public InventoryCoordinator(
            JavaPlugin plugin,
            Clock clock,
            String scopeId,
            String serverId,
            Supplier<OperationalMode> mode,
            Supplier<InventoryJournalStore> store,
            Supplier<PlayerDirectory> directory,
            ExecutorService workers
    ) {
        this.plugin = java.util.Objects.requireNonNull(plugin, "plugin");
        this.clock = java.util.Objects.requireNonNull(clock, "clock");
        this.scopeId = requireIdentifier(scopeId, "scopeId");
        this.serverId = requireIdentifier(serverId, "serverId");
        this.mode = java.util.Objects.requireNonNull(mode, "mode");
        this.store = java.util.Objects.requireNonNull(store, "store");
        this.directory = java.util.Objects.requireNonNull(directory, "directory");
        this.workers = java.util.Objects.requireNonNull(workers, "workers");
        this.reconciliationTask = plugin.getServer().getGlobalRegionScheduler().runAtFixedRate(
                plugin,
                ignored -> reconcileViewedTargets(),
                5L,
                5L
        );
    }

    public void open(Player viewer, PlayerIdentity target, boolean enderChest) {
        if (viewer == null || target == null) {
            throw new IllegalArgumentException("viewer and target must be present");
        }
        if (mode.get() != OperationalMode.ACTIVE) {
            viewer.sendMessage(Component.text("Inventory editing is available only while moderation is ACTIVE."));
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
            if (mode.get() == OperationalMode.ACTIVE) {
                event.disallow(
                        AsyncPlayerPreLoginEvent.Result.KICK_OTHER,
                        Component.text("Inventory safety verification is temporarily unavailable. Please retry.")
                );
            }
            return;
        }
        try {
            loaded.cancelAbandonedConfiscations(
                    event.getUniqueId(),
                    scopeId,
                    serverId,
                    clock.instant()
            );
            List<InventoryPatch> patches = loaded.pending(event.getUniqueId(), scopeId, serverId, 2);
            if (patches.size() > 1) {
                loginBlocks.add(event.getUniqueId());
                event.disallow(
                        AsyncPlayerPreLoginEvent.Result.KICK_OTHER,
                        Component.text("Multiple inventory recovery operations require staff review.")
                );
                return;
            }
            if (!patches.isEmpty()) {
                preloadedPatches.put(event.getUniqueId(), patches.getFirst());
                loginBlocks.add(event.getUniqueId());
            }
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.SEVERE, "Inventory pre-login recovery lookup failed", exception);
            if (mode.get() == OperationalMode.ACTIVE) {
                event.disallow(
                        AsyncPlayerPreLoginEvent.Result.KICK_OTHER,
                        Component.text("Inventory safety verification failed. Please retry.")
                );
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        InventoryPatch patch = preloadedPatches.remove(player.getUniqueId());
        if (patch == null) {
            loginBlocks.remove(player.getUniqueId());
            observe(player);
            return;
        }
        applyPendingOnLogin(player, patch, 1);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        closeTargetViews(player.getUniqueId(), "The target left this backend; any durable edit remains queued.");
        if (!isLocked(player.getUniqueId())) {
            observe(player);
        }
        assetLocks.remove(player.getUniqueId());
        loginBlocks.remove(player.getUniqueId());
        preloadedPatches.remove(player.getUniqueId());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player viewer)) {
            return;
        }
        if (restricted(viewer.getUniqueId())) {
            event.setCancelled(true);
            return;
        }
        if (!(event.getView().getTopInventory().getHolder(false) instanceof ModerationInventoryHolder holder)) {
            return;
        }
        event.setCancelled(true);
        if (!holder.viewerId().equals(viewer.getUniqueId()) || event.getRawSlot() < 0
                || event.getRawSlot() >= event.getView().getTopInventory().getSize()) {
            return;
        }
        if (!viewer.hasPermission("enthusiastaff.inventory.edit")) {
            viewer.sendMessage(Component.text("You may inspect this inventory but not edit it."));
            return;
        }
        int logicalSlot = holder.logicalSlot(event.getRawSlot());
        if (logicalSlot < 0) {
            return;
        }
        if (event.isShiftClick()) {
            viewer.sendMessage(Component.text("Shift-click selection is reserved for the confiscation workflow."));
            return;
        }
        ItemStack replacement = replacement(
                holder.image().item(logicalSlot),
                event.getCursor(),
                event.isLeftClick(),
                event.isRightClick()
        );
        if (replacement == EditRejected.ITEM) {
            viewer.sendMessage(Component.text(
                    "Use left click to replace/remove a stack or right click to add/remove one item."
            ));
            return;
        }
        InventoryImage next = holder.image().withItem(logicalSlot, replacement);
        if (holder.offline()) {
            holder.image(next, true);
            render(holder);
        } else {
            editLive(viewer, holder, next, logicalSlot);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player viewer)) {
            return;
        }
        if (restricted(viewer.getUniqueId())
                || event.getView().getTopInventory().getHolder(false) instanceof ModerationInventoryHolder) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player viewer)
                || !(event.getInventory().getHolder(false) instanceof ModerationInventoryHolder holder)
                || !holder.closeOnce()) {
            return;
        }
        if (holder.offline()) {
            queueOfflineEdit(viewer, holder);
            return;
        }
        LiveSession session = liveSessions.get(holder.targetId());
        if (session != null) {
            session.removeViewer(holder.viewerId());
            if (session.removable()) {
                liveSessions.remove(holder.targetId(), session);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onLockedOpen(InventoryOpenEvent event) {
        if (event.getPlayer() instanceof Player player && restricted(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onLockedDrop(PlayerDropItemEvent event) {
        if (restricted(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onLockedPickup(PlayerAttemptPickupItemEvent event) {
        if (restricted(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onLockedEntityPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player && restricted(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onLockedSwap(PlayerSwapHandItemsEvent event) {
        if (restricted(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onLockedHeldSlot(PlayerItemHeldEvent event) {
        if (restricted(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onLockedInteract(PlayerInteractEvent event) {
        if (restricted(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    private void openLive(
            Player viewer,
            PlayerIdentity target,
            Player online,
            ModerationInventoryHolder.Kind kind
    ) {
        online.getScheduler().execute(plugin, () -> {
            InventoryImage image = codec.capture(online);
            InventoryImageCodec.EncodedImage encoded = codec.encodeWithChecksum(image);
            submit(() -> {
                InventoryJournalStore loaded = store.get();
                if (loaded == null) {
                    message(viewer, "Inventory storage is not ready; the view was not opened.");
                    return;
                }
                try {
                    InventoryObservation observation = loaded.recordObservation(
                            target.playerId(), scopeId, serverId, encoded.checksum(), encoded.bytes(), clock.instant()
                    );
                    LiveSession session = liveSessions.compute(target.playerId(), (ignored, existing) -> {
                        LiveSession selected = existing == null ? new LiveSession(target.playerId()) : existing;
                        selected.observed(observation, image);
                        return selected;
                    });
                    openView(viewer, target, kind, false, observation, image, session);
                } catch (RuntimeException exception) {
                    plugin.getLogger().log(Level.SEVERE, "Unable to prepare a live inventory view", exception);
                    message(viewer, "The live inventory could not be journaled; no view was opened.");
                }
            });
        }, () -> openOffline(viewer, target, kind), 1L);
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
                InventoryImage image = codec.decode(observation.snapshot());
                openView(viewer, target, kind, true, observation, image, null);
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
            render(holder);
            viewer.openInventory(inventory);
            viewer.sendMessage(Component.text(
                    "Editor: held cursor + left click replaces; empty cursor + left click removes; right click adjusts one."
            ));
        });
    }

    private void editLive(
            Player viewer,
            ModerationInventoryHolder holder,
            InventoryImage replacement,
            int changedSlot
    ) {
        LiveSession session = liveSessions.get(holder.targetId());
        if (session == null || !session.beginEdit()) {
            viewer.sendMessage(Component.text("That inventory is synchronizing; retry the edit."));
            return;
        }
        Player target = plugin.getServer().getPlayer(holder.targetId());
        if (target == null) {
            session.finishWork();
            viewer.sendMessage(Component.text("The target left; reopen the offline view after its snapshot is recorded."));
            return;
        }
        InventoryObservation before = session.observation();
        InventoryImage beforeImage = session.image();
        InventoryImageCodec.EncodedImage replacementBytes = codec.encodeWithChecksum(replacement);
        UUID operationId = UUID.randomUUID();
        InventoryPrepareRequest request = new InventoryPrepareRequest(
                operationId,
                new IdempotencyKey("inventory:live:" + operationId).value(),
                holder.targetId(),
                scopeId,
                serverId,
                viewer.getUniqueId(),
                Optional.empty(),
                "ONLINE_EDIT",
                before.revision(),
                before.checksum(),
                before.snapshot(),
                replacementBytes.checksum(),
                replacementBytes.bytes(),
                List.of(changedSlot),
                false
        );
        if (!assetLocks.add(holder.targetId())) {
            session.finishWork();
            viewer.sendMessage(Component.text("Another asset operation already owns this player."));
            return;
        }
        if (!submit(() -> prepareAndApplyLive(viewer, target, session, request, replacement))) {
            assetLocks.remove(holder.targetId());
            session.finishWork();
        }
    }

    private void prepareAndApplyLive(
            Player viewer,
            Player target,
            LiveSession session,
            InventoryPrepareRequest request,
            InventoryImage replacement
    ) {
        InventoryJournalStore loaded = store.get();
        if (loaded == null) {
            finishLiveFailure(viewer, session, request.playerId(), "Inventory storage became unavailable.");
            return;
        }
        try {
            InventoryPreparation preparation = loaded.prepare(request, APPLY_LEASE, clock.instant());
            if (preparation.patch().isEmpty()) {
                finishLiveFailure(viewer, session, request.playerId(), preparation.detail());
                reconcile(session);
                return;
            }
            InventoryPatch patch = loaded.claimForApply(
                    preparation.patch().orElseThrow().patchId(),
                    request.operationId(),
                    APPLY_LEASE,
                    clock.instant()
            ).orElse(null);
            if (patch == null) {
                finishLiveFailure(viewer, session, request.playerId(), "The prepared inventory lease could not be claimed.");
                return;
            }
            target.getScheduler().execute(
                    plugin,
                    () -> applyLiveOnTarget(viewer, target, session, patch, replacement),
                    () -> {
                        assetLocks.remove(target.getUniqueId());
                        session.finishWork();
                        message(viewer, "The target left; the durable patch will apply before their next interaction.");
                    },
                    1L
            );
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.SEVERE, "Live inventory operation preparation failed", exception);
            finishLiveFailure(viewer, session, request.playerId(), "The inventory edit failed before target state changed.");
        }
    }

    private void applyLiveOnTarget(
            Player viewer,
            Player target,
            LiveSession session,
            InventoryPatch patch,
            InventoryImage replacement
    ) {
        InventoryImage current = codec.capture(target);
        InventoryImageCodec.EncodedImage currentBytes = codec.encodeWithChecksum(current);
        if (!currentBytes.checksum().equals(patch.expectedChecksum())) {
            submit(() -> {
                quarantine(patch, "LIVE_STATE_CHANGED", "Target inventory changed after the edit was prepared");
                finishLiveFailure(viewer, session, target.getUniqueId(), "The target inventory changed; the edit was rejected.");
                reconcile(session);
            });
            return;
        }
        try {
            codec.apply(target, replacement);
            InventoryImage applied = codec.capture(target);
            InventoryImageCodec.EncodedImage appliedBytes = codec.encodeWithChecksum(applied);
            submit(() -> finalizeLive(viewer, session, patch, applied, appliedBytes));
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.SEVERE, "Live inventory application failed", exception);
            submit(() -> {
                quarantine(patch, "BUKKIT_APPLY_FAILED", "Paper inventory application failed");
                finishLiveFailure(viewer, session, target.getUniqueId(), "The target edit failed and requires review.");
            });
        }
    }

    private void finalizeLive(
            Player viewer,
            LiveSession session,
            InventoryPatch patch,
            InventoryImage applied,
            InventoryImageCodec.EncodedImage appliedBytes
    ) {
        try {
            InventoryFinalizeResult result = store.get().finalizeApplied(
                    patch.patchId(),
                    patch.operationId(),
                    patch.fencingToken(),
                    appliedBytes.checksum(),
                    appliedBytes.bytes(),
                    clock.instant()
            );
            if (result.status() == InventoryFinalizeResult.Status.COMMITTED
                    || result.status() == InventoryFinalizeResult.Status.REPLAYED) {
                InventoryObservation next = new InventoryObservation(
                        patch.profileId(),
                        patch.playerId(),
                        patch.scopeId(),
                        patch.owningServerId(),
                        result.resultingRevision(),
                        appliedBytes.checksum(),
                        appliedBytes.bytes(),
                        clock.instant()
                );
                session.observed(next, applied);
                renderSession(session);
                message(viewer, "Inventory edit committed at revision " + result.resultingRevision() + '.');
            } else {
                message(viewer, "Inventory state changed ambiguously; the operation is quarantined for review.");
            }
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.SEVERE,
                    "Inventory changed but durable finalization failed; login recovery will verify it", exception);
            message(viewer, "The edit reached the target but final verification is pending; do not repeat it.");
        } finally {
            assetLocks.remove(patch.playerId());
            session.finishWork();
        }
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
                true
        );
        submit(() -> {
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
        });
    }

    private void applyPendingOnLogin(Player player, InventoryPatch original, int attempt) {
        submit(() -> {
            InventoryJournalStore loaded = store.get();
            if (loaded == null) {
                retryLoginApply(player, original, attempt, "Inventory recovery storage is unavailable.");
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
                onEntity(player, () -> applyPendingOnPlayer(player, claimed, attempt));
            } catch (RuntimeException exception) {
                plugin.getLogger().log(Level.SEVERE, "Unable to claim a login inventory patch", exception);
                retryLoginApply(player, original, attempt, "Inventory recovery claim failed.");
            }
        });
    }

    private void applyPendingOnPlayer(Player player, InventoryPatch patch, int attempt) {
        InventoryImage current = codec.capture(player);
        InventoryImageCodec.EncodedImage currentBytes = codec.encodeWithChecksum(current);
        switch (InventoryPatchDecision.decide(
                currentBytes.checksum(),
                patch.expectedChecksum(),
                patch.replacementChecksum()
        )) {
            case APPLY_REPLACEMENT -> {
                codec.apply(player, codec.decode(patch.replacementSnapshot()));
                current = codec.capture(player);
                currentBytes = codec.encodeWithChecksum(current);
            }
            case QUARANTINE_CONFLICT -> {
                InventoryImageCodec.EncodedImage conflicting = currentBytes;
                submit(() -> {
                    quarantine(patch, "LOGIN_STATE_CONFLICT", "Loaded player data does not match before or replacement");
                    loginBlocks.remove(player.getUniqueId());
                    alertStaff("Inventory recovery was quarantined for " + player.getName() + " after a state conflict.");
                    observeEncoded(player.getUniqueId(), conflicting);
                });
                return;
            }
            case FINALIZE_ALREADY_APPLIED -> {
                // A previous attempt reached Paper but crashed before the durable commit.
            }
        }
        InventoryImage applied = current;
        InventoryImageCodec.EncodedImage appliedBytes = currentBytes;
        submit(() -> {
            try {
                InventoryFinalizeResult result = store.get().finalizeApplied(
                        patch.patchId(),
                        patch.operationId(),
                        patch.fencingToken(),
                        appliedBytes.checksum(),
                        appliedBytes.bytes(),
                        clock.instant()
                );
                if (result.status() == InventoryFinalizeResult.Status.COMMITTED
                        || result.status() == InventoryFinalizeResult.Status.REPLAYED) {
                    loginBlocks.remove(player.getUniqueId());
                    alertStaff("A queued inventory correction was applied and verified for " + player.getName() + '.');
                    return;
                }
                retryLoginApply(player, patch, attempt, result.detail());
            } catch (RuntimeException exception) {
                plugin.getLogger().log(Level.SEVERE, "Login inventory patch finalization failed", exception);
                retryLoginApply(player, patch, attempt, "Inventory recovery finalization failed.");
            }
        });
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
            session.finishWork();
            closeTargetViews(session.targetId(), "The target is no longer on this backend.");
            return;
        }
        target.getScheduler().execute(plugin, () -> {
            InventoryImage image = codec.capture(target);
            InventoryImageCodec.EncodedImage encoded = codec.encodeWithChecksum(image);
            if (encoded.checksum().equals(session.observation().checksum())) {
                session.finishWork();
                return;
            }
            submit(() -> {
                try {
                    InventoryJournalStore loaded = store.get();
                    if (loaded != null) {
                        InventoryObservation observation = loaded.recordObservation(
                                target.getUniqueId(),
                                scopeId,
                                serverId,
                                encoded.checksum(),
                                encoded.bytes(),
                                clock.instant()
                        );
                        session.observed(observation, image);
                        renderSession(session);
                    }
                } catch (RuntimeException exception) {
                    plugin.getLogger().log(Level.SEVERE, "Live inventory reconciliation failed", exception);
                } finally {
                    session.finishWork();
                }
            });
        }, session::finishWork, 1L);
    }

    private void renderSession(LiveSession session) {
        for (ModerationInventoryHolder holder : session.viewers()) {
            holder.image(session.image(), false);
            Player viewer = plugin.getServer().getPlayer(holder.viewerId());
            if (viewer != null) {
                onEntity(viewer, () -> render(holder));
            }
        }
    }

    private void render(ModerationInventoryHolder holder) {
        Inventory inventory = holder.getInventory();
        InventoryImage image = holder.image();
        for (int guiSlot = 0; guiSlot < inventory.getSize(); guiSlot++) {
            int logical = holder.logicalSlot(guiSlot);
            inventory.setItem(guiSlot, logical < 0 ? filler() : image.item(logical));
        }
    }

    private static ItemStack replacement(
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
        if (template == null) {
            if (!usable(current) || current.getAmount() == 1) {
                return null;
            }
            ItemStack reduced = current.clone();
            reduced.setAmount(reduced.getAmount() - 1);
            return reduced;
        }
        if (!usable(current) || !current.isSimilar(template)) {
            template.setAmount(1);
            return template;
        }
        ItemStack increased = current.clone();
        increased.setAmount(Math.min(increased.getMaxStackSize(), increased.getAmount() + 1));
        return increased;
    }

    private static boolean usable(ItemStack item) {
        return item != null && !item.isEmpty() && item.getType() != Material.AIR;
    }

    private static ItemStack filler() {
        ItemStack filler = ItemStack.of(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = filler.getItemMeta();
        meta.displayName(Component.text("Inventory metadata slot"));
        filler.setItemMeta(meta);
        return filler;
    }

    private void quarantine(InventoryPatch patch, String reasonCode, String detail) {
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
    }

    private void finishLiveFailure(Player viewer, LiveSession session, UUID targetId, String detail) {
        assetLocks.remove(targetId);
        session.finishWork();
        message(viewer, detail);
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
                        viewer.sendMessage(Component.text(reason));
                    }
                });
            }
        }
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
        player.getScheduler().execute(plugin, operation, null, 1L);
    }

    private void message(Player player, String body) {
        onEntity(player, () -> player.sendMessage(Component.text(body)));
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

    @Override
    public void close() {
        reconciliationTask.cancel();
        liveSessions.clear();
        preloadedPatches.clear();
        assetLocks.clear();
        loginBlocks.clear();
    }

    private static final class LiveSession {
        private final UUID targetId;
        private final Map<UUID, ModerationInventoryHolder> viewers = new ConcurrentHashMap<>();
        private final AtomicBoolean working = new AtomicBoolean();
        private volatile InventoryObservation observation;
        private volatile InventoryImage image;

        private LiveSession(UUID targetId) {
            this.targetId = targetId;
        }

        UUID targetId() {
            return targetId;
        }

        void observed(InventoryObservation nextObservation, InventoryImage nextImage) {
            observation = java.util.Objects.requireNonNull(nextObservation);
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

        boolean beginEdit() {
            return !viewers.isEmpty() && working.compareAndSet(false, true);
        }

        boolean beginReconcile() {
            return !viewers.isEmpty() && working.compareAndSet(false, true);
        }

        void finishWork() {
            working.set(false);
        }

        boolean removable() {
            return viewers.isEmpty() && !working.get();
        }
    }

    private static final class EditRejected {
        private static final ItemStack ITEM = ItemStack.of(Material.BARRIER);

        private EditRejected() {
        }
    }
}
