package net.enthusia.staff.paper.punishment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static net.enthusia.staff.paper.punishment.PunishmentRequestControllerHarness.REQUEST_ID;
import static net.enthusia.staff.paper.punishment.PunishmentRequestControllerHarness.REVIEWER_ID;
import static net.enthusia.staff.paper.punishment.PunishmentRequestControllerHarness.acquire;
import static net.enthusia.staff.paper.punishment.PunishmentRequestControllerHarness.click;
import static net.enthusia.staff.paper.punishment.PunishmentRequestControllerHarness.deniedRequest;
import static net.enthusia.staff.paper.punishment.PunishmentRequestControllerHarness.fixture;
import static net.enthusia.staff.paper.punishment.PunishmentRequestControllerHarness.pending;
import static net.enthusia.staff.paper.punishment.PunishmentRequestControllerHarness.review;

import net.enthusia.staff.domain.application.PunishmentApprovalLease;
import net.enthusia.staff.domain.application.PunishmentApprovalRequest;
import net.enthusia.staff.domain.application.PunishmentRequestStatus;
import net.enthusia.staff.domain.auth.StaffRank;
import net.enthusia.staff.paper.punishment.PunishmentRequestControllerHarness.Fixture;
import net.enthusia.staff.paper.punishment.PunishmentRequestControllerHarness.PlayerHarness;
import net.enthusia.staff.paper.punishment.PunishmentRequestControllerHarness.SchedulerHarness;
import net.enthusia.staff.paper.punishment.PunishmentRequestControllerHarness.SchedulerMode;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.junit.jupiter.api.Test;

class PunishmentRequestControllerSchedulingIntegrationTest {
    @Test
    void queueCompletionSchedulesTheRealPresentationOnThePlayerScheduler() {
        Fixture fixture = fixture(pending(StaffRank.MOD), SchedulerMode.HOLD);

        assertTrue(fixture.controller().openQueue(fixture.player().proxy()));

        assertEquals(1, fixture.store().pendingReads);
        assertEquals(1, fixture.scheduler().scheduled.get());
        assertEquals(1, fixture.scheduler().heldActions.size());
    }

    @Test
    void pendingReviewCompletionSchedulesReviewPresentationOnThePlayerScheduler() {
        Fixture fixture = fixture(pending(StaffRank.MOD), SchedulerMode.HOLD);

        assertTrue(fixture.controller().openReview(fixture.player().proxy(), REQUEST_ID));

        assertEquals(1, fixture.store().acquireCalls);
        assertEquals(1, fixture.scheduler().scheduled.get());
    }

    @Test
    void resolvedRequestCompletionSchedulesDetailsPresentationOnThePlayerScheduler() {
        Fixture fixture = fixture(deniedRequest(), SchedulerMode.HOLD);

        assertTrue(fixture.controller().openReview(fixture.player().proxy(), REQUEST_ID));

        assertEquals(0, fixture.store().acquireCalls);
        assertEquals(1, fixture.scheduler().scheduled.get());
    }

    @Test
    void missingRequestMessageRunsOnThePlayerScheduler() {
        Fixture fixture = fixture(null, SchedulerMode.RUN);

        assertTrue(fixture.controller().openReview(fixture.player().proxy(), REQUEST_ID));

        assertEquals(1, fixture.scheduler().scheduled.get());
        assertEquals(1, fixture.player().messages.get());
    }

    @Test
    void forbiddenRequestMessageRunsOnThePlayerScheduler() {
        Fixture fixture = fixture(pending(StaffRank.ADMIN), SchedulerMode.RUN);

        assertTrue(fixture.controller().openReview(fixture.player().proxy(), REQUEST_ID));

        assertEquals(1, fixture.scheduler().scheduled.get());
        assertEquals(1, fixture.player().messages.get());
        assertEquals(0, fixture.store().acquireCalls);
    }

    @Test
    void acquireConflictMessageRunsOnThePlayerScheduler() {
        Fixture fixture = fixture(pending(StaffRank.MOD), SchedulerMode.RUN);
        fixture.store().rejectAcquire = true;

        assertTrue(fixture.controller().openReview(fixture.player().proxy(), REQUEST_ID));

        assertEquals(1, fixture.store().acquireCalls);
        assertEquals(1, fixture.scheduler().scheduled.get());
        assertEquals(1, fixture.player().messages.get());
    }

    @Test
    void storageFailureMessageReturnsThroughThePlayerScheduler() {
        Fixture fixture = fixture(pending(StaffRank.MOD), SchedulerMode.RUN);
        fixture.store().failPending = true;

        assertTrue(fixture.controller().openQueue(fixture.player().proxy()));

        assertEquals(1, fixture.scheduler().scheduled.get());
        assertEquals(1, fixture.player().messages.get());
        assertEquals(1, fixture.logger().severe.get());
    }

    @Test
    void retiredPlayerDropsQueueCallbackAndReconnectDoesNotReceiveIt() {
        Fixture fixture = fixture(pending(StaffRank.MOD), SchedulerMode.RETIRE);
        SchedulerHarness reconnectScheduler = new SchedulerHarness(SchedulerMode.RUN);
        PlayerHarness reconnect = new PlayerHarness(REVIEWER_ID, reconnectScheduler);

        assertTrue(fixture.controller().openQueue(fixture.player().proxy()));

        assertEquals(1, fixture.scheduler().scheduled.get());
        assertEquals(0, fixture.player().openInventories.get());
        assertEquals(0, reconnectScheduler.scheduled.get());
        assertEquals(0, reconnect.messages.get());
    }

    @Test
    void schedulerRetirementPlusFalseReturnIsHandledExactlyOnceByControllerPath() {
        Fixture fixture = fixture(pending(StaffRank.MOD), SchedulerMode.RETIRE_FALSE);

        assertTrue(fixture.controller().openQueue(fixture.player().proxy()));

        assertEquals(1, fixture.scheduler().scheduled.get());
        assertEquals(1, fixture.logger().retired.get());
        assertEquals(0, fixture.player().openInventories.get());
    }

    @Test
    void approvedDecisionCommitsOnceAndPresentsOnThePlayerScheduler() {
        Fixture fixture = fixture(pending(StaffRank.MOD), SchedulerMode.RUN);
        PunishmentApprovalLease lease = acquire(fixture);
        InventoryClickEvent approve = click(fixture.player().proxy(), review(lease), PunishmentRequestGuiRenderer.APPROVE_SLOT);

        fixture.controller().onInventoryClick(approve);

        assertEquals(1, fixture.store().approveTransitions);
        assertEquals(PunishmentRequestStatus.APPROVED, fixture.store().current.status());
        assertEquals(1, fixture.player().messages.get());
        assertEquals(1, fixture.scheduler().scheduled.get());

        fixture.scheduler().mode = SchedulerMode.HOLD;
        fixture.controller().onInventoryClick(click(
                fixture.player().proxy(),
                review(lease),
                PunishmentRequestGuiRenderer.APPROVE_SLOT
        ));

        assertEquals(1, fixture.store().approveTransitions);
        assertEquals(PunishmentRequestStatus.APPROVED, fixture.store().current.status());
        assertEquals(2, fixture.scheduler().scheduled.get());
    }

    @Test
    void refreshAfterApprovedDecisionDoesNotCreateASecondDurableDecision() {
        Fixture fixture = fixture(pending(StaffRank.MOD), SchedulerMode.RUN);
        PunishmentApprovalLease lease = acquire(fixture);
        fixture.controller().onInventoryClick(click(
                fixture.player().proxy(),
                review(lease),
                PunishmentRequestGuiRenderer.APPROVE_SLOT
        ));
        PunishmentApprovalRequest approved = fixture.store().current;
        fixture.scheduler().mode = SchedulerMode.HOLD;

        fixture.controller().onInventoryClick(click(
                fixture.player().proxy(),
                new PunishmentRequestGuiState.Details(
                        new PunishmentRequestGuiState.RequestView(approved, "Target"),
                        0
                ),
                PunishmentRequestGuiRenderer.DETAILS_REFRESH_SLOT
        ));

        assertEquals(1, fixture.store().approveTransitions);
        assertEquals(0, fixture.store().denyTransitions);
        assertEquals(PunishmentRequestStatus.APPROVED, fixture.store().current.status());
        assertEquals(2, fixture.scheduler().scheduled.get());
    }

    @Test
    void deniedDecisionSurvivesPresentationRetirementAndRetryDoesNotDuplicateIt() {
        Fixture fixture = fixture(pending(StaffRank.MOD), SchedulerMode.RETIRE);
        PunishmentApprovalLease lease = acquire(fixture);
        PunishmentRequestGuiState.Denial denial = new PunishmentRequestGuiState.Denial(lease, "Target", 0);
        int presetSlot = PunishmentRequestDenialPreset.values()[0].slot();

        fixture.controller().onInventoryClick(click(fixture.player().proxy(), denial, presetSlot));

        assertEquals(1, fixture.store().denyTransitions);
        assertEquals(PunishmentRequestStatus.DENIED, fixture.store().current.status());
        assertEquals(0, fixture.player().messages.get());
        assertEquals(1, fixture.scheduler().scheduled.get());

        fixture.controller().onInventoryClick(click(fixture.player().proxy(), denial, presetSlot));

        assertEquals(1, fixture.store().denyTransitions);
        assertEquals(PunishmentRequestStatus.DENIED, fixture.store().current.status());
        assertEquals(2, fixture.scheduler().scheduled.get());
    }

    @Test
    void workerAdmissionRejectionStartsNoStorageWork() {
        Fixture fixture = fixture(pending(StaffRank.MOD), SchedulerMode.RUN);
        fixture.executor().reject = true;

        assertTrue(fixture.controller().openQueue(fixture.player().proxy()));

        assertEquals(0, fixture.store().pendingReads);
        assertEquals(0, fixture.store().findReads);
        assertEquals(0, fixture.store().acquireCalls);
        assertEquals(0, fixture.scheduler().scheduled.get());
        assertEquals(1, fixture.player().messages.get());
        assertEquals(1, fixture.logger().warnings.get());
    }

}
