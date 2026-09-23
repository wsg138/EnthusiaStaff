from pathlib import Path


def replace_section(path: str, start_marker: str, end_marker: str, replacement: str) -> None:
    file = Path(path)
    text = file.read_text(encoding="utf-8")
    start = text.index(start_marker)
    end = text.index(end_marker, start)
    file.write_text(text[:start] + replacement + text[end:], encoding="utf-8")


def repair_command() -> None:
    path = "paper/src/main/java/net/enthusia/staff/paper/command/PunishmentCommand.java"
    constructors = '''    public PunishmentCommand(
            JavaPlugin plugin,
            Supplier<OperationalMode> mode,
            Supplier<PunishmentDraftWorkflow> workflows,
            Supplier<PlayerDirectory> players,
            AuthorizationPolicy authorization,
            PunishmentGuiController gui,
            PunishmentRequestCommandHandler requestCommands,
            ExecutorService workers
    ) {
        this(
                new Dependencies(
                        plugin,
                        mode,
                        workflows,
                        players,
                        authorization,
                        gui,
                        requestCommands,
                        workers
                ),
                LuckPermsStaffTargetGuard.discover(plugin)
        );
    }

    PunishmentCommand(Dependencies dependencies, StaffTargetGuard targetGuard) {
        Dependencies checked = java.util.Objects.requireNonNull(dependencies, "dependencies");
        this.plugin = checked.plugin();
        this.mode = checked.mode();
        this.workflows = checked.workflows();
        this.players = checked.players();
        this.authorization = checked.authorization();
        this.gui = checked.gui();
        this.requestCommands = checked.requestCommands();
        this.workers = checked.workers();
        this.targetGuard = java.util.Objects.requireNonNull(targetGuard, "targetGuard");
    }

'''
    replace_section(
        path,
        "    public PunishmentCommand(\n",
        "    @Override\n    public boolean onCommand",
        constructors,
    )
    file = Path(path)
    text = file.read_text(encoding="utf-8")
    record = '''

    record Dependencies(
            JavaPlugin plugin,
            Supplier<OperationalMode> mode,
            Supplier<PunishmentDraftWorkflow> workflows,
            Supplier<PlayerDirectory> players,
            AuthorizationPolicy authorization,
            PunishmentGuiController gui,
            PunishmentRequestCommandHandler requestCommands,
            ExecutorService workers
    ) {
        Dependencies {
            plugin = java.util.Objects.requireNonNull(plugin, "plugin");
            mode = java.util.Objects.requireNonNull(mode, "mode");
            workflows = java.util.Objects.requireNonNull(workflows, "workflows");
            players = java.util.Objects.requireNonNull(players, "players");
            authorization = java.util.Objects.requireNonNull(authorization, "authorization");
            gui = java.util.Objects.requireNonNull(gui, "gui");
            requestCommands = java.util.Objects.requireNonNull(requestCommands, "requestCommands");
            workers = java.util.Objects.requireNonNull(workers, "workers");
        }
    }
'''
    prefix, suffix = text.rsplit("\n}", 1)
    file.write_text(prefix + record + "\n}" + suffix, encoding="utf-8")


def repair_gui() -> None:
    path = "paper/src/main/java/net/enthusia/staff/paper/punishment/PunishmentGuiController.java"
    constructors = '''    public PunishmentGuiController(
            JavaPlugin plugin,
            Supplier<OperationalMode> mode,
            Supplier<PunishmentDraftWorkflow> workflows,
            Supplier<PlayerDirectory> players,
            AuthorizationPolicy authorization,
            ReasonPolicyRepository policies,
            ExecutorService workers
    ) {
        this(
                new Dependencies(
                        plugin,
                        mode,
                        workflows,
                        players,
                        authorization,
                        policies,
                        workers
                ),
                LuckPermsStaffTargetGuard.discover(plugin)
        );
    }

    PunishmentGuiController(Dependencies dependencies, StaffTargetGuard targetGuard) {
        Dependencies checked = java.util.Objects.requireNonNull(dependencies, "dependencies");
        this.plugin = checked.plugin();
        this.mode = checked.mode();
        this.workflows = checked.workflows();
        this.players = checked.players();
        this.authorization = checked.authorization();
        this.policies = checked.policies();
        this.workers = checked.workers();
        this.targetGuard = java.util.Objects.requireNonNull(targetGuard, "targetGuard");
        this.catalog = new PunishmentGuiCatalog(this.policies, this.authorization);
        this.renderer = new PunishmentGuiRenderer(catalog);
    }

'''
    replace_section(
        path,
        "    public PunishmentGuiController(\n",
        "    public void open",
        constructors,
    )
    file = Path(path)
    text = file.read_text(encoding="utf-8")
    record = '''    record Dependencies(
            JavaPlugin plugin,
            Supplier<OperationalMode> mode,
            Supplier<PunishmentDraftWorkflow> workflows,
            Supplier<PlayerDirectory> players,
            AuthorizationPolicy authorization,
            ReasonPolicyRepository policies,
            ExecutorService workers
    ) {
        Dependencies {
            plugin = java.util.Objects.requireNonNull(plugin, "plugin");
            mode = java.util.Objects.requireNonNull(mode, "mode");
            workflows = java.util.Objects.requireNonNull(workflows, "workflows");
            players = java.util.Objects.requireNonNull(players, "players");
            authorization = java.util.Objects.requireNonNull(authorization, "authorization");
            policies = java.util.Objects.requireNonNull(policies, "policies");
            workers = java.util.Objects.requireNonNull(workers, "workers");
        }
    }

'''
    marker = "    private record NoteCapture(PunishmentGuiState.Review review) {\n"
    if text.count(marker) != 1:
        raise RuntimeError("PunishmentGuiController NoteCapture insertion point changed")
    file.write_text(text.replace(marker, record + marker, 1), encoding="utf-8")


repair_command()
repair_gui()
