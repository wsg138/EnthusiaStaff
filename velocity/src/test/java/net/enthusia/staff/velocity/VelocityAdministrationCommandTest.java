package net.enthusia.staff.velocity;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.permission.Tristate;
import com.velocitypowered.api.proxy.ProxyServer;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;

class VelocityAdministrationCommandTest {
    private static final String STATUS = "status";
    private static final String STATUS_PERMISSION = "enthusiastaff.status";

    @Test
    void defaultAndUnknownRoutesRenderRuntimeStatus() {
        EnthusiaStaffVelocityPlugin plugin = plugin();
        RecordingSource source = new RecordingSource(Set.of(STATUS_PERMISSION));
        SimpleCommand command = plugin.new StatusCommand();

        command.execute(invocation(source));
        assertEquals(Component.text("EnthusiaStaff mode: BOOTSTRAP"), source.messages().getFirst());

        source.clear();
        command.execute(invocation(source, "unknown"));
        assertEquals(Component.text("EnthusiaStaff mode: BOOTSTRAP"), source.messages().getFirst());
    }

    @Test
    void privilegedRoutesRejectMissingPermissionBeforeAccessingRuntimeState() {
        EnthusiaStaffVelocityPlugin plugin = plugin();
        RecordingSource source = new RecordingSource(Set.of(STATUS_PERMISSION));
        SimpleCommand command = plugin.new StatusCommand();

        command.execute(invocation(source, "reload"));
        assertEquals(
                List.of(Component.text("You do not have permission to reload EnthusiaStaff.")),
                source.messages()
        );

        source.clear();
        command.execute(invocation(source, "migration", "inspect"));
        assertEquals(
                List.of(Component.text("You do not have permission to run migration operations.")),
                source.messages()
        );

        source.clear();
        command.execute(invocation(source, "cutover", STATUS));
        assertEquals(
                List.of(Component.text("You do not have permission to manage cutover.")),
                source.messages()
        );

        source.clear();
        command.execute(invocation(source, "discord", STATUS));
        assertEquals(
                List.of(Component.text("You do not have permission to manage Discord delivery.")),
                source.messages()
        );

        source.clear();
        command.execute(invocation(source, "website", STATUS));
        assertEquals(
                List.of(Component.text("You do not have permission to manage website bindings.")),
                source.messages()
        );
    }

    @Test
    void permittedRoutesReportUnavailableDependenciesWithoutStartingWork() {
        EnthusiaStaffVelocityPlugin plugin = plugin();
        RecordingSource source = new RecordingSource(Set.of(
                STATUS_PERMISSION,
                "enthusiastaff.migration",
                "enthusiastaff.cutover",
                "enthusiastaff.discord.manage",
                "enthusiastaff.website.manage"
        ));
        SimpleCommand command = plugin.new StatusCommand();

        command.execute(invocation(source, "migration", "inspect"));
        assertEquals(Component.text("MariaDB is not ready; no migration action was taken."), source.onlyMessage());

        source.clear();
        command.execute(invocation(source, "cutover", STATUS));
        assertEquals(Component.text("MariaDB is not ready; no cutover action was taken."), source.onlyMessage());

        source.clear();
        command.execute(invocation(source, "discord", STATUS));
        assertEquals(Component.text("MariaDB is not ready; Discord status is unavailable."), source.onlyMessage());

        source.clear();
        command.execute(invocation(source, "website", STATUS));
        assertEquals(Component.text("Website API: DISABLED or unavailable"), source.onlyMessage());

        source.clear();
        command.execute(invocation(source, "website", "code", "show", "ES-1"));
        assertEquals(Component.text("The website API store is not available."), source.onlyMessage());
    }

    @Test
    void altCommandRejectsInvalidShapesBeforeSubmittingWork() {
        EnthusiaStaffVelocityPlugin plugin = plugin();
        RecordingSource source = new RecordingSource(Set.of("enthusiastaff.alts.manage"));
        SimpleCommand command = plugin.new AltCommand();

        command.execute(invocation(source, "link"));
        assertEquals(
                Component.text("Usage: /alt <link|approve|household|notrelated|unlink|reopen> "
                        + "<player1> <player2> <reason>"),
                source.onlyMessage()
        );

        source.clear();
        command.execute(invocation(source, "unknown", "first", "second", "reason"));
        assertEquals(Component.text("Unknown alt operation."), source.onlyMessage());

        source.clear();
        command.execute(invocation(source, "reopen", "first", "second", "reason"));
        assertEquals(
                Component.text("Admin permission is required to reopen a not-related decision."),
                source.onlyMessage()
        );
    }

    private static EnthusiaStaffVelocityPlugin plugin() {
        return new EnthusiaStaffVelocityPlugin(
                interfaceProxy(ProxyServer.class),
                interfaceProxy(Logger.class),
                Path.of(".")
        );
    }

    private static TestInvocation invocation(RecordingSource source, String... arguments) {
        return new TestInvocation(source, "estaff", arguments);
    }

    private static <T> T interfaceProxy(Class<T> type) {
        return type.cast(Proxy.newProxyInstance(
                Thread.currentThread().getContextClassLoader(),
                new Class<?>[]{type},
                (proxy, method, arguments) -> defaultValue(method.getReturnType())
        ));
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) {
            return null;
        }
        if (type == boolean.class) {
            return false;
        }
        if (type == char.class) {
            return '\0';
        }
        return numericDefault(type);
    }

    private static Object numericDefault(Class<?> type) {
        if (type == byte.class) {
            return (byte) 0;
        }
        if (type == short.class) {
            return (short) 0;
        }
        if (type == int.class) {
            return 0;
        }
        if (type == long.class) {
            return 0L;
        }
        if (type == float.class) {
            return 0.0F;
        }
        return 0.0D;
    }

    private record TestInvocation(CommandSource source, String alias, String[] arguments)
            implements SimpleCommand.Invocation {
    }

    private static final class RecordingSource implements CommandSource {
        private final Set<String> permissions;
        private final List<Component> messages = new ArrayList<>();

        private RecordingSource(Set<String> permissions) {
            this.permissions = Set.copyOf(permissions);
        }

        @Override
        public Tristate getPermissionValue(String permission) {
            return permissions.contains(permission) ? Tristate.TRUE : Tristate.FALSE;
        }

        @Override
        public void sendMessage(Component message) {
            messages.add(message);
        }

        private List<Component> messages() {
            return List.copyOf(messages);
        }

        private Component onlyMessage() {
            assertEquals(1, messages.size());
            return messages.getFirst();
        }

        private void clear() {
            messages.clear();
        }
    }
}
