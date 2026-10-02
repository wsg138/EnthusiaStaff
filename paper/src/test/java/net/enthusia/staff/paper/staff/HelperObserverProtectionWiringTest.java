package net.enthusia.staff.paper.staff;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class HelperObserverProtectionWiringTest {
    @Test
    void paperRuntimeRegistersHelperObserverProtectionListener() throws IOException {
        String runtime = Files.readString(paperModule().resolve(
                "src/main/java/net/enthusia/staff/paper/PaperRuntimeComponents.java"
        ));

        assertTrue(
                runtime.contains("new HelperObserverProtectionListener(staffMode)"),
                "Helper observer protections must remain registered in the Paper runtime"
        );
    }

    @Test
    void staffModeManagerUsesRankAwareAllowedGameModes() throws IOException {
        String manager = Files.readString(paperModule().resolve(
                "src/main/java/net/enthusia/staff/paper/staff/StaffModeManager.java"
        ));

        assertTrue(
                manager.contains("StaffModeAccessPolicy.allowsGameMode(rank, event.getNewGameMode())"),
                "Staff Mode runtime changes must use the rank-aware allowed game-mode policy"
        );
        assertFalse(
                manager.contains("event.getNewGameMode() != StaffModeAccessPolicy.requiredGameMode(rank)"),
                "The old single-required-mode runtime guard would block Helper Survival mode"
        );
    }

    @Test
    void helperAirItemGuardDoesNotSkipPreCancelledAirInteractions() throws IOException {
        String listener = Files.readString(paperModule().resolve(
                "src/main/java/net/enthusia/staff/paper/staff/HelperObserverProtectionListener.java"
        ));

        assertTrue(
                listener.contains("@EventHandler(priority = EventPriority.HIGHEST)\n    public void onAirItemUse"),
                "Helper air-item protection must run even when Paper marks the interaction cancelled"
        );
        assertFalse(
                listener.contains("@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)\n"
                        + "    public void onAirItemUse"),
                "Ignoring cancelled PlayerInteractEvent instances reopens ordinary air-item use"
        );
    }

    @Test
    void retainedMobTargetsAreReconciledThroughEntitySchedulers() throws IOException {
        String listener = Files.readString(paperModule().resolve(
                "src/main/java/net/enthusia/staff/paper/staff/HelperObserverProtectionListener.java"
        ));

        assertTrue(
                listener.contains("player.getScheduler().run(plugin, ignoredPlayer -> reconcileRetainedTargets(player), null)"),
                "Retained-target discovery must run on the Helper's entity scheduler"
        );
        assertTrue(
                listener.contains("targetMob.getScheduler().run(plugin, ignoredMob ->"),
                "Every mob target mutation must run on the mob's own entity scheduler"
        );
        assertTrue(
                listener.contains("staffMode.authorityActiveProfile(player.getUniqueId(), StaffRank.HELPER)"),
                "All Helper guards must use the cached usable Helper profile"
        );
        assertFalse(
                listener.contains("PaperStaffRankResolver"),
                "Helper protections must not disappear during the live-permission/cached-profile reconciliation window"
        );
        assertTrue(
                listener.contains("staffMode.authorityActiveProfile(targetId, StaffRank.HELPER)"),
                "Queued mob callbacks must revalidate the Helper profile before mutating the mob"
        );
        assertTrue(
                listener.contains("targetMob.setTarget(null)"),
                "A mob that still targets an active Helper must be detached"
        );
    }

    private static Path paperModule() {
        Path current = Path.of("").toAbsolutePath().normalize();
        if (Files.exists(current.resolve("src/main/java/net/enthusia/staff/paper/PaperRuntimeComponents.java"))) {
            return current;
        }
        Path paper = current.resolve("paper");
        if (Files.exists(paper.resolve("src/main/java/net/enthusia/staff/paper/PaperRuntimeComponents.java"))) {
            return paper;
        }
        throw new IllegalStateException("Could not locate the Paper module from " + current);
    }
}
