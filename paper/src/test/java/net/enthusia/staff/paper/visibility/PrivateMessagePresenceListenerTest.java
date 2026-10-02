package net.enthusia.staff.paper.visibility;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.enthusia.staff.domain.auth.StaffRank;
import org.junit.jupiter.api.Test;

class PrivateMessagePresenceListenerTest {
    private static final String HIDDEN_ADMIN = "HiddenAdmin";

    private final UUID viewer = UUID.randomUUID();
    private final UUID first = UUID.randomUUID();
    private final UUID second = UUID.randomUUID();
    private final UUID ordinary = UUID.randomUUID();
    private final DefaultStaffVisibilityService visibility = new DefaultStaffVisibilityService(
            DefaultStaffVisibilityService.defaultMatrix());
    private final PrivateMessagePresenceListener listener = new PrivateMessagePresenceListener(
            visibility::canSee, Map.of(first, HIDDEN_ADMIN, second, "HiddenHelper", ordinary, "VisiblePlayer"));

    @Test
    void defaultViewerCannotCompleteMultipleHiddenNamesOrPartialNames() {
        visibility.setVanished(first, StaffRank.ADMIN, true);
        visibility.setVanished(second, StaffRank.HELPER, true);
        assertEquals(List.of("VisiblePlayer"), listener.suggestions(viewer, "/msg ").orElseThrow());
        assertEquals(List.of(), listener.suggestions(viewer, "/rosechat:msg Hid").orElseThrow());
        assertFalse(listener.targetAllowed(viewer, HIDDEN_ADMIN));
        assertFalse(listener.targetAllowed(viewer, "RemotePlayer"));
        assertTrue(listener.targetAllowed(viewer, "visibleplayer"));
    }

    @Test
    void authorizedViewerUsesCurrentRankAndVanishPolicy() {
        visibility.setVanished(first, StaffRank.ADMIN, true);
        visibility.setVanished(second, StaffRank.HELPER, true);
        visibility.setViewerRank(viewer, StaffRank.HELPER);
        assertEquals(List.of("HiddenHelper"), listener.suggestions(viewer, "/tell Hid").orElseThrow());
        visibility.setViewerRank(viewer, StaffRank.DEVELOPER);
        assertEquals(List.of(HIDDEN_ADMIN, "HiddenHelper"), listener.suggestions(viewer, "/msg Hid").orElseThrow());
        visibility.removeViewer(viewer);
        visibility.setVanished(first, StaffRank.ADMIN, false);
        assertEquals(List.of(HIDDEN_ADMIN), listener.suggestions(viewer, "/msg Hid").orElseThrow());
    }

    @Test
    void unrelatedCommandsAndMessageTextKeepTheirOwnCompletionHandling() {
        assertTrue(listener.suggestions(viewer, "/staff ").isEmpty());
        assertTrue(listener.suggestions(viewer, "/msg VisiblePlayer some text").isEmpty());
        assertTrue(listener.suggestions(viewer, "msg ").isEmpty());
    }
}
