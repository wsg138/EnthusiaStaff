package net.enthusia.staff.paper.punishment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static net.enthusia.staff.paper.punishment.PunishmentRequestControllerHarness.acquire;
import static net.enthusia.staff.paper.punishment.PunishmentRequestControllerHarness.click;
import static net.enthusia.staff.paper.punishment.PunishmentRequestControllerHarness.fixture;
import static net.enthusia.staff.paper.punishment.PunishmentRequestControllerHarness.pending;
import static net.enthusia.staff.paper.punishment.PunishmentRequestControllerHarness.review;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import net.enthusia.staff.domain.application.PunishmentApprovalLease;
import net.enthusia.staff.domain.application.PunishmentRequestStatus;
import net.enthusia.staff.domain.auth.StaffRank;
import net.enthusia.staff.paper.auth.StaffTargetGuard;
import org.bukkit.inventory.Inventory;
import org.junit.jupiter.api.Test;

/** Approval must not punish a target who became protected after submission/lease acquisition. */
class PunishmentRequestTargetHierarchyTest {
    @Test
    void promotionAfterRequestLeaseBlocksGuiApprovalAndReleasesLease() {
        AtomicBoolean protectedTarget = new AtomicBoolean();
        AtomicInteger checks = new AtomicInteger();
        var fixture = fixture(pending(StaffRank.MOD),
                PunishmentRequestControllerHarness.SchedulerMode.RUN,
                state -> inventory(state), (actor, target, system) -> {
                    checks.incrementAndGet();
                    return protectedTarget.get()
                            ? StaffTargetGuard.Result.deny("Target is now protected")
                            : StaffTargetGuard.Result.allow();
                });
        PunishmentApprovalLease lease = acquire(fixture);
        protectedTarget.set(true);

        fixture.controller().onInventoryClick(click(
                fixture.player().proxy(), review(lease), PunishmentRequestGuiRenderer.APPROVE_SLOT));

        assertTrue(checks.get() > 0, "A fresh target hierarchy check is required at approval time");
        assertEquals(0, fixture.store().approveTransitions);
        assertEquals(PunishmentRequestStatus.PENDING, fixture.store().current.status());
        assertEquals(1, fixture.store().abandonCalls);
    }

    @Test
    void promotionAfterRequestLeaseBlocksGuiDenialAndReleasesLease() {
        AtomicBoolean protectedTarget = new AtomicBoolean();
        var fixture = fixture(pending(StaffRank.MOD),
                PunishmentRequestControllerHarness.SchedulerMode.RUN,
                state -> inventory(state), (actor, target, system) -> protectedTarget.get()
                        ? StaffTargetGuard.Result.deny("Target is now protected")
                        : StaffTargetGuard.Result.allow());
        PunishmentApprovalLease lease = acquire(fixture);
        protectedTarget.set(true);

        var denial = new PunishmentRequestGuiState.Denial(lease, "Target", 0);
        fixture.controller().onInventoryClick(click(fixture.player().proxy(), denial,
                PunishmentRequestDenialPreset.values()[0].slot()));

        assertEquals(0, fixture.store().denyTransitions);
        assertEquals(PunishmentRequestStatus.PENDING, fixture.store().current.status());
        assertEquals(1, fixture.store().abandonCalls);
    }

    @Test
    void unchangedOrdinaryTargetStillAllowsAuthorizedGuiApproval() {
        AtomicInteger checks = new AtomicInteger();
        var fixture = fixture(pending(StaffRank.MOD),
                PunishmentRequestControllerHarness.SchedulerMode.RUN,
                state -> inventory(state), (actor, target, system) -> {
                    checks.incrementAndGet();
                    return StaffTargetGuard.Result.allow();
                });
        PunishmentApprovalLease lease = acquire(fixture);
        fixture.controller().onInventoryClick(click(
                fixture.player().proxy(), review(lease), PunishmentRequestGuiRenderer.APPROVE_SLOT));

        assertEquals(1, checks.get());
        assertEquals(1, fixture.store().approveTransitions);
        assertEquals(PunishmentRequestStatus.APPROVED, fixture.store().current.status());
    }

    @Test
    void hierarchyLookupFailureFailsClosedAndReturnsLease() {
        var fixture = fixture(pending(StaffRank.MOD),
                PunishmentRequestControllerHarness.SchedulerMode.RUN,
                state -> inventory(state), (actor, target, system) -> {
                    throw new IllegalStateException("LuckPerms lookup failed");
                });
        PunishmentApprovalLease lease = acquire(fixture);
        fixture.controller().onInventoryClick(click(
                fixture.player().proxy(), review(lease), PunishmentRequestGuiRenderer.APPROVE_SLOT));

        assertEquals(0, fixture.store().approveTransitions);
        assertEquals(PunishmentRequestStatus.PENDING, fixture.store().current.status());
        assertEquals(1, fixture.store().abandonCalls);
    }

    private static Inventory inventory(PunishmentRequestGuiState.Review state) {
        return PunishmentRequestControllerHarness.inventory(new PunishmentRequestGuiHolder(state));
    }
}
