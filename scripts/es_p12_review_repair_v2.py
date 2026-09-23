from pathlib import Path
import runpy

runpy.run_path("scripts/es_p12_review_repair.py", run_name="__main__")


def replace(path: str, old: str, new: str) -> None:
    file = Path(path)
    text = file.read_text()
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"expected exactly one v2 match in {path}, found {count}")
    file.write_text(text.replace(old, new))


path = "paper/src/main/java/net/enthusia/staff/paper/command/FreezeCommand.java"
replace(
    path,
    '''        this(\n                plugin, clock, mode, players, freezes, manager, workers, targetNotices,\n                new RuntimeHooks(\n                        LuckPermsStaffTargetGuard.discover(plugin),\n                        new FreezeStaffNotifier(plugin),\n                        commandResponses(plugin)\n                )\n        );''',
    '''        this(\n                plugin, clock, mode, players, freezes, manager, workers,\n                new RuntimeHooks(\n                        LuckPermsStaffTargetGuard.discover(plugin),\n                        new FreezeStaffNotifier(plugin),\n                        targetNotices,\n                        commandResponses(plugin)\n                )\n        );'''
)
replace(
    path,
    '''        this(\n                plugin, clock, mode, players, freezes, manager, workers, FreezeNoticeSink.noOp(),\n                new RuntimeHooks(\n                        (actor, targetId, systemActor) -> StaffTargetGuard.Result.allow(),\n                        FreezeAlertSink.noOp(),\n                        responses\n                )\n        );''',
    '''        this(\n                plugin, clock, mode, players, freezes, manager, workers,\n                new RuntimeHooks(\n                        (actor, targetId, systemActor) -> StaffTargetGuard.Result.allow(),\n                        FreezeAlertSink.noOp(),\n                        FreezeNoticeSink.noOp(),\n                        responses\n                )\n        );'''
)
replace(
    path,
    '''    FreezeCommand(\n            JavaPlugin plugin,\n            Clock clock,\n            Supplier<OperationalMode> mode,\n            Supplier<PlayerDirectory> players,\n            Supplier<FreezeStore> freezes,\n            FreezeManager manager,\n            ExecutorService workers,\n            RuntimeHooks hooks\n    ) {\n        this(plugin, clock, mode, players, freezes, manager, workers, FreezeNoticeSink.noOp(), hooks);\n    }\n\n    private FreezeCommand(\n            JavaPlugin plugin,\n            Clock clock,\n            Supplier<OperationalMode> mode,\n            Supplier<PlayerDirectory> players,\n            Supplier<FreezeStore> freezes,\n            FreezeManager manager,\n            ExecutorService workers,\n            FreezeNoticeSink targetNotices,\n            RuntimeHooks hooks\n    ) {\n        this.plugin = plugin;''',
    '''    FreezeCommand(\n            JavaPlugin plugin,\n            Clock clock,\n            Supplier<OperationalMode> mode,\n            Supplier<PlayerDirectory> players,\n            Supplier<FreezeStore> freezes,\n            FreezeManager manager,\n            ExecutorService workers,\n            RuntimeHooks hooks\n    ) {\n        this.plugin = plugin;'''
)
replace(
    path,
    '''        this.targetGuard = hooks.targetGuard();\n        this.alerts = hooks.alerts();\n        this.targetNotices = java.util.Objects.requireNonNull(targetNotices, "targetNotices");\n        this.responses = hooks.responses();''',
    '''        this.targetGuard = hooks.targetGuard();\n        this.alerts = hooks.alerts();\n        this.targetNotices = hooks.targetNotices();\n        this.responses = hooks.responses();'''
)
replace(
    path,
    '''    record RuntimeHooks(\n            StaffTargetGuard targetGuard,\n            FreezeAlertSink alerts,\n            BiConsumer<CommandSender, List<Component>> responses\n    ) {\n        RuntimeHooks {\n            java.util.Objects.requireNonNull(targetGuard, "targetGuard");\n            java.util.Objects.requireNonNull(alerts, "alerts");\n            java.util.Objects.requireNonNull(responses, "responses");\n        }\n    }''',
    '''    record RuntimeHooks(\n            StaffTargetGuard targetGuard,\n            FreezeAlertSink alerts,\n            FreezeNoticeSink targetNotices,\n            BiConsumer<CommandSender, List<Component>> responses\n    ) {\n        RuntimeHooks {\n            java.util.Objects.requireNonNull(targetGuard, "targetGuard");\n            java.util.Objects.requireNonNull(alerts, "alerts");\n            java.util.Objects.requireNonNull(targetNotices, "targetNotices");\n            java.util.Objects.requireNonNull(responses, "responses");\n        }\n    }'''
)

hierarchy = "paper/src/test/java/net/enthusia/staff/paper/command/FreezeCommandHierarchyTest.java"
replace(
    hierarchy,
    "import net.enthusia.staff.paper.freeze.FreezeAlertSink;",
    "import net.enthusia.staff.paper.freeze.FreezeAlertSink;\nimport net.enthusia.staff.paper.freeze.FreezeNoticeSink;"
)
replace(
    hierarchy,
    '''                new FreezeCommand.RuntimeHooks(\n                        denied,\n                        FreezeAlertSink.noOp(),\n                        (sender, responses) -> messages.addAll(responses)\n                )''',
    '''                new FreezeCommand.RuntimeHooks(\n                        denied,\n                        FreezeAlertSink.noOp(),\n                        FreezeNoticeSink.noOp(),\n                        (sender, responses) -> messages.addAll(responses)\n                )'''
)
