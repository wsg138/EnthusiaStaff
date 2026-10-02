package net.enthusia.staff.paper.command;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class StaffModeVanishEntryWiringTest {
    @Test
    void staffCommandPreservesRecoveryAndRoutesEntriesThroughCoordinator() throws IOException {
        String command = Files.readString(paperModule().resolve(
                "src/main/java/net/enthusia/staff/paper/command/StaffModeCommand.java"
        ));

        assertTrue(command.contains("targetedRecovery(arguments)"));
        assertTrue(command.contains("recoverTarget(sender, arguments[1])"));
        assertTrue(command.contains("if (recoveryRequested(arguments))"));
        assertTrue(command.contains("StaffModeVanishEntryOption.parse(arguments)"));
        assertTrue(command.contains("entry.enter(player, option)"));
        assertTrue(command.contains("Use /vanish to change visibility before exiting."));
        assertTrue(command.contains("List.of(RECOVER, \"-v\", \"vanish\", \"-nv\", \"visible\")"));
    }

    @Test
    void entryCoordinatorWaitsForUsableAuthorityBeforeApplyingVisibility() throws IOException {
        String coordinator = Files.readString(paperModule().resolve(
                "src/main/java/net/enthusia/staff/paper/command/StaffModeVanishEntryCoordinator.java"
        ));

        assertTrue(coordinator.contains("if (!staffMode.enter(player, rank))"));
        assertTrue(coordinator.contains("staffMode.authorityActive(playerId)"));
        assertTrue(coordinator.contains("vanish.applyStaffModeEntryVisibility("));
        assertTrue(coordinator.contains("choice.rememberChoice()"));
        assertTrue(coordinator.contains(
                "Staff mode entry visibility could not be confirmed automatically."
        ));
    }

    @Test
    void registrarProvidesDurableStoreAndBoundedWorkersToEntryCoordinator() throws IOException {
        String registrar = Files.readString(paperModule().resolve(
                "src/main/java/net/enthusia/staff/paper/PaperCommandRegistrar.java"
        ));

        assertTrue(registrar.contains("new StaffModeVanishEntryCoordinator("));
        assertTrue(registrar.contains("storage(PaperStorageBindings::vanishStore)"));
        assertTrue(registrar.contains("bindCompleting(\"staff\", staffMode, staffMode)"));
    }

    private static Path paperModule() {
        Path current = Path.of("").toAbsolutePath().normalize();
        if (Files.exists(current.resolve(
                "paper/src/main/java/net/enthusia/staff/paper/PaperCommandRegistrar.java"
        ))) {
            return current.resolve("paper");
        }
        if (Files.exists(current.resolve(
                "src/main/java/net/enthusia/staff/paper/PaperCommandRegistrar.java"
        ))) {
            return current;
        }
        throw new IllegalStateException("Could not locate the Paper module from " + current);
    }
}
