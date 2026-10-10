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
        String runtime = normalizedSource(paperModule().resolve(
                "src/main/java/net/enthusia/staff/paper/PaperRuntimeComponents.java"
        ));

        assertTrue(
                runtime.contains("new HelperObserverProtectionListener(staffMode)"),
                "Helper observer protections must remain registered in the Paper runtime"
        );
    }

    @Test
    void staffModeManagerUsesRankAwareAllowedGameModes() throws IOException {
        String manager = normalizedSource(paperModule().resolve(
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
        String listener = normalizedSource(paperModule().resolve(
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
    void helperProjectilePassThroughUsesModernProjectileHitEventOnly() throws IOException {
        String listener = normalizedSource(paperModule().resolve(
                "src/main/java/net/enthusia/staff/paper/staff/HelperObserverProtectionListener.java"
        ));

        assertTrue(
                listener.contains("public void onProjectileHit(ProjectileHitEvent event)")
                        && listener.contains("event.setCancelled(true)"),
                "Helper projectile pass-through must remain implemented through cancellable ProjectileHitEvent"
        );
        assertFalse(
                listener.contains("import com.destroystokyo.paper.event.entity.ProjectileCollideEvent;")
                        || listener.contains("public void onFireworkCollision("),
                "The deprecated Paper ProjectileCollideEvent compatibility hook must not be registered"
        );
    }

    @Test
    void retainedMobTargetsAreReconciledThroughEntitySchedulers() throws IOException {
        String listener = normalizedSource(paperModule().resolve(
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
                listener.contains("targetMob.setTarget(null)"),
                "A mob that still targets an active Helper must be detached"
        );
    }

    @Test
    void helperObserverAuthorityUsesAppliedSessionRankInsteadOfLivePermissionResolution() throws IOException {
        String manager = normalizedSource(paperModule().resolve(
                "src/main/java/net/enthusia/staff/paper/staff/StaffModeManager.java"
        ));
        String listener = normalizedSource(paperModule().resolve(
                "src/main/java/net/enthusia/staff/paper/staff/HelperObserverProtectionListener.java"
        ));

        assertTrue(
                manager.contains("boolean helperObserverActive(UUID playerId)")
                        && manager.contains("ranks.get(playerId) == StaffRank.HELPER"),
                "Helper observer authority must remain bound to the applied Staff Mode rank snapshot"
        );
        assertTrue(
                listener.contains("staffMode.helperObserverActive(player.getUniqueId())")
                        && listener.contains("!staffMode.isUnrestricted(player)"),
                "Helper protections must use Staff Mode's authoritative cached profile while exempting explicit unrestricted identities"
        );
        assertFalse(
                listener.contains("PaperStaffRankResolver"),
                "The observer listener must not fail open when live rank permissions temporarily disappear"
        );
    }

    @Test
    void queuedMobTargetClearRevalidatesObserverProfileAtMutationTime() throws IOException {
        String listener = normalizedSource(paperModule().resolve(
                "src/main/java/net/enthusia/staff/paper/staff/HelperObserverProtectionListener.java"
        ));

        assertTrue(
                listener.contains("targetMob.getScheduler().run(plugin, ignoredMob -> {\n"
                        + "            if (!staffMode.helperObserverActive(targetId) || staffMode.unrestricted(targetId))"),
                "A queued mob target clear must be abandoned after Helper observer mode ends, changes rank, or becomes unrestricted"
        );
    }

    /** Source-shape assertions must be stable for both CRLF Windows and LF CI checkouts. */
    private static String normalizedSource(Path path) throws IOException {
        return Files.readString(path).replace("\r\n", "\n");
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
