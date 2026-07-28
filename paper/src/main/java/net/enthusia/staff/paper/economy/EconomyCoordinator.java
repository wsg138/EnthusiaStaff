package net.enthusia.staff.paper.economy;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import java.time.Clock;
import java.time.Duration;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import java.util.logging.Level;
import net.enthusia.staff.common.IdempotencyKey;
import net.enthusia.staff.domain.OperationalMode;
import net.enthusia.staff.domain.auth.Actor;
import net.enthusia.staff.domain.auth.AuthorizationPolicy;
import net.enthusia.staff.domain.auth.ModerationAction;
import net.enthusia.staff.domain.economy.EconomyAmountMode;
import net.enthusia.staff.domain.economy.EconomyJournalResult;
import net.enthusia.staff.domain.economy.EconomyOperation;
import net.enthusia.staff.domain.economy.EconomyOperationState;
import net.enthusia.staff.domain.economy.EconomyPreparation;
import net.enthusia.staff.domain.economy.EconomyPrepareRequest;
import net.enthusia.staff.domain.economy.EconomyReconciliationDecision;
import net.enthusia.staff.domain.economy.EconomyTerminalUpdate;
import net.enthusia.staff.domain.economy.EconomyValidatedPlan;
import net.enthusia.staff.domain.ports.EconomyJournalStore;
import net.enthusia.staff.paper.auth.PaperActorResolver;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

public final class EconomyCoordinator implements Listener, AutoCloseable {
    private static final Duration LEASE_DURATION = Duration.ofMinutes(2);
    private static final long RENEWAL_PERIOD_TICKS = 20L * 30L;

    private final JavaPlugin plugin;
    private final Clock clock;
    private final String serverId;
    private final Supplier<OperationalMode> mode;
    private final AuthorizationPolicy authorization;
    private final Supplier<EconomyJournalStore> store;
    private final ExecutorService workers;
    private final CurrencyGateway currency;
    private final List<CurrencyAssetSource> sourceOrder;
    private final CurrencyJournalCodec codec;
    private final Map<UUID, EconomyOperation> preloadedRecovery = new ConcurrentHashMap<>();
    private final Map<UUID, LeaseGuard> leaseGuards = new ConcurrentHashMap<>();
    private final AtomicBoolean closed = new AtomicBoolean();

    public EconomyCoordinator(
            JavaPlugin plugin,
            Clock clock,
            String serverId,
            Supplier<OperationalMode> mode,
            AuthorizationPolicy authorization,
            Supplier<EconomyJournalStore> store,
            ExecutorService workers,
            CurrencyGateway currency,
            List<CurrencyAssetSource> sourceOrder,
            ObjectMapper json
    ) {
        this.plugin = java.util.Objects.requireNonNull(plugin, "plugin");
        this.clock = java.util.Objects.requireNonNull(clock, "clock");
        this.serverId = requireIdentifier(serverId);
        this.mode = java.util.Objects.requireNonNull(mode, "mode");
        this.authorization = java.util.Objects.requireNonNull(authorization, "authorization");
        this.store = java.util.Objects.requireNonNull(store, "store");
        this.workers = java.util.Objects.requireNonNull(workers, "workers");
        this.currency = java.util.Objects.requireNonNull(currency, "currency");
        this.sourceOrder = validateSourceOrder(sourceOrder);
        this.codec = new CurrencyJournalCodec(json);
    }

    public void confiscate(
            Player actor,
            Player target,
            String caseId,
            OptionalLong requestedAmount
    ) {
        if (actor == null || target == null || requestedAmount == null) {
            throw new IllegalArgumentException("actor, target, and requestedAmount must be present");
        }
        if (!authorize(actor, ModerationAction.APPLY_CASE_CONFISCATION)) {
            message(actor, "You do not have case confiscation authority.");
            return;
        }
        if (mode.get() != OperationalMode.ACTIVE) {
            message(actor, "Economy confiscation is available only while moderation is ACTIVE.");
            return;
        }
        if (!target.isOnline()) {
            message(actor, "Economy confiscation requires the target to be online on this backend.");
            return;
        }
        EconomyAmountMode amountMode = requestedAmount.isPresent()
                ? EconomyAmountMode.CUSTOM
                : EconomyAmountMode.ALL;
        UUID operationId = UUID.randomUUID();
        EconomyPrepareRequest request;
        try {
            request = new EconomyPrepareRequest(
                    operationId,
                    new IdempotencyKey("economy:confiscate:" + operationId).value(),
                    caseId,
                    target.getUniqueId(),
                    actor.getUniqueId(),
                    amountMode,
                    requestedAmount,
                    serverId,
                    clock.instant()
            );
        } catch (IllegalArgumentException exception) {
            message(actor, "Economy confiscation request is invalid: " + exception.getMessage());
            return;
        }
        if (!submit(() -> prepare(actor, target, request))) {
            message(actor, "Economy confiscation queue is full; no operation was created.");
        }
    }

    public boolean isLocked(UUID playerId) {
        return playerId != null && currency.isMovementLocked(playerId);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        EconomyJournalStore loaded = store.get();
        if (loaded == null) {
            if (mode.get() == OperationalMode.ACTIVE) {
                event.disallow(
                        AsyncPlayerPreLoginEvent.Result.KICK_OTHER,
                        Component.text("Economy safety verification is temporarily unavailable. Please retry.")
                );
            }
            return;
        }
        try {
            List<EconomyOperation> operations = loaded.recoverableForTarget(
                    event.getUniqueId(),
                    serverId,
                    2
            );
            if (operations.size() > 1) {
                event.disallow(
                        AsyncPlayerPreLoginEvent.Result.KICK_OTHER,
                        Component.text("Multiple economy recovery operations require staff review.")
                );
                return;
            }
            if (operations.isEmpty()) {
                return;
            }
            EconomyOperation operation = operations.getFirst();
            if (operation.state() == EconomyOperationState.QUARANTINED) {
                event.disallow(
                        AsyncPlayerPreLoginEvent.Result.KICK_OTHER,
                        Component.text("Your economy state is protected pending staff recovery.")
                );
                return;
            }
            preloadedRecovery.put(event.getUniqueId(), operation);
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.SEVERE, "Economy pre-login recovery lookup failed", exception);
            if (mode.get() == OperationalMode.ACTIVE) {
                event.disallow(
                        AsyncPlayerPreLoginEvent.Result.KICK_OTHER,
                        Component.text("Economy safety verification failed. Please retry.")
                );
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        EconomyOperation operation = preloadedRecovery.remove(player.getUniqueId());
        if (operation == null) {
            return;
        }
        if (!currency.acquireMovementLock(
                player.getUniqueId(),
                operation.operationId(),
                LEASE_DURATION
        )) {
            player.kick(Component.text("Economy recovery could not acquire its local safety lock."));
            alertStaff("Economy recovery could not lock " + player.getName() + " on join.");
            return;
        }
        player.closeInventory();
        if (!submit(() -> reclaimForRecovery(player, operation))) {
            currency.releaseMovementLock(player.getUniqueId(), operation.operationId());
            player.kick(Component.text("Economy recovery queue is full. Please retry."));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        preloadedRecovery.remove(event.getPlayer().getUniqueId());
        leaseGuards.values().stream()
                .filter(guard -> guard.targetId().equals(event.getPlayer().getUniqueId()))
                .toList()
                .forEach(LeaseGuard::close);
    }

    private void prepare(Player actor, Player target, EconomyPrepareRequest request) {
        EconomyJournalStore loaded = store.get();
        if (loaded == null) {
            message(actor, "Economy storage is unavailable; no assets changed.");
            return;
        }
        try {
            EconomyPreparation preparation = loaded.prepare(
                    request,
                    LEASE_DURATION,
                    clock.instant()
            );
            EconomyOperation operation = preparation.operation().orElse(null);
            if (operation == null) {
                message(actor, "Economy confiscation rejected: " + preparation.detail());
                return;
            }
            if (preparation.status() == EconomyPreparation.Status.REPLAYED) {
                message(actor, "That exact economy operation already exists; recovery will finish it safely.");
                return;
            }
            onEntity(
                    target,
                    () -> lockAndSnapshot(actor, target, request, operation),
                    () -> rollBackBeforeLocalLock(
                            actor,
                            operation,
                            "TARGET_LEFT_BEFORE_LOCK",
                            "The target left before the local currency lock was acquired"
                    )
            );
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.SEVERE, "Economy operation preparation failed", exception);
            message(actor, "Economy confiscation failed before any assets changed.");
        }
    }

    private void lockAndSnapshot(
            Player actor,
            Player target,
            EconomyPrepareRequest request,
            EconomyOperation operation
    ) {
        if (!target.isOnline() || !currency.acquireMovementLock(
                target.getUniqueId(),
                operation.operationId(),
                LEASE_DURATION
        )) {
            rollBackBeforeLocalLock(
                    actor,
                    operation,
                    "LOCAL_LOCK_REJECTED",
                    "EnthusiaCurrency rejected the local movement lock"
            );
            return;
        }
        target.closeInventory();
        LeaseGuard guard = startGuard(operation, target.getUniqueId());
        try {
            CurrencyAccountState before = currency.snapshot(target);
            if (!before.playerId().equals(operation.targetId())) {
                quarantine(
                        actor,
                        target,
                        operation,
                        guard,
                        Optional.of(before),
                        "SNAPSHOT_IDENTITY_MISMATCH",
                        "EnthusiaCurrency returned a snapshot for another player"
                );
                return;
            }
            long amount = request.requestedAmount().orElse(before.authoritativeTotal());
            if (amount <= 0L) {
                rollBackVerified(
                        actor,
                        target,
                        operation,
                        guard,
                        before,
                        "NOTHING_TO_CONFISCATE",
                        "The target has no personal currency to confiscate"
                );
                return;
            }
            if (amount > before.authoritativeTotal()) {
                rollBackVerified(
                        actor,
                        target,
                        operation,
                        guard,
                        before,
                        "AMOUNT_EXCEEDS_TOTAL",
                        "Requested amount exceeds the authoritative personal total"
                );
                return;
            }
            CurrencyRemovalPlanState plan = currency.planRemoval(
                    operation.operationId(),
                    before,
                    amount,
                    sourceOrder
            );
            if (!submit(() -> persistPlan(actor, target, operation, guard, before, plan))) {
                quarantine(
                        actor,
                        target,
                        operation,
                        guard,
                        Optional.of(before),
                        "WORK_QUEUE_REJECTED",
                        "The exact plan could not enter the durable work queue"
                );
            }
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.SEVERE, "Currency snapshot or planning failed", exception);
            quarantine(
                    actor,
                    target,
                    operation,
                    guard,
                    Optional.empty(),
                    "CURRENCY_PLAN_FAILED",
                    "EnthusiaCurrency could not produce an exact removal plan"
            );
        }
    }

    private void persistPlan(
            Player actor,
            Player target,
            EconomyOperation operation,
            LeaseGuard guard,
            CurrencyAccountState before,
            CurrencyRemovalPlanState plan
    ) {
        EconomyJournalStore loaded = store.get();
        if (loaded == null) {
            quarantine(
                    actor,
                    target,
                    operation,
                    guard,
                    Optional.of(before),
                    "STORAGE_UNAVAILABLE",
                    "Economy storage became unavailable before apply"
            );
            return;
        }
        try {
            EconomyValidatedPlan durablePlan = new EconomyValidatedPlan(
                    before.authoritativeTotal(),
                    plan.amount(),
                    before.checksum(),
                    plan.replacementChecksum(),
                    codec.snapshot(before),
                    codec.plan(plan)
            );
            EconomyJournalResult saved = loaded.saveValidatedPlan(
                    operation.operationId(),
                    operation.fencingToken(),
                    durablePlan,
                    clock.instant()
            );
            if (!saved.successful()) {
                rollBackVerified(
                        actor,
                        target,
                        operation,
                        guard,
                        before,
                        "PLAN_JOURNAL_REJECTED",
                        saved.detail()
                );
                return;
            }
            if (saved.status() == EconomyJournalResult.Status.REPLAYED) {
                message(actor, "That exact economy plan is already durable; recovery will finish it safely.");
                return;
            }
            EconomyJournalResult applying = loaded.markApplying(
                    operation.operationId(),
                    operation.fencingToken(),
                    clock.instant()
            );
            if (!applying.successful()) {
                rollBackVerified(
                        actor,
                        target,
                        operation,
                        guard,
                        before,
                        "APPLY_FENCE_REJECTED",
                        applying.detail()
                );
                return;
            }
            if (applying.status() == EconomyJournalResult.Status.REPLAYED) {
                message(actor, "That economy operation is already applying; it was not executed again.");
                return;
            }
            EconomyOperation current = applying.operation().orElse(operation);
            onEntity(
                    target,
                    () -> apply(actor, target, current, guard, before, plan),
                    () -> finishOutcome(
                            actor,
                            target,
                            current,
                            guard,
                            EconomyTerminalUpdate.rolledBack(
                                    OptionalLong.empty(),
                                    Optional.empty(),
                                    Optional.empty(),
                                    "TARGET_LEFT_BEFORE_APPLY",
                                    "The target left before the exact removal plan was applied"
                            ),
                            "Economy confiscation was cancelled before assets changed."
                    )
            );
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.SEVERE, "Economy plan journaling failed", exception);
            quarantine(
                    actor,
                    target,
                    operation,
                    guard,
                    Optional.of(before),
                    "PLAN_JOURNAL_FAILED",
                    "The before snapshot or exact plan could not be committed"
            );
        }
    }

    private void apply(
            Player actor,
            Player target,
            EconomyOperation operation,
            LeaseGuard guard,
            CurrencyAccountState before,
            CurrencyRemovalPlanState plan
    ) {
        if (guard.lost() || !currency.renewMovementLock(
                target.getUniqueId(),
                operation.operationId(),
                LEASE_DURATION
        )) {
            quarantine(
                    actor,
                    target,
                    operation,
                    guard,
                    Optional.of(before),
                    "LOCAL_LEASE_LOST",
                    "The local Currency movement lease was lost before apply"
            );
            return;
        }
        try {
            currency.applyRemoval(target, plan).whenComplete((outcome, failure) -> {
                Runnable completion = () -> finishProviderOutcome(
                        actor,
                        target,
                        operation,
                        guard,
                        before,
                        plan,
                        outcome,
                        failure
                );
                if (!submit(completion)) {
                    alertStaff(
                            "Economy operation " + operation.operationId()
                                    + " completed in Currency but its journal queue is full; recovery is required."
                    );
                }
            });
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.SEVERE, "Currency apply invocation failed", exception);
            quarantine(
                    actor,
                    target,
                    operation,
                    guard,
                    Optional.empty(),
                    "CURRENCY_APPLY_THREW",
                    "EnthusiaCurrency failed while invoking the exact removal"
            );
        }
    }

    private void finishProviderOutcome(
            Player actor,
            Player target,
            EconomyOperation operation,
            LeaseGuard guard,
            CurrencyAccountState before,
            CurrencyRemovalPlanState plan,
            CurrencyRemovalOutcome outcome,
            Throwable failure
    ) {
        if (failure != null || outcome == null) {
            plugin.getLogger().log(Level.SEVERE, "Currency apply completed exceptionally", failure);
            quarantine(
                    actor,
                    target,
                    operation,
                    guard,
                    Optional.empty(),
                    "CURRENCY_APPLY_EXCEPTION",
                    "The Currency completion outcome is ambiguous"
            );
            return;
        }
        switch (outcome.status()) {
            case COMMITTED -> {
                CurrencyAccountState after = outcome.accountState().orElse(null);
                if (after == null || outcome.amountRemoved() != plan.amount()
                        || outcome.finalTotal() != plan.expectedFinalTotal()
                        || !after.checksum().equals(plan.replacementChecksum())) {
                    quarantine(
                            actor,
                            target,
                            operation,
                            guard,
                            outcome.accountState(),
                            "COMMIT_RESULT_MISMATCH",
                            "Currency reported commit data that does not match the exact plan"
                    );
                    return;
                }
                finishOutcome(
                        actor,
                        target,
                        operation,
                        guard,
                        EconomyTerminalUpdate.committed(
                                after.authoritativeTotal(),
                                after.checksum(),
                                codec.snapshot(after)
                        ),
                        "Confiscated " + plan.amount() + " personal currency into case "
                                + operation.caseId() + '.'
                );
            }
            case FAILED_ROLLED_BACK -> {
                CurrencyAccountState restored = outcome.accountState().orElse(null);
                if (restored == null || restored.authoritativeTotal() != before.authoritativeTotal()) {
                    quarantine(
                            actor,
                            target,
                            operation,
                            guard,
                            outcome.accountState(),
                            "ROLLBACK_RESULT_MISSING",
                            "Currency could not prove the compensated account total"
                    );
                    return;
                }
                finishOutcome(
                        actor,
                        target,
                        operation,
                        guard,
                        EconomyTerminalUpdate.rolledBack(
                                OptionalLong.of(restored.authoritativeTotal()),
                                Optional.of(restored.checksum()),
                                Optional.of(codec.snapshot(restored)),
                                "CURRENCY_FAILED_ROLLED_BACK",
                                bounded(outcome.detail())
                        ),
                        "Economy confiscation failed and Currency restored the before assets."
                );
            }
            case STALE, INVALID_PLAN, LOCK_REQUIRED, PLAYER_OFFLINE -> finishOutcome(
                    actor,
                    target,
                    operation,
                    guard,
                    EconomyTerminalUpdate.rolledBack(
                            OptionalLong.empty(),
                            Optional.empty(),
                            Optional.empty(),
                            "CURRENCY_" + outcome.status().name(),
                            bounded(outcome.detail())
                    ),
                    "Economy confiscation was rejected before assets changed: " + bounded(outcome.detail())
            );
            case QUARANTINE_REQUIRED -> quarantine(
                    actor,
                    target,
                    operation,
                    guard,
                    outcome.accountState(),
                    "CURRENCY_QUARANTINE_REQUIRED",
                    bounded(outcome.detail())
            );
            default -> throw new IllegalStateException(
                    "Unsupported Currency outcome status: " + outcome.status()
            );
        }
    }

    private void rollBackVerified(
            Player actor,
            Player target,
            EconomyOperation operation,
            LeaseGuard guard,
            CurrencyAccountState current,
            String failureCode,
            String detail
    ) {
        if (!submit(() -> finishOutcome(
                actor,
                target,
                operation,
                guard,
                EconomyTerminalUpdate.rolledBack(
                        OptionalLong.of(current.authoritativeTotal()),
                        Optional.of(current.checksum()),
                        Optional.of(codec.snapshot(current)),
                        failureCode,
                        detail
                ),
                detail
        ))) {
            alertStaff("Economy rollback journaling queue is full for " + operation.operationId() + '.');
        }
    }

    private void rollBackBeforeLocalLock(
            Player actor,
            EconomyOperation operation,
            String failureCode,
            String detail
    ) {
        Runnable rollback = () -> {
            EconomyJournalStore loaded = store.get();
            if (loaded == null) {
                message(actor, "Economy storage is unavailable; recovery will retain the durable fence.");
                return;
            }
            try {
                EconomyJournalResult finished = loaded.finish(
                        operation.operationId(),
                        operation.fencingToken(),
                        EconomyTerminalUpdate.rolledBack(
                                OptionalLong.empty(),
                                Optional.empty(),
                                Optional.empty(),
                                failureCode,
                                detail
                        ),
                        clock.instant()
                );
                if (finished.successful()) {
                    loaded.release(operation.operationId(), operation.fencingToken(), clock.instant());
                }
                message(actor, detail + "; no assets changed.");
            } catch (RuntimeException exception) {
                plugin.getLogger().log(Level.SEVERE, "Economy pre-lock rollback failed", exception);
                message(actor, "No assets changed, but the durable economy fence requires recovery.");
            }
        };
        if (!submit(rollback)) {
            message(actor, "No assets changed, but the durable economy fence remains queued for recovery.");
        }
    }

    private void finishOutcome(
            Player actor,
            Player target,
            EconomyOperation operation,
            LeaseGuard guard,
            EconomyTerminalUpdate update,
            String successMessage
    ) {
        EconomyJournalStore loaded = store.get();
        if (loaded == null) {
            alertStaff(
                    "Economy operation " + operation.operationId()
                            + " reached Currency but storage is unavailable; keep the target locked."
            );
            return;
        }
        try {
            EconomyJournalResult finished = loaded.finish(
                    operation.operationId(),
                    operation.fencingToken(),
                    update,
                    clock.instant()
            );
            if (!finished.successful()) {
                alertStaff(
                        "Economy operation " + operation.operationId()
                                + " could not record its terminal outcome: " + finished.detail()
                );
                message(actor, "Economy state requires recovery; do not repeat the operation.");
                return;
            }
            if (update.outcome() == net.enthusia.staff.domain.economy.EconomyTerminalOutcome.QUARANTINED) {
                message(actor, "Economy state was quarantined; do not repeat the operation.");
                alertStaff(
                        "Economy operation " + operation.operationId()
                                + " was quarantined for case " + operation.caseId() + '.'
                );
                return;
            }
            releaseProviderThenJournal(actor, target, operation, guard, successMessage);
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.SEVERE, "Economy terminal journaling failed", exception);
            message(actor, "Economy state requires recovery; do not repeat the operation.");
        }
    }

    private void quarantine(
            Player actor,
            Player target,
            EconomyOperation operation,
            LeaseGuard guard,
            Optional<CurrencyAccountState> observed,
            String failureCode,
            String detail
    ) {
        Runnable work = () -> finishOutcome(
                actor,
                target,
                operation,
                guard,
                EconomyTerminalUpdate.quarantined(
                        observed.isPresent()
                                ? OptionalLong.of(observed.orElseThrow().authoritativeTotal())
                                : OptionalLong.empty(),
                        observed.map(CurrencyAccountState::checksum),
                        observed.map(codec::snapshot),
                        failureCode,
                        detail
                ),
                ""
        );
        if (!submit(work)) {
            alertStaff(
                    "Economy operation " + operation.operationId()
                            + " is ambiguous and its quarantine write queue is full."
            );
        }
    }

    private void releaseProviderThenJournal(
            Player actor,
            Player target,
            EconomyOperation operation,
            LeaseGuard guard,
            String successMessage
    ) {
        Runnable release = () -> {
            boolean released = currency.releaseMovementLock(
                    operation.targetId(),
                    operation.operationId()
            );
            boolean clear = released || !currency.isMovementLocked(operation.targetId());
            if (!clear) {
                alertStaff(
                        "Economy operation " + operation.operationId()
                                + " could not release its local Currency lock."
                );
                return;
            }
            guard.close();
            if (!submit(() -> releaseJournal(actor, operation, successMessage))) {
                alertStaff(
                        "Economy operation " + operation.operationId()
                                + " released locally but its durable unlock queue is full."
                );
            }
        };
        if (target.isOnline()) {
            onEntity(
                    target,
                    release,
                    () -> plugin.getServer().getGlobalRegionScheduler().execute(plugin, release)
            );
        } else {
            plugin.getServer().getGlobalRegionScheduler().execute(plugin, release);
        }
    }

    private void releaseJournal(
            Player actor,
            EconomyOperation operation,
            String successMessage
    ) {
        EconomyJournalStore loaded = store.get();
        if (loaded == null) {
            alertStaff("Durable economy unlock is pending for " + operation.operationId() + '.');
            return;
        }
        try {
            EconomyJournalResult released = loaded.release(
                    operation.operationId(),
                    operation.fencingToken(),
                    clock.instant()
            );
            if (released.successful()) {
                message(actor, successMessage);
            } else {
                message(actor, "Economy outcome is durable, but its network fence requires recovery.");
                alertStaff(
                        "Durable economy unlock failed for " + operation.operationId()
                                + ": " + released.detail()
                );
            }
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.SEVERE, "Durable economy unlock failed", exception);
            message(actor, "Economy outcome is durable, but its network fence requires recovery.");
        }
    }

    private LeaseGuard startGuard(EconomyOperation operation, UUID targetId) {
        LeaseGuard existing = leaseGuards.get(operation.operationId());
        if (existing != null) {
            return existing;
        }
        LeaseGuard created = new LeaseGuard(operation.operationId(), targetId, operation.fencingToken());
        LeaseGuard raced = leaseGuards.putIfAbsent(operation.operationId(), created);
        LeaseGuard guard = raced == null ? created : raced;
        if (raced == null) {
            ScheduledTask task = plugin.getServer().getGlobalRegionScheduler().runAtFixedRate(
                    plugin,
                    ignored -> renew(guard),
                    RENEWAL_PERIOD_TICKS,
                    RENEWAL_PERIOD_TICKS
            );
            guard.task(task);
        }
        return guard;
    }

    private void renew(LeaseGuard guard) {
        if (guard.closed() || !submit(() -> renewDurableThenLocal(guard))) {
            guard.markLost();
        }
    }

    private void renewDurableThenLocal(LeaseGuard guard) {
        EconomyJournalStore loaded = store.get();
        if (loaded == null || loaded.renewLease(
                guard.operationId(),
                guard.fencingToken(),
                LEASE_DURATION,
                clock.instant()
        ).isEmpty()) {
            guard.markLost();
            return;
        }
        Player target = plugin.getServer().getPlayer(guard.targetId());
        if (target == null) {
            guard.markLost();
            return;
        }
        onEntity(
                target,
                () -> {
                    if (!currency.renewMovementLock(
                            guard.targetId(),
                            guard.operationId(),
                            LEASE_DURATION
                    )) {
                        guard.markLost();
                    }
                },
                guard::markLost
        );
    }

    private void reclaimForRecovery(Player player, EconomyOperation original) {
        EconomyJournalStore loaded = store.get();
        if (loaded == null) {
            abortRecovery(player, original, "Economy recovery storage became unavailable.");
            return;
        }
        try {
            EconomyOperation operation = loaded.reclaim(
                    original.operationId(),
                    LEASE_DURATION,
                    clock.instant()
            ).orElse(null);
            if (operation == null) {
                abortRecovery(player, original, "Economy recovery lease could not be reclaimed.");
                return;
            }
            LeaseGuard guard = startGuard(operation, player.getUniqueId());
            onEntity(
                    player,
                    () -> reconcile(player, operation, guard),
                    () -> guard.close()
            );
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.SEVERE, "Economy recovery reclaim failed", exception);
            abortRecovery(player, original, "Economy recovery failed. Please retry.");
        }
    }

    private void reconcile(Player player, EconomyOperation operation, LeaseGuard guard) {
        CurrencyAccountState current;
        try {
            current = currency.snapshot(player);
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.SEVERE, "Economy recovery snapshot failed", exception);
            quarantine(
                    player,
                    player,
                    operation,
                    guard,
                    Optional.empty(),
                    "RECOVERY_SNAPSHOT_FAILED",
                    "Currency recovery could not capture the current account"
            );
            return;
        }
        if (operation.state() == EconomyOperationState.COMMITTED) {
            String expected = operation.resultChecksum()
                    .or(() -> operation.replacementChecksum())
                    .orElse("");
            if (expected.equals(current.checksum())) {
                releaseProviderThenJournal(
                        player,
                        player,
                        operation,
                        guard,
                        "Economy recovery verified the committed state."
                );
            } else {
                quarantine(
                        player,
                        player,
                        operation,
                        guard,
                        Optional.of(current),
                        "COMMITTED_RECOVERY_CONFLICT",
                        "Current Currency state does not match the committed result"
                );
            }
            return;
        }
        if (operation.state() == EconomyOperationState.ROLLED_BACK) {
            if (operation.resultChecksum().isEmpty()
                    || operation.resultChecksum().orElseThrow().equals(current.checksum())) {
                releaseProviderThenJournal(
                        player,
                        player,
                        operation,
                        guard,
                        "Economy recovery verified the rolled-back state."
                );
            } else {
                quarantine(
                        player,
                        player,
                        operation,
                        guard,
                        Optional.of(current),
                        "ROLLED_BACK_RECOVERY_CONFLICT",
                        "Current Currency state does not match the recorded rollback"
                );
            }
            return;
        }
        if (operation.beforeChecksum().isEmpty() || operation.replacementChecksum().isEmpty()
                || operation.beforeSnapshotJson().isEmpty()) {
            rollBackVerified(
                    player,
                    player,
                    operation,
                    guard,
                    current,
                    "RECOVERY_UNAPPLIED",
                    "Economy recovery found no saved apply plan"
            );
            return;
        }
        EconomyReconciliationDecision decision = EconomyReconciliationDecision.decide(
                current.checksum(),
                operation.beforeChecksum().orElseThrow(),
                operation.replacementChecksum().orElseThrow()
        );
        switch (decision) {
            case ROLL_BACK_UNAPPLIED -> rollBackVerified(
                    player,
                    player,
                    operation,
                    guard,
                    current,
                    "RECOVERY_UNAPPLIED",
                    "Economy recovery verified that the plan was not applied"
            );
            case COMMIT_ALREADY_APPLIED -> {
                if (operation.state() != EconomyOperationState.APPLYING) {
                    quarantine(
                            player,
                            player,
                            operation,
                            guard,
                            Optional.of(current),
                            "REPLACEMENT_IN_INVALID_PHASE",
                            "Replacement state exists without an APPLYING journal phase"
                    );
                    return;
                }
                finishOutcome(
                        player,
                        player,
                        operation,
                        guard,
                        EconomyTerminalUpdate.committed(
                                current.authoritativeTotal(),
                                current.checksum(),
                                codec.snapshot(current)
                        ),
                        "Economy recovery finalized the already-applied exact removal."
                );
            }
            case RESTORE_BEFORE_STATE -> {
                if (operation.state() != EconomyOperationState.APPLYING) {
                    quarantine(
                            player,
                            player,
                            operation,
                            guard,
                            Optional.of(current),
                            "RECOVERY_STATE_CONFLICT",
                            "Currency state changed before the operation entered APPLYING"
                    );
                    return;
                }
                restoreBefore(player, operation, guard, current);
            }
            default -> throw new IllegalStateException(
                    "Unsupported economy reconciliation decision: " + decision
            );
        }
    }

    private void restoreBefore(
            Player player,
            EconomyOperation operation,
            LeaseGuard guard,
            CurrencyAccountState current
    ) {
        CurrencyAccountState before;
        try {
            before = codec.snapshot(operation.beforeSnapshotJson().orElseThrow());
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.SEVERE, "Stored Currency before snapshot is invalid", exception);
            quarantine(
                    player,
                    player,
                    operation,
                    guard,
                    Optional.of(current),
                    "BEFORE_SNAPSHOT_INVALID",
                    "The stored Currency before snapshot could not be decoded"
            );
            return;
        }
        try {
            currency.restore(
                    player,
                    operation.operationId(),
                    before,
                    current.checksum()
            ).whenComplete((outcome, failure) -> {
                if (!submit(() -> finishRestore(player, operation, guard, outcome, failure))) {
                    alertStaff(
                            "Currency restore completed for " + operation.operationId()
                                    + " but its journal queue is full."
                    );
                }
            });
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.SEVERE, "Currency restore invocation failed", exception);
            quarantine(
                    player,
                    player,
                    operation,
                    guard,
                    Optional.of(current),
                    "RESTORE_INVOCATION_FAILED",
                    "Currency recovery could not invoke the exact restore"
            );
        }
    }

    private void finishRestore(
            Player player,
            EconomyOperation operation,
            LeaseGuard guard,
            CurrencyRestoreOutcome outcome,
            Throwable failure
    ) {
        if (failure != null || outcome == null || outcome.status() != CurrencyRestoreOutcome.Status.RESTORED
                || outcome.accountState().isEmpty()) {
            quarantine(
                    player,
                    player,
                    operation,
                    guard,
                    outcome == null ? Optional.empty() : outcome.accountState(),
                    "RESTORE_NOT_VERIFIED",
                    outcome == null ? "Currency restore completed ambiguously" : bounded(outcome.detail())
            );
            return;
        }
        CurrencyAccountState restored = outcome.accountState().orElseThrow();
        finishOutcome(
                player,
                player,
                operation,
                guard,
                EconomyTerminalUpdate.rolledBack(
                        OptionalLong.of(restored.authoritativeTotal()),
                        Optional.of(restored.checksum()),
                        Optional.of(codec.snapshot(restored)),
                        "RECOVERY_RESTORED",
                        bounded(outcome.detail())
                ),
                "Economy recovery restored the exact before assets."
        );
    }

    private boolean submit(Runnable operation) {
        if (closed.get()) {
            return false;
        }
        try {
            workers.execute(operation);
            return true;
        } catch (RejectedExecutionException exception) {
            plugin.getLogger().warning("Economy operation skipped because the bounded worker queue is full");
            return false;
        }
    }

    private void onEntity(Player player, Runnable operation, Runnable retired) {
        player.getScheduler().execute(plugin, operation, retired, 1L);
    }

    private void message(Player player, String body) {
        if (player == null) {
            return;
        }
        onEntity(player, () -> player.sendMessage(Component.text(body)), () -> {
        });
    }

    private void abortRecovery(Player player, EconomyOperation operation, String body) {
        Runnable abort = () -> {
            currency.releaseMovementLock(operation.targetId(), operation.operationId());
            if (player.isOnline()) {
                player.kick(Component.text(body));
            }
        };
        if (player.isOnline()) {
            onEntity(
                    player,
                    abort,
                    () -> plugin.getServer().getGlobalRegionScheduler().execute(plugin, abort)
            );
        } else {
            plugin.getServer().getGlobalRegionScheduler().execute(plugin, abort);
        }
    }

    private void alertStaff(String body) {
        plugin.getServer().getGlobalRegionScheduler().execute(plugin, () ->
                plugin.getServer().getOnlinePlayers().stream()
                        .filter(player -> player.hasPermission("enthusiastaff.alerts"))
                        .forEach(player -> player.sendMessage(Component.text(body))));
    }

    private static String bounded(String detail) {
        if (detail == null || detail.isBlank()) {
            return "No provider detail was supplied";
        }
        return detail.length() <= 1024 ? detail : detail.substring(0, 1024);
    }

    private static String requireIdentifier(String value) {
        if (value == null || value.isBlank() || value.length() > 64) {
            throw new IllegalArgumentException("serverId must contain 1-64 characters");
        }
        return value;
    }

    private boolean authorize(Player player, ModerationAction action) {
        Actor actor = PaperActorResolver.resolve(player).orElse(null);
        return actor != null && authorization.permits(actor, action);
    }

    private static List<CurrencyAssetSource> validateSourceOrder(
            List<CurrencyAssetSource> order
    ) {
        List<CurrencyAssetSource> copy = List.copyOf(
                java.util.Objects.requireNonNull(order, "sourceOrder")
        );
        if (copy.size() != CurrencyAssetSource.values().length
                || !EnumSet.copyOf(copy).equals(EnumSet.allOf(CurrencyAssetSource.class))) {
            throw new IllegalArgumentException("sourceOrder must contain each source exactly once");
        }
        return copy;
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        leaseGuards.values().forEach(LeaseGuard::close);
        leaseGuards.clear();
        preloadedRecovery.clear();
    }

    private final class LeaseGuard implements AutoCloseable {
        private final UUID operationId;
        private final UUID targetId;
        private final long fencingToken;
        private final AtomicBoolean lost = new AtomicBoolean();
        private final AtomicBoolean stopped = new AtomicBoolean();
        private volatile ScheduledTask task;

        private LeaseGuard(UUID operationId, UUID targetId, long fencingToken) {
            this.operationId = operationId;
            this.targetId = targetId;
            this.fencingToken = fencingToken;
        }

        UUID operationId() {
            return operationId;
        }

        UUID targetId() {
            return targetId;
        }

        long fencingToken() {
            return fencingToken;
        }

        boolean lost() {
            return lost.get();
        }

        boolean closed() {
            return stopped.get();
        }

        void markLost() {
            lost.set(true);
        }

        void task(ScheduledTask scheduledTask) {
            task = scheduledTask;
            if (stopped.get()) {
                scheduledTask.cancel();
            }
        }

        @Override
        public void close() {
            if (!stopped.compareAndSet(false, true)) {
                return;
            }
            ScheduledTask scheduledTask = task;
            if (scheduledTask != null) {
                scheduledTask.cancel();
            }
            leaseGuards.remove(operationId, this);
        }
    }
}
