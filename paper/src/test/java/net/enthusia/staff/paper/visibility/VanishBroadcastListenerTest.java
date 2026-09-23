package net.enthusia.staff.paper.visibility;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class VanishBroadcastListenerTest {
    private static final Path SOURCE = Path.of(
            "src/main/java/net/enthusia/staff/paper/visibility/VanishBroadcastListener.java"
    );

    @Test
    void vanishedPlayersAreRemovedFromAdvertisedCountWithoutGoingNegative() {
        assertEquals(7, VanishBroadcastListener.visibleCount(10, 3));
        assertEquals(0, VanishBroadcastListener.visibleCount(1, 4));
        assertEquals(5, VanishBroadcastListener.visibleCount(5, -1));
    }

    @Test
    void pingFilteringUsesEventOwnedPlayersInsteadOfServerRosterScan() throws IOException {
        String source = Files.readString(SOURCE);

        assertTrue(source.contains("removeVanished(event.iterator(), vanish::isVanished)"));
        assertFalse(source.contains("getServer().getOnlinePlayers()"));
    }
}
