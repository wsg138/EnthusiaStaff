package net.enthusia.staff.paper.staff;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class StaffModeDeathWiringTest {
    @Test
    void paperRuntimeRegistersDeathContainmentListener() throws IOException {
        String runtime = readNormalized(paperModule().resolve(
                "src/main/java/net/enthusia/staff/paper/PaperRuntimeComponents.java"
        ));

        assertTrue(
                runtime.contains("new StaffModeDeathListener(staffMode)"),
                "Staff Mode death containment must remain registered in the Paper runtime"
        );
    }

    @Test
    void deathListenerContainsBeforeNormalDeathLifecycleAndUsesDurableExit() throws IOException {
        String listener = readNormalized(paperModule().resolve(
                "src/main/java/net/enthusia/staff/paper/staff/StaffModeDeathListener.java"
        ));

        assertTrue(listener.contains("@EventHandler(priority = EventPriority.LOWEST)"));
        assertTrue(listener.contains("@EventHandler(priority = EventPriority.HIGHEST)"));
        assertTrue(listener.contains("StaffModeDeathPolicy.decide("));
        assertTrue(listener.contains("staffMode.authorityActive(playerId)"));
        assertTrue(listener.contains("event.setCancelled(true)"));
        assertTrue(listener.contains("event.setReviveHealth(maximumHealth.getValue())"));
        assertTrue(listener.contains("event.setKeepInventory(true)"));
        assertTrue(listener.contains("event.getDrops().clear()"));
        assertTrue(listener.contains("event.setKeepLevel(true)"));
        assertTrue(listener.contains("event.setDroppedExp(0)"));
        assertTrue(listener.contains("event.setShouldDropExperience(false)"));
        assertTrue(listener.contains("event.deathMessage(null)"));
        assertTrue(listener.contains("event.setShowDeathMessages(false)"));
        assertTrue(listener.contains("event.setShouldPlayDeathSound(false)"));
        assertTrue(listener.contains("staffMode.exit(player)"));
    }

    @Test
    void deathDuringAnotherStaffTransitionIsRetriedAfterTheGateReleases() throws IOException {
        String listener = readNormalized(paperModule().resolve(
                "src/main/java/net/enthusia/staff/paper/staff/StaffModeDeathListener.java"
        ));

        assertTrue(listener.contains("pendingDeathExits.add(playerId)"));
        assertTrue(listener.contains("if (staffMode.authorityActive(playerId))"));
        assertTrue(listener.contains("scheduleExitRetry(player, EXIT_RETRY_TICKS)"));
        assertTrue(listener.contains("if (!player.getScheduler().execute("));
        assertTrue(listener.contains("() -> pendingDeathExits.add(playerId)"));
        assertTrue(
                listener.contains("@EventHandler(priority = EventPriority.MONITOR)\n"
                        + "    public void onJoin(PlayerJoinEvent event)"),
                "A same-runtime disconnect must not discard a contained-death exit request"
        );
        assertTrue(listener.contains("scheduleJoinRecoveryCheck(player, JOIN_RECOVERY_ATTEMPTS)"));
    }

    @Test
    void internalCompletedDeathConsumersIgnoreCancelledStaffDeaths() throws IOException {
        Path root = repositoryRoot();
        String tester = readNormalized(paperModule().resolve(
                "src/main/java/net/enthusia/staff/paper/tester/CheatTesterLifecycleListener.java"
        ));
        String stalk = readNormalized(root.resolve(
                "components/enthusia-commend/src/main/java/org/enthusia/rep/stalk/StalkManager.java"
        ));
        String balance = readNormalized(root.resolve(
                "components/enthusia-currency/src/main/java/com/enthusia/enthusiacurrency/item/ItemBalanceTracker.java"
        ));

        assertTrue(
                tester.contains("@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)\n"
                        + "    public void onDeath(PlayerDeathEvent event)"),
                "Cancelled Staff Mode death events must not retire cheat-tester sessions"
        );
        assertTrue(
                stalk.contains("@EventHandler(ignoreCancelled = true)\n"
                        + "    public void onDeath(PlayerDeathEvent event)"),
                "Cancelled Staff Mode death events must not clear reputation stalk zones"
        );
        assertTrue(
                balance.contains("@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)\n"
                        + "    public void onDeath(PlayerDeathEvent event)"),
                "Cancelled Staff Mode death events must not schedule item-balance death scans"
        );
    }

    private static Path paperModule() {
        return repositoryRoot().resolve("paper");
    }

    private static Path repositoryRoot() {
        Path current = Path.of("").toAbsolutePath().normalize();
        if (Files.exists(current.resolve("paper/src/main/java/net/enthusia/staff/paper/PaperRuntimeComponents.java"))) {
            return current;
        }
        if (Files.exists(current.resolve("src/main/java/net/enthusia/staff/paper/PaperRuntimeComponents.java"))
                && current.getParent() != null) {
            return current.getParent();
        }
        throw new IllegalStateException("Could not locate the repository root from " + current);
    }
    private static String readNormalized(Path file) throws IOException {
        // Source assertions match Java syntax rather than the host's Git checkout line endings.
        return Files.readString(file).replace("\r\n", "\n");
    }

}
