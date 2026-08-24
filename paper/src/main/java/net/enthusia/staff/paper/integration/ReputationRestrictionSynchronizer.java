package net.enthusia.staff.paper.integration;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.logging.Level;
import net.enthusia.staff.domain.application.PunishmentPlan;
import net.enthusia.staff.domain.application.PunishmentService;
import net.enthusia.staff.domain.evidence.IntegrationAvailability;
import net.enthusia.staff.domain.ports.SanctionLookup;
import net.enthusia.staff.domain.sanction.ActiveSanction;
import net.enthusia.staff.domain.sanction.SanctionType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.enthusia.rep.api.ReputationBlacklist;
import org.enthusia.rep.api.ReputationMutationResult;

/**
 * Projects the authoritative EnthusiaStaff REPUTATION_BLACKLIST sanction into EnthusiaCommend.
 *
 * <p>The durable sanction remains authoritative. Post-commit notification makes new restrictions
 * prompt, while join and periodic reconciliation recover missed notifications, restarts, stale
 * provider revisions, and sanction revocations without relying on command-path side effects.</p>
 */
public final class ReputationRestrictionSynchronizer implements Listener, AutoCloseable {
    private static final Set<SanctionType> REPUTATION_TYPES = Set.of(SanctionType.REPUTATION_BLACKLIST);
    private static final long RECONCILE_SECONDS = 10L;

    private final JavaPlugin plugin;
    private final Clock clock;
    private final ReputationIntegration reputation;
    private final Supplier<PunishmentService> punishmentService;
    private final Supplier<SanctionLookup> sanctionLookup;
    private final ExecutorService workers;
    private final Set<UUID> tracked = ConcurrentHashMap.newKeySet();
    private final Set<UUID> inFlight = ConcurrentHashMap.newKeySet();
    private volatile PunishmentService observedService;
    private volatile ScheduledTask task;
    private volatile boolean closed;

    public ReputationRestrictionSynchronizer(
            JavaPlugin plugin,
            Clock clock,
            ReputationIntegration reputation,
            Supplier<PunishmentService> punishmentService,
            Supplier<SanctionLookup> sanctionLookup,
            ExecutorService workers
    ) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.reputation = Objects.requireNonNull(reputation, "reputation");
        this.punishmentService = Objects.requireNonNull(punishmentService, "punishmentService");
        this.sanctionLookup = Objects.requireNonNull(sanctionLookup, "sanctionLookup");
        this.workers = Objects.requireNonNull(workers, "workers");
        if (reputation.availability() != IntegrationAvailability.AVAILABLE) {
            throw new IllegalArgumentException("reputation provider must be available before reconciliation starts");
        }
    }

    public void start() {
        if (closed || task != null) {
            return;
        }
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        plugin.getServer().getOnlinePlayers().forEach(player -> trigger(player.getUniqueId()));
        ensureObserverInstalled();
        task = plugin.getServer().getAsyncScheduler().runAtFixedRate(
                plugin,
                ignored -> tick(),
                1L,
                RECONCILE_SECONDS,
                TimeUnit.SECONDS
        );
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        trigger(event.getPlayer().getUniqueId());
    }

    void onPunishmentCommitted(PunishmentPlan plan) {
        if (plan == null || plan.sanctions().stream().noneMatch(spec -> spec.type() == SanctionType.REPUTATION_BLACKLIST)) {
            return;
        }
        trigger(plan.targetId());
    }

    void reconcileNow(UUID playerId) {
        reconcile(Objects.requireNonNull(playerId, "playerId"));
    }

    private void tick() {
        if (closed) {
            return;
        }
        ensureObserverInstalled();
        tracked.forEach(this::submit);
    }

    private void ensureObserverInstalled() {
        if (closed) {
            return;
        }
        try {
            PunishmentService current = punishmentService.get();
            if (current == null || current == observedService) {
                return;
            }
            PunishmentService previous = observedService;
            if (previous != null) {
                previous.clearCommittedObserver();
            }
            current.setCommittedObserver(this::onPunishmentCommitted);
            observedService = current;
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.FINE, "Reputation post-commit observer is not ready yet", exception);
        }
    }

    private void trigger(UUID playerId) {
        if (closed || playerId == null) {
            return;
        }
        tracked.add(playerId);
        submit(playerId);
    }

    private void submit(UUID playerId) {
        if (closed || !inFlight.add(playerId)) {
            return;
        }
        try {
            workers.execute(() -> {
                try {
                    reconcile(playerId);
                } finally {
                    inFlight.remove(playerId);
                }
            });
        } catch (RejectedExecutionException exception) {
            inFlight.remove(playerId);
        }
    }

    private void reconcile(UUID playerId) {
        if (closed) {
            return;
        }
        SanctionLookup lookup;
        try {
            lookup = sanctionLookup.get();
        } catch (RuntimeException exception) {
            logRetryable("Authoritative sanction lookup is unavailable", exception);
            return;
        }
        if (lookup == null) {
            return;
        }
        try {
            Instant now = clock.instant();
            ActiveSanction authoritative = lookup.activeFor(playerId, REPUTATION_TYPES, now).stream()
                    .max(Comparator.comparing(ActiveSanction::issuedAt)
                            .thenComparing(value -> value.sanctionId().toString()))
                    .orElse(null);
            Optional<ReputationBlacklist> current = reputation.blacklist(playerId);
            if (authoritative != null) {
                reconcileActive(playerId, authoritative, current);
                return;
            }
            if (current.isPresent() && current.orElseThrow().status() == ReputationBlacklist.Status.ACTIVE) {
                removeStaleRestriction(playerId, current.orElseThrow());
                return;
            }
            tracked.remove(playerId);
        } catch (RuntimeException exception) {
            logRetryable("Reputation restriction reconciliation failed for " + playerId, exception);
        }
    }

    private void reconcileActive(
            UUID playerId,
            ActiveSanction authoritative,
            Optional<ReputationBlacklist> current
    ) {
        String caseId = authoritative.caseId().toString();
        Optional<Instant> expiration = authoritative.expiresAt();
        if (current.isPresent()) {
            ReputationBlacklist value = current.orElseThrow();
            if (value.status() == ReputationBlacklist.Status.ACTIVE
                    && value.caseId().equals(caseId)
                    && value.expirationAt().equals(expiration)) {
                return;
            }
        }
        long expectedRevision = current.map(ReputationBlacklist::revision).orElse(0L);
        UUID operationId = operationId(
                "apply", playerId, caseId, expiration.map(Instant::toString).orElse("permanent"), expectedRevision);
        ReputationMutationResult result = reputation.reconcileApply(
                operationId,
                playerId,
                expiration,
                caseId,
                expectedRevision
        );
        verifyResult(result, "apply", playerId);
    }

    private void removeStaleRestriction(UUID playerId, ReputationBlacklist current) {
        UUID operationId = operationId("remove", playerId, current.caseId(), "none", current.revision());
        ReputationMutationResult result = reputation.reconcileRemove(
                operationId,
                playerId,
                current.caseId(),
                current.revision()
        );
        verifyResult(result, "remove", playerId);
        if (result.success()) {
            tracked.remove(playerId);
        }
    }

    private void verifyResult(ReputationMutationResult result, String operation, UUID playerId) {
        if (!result.success()) {
            plugin.getLogger().warning("Reputation blacklist " + operation + " for " + playerId
                    + " will retry after provider response " + result.status() + ": " + result.detail());
            return;
        }
        if (!result.before().equals(result.after())) {
            plugin.getLogger().severe("Reputation provider changed score/category state while performing blacklist "
                    + operation + " for " + playerId + "; provider state requires review");
        }
    }

    private static UUID operationId(String action, UUID playerId, String caseId, String expiration, long revision) {
        String value = "enthusiastaff:reputation:" + action + '|' + playerId + '|' + caseId + '|'
                + expiration + '|' + revision;
        return UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8));
    }

    private void logRetryable(String message, RuntimeException exception) {
        plugin.getLogger().log(Level.WARNING, message + "; reconciliation will retry", exception);
    }

    @Override
    public void close() {
        closed = true;
        ScheduledTask currentTask = task;
        task = null;
        if (currentTask != null) {
            currentTask.cancel();
        }
        PunishmentService currentService = observedService;
        observedService = null;
        if (currentService != null) {
            currentService.clearCommittedObserver();
        }
        HandlerList.unregisterAll(this);
        tracked.clear();
        inFlight.clear();
    }
}
