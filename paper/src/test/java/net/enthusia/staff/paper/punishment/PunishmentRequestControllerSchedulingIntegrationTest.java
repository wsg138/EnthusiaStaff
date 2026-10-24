package net.enthusia.staff.paper.punishment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static net.enthusia.staff.paper.punishment.PunishmentRequestControllerHarness.REQUEST_ID;
import static net.enthusia.staff.paper.punishment.PunishmentRequestControllerHarness.REVIEWER_ID;
import static net.enthusia.staff.paper.punishment.PunishmentRequestControllerHarness.acquire;
import static net.enthusia.staff.paper.punishment.PunishmentRequestControllerHarness.click;
import static net.enthusia.staff.paper.punishment.PunishmentRequestControllerHarness.close;
import static net.enthusia.staff.paper.punishment.PunishmentRequestControllerHarness.viewForState;
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
import org.bukkit.event.player.PlayerQuitEvent;
import net.kyori.adventure.text.Component;
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
        assertEquals(2, fixture.scheduler().scheduled.get(),
                "Worker authorization and result presentation each use the entity scheduler");

        fixture.controller().onInventoryClick(click(
                fixture.player().proxy(),
                review(lease),
                PunishmentRequestGuiRenderer.APPROVE_SLOT
        ));

        assertEquals(1, fixture.store().approveTransitions);
        assertEquals(PunishmentRequestStatus.APPROVED, fixture.store().current.status());
        assertEquals(4, fixture.scheduler().scheduled.get());
    }

    @Test
    void lostAcknowledgementAfterDurableApprovalDoesNotFalselyClaimNoAction() {
        Fixture fixture = fixture(pending(StaffRank.MOD), SchedulerMode.RUN);
        PunishmentApprovalLease lease = acquire(fixture);
        fixture.store().failAfterApprove = true;

        fixture.controller().onInventoryClick(click(
                fixture.player().proxy(), review(lease), PunishmentRequestGuiRenderer.APPROVE_SLOT
        ));

        assertEquals(1, fixture.store().approveTransitions);
        assertEquals(PunishmentRequestStatus.APPROVED, fixture.store().current.status());
        assertEquals(1, fixture.player().messages.get());
        assertEquals(1, fixture.logger().severe.get());
        assertTrue(fixture.player().lastMessage.contains("may have been committed"));
        assertTrue(fixture.player().lastMessage.contains("Check case history"));
        assertTrue(!fixture.player().lastMessage.contains("no action was taken"));
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
        assertEquals(3, fixture.scheduler().scheduled.get());
    }

    @Test
    void deniedDecisionSurvivesPresentationRetirementAndRetryDoesNotDuplicateIt() {
        Fixture fixture = fixture(pending(StaffRank.MOD), SchedulerMode.RETIRE_AFTER_FIRST);
        PunishmentApprovalLease lease = acquire(fixture);
        PunishmentRequestGuiState.Denial denial = new PunishmentRequestGuiState.Denial(lease, "Target", 0);
        int presetSlot = PunishmentRequestDenialPreset.values()[0].slot();

        fixture.controller().onInventoryClick(click(fixture.player().proxy(), denial, presetSlot));

        assertEquals(1, fixture.store().denyTransitions);
        assertEquals(PunishmentRequestStatus.DENIED, fixture.store().current.status());
        assertEquals(0, fixture.player().messages.get());
        assertEquals(2, fixture.scheduler().scheduled.get());

        fixture.controller().onInventoryClick(click(fixture.player().proxy(), denial, presetSlot));

        assertEquals(1, fixture.store().denyTransitions);
        assertEquals(PunishmentRequestStatus.DENIED, fixture.store().current.status());
        assertEquals(3, fixture.scheduler().scheduled.get());
    }

    @Test
    void queuedApprovalListDoesNotRenderAfterReviewerLosesPermission() {
        Fixture fixture = fixture(pending(StaffRank.MOD), SchedulerMode.HOLD);
        assertTrue(fixture.controller().openQueue(fixture.player().proxy()));
        assertEquals(1, fixture.scheduler().heldActions.size());

        fixture.player().authorized = false;
        fixture.scheduler().heldActions.removeFirst().run();

        assertEquals(0, fixture.player().openInventories.get());
        assertEquals(1, fixture.player().closedInventories.get());
        assertEquals(1, fixture.player().messages.get());
        assertEquals(1, fixture.store().pendingReads);
    }

    @Test
    void resolvedRequestDetailsDoNotRenderAfterReviewerLosesPermission() {
        Fixture fixture = fixture(deniedRequest(), SchedulerMode.HOLD);
        assertTrue(fixture.controller().openReview(fixture.player().proxy(), REQUEST_ID));
        assertEquals(1, fixture.scheduler().heldActions.size());

        fixture.player().authorized = false;
        fixture.scheduler().heldActions.removeFirst().run();

        assertEquals(0, fixture.player().openInventories.get());
        assertEquals(1, fixture.player().closedInventories.get());
        assertEquals(1, fixture.player().messages.get());
    }

    @Test
    void pendingReviewDoesNotRenderAndReleasesLeaseAfterReviewerLosesPermission() {
        Fixture fixture = fixture(pending(StaffRank.MOD), SchedulerMode.HOLD);
        assertTrue(fixture.controller().openReview(fixture.player().proxy(), REQUEST_ID));
        assertEquals(1, fixture.store().acquireCalls);
        assertEquals(1, fixture.scheduler().heldActions.size());

        fixture.player().authorized = false;
        fixture.scheduler().heldActions.removeFirst().run();

        assertEquals(0, fixture.player().openInventories.get());
        assertEquals(1, fixture.player().closedInventories.get());
        assertEquals(1, fixture.player().messages.get());
        assertEquals(1, fixture.store().abandonCalls);
    }

    @Test
    void retiredReviewerReleasesUnpresentedLeaseExactlyOnce() {
        Fixture fixture = fixture(pending(StaffRank.MOD), SchedulerMode.RETIRE_FALSE);
        assertTrue(fixture.controller().openReview(fixture.player().proxy(), REQUEST_ID));

        assertEquals(1, fixture.store().acquireCalls);
        assertEquals(1, fixture.store().abandonCalls);
        assertEquals(0, fixture.player().openInventories.get());
        assertEquals(1, fixture.logger().retired.get());
    }

    @Test
    void committedDecisionIsAcknowledgedWithoutRevealingDetailsAfterDemotion() {
        Fixture fixture = fixture(pending(StaffRank.MOD), SchedulerMode.HOLD_AFTER_FIRST);
        PunishmentApprovalLease lease = acquire(fixture);
        fixture.controller().onInventoryClick(click(
                fixture.player().proxy(), review(lease), PunishmentRequestGuiRenderer.APPROVE_SLOT));
        assertEquals(PunishmentRequestStatus.APPROVED, fixture.store().current.status());
        assertEquals(1, fixture.scheduler().heldActions.size());

        fixture.player().authorized = false;
        fixture.scheduler().heldActions.removeFirst().run();

        assertEquals(1, fixture.store().approveTransitions);
        assertEquals(0, fixture.player().openInventories.get());
        assertTrue(fixture.player().messages.get() >= 1);
        assertTrue(fixture.player().closedInventories.get() >= 2);
    }

    @Test
    void alreadyOpenReviewCannotBeClickedAfterReviewerLosesPermission() {
        Fixture fixture = fixture(pending(StaffRank.MOD), SchedulerMode.RUN);
        PunishmentApprovalLease lease = acquire(fixture);
        fixture.player().authorized = false;

        fixture.controller().onInventoryClick(click(
                fixture.player().proxy(), review(lease), PunishmentRequestGuiRenderer.DENY_SLOT));

        assertEquals(0, fixture.store().approveTransitions);
        assertEquals(0, fixture.store().denyTransitions);
        assertEquals(1, fixture.player().closedInventories.get());
        assertEquals(1, fixture.player().messages.get());
    }

    @Test
    void adminOnlyPendingRequestIsNotPresentedAfterReviewerDemotesToMod() {
        Fixture fixture = fixture(pending(StaffRank.ADMIN), SchedulerMode.HOLD);
        fixture.player().rank = StaffRank.ADMIN;
        assertTrue(fixture.controller().openReview(fixture.player().proxy(), REQUEST_ID));
        assertEquals(1, fixture.store().acquireCalls);
        assertEquals(1, fixture.scheduler().heldActions.size());

        fixture.player().rank = StaffRank.MOD; // Still has review permission, not required rank.
        fixture.scheduler().heldActions.removeFirst().run();

        assertEquals(0, fixture.player().openInventories.get());
        assertEquals(1, fixture.player().closedInventories.get());
        assertEquals(1, fixture.store().abandonCalls);
    }

    @Test
    void adminOnlyResolvedDetailsAreNotPresentedAfterReviewerDemotesToMod() {
        var adminRequest = pending(StaffRank.ADMIN);
        var resolved = new PunishmentApprovalRequest(
                adminRequest.requestId(), adminRequest.submissionKey(),
                adminRequest.proposal(), adminRequest.createdAt(), adminRequest.expiresAt(),
                PunishmentRequestStatus.DENIED, 1, REVIEWER_ID, "Test denial", null,
                PunishmentRequestControllerHarness.NOW.plusSeconds(1));
        Fixture fixture = fixture(resolved, SchedulerMode.HOLD);
        fixture.player().rank = StaffRank.ADMIN;
        assertTrue(fixture.controller().openReview(fixture.player().proxy(), REQUEST_ID));
        assertEquals(1, fixture.scheduler().heldActions.size());

        fixture.player().rank = StaffRank.MOD;
        fixture.scheduler().heldActions.removeFirst().run();

        assertEquals(0, fixture.player().openInventories.get());
        assertEquals(1, fixture.player().closedInventories.get());
    }

    @Test
    void queueSnapshotIsFilteredAgainstCurrentRankBeforePresentation() {
        PunishmentApprovalRequest adminOnly = pending(StaffRank.ADMIN);
        Fixture fixture = fixture(adminOnly, SchedulerMode.HOLD);
        var loaded = java.util.List.of(
                new PunishmentRequestGuiState.RequestView(adminOnly, "Target"));
        var admin = new net.enthusia.staff.domain.auth.Actor(REVIEWER_ID, "Reviewer", StaffRank.ADMIN);
        var mod = new net.enthusia.staff.domain.auth.Actor(REVIEWER_ID, "Reviewer", StaffRank.MOD);

        assertEquals(1, PunishmentRequestGuiController.visibleRequestsForActor(
                loaded, admin, fixture.service()).size());
        assertEquals(0, PunishmentRequestGuiController.visibleRequestsForActor(
                loaded, mod, fixture.service()).size());
    }

    @Test
    void alreadyOpenAdminOnlyReviewCannotBeDecidedAfterDemotionToMod() {
        Fixture fixture = fixture(pending(StaffRank.ADMIN), SchedulerMode.RUN);
        fixture.player().rank = StaffRank.ADMIN;
        var admin = new net.enthusia.staff.domain.auth.Actor(REVIEWER_ID, "Reviewer", StaffRank.ADMIN);
        PunishmentApprovalLease lease = org.junit.jupiter.api.Assertions.assertInstanceOf(
                net.enthusia.staff.domain.application.PunishmentRequestResult.Leased.class,
                fixture.service().acquire(REQUEST_ID, admin)).lease();
        fixture.player().rank = StaffRank.MOD;

        fixture.controller().onInventoryClick(click(
                fixture.player().proxy(), review(lease), PunishmentRequestGuiRenderer.APPROVE_SLOT));

        assertEquals(0, fixture.store().approveTransitions);
        assertEquals(0, fixture.store().denyTransitions);
        assertEquals(1, fixture.player().closedInventories.get());
        assertEquals(0, fixture.scheduler().scheduled.get(),
                "A revoked rank must not enqueue an approval decision worker");
    }

    @Test
    void approvedAdminOnlyDecisionAcknowledgesCommitWithoutDetailsAfterDemotion() {
        Fixture fixture = fixture(pending(StaffRank.ADMIN), SchedulerMode.HOLD_AFTER_FIRST);
        fixture.player().rank = StaffRank.ADMIN;
        var admin = new net.enthusia.staff.domain.auth.Actor(REVIEWER_ID, "Reviewer", StaffRank.ADMIN);
        PunishmentApprovalLease lease = org.junit.jupiter.api.Assertions.assertInstanceOf(
                net.enthusia.staff.domain.application.PunishmentRequestResult.Leased.class,
                fixture.service().acquire(REQUEST_ID, admin)).lease();

        fixture.controller().onInventoryClick(click(
                fixture.player().proxy(), review(lease), PunishmentRequestGuiRenderer.APPROVE_SLOT));
        assertEquals(1, fixture.store().approveTransitions);
        assertEquals(1, fixture.scheduler().heldActions.size());

        fixture.player().rank = StaffRank.MOD;
        fixture.scheduler().heldActions.removeFirst().run();

        assertEquals(1, fixture.store().approveTransitions);
        assertEquals(0, fixture.player().openInventories.get());
        assertTrue(fixture.player().messages.get() >= 1);
    }

    @Test
    void retiredReviewerBeforeWorkerPreflightCannotCommitAndReleasesLease() {
        Fixture fixture = fixture(pending(StaffRank.MOD), SchedulerMode.RETIRE);
        PunishmentApprovalLease lease = acquire(fixture);

        fixture.controller().onInventoryClick(click(
                fixture.player().proxy(), review(lease), PunishmentRequestGuiRenderer.APPROVE_SLOT));

        assertEquals(0, fixture.store().approveTransitions);
        assertEquals(PunishmentRequestStatus.PENDING, fixture.store().current.status());
        assertEquals(1, fixture.store().abandonCalls);
    }

    @Test
    void queuedApprovalMustRecheckPermissionAfterClickBeforeWorkerCommits() {
        Fixture fixture = fixture(pending(StaffRank.MOD), SchedulerMode.RUN);
        PunishmentApprovalLease lease = acquire(fixture);
        fixture.executor().hold = true;

        fixture.controller().onInventoryClick(click(
                fixture.player().proxy(), review(lease), PunishmentRequestGuiRenderer.APPROVE_SLOT));
        assertEquals(0, fixture.store().approveTransitions);
        assertEquals(1, fixture.executor().heldActions.size());

        fixture.player().authorized = false;
        fixture.executor().hold = false;
        fixture.executor().runNext();

        assertEquals(0, fixture.store().approveTransitions,
                "A revoked reviewer must not approve with the Actor captured at click time");
        assertEquals(PunishmentRequestStatus.PENDING, fixture.store().current.status());
        assertEquals(1, fixture.store().abandonCalls,
                "Rejected queued decisions should release the held lease");
    }

    @Test
    void queuedApprovalAndDenialMustRecheckRequestRankAfterClick() {
        for (boolean approve : new boolean[]{true, false}) {
            Fixture fixture = fixture(pending(StaffRank.ADMIN), SchedulerMode.RUN);
            fixture.player().rank = StaffRank.ADMIN;
            var admin = new net.enthusia.staff.domain.auth.Actor(
                    REVIEWER_ID, "Reviewer", StaffRank.ADMIN);
            PunishmentApprovalLease lease = org.junit.jupiter.api.Assertions.assertInstanceOf(
                    net.enthusia.staff.domain.application.PunishmentRequestResult.Leased.class,
                    fixture.service().acquire(REQUEST_ID, admin)).lease();
            fixture.executor().hold = true;

            if (approve) {
                fixture.controller().onInventoryClick(click(
                        fixture.player().proxy(), review(lease), PunishmentRequestGuiRenderer.APPROVE_SLOT));
            } else {
                fixture.controller().onInventoryClick(click(
                        fixture.player().proxy(),
                        new PunishmentRequestGuiState.Denial(lease, "Target", 0),
                        PunishmentRequestDenialPreset.values()[0].slot()));
            }
            assertEquals(1, fixture.executor().heldActions.size());

            fixture.player().rank = StaffRank.MOD; // Generic review permission remains.
            fixture.executor().hold = false;
            fixture.executor().runNext();

            assertEquals(0, fixture.store().approveTransitions);
            assertEquals(0, fixture.store().denyTransitions);
            assertEquals(PunishmentRequestStatus.PENDING, fixture.store().current.status());
            assertEquals(1, fixture.store().abandonCalls);
        }
    }

    @Test
    void rejectedDecisionWorkerAdmissionKeepsLeasedReviewOpenForSafeRetry() {
        for (boolean approve : new boolean[]{true, false}) {
            Fixture fixture = fixture(pending(StaffRank.MOD), SchedulerMode.RUN);
            PunishmentApprovalLease lease = acquire(fixture);
            PunishmentRequestGuiState state = approve
                    ? review(lease)
                    : new PunishmentRequestGuiState.Denial(lease, "Target", 0);
            int slot = approve
                    ? PunishmentRequestGuiRenderer.APPROVE_SLOT
                    : PunishmentRequestDenialPreset.values()[0].slot();
            fixture.executor().reject = true;

            fixture.controller().onInventoryClick(click(fixture.player().proxy(), state, slot));

            assertEquals(0, fixture.player().closedInventories.get(),
                    "A rejected decision worker must leave the leased review open for retry");
            assertEquals(1, fixture.player().messages.get());
            assertEquals(0, fixture.store().approveTransitions);
            assertEquals(0, fixture.store().denyTransitions);
            assertEquals(0, fixture.store().abandonCalls);
            assertEquals(PunishmentRequestStatus.PENDING, fixture.store().current.status());

            fixture.executor().reject = false;
            fixture.controller().onInventoryClick(click(fixture.player().proxy(), state, slot));

            assertEquals(1, fixture.player().closedInventories.get(),
                    "Only an admitted decision should close the review");
            assertEquals(approve ? 1 : 0, fixture.store().approveTransitions);
            assertEquals(approve ? 0 : 1, fixture.store().denyTransitions);
            assertEquals(approve ? PunishmentRequestStatus.APPROVED : PunishmentRequestStatus.DENIED,
                    fixture.store().current.status());
            assertEquals(0, fixture.store().abandonCalls);
        }
    }

    @Test
    void closingLeasedReviewOrDenialReleasesFenceOnWorkerNotEntityThread() {
        for (boolean denial : new boolean[]{false, true}) {
            Fixture fixture = fixture(pending(StaffRank.MOD), SchedulerMode.RUN);
            PunishmentApprovalLease lease = acquire(fixture);
            PunishmentRequestGuiState state = denial
                    ? new PunishmentRequestGuiState.Denial(lease, "Target", 0)
                    : review(lease);
            fixture.executor().hold = true;

            fixture.controller().onInventoryClose(close(fixture.player().proxy(), state));

            assertEquals(0, fixture.store().abandonCalls,
                    "Inventory close must never perform database release on the entity thread");
            assertEquals(1, fixture.executor().heldActions.size());
            fixture.executor().runNext();
            assertEquals(1, fixture.store().abandonCalls);
        }
    }

    @Test
    void reviewDenialNavigationKeepsSameLeaseAndDoesNotQueueAbandon() {
        Fixture fixture = fixture(pending(StaffRank.MOD), SchedulerMode.RUN);
        PunishmentApprovalLease lease = acquire(fixture);
        var reviewState = review(lease);
        var denialState = new PunishmentRequestGuiState.Denial(lease, "Target", 0);
        fixture.player().onOpenInventory = () ->
                fixture.controller().onInventoryClose(close(fixture.player().proxy(), reviewState));

        fixture.controller().onInventoryClick(click(
                fixture.player().proxy(), reviewState, PunishmentRequestGuiRenderer.DENY_SLOT));

        assertEquals(0, fixture.store().abandonCalls,
                "Review to denial is a lease-preserving page transition");
        fixture.player().onOpenInventory = () ->
                fixture.controller().onInventoryClose(close(fixture.player().proxy(), denialState));
        fixture.controller().onInventoryClick(click(
                fixture.player().proxy(), denialState, PunishmentRequestGuiRenderer.BACK_SLOT));
        assertEquals(0, fixture.store().abandonCalls,
                "Denial to review must preserve the same fenced lease");
    }

    @Test
    void admittedDecisionCloseCannotReleaseFenceWhileWorkerIsQueued() {
        for (boolean approve : new boolean[]{true, false}) {
            Fixture fixture = fixture(pending(StaffRank.MOD), SchedulerMode.RUN);
            PunishmentApprovalLease lease = acquire(fixture);
            PunishmentRequestGuiState state = approve
                    ? review(lease) : new PunishmentRequestGuiState.Denial(lease, "Target", 0);
            fixture.player().onCloseInventory = () ->
                    fixture.controller().onInventoryClose(close(fixture.player().proxy(), state));
            fixture.executor().hold = true;
            fixture.controller().onInventoryClick(click(fixture.player().proxy(), state,
                    approve ? PunishmentRequestGuiRenderer.APPROVE_SLOT
                            : PunishmentRequestDenialPreset.values()[0].slot()));

            assertEquals(0, fixture.store().abandonCalls);
            assertEquals(1, fixture.executor().heldActions.size(),
                    "The only queued work should be the durable decision");
            fixture.executor().hold = false;
            fixture.executor().runNext();
            assertEquals(approve ? 1 : 0, fixture.store().approveTransitions);
            assertEquals(approve ? 0 : 1, fixture.store().denyTransitions);
            assertEquals(0, fixture.store().abandonCalls);
        }
    }

    @Test
    void reviewerDisconnectReleasesOpenLeaseWithoutWaitingForExpiry() {
        Fixture fixture = fixture(pending(StaffRank.MOD), SchedulerMode.RUN);
        PunishmentApprovalLease lease = acquire(fixture);
        fixture.player().openView = viewForState(fixture.player().proxy(), review(lease));
        fixture.executor().hold = true;

        fixture.controller().onPlayerQuit(new PlayerQuitEvent(fixture.player().proxy(), Component.empty(), PlayerQuitEvent.QuitReason.DISCONNECTED));

        assertEquals(0, fixture.store().abandonCalls);
        assertEquals(1, fixture.executor().heldActions.size());
        fixture.executor().runNext();
        assertEquals(1, fixture.store().abandonCalls);
    }

    @Test
    void immediateDecisionCompletionStillSuppressesItsOwnCloseEvent() {
        Fixture fixture = fixture(pending(StaffRank.MOD), SchedulerMode.RUN);
        PunishmentApprovalLease lease = acquire(fixture);
        var state = review(lease);
        fixture.player().onCloseInventory = () ->
                fixture.controller().onInventoryClose(close(fixture.player().proxy(), state));

        fixture.controller().onInventoryClick(click(
                fixture.player().proxy(), state, PunishmentRequestGuiRenderer.APPROVE_SLOT));

        assertEquals(1, fixture.store().approveTransitions);
        assertEquals(0, fixture.store().abandonCalls,
                "An inline worker may commit before closeInventory is called");
    }

    @Test
    void failedDecisionAdmissionStillReleasesLeaseIfReviewerThenCloses() {
        Fixture fixture = fixture(pending(StaffRank.MOD), SchedulerMode.RUN);
        PunishmentApprovalLease lease = acquire(fixture);
        var state = review(lease);
        fixture.executor().reject = true;
        fixture.controller().onInventoryClick(click(
                fixture.player().proxy(), state, PunishmentRequestGuiRenderer.APPROVE_SLOT));
        assertEquals(0, fixture.store().abandonCalls);
        assertEquals(0, fixture.player().closedInventories.get());

        fixture.executor().reject = false;
        fixture.controller().onInventoryClose(close(fixture.player().proxy(), state));

        assertEquals(1, fixture.store().abandonCalls,
                "Rejected admission must clear the in-flight guard for a later close");
        assertEquals(0, fixture.store().approveTransitions);
    }

    @Test
    void disconnectWhileDecisionQueuedMustNotReleaseItsFenceBeforeCommit() {
        Fixture fixture = fixture(pending(StaffRank.MOD), SchedulerMode.RUN);
        PunishmentApprovalLease lease = acquire(fixture);
        var state = review(lease);
        fixture.player().openView = viewForState(fixture.player().proxy(), state);
        fixture.executor().hold = true;
        fixture.controller().onInventoryClick(click(
                fixture.player().proxy(), state, PunishmentRequestGuiRenderer.APPROVE_SLOT));

        fixture.controller().onPlayerQuit(new PlayerQuitEvent(
                fixture.player().proxy(), Component.empty(), PlayerQuitEvent.QuitReason.DISCONNECTED));

        assertEquals(0, fixture.store().abandonCalls);
        assertEquals(1, fixture.executor().heldActions.size());
        fixture.executor().hold = false;
        fixture.executor().runNext();
        assertEquals(1, fixture.store().approveTransitions);
        assertEquals(0, fixture.store().abandonCalls);
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
