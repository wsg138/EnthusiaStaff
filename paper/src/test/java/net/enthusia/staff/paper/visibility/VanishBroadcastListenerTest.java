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
    private static final Path MANAGER_SOURCE = Path.of(
            "src/main/java/net/enthusia/staff/paper/visibility/VanishManager.java"
    );

    @Test
    void vanishedPlayersAreRemovedFromAdvertisedCountWithoutGoingNegative() {
        assertEquals(7, VanishBroadcastListener.visibleCount(10, 3));
        assertEquals(0, VanishBroadcastListener.visibleCount(1, 4));
        assertEquals(5, VanishBroadcastListener.visibleCount(5, -1));
    }

    @Test
    void pingFilteringAvoidsDeprecatedOrCrossThreadRosterIteration() throws IOException {
        String source = Files.readString(SOURCE);
        String manager = Files.readString(MANAGER_SOURCE);

        assertTrue(source.contains("event.getListedPlayers().removeIf"));
        assertTrue(source.contains("vanish.vanishedOnlineCount()"));
        assertFalse(source.contains("event.iterator()"));
        assertFalse(source.contains("getServer().getOnlinePlayers()"));
        assertTrue(manager.contains("audiences.playerIds().stream()"));
        assertTrue(manager.contains(".filter(this::isVanished)"));
    }
}
