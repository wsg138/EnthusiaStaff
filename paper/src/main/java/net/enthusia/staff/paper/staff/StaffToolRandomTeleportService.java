package net.enthusia.staff.paper.staff;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import net.enthusia.staff.paper.freeze.FreezeManager;
import net.enthusia.staff.paper.visibility.VanishManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/** Executes scheduler-safe random staff teleport while preserving dispatcher authorization boundaries. */
final class StaffToolRandomTeleportService {
    private static final String RANDOM_EXEMPT_PERMISSION = "enthusiastaff.stafftools.random-exempt";

    private final JavaPlugin plugin;
    private final String serverId;
    private final StaffModeManager staffMode;
    private final VanishManager vanish;
    private final FreezeManager freeze;
    private final StaffToolSettings settings;

    StaffToolRandomTeleportService(
            JavaPlugin plugin,
            String serverId,
            StaffModeManager staffMode,
            VanishManager vanish,
            FreezeManager freeze,
            StaffToolSettings settings
    ) {
        this.plugin = java.util.Objects.requireNonNull(plugin, "plugin");
        this.serverId = java.util.Objects.requireNonNull(serverId, "serverId");
        this.staffMode = java.util.Objects.requireNonNull(staffMode, "staffMode");
        this.vanish = java.util.Objects.requireNonNull(vanish, "vanish");
        this.freeze = java.util.Objects.requireNonNull(freeze, "freeze");
        this.settings = java.util.Objects.requireNonNull(settings, "settings");
    }

    void begin(Player actor) {
        if (!settings.randomTeleportEnabledOn(serverId)) {
            actor.sendMessage(Component.text(
                    "Random staff teleport is disabled on backend " + serverId + '.',
                    NamedTextColor.YELLOW
            ));
            return;
        }
        UUID actorId = actor.getUniqueId();
        plugin.getServer().getGlobalRegionScheduler().execute(plugin, () -> collectCandidates(actorId));
    }

    private void collectCandidates(UUID actorId) {
        List<UUID> candidates = plugin.getServer().getOnlinePlayers().stream()
                .map(Player::getUniqueId)
                .toList();
        if (candidates.isEmpty()) {
            message(actorId, "No suitable random-teleport target is online.");
            return;
        }
        ConcurrentLinkedQueue<TargetSnapshot> eligible = new ConcurrentLinkedQueue<>();
        AtomicInteger remaining = new AtomicInteger(candidates.size());
        Runnable finishedOne = () -> finishCandidateCollection(actorId, eligible, remaining);
        for (UUID candidateId : candidates) {
            snapshotCandidate(actorId, candidateId, eligible, finishedOne);
        }
    }

    private void finishCandidateCollection(
            UUID actorId,
            Collection<TargetSnapshot> eligible,
            AtomicInteger remaining
    ) {
        if (remaining.decrementAndGet() == 0) {
            finishTeleport(actorId, eligible);
        }
    }

    private void snapshotCandidate(
            UUID actorId,
            UUID targetId,
            Collection<TargetSnapshot> eligible,
            Runnable finished
    ) {
        onEntity(targetId, target -> {
            try {
                if (eligibleCandidate(actorId, target)) {
                    eligible.add(new TargetSnapshot(targetId, target.getName(), target.getLocation().clone()));
                }
            } finally {
                finished.run();
            }
        }, finished);
    }

    private boolean eligibleCandidate(UUID actorId, Player target) {
        UUID targetId = target.getUniqueId();
        StaffToolTargetPolicy.Candidate candidate = new StaffToolTargetPolicy.Candidate(
                new StaffToolTargetPolicy.Identity(actorId, targetId),
                new StaffToolTargetPolicy.State(
                        staffMode.active(targetId),
                        vanish.isVanished(targetId),
                        freeze.isRestricted(targetId),
                        target.hasPermission(RANDOM_EXEMPT_PERMISSION),
                        target.isDead(),
                        target.isSleeping(),
                        target.isInsideVehicle()
                ),
                new StaffToolTargetPolicy.Environment(
                        target.getGameMode(),
                        settings.worldEnabled(target.getWorld().getName())
                )
        );
        return StaffToolTargetPolicy.eligibleRandomTarget(candidate);
    }

    private void finishTeleport(UUID actorId, Collection<TargetSnapshot> candidates) {
        onEntity(actorId, actor -> {
            if (!canContinue(actor)) {
                return;
            }
            List<TargetSnapshot> shuffled = new ArrayList<>(candidates);
            if (shuffled.isEmpty()) {
                actor.sendMessage(Component.text("No suitable random-teleport target is online."));
                return;
            }
            TargetSnapshot target = shuffled.get(ThreadLocalRandom.current().nextInt(shuffled.size()));
            actor.teleportAsync(target.location()).whenComplete(
                    (success, failure) -> finishTeleport(actorId, target, success, failure)
            );
        });
    }

    private boolean canContinue(Player actor) {
        if (staffMode.authorizedForTool(actor, StaffToolDefinition.RANDOM_TELEPORT)
                && actor.hasPermission(StaffToolDefinition.RANDOM_TELEPORT.permission())) {
            return true;
        }
        actor.sendMessage(Component.text(
                "Random teleport was cancelled because your staff session or permission changed.",
                NamedTextColor.RED
        ));
        return false;
    }

    private void finishTeleport(UUID actorId, TargetSnapshot target, Boolean success, Throwable failure) {
        if (failure != null || !Boolean.TRUE.equals(success)) {
            message(actorId, "Random staff teleport failed safely; no state was changed.");
            return;
        }
        message(actorId, "Teleported to a suitable random player: " + target.name() + '.');
    }

    private void onEntity(UUID playerId, Consumer<Player> operation, Runnable retired) {
        plugin.getServer().getGlobalRegionScheduler().execute(plugin, () -> {
            Player player = plugin.getServer().getPlayer(playerId);
            if (player == null) {
                retired.run();
                return;
            }
            boolean scheduled = player.getScheduler().execute(
                    plugin,
                    () -> operation.accept(player),
                    retired,
                    1L
            );
            if (!scheduled) {
                retired.run();
            }
        });
    }

    private void message(UUID playerId, String text) {
        onEntity(playerId, player -> player.sendMessage(Component.text(text)), () -> {
        });
    }

    private record TargetSnapshot(UUID playerId, String name, Location location) {
    }
}
