package net.enthusia.staff.paper.aireview;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.logging.Level;
import net.enthusia.staff.domain.auth.StaffRank;
import net.enthusia.staff.paper.aireview.AiReviewModels.Correction;
import net.enthusia.staff.paper.aireview.AiReviewModels.CorrectionAuthority;
import net.enthusia.staff.paper.aireview.AiReviewModels.CorrectionDecision;
import net.enthusia.staff.paper.aireview.AiReviewModels.EventDetails;
import net.enthusia.staff.paper.aireview.AiReviewModels.ReviewItem;
import net.enthusia.staff.paper.aireview.AiReviewModels.ReviewPriority;
import net.enthusia.staff.paper.presentation.StaffMessageStyle;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

public final class AiReviewSubsystem implements AutoCloseable, Listener {
    private final JavaPlugin plugin;
    private final Clock clock;
    private final AiReviewConfiguration configuration;
    private final String disabledReason;
    private final ThreadPoolExecutor executor;
    private final AiReviewClient client;
    private final AiReviewPollState pollState;
    private final Predicate<UUID> activeDuty;
    private final Function<UUID, UUID> activeSession;
    private final Function<UUID, StaffRank> sessionRank;
    private final AiReviewBackoff backoff;
    private final Object pollLock = new Object();
    private final ArrayBlockingQueue<Runnable> pollWaiters;
    private final AtomicBoolean pollRunning = new AtomicBoolean();
    private final AtomicBoolean closed = new AtomicBoolean();
    private volatile ScheduledTask pollTask;
    private AiReviewGuiController gui;

    private AiReviewSubsystem(
            JavaPlugin plugin,
            Clock clock,
            AiReviewConfiguration configuration,
            String disabledReason,
            ThreadPoolExecutor executor,
            AiReviewClient client,
            AiReviewPollState pollState,
            Predicate<UUID> activeDuty,
            Function<UUID, UUID> activeSession,
            Function<UUID, StaffRank> sessionRank
    ) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.configuration = configuration;
        this.disabledReason = disabledReason;
        this.executor = executor;
        this.client = client;
        this.pollState = pollState;
        this.activeDuty = Objects.requireNonNull(activeDuty, "activeDuty");
        this.activeSession = Objects.requireNonNull(activeSession, "activeSession");
        this.sessionRank = Objects.requireNonNull(sessionRank, "sessionRank");
        long initialBackoffMillis = configuration == null
                ? 5_000L
                : Math.max(1_000L, Math.min(5_000L, configuration.pollInterval().toMillis()));
        this.backoff = new AiReviewBackoff(
                java.time.Duration.ofMillis(initialBackoffMillis),
                java.time.Duration.ofSeconds(60)
        );
        this.pollWaiters = new ArrayBlockingQueue<>(
                configuration == null ? 8 : Math.max(8, configuration.queueCapacity())
        );
    }

    public static AiReviewSubsystem create(
            JavaPlugin plugin,
            ObjectMapper json,
            Function<String, String> environment,
            Predicate<UUID> activeDuty,
            Function<UUID, UUID> activeSession,
            Function<UUID, StaffRank> sessionRank
    ) {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(json, "json");
        AiReviewConfiguration.LoadResult loaded = AiReviewConfiguration.load(
                plugin.getConfig().getConfigurationSection(AiReviewConfiguration.ROOT),
                environment
        );
        if (loaded.configuration().isEmpty()) {
            return new AiReviewSubsystem(
                    plugin,
                    Clock.systemUTC(),
                    null,
                    loaded.diagnostic(),
                    null,
                    null,
                    new AiReviewPollState(64),
                    activeDuty,
                    activeSession,
                    sessionRank
            );
        }
        AiReviewConfiguration configuration = loaded.configuration().orElseThrow();
        ThreadPoolExecutor executor = createExecutor(
                configuration.workerThreads(),
                configuration.queueCapacity()
        );
        return new AiReviewSubsystem(
                plugin,
                Clock.systemUTC(),
                configuration,
                null,
                executor,
                new AiReviewHttpClient(configuration, json),
                new AiReviewPollState(configuration.notifiedCacheSize()),
                activeDuty,
                activeSession,
                sessionRank
        );
    }

    public void start() {
        PluginCommand command = Objects.requireNonNull(
                plugin.getCommand("aireview"),
                "aireview command is missing from plugin.yml"
        );
        gui = new AiReviewGuiController(plugin, this);
        plugin.getServer().getPluginManager().registerEvents(gui, plugin);
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        AiReviewCommand handler = new AiReviewCommand(plugin, this, gui);
        command.setExecutor(handler);
        command.setTabCompleter(handler);
        if (!enabled()) {
            if (disabledReason != null && plugin.getLogger().isLoggable(Level.WARNING)) {
                plugin.getLogger().warning(disabledReason + "; AI review is disabled only");
            }
            return;
        }
        long periodTicks = Math.max(20L, configuration.pollInterval().toMillis() / 50L);
        pollTask = plugin.getServer().getGlobalRegionScheduler().runAtFixedRate(
                plugin,
                ignored -> refreshQueue(false, null),
                1L,
                periodTicks
        );
    }

    boolean enabled() {
        return configuration != null && client != null && executor != null && !closed.get();
    }

    String disabledReason() {
        return disabledReason == null ? "AI review is disabled." : disabledReason;
    }

    AiReviewConfiguration configuration() {
        if (configuration == null) {
            throw new IllegalStateException("AI review configuration is unavailable");
        }
        return configuration;
    }

    AiReviewPollState.Snapshot snapshot() {
        return pollState.snapshot();
    }

    boolean activeDuty(UUID playerId) {
        return playerId != null && activeDuty.test(playerId);
    }

    boolean activeDuty(Player player) {
        return player != null && player.isOnline() && activeDuty(player.getUniqueId());
    }

    void refreshQueue(boolean userRequested, Runnable completion) {
        if (!enabled()) {
            if (completion != null) {
                schedule(completion);
            }
            return;
        }

        Runnable overflow = null;
        boolean startPoll = false;
        synchronized (pollLock) {
            if (completion != null && !pollWaiters.offer(completion)) {
                overflow = completion;
            }
            if (!pollRunning.get()) {
                pollRunning.set(true);
                startPoll = true;
            }
        }

        if (overflow != null) {
            pollState.failure(
                    userRequested
                            ? "AI review refresh waiter queue is full"
                            : "AI review refresh completion queue is full",
                    clock.instant()
            );
            schedule(overflow);
        }
        if (!startPoll) {
            return;
        }

        submit(
                () -> client.listReviews(configuration.reviewLimit()),
                items -> {
                    AiReviewPollState.Update update = pollState.success(items, clock.instant());
                    notifyNewItems(update.newlyDiscovered());
                    finishPoll();
                },
                issue -> {
                    pollState.failure(issue, clock.instant());
                    finishPoll();
                }
        );
    }

    private void finishPoll() {
        List<Runnable> completions = new ArrayList<>();
        synchronized (pollLock) {
            pollRunning.set(false);
            pollWaiters.drainTo(completions);
        }
        completions.forEach(Runnable::run);
    }

    void loadEvent(
            String eventId,
            Consumer<EventDetails> success,
            Consumer<String> failure
    ) {
        submit(() -> client.event(eventId), success, failure);
    }

    void correct(
            String eventId,
            String reviewerId,
            CorrectionAuthority authority,
            CorrectionDecision corrected,
            String note,
            Consumer<Correction> success,
            Consumer<String> failure
    ) {
        submitWrite(
                UUID.fromString(reviewerId),
                authority,
                () -> client.correct(eventId, reviewerId, authority, corrected, note),
                success,
                failure
        );
    }

    void reject(
            String proposalId,
            String reviewerId,
            CorrectionAuthority authority,
            String note,
            Consumer<Correction> success,
            Consumer<String> failure
    ) {
        submitWrite(
                UUID.fromString(reviewerId),
                authority,
                () -> client.reject(proposalId, reviewerId, authority, note),
                success,
                failure
        );
    }

    private <T> void submitWrite(
            UUID reviewerId,
            CorrectionAuthority authority,
            Supplier<T> work,
            Consumer<T> success,
            Consumer<String> failure
    ) {
        UUID sessionId = activeSession.apply(reviewerId);
        StaffRank expectedRank = sessionRank.apply(reviewerId);
        if (sessionId == null
                || !activeDuty(reviewerId)
                || !allowsAuthority(expectedRank, authority)) {
            schedule(() -> failure.accept("active staff mode review authority is required"));
            return;
        }
        submit(() -> {
            StaffRank currentRank = sessionRank.apply(reviewerId);
            if (!activeDuty(reviewerId)
                    || !sessionId.equals(activeSession.apply(reviewerId))
                    || currentRank != expectedRank
                    || !allowsAuthority(currentRank, authority)) {
                throw new ReviewerDutyEndedException();
            }
            return work.get();
        }, success, failure);
    }

    private static boolean allowsAuthority(StaffRank rank, CorrectionAuthority authority) {
        if (rank == null || authority == null) {
            return false;
        }
        if (authority == CorrectionAuthority.ADMIN) {
            return rank == StaffRank.ADMIN || rank == StaffRank.FOUNDER;
        }
        return rank == StaffRank.MOD || rank == StaffRank.ADMIN || rank == StaffRank.FOUNDER;
    }

    <T> void submit(Supplier<T> work, Consumer<T> success, Consumer<String> failure) {
        if (!enabled()) {
            schedule(() -> failure.accept(disabledReason()));
            return;
        }
        java.time.Duration remaining = backoff.remaining(clock.instant());
        if (!remaining.isZero()) {
            long seconds = Math.max(1L, (remaining.toMillis() + 999L) / 1_000L);
            schedule(() -> failure.accept("central review backing off for " + seconds + "s"));
            return;
        }
        try {
            executor.execute(() -> {
                try {
                    T result = work.get();
                    backoff.success();
                    schedule(() -> {
                        if (!closed.get()) {
                            success.accept(result);
                        }
                    });
                } catch (AiReviewClientException exception) {
                    if (exception.category() == AiReviewClientException.Category.CONFLICT) {
                        backoff.success();
                        schedule(() -> {
                            if (!closed.get()) {
                                failure.accept("central review conflict");
                            }
                        });
                    } else {
                        java.time.Duration delay = backoff.failure(clock.instant());
                        String issue = "central review "
                                + exception.category().name().toLowerCase(java.util.Locale.ROOT)
                                + "; retry backoff " + Math.max(1L, delay.toSeconds()) + "s";
                        schedule(() -> {
                            if (!closed.get()) {
                                failure.accept(issue);
                            }
                        });
                    }
                } catch (ReviewerDutyEndedException exception) {
                    backoff.success();
                    schedule(() -> {
                        if (!closed.get()) {
                            failure.accept("active staff mode session or rank changed; no review write was sent");
                        }
                    });
                } catch (RuntimeException exception) {
                    java.time.Duration delay = backoff.failure(clock.instant());
                    plugin.getLogger().log(Level.WARNING, "AI review operation failed", exception);
                    schedule(() -> {
                        if (!closed.get()) {
                            failure.accept(
                                    "central review internal error; retry backoff "
                                            + Math.max(1L, delay.toSeconds()) + "s"
                            );
                        }
                    });
                }
            });
        } catch (RejectedExecutionException exception) {
            schedule(() -> failure.accept("AI review work queue is full"));
        }
    }

    void schedule(Runnable action) {
        if (closed.get()) {
            return;
        }
        plugin.getServer().getGlobalRegionScheduler().execute(plugin, () -> {
            if (!closed.get()) {
                action.run();
            }
        });
    }

    private void notifyNewItems(List<ReviewItem> items) {
        if (items.isEmpty()) {
            return;
        }
        int urgent = (int) items.stream()
                .filter(item -> item.reviewPriority() == ReviewPriority.URGENT)
                .count();
        Component message = Component.text(
                "AI review: " + items.size() + " new item(s)"
                        + (urgent == 0 ? "" : " (" + urgent + " urgent)")
                        + ". Use /aireview.",
                urgent > 0 ? NamedTextColor.RED : NamedTextColor.GOLD
        );
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (!activeDuty(player)
                    || !AiReviewPermissions.queue(player)
                    || !player.hasPermission(configuration.notificationPermission())) {
                continue;
            }
            player.getScheduler().execute(plugin, () -> {
                if (activeDuty(player)
                        && AiReviewPermissions.queue(player)
                        && player.hasPermission(configuration.notificationPermission())) {
                    player.sendMessage(StaffMessageStyle.style(message));
                }
            }, null, 1L);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        if (!enabled()
                || !activeDuty(event.getPlayer())
                || !AiReviewPermissions.queue(event.getPlayer())
                || !event.getPlayer().hasPermission(configuration.notificationPermission())) {
            return;
        }
        Player player = event.getPlayer();
        AiReviewPollState.Snapshot current = snapshot();
        Instant now = clock.instant();
        if (current.fresh(now, configuration.cacheStaleAfter())) {
            player.sendMessage(StaffMessageStyle.style(Component.text(
                    "AI review queue: " + current.items().size() + " pending"
                            + (current.urgentCount() == 0 ? "" : ", " + current.urgentCount() + " urgent")
                            + ".",
                    current.urgentCount() > 0 ? NamedTextColor.RED : NamedTextColor.GOLD
            )));
            return;
        }
        player.sendMessage(StaffMessageStyle.style(Component.text(
                "AI review queue status is refreshing; normal Staff features are unaffected.",
                NamedTextColor.GRAY
        )));
        refreshQueue(false, null);
    }

    static ThreadPoolExecutor createExecutor(int threads, int queueCapacity) {
        AtomicInteger sequence = new AtomicInteger();
        ThreadFactory factory = runnable -> {
            Thread thread = new Thread(
                    runnable,
                    "EnthusiaStaff-AIReview-" + sequence.incrementAndGet()
            );
            thread.setDaemon(true);
            return thread;
        };
        return new ThreadPoolExecutor(
                threads,
                threads,
                0L,
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(queueCapacity),
                factory,
                new ThreadPoolExecutor.AbortPolicy()
        );
    }

    private static final class ReviewerDutyEndedException extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        ScheduledTask task = pollTask;
        if (task != null) {
            try {
                task.cancel();
            } catch (RuntimeException exception) {
                plugin.getLogger().log(Level.FINE, "AI review poll cancellation failed", exception);
            }
        }
        synchronized (pollLock) {
            pollRunning.set(false);
            pollWaiters.clear();
        }
        if (executor == null) {
            return;
        }
        executor.shutdownNow();
        try {
            if (!executor.awaitTermination(2, TimeUnit.SECONDS)) {
                plugin.getLogger().warning("AI review workers did not stop within the shutdown deadline");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }
}
