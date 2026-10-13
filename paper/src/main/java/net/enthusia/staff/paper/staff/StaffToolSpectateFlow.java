package net.enthusia.staff.paper.staff;

import net.enthusia.staff.paper.presentation.StaffMessageStyle;
import java.util.UUID;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
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
    private final Map<UUID, FollowSession> follows = new ConcurrentHashMap<>();
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
        onEntity(
                targetId,
                target -> inspectTarget(actorId, target),
                () -> message(actorId, "That player is no longer online.")
        );
    }

    private void inspectTarget(UUID actorId, Player target) {
        if (!targetEligible(target)) {
            message(actorId, "That target is protected from staff follow/spectate tools.");
            return;
        }
        TargetSnapshot snapshot = new TargetSnapshot(
                target.getUniqueId(),
                target.getName(),
                target.getLocation().clone(),
                target
        );
        onEntity(actorId, actor -> followSnapshot(actor, snapshot));
    }

    private void followSnapshot(Player actor, TargetSnapshot target) {
        if (!canContinue(actor)) {
            return;
        }
        UUID actorId = actor.getUniqueId();
        onEntity(
                target.playerId(),
                liveTarget -> revalidateBeforeTeleport(actorId, target, liveTarget),
                () -> message(actorId, "Follow/Spectate was cancelled because that target is no longer online.")
        );
    }

    private void revalidateBeforeTeleport(UUID actorId, TargetSnapshot snapshot, Player liveTarget) {
        if (connectionChanged(snapshot, liveTarget)) {
            message(actorId, "Follow/Spectate was cancelled because the target connection changed.");
            return;
        }
        if (!targetEligible(liveTarget)) {
            message(actorId, "Follow/Spectate was cancelled because that target became protected.");
            return;
        }
        TargetSnapshot current = snapshot.withLocation(liveTarget.getLocation().clone());
        onEntity(actorId, actor -> beginTeleport(actor, current));
    }

    private void beginTeleport(Player actor, TargetSnapshot target) {
        if (!canContinue(actor)) {
            return;
        }
        UUID actorId = actor.getUniqueId();
        try {
            actor.teleportAsync(target.location()).whenComplete(
                    (success, failure) -> finishTeleport(actorId, target, success, failure)
            );
        } catch (RuntimeException exception) {
            actor.sendMessage(StaffMessageStyle.style(Component.text("Follow/Spectate teleport failed safely.", NamedTextColor.RED)));
        }
    }

    private void finishTeleport(UUID actorId, TargetSnapshot target, Boolean success, Throwable failure) {
        if (failure != null || !Boolean.TRUE.equals(success)) {
            message(actorId, "Follow/Spectate teleport failed safely.");
            return;
        }
        onEntity(actorId, actor -> prepareAttachment(actor, target));
    }

    private void prepareAttachment(Player actor, TargetSnapshot target) {
        if (!canContinue(actor)) {
            return;
        }
        if (actor.getGameMode() != GameMode.SPECTATOR) {
            beginContinuousFollow(actor, target);
            return;
        }
        UUID actorId = actor.getUniqueId();
        onEntity(
                target.playerId(),
                liveTarget -> revalidateBeforeAttachment(actorId, target, liveTarget),
                () -> message(actorId, "Teleported to the last safe target location; direct spectating is unavailable.")
        );
    }

    /** Non-spectator Staff Mode retains its configured game mode while following. */
    private void beginContinuousFollow(Player actor, TargetSnapshot target) {
        FollowSession session = new FollowSession(target);
        follows.put(actor.getUniqueId(), session);
        try {
            actor.getScheduler().runAtFixedRate(plugin,
                    task -> followTick(actor, session, task::cancel),
                    () -> follows.remove(actor.getUniqueId(), session), 5L, 5L);
            actor.sendMessage(StaffMessageStyle.style(Component.text(
                    "Following " + target.name() + ". Right-click air with the Follow tool to stop.",
                    NamedTextColor.GREEN)));
        } catch (RuntimeException exception) {
            follows.remove(actor.getUniqueId(), session);
            actor.sendMessage(StaffMessageStyle.style(Component.text(
                    "Unable to keep following this player; no continuous follow was started.",
                    NamedTextColor.RED)));
        }
    }

    boolean stop(Player actor) {
        return follows.remove(actor.getUniqueId()) != null;
    }

    private void followTick(Player actor, FollowSession session, Runnable cancel) {
        if (follows.get(actor.getUniqueId()) != session) {
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
                            () -> follows.remove(actorId, session));
                },
                () -> stopFollow(actorId, session, "Follow ended: the player left the server."));
    }

    private void followToPosition(Player actor, FollowSession session, Location where) {
        if (follows.get(actor.getUniqueId()) != session
                || !actorAuthorized.test(actor)) {
            follows.remove(actor.getUniqueId(), session);
            session.inFlight.set(false);
            return;
        }
        Location here = actor.getLocation();
        if (here.getWorld() != null && here.getWorld() == where.getWorld()
                && here.distanceSquared(where) < FOLLOW_REFRESH_DISTANCE_SQUARED) {
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

    private void stopFollow(UUID actorId, FollowSession session, String reason) {
        if (follows.remove(actorId, session)) {
            message(actorId, reason);
        }
        session.inFlight.set(false);
    }

    private static final class FollowSession {
        private final TargetSnapshot target;
        private final AtomicBoolean inFlight = new AtomicBoolean();
        private FollowSession(TargetSnapshot target) {
            this.target = target;
        }
    }

    private void revalidateBeforeAttachment(UUID actorId, TargetSnapshot snapshot, Player liveTarget) {
        if (connectionChanged(snapshot, liveTarget)) {
            message(actorId, "Teleported safely; direct spectating was cancelled because the target connection changed.");
            return;
        }
        if (!targetEligible(liveTarget)) {
            message(actorId, "Teleported to the last safe target location; direct spectating is no longer available.");
            return;
        }
        onEntity(actorId, actor -> attach(actor, snapshot));
    }

    private void attach(Player actor, TargetSnapshot snapshot) {
        if (!canContinue(actor)) {
            return;
        }
        Player currentTarget = plugin.getServer().getPlayer(snapshot.playerId());
        if (currentTarget == null || connectionChanged(snapshot, currentTarget)) {
            actor.sendMessage(StaffMessageStyle.style(Component.text(
                    "Direct spectating was cancelled because the target connection changed.",
                    NamedTextColor.YELLOW
            )));
            return;
        }
        if (vanished.test(snapshot.playerId())) {
            actor.sendMessage(StaffMessageStyle.style(Component.text(
                    "Direct spectating was cancelled because that target entered vanish.",
                    NamedTextColor.YELLOW
            )));
            return;
        }
        try {
            actor.setSpectatorTarget(currentTarget);
            if (!monitorAttachment(actor, snapshot)) {
                return;
            }
            actor.sendMessage(StaffMessageStyle.style(Component.text("Now spectating " + snapshot.name() + '.', NamedTextColor.GREEN)));
        } catch (IllegalArgumentException | IllegalStateException exception) {
            actor.sendMessage(StaffMessageStyle.style(Component.text(
                    "Teleported to " + snapshot.name() + "; direct spectator attachment was unavailable.",
                    NamedTextColor.YELLOW
            )));
        }
    }

    private boolean monitorAttachment(Player actor, TargetSnapshot snapshot) {
        try {
            actor.getScheduler().runAtFixedRate(
                    plugin,
                    task -> monitorAttachmentTick(actor, snapshot, task::cancel),
                    () -> {
                    },
                    1L,
                    1L
            );
            return true;
        } catch (RuntimeException exception) {
            detach(actor);
            actor.sendMessage(StaffMessageStyle.style(Component.text(
                    "Direct spectating was cancelled because its safety monitor could not start.",
                    NamedTextColor.RED
            )));
            return false;
        }
    }

    private void monitorAttachmentTick(Player actor, TargetSnapshot snapshot, Runnable cancelTask) {
        if (actor.getGameMode() != GameMode.SPECTATOR
                || !sameConnection(snapshot.connection(), actor.getSpectatorTarget())) {
            cancelTask.run();
            return;
        }
        if (!actorAuthorized.test(actor)) {
            detach(actor);
            actor.sendMessage(StaffMessageStyle.style(Component.text(
                    "Follow/Spectate was cancelled because your staff session or permission changed.",
                    NamedTextColor.RED
            )));
            cancelTask.run();
            return;
        }
        if (vanished.test(snapshot.playerId())) {
            detach(actor);
            actor.sendMessage(StaffMessageStyle.style(Component.text(
                    "Spectating stopped because that target entered vanish.",
                    NamedTextColor.YELLOW
            )));
            cancelTask.run();
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
