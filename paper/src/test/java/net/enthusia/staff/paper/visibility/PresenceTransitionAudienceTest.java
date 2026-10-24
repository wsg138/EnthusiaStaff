package net.enthusia.staff.paper.visibility;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.enthusia.staff.domain.auth.StaffRank;
import org.junit.jupiter.api.Test;

final class PresenceTransitionAudienceTest {
    private static final UUID SUBJECT = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final int AUTHORIZED_VIEWER_INTERVAL = 5;
    private static final int AUTHORIZED_VIEWER_OFFSET = 4;
    private static final List<UUID> VIEWER_IDS = java.util.stream.LongStream.range(2L, 42L)
            .mapToObj(value -> new UUID(0L, value))
            .toList();

    @Test
    void onlyActualAudienceTransitionsRequireRoseChatRendering() {
        for (int count : new int[]{1, 5, 20, 40}) {
            assertAudienceCount(count);
        }
    }

    private static void assertAudienceCount(int count) {
        DefaultStaffVisibilityService visibility = visibility();
        List<UUID> viewers = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            UUID viewer = VIEWER_IDS.get(index);
            viewers.add(viewer);
            if (index % AUTHORIZED_VIEWER_INTERVAL == AUTHORIZED_VIEWER_OFFSET) {
                visibility.setViewerRank(viewer, StaffRank.ADMIN);
            }
        }

        Map<UUID, Boolean> beforeQuit = PresenceTransitionAudience.capture(
                SUBJECT, viewers, visibility::canSee);
        visibility.setVanished(SUBJECT, StaffRank.MOD, true);
        assertEquals(count - count / AUTHORIZED_VIEWER_INTERVAL, notificationCount(beforeQuit, visibility, true));

        Map<UUID, Boolean> beforeJoin = PresenceTransitionAudience.capture(
                SUBJECT, viewers, visibility::canSee);
        visibility.setVanished(SUBJECT, StaffRank.MOD, false);
        assertEquals(count - count / AUTHORIZED_VIEWER_INTERVAL, notificationCount(beforeJoin, visibility, false));
    }

    @Test
    void repeatedStateWritesDoNotProduceFakePresence() {
        DefaultStaffVisibilityService visibility = visibility();
        UUID viewer = new UUID(0L, 2L);
        visibility.setVanished(SUBJECT, StaffRank.MOD, true);
        Map<UUID, Boolean> before = PresenceTransitionAudience.capture(
                SUBJECT, List.of(viewer), visibility::canSee);
        visibility.setVanished(SUBJECT, StaffRank.MOD, true);
        assertEquals(0, notificationCount(before, visibility, true));
    }

    @Test
    void viewerRankChangeBeforePublishCannotSendAnIncorrectQuit() {
        DefaultStaffVisibilityService visibility = visibility();
        UUID viewer = new UUID(0L, 2L);
        Map<UUID, Boolean> before = PresenceTransitionAudience.capture(
                SUBJECT, List.of(viewer), visibility::canSee);
        visibility.setVanished(SUBJECT, StaffRank.MOD, true);
        visibility.setViewerRank(viewer, StaffRank.ADMIN);
        assertEquals(0, notificationCount(before, visibility, true));
    }

    @Test
    void joiningViewerIsNotRetroactivelyAddedToAnExistingTransition() {
        DefaultStaffVisibilityService visibility = visibility();
        Map<UUID, Boolean> before = PresenceTransitionAudience.capture(
                SUBJECT, List.of(SUBJECT), visibility::canSee);
        assertTrue(before.isEmpty());
        visibility.setVanished(SUBJECT, StaffRank.MOD, true);
        assertEquals(0, notificationCount(before, visibility, true));
    }

    @Test
    void rejectsDirectionMismatchAndUnchangedVisibility() {
        assertTrue(PresenceTransitionAudience.shouldNotify(true, false, true));
        assertTrue(PresenceTransitionAudience.shouldNotify(false, true, false));
        assertFalse(PresenceTransitionAudience.shouldNotify(true, true, true));
        assertFalse(PresenceTransitionAudience.shouldNotify(false, false, false));
        assertFalse(PresenceTransitionAudience.shouldNotify(false, true, true));
        assertFalse(PresenceTransitionAudience.shouldNotify(true, false, false));
    }

    private static DefaultStaffVisibilityService visibility() {
        return new DefaultStaffVisibilityService(DefaultStaffVisibilityService.defaultMatrix());
    }

    private static long notificationCount(
            Map<UUID, Boolean> before,
            DefaultStaffVisibilityService visibility,
            boolean vanished
    ) {
        return before.entrySet().stream().filter(entry -> PresenceTransitionAudience.shouldNotify(
                entry.getValue(),
                visibility.canSee(entry.getKey(), SUBJECT),
                vanished
        )).count();
    }
}
