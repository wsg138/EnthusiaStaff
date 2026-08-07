package net.enthusia.staff.paper.staff;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import net.enthusia.staff.domain.auth.StaffRank;
import org.junit.jupiter.api.Test;

class StaffToolDefinitionTest {
    @Test
    void canonicalIdsAndSlotsAreUniqueAndCoverTheNineHotbarPositions() {
        Set<String> ids = Arrays.stream(StaffToolDefinition.values())
                .map(StaffToolDefinition::id)
                .collect(Collectors.toSet());
        Set<Integer> slots = Arrays.stream(StaffToolDefinition.values())
                .map(StaffToolDefinition::slot)
                .collect(Collectors.toSet());

        assertEquals(StaffToolDefinition.values().length, ids.size());
        assertEquals(StaffToolDefinition.values().length, slots.size());
        assertEquals(Set.of(0, 1, 2, 3, 4, 5, 6, 7, 8), slots);
    }

    @Test
    void fixedSlotOrderingMatchesDocumentedStaffHotbar() {
        assertEquals(0, StaffToolDefinition.RANDOM_TELEPORT.slot());
        assertEquals(1, StaffToolDefinition.PLAYER_INSPECTOR.slot());
        assertEquals(2, StaffToolDefinition.FREEZE.slot());
        assertEquals(3, StaffToolDefinition.REPORTS.slot());
        assertEquals(4, StaffToolDefinition.CHEAT_TESTER.slot());
        assertEquals(5, StaffToolDefinition.SPECTATE.slot());
        assertEquals(6, StaffToolDefinition.VANISH.slot());
        assertEquals(7, StaffToolDefinition.STAFF_CHAT.slot());
        assertEquals(8, StaffToolDefinition.STAFF_TOOLS.slot());
    }

    @Test
    void operationalToolsExcludeDeferredCheatTesterForEveryRank() {
        assertTrue(StaffToolDefinition.RANDOM_TELEPORT.availableFor(StaffRank.HELPER));
        assertTrue(StaffToolDefinition.STAFF_TOOLS.availableFor(StaffRank.HELPER));
        for (StaffRank rank : StaffRank.values()) {
            assertFalse(StaffToolDefinition.CHEAT_TESTER.availableFor(rank));
        }
        assertFalse(StaffToolDefinition.CHEAT_TESTER.availableFor(null));
    }

    @Test
    void idsRoundTripAndUnknownTagsDoNotResolve() {
        for (StaffToolDefinition tool : StaffToolDefinition.values()) {
            assertEquals(tool, StaffToolDefinition.fromId(tool.id()).orElseThrow());
        }
        assertTrue(StaffToolDefinition.fromId("spoofed-tool").isEmpty());
        assertTrue(StaffToolDefinition.fromId("").isEmpty());
        assertTrue(StaffToolDefinition.fromId(null).isEmpty());
    }
}
