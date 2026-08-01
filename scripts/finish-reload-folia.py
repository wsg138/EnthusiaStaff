from pathlib import Path


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected one match, found {count}")
    return text.replace(old, new, 1)


registrar_path = Path("paper/src/main/java/net/enthusia/staff/paper/PaperCommandRegistrar.java")
registrar = registrar_path.read_text(encoding="utf-8")
registrar = replace_once(
    registrar,
    "        registerStatus(plugin, health, new EstaffCommand(health, reloadAction));\n",
    "        registerStatus(plugin, health, new EstaffCommand(plugin, health, reloadAction));\n",
    "production reload dispatcher wiring",
)
registrar_path.write_text(registrar, encoding="utf-8")

controller_test_path = Path(
    "paper/src/test/java/net/enthusia/staff/paper/alert/PunishmentRequestAlertControllerTest.java"
)
controller_test = controller_test_path.read_text(encoding="utf-8")
controller_test = replace_once(
    controller_test,
    "        PunishmentRequestAlertController controller = controller(disabled(), factory, status);\n",
    "        PunishmentRequestAlertController controller = controller(disabled(), factory, status::set);\n",
    "controller status sink compilation fix",
)
controller_test_path.write_text(controller_test, encoding="utf-8")

command_test_path = Path(
    "paper/src/test/java/net/enthusia/staff/paper/command/EstaffCommandReloadTest.java"
)
command_test = command_test_path.read_text(encoding="utf-8")
command_test = replace_once(
    command_test,
    "import java.util.concurrent.atomic.AtomicBoolean;\n",
    "import java.util.concurrent.atomic.AtomicBoolean;\n"
    "import java.util.concurrent.atomic.AtomicReference;\n",
    "reload test atomic reference import",
)
anchor = '''    @Test
    void unauthorizedPlayerIsDeniedWithoutRunningReload() {
'''
new_tests = '''    @Test
    void scheduledReloadDefersExecutionAndReportsTheFinalResultLater() {
        AtomicBoolean called = new AtomicBoolean();
        AtomicReference<Runnable> scheduled = new AtomicReference<>();
        List<String> messages = new ArrayList<>();
        EstaffCommand command = new EstaffCommand(
                health(),
                () -> {
                    called.set(true);
                    return result(ConfigurationReloadResult.Outcome.APPLIED, "Applied", List.of(), false);
                },
                (sender, action, reporter) -> {
                    scheduled.set(() -> reporter.accept(action.reload()));
                    return EstaffCommand.ReloadDispatch.SCHEDULED;
                }
        );

        command.onCommand(
                sender(Map.of("enthusiastaff.reload", true), messages),
                COMMAND,
                "estaff",
                new String[]{"reload"}
        );

        assertFalse(called.get());
        assertEquals(List.of("EnthusiaStaff reload scheduled on the global region thread."), messages);
        scheduled.get().run();
        assertTrue(called.get());
        assertEquals(List.of(
                "EnthusiaStaff reload scheduled on the global region thread.",
                "Applied"
        ), messages);
    }

    @Test
    void rejectedReloadDoesNotRunOrClaimConfigurationChanged() {
        AtomicBoolean called = new AtomicBoolean();
        List<String> messages = new ArrayList<>();
        EstaffCommand command = new EstaffCommand(
                health(),
                () -> {
                    called.set(true);
                    return result(ConfigurationReloadResult.Outcome.APPLIED, "unexpected", List.of(), false);
                },
                (sender, action, reporter) -> EstaffCommand.ReloadDispatch.REJECTED
        );

        command.onCommand(
                sender(Map.of("enthusiastaff.reload", true), messages),
                COMMAND,
                "estaff",
                new String[]{"reload"}
        );

        assertFalse(called.get());
        assertEquals(List.of(
                "EnthusiaStaff reload could not be scheduled; no configuration was changed."
        ), messages);
    }

'''
command_test = replace_once(command_test, anchor, new_tests + anchor, "reload dispatcher tests")
command_test_path.write_text(command_test, encoding="utf-8")
