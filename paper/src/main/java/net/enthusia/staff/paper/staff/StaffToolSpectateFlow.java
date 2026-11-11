package net.enthusia.staff.paper.staff;

import net.enthusia.staff.paper.presentation.StaffMessageStyle;
import java.util.UUID;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Predicate;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/** Scheduler-safe follow/spectate orchestration used by {@link StaffToolDispatcher}. */
final class StaffToolSpectateFlow {
    private static final String SPECTATE_EXEMPT_PERMISSION = "enthusiastaff.stafftools.spectate-exempt";

    private static final double FOLLOW_REFRESH_DISTANCE_SQUARED = 2.25;
    private static final int TARGET_CHECK_INTERVAL_TICKS = 20;
    private final Map<UUID, FollowSession> follows = new ConcurrentHashMap<>();
    private final Map<UUID, Object> followAttempts = new ConcurrentHashMap<>();
    private final Map<UUID, Player> ownedSpectators = new ConcurrentHashMap<>();
    private final Plugin plugin;
    private final Predicate<Player> actorAuthorized;
    private final Predicate<UUID> vanished;

    StaffToolSpectateFlow(
            Plugin plugin,
            Predicate<Player> actorAuthorized,
            Predicate<UUID> vanished
    ) {
        this.plugin = java.util.Objects.requireNonNull(plugin, "plugin");
        this.actorAuthorized = java.util.Objects.requireNonNull(actorAuthorized, "actorAuthorized");
        this.vanished = java.util.Objects.requireNonNull(vanished, "vanished");
    }

    void begin(Player actor, UUID targetId) {
        stop(actor);
        UUID actorId = actor.getUniqueId();
        Object attempt = new Object();
        followAttempts.put(actorId, attempt);
        onEntity(
                targetId,
                target -> inspectTarget(actorId, attempt, target),
                () -> endAttempt(actorId, attempt, "That player is no longer online.")
        );
    }

    private void inspectTarget(UUID actorId, Object attempt, Player target) {
        if (!currentAttempt(actorId, attempt)) {
            return;
        }
        if (!targetEligible(target)) {
            endAttempt(actorId, attempt, "That target is protected from staff follow/spectate tools.");
            return;
        }
        TargetSnapshot snapshot = new TargetSnapshot(
                target.getUniqueId(),
                target.getName(),
                target.getLocation().clone(),
                target
        );
        onEntity(actorId, actor -> followSnapshot(actor, attempt, snapshot),
                () -> followAttempts.remove(actorId, attempt));
    }

    private void followSnapshot(Player actor, Object attempt, TargetSnapshot target) {
        if (!currentAttempt(actor.getUniqueId(), attempt)) {
            return;
        }
        if (!canContinue(actor)) {
            followAttempts.remove(actor.getUniqueId(), attempt);
            return;
        }
        UUID actorId = actor.getUniqueId();
        onEntity(
                target.playerId(),
                liveTarget -> revalidateBeforeTeleport(actorId, attempt, target, liveTarget),
                () -> endAttempt(actorId, attempt, "Follow/Spectate was cancelled because that target is no longer online.")
        );
    }

    private void revalidateBeforeTeleport(UUID actorId, Object attempt, TargetSnapshot snapshot, Player liveTarget) {
        if (!currentAttempt(actorId, attempt)) {
            return;
        }
        if (connectionChanged(snapshot, liveTarget)) {
            endAttempt(actorId, attempt, "Follow/Spectate was cancelled because the target connection changed.");
            return;
        }
        if (!targetEligible(liveTarget)) {
            endAttempt(actorId, attempt, "Follow/Spectate was cancelled because that target became protected.");
            return;
        }
        TargetSnapshot current = snapshot.withLocation(liveTarget.getLocation().clone());
        onEntity(actorId, actor -> beginTeleport(actor, attempt, current),
                () -> followAttempts.remove(actorId, attempt));
    }

    private void beginTeleport(Player actor, Object attempt, TargetSnapshot target) {
        if (!currentAttempt(actor.getUniqueId(), attempt)) {
            return;
        }
        if (!canContinue(actor)) {
            followAttempts.remove(actor.getUniqueId(), attempt);
            return;
        }
        UUID actorId = actor.getUniqueId();
        try {
            actor.teleportAsync(target.location()).whenComplete(
                    (success, failure) -> finishTeleport(actorId, attempt, target, success, failure)
            );
        } catch (RuntimeException exception) {
            endAttempt(actorId, attempt, "Follow/Spectate teleport failed safely.");
        }
    }

    private void finishTeleport(UUID actorId, Object attempt, TargetSnapshot target,
                                Boolean success, Throwable failure) {
        if (!currentAttempt(actorId, attempt)) {
            return;
        }
        if (failure != null || !Boolean.TRUE.equals(success)) {
            endAttempt(actorId, attempt, "Follow/Spectate teleport failed safely.");
            return;
        }
        onEntity(actorId, actor -> prepareAttachment(actor, attempt, target),
                () -> followAttempts.remove(actorId, attempt));
    }

    private void prepareAttachment(Player actor, Object attempt, TargetSnapshot target) {
        if (!currentAttempt(actor.getUniqueId(), attempt)) {
            return;
        }
        if (!canContinue(actor)) {
            followAttempts.remove(actor.getUniqueId(), attempt);
            return;
        }
        if (actor.getGameMode() != GameMode.SPECTATOR) {
            beginContinuousFollow(actor, attempt, target);
            return;
        }
        UUID actorId = actor.getUniqueId();
        onEntity(
                target.playerId(),
                liveTarget -> revalidateBeforeAttachment(actorId, attempt, target, liveTarget),
                () -> endAttempt(actorId, attempt, "Teleported to the last safe target location; direct spectating is unavailable.")
        );
    }

    /** Non-spectator Staff Mode retains its configured game mode while following. */
    private void beginContinuousFollow(Player actor, Object attempt, TargetSnapshot target) {
        if (!currentAttempt(actor.getUniqueId(), attempt)) {
            return;
        }
        FollowSession session = new FollowSession(attempt, target);
        follows.put(actor.getUniqueId(), session);
        try {
            actor.getScheduler().runAtFixedRate(plugin,
                    task -> followTick(actor, session, task::cancel),
                    () -> stopFollow(actor.getUniqueId(), session, "Follow ended: staff player disconnected."), 5L, 5L);
            actor.sendMessage(StaffMessageStyle.style(Component.text(
                    "Following " + target.name() + ". Right-click air with the Follow tool to stop.",
                    NamedTextColor.GREEN)));
        } catch (RuntimeException exception) {
            follows.remove(actor.getUniqueId(), session);
            followAttempts.remove(actor.getUniqueId(), attempt);
            actor.sendMessage(StaffMessageStyle.style(Component.text(
                    "Unable to keep following this player; no continuous follow was started.",
                    NamedTextColor.RED)));
        }
    }

    boolean stop(Player actor) {
        UUID actorId = actor.getUniqueId();
        boolean pendingOrAttached = followAttempts.remove(actorId) != null;
        boolean following = follows.remove(actorId) != null;
        Player attachment = ownedSpectators.remove(actorId);
        if (attachment != null) {
            try {
                if (sameConnection(attachment, actor.getSpectatorTarget())) {
                    detach(actor);
                }
            } catch (IllegalArgumentException | IllegalStateException ignored) {
                // A disconnecting actor may already have retired the attachment.
                // The logical attempt and follow session have still been invalidated.
            }
        }
        return pendingOrAttached || following;
    }

    private boolean currentAttempt(UUID actorId, Object attempt) {
        // Attempt tokens use Object's identity-based equals implementation.
        return attempt.equals(followAttempts.get(actorId));
    }

    private void endAttempt(UUID actorId, Object attempt, String reason) {
        if (followAttempts.remove(actorId, attempt)) {
            message(actorId, reason);
        }
    }

    private void followTick(Player actor, FollowSession session, Runnable cancel) {
        if (follows.get(actor.getUniqueId()) != session
                || !currentAttempt(actor.getUniqueId(), session.attempt)) {
            cancel.run();
            return;
        }
        if (!actorAuthorized.test(actor)) {
            stopFollow(actor.getUniqueId(), session, "Follow ended: staff authorization changed.");
            cancel.run();
            return;
        }
        if (!session.inFlight.compareAndSet(false, true)) {
            return;
        }
        UUID actorId = actor.getUniqueId();
        TargetSnapshot target = session.target;
        onEntity(target.playerId(),
                live -> {
                    if (connectionChanged(target, live) || !targetEligible(live)) {
                        stopFollow(actorId, session, "Follow ended: the target disconnected or is protected.");
                        return;
                    }
                    Location where = live.getLocation().clone();
                    onEntity(actorId, current -> followToPosition(current, session, where),
                            () -> stopFollow(actorId, session, "Follow ended: staff player disconnected."));
                },
                () -> stopFollow(actorId, session, "Follow ended: the player left the server."));
    }

    private void followToPosition(Player actor, FollowSession session, Location where) {
        if (!followStillAuthorized(actor, session) || closeEnough(actor.getLocation(), where)) {
            session.inFlight.set(false);
            return;
        }
        try {
            actor.teleportAsync(where).whenComplete((success, failure) -> {
                session.inFlight.set(false);
                if (failure != null || !Boolean.TRUE.equals(success)) {
                    stopFollow(actor.getUniqueId(), session, "Follow ended: teleport failed.");
                }
            });
        } catch (RuntimeException exception) {
            stopFollow(actor.getUniqueId(), session, "Follow ended: teleport could not be scheduled.");
        }
    }

    private boolean followStillAuthorized(Player actor, FollowSession session) {
        if (follows.get(actor.getUniqueId()) == session
                && currentAttempt(actor.getUniqueId(), session.attempt) && actorAuthorized.test(actor)) {
            return true;
        }
        follows.remove(actor.getUniqueId(), session);
        followAttempts.remove(actor.getUniqueId(), session.attempt);
        return false;
    }

    private static boolean closeEnough(Location current, Location target) {
        return current.getWorld() != null && current.getWorld() == target.getWorld()
                && current.distanceSquared(target) < FOLLOW_REFRESH_DISTANCE_SQUARED;
    }

    private void stopFollow(UUID actorId, FollowSession session, String reason) {
        if (follows.remove(actorId, session)) {
            followAttempts.remove(actorId, session.attempt);
            message(actorId, reason);
        }
        session.inFlight.set(false);
    }

    private static final class FollowSession {
        private final Object attempt;
        private final TargetSnapshot target;
        private final AtomicBoolean inFlight = new AtomicBoolean();
        private FollowSession(Object attempt, TargetSnapshot target) {
            this.attempt = attempt;
            this.target = target;
        }
    }

    private void revalidateBeforeAttachment(UUID actorId, Object attempt, TargetSnapshot snapshot, Player liveTarget) {
        if (!currentAttempt(actorId, attempt)) {
            return;
        }
        if (connectionChanged(snapshot, liveTarget)) {
            endAttempt(actorId, attempt, "Teleported safely; direct spectating was cancelled because the target connection changed.");
            return;
        }
        if (!targetEligible(liveTarget)) {
            endAttempt(actorId, attempt, "Teleported to the last safe target location; direct spectating is no longer available.");
            return;
        }
        onEntity(actorId, actor -> attach(actor, attempt, snapshot),
                () -> followAttempts.remove(actorId, attempt));
    }

    private void attach(Player actor, Object attempt, TargetSnapshot snapshot) {
        if (!currentAttempt(actor.getUniqueId(), attempt)) {
            return;
        }
        if (!canContinue(actor)) {
            followAttempts.remove(actor.getUniqueId(), attempt);
            return;
        }
        Player currentTarget = plugin.getServer().getPlayer(snapshot.playerId());
        if (currentTarget == null || connectionChanged(snapshot, currentTarget)) {
            followAttempts.remove(actor.getUniqueId(), attempt);
            actor.sendMessage(StaffMessageStyle.style(Component.text(
                    "Direct spectating was cancelled because the target connection changed.",
                    NamedTextColor.YELLOW
            )));
            return;
        }
        if (vanished.test(snapshot.playerId())) {
            followAttempts.remove(actor.getUniqueId(), attempt);
            actor.sendMessage(StaffMessageStyle.style(Component.text(
                    "Direct spectating was cancelled because that target entered vanish.",
                    NamedTextColor.YELLOW
            )));
            return;
        }
        try {
            actor.setSpectatorTarget(currentTarget);
            ownedSpectators.put(actor.getUniqueId(), currentTarget);
            if (!monitorAttachment(actor, attempt, snapshot)) {
                return;
            }
            actor.sendMessage(StaffMessageStyle.style(Component.text("Now spectating " + snapshot.name() + '.', NamedTextColor.GREEN)));
        } catch (IllegalArgumentException | IllegalStateException exception) {
            followAttempts.remove(actor.getUniqueId(), attempt);
            ownedSpectators.remove(actor.getUniqueId());
            actor.sendMessage(StaffMessageStyle.style(Component.text(
                    "Teleported to " + snapshot.name() + "; direct spectator attachment was unavailable.",
                    NamedTextColor.YELLOW
            )));
        }
    }

    private boolean monitorAttachment(Player actor, Object attempt, TargetSnapshot snapshot) {
        AtomicInteger ticks = new AtomicInteger();
        AtomicBoolean targetCheckInFlight = new AtomicBoolean();
        try {
            actor.getScheduler().runAtFixedRate(
                    plugin,
                    task -> monitorAttachmentTick(actor, attempt, snapshot, task::cancel,
                            ticks, targetCheckInFlight),
                    () -> {
                    },
                    1L,
                    1L
            );
            return true;
        } catch (RuntimeException exception) {
            detach(actor);
            followAttempts.remove(actor.getUniqueId(), attempt);
            ownedSpectators.remove(actor.getUniqueId());
            actor.sendMessage(StaffMessageStyle.style(Component.text(
                    "Direct spectating was cancelled because its safety monitor could not start.",
                    NamedTextColor.RED
            )));
            return false;
        }
    }

    private void monitorAttachmentTick(Player actor, Object attempt, TargetSnapshot snapshot,
                                       Runnable cancelTask, AtomicInteger ticks,
                                       AtomicBoolean targetCheckInFlight) {
        if (cancelInactiveAttachment(actor, attempt, snapshot, cancelTask)) {
            return;
        }
        // Check target safety on the target-owned scheduler every second.
        if (ticks.incrementAndGet() % TARGET_CHECK_INTERVAL_TICKS == 0
                && targetCheckInFlight.compareAndSet(false, true)) {
            scheduleTargetSafetyCheck(actor.getUniqueId(), attempt, snapshot, targetCheckInFlight);
        }
    }

    private boolean cancelInactiveAttachment(Player actor, Object attempt, TargetSnapshot snapshot,
                                             Runnable cancelTask) {
        if (!currentAttempt(actor.getUniqueId(), attempt)) {
            cancelTask.run();
            return true;
        }
        if (actor.getGameMode() != GameMode.SPECTATOR
                || !sameConnection(snapshot.connection(), actor.getSpectatorTarget())) {
            followAttempts.remove(actor.getUniqueId(), attempt);
            ownedSpectators.remove(actor.getUniqueId());
            cancelTask.run();
            return true;
        }
        if (!actorAuthorized.test(actor)) {
            stop(actor);
            actor.sendMessage(StaffMessageStyle.style(Component.text(
                    "Follow/Spectate was cancelled because your staff session or permission changed.",
                    NamedTextColor.RED
            )));
            cancelTask.run();
            return true;
        }
        if (vanished.test(snapshot.playerId())) {
            stop(actor);
            actor.sendMessage(StaffMessageStyle.style(Component.text(
                    "Spectating stopped because that target entered vanish.",
                    NamedTextColor.YELLOW
            )));
            cancelTask.run();
            return true;
        }
        return false;
    }

    private void scheduleTargetSafetyCheck(UUID actorId, Object attempt, TargetSnapshot snapshot,
                                           AtomicBoolean targetCheckInFlight) {
        onEntity(snapshot.playerId(),
                target -> {
                    boolean protectedNow = connectionChanged(snapshot, target) || !targetEligible(target);
                    targetCheckInFlight.set(false);
                    if (protectedNow) {
                        onEntity(actorId, current -> stopProtectedSpectator(current, attempt, snapshot));
                    }
                },
                () -> {
                    targetCheckInFlight.set(false);
                    onEntity(actorId, current -> stopProtectedSpectator(current, attempt, snapshot));
                });
    }

    private void stopProtectedSpectator(Player actor, Object attempt, TargetSnapshot snapshot) {
        if (!currentAttempt(actor.getUniqueId(), attempt)) {
            return;
        }
        if (actor.getGameMode() == GameMode.SPECTATOR
                && sameConnection(snapshot.connection(), actor.getSpectatorTarget())) {
            stop(actor);
            actor.sendMessage(StaffMessageStyle.style(Component.text(
                    "Spectating stopped because that target disconnected or became protected.",
                    NamedTextColor.YELLOW)));
        }
    }

    private static void detach(Player actor) {
        try {
            actor.setSpectatorTarget(null);
        } catch (IllegalArgumentException | IllegalStateException ignored) {
            // Best effort: the actor may already have left spectator mode or retired.
        }
    }

    private boolean canContinue(Player actor) {
        if (actorAuthorized.test(actor)) {
            return true;
        }
        actor.sendMessage(StaffMessageStyle.style(Component.text(
                "Follow/Spectate was cancelled because your staff session or permission changed.",
                NamedTextColor.RED
        )));
        return false;
    }

    private boolean targetEligible(Player target) {
        return StaffToolSpectateTargetPolicy.eligible(
                vanished.test(target.getUniqueId()),
                target.hasPermission(SPECTATE_EXEMPT_PERMISSION)
        );
    }

    private static boolean connectionChanged(TargetSnapshot snapshot, Player liveTarget) {
        return !sameConnection(snapshot.connection(), liveTarget);
    }

    // Bukkit entity equality is identity/UUID-oriented, but this guard must distinguish a reconnect
    // that reuses the same player UUID. Reference identity is intentional.
    @SuppressWarnings({"PMD.CompareObjectsWithEquals", "ReferenceEquality"})
    private static boolean sameConnection(Entity expected, Entity actual) {
        return expected == actual;
    }

    private void onEntity(UUID playerId, Consumer<Player> operation) {
        onEntity(playerId, operation, () -> {
        });
    }

    private void onEntity(UUID playerId, Consumer<Player> operation, Runnable retired) {
        AtomicBoolean settled = new AtomicBoolean();
        Runnable retireOnce = () -> {
            if (settled.compareAndSet(false, true)) {
                retired.run();
            }
        };
        try {
            plugin.getServer().getGlobalRegionScheduler().execute(
                    plugin,
                    () -> submitEntity(playerId, operation, retired, retireOnce, settled)
            );
        } catch (RuntimeException exception) {
            retireOnce.run();
        }
    }

    private void submitEntity(
            UUID playerId,
            Consumer<Player> operation,
            Runnable retired,
            Runnable retireOnce,
            AtomicBoolean settled
    ) {
        Player player = plugin.getServer().getPlayer(playerId);
        if (player == null) {
            retireOnce.run();
            return;
        }
        try {
            boolean scheduled = player.getScheduler().execute(
                    plugin,
                    () -> runOwned(player, operation, retired, settled),
                    retireOnce,
                    1L
            );
            if (!scheduled) {
                retireOnce.run();
            }
        } catch (RuntimeException exception) {
            retireOnce.run();
        }
    }

    private static void runOwned(
            Player player,
            Consumer<Player> operation,
            Runnable retired,
            AtomicBoolean settled
    ) {
        if (!settled.compareAndSet(false, true)) {
            return;
        }
        if (!player.isOnline()) {
            retired.run();
            return;
        }
        operation.accept(player);
    }

    private void message(UUID playerId, String text) {
        onEntity(playerId, player -> player.sendMessage(StaffMessageStyle.style(Component.text(text))));
    }

    private record TargetSnapshot(UUID playerId, String name, Location location, Player connection) {
        private TargetSnapshot withLocation(Location currentLocation) {
            return new TargetSnapshot(playerId, name, currentLocation, connection);
        }
    }
}
