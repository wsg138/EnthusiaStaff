package net.enthusia.staff.paper.staff;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Predicate;
import net.enthusia.staff.paper.freeze.FreezeManager;
import net.enthusia.staff.paper.visibility.VanishManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

/** Executes scheduler-safe random staff teleport while preserving dispatcher authorization boundaries. */
final class StaffToolRandomTeleportService {
    private static final String RANDOM_EXEMPT_PERMISSION = "enthusiastaff.stafftools.random-exempt";

    private final Platform platform;
    private final String serverId;
    private final Predicate<String> enabled;
    private final CandidateEligibility candidateEligibility;
    private final Predicate<Player> actorAuthorized;
    private final Consumer<List<UUID>> shuffle;

    StaffToolRandomTeleportService(
            JavaPlugin plugin,
            String serverId,
            StaffModeManager staffMode,
            VanishManager vanish,
            FreezeManager freeze,
            StaffToolSettings settings
    ) {
        this(
                new BukkitPlatform(plugin),
                serverId,
                settings::randomTeleportEnabledOn,
                (actorId, target) -> eligibleCandidate(actorId, target, staffMode, vanish, freeze, settings),
                actor -> staffMode.authorizedForTool(actor, StaffToolDefinition.RANDOM_TELEPORT)
                        && actor.hasPermission(StaffToolDefinition.RANDOM_TELEPORT.permission()),
                Collections::shuffle
        );
    }

    StaffToolRandomTeleportService(
            Platform platform,
            String serverId,
            Predicate<String> enabled,
            CandidateEligibility candidateEligibility,
            Predicate<Player> actorAuthorized,
            Consumer<List<UUID>> shuffle
    ) {
        this.platform = java.util.Objects.requireNonNull(platform, "platform");
        this.serverId = java.util.Objects.requireNonNull(serverId, "serverId");
        this.enabled = java.util.Objects.requireNonNull(enabled, "enabled");
        this.candidateEligibility = java.util.Objects.requireNonNull(candidateEligibility, "candidateEligibility");
        this.actorAuthorized = java.util.Objects.requireNonNull(actorAuthorized, "actorAuthorized");
        this.shuffle = java.util.Objects.requireNonNull(shuffle, "shuffle");
    }

    void begin(Player actor) {
        if (!enabled.test(serverId)) {
            actor.sendMessage(Component.text(
                    "Random staff teleport is disabled on backend " + serverId + '.',
                    NamedTextColor.YELLOW
            ));
            return;
        }
        UUID actorId = actor.getUniqueId();
        try {
            platform.executeGlobal(() -> collectCandidates(actorId));
        } catch (RuntimeException failure) {
            actor.sendMessage(Component.text("Random staff teleport could not start safely.", NamedTextColor.RED));
        }
    }

    private void collectCandidates(UUID actorId) {
        final List<Player> candidates;
        try {
            candidates = List.copyOf(platform.onlinePlayers());
        } catch (RuntimeException failure) {
            message(actorId, "Random staff teleport could not inspect online players safely.");
            return;
        }
        if (candidates.isEmpty()) {
            message(actorId, "No suitable random-teleport target is online.");
            return;
        }
        ConcurrentLinkedQueue<UUID> eligible = new ConcurrentLinkedQueue<>();
        AtomicInteger remaining = new AtomicInteger(candidates.size());
        Runnable finishedOne = () -> finishCandidateCollection(actorId, eligible, remaining);
        for (Player candidate : candidates) {
            snapshotCandidate(actorId, candidate, eligible, finishedOne);
        }
    }

    private void finishCandidateCollection(
            UUID actorId,
            Collection<UUID> eligible,
            AtomicInteger remaining
    ) {
        if (remaining.decrementAndGet() == 0) {
            finishTeleport(actorId, eligible);
        }
    }

    private void snapshotCandidate(
            UUID actorId,
            Player target,
            Collection<UUID> eligible,
            Runnable finished
    ) {
        AtomicBoolean settled = new AtomicBoolean();
        Runnable retired = () -> settleCandidate(settled, finished);
        Runnable inspect = () -> inspectCandidate(actorId, target, eligible, settled, finished);
        try {
            boolean scheduled = platform.executeEntity(target, inspect, retired);
            if (!scheduled) {
                retired.run();
            }
        } catch (RuntimeException failure) {
            retired.run();
        }
    }

    private void inspectCandidate(
            UUID actorId,
            Player target,
            Collection<UUID> eligible,
            AtomicBoolean settled,
            Runnable finished
    ) {
        if (!settled.compareAndSet(false, true)) {
            return;
        }
        try {
            if (candidateEligibility.eligible(actorId, target)) {
                eligible.add(target.getUniqueId());
            }
        } catch (RuntimeException ignored) {
            // Fail closed: this target simply does not enter the eligible queue.
        } finally {
            finished.run();
        }
    }

    private static void settleCandidate(AtomicBoolean settled, Runnable finished) {
        if (settled.compareAndSet(false, true)) {
            finished.run();
        }
    }

    private static boolean eligibleCandidate(
            UUID actorId,
            Player target,
            StaffModeManager staffMode,
            VanishManager vanish,
            FreezeManager freeze,
            StaffToolSettings settings
    ) {
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

    private void finishTeleport(UUID actorId, Collection<UUID> candidates) {
        List<UUID> shuffled = new ArrayList<>(candidates);
        shuffle.accept(shuffled);
        attemptNextCandidate(actorId, new ConcurrentLinkedQueue<>(shuffled));
    }

    private void attemptNextCandidate(UUID actorId, ConcurrentLinkedQueue<UUID> candidates) {
        UUID targetId = candidates.poll();
        if (targetId == null) {
            message(actorId, "No suitable random-teleport target is online.");
            return;
        }
        onEntity(
                targetId,
                target -> revalidateCandidate(actorId, candidates, target),
                () -> attemptNextCandidate(actorId, candidates)
        );
    }

    private void revalidateCandidate(
            UUID actorId,
            ConcurrentLinkedQueue<UUID> candidates,
            Player target
    ) {
        if (!candidateEligibility.eligible(actorId, target)) {
            attemptNextCandidate(actorId, candidates);
            return;
        }
        TargetSnapshot snapshot = new TargetSnapshot(target.getName(), target.getLocation().clone());
        onEntity(actorId, actor -> teleportToSnapshot(actorId, actor, snapshot));
    }

    private void teleportToSnapshot(UUID actorId, Player actor, TargetSnapshot target) {
        if (!canContinue(actor)) {
            return;
        }
        try {
            actor.teleportAsync(target.location()).whenComplete(
                    (success, failure) -> finishTeleport(actorId, target, success, failure)
            );
        } catch (RuntimeException failure) {
            finishTeleport(actorId, target, false, failure);
        }
    }

    private boolean canContinue(Player actor) {
        try {
            if (actorAuthorized.test(actor)) {
                return true;
            }
        } catch (RuntimeException ignored) {
            // Authorization uncertainty fails closed.
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
            platform.executeGlobal(() -> resolvePlayer(playerId, operation, retired, settled, retireOnce));
        } catch (RuntimeException failure) {
            retireOnce.run();
        }
    }

    private void resolvePlayer(
            UUID playerId,
            Consumer<Player> operation,
            Runnable retired,
            AtomicBoolean settled,
            Runnable retireOnce
    ) {
        final Player player;
        try {
            player = platform.player(playerId);
        } catch (RuntimeException failure) {
            retireOnce.run();
            return;
        }
        if (player == null) {
            retireOnce.run();
            return;
        }
        Runnable owned = () -> runOwned(settled, player, operation, retired);
        try {
            boolean scheduled = platform.executeEntity(player, owned, retireOnce);
            if (!scheduled) {
                retireOnce.run();
            }
        } catch (RuntimeException failure) {
            retireOnce.run();
        }
    }

    private static void runOwned(
            AtomicBoolean settled,
            Player player,
            Consumer<Player> operation,
            Runnable retired
    ) {
        if (!settled.compareAndSet(false, true)) {
            return;
        }
        try {
            operation.accept(player);
        } catch (RuntimeException failure) {
            retired.run();
        }
    }

    private void message(UUID playerId, String text) {
        onEntity(playerId, player -> player.sendMessage(Component.text(text)));
    }

    @FunctionalInterface
    interface CandidateEligibility {
        boolean eligible(UUID actorId, Player target);
    }

    interface Platform {
        Collection<? extends Player> onlinePlayers();

        Player player(UUID playerId);

        void executeGlobal(Runnable operation);

        boolean executeEntity(Player player, Runnable operation, Runnable retired);
    }

    static final class BukkitPlatform implements Platform {
        private final Plugin plugin;

        BukkitPlatform(Plugin plugin) {
            this.plugin = java.util.Objects.requireNonNull(plugin, "plugin");
        }

        @Override
        public Collection<? extends Player> onlinePlayers() {
            return plugin.getServer().getOnlinePlayers();
        }

        @Override
        public Player player(UUID playerId) {
            return plugin.getServer().getPlayer(playerId);
        }

        @Override
        public void executeGlobal(Runnable operation) {
            plugin.getServer().getGlobalRegionScheduler().execute(plugin, operation);
        }

        @Override
        public boolean executeEntity(Player player, Runnable operation, Runnable retired) {
            return player.getScheduler().execute(plugin, operation, retired, 1L);
        }
    }

    private record TargetSnapshot(String name, Location location) {
    }
}
