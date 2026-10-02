package net.enthusia.staff.paper.market;

import java.time.Instant;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import net.enthusia.market.api.moderation.MarketBlacklistRemoval;
import net.enthusia.market.api.moderation.MarketBlacklistRequest;
import net.enthusia.market.api.moderation.MarketConfiscationApproval;
import net.enthusia.market.api.moderation.MarketOperationRecord;
import net.enthusia.market.api.moderation.MarketOperationRequest;
import net.enthusia.market.api.moderation.MarketOperationResult;
import net.enthusia.market.api.moderation.MarketRestoreRequest;
import net.enthusia.staff.common.CaseId;
import net.enthusia.staff.domain.OperationalMode;
import net.enthusia.staff.domain.auth.Actor;
import net.enthusia.staff.domain.auth.AuthorizationPolicy;
import net.enthusia.staff.domain.auth.ModerationAction;
import net.enthusia.staff.domain.evidence.IntegrationAvailability;
import net.enthusia.staff.domain.market.MarketComplianceKind;
import net.enthusia.staff.domain.market.MarketComplianceOperation;
import net.enthusia.staff.domain.market.MarketComplianceRequest;
import net.enthusia.staff.domain.market.MarketComplianceResult;
import net.enthusia.staff.domain.market.MarketComplianceState;
import net.enthusia.staff.domain.ports.CaseLookup;
import net.enthusia.staff.domain.ports.MarketComplianceStore;

/** Coordinates durable Staff intent with the provider-owned EnthusiaMarket journal. */
public final class MarketComplianceCoordinator {
    private static final int RECOVERY_BATCH = 64;

    private final java.time.Clock clock;
    private final Supplier<OperationalMode> mode;
    private final AuthorizationPolicy authorization;
    private final Supplier<MarketComplianceStore> stores;
    private final Supplier<CaseLookup> cases;
    private final Executor workers;
    private final MarketGateway market;
    private final MarketComplianceRequests requests;
    private final MarketJournalReconciler reconciler = new MarketJournalReconciler();
    private final AtomicBoolean recoveryRunning = new AtomicBoolean();

    public MarketComplianceCoordinator(
            MarketCoordinatorRuntime runtime,
            MarketGateway market,
            Supplier<UUID> identifiers
    ) {
        MarketCoordinatorRuntime checked = Objects.requireNonNull(runtime, "runtime");
        clock = Objects.requireNonNull(checked.clock(), "clock");
        mode = Objects.requireNonNull(checked.mode(), "mode");
        authorization = Objects.requireNonNull(checked.authorization(), "authorization");
        stores = Objects.requireNonNull(checked.store(), "store");
        cases = Objects.requireNonNull(checked.cases(), "cases");
        workers = Objects.requireNonNull(checked.workers(), "workers");
        this.market = Objects.requireNonNull(market, "market");
        requests = new MarketComplianceRequests(identifiers);
    }

    public CompletionStage<MarketCoordinationResult> prepareStall(
            Actor actor,
            UUID targetId,
            CaseId caseId,
            String stallId,
            Optional<Instant> blacklistExpiresAt
    ) {
        Optional<MarketCoordinationResult> rejected = reject(
                actor, ModerationAction.MODIFY_MARKET_RESTRICTION
        );
        if (rejected.isPresent()) {
            return completed(rejected.orElseThrow());
        }
        MarketComplianceRequest request;
        try {
            request = requests.stall(
                    actor.id(), targetId, caseId, stallId, blacklistExpiresAt, clock.instant()
            );
        } catch (RuntimeException exception) {
            return completed(rejected("Invalid market request: " + safeMessage(exception)));
        }
        return begin(request).thenCompose(started -> continueStallPreparation(started, request))
                .exceptionally(this::failed);
    }

    public CompletionStage<MarketCoordinationResult> approveConfiscation(
            Actor actor,
            UUID operationId
    ) {
        return withOperation(
                actor,
                ModerationAction.MODIFY_MARKET_RESTRICTION,
                operationId,
                MarketComplianceState.PREPARED,
                operation -> market.confiscate(new MarketConfiscationApproval(
                        operation.operationId(),
                        actor.id(),
                        operation.snapshotChecksum().orElseThrow(),
                        clock.instant()
                )).thenApplyAsync(
                        result -> reconcile(operation, result), workers
                )
        );
    }

    public CompletionStage<MarketCoordinationResult> release(
            Actor actor,
            UUID operationId
    ) {
        return withOperation(
                actor,
                ModerationAction.MODIFY_MARKET_RESTRICTION,
                operationId,
                MarketComplianceState.PREPARED,
                operation -> market.release(
                        operation.operationId(), operation.snapshotChecksum().orElseThrow()
                ).thenApplyAsync(result -> reconcile(operation, result), workers)
        );
    }

    public CompletionStage<MarketCoordinationResult> restore(
            Actor actor,
            UUID operationId
    ) {
        return withOperation(
                actor,
                ModerationAction.RESTORE_ASSETS,
                operationId,
                MarketComplianceState.MODERATION_HOLD,
                operation -> market.restore(new MarketRestoreRequest(
                        operation.operationId(),
                        actor.id(),
                        operation.currentChecksum().orElseThrow()
                )).thenApplyAsync(result -> reconcile(operation, result), workers)
        );
    }

    public CompletionStage<MarketCoordinationResult> applyBlacklist(
            Actor actor,
            UUID targetId,
            CaseId caseId,
            Optional<Instant> expiresAt
    ) {
        Optional<MarketCoordinationResult> rejected = reject(
                actor, ModerationAction.MODIFY_MARKET_RESTRICTION
        );
        if (rejected.isPresent()) {
            return completed(rejected.orElseThrow());
        }
        MarketComplianceRequest request;
        try {
            request = requests.blacklistApply(
                    actor.id(), targetId, caseId, expiresAt, clock.instant()
            );
        } catch (RuntimeException exception) {
            return completed(rejected("Invalid market request: " + safeMessage(exception)));
        }
        return begin(request).thenCompose(started -> continueBlacklistApply(started, request))
                .exceptionally(this::failed);
    }

    public CompletionStage<MarketCoordinationResult> removeBlacklist(
            Actor actor,
            UUID targetId,
            CaseId caseId,
            long expectedRevision
    ) {
        Optional<MarketCoordinationResult> rejected = reject(
                actor, ModerationAction.MODIFY_MARKET_RESTRICTION
        );
        if (rejected.isPresent()) {
            return completed(rejected.orElseThrow());
        }
        MarketComplianceRequest request;
        try {
            request = requests.blacklistRemove(
                    actor.id(), targetId, caseId, expectedRevision, clock.instant()
            );
        } catch (RuntimeException exception) {
            return completed(rejected("Invalid market request: " + safeMessage(exception)));
        }
        return begin(request).thenCompose(started -> continueBlacklistRemove(started, request))
                .exceptionally(this::failed);
    }

    public CompletionStage<MarketCoordinationResult> find(Actor actor, UUID operationId) {
        Optional<MarketCoordinationResult> rejected = reject(
                actor, ModerationAction.MODIFY_MARKET_RESTRICTION, false
        );
        if (rejected.isPresent()) {
            return completed(rejected.orElseThrow());
        }
        return supply(() -> {
            MarketComplianceStore store = requireStore();
            MarketComplianceOperation operation = store.find(operationId).orElse(null);
            if (operation == null) {
                return new MarketCoordinationResult(
                        MarketCoordinationResult.Status.REJECTED,
                        Optional.empty(),
                        "Market operation was not found"
                );
            }
            return new MarketCoordinationResult(
                    MarketCoordinationResult.Status.REPLAYED,
                    Optional.of(operation),
                    operation.detail()
            );
        }).exceptionally(this::failed);
    }

    /** Reconciles interrupted calls; it never advances PREPARED to confiscated automatically. */
    public CompletionStage<Integer> recoverPending() {
        if (mode.get() != OperationalMode.ACTIVE
                || market.availability() != IntegrationAvailability.AVAILABLE
                || !recoveryRunning.compareAndSet(false, true)) {
            return CompletableFuture.completedFuture(0);
        }
        return supply(() -> requireStore().recoverable(RECOVERY_BATCH))
                .thenCompose(this::recoverSequentially)
                .whenComplete((ignored, failure) -> recoveryRunning.set(false));
    }

    public CompletionStage<Integer> emitDueReviewAlerts() {
        if (mode.get() != OperationalMode.ACTIVE) {
            return CompletableFuture.completedFuture(0);
        }
        return supply(() -> requireStore().emitDueReviewAlerts(clock.instant(), RECOVERY_BATCH));
    }

    private CompletionStage<MarketCoordinationResult> continueStallPreparation(
            MarketCoordinationResult started,
            MarketComplianceRequest request
    ) {
        MarketComplianceOperation operation = preparable(started);
        if (operation == null) {
            return completed(started);
        }
        return market.prepare(providerRequest(request))
                .thenApplyAsync(result -> reconcile(operation, result), workers);
    }

    private CompletionStage<MarketCoordinationResult> continueBlacklistApply(
            MarketCoordinationResult started,
            MarketComplianceRequest request
    ) {
        MarketComplianceOperation operation = preparable(started);
        if (operation == null) {
            return completed(started);
        }
        return market.applyBlacklist(new MarketBlacklistRequest(
                request.operationId(),
                request.targetId(),
                request.caseId().value(),
                request.blacklistExpiresAt()
        )).thenApplyAsync(
                result -> reconciler.blacklist(requireStore(), operation, result, clock.instant()),
                workers
        );
    }

    private CompletionStage<MarketCoordinationResult> continueBlacklistRemove(
            MarketCoordinationResult started,
            MarketComplianceRequest request
    ) {
        MarketComplianceOperation operation = preparable(started);
        if (operation == null) {
            return completed(started);
        }
        return market.removeBlacklist(new MarketBlacklistRemoval(
                request.operationId(),
                request.targetId(),
                request.caseId().value(),
                request.expectedBlacklistRevision().orElseThrow()
        )).thenApplyAsync(
                result -> reconciler.blacklist(requireStore(), operation, result, clock.instant()),
                workers
        );
    }

    private CompletionStage<MarketCoordinationResult> withOperation(
            Actor actor,
            ModerationAction action,
            UUID operationId,
            MarketComplianceState requiredState,
            java.util.function.Function<MarketComplianceOperation,
                    CompletionStage<MarketCoordinationResult>> invocation
    ) {
        Optional<MarketCoordinationResult> rejected = reject(actor, action);
        if (rejected.isPresent()) {
            return completed(rejected.orElseThrow());
        }
        return supply(() -> loadForAction(operationId, requiredState))
                .thenCompose(loaded -> loaded.operation().isEmpty()
                        ? completed(loaded)
                        : invocation.apply(loaded.operation().orElseThrow()))
                .exceptionally(this::failed);
    }

    private MarketCoordinationResult loadForAction(
            UUID operationId,
            MarketComplianceState requiredState
    ) {
        MarketComplianceOperation operation = requireStore().find(operationId).orElse(null);
        if (operation == null) {
            return new MarketCoordinationResult(
                    MarketCoordinationResult.Status.REJECTED,
                    Optional.empty(),
                    "Market operation was not found"
            );
        }
        if (operation.request().kind() != MarketComplianceKind.STALL
                || operation.state() != requiredState) {
            return rejected(
                    "Market operation must be a " + requiredState + " stall operation"
            );
        }
        if (!caseMatches(operation.request())) {
            return rejected("Market operation case target no longer matches the durable case");
        }
        return new MarketCoordinationResult(
                MarketCoordinationResult.Status.UPDATED,
                Optional.of(operation),
                "Market operation is ready"
        );
    }

    private CompletionStage<MarketCoordinationResult> begin(MarketComplianceRequest request) {
        return supply(() -> {
            if (!caseMatches(request)) {
                return rejected("Market request target does not match the durable case");
            }
            MarketComplianceResult started = requireStore().start(request);
            MarketComplianceOperation operation = started.operation().orElse(null);
            return switch (started.status()) {
                case CREATED -> result(MarketCoordinationResult.Status.UPDATED, operation, started.detail());
                case REPLAYED -> result(
                        MarketCoordinationResult.Status.REPLAYED, operation, started.detail()
                );
                case CONFLICT, STALE -> result(
                        MarketCoordinationResult.Status.CONFLICT, operation, started.detail()
                );
                case NOT_FOUND, UPDATED -> rejected(started.detail());
            };
        });
    }

    private boolean caseMatches(MarketComplianceRequest request) {
        CaseLookup lookup = cases.get();
        return lookup != null
                && lookup.target(request.caseId()).filter(request.targetId()::equals).isPresent();
    }

    private CompletionStage<Integer> recoverSequentially(List<MarketComplianceOperation> operations) {
        CompletionStage<Integer> recovered = CompletableFuture.completedFuture(0);
        for (MarketComplianceOperation operation : operations) {
            recovered = recovered.thenCompose(count -> recover(operation)
                    .handle((ignored, failure) -> count + (failure == null ? 1 : 0)));
        }
        return recovered;
    }

    private CompletionStage<MarketCoordinationResult> recover(MarketComplianceOperation operation) {
        if (!caseMatches(operation.request())) {
            return supply(() -> reconciler.quarantine(
                    requireStore(),
                    operation,
                    operation.providerRevision(),
                    "Durable case target does not match the market operation",
                    clock.instant()
            ));
        }
        if (operation.request().kind() == MarketComplianceKind.BLACKLIST_APPLY) {
            return continueBlacklistApply(recoveryStart(operation), operation.request());
        }
        if (operation.request().kind() == MarketComplianceKind.BLACKLIST_REMOVE) {
            return continueBlacklistRemove(recoveryStart(operation), operation.request());
        }
        return market.findOperation(operation.operationId()).thenCompose(found ->
                recoverStall(operation, found));
    }

    private CompletionStage<MarketCoordinationResult> recoverStall(
            MarketComplianceOperation local,
            Optional<MarketOperationRecord> found
    ) {
        if (found.isPresent()) {
            MarketOperationResult replay = new MarketOperationResult(
                    MarketOperationResult.Status.REPLAYED,
                    found,
                    "Market operation recovered from provider journal"
            );
            return supply(() -> reconcile(local, replay));
        }
        if (local.state() == MarketComplianceState.PREPARING) {
            return market.prepare(providerRequest(local.request()))
                    .thenApplyAsync(result -> reconcile(local, result), workers);
        }
        return supply(() -> reconciler.quarantine(
                requireStore(),
                local,
                local.providerRevision(),
                "Provider operation is missing during Staff journal recovery",
                clock.instant()
        ));
    }

    private MarketCoordinationResult reconcile(
            MarketComplianceOperation operation,
            MarketOperationResult result
    ) {
        return reconciler.operation(requireStore(), operation, result, clock.instant());
    }

    private Optional<MarketCoordinationResult> reject(Actor actor, ModerationAction action) {
        return reject(actor, action, true);
    }

    private Optional<MarketCoordinationResult> reject(
            Actor actor,
            ModerationAction action,
            boolean providerMutation
    ) {
        if (actor == null || !authorization.permits(actor, action)) {
            return Optional.of(rejected("You do not have authority for this market operation"));
        }
        Optional<MarketCoordinationResult> providerRejection = rejectProvider(providerMutation);
        if (providerRejection.isPresent()) {
            return providerRejection;
        }
        if (dependenciesUnavailable(providerMutation)) {
            return Optional.of(unavailable("Market compliance storage is unavailable"));
        }
        return Optional.empty();
    }

    private Optional<MarketCoordinationResult> rejectProvider(boolean providerMutation) {
        if (!providerMutation) {
            return Optional.empty();
        }
        if (mode.get() != OperationalMode.ACTIVE) {
            return Optional.of(unavailable("Market writes require ACTIVE moderation mode"));
        }
        if (market.availability() != IntegrationAvailability.AVAILABLE) {
            return Optional.of(unavailable(market.issue()));
        }
        return Optional.empty();
    }

    private boolean dependenciesUnavailable(boolean providerMutation) {
        return stores.get() == null || (providerMutation && cases.get() == null);
    }

    private MarketComplianceStore requireStore() {
        return Objects.requireNonNull(stores.get(), "Market compliance storage is unavailable");
    }

    private <T> CompletionStage<T> supply(Supplier<T> operation) {
        return CompletableFuture.supplyAsync(operation, workers);
    }

    private MarketCoordinationResult failed(Throwable failure) {
        return unavailable("Market coordination failed safely: " + safeMessage(failure));
    }

    private static MarketComplianceOperation preparable(MarketCoordinationResult started) {
        MarketComplianceOperation operation = started.operation().orElse(null);
        return operation != null && operation.state() == MarketComplianceState.PREPARING
                ? operation
                : null;
    }

    private static MarketOperationRequest providerRequest(MarketComplianceRequest request) {
        return new MarketOperationRequest(
                request.operationId(),
                request.targetId(),
                request.caseId().value(),
                request.stallId().orElseThrow(),
                request.reviewDueAt(),
                request.recoveryUntil(),
                request.blacklistExpiresAt()
        );
    }

    private static MarketCoordinationResult recoveryStart(MarketComplianceOperation operation) {
        return new MarketCoordinationResult(
                MarketCoordinationResult.Status.REPLAYED,
                Optional.of(operation),
                "Recovering durable market operation"
        );
    }

    private static MarketCoordinationResult result(
            MarketCoordinationResult.Status status,
            MarketComplianceOperation operation,
            String detail
    ) {
        return new MarketCoordinationResult(status, Optional.ofNullable(operation), bounded(detail));
    }

    private static MarketCoordinationResult rejected(String detail) {
        return new MarketCoordinationResult(
                MarketCoordinationResult.Status.REJECTED, Optional.empty(), bounded(detail)
        );
    }

    private static MarketCoordinationResult unavailable(String detail) {
        return new MarketCoordinationResult(
                MarketCoordinationResult.Status.UNAVAILABLE, Optional.empty(), bounded(detail)
        );
    }

    private static <T> CompletionStage<T> completed(T value) {
        return CompletableFuture.completedFuture(value);
    }

    private static String safeMessage(Throwable failure) {
        Throwable current = failure;
        Set<Throwable> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        visited.add(current);
        while (current.getCause() != null && visited.add(current.getCause())) {
            current = current.getCause();
        }
        String message = current.getMessage();
        return message == null || message.isBlank()
                ? current.getClass().getSimpleName()
                : bounded(message);
    }

    private static String bounded(String detail) {
        if (detail == null || detail.isBlank()) {
            return "Market operation failed without detail";
        }
        return detail.length() <= 512 ? detail : detail.substring(0, 512);
    }
}
