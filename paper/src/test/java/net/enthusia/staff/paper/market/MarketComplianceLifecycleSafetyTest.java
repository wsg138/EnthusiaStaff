package net.enthusia.staff.paper.market;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicReference;
import net.enthusia.market.api.moderation.MarketBlacklistResult;
import net.enthusia.market.api.moderation.MarketOperationRecord;
import net.enthusia.market.api.moderation.MarketOperationRequest;
import net.enthusia.market.api.moderation.MarketOperationResult;
import net.enthusia.market.api.moderation.StallBlacklistState;
import net.enthusia.staff.common.CaseId;
import net.enthusia.staff.domain.OperationalMode;
import net.enthusia.staff.domain.auth.Actor;
import net.enthusia.staff.domain.auth.AuthorizationPolicy;
import net.enthusia.staff.domain.auth.DefaultAuthorizationPolicy;
import net.enthusia.staff.domain.auth.StaffRank;
import net.enthusia.staff.domain.market.MarketComplianceState;
import net.enthusia.staff.paper.auth.ActiveDutyAuthorizationPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MarketComplianceLifecycleSafetyTest {
    private static final Instant NOW = Instant.parse("2026-10-02T12:00:00Z");
    private static final UUID ADMIN_ID =
            UUID.fromString("1725b46f-5674-49a8-81c8-1809b3499baf");
    private static final UUID FOUNDER_ID =
            UUID.fromString("4329fc75-4c55-43e0-b66e-8439203de4f1");
    private static final UUID TARGET_ID =
            UUID.fromString("dad9430f-093b-42e4-bc1a-57a5730b3a17");
    private static final UUID OPERATION_ID =
            UUID.fromString("32e4327f-8647-4772-988a-527c37c44029");
    private static final CaseId CASE_ID = new CaseId("01HZX3K8M2N4P6QR");
    private static final String STALL_ID = "stall-1";
    private static final String SNAPSHOT = "a".repeat(64);
    private static final String HELD = "b".repeat(64);

    private MarketComplianceCoordinatorTest.FakeStore store;
    private MarketComplianceCoordinatorTest.FakeGateway gateway;
    private OperationalMode mode;
    private MarketComplianceCoordinator coordinator;

    @BeforeEach
    void setUp() {
        store = new MarketComplianceCoordinatorTest.FakeStore();
        gateway = new MarketComplianceCoordinatorTest.FakeGateway();
        mode = OperationalMode.ACTIVE;
        coordinator = coordinator(
                new DefaultAuthorizationPolicy(),
                new MarketComplianceCoordinatorTest.FixedCaseLookup()
        );
    }

    @Test
    void offDutyAdminCannotStartMarketMutation() {
        coordinator = coordinator(
                new ActiveDutyAuthorizationPolicy(
                        new DefaultAuthorizationPolicy(),
                        ignored -> false
                ),
                new MarketComplianceCoordinatorTest.FixedCaseLookup()
        );

        MarketCoordinationResult result = prepare(admin());

        assertEquals(MarketCoordinationResult.Status.REJECTED, result.status());
        assertTrue(store.find(OPERATION_ID).isEmpty());
        assertEquals(0, gateway.preparations.get());
    }

    @Test
    void caseTargetMismatchRejectsBeforeIntentAndProviderCall() {
        coordinator = coordinator(
                new DefaultAuthorizationPolicy(),
                new MarketComplianceCoordinatorTest.FixedCaseLookup(UUID.randomUUID())
        );

        MarketCoordinationResult result = prepare(admin());

        assertEquals(MarketCoordinationResult.Status.REJECTED, result.status());
        assertTrue(store.find(OPERATION_ID).isEmpty());
        assertEquals(0, gateway.preparations.get());
    }

    @Test
    void providerRejectionBecomesDurableRejectedState() {
        gateway.prepare = request -> completed(new MarketOperationResult(
                MarketOperationResult.Status.REJECTED,
                Optional.empty(),
                "target no longer owns stall"
        ));

        MarketCoordinationResult result = prepare(admin());

        assertEquals(MarketCoordinationResult.Status.UPDATED, result.status());
        assertEquals(
                MarketComplianceState.REJECTED,
                result.operation().orElseThrow().state()
        );
    }

    @Test
    void preparedOperationCanBeReleasedWithoutConfiscation() {
        AtomicReference<MarketOperationRequest> prepared = prepareSuccessfully();
        gateway.release = (operationId, checksum) -> {
            assertEquals(OPERATION_ID, operationId);
            assertEquals(SNAPSHOT, checksum);
            return completed(operationResult(
                    prepared.get(),
                    MarketOperationRecord.State.RELEASED,
                    Optional.empty()
            ));
        };

        MarketCoordinationResult result = coordinator.release(admin(), OPERATION_ID)
                .toCompletableFuture().join();

        assertEquals(MarketCoordinationResult.Status.UPDATED, result.status());
        assertEquals(
                MarketComplianceState.RELEASED,
                result.operation().orElseThrow().state()
        );
        assertEquals(0, gateway.confiscations.get());
    }

    @Test
    void founderCanRestoreHoldReviewedByDifferentStaffMember() {
        AtomicReference<MarketOperationRequest> prepared = prepareSuccessfully();
        gateway.confiscate = approval -> completed(operationResult(
                prepared.get(),
                MarketOperationRecord.State.MODERATION_HOLD,
                Optional.of(approval.reviewerId())
        ));
        coordinator.approveConfiscation(admin(), OPERATION_ID)
                .toCompletableFuture().join();
        gateway.restore = request -> {
            assertEquals(HELD, request.expectedCurrentChecksum());
            return completed(operationResult(
                    prepared.get(),
                    MarketOperationRecord.State.RESTORED,
                    Optional.of(request.reviewerId())
            ));
        };

        MarketCoordinationResult result = coordinator.restore(founder(), OPERATION_ID)
                .toCompletableFuture().join();

        assertEquals(MarketCoordinationResult.Status.UPDATED, result.status());
        assertEquals(
                MarketComplianceState.RESTORED,
                result.operation().orElseThrow().state()
        );
        assertEquals(
                FOUNDER_ID,
                result.operation().orElseThrow().reviewedBy().orElseThrow()
        );
    }

    @Test
    void blacklistApplyPersistsProviderState() {
        gateway.applyBlacklist = request -> completed(new MarketBlacklistResult(
                MarketBlacklistResult.Status.APPLIED,
                Optional.of(new StallBlacklistState(
                        request.targetId(),
                        StallBlacklistState.Status.ACTIVE,
                        request.expiresAt(),
                        request.caseId(),
                        request.operationId(),
                        1L,
                        NOW
                )),
                "blacklist applied"
        ));

        MarketCoordinationResult result = coordinator.applyBlacklist(
                admin(), TARGET_ID, CASE_ID, Optional.empty()
        ).toCompletableFuture().join();

        assertEquals(MarketCoordinationResult.Status.UPDATED, result.status());
        assertEquals(
                MarketComplianceState.BLACKLIST_ACTIVE,
                result.operation().orElseThrow().state()
        );
    }

    @Test
    void staleBlacklistRemovalIsQuarantinedWithoutGuessing() {
        gateway.removeBlacklist = removal -> completed(new MarketBlacklistResult(
                MarketBlacklistResult.Status.CONFLICT,
                Optional.of(new StallBlacklistState(
                        removal.targetId(),
                        StallBlacklistState.Status.ACTIVE,
                        Optional.empty(),
                        removal.caseId(),
                        UUID.randomUUID(),
                        removal.expectedRevision() + 1L,
                        NOW
                )),
                "blacklist revision changed"
        ));

        MarketCoordinationResult result = coordinator.removeBlacklist(
                admin(), TARGET_ID, CASE_ID, 1L
        ).toCompletableFuture().join();

        assertEquals(MarketCoordinationResult.Status.QUARANTINED, result.status());
        assertEquals(
                MarketComplianceState.QUARANTINED,
                result.operation().orElseThrow().state()
        );
    }

    private MarketComplianceCoordinator coordinator(
            AuthorizationPolicy authorization,
            net.enthusia.staff.domain.ports.CaseLookup cases
    ) {
        return new MarketComplianceCoordinator(
                new MarketCoordinatorRuntime(
                        Clock.fixed(NOW, ZoneOffset.UTC),
                        () -> mode,
                        authorization,
                        () -> store,
                        () -> cases,
                        Runnable::run
                ),
                gateway,
                () -> OPERATION_ID
        );
    }

    private MarketCoordinationResult prepare(Actor actor) {
        return coordinator.prepareStall(
                actor,
                TARGET_ID,
                CASE_ID,
                STALL_ID,
                Optional.empty()
        ).toCompletableFuture().join();
    }

    private AtomicReference<MarketOperationRequest> prepareSuccessfully() {
        AtomicReference<MarketOperationRequest> prepared = new AtomicReference<>();
        gateway.prepare = request -> {
            prepared.set(request);
            return completed(operationResult(
                    request,
                    MarketOperationRecord.State.PREPARED,
                    Optional.empty()
            ));
        };
        MarketCoordinationResult result = prepare(admin());
        assertEquals(MarketComplianceState.PREPARED, result.operation().orElseThrow().state());
        return prepared;
    }

    private static MarketOperationResult operationResult(
            MarketOperationRequest request,
            MarketOperationRecord.State state,
            Optional<UUID> reviewer
    ) {
        Optional<String> current = switch (state) {
            case MODERATION_HOLD -> Optional.of(HELD);
            case RESTORED -> Optional.of(SNAPSHOT);
            default -> Optional.empty();
        };
        MarketOperationRecord record = new MarketOperationRecord(
                request.operationId(),
                request.targetId(),
                request.caseId(),
                request.stallId(),
                state,
                SNAPSHOT,
                current,
                reviewer,
                request.reviewDueAt(),
                request.recoveryUntil(),
                2L,
                state.name(),
                NOW
        );
        MarketOperationResult.Status resultStatus = switch (state) {
            case PREPARED -> MarketOperationResult.Status.PREPARED;
            case MODERATION_HOLD -> MarketOperationResult.Status.HELD;
            case RESTORED -> MarketOperationResult.Status.RESTORED;
            case RELEASED -> MarketOperationResult.Status.RELEASED;
            case QUARANTINED -> MarketOperationResult.Status.QUARANTINED;
        };
        return new MarketOperationResult(
                resultStatus,
                Optional.of(record),
                state.name()
        );
    }

    private static Actor admin() {
        return new Actor(ADMIN_ID, "admin", StaffRank.ADMIN);
    }

    private static Actor founder() {
        return new Actor(FOUNDER_ID, "founder", StaffRank.FOUNDER);
    }

    private static <T> CompletionStage<T> completed(T value) {
        return CompletableFuture.completedFuture(value);
    }
}
