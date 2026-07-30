package net.enthusia.staff.paper.command;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.function.Supplier;
import net.enthusia.staff.domain.auth.AuthorizationPolicy;
import net.enthusia.staff.paper.punishment.PunishmentGuiController;
import net.enthusia.staff.paper.punishment.PunishmentRequestGuiController;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;

final class PunishmentRequestWiringTest {
    private static final Path PLUGIN_SOURCE = Path.of(
            "src/main/java/net/enthusia/staff/paper/EnthusiaStaffPaperPlugin.java"
    );
    private static final Path COMMAND_SOURCE = Path.of(
            "src/main/java/net/enthusia/staff/paper/command/PunishmentCommand.java"
    );

    @Test
    void punishmentCommandConstructorRequiresTheRequestHandler() {
        assertDoesNotThrow(() -> PunishmentCommand.class.getConstructor(
                JavaPlugin.class,
                Supplier.class,
                Supplier.class,
                Supplier.class,
                AuthorizationPolicy.class,
                PunishmentGuiController.class,
                PunishmentRequestCommandHandler.class,
                ExecutorService.class
        ));
    }

    @Test
    void requestInterfacesRetainBootstrapCompatibleConstructors() {
        assertDoesNotThrow(() -> PunishmentRequestCommandHandler.class.getConstructor(
                JavaPlugin.class,
                Supplier.class,
                AuthorizationPolicy.class,
                PunishmentRequestGuiController.class,
                ExecutorService.class
        ));
        assertDoesNotThrow(() -> PunishmentRequestGuiController.class.getConstructor(
                JavaPlugin.class,
                Supplier.class,
                Supplier.class,
                AuthorizationPolicy.class,
                ExecutorService.class
        ));
    }

    @Test
    void pluginBootstrapRegistersTheRequestGuiWithThePlayerDirectory() throws IOException {
        String source = Files.readString(PLUGIN_SOURCE);

        assertTrue(source.contains("new PunishmentRequestGuiController("));
        assertTrue(source.contains("punishmentRequestService,\n                playerDirectory,"));
        assertTrue(source.contains("punishmentRequestGui.register();"));
        assertTrue(source.contains("new PunishmentRequestCommandHandler("));
        assertTrue(source.contains("punishmentRequestCommands,"));
    }

    @Test
    void punishmentCommandHasNoPlayerDirectoryBindingSideEffect() throws IOException {
        String source = Files.readString(COMMAND_SOURCE);

        assertFalse(source.contains("requestCommands.bindPlayerDirectory(players);"));
    }
}
