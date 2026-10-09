package net.enthusia.staff.velocity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.enthusia.staff.domain.auth.StaffRank;
import org.junit.jupiter.api.Test;

class StaffTabPresenceTest {
    private final UUID viewer = UUID.randomUUID();
    private final UUID target = UUID.randomUUID();

    @Test
    void allRankPairsFollowRequestedGroupsAndOrdinaryPlayersSeeNoVanishedStaff() {
        StaffRank[] ranks = {StaffRank.HELPER, StaffRank.MOD, StaffRank.DEVELOPER,
                StaffRank.ADMIN, StaffRank.FOUNDER};
        boolean[][] expected = {
                {true, true, true, false, false},
                {true, true, true, false, false},
                {true, true, true, true, false},
                {true, true, true, true, false},
                {true, true, true, true, true}
        };
        for (int t = 0; t < ranks.length; t++) {
            StaffTabPresence presence = presenceFor(ranks[t]);
            assertFalse(presence.canSee(viewer, null, target, ranks[t]));
            for (int v = 0; v < ranks.length; v++) {
                assertEquals(expected[v][t], presence.canSee(viewer, ranks[v], target, ranks[t]),
                        ranks[v] + " viewing " + ranks[t]);
            }
        }
    }

    private StaffTabPresence presenceFor(StaffRank rank) {
        return new StaffTabPresence(Map.of(target, rank), Set.of());
    }

    @Test
    void rankChangesCannotExposeOwnerWithOldModeratorVanishRecord() {
        StaffTabPresence presence = new StaffTabPresence(Map.of(target, StaffRank.MOD), Set.of(target));
        assertFalse(presence.canSee(viewer, StaffRank.MOD, target, StaffRank.FOUNDER));
        assertFalse(presence.canSee(viewer, StaffRank.MOD, target, null));
        assertTrue(presence.canSee(target, null, target, null));
        assertEquals("<gold>[STAFF]</gold> <aqua>[V]</aqua> ", presence.marker(target));
    }

    @Test
    void ordinaryVisiblePlayersStayVisibleWithoutStaffMarkers() {
        StaffTabPresence presence = new StaffTabPresence(Map.of(), Set.of());
        assertTrue(presence.canSee(viewer, null, target, null));
        assertEquals("", presence.marker(target));
    }
    @Test
    void staffModeWithoutVanishUsesPrivateRankVisibility() {
        StaffTabPresence presence = new StaffTabPresence(Map.of(), Set.of(target));
        assertEquals(Set.of(target), presence.hiddenFromPublic());
        assertTrue(presence.hiddenFromPublic(target));
        assertFalse(presence.canSee(viewer, null, target, StaffRank.MOD));
        assertFalse(presence.canSee(viewer, StaffRank.MOD, target, StaffRank.FOUNDER));
        assertTrue(presence.canSee(viewer, StaffRank.FOUNDER, target, StaffRank.MOD));
        assertFalse(presence.canSee(viewer, StaffRank.FOUNDER, target, null));
        assertTrue(presence.canSee(target, null, target, null));
    }
}
