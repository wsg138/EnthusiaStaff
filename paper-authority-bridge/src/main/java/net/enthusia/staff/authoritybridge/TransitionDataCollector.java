package net.enthusia.staff.authoritybridge;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import net.enthusia.staff.domain.application.DiscordSrvMigrationService;
import net.enthusia.staff.domain.application.DiscordSrvMigrationService.DiscordSrvLinkProvider;
import net.enthusia.staff.domain.application.DiscordSrvMigrationService.ImportReport;
import net.enthusia.staff.domain.application.DiscordSrvMigrationService.MirrorResult;
import net.enthusia.staff.domain.player.PlayerPlatform;
import net.enthusia.staff.persistence.TransitionDataRuntime;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/** Bounded transition-only collector. It never performs moderation or writes to DiscordSRV/LiteBans. */
final class TransitionDataCollector implements AutoCloseable {
    private static final int OFFLINE_LOOKUPS_PER_PASS = 128;
    private static final long SHUTDOWN_SECONDS = 3L;

    private final JavaPlugin plugin;
    private final TransitionCollectorConfiguration.Value configuration;
    private final TransitionDataRuntime data;
    private final Optional<DiscordSrvTransitionLinkSource> discordSrv;
    private final DiscordSrvMigrationService migration;
    private final ThreadPoolExecutor worker;
    private final Clock clock;
    private final AtomicBoolean passInFlight = new AtomicBoolean();
    private volatile ScheduledTask task;
    private int offlineCursor;

    private TransitionDataCollector(
            JavaPlugin plugin,
            TransitionCollectorConfiguration.Value configuration,
            TransitionDataRuntime data,
            Optional<DiscordSrvTransitionLinkSource> discordSrv,
            Clock clock
    ) {
        this.plugin = plugin;
        this.configuration = configuration;
        this.data = data;
        this.discordSrv = discordSrv;
        this.clock = clock;
        this.migration = new DiscordSrvMigrationService(clock, data.identities());
        this.worker = new ThreadPoolExecutor(
                1, 1, 0L, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(1),
                runnable -> Thread.ofPlatform().daemon(true).name("enthusia-transition-collector").unstarted(runnable),
                new ThreadPoolExecutor.AbortPolicy());
    }

    static TransitionDataCollector open(
            JavaPlugin plugin,
            TransitionCollectorConfiguration.Value configuration
    ) {
        if (plugin == null || configuration == null) {
            throw new IllegalArgumentException("transition collector dependencies must be present");
        }
        TransitionDataRuntime data = TransitionDataRuntime.open(configuration.database());
        try {
            return new TransitionDataCollector(
                    plugin, configuration, data, DiscordSrvTransitionLinkSource.discover(plugin), Clock.systemUTC());
        } catch (RuntimeException exception) {
            data.close();
            throw exception;
        }
    }

    void start() {
        long intervalTicks = Math.max(1L, configuration.interval().toMillis() / 50L);
        task = plugin.getServer().getGlobalRegionScheduler().runAtFixedRate(
                plugin, ignored -> captureAndSubmit(), 1L, intervalTicks);
    }

    private void captureAndSubmit() {
        if (!passInFlight.compareAndSet(false, true)) {
            return;
        }
        try {
            TransitionSnapshotPlanner.Plan snapshot = capture();
            worker.execute(() -> persist(snapshot));
        } catch (RuntimeException exception) {
            passInFlight.set(false);
            logFailure("enthusiastaff_transition_collector_capture_failed", exception);
        }
    }

    private TransitionSnapshotPlanner.Plan capture() {
        Instant now = clock.instant();
        List<TransitionSnapshotPlanner.Observation> observations = new ArrayList<>();
        Map<String, UUID> links = discordSrv.map(DiscordSrvTransitionLinkSource::snapshotLinks).orElse(Map.of());
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            observations.add(new TransitionSnapshotPlanner.Observation(
                    player.getUniqueId(), player.getName(), now, true));
        }
        captureOfflineLinkBatch(links, observations);
        return TransitionSnapshotPlanner.plan(links, observations);
    }

    private void captureOfflineLinkBatch(
            Map<String, UUID> links,
            Collection<TransitionSnapshotPlanner.Observation> observations
    ) {
        List<UUID> players = links.values().stream().distinct().sorted(Comparator.comparing(UUID::toString)).toList();
        if (players.isEmpty()) {
            offlineCursor = 0;
            return;
        }
        int lookups = Math.min(OFFLINE_LOOKUPS_PER_PASS, players.size());
        for (int offset = 0; offset < lookups; offset++) {
            UUID playerId = players.get((offlineCursor + offset) % players.size());
            if (!currentlyObserved(playerId, observations)) {
                offlineObservation(playerId).ifPresent(observations::add);
            }
        }
        offlineCursor = (offlineCursor + lookups) % players.size();
    }

    private Optional<TransitionSnapshotPlanner.Observation> offlineObservation(UUID playerId) {
        OfflinePlayer player = plugin.getServer().getOfflinePlayer(playerId);
        String name = player.getName();
        long lastSeen = player.getLastSeen();
        if (name == null || name.isBlank() || lastSeen <= 0L) {
            return Optional.empty();
        }
        return Optional.of(new TransitionSnapshotPlanner.Observation(
                playerId, name, Instant.ofEpochMilli(lastSeen), false));
    }

    private static boolean currentlyObserved(
            UUID playerId,
            Collection<TransitionSnapshotPlanner.Observation> observations
    ) {
        return observations.stream().anyMatch(value -> value.playerId().equals(playerId));
    }

    private void persist(TransitionSnapshotPlanner.Plan snapshot) {
        try {
            snapshot.observations().forEach(this::persistObservation);
            ImportReport report = migration.importSnapshot(new ReadOnlySnapshotProvider(snapshot.importableLinks()));
            plugin.getLogger().log(Level.INFO,
                    "enthusiastaff_transition_collector_pass observed={0} imported={1} unchanged={2} conflicts={3} skipped={4}",
                    new Object[]{snapshot.observations().size(), report.imported(), report.unchanged(),
                            report.conflicts().size(), snapshot.skippedLinks()});
        } catch (RuntimeException exception) {
            logFailure("enthusiastaff_transition_collector_pass_failed", exception);
        } finally {
            passInFlight.set(false);
        }
    }

    private void persistObservation(TransitionSnapshotPlanner.Observation observation) {
        data.players().recordSeen(
                observation.playerId(), observation.username(), PlayerPlatform.UNKNOWN,
                configuration.serverId(), observation.seenAt());
        if (!observation.online()) {
            data.players().recordDisconnected(
                    observation.playerId(), configuration.serverId(), observation.seenAt().plusMillis(1L));
        }
    }

    private void logFailure(String code, RuntimeException exception) {
        plugin.getLogger().log(Level.WARNING, code + " type={0}", exception.getClass().getSimpleName());
    }

    @Override
    public void close() {
        ScheduledTask scheduled = task;
        if (scheduled != null) {
            scheduled.cancel();
        }
        worker.shutdown();
        try {
            if (!worker.awaitTermination(SHUTDOWN_SECONDS, TimeUnit.SECONDS)) {
                worker.shutdownNow();
                worker.awaitTermination(SHUTDOWN_SECONDS, TimeUnit.SECONDS);
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            worker.shutdownNow();
        } finally {
            data.close();
        }
    }

    private record ReadOnlySnapshotProvider(Map<String, UUID> links) implements DiscordSrvLinkProvider {
        @Override
        public Map<String, UUID> snapshotLinks() {
            return links;
        }

        @Override
        public MirrorResult mirrorMain(String discordUserId, UUID minecraftPlayerId) {
            return MirrorResult.UNAVAILABLE;
        }
    }
}
