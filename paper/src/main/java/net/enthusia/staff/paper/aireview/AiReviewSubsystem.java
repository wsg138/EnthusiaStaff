package net.enthusia.staff.paper.aireview;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Level;
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
            AiReviewPollState pollState
    ) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.configuration = configuration;
        this.disabledReason = disabledReason;
        this.executor = executor;
        this.client = client;
        this.pollState = pollState;
    }

    public static AiReviewSubsystem create(
            JavaPlugin plugin,
            ObjectMapper json,
            Function<String, String> environment
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
                    new AiReviewPollState(64)
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
                new AiReviewPollState(configuration.notifiedCacheSize())
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
        AiReviewCommand handler = new AiReviewCommand(this, gui);
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

    void refreshQueue(boolean userRequested, Runnable completion) {
        if (!enabled() || !pollRunning.compareAndSet(false, true)) {
            if (userRequested && completion != null) {
                schedule(completion);
            }
            return;
        }
        submit(
                () -> client.listReviews(configuration.reviewLimit()),
                items -> {
                    pollRunning.set(false);
                    AiReviewPollState.Update update = pollState.success(items, clock.instant());
                    notifyNewItems(update.newlyDiscovered());
                    if (completion != null) {
                        completion.run();
                    }
                },
                issue -> {
                    pollRunning.set(false);
                    pollState.failure(issue, clock.instant());
                    if (completion != null) {
                        completion.run();
                    }
                }
        );
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
        submit(
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
        submit(
                () -> client.reject(proposalId, reviewerId, authority, note),
                success,
                failure
        );
    }

    <T> void submit(Supplier<T> work, Consumer<T> success, Consumer<String> failure) {
        if (!enabled()) {
            schedule(() -> failure.accept(disabledReason()));
            return;
        }
        try {
            executor.execute(() -> {
                try {
                    T result = work.get();
                    schedule(() -> {
                        if (!closed.get()) {
                            success.accept(result);
                        }
                    });
                } catch (AiReviewClientException exception) {
                    String issue = "central review " + exception.category().name().toLowerCase(java.util.Locale.ROOT);
                    schedule(() -> {
                        if (!closed.get()) {
                            failure.accept(issue);
                        }
                    });
                } catch (RuntimeException exception) {
                    plugin.getLogger().log(Level.WARNING, "AI review operation failed", exception);
                    schedule(() -> {
                        if (!closed.get()) {
                            failure.accept("central review internal error");
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
            if (AiReviewPermissions.queue(player)) {
                player.sendMessage(StaffMessageStyle.style(message));
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        if (!enabled() || !AiReviewPermissions.queue(event.getPlayer())) {
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

    private static ThreadPoolExecutor createExecutor(int threads, int queueCapacity) {
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
