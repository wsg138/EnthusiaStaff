package net.enthusia.staff.paper.tester;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import net.enthusia.staff.domain.tester.CheatTesterType;
import org.junit.jupiter.api.Test;

class CheatTesterConfigurationMenuTest {
    @Test
    void slotRoutingUsesExplicitTesterAllowlist() {
        assertEquals(CheatTesterType.TOTEM_REFILL, CheatTesterConfigurationMenu.typeAtSlot(10));
        assertEquals(CheatTesterType.NO_FALL, CheatTesterConfigurationMenu.typeAtSlot(11));
        assertEquals(CheatTesterType.VELOCITY, CheatTesterConfigurationMenu.typeAtSlot(12));
        assertEquals(CheatTesterType.AUTO_ARMOR, CheatTesterConfigurationMenu.typeAtSlot(14));
        assertEquals(CheatTesterType.FAKE_ENTITY, CheatTesterConfigurationMenu.typeAtSlot(15));
        assertNull(CheatTesterConfigurationMenu.typeAtSlot(CheatTesterConfigurationMenu.INFO_SLOT));
        assertNull(CheatTesterConfigurationMenu.typeAtSlot(CheatTesterConfigurationMenu.CLOSE_SLOT));
        assertNull(CheatTesterConfigurationMenu.typeAtSlot(13));
    }
}
