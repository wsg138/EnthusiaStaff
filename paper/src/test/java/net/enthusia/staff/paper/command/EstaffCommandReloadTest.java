package net.enthusia.staff.paper.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import net.enthusia.staff.domain.OperationalMode;
import net.enthusia.staff.paper.RuntimeHealth;
import net.enthusia.staff.paper.config.reload.ConfigurationReloadResult;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.junit.jupiter.api.Test;

class EstaffCommandReloadTest {
    private static final Command COMMAND = new Command("estaff") {
        @Override
        public boolean execute(CommandSender sender, String commandLabel, String[] args) {
            return false;
        }
    };

    @Test
    void authorizedPlayerReloadRunsActionAndReportsSuccess() {
        AtomicBoolean called = new AtomicBoolean();
        List<String> messages = new ArrayList<>();
        EstaffCommand command = new EstaffCommand(
                health(),
                () -> {
                    called.set(true);
                    return result(ConfigurationReloadResult.Outcome.APPLIED, "Alerts enabled", List.of(), true);
                }
        );

        command.onCommand(sender(Map.of("enthusiastaff.reload", true), messages), COMMAND, "estaff", new String[]{"reload"});

        assertTrue(called.get());
        assertEquals(List.of("Alerts enabled", "Reason policies were replaced atomically."), messages);
    }

    @Test
    void unauthorizedPlayerIsDeniedWithoutRunningReload() {
        AtomicBoolean called = new AtomicBoolean();
        List<String> messages = new ArrayList<>();
        EstaffCommand command = new EstaffCommand(health(), () -> {
            called.set(true);
            return result(ConfigurationReloadResult.Outcome.APPLIED, "unexpected", List.of(), false);
        });

        command.onCommand(sender(Map.of(), messages), COMMAND, "estaff", new String[]{"reload"});

        assertFalse(called.get());
        assertEquals(List.of("You do not have permission to reload EnthusiaStaff configuration."), messages);
    }

    @Test
    void localConsoleReloadUsesExistingBypassPolicy() {
        AtomicBoolean called = new AtomicBoolean();
        List<String> messages = new ArrayList<>();
        EstaffCommand command = new EstaffCommand(health(), () -> {
            called.set(true);
            return result(ConfigurationReloadResult.Outcome.NO_CHANGES, "No changes", List.of(), false);
        });

        command.onCommand(console(messages), COMMAND, "estaff", new String[]{"reload"});

        assertTrue(called.get());
        assertEquals(List.of("No changes"), messages);
    }

    @Test
    void validationAndRestartFailuresShowBoundedSanitizedDetails() {
        List<String> messages = new ArrayList<>();
        List<String> details = List.of("one", "two", "three", "four", "five", "six", "seven");
        EstaffCommand command = new EstaffCommand(
                health(),
                () -> result(ConfigurationReloadResult.Outcome.VALIDATION_FAILED, "Rejected", details, false)
        );

        command.onCommand(sender(Map.of("enthusiastaff.reload", true), messages), COMMAND, "estaff", new String[]{"reload"});

        assertEquals(List.of(
                "Rejected",
                "- one",
                "- two",
                "- three",
                "- four",
                "- five",
                "Additional sanitized reload details were written to the server log."
        ), messages);
    }

    @Test
    void tabCompletionIncludesOnlyAuthorizedSubcommands() {
        EstaffCommand command = new EstaffCommand(health());
        CommandSender player = sender(Map.of(
                "enthusiastaff.status", true,
                "enthusiastaff.reload", true
        ), new ArrayList<>());

        assertEquals(List.of("status", "reload"), command.onTabComplete(
                player,
                COMMAND,
                "estaff",
                new String[]{""}
        ));
        assertEquals(List.of("reload"), command.onTabComplete(
                player,
                COMMAND,
                "estaff",
                new String[]{"r"}
        ));
        assertEquals(List.of(), command.onTabComplete(
                player,
                COMMAND,
                "estaff",
                new String[]{"reload", "extra"}
        ));
    }

    @Test
    void statusAndVerifyStillReadRuntimeHealth() {
        RuntimeHealth health = health();
        health.update(OperationalMode.DEGRADED, Map.of("alerts", "waiting"));
        EstaffCommand command = new EstaffCommand(health);
        List<String> messages = new ArrayList<>();

        command.onCommand(sender(Map.of("enthusiastaff.verify", true), messages), COMMAND, "estaff", new String[]{"verify"});

        assertEquals(List.of("EnthusiaStaff mode: DEGRADED", "DISABLED alerts: waiting"), messages);
    }

    private static RuntimeHealth health() {
        return new RuntimeHealth();
    }

    private static ConfigurationReloadResult result(
            ConfigurationReloadResult.Outcome outcome,
            String message,
            List<String> details,
            boolean policies
    ) {
        return new ConfigurationReloadResult(outcome, message, details, policies);
    }

    private static CommandSender sender(Map<String, Boolean> permissions, List<String> messages) {
        return proxy(CommandSender.class, permissions, messages);
    }

    private static CommandSender console(List<String> messages) {
        return proxy(ConsoleCommandSender.class, Map.of(), messages);
    }

    private static CommandSender proxy(
            Class<? extends CommandSender> type,
            Map<String, Boolean> permissions,
            List<String> messages
    ) {
        return (CommandSender) Proxy.newProxyInstance(
                Thread.currentThread().getContextClassLoader(),
                new Class<?>[]{type},
                (instance, method, arguments) -> switch (method.getName()) {
                    case "hasPermission" -> permissions.getOrDefault(String.valueOf(arguments[0]), false);
                    case "sendMessage" -> {
                        messages.add(String.valueOf(arguments[0]));
                        yield null;
                    }
                    case "getName" -> "EstaffCommandReloadTest";
                    default -> defaultValue(method.getReturnType());
                }
        );
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
        if (type == long.class) {
            return 0L;
        }
        if (type == double.class) {
            return 0D;
        }
        if (type == float.class) {
            return 0F;
        }
        return 0;
    }
}
