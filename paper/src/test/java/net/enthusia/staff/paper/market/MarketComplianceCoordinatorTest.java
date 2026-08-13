package net.enthusia.staff.paper.market;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicInteger;
import net.enthusia.market.api.moderation.MarketBlacklistRemoval;
import net.enthusia.market.api.moderation.MarketBlacklistRequest;
import net.enthusia.market.api.moderation.MarketBlacklistResult;
import net.enthusia.market.api.moderation.MarketConfiscationApproval;
import net.enthusia.market.api.moderation.MarketOperationRecord;
import net.enthusia.market.api.moderation.MarketOperationRequest;
import net.enthusia.market.api.moderation.MarketOperationResult;
import net.enthusia.market.api.moderation.MarketRestoreRequest;
import net.enthusia.market.api.moderation.StallBlacklistState;
import net.enthusia.staff.common.CaseId;
import net.enthusia.staff.domain.OperationalMode;
import net.enthusia.staff.domain.auth.Actor;
import net.enthusia.staff.domain.auth.DefaultAuthorizationPolicy;
import net.enthusia.staff.domain.auth.StaffRank;
import net.enthusia.staff.domain.evidence.IntegrationAvailability;
import net.enthusia.staff.domain.market.MarketComplianceOperation;
import net.enthusia.staff.domain.market.MarketComplianceRequest;
import net.enthusia.staff.domain.market.MarketComplianceResult;
import net.enthusia.staff.domain.market.MarketComplianceState;
import net.enthusia.staff.domain.market.MarketComplianceUpdate;
import net.enthusia.staff.domain.ports.CaseLookup;
import net.enthusia.staff.domain.ports.MarketComplianceStore;
import net.enthusia.staff.domain.sanction.SanctionType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MarketComplianceCoordinatorTest {
    private static final Instant NOW = Instant.parse("2026-08-13T12:00:00Z");
    private static final UUID ACTOR_ID = UUID.fromString("1725b46f-5674-49a8-81c8-1809b3499baf");
    private static final UUID TARGET_ID = UUID.fromString("dad9430f-093b-42e4-bc1a-57a5730b3a17");
    private static final UUID OPERATION_ID = UUID.fromString("32e4327f-8647-4772-988a-527c37c44029");
    private static final CaseId CASE_ID = new CaseId("01HZX3K8M2N4P6QR");
    private static final String CHECKSUM = "a".repeat(64);

    private final FakeStore store = new FakeStore();
    private final FakeGateway gateway = new FakeGateway();
    private OperationalMode mode;
    private MarketComplianceCoordinator coordinator;

    @BeforeEach
    void setUp() {
        mode = OperationalMode.ACTIVE;
        CaseLookup cases = new FixedCaseLookup();
        coordinator = new MarketComplianceCoordinator(
                new MarketCoordinatorRuntime(
                        Clock.fixed(NOW, ZoneOffset.UTC),
                        () -> mode,
                        new DefaultAuthorizationPolicy(),
                        () -> store,
                        () -> cases,
                        Runnable::run
                ),
                gateway,
                () -> OPERATION_ID
        );
    }

    @Test
    void preparePersistsIntentBeforeProviderMutation() {
        gateway.prepare = request -> {
            assertTrue(store.find(request.operationId()).isPresent());
            return completed(operationResult(request, MarketOperationRecord.State.PREPARED));
        };

        MarketCoordinationResult result = coordinator.prepareStall(
                admin(), TARGET_ID, CASE_ID, "stall-1", Optional.empty()
        ).toCompletableFuture().join();

        assertEquals(MarketCoordinationResult.Status.UPDATED, result.status());
        assertEquals(MarketComplianceState.PREPARED, result.operation().orElseThrow().state());
        assertEquals(CHECKSUM, result.operation().orElseThrow().snapshotChecksum().orElseThrow());
    }

    @Test
    void failedProviderCallLeavesDurablePreparingIntentForRecovery() {
        gateway.prepare = request -> CompletableFuture.failedStage(
                new IllegalStateException("provider unavailable")
        );

        MarketCoordinationResult failed = coordinator.prepareStall(
                admin(), TARGET_ID, CASE_ID, "stall-1", Optional.empty()
        ).toCompletableFuture().join();

        assertEquals(MarketCoordinationResult.Status.UNAVAILABLE, failed.status());
        assertEquals(MarketComplianceState.PREPARING, store.find(OPERATION_ID).orElseThrow().state());

        gateway.prepare = request -> completed(
                operationResult(request, MarketOperationRecord.State.PREPARED)
        );
        gateway.find = ignored -> completed(Optional.empty());

        assertEquals(1, coordinator.recoverPending().toCompletableFuture().join());
        assertEquals(MarketComplianceState.PREPARED, store.find(OPERATION_ID).orElseThrow().state());
    }

    @Test
    void recoveryNeverAutoApprovesPreparedConfiscation() {
        gateway.prepare = request -> completed(
                operationResult(request, MarketOperationRecord.State.PREPARED)
        );
        coordinator.prepareStall(
                admin(), TARGET_ID, CASE_ID, "stall-1", Optional.empty()
        ).toCompletableFuture().join();
        gateway.find = ignored -> completed(Optional.of(gateway.record));

        coordinator.recoverPending().toCompletableFuture().join();

        assertEquals(0, gateway.confiscations.get());
        assertEquals(MarketComplianceState.PREPARED, store.find(OPERATION_ID).orElseThrow().state());
    }

    @Test
    void mismatchedProviderIdentityIsQuarantined() {
        gateway.prepare = request -> {
            MarketOperationRecord mismatch = new MarketOperationRecord(
                    request.operationId(),
                    UUID.randomUUID(),
                    request.caseId(),
                    request.stallId(),
                    MarketOperationRecord.State.PREPARED,
                    CHECKSUM,
                    Optional.empty(),
                    Optional.empty(),
                    request.reviewDueAt(),
                    request.recoveryUntil(),
                    1L,
                    "prepared",
                    NOW
            );
            return completed(new MarketOperationResult(
                    MarketOperationResult.Status.PREPARED, Optional.of(mismatch), "prepared"
            ));
        };

        MarketCoordinationResult result = coordinator.prepareStall(
                admin(), TARGET_ID, CASE_ID, "stall-1", Optional.empty()
        ).toCompletableFuture().join();

        assertEquals(MarketCoordinationResult.Status.QUARANTINED, result.status());
        assertEquals(MarketComplianceState.QUARANTINED, store.find(OPERATION_ID).orElseThrow().state());
    }

    @Test
    void confiscationRequiresExplicitAuthorizedApproval() {
        gateway.prepare = request -> completed(
                operationResult(request, MarketOperationRecord.State.PREPARED)
        );
        coordinator.prepareStall(
                admin(), TARGET_ID, CASE_ID, "stall-1", Optional.empty()
        ).toCompletableFuture().join();
        gateway.confiscate = approval -> {
            gateway.confiscations.incrementAndGet();
            assertEquals(ACTOR_ID, approval.reviewerId());
            assertEquals(CHECKSUM, approval.expectedSnapshotChecksum());
            return completed(operationResult(
                    gateway.request,
                    MarketOperationRecord.State.MODERATION_HOLD,
                    Optional.of(approval.reviewerId())
            ));
        };

        MarketCoordinationResult result = coordinator.approveConfiscation(admin(), OPERATION_ID)
                .toCompletableFuture().join();

        assertEquals(1, gateway.confiscations.get());
        assertEquals(MarketComplianceState.MODERATION_HOLD, result.operation().orElseThrow().state());
        assertEquals(ACTOR_ID, result.operation().orElseThrow().reviewedBy().orElseThrow());
    }

    @Test
    void writesFailClosedOutsideActiveMode() {
        mode = OperationalMode.MAINTENANCE;

        MarketCoordinationResult result = coordinator.prepareStall(
                admin(), TARGET_ID, CASE_ID, "stall-1", Optional.empty()
        ).toCompletableFuture().join();

        assertEquals(MarketCoordinationResult.Status.UNAVAILABLE, result.status());
        assertTrue(store.operations.isEmpty());
    }

    private static Actor admin() {
        return new Actor(ACTOR_ID, "admin", StaffRank.ADMIN);
    }

    private MarketOperationResult operationResult(
            MarketOperationRequest request,
            MarketOperationRecord.State state
    ) {
        return operationResult(request, state, Optional.empty());
    }

    private MarketOperationResult operationResult(
            MarketOperationRequest request,
            MarketOperationRecord.State state,
            Optional<UUID> reviewer
    ) {
        Optional<String> current = state == MarketOperationRecord.State.MODERATION_HOLD
                ? Optional.of("b".repeat(64))
                : Optional.empty();
        gateway.request = request;
        gateway.record = new MarketOperationRecord(
                request.operationId(),
                request.targetId(),
                request.caseId(),
                request.stallId(),
                state,
                CHECKSUM,
                current,
                reviewer,
                request.reviewDueAt(),
                request.recoveryUntil(),
                1L,
                state.name(),
                NOW
        );
        MarketOperationResult.Status status = switch (state) {
            case PREPARED -> MarketOperationResult.Status.PREPARED;
            case MODERATION_HOLD -> MarketOperationResult.Status.HELD;
            case RESTORED -> MarketOperationResult.Status.RESTORED;
            case RELEASED -> MarketOperationResult.Status.RELEASED;
            case QUARANTINED -> MarketOperationResult.Status.QUARANTINED;
        };
        return new MarketOperationResult(status, Optional.of(gateway.record), state.name());
    }

    private static <T> CompletionStage<T> completed(T value) {
        return CompletableFuture.completedFuture(value);
    }

    private static final class FixedCaseLookup implements CaseLookup {
        @Override
        public Optional<CaseId> latestCase(UUID targetId, Set<SanctionType> types, boolean activeOnly) {
            return Optional.empty();
        }

        @Override
        public Optional<UUID> target(CaseId caseId) {
            return CASE_ID.equals(caseId) ? Optional.of(TARGET_ID) : Optional.empty();
        }

        @Override
        public boolean containsSanction(CaseId caseId, Set<SanctionType> types, boolean activeOnly) {
            return false;
        }

        @Override
        public boolean exists(CaseId caseId) {
            return CASE_ID.equals(caseId);
        }
    }

    private static final class FakeStore implements MarketComplianceStore {
        private final Map<UUID, MarketComplianceOperation> operations = new LinkedHashMap<>();

        @Override
        public MarketComplianceResult start(MarketComplianceRequest request) {
            MarketComplianceOperation existing = operations.get(request.operationId());
            if (existing != null) {
                return result(MarketComplianceResult.Status.REPLAYED, existing, "replayed");
            }
            MarketComplianceOperation created = new MarketComplianceOperation(
                    request,
                    MarketComplianceState.PREPARING,
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty(),
                    0L,
                    0L,
                    "pending",
                    request.createdAt(),
                    Optional.empty()
            );
            operations.put(request.operationId(), created);
            return result(MarketComplianceResult.Status.CREATED, created, "created");
        }

        @Override
        public Optional<MarketComplianceOperation> find(UUID operationId) {
            return Optional.ofNullable(operations.get(operationId));
        }

        @Override
        public MarketComplianceResult update(
                UUID operationId,
                long expectedJournalRevision,
                MarketComplianceUpdate update
        ) {
            MarketComplianceOperation current = operations.get(operationId);
            if (current == null) {
                return new MarketComplianceResult(
                        MarketComplianceResult.Status.NOT_FOUND, Optional.empty(), "missing"
                );
            }
            if (current.journalRevision() != expectedJournalRevision) {
                return result(MarketComplianceResult.Status.STALE, current, "stale");
            }
            MarketComplianceOperation changed = new MarketComplianceOperation(
                    current.request(),
                    update.state(),
                    update.reviewedBy(),
                    update.snapshotChecksum(),
                    update.currentChecksum(),
                    update.providerRevision(),
                    current.journalRevision() + 1L,
                    update.detail(),
                    update.updatedAt(),
                    current.reviewAlertedAt()
            );
            operations.put(operationId, changed);
            return result(MarketComplianceResult.Status.UPDATED, changed, "updated");
        }

        @Override
        public List<MarketComplianceOperation> recoverable(int limit) {
            List<MarketComplianceOperation> found = new ArrayList<>();
            for (MarketComplianceOperation operation : operations.values()) {
                if (operation.state() == MarketComplianceState.PREPARING
                        || operation.state() == MarketComplianceState.PREPARED
                        || operation.state() == MarketComplianceState.MODERATION_HOLD) {
                    found.add(operation);
                }
            }
            return found.stream().limit(limit).toList();
        }

        @Override
        public int emitDueReviewAlerts(Instant now, int limit) {
            return 0;
        }

        private static MarketComplianceResult result(
                MarketComplianceResult.Status status,
                MarketComplianceOperation operation,
                String detail
        ) {
            return new MarketComplianceResult(status, Optional.ofNullable(operation), detail);
        }
    }

    private static final class FakeGateway implements MarketGateway {
        private java.util.function.Function<MarketOperationRequest,
                CompletionStage<MarketOperationResult>> prepare = ignored -> null;
        private java.util.function.Function<MarketConfiscationApproval,
                CompletionStage<MarketOperationResult>> confiscate = ignored -> null;
        private java.util.function.Function<UUID,
                CompletionStage<Optional<MarketOperationRecord>>> find = ignored -> completed(Optional.empty());
        private final AtomicInteger confiscations = new AtomicInteger();
        private MarketOperationRequest request;
        private MarketOperationRecord record;

        @Override
        public IntegrationAvailability availability() {
            return IntegrationAvailability.AVAILABLE;
        }

        @Override
        public String issue() {
            return "";
        }

        @Override
        public CompletionStage<MarketOperationResult> prepare(MarketOperationRequest request) {
            return Objects.requireNonNull(prepare.apply(request));
        }

        @Override
        public CompletionStage<MarketOperationResult> confiscate(MarketConfiscationApproval approval) {
            return Objects.requireNonNull(confiscate.apply(approval));
        }

        @Override
        public CompletionStage<MarketOperationResult> restore(MarketRestoreRequest request) {
            return CompletableFuture.failedStage(new UnsupportedOperationException());
        }

        @Override
        public CompletionStage<MarketOperationResult> release(UUID operationId, String snapshotChecksum) {
            return CompletableFuture.failedStage(new UnsupportedOperationException());
        }

        @Override
        public CompletionStage<Optional<MarketOperationRecord>> findOperation(UUID operationId) {
            return find.apply(operationId);
        }

        @Override
        public CompletionStage<Optional<StallBlacklistState>> getBlacklist(UUID targetId) {
            return completed(Optional.empty());
        }

        @Override
        public CompletionStage<MarketBlacklistResult> applyBlacklist(MarketBlacklistRequest request) {
            return CompletableFuture.failedStage(new UnsupportedOperationException());
        }

        @Override
        public CompletionStage<MarketBlacklistResult> removeBlacklist(MarketBlacklistRemoval removal) {
            return CompletableFuture.failedStage(new UnsupportedOperationException());
        }
    }
}
