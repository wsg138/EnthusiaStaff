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
legacy = "FreezeCommand freezes = new FreezeCommand("
runtime = "FreezeCommand freezes = FreezeCommand.createRuntime("
if text.count(legacy) == 1 and text.count(runtime) == 0:
    text = text.replace(legacy, runtime)
elif text.count(legacy) == 0 and text.count(runtime) == 1:
    pass
else:
    raise SystemExit("FreezeCommand registrar construction shape changed unexpectedly")
registrar.write_text(text)


# The movement regression uses a real-world-shaped Location. Bukkit rejects setTo()
# with a null world, which cannot occur for an actual online player's location.
movement = Path("paper/src/test/java/net/enthusia/staff/paper/freeze/FreezeMovementAndCommandTest.java")
text = movement.read_text()
location_import = "import org.bukkit.Location;\n"
if text.count(location_import) != 1:
    raise SystemExit("movement test Location import shape changed unexpectedly")
text = text.replace(location_import, location_import + "import org.bukkit.World;\n")
null_locations = text.count("new Location(null,")
if null_locations < 2:
    raise SystemExit(f"expected at least two null-world locations, found {null_locations}")
text = text.replace("new Location(null,", "new Location(world(),")
player_marker = "    private static Player player(List<Component> messages) {\n"
if text.count(player_marker) != 1:
    raise SystemExit("movement test player helper shape changed unexpectedly")
world_helper = '''    private static World world() {
        return proxy(World.class);
    }

'''
text = text.replace(player_marker, world_helper + player_marker)
movement.write_text(text)

# Retry trigger only; this file is removed by the validated publish step.
