package net.enthusia.staff.paper.enforcement;

import io.papermc.paper.event.player.AsyncChatEvent;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.logging.Level;
import net.enthusia.staff.domain.OperationalMode;
import net.enthusia.staff.domain.player.PlayerPlatform;
import net.enthusia.staff.domain.ports.PlayerDirectory;
import net.enthusia.staff.domain.ports.SanctionLookup;
import net.enthusia.staff.domain.sanction.ActiveSanction;
import net.enthusia.staff.domain.sanction.SanctionType;
import net.enthusia.staff.paper.client.PaperPlayerPlatformResolver;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

public final class MuteEnforcementListener implements Listener, AutoCloseable {
    private static final Duration CACHE_TTL = Duration.ofSeconds(45);
    private static final Set<SanctionType> MUTE_TYPES = Set.of(SanctionType.MUTE);

    private final JavaPlugin plugin;
    private final Clock clock;
    private final String serverId;
    private final Supplier<OperationalMode> mode;
    private final Supplier<SanctionLookup> sanctions;
    private final Supplier<PlayerDirectory> players;
    private final ExecutorService workers;
    private final PaperPlayerPlatformResolver platforms;
    private final ConcurrentHashMap<UUID, Entry> cache = new ConcurrentHashMap<>();
    private ScheduledTask refreshTask;

    public MuteEnforcementListener(
            JavaPlugin plugin,
            Clock clock,
            String serverId,
            Supplier<OperationalMode> mode,
            Supplier<SanctionLookup> sanctions,
            Supplier<PlayerDirectory> players,
            ExecutorService workers
    ) {
        this.plugin = plugin;
        this.clock = clock;
        this.serverId = serverId;
        this.mode = mode;
        this.sanctions = sanctions;
        this.players = players;
        this.workers = workers;
        this.platforms = PaperPlayerPlatformResolver.discover(plugin);
    }

    public void start() {
        refreshTask = plugin.getServer().getAsyncScheduler().runAtFixedRate(
                plugin,
                ignored -> plugin.getServer().getOnlinePlayers().forEach(this::refresh),
                5,
                15,
                TimeUnit.SECONDS
        );
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        String username = player.getName();
        PlayerPlatform platform = platforms.resolve(playerId);
        Instant seenAt = clock.instant();
        submit(() -> {
            PlayerDirectory directory = players.get();
            if (directory != null) {
                directory.recordSeenVerified(playerId, username, platform, serverId, seenAt);
            }
            refreshNow(playerId);
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        cache.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPaperChat(AsyncChatEvent event) {
        enforce(event.getPlayer(), event::setCancelled);
    }

    @SuppressWarnings("deprecation")
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onLegacyChat(AsyncPlayerChatEvent event) {
        enforce(event.getPlayer(), event::setCancelled);
    }

    private void enforce(Player player, java.util.function.Consumer<Boolean> cancellation) {
        if (mode.get() != OperationalMode.ACTIVE) {
            return;
        }
        Entry entry = cache.get(player.getUniqueId());
        if (entry == null || entry.validUntil().isBefore(clock.instant())) {
            cancellation.accept(true);
            notifyPlayer(player, "Your moderation status is still being verified. Please try again shortly.");
            refresh(player);
            return;
        }
        if (!entry.active().isEmpty()) {
            cancellation.accept(true);
            ActiveSanction mute = entry.active().getFirst();
            String expiration = mute.expiresAt().map(Instant::toString).orElse("permanent");
            notifyPlayer(player, "You are muted (case " + mute.caseId() + ", expires " + expiration + ").");
        }
    }

    private void refresh(Player player) {
        refresh(player.getUniqueId());
    }

    public void invalidate(UUID playerId) {
        cache.remove(playerId);
        refresh(playerId);
    }

    public CachedMuteStatus cachedStatus(UUID playerId) {
        if (playerId == null) {
            throw new IllegalArgumentException("playerId must be present");
        }
        if (mode.get() != OperationalMode.ACTIVE) {
            return CachedMuteStatus.CLEAR;
        }
        Entry entry = cache.get(playerId);
        if (entry == null || !entry.validUntil().isAfter(clock.instant())) {
            return CachedMuteStatus.UNVERIFIED;
        }
        return entry.active().isEmpty() ? CachedMuteStatus.CLEAR : CachedMuteStatus.MUTED;
    }

    private void refresh(UUID playerId) {
        submit(() -> refreshNow(playerId));
    }

    private void refreshNow(UUID playerId) {
        SanctionLookup lookup = sanctions.get();
        if (lookup == null) {
            cache.remove(playerId);
            return;
        }
        try {
            Instant now = clock.instant();
            cache.put(playerId, new Entry(lookup.activeFor(playerId, MUTE_TYPES, now), now.plus(CACHE_TTL)));
        } catch (RuntimeException exception) {
            cache.remove(playerId);
            plugin.getLogger().log(Level.SEVERE, "Authoritative mute lookup failed; chat remains fail-closed", exception);
        }
    }

    private void submit(Runnable action) {
        try {
            workers.execute(action);
        } catch (RejectedExecutionException exception) {
            plugin.getLogger().warning("Mute refresh skipped because the bounded work queue is full");
        }
    }

    private void notifyPlayer(Player player, String message) {
        plugin.getServer().getGlobalRegionScheduler().execute(plugin, () -> player.sendMessage(Component.text(message)));
    }

    @Override
    public void close() {
        if (refreshTask != null) {
            refreshTask.cancel();
        }
        cache.clear();
    }

    private record Entry(List<ActiveSanction> active, Instant validUntil) {
        private Entry {
            active = List.copyOf(active);
        }
    }

    public enum CachedMuteStatus {
        CLEAR,
        MUTED,
        UNVERIFIED
    }
}
