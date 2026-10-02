package net.enthusia.staff.paper.staff;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.bukkit.event.block.Action;
import org.junit.jupiter.api.Test;

class HelperObserverPolicyTest {
    @Test
    void helperBlocksOrdinaryRightClickAirUseOnly() {
        assertTrue(HelperObserverPolicy.blocksAirItemUse(
                true, Action.RIGHT_CLICK_AIR, true
        ));
        assertFalse(HelperObserverPolicy.blocksAirItemUse(
                true, Action.RIGHT_CLICK_AIR, false
        ));
        assertFalse(HelperObserverPolicy.blocksAirItemUse(
                true, Action.LEFT_CLICK_AIR, true
        ));
        assertFalse(HelperObserverPolicy.blocksAirItemUse(
                true, Action.RIGHT_CLICK_BLOCK, true
        ));
        assertFalse(HelperObserverPolicy.blocksAirItemUse(
                false, Action.RIGHT_CLICK_AIR, true
        ));
    }

    @Test
    void helperProjectileCollisionIsSuppressedOnlyForEntityHits() {
        assertTrue(HelperObserverPolicy.blocksProjectileCollision(true, true));
        assertFalse(HelperObserverPolicy.blocksProjectileCollision(true, false));
        assertFalse(HelperObserverPolicy.blocksProjectileCollision(false, true));
    }

    @Test
    void helperCannotGainExperience() {
        assertEquals(0, HelperObserverPolicy.experienceAmount(true, 25));
        assertEquals(25, HelperObserverPolicy.experienceAmount(false, 25));
    }
}
