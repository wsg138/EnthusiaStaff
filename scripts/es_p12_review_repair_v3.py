from pathlib import Path
import runpy

runpy.run_path("scripts/es_p12_review_repair_v2.py", run_name="__main__")


# PMD: make the retry-test sentinel explicit instead of using a control-flow literal.
presence = Path("paper/src/test/java/net/enthusia/staff/paper/PaperPresenceListenerTest.java")
text = presence.read_text()
logger = "    private static final Logger LOGGER = Logger.getLogger(PaperPresenceListenerTest.class.getName());"
condition = "attempts.incrementAndGet() == 1"
if text.count(logger) != 1 or text.count(condition) != 1:
    raise SystemExit("presence retry test shape changed unexpectedly")
text = text.replace(logger, "    private static final int FIRST_ATTEMPT = 1;\n" + logger)
text = text.replace(condition, "attempts.incrementAndGet() == FIRST_ATTEMPT")
presence.write_text(text)


# Avoid a functional-interface overload collision with the existing test-only response constructor.
# The generated v2 source always has the two public constructors before the package-private constructor.
freeze = Path("paper/src/main/java/net/enthusia/staff/paper/command/FreezeCommand.java")
text = freeze.read_text()
start_marker = "    public FreezeCommand(\n            JavaPlugin plugin,\n"
end_marker = "    FreezeCommand(\n            JavaPlugin plugin,\n"
start = text.find(start_marker)
end = text.find(end_marker, start + len(start_marker))
if start < 0 or end <= start:
    raise SystemExit("could not locate generated FreezeCommand public-constructor region")
replacement = '''    public FreezeCommand(
            JavaPlugin plugin,
            Clock clock,
            Supplier<OperationalMode> mode,
            Supplier<PlayerDirectory> players,
            Supplier<FreezeStore> freezes,
            FreezeManager manager,
            ExecutorService workers
    ) {
        this(
                plugin, clock, mode, players, freezes, manager, workers,
                new RuntimeHooks(
                        LuckPermsStaffTargetGuard.discover(plugin),
                        new FreezeStaffNotifier(plugin),
                        FreezeNoticeSink.noOp(),
                        commandResponses(plugin)
                )
        );
    }

    public static FreezeCommand createRuntime(
            JavaPlugin plugin,
            Clock clock,
            Supplier<OperationalMode> mode,
            Supplier<PlayerDirectory> players,
            Supplier<FreezeStore> freezes,
            FreezeManager manager,
            ExecutorService workers,
            FreezeNoticeSink targetNotices
    ) {
        return new FreezeCommand(
                plugin, clock, mode, players, freezes, manager, workers,
                new RuntimeHooks(
                        LuckPermsStaffTargetGuard.discover(plugin),
                        new FreezeStaffNotifier(plugin),
                        targetNotices,
                        commandResponses(plugin)
                )
        );
    }

'''
freeze.write_text(text[:start] + replacement + text[end:])

registrar = Path("paper/src/main/java/net/enthusia/staff/paper/PaperCommandRegistrar.java")
text = registrar.read_text()
legacy = "FreezeCommand freezeCommand = new FreezeCommand("
runtime = "FreezeCommand freezeCommand = FreezeCommand.createRuntime("
if text.count(legacy) == 1 and text.count(runtime) == 0:
    text = text.replace(legacy, runtime)
elif text.count(legacy) == 0 and text.count(runtime) == 1:
    pass
else:
    raise SystemExit("FreezeCommand registrar construction shape changed unexpectedly")
registrar.write_text(text)
