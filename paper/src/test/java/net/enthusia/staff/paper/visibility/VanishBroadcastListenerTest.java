package net.enthusia.staff.paper.visibility;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class VanishBroadcastListenerTest {
    @Test
    void vanishedPlayersAreRemovedFromAdvertisedCountWithoutGoingNegative() {
        assertEquals(7, VanishBroadcastListener.visibleCount(10, 3));
        assertEquals(0, VanishBroadcastListener.visibleCount(1, 4));
        assertEquals(5, VanishBroadcastListener.visibleCount(5, -1));
    }
}
