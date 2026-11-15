package net.enthusia.staff.discordbot;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import net.enthusia.staff.domain.auth.StaffRank;
import net.enthusia.staff.discordbot.JdaStaffModerationListener.ReviewQueueView;
import org.junit.jupiter.api.Test;

class ReportQueueRankGateTest {
    @Test void helpersAndDevelopersCanViewOnlyReports() {
        for (StaffRank rank : new StaffRank[] {StaffRank.HELPER, StaffRank.DEVELOPER}) {
            assertTrue(JdaStaffModerationListener.reviewQueueAllowed(rank, ReviewQueueView.REPORTS));
            assertFalse(JdaStaffModerationListener.canMutateReportInDiscord(rank));
            assertFalse(JdaStaffModerationListener.mayPreviewReportText(ReviewQueueView.REPORTS, rank));
            assertFalse(JdaStaffModerationListener.reviewQueueAllowed(rank, ReviewQueueView.ALL));
            assertFalse(JdaStaffModerationListener.reviewQueueAllowed(rank, ReviewQueueView.PUNISHMENTS));
            assertFalse(JdaStaffModerationListener.reviewQueueAllowed(rank, ReviewQueueView.ALTS));
        }
    }

    @Test void modAndAboveKeepNormalReviewQueuesButSystemCannotInvoke() {
        for (StaffRank rank : new StaffRank[] {StaffRank.MOD, StaffRank.ADMIN, StaffRank.FOUNDER}) {
            for (ReviewQueueView view : ReviewQueueView.values()) {
                assertTrue(JdaStaffModerationListener.reviewQueueAllowed(rank, view));
                assertTrue(JdaStaffModerationListener.canMutateReportInDiscord(rank));
                assertTrue(JdaStaffModerationListener.mayPreviewReportText(ReviewQueueView.REPORTS, rank));
                assertFalse(JdaStaffModerationListener.mayPreviewReportText(ReviewQueueView.ALL, rank));
            }
        }
        assertFalse(JdaStaffModerationListener.reviewQueueAllowed(StaffRank.SYSTEM, ReviewQueueView.REPORTS));
        assertFalse(JdaStaffModerationListener.reviewQueueAllowed(null, ReviewQueueView.REPORTS));
        assertFalse(JdaStaffModerationListener.canMutateReportInDiscord(StaffRank.SYSTEM));
        assertFalse(JdaStaffModerationListener.canMutateReportInDiscord(null));
    }
}
