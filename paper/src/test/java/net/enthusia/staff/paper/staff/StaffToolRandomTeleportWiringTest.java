package net.enthusia.staff.paper.staff;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class StaffToolRandomTeleportWiringTest {
    private static final Path SERVICE_SOURCE = Path.of(
            "src/main/java/net/enthusia/staff/paper/staff/StaffToolRandomTeleportService.java"
    );

    @Test
    void finalSelectionRevalidatesTargetAndRetriesStaleCandidates() throws IOException {
        String source = Files.readString(SERVICE_SOURCE).replace("\r\n", "\n");

        assertTrue(source.contains("List<Player> candidates = List.copyOf(plugin.getServer().getOnlinePlayers())"));
        assertTrue(source.contains("boolean scheduled = target.getScheduler().execute("));
        assertTrue(source.contains("eligible.add(target.getUniqueId())"));
        assertFalse(source.contains(".map(Player::getUniqueId)"));
        assertTrue(source.contains("target -> revalidateCandidate(actorId, candidates, target)"));
        assertTrue(source.contains("if (!eligibleCandidate(actorId, target)) {\n"
                + "            attemptNextCandidate(actorId, candidates);"));
        assertTrue(source.contains("new TargetSnapshot(target.getName(), target.getLocation().clone())"));
        assertTrue(source.contains("() -> attemptNextCandidate(actorId, candidates)"));
        assertFalse(source.contains("eligible.add(new TargetSnapshot"));
    }
}
