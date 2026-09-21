package net.enthusia.staff.paper.staff;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Predicate;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/** Scheduler-safe follow/spectate orchestration used by {@link StaffToolDispatcher}. */
final class StaffToolSpectateFlow {
    private static final String SPECTATE_EXEMPT_PERMISSION = "enthusiastaff.stafftools.spectate-exempt";

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
                target.getLocation().clone()
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
        if (!targetEligible(liveTarget)) {
            message(actorId, "Follow/Spectate was cancelled because that target became protected.");
            return;
        }
        onEntity(actorId, actor -> beginTeleport(actor, snapshot));
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
            actor.sendMessage(Component.text("Follow/Spectate teleport failed safely.", NamedTextColor.RED));
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
            actor.sendMessage(Component.text(
                    "Teleported to " + target.name()
                            + ". Direct spectating requires spectator mode; your game mode was not changed.",
                    NamedTextColor.GREEN
            ));
            return;
        }
        UUID actorId = actor.getUniqueId();
        onEntity(
                target.playerId(),
                liveTarget -> revalidateBeforeAttachment(actorId, target, liveTarget),
                () -> message(actorId, "Teleported to the last safe target location; direct spectating is unavailable.")
        );
    }

    private void revalidateBeforeAttachment(UUID actorId, TargetSnapshot snapshot, Player liveTarget) {
        if (!targetEligible(liveTarget)) {
            message(actorId, "Teleported to the last safe target location; direct spectating is no longer available.");
            return;
        }
        onEntity(actorId, actor -> attach(actor, snapshot, liveTarget));
    }

    private void attach(Player actor, TargetSnapshot snapshot, Player expectedTarget) {
        if (!canContinue(actor)) {
            return;
        }
        Player currentTarget = plugin.getServer().getPlayer(snapshot.playerId());
        if (currentTarget != expectedTarget) {
            actor.sendMessage(Component.text(
                    "Direct spectating was cancelled because the target connection changed.",
                    NamedTextColor.YELLOW
            ));
            return;
        }
        try {
            actor.setSpectatorTarget(currentTarget);
            actor.sendMessage(Component.text("Now spectating " + snapshot.name() + '.', NamedTextColor.GREEN));
        } catch (IllegalArgumentException | IllegalStateException exception) {
            actor.sendMessage(Component.text(
                    "Teleported to " + snapshot.name() + "; direct spectator attachment was unavailable.",
                    NamedTextColor.YELLOW
            ));
        }
    }

    private boolean canContinue(Player actor) {
        if (actorAuthorized.test(actor)) {
            return true;
        }
        actor.sendMessage(Component.text(
                "Follow/Spectate was cancelled because your staff session or permission changed.",
                NamedTextColor.RED
        ));
        return false;
    }

    private boolean targetEligible(Player target) {
        return StaffToolSpectateTargetPolicy.eligible(
                vanished.test(target.getUniqueId()),
                target.hasPermission(SPECTATE_EXEMPT_PERMISSION)
        );
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
        onEntity(playerId, player -> player.sendMessage(Component.text(text)));
    }

    private record TargetSnapshot(UUID playerId, String name, Location location) {
    }
}
