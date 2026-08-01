package net.enthusia.staff.paper.command;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
    private static final Path REGISTRAR_SOURCE = Path.of(
            "src/main/java/net/enthusia/staff/paper/PaperCommandRegistrar.java"
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
    void requestInterfacesRequireExplicitPlayerDirectoryWiring() {
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
        assertThrows(NoSuchMethodException.class, () -> PunishmentRequestGuiController.class.getConstructor(
                JavaPlugin.class,
                Supplier.class,
                AuthorizationPolicy.class,
                ExecutorService.class
        ));
    }

    @Test
    void commandRegistrarRegistersTheRequestGuiWithThePlayerDirectory() throws IOException {
        String source = normalizedSource(REGISTRAR_SOURCE);

        assertTrue(source.contains("new PunishmentRequestGuiController("));
        assertTrue(source.contains("plugin(), requests, players, authorization(), workers()"));
        assertTrue(source.contains("requestGui.register();"));
        assertTrue(source.contains("new PunishmentRequestCommandHandler("));
        assertTrue(source.contains("punishmentGui, requestHandler, workers()"));
    }

    @Test
    void punishmentCommandHasNoPlayerDirectoryBindingSideEffect() throws IOException {
        String source = normalizedSource(COMMAND_SOURCE);

        assertFalse(source.contains("requestCommands.bindPlayerDirectory(players);"));
    }

    private static String normalizedSource(Path source) throws IOException {
        return Files.readString(source).replace("\r\n", "\n");
    }
}
