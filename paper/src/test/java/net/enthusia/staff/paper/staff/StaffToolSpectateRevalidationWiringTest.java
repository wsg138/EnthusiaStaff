package net.enthusia.staff.paper.staff;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class StaffToolSpectateRevalidationWiringTest {
    private static final Path DISPATCHER_SOURCE = Path.of(
            "src/main/java/net/enthusia/staff/paper/staff/StaffToolDispatcher.java"
    );

    @Test
    void targetProtectionIsRecheckedBeforeTeleportAndAttachment() throws IOException {
        String source = Files.readString(DISPATCHER_SOURCE).replace("\r\n", "\n");

        int targetRecheck = source.indexOf("revalidateFollowTarget(actorId, target, liveTarget)");
        int teleport = source.indexOf("actor.teleportAsync(target.location())");
        assertTrue(targetRecheck >= 0 && targetRecheck < teleport);

        long eligibilityChecks = source.lines()
                .filter(line -> line.contains("if (!spectateTargetEligible(liveTarget))"))
                .count();
        assertEquals(2L, eligibilityChecks);
        assertTrue(source.contains("prepareSpectatorAttachment(actorId, target, liveTarget)"));
    }
}
