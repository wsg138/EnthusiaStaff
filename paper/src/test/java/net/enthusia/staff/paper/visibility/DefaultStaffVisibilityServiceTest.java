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
    void helperVisibilityIsExplicitlyBelowModerator() {
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
        assertFalse(visibility.canSee(helperViewer, vanishedMod));
        assertTrue(visibility.canSee(modViewer, vanishedHelper));
        assertTrue(visibility.canSee(modViewer, vanishedMod));
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
}
