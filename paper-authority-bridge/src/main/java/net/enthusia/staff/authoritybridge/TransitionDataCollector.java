package net.enthusia.staff.authoritybridge;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import javax.sql.DataSource;
import net.enthusia.staff.domain.application.DiscordSrvMigrationService;
import net.enthusia.staff.domain.application.DiscordSrvMigrationService.DiscordSrvLinkProvider;
import net.enthusia.staff.domain.application.DiscordSrvMigrationService.ImportReport;
import net.enthusia.staff.domain.application.DiscordSrvMigrationService.MirrorResult;
import net.enthusia.staff.domain.player.PlayerPlatform;
import net.enthusia.staff.persistence.TransitionDataRuntime;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

/** Observes live player events without synchronous offline-player disk lookups. */
final class TransitionDataCollector implements AutoCloseable, Listener {
    private static final long SHUTDOWN_SECONDS = 3L;
    private static final int MAX_PENDING_OBSERVATIONS = 10_000;
    private static final int LINK_LOOKUPS_PER_PASS = 128;

    private final JavaPlugin plugin;
    private final TransitionCollectorConfiguration.Value configuration;
    private final TransitionDataRuntime data;
    private final Optional<DiscordSrvTransitionLinkSource> discordSrv;
    private final DiscordSrvMigrationService migration;
    private final ThreadPoolExecutor worker;
    private final Clock clock;
    private final AtomicBoolean passInFlight = new AtomicBoolean();
    private final ConcurrentMap<UUID, TransitionSnapshotPlanner.Observation> pending = new java.util.concurrent.ConcurrentHashMap<>();
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
        this.migration = new DiscordSrvMigrationService(clock, data.discordSrvImports());
        this.worker = new ThreadPoolExecutor(
                1, 1, 0L, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(1),
                runnable -> Thread.ofPlatform().daemon(true).name("enthusia-transition-collector").unstarted(runnable),
                new ThreadPoolExecutor.AbortPolicy());
    }

    static TransitionDataCollector open(
            JavaPlugin plugin,
            TransitionCollectorConfiguration.Value configuration
    ) {
        return openWithData(plugin, configuration, TransitionDataRuntime.open(configuration.database()));
    }

    /** Borrow Paper's already migrated pool; never run Flyway or close Paper's connection pool. */
    static TransitionDataCollector openEmbedded(
            JavaPlugin plugin,
            TransitionCollectorConfiguration.Value configuration,
            DataSource dataSource
    ) {
        return openWithData(plugin, configuration, TransitionDataRuntime.borrow(dataSource));
    }

    private static TransitionDataCollector openWithData(
            JavaPlugin plugin,
            TransitionCollectorConfiguration.Value configuration,
            TransitionDataRuntime data
    ) {
        try {
            if (plugin == null || configuration == null) {
                throw new IllegalArgumentException("transition collector dependencies must be present");
            }
            return new TransitionDataCollector(
                    plugin, configuration, data, DiscordSrvTransitionLinkSource.discover(plugin), Clock.systemUTC());
        } catch (RuntimeException exception) {
            data.close();
            throw exception;
        }
    }

    void start() {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        try {
            long intervalTicks = Math.max(1L, configuration.interval().toMillis() / 50L);
            task = plugin.getServer().getGlobalRegionScheduler().runAtFixedRate(
                    plugin, ignored -> captureAndSubmit(), 1L, intervalTicks);
        } catch (RuntimeException exception) {
            HandlerList.unregisterAll(this);
            throw exception;
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        observe(event.getPlayer(), true);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        observe(event.getPlayer(), false);
    }

    private void observe(Player player, boolean online) {
        if (pending.size() < MAX_PENDING_OBSERVATIONS || pending.containsKey(player.getUniqueId())) {
            pending.put(player.getUniqueId(), new TransitionSnapshotPlanner.Observation(
                    player.getUniqueId(), player.getName(), clock.instant(), online));
        }
    }

    private void captureAndSubmit() {
        if (!passInFlight.compareAndSet(false, true)) {
            return;
        }
        try {
            Capture snapshot = capture();
            worker.execute(() -> persist(snapshot));
        } catch (RuntimeException exception) {
            passInFlight.set(false);
            logFailure("enthusiastaff_transition_collector_capture_failed", exception);
        }
    }

    private Capture capture() {
        Instant now = clock.instant();
        Map<UUID, TransitionSnapshotPlanner.Observation> pendingSnapshot = Map.copyOf(pending);
        Map<UUID, TransitionSnapshotPlanner.Observation> online = new LinkedHashMap<>();
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            captureOnline(player, now, online);
        }
        Map<UUID, TransitionSnapshotPlanner.Observation> observations =
                TransitionSnapshotPlanner.mergePending(online, pendingSnapshot);
        Map<String, UUID> links = discordSrv.map(DiscordSrvTransitionLinkSource::snapshotLinks).orElse(Map.of());
        return new Capture(links, List.copyOf(observations.values()), pendingSnapshot);
    }

    private static void captureOnline(
            Player player, Instant now, Map<UUID, TransitionSnapshotPlanner.Observation> observations
    ) {
        observations.put(player.getUniqueId(), new TransitionSnapshotPlanner.Observation(
                player.getUniqueId(), player.getName(), now, true));
    }

    private void persist(Capture snapshot) {
        try {
            for (TransitionSnapshotPlanner.Observation observation
                    : TransitionSnapshotPlanner.plan(Map.of(), snapshot.observations()).observations()) {
                persistObservation(observation);
            }
            PlannedSnapshot planned = planPersistedLinks(snapshot);
            TransitionSnapshotPlanner.Plan plan = planned.plan();
            ImportReport report = migration.importSnapshot(new ReadOnlySnapshotProvider(plan.importableLinks()));
            offlineCursor = planned.nextCursor();
            retireAcknowledged(pending, snapshot.pendingSnapshot());
            plugin.getLogger().log(Level.INFO,
                    "enthusiastaff_transition_collector_pass observed={0} imported={1} unchanged={2} conflicts={3} skipped={4} deferred={5}",
                    new Object[]{snapshot.observations().size(), report.imported(), report.unchanged(),
                            report.conflicts().size(), plan.skippedLinks(), planned.deferred()});
        } catch (RuntimeException exception) {
            logFailure("enthusiastaff_transition_collector_pass_failed", exception);
        } finally {
            passInFlight.set(false);
        }
    }

    private PlannedSnapshot planPersistedLinks(Capture snapshot) {
        java.util.Set<UUID> onlineIds = snapshot.observations().stream()
                .filter(TransitionSnapshotPlanner.Observation::online)
                .map(TransitionSnapshotPlanner.Observation::playerId)
                .collect(java.util.stream.Collectors.toSet());
        TransitionSnapshotPlanner.LinkBatch batch = TransitionSnapshotPlanner.selectLinkBatch(
                snapshot.links(), offlineCursor, LINK_LOOKUPS_PER_PASS, onlineIds);
        List<TransitionSnapshotPlanner.Observation> observations = new ArrayList<>(snapshot.observations());
        for (UUID playerId : batch.links().values().stream().distinct().toList()) {
            if (onlineIds.contains(playerId)) {
                continue;
            }
            appendPersistedIdentity(playerId, observations);
        }
        return new PlannedSnapshot(
                TransitionSnapshotPlanner.plan(batch.links(), observations), batch.nextCursor(),
                snapshot.links().size() - batch.links().size());
    }

    private void appendPersistedIdentity(
            UUID playerId, List<TransitionSnapshotPlanner.Observation> observations
    ) {
        data.players().find(playerId.toString()).ifPresent(identity ->
                identity.currentUsername().ifPresent(name -> observations.add(
                        new TransitionSnapshotPlanner.Observation(
                                playerId, name, identity.lastSeenAt(), false))));
    }

    /** Compare-and-remove retains a newer quit/join event received while persistence was in flight. */
    static void retireAcknowledged(
            ConcurrentMap<UUID, TransitionSnapshotPlanner.Observation> pending,
            Map<UUID, TransitionSnapshotPlanner.Observation> acknowledged
    ) {
        acknowledged.forEach((playerId, event) -> pending.remove(playerId, event));
    }

    private void persistObservation(TransitionSnapshotPlanner.Observation observation) {
        data.players().recordSeen(observation.playerId(), observation.username(), PlayerPlatform.UNKNOWN,
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
        HandlerList.unregisterAll(this);
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

    private record PlannedSnapshot(TransitionSnapshotPlanner.Plan plan, int nextCursor, int deferred) { }

    private record Capture(
            Map<String, UUID> links,
            List<TransitionSnapshotPlanner.Observation> observations,
            Map<UUID, TransitionSnapshotPlanner.Observation> pendingSnapshot
    ) {
        private Capture {
            links = Map.copyOf(links);
            observations = List.copyOf(observations);
            pendingSnapshot = Map.copyOf(pendingSnapshot);
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
