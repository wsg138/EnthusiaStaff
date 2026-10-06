package net.enthusia.staff.paper.command;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class StaffModeVanishEntryWiringTest {
    @Test
    void staffCommandPreservesRecoveryAndRoutesEntriesThroughVanishCoordinator() throws IOException {
        String command = Files.readString(paperModule().resolve(
                "src/main/java/net/enthusia/staff/paper/command/StaffModeCommand.java"
        ));

        assertTrue(command.contains("if (targetedRecovery(arguments))"));
        assertTrue(command.contains("if (recoveryRequested(arguments))"));
        assertTrue(command.contains("StaffModeVanishEntryOption.parse(arguments)"));
        assertTrue(command.contains("entry.enter(player, option)"));
        assertTrue(command.contains("vanish.configureSpectatorTab(player, true)"));
        assertTrue(command.contains("vanish.configureSpectatorTab(player, false)"));
        assertTrue(command.contains("vanish.toggle(player)"));
        assertTrue(command.contains("List.of(\"recover\", \"-v\", \"vanish\", \"-nv\", \"visible\", \"tab\")"));
        assertTrue(command.contains("Targeted snapshot recovery is available only from the server console."));
    }

    @Test
    void registrarProvidesDurableStoreAndBoundedWorkersToEntryCoordinator() throws IOException {
        String registrar = Files.readString(paperModule().resolve(
                "src/main/java/net/enthusia/staff/paper/PaperCommandRegistrar.java"
        ));

        assertTrue(registrar.contains("new StaffModeVanishEntryCoordinator("));
        assertTrue(registrar.contains("storage(PaperStorageBindings::vanishStore)"));
        assertTrue(registrar.contains("dependencies.players().vanish()"));
        assertTrue(registrar.contains("bindCompleting(\"staff\", staffMode, staffMode)"));
    }

    private static Path paperModule() {
        Path current = Path.of("").toAbsolutePath().normalize();
        if (Files.exists(current.resolve("paper/src/main/java/net/enthusia/staff/paper/PaperCommandRegistrar.java"))) {
            return current.resolve("paper");
        }
        if (Files.exists(current.resolve("src/main/java/net/enthusia/staff/paper/PaperCommandRegistrar.java"))) {
            return current;
        }
        throw new IllegalStateException("Could not locate the Paper module from " + current);
    }
}
