package net.enthusia.staff.domain.tester;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CheatTesterTypeTest {
    @Test
    void parsesStableIdsAndSeparatesPacketOnlyTesterFromStateMutators() {
        assertEquals(CheatTesterType.TOTEM_REFILL, CheatTesterType.fromId("totem_refill").orElseThrow());
        assertEquals(CheatTesterType.FAKE_ENTITY, CheatTesterType.fromId("fake-entity").orElseThrow());
        assertTrue(CheatTesterType.AUTO_ARMOR.mutatesTargetState());
        assertTrue(CheatTesterType.NO_FALL.mutatesTargetState());
        assertFalse(CheatTesterType.FAKE_ENTITY.mutatesTargetState());
        assertTrue(CheatTesterType.fromId("unknown").isEmpty());
    }
}
