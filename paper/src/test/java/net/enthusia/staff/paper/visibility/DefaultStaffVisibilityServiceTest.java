package net.enthusia.staff.paper.visibility;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.enthusia.staff.domain.auth.StaffRank;
import org.junit.jupiter.api.Test;

class DefaultStaffVisibilityServiceTest {
    @Test
    void helpersAndModeratorsCanSeeEachOtherWhileVanished() {
        DefaultStaffVisibilityService visibility = new DefaultStaffVisibilityService(
                DefaultStaffVisibilityService.defaultMatrix()
        );
        UUID helperViewer = UUID.randomUUID();
        UUID modViewer = UUID.randomUUID();
        UUID vanishedHelper = UUID.randomUUID();
        UUID vanishedMod = UUID.randomUUID();

        visibility.setViewerRank(helperViewer, StaffRank.HELPER);
        visibility.setViewerRank(modViewer, StaffRank.MOD);
        visibility.setVanished(vanishedHelper, StaffRank.HELPER, true);
        visibility.setVanished(vanishedMod, StaffRank.MOD, true);

        assertTrue(visibility.canSee(helperViewer, vanishedHelper));
        assertTrue(visibility.canSee(helperViewer, vanishedMod));
        assertTrue(visibility.canSee(modViewer, vanishedHelper));
        assertTrue(visibility.canSee(modViewer, vanishedMod));
    }

    @Test
    void removingViewerAuthorityDoesNotClearVanishedTargetState() {
        DefaultStaffVisibilityService visibility = new DefaultStaffVisibilityService(
                DefaultStaffVisibilityService.defaultMatrix()
        );
        UUID vanishedPlayer = UUID.randomUUID();

        visibility.setViewerRank(vanishedPlayer, StaffRank.MOD);
        visibility.setVanished(vanishedPlayer, StaffRank.MOD, true);
        visibility.removeViewer(vanishedPlayer);

        assertTrue(visibility.isVanished(vanishedPlayer));
    }

    @Test
    void legacyMatrixCannotHideHelperFromSupervisingRanks() {
        DefaultStaffVisibilityService visibility = new DefaultStaffVisibilityService(Map.of(
                StaffRank.MOD, Set.of(StaffRank.MOD, StaffRank.DEVELOPER),
                StaffRank.DEVELOPER, Set.of(StaffRank.MOD, StaffRank.DEVELOPER),
                StaffRank.ADMIN, Set.of(StaffRank.MOD, StaffRank.DEVELOPER, StaffRank.ADMIN),
                StaffRank.FOUNDER, Set.of(StaffRank.MOD, StaffRank.DEVELOPER, StaffRank.ADMIN, StaffRank.FOUNDER)
        ));
        UUID modViewer = UUID.randomUUID();
        UUID vanishedHelper = UUID.randomUUID();

        visibility.setViewerRank(modViewer, StaffRank.MOD);
        visibility.setVanished(vanishedHelper, StaffRank.HELPER, true);

        assertTrue(visibility.canSee(modViewer, vanishedHelper));
    }
    @Test
    void staffModeIsPrivateWithoutVanishAndUnknownDutyRankFailsClosed() {
        DefaultStaffVisibilityService visibility = new DefaultStaffVisibilityService(
                DefaultStaffVisibilityService.defaultMatrix());
        UUID target = UUID.randomUUID();
        UUID viewer = UUID.randomUUID();
        java.util.concurrent.atomic.AtomicReference<StaffRank> rank =
                new java.util.concurrent.atomic.AtomicReference<>(StaffRank.MOD);
        visibility.setStaffModeVisibility(id -> id.equals(target), id -> rank.get());
        assertFalse(visibility.isVanished(target));
        assertFalse(visibility.canSee(viewer, target));
        visibility.setViewerRank(viewer, StaffRank.FOUNDER);
        assertTrue(visibility.canSee(viewer, target));
        rank.set(null);
        assertFalse(visibility.canSee(viewer, target));
        assertTrue(visibility.canSee(target, target));
        visibility.setStaffModeVisibility(id -> false, id -> null);
        assertTrue(visibility.canSee(viewer, target));
    }
}
