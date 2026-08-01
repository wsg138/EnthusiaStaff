from pathlib import Path
import subprocess


def git_show(spec: str) -> str:
    return subprocess.check_output(["git", "show", spec], text=True)


def replace_once(text: str, old: str, new: str, label: str) -> str:
    matches = text.count(old)
    if matches != 1:
        raise RuntimeError(f"{label}: expected one match, found {matches}")
    return text.replace(old, new, 1)


plugin_path = Path("paper/src/main/java/net/enthusia/staff/paper/EnthusiaStaffPaperPlugin.java")
plugin = git_show("refs/remotes/origin/main:" + plugin_path.as_posix())

plugin = replace_once(
    plugin,
    "import io.papermc.paper.threadedregions.scheduler.ScheduledTask;\n"
    "import java.time.Clock;\n"
    "import java.util.Map;\n"
    "import java.util.Optional;\n",
    "import io.papermc.paper.threadedregions.scheduler.ScheduledTask;\n"
    "import java.nio.file.Path;\n"
    "import java.time.Clock;\n"
    "import java.util.List;\n"
    "import java.util.Map;\n"
    "import java.util.Optional;\n"
    "import java.util.UUID;\n",
    "plugin utility imports",
)
plugin = replace_once(
    plugin,
    "import net.enthusia.staff.paper.client.ClientEvidenceCollector;\n"
    "import net.enthusia.staff.paper.enforcement.MuteEnforcementListener;\n"
    "import net.enthusia.staff.paper.report.ChatContextBuffer;\n",
    "import net.enthusia.staff.paper.alert.PunishmentRequestAlertController;\n"
    "import net.enthusia.staff.paper.alert.PunishmentRequestAlertLifecycle;\n"
    "import net.enthusia.staff.paper.client.ClientEvidenceCollector;\n"
    "import net.enthusia.staff.paper.config.PaperConfigurationLoader;\n"
    "import net.enthusia.staff.paper.config.PaperConfigurationSnapshot;\n"
    "import net.enthusia.staff.paper.config.PaperConfigurationValidationException;\n"
    "import net.enthusia.staff.paper.config.ReasonPolicyConfigurationLoader;\n"
    "import net.enthusia.staff.paper.config.RestartRequiredConfiguration;\n"
    "import net.enthusia.staff.paper.config.reload.ConfigurationReloadAction;\n"
    "import net.enthusia.staff.paper.config.reload.ConfigurationReloadCoordinator;\n"
    "import net.enthusia.staff.paper.config.reload.ConfigurationReloadResult;\n"
    "import net.enthusia.staff.paper.enforcement.MuteEnforcementListener;\n"
    "import net.enthusia.staff.paper.report.ChatContextBuffer;\n",
    "plugin feature imports",
)
plugin = replace_once(
    plugin,
    "    private final ConcurrentHashMap<String, String> featureIssues = new ConcurrentHashMap<>();\n"
    "    private final PaperRuntimeLifecycle<PaperStorageBindings, ScheduledTask, PersistentChannelClient> lifecycle =\n",
    "    private final ConcurrentHashMap<String, String> featureIssues = new ConcurrentHashMap<>();\n"
    "    private final AtomicReference<Map<String, String>> operationalIssues = new AtomicReference<>(\n"
    "            Map.of(\"bootstrap\", \"Initialization has not completed\")\n"
    "    );\n"
    "    private final PaperRuntimeLifecycle<PaperStorageBindings, ScheduledTask, PersistentChannelClient> lifecycle =\n",
    "plugin health state",
)
plugin = replace_once(
    plugin,
    "    private ClientEvidenceCollector clientEvidenceCollector;\n"
    "    private PaperResourceCloser resources;\n",
    "    private ClientEvidenceCollector clientEvidenceCollector;\n"
    "    private PaperResourceCloser resources;\n"
    "    private PaperConfigurationLoader configurationLoader;\n"
    "    private PaperConfigurationSnapshot configurationSnapshot;\n"
    "    private PunishmentRequestAlertController alertController;\n"
    "    private ConfigurationReloadCoordinator reloadCoordinator;\n",
    "plugin configuration fields",
)

old_enable = '''        resources = new PaperResourceCloser(getLogger());
        saveDefaultConfig();
        saveResource("reason-policies.yml", false);
        boolean policiesReady = loadReasonPolicies();
        int threads = getConfig().getInt("workers.threads", 4);
        int queueCapacity = getConfig().getInt("workers.queue-capacity", 256);
        workers = BoundedExecutorFactory.create(threads, queueCapacity);
        runtimeComponents = createRuntimeComponents();
'''
new_enable = '''        resources = new PaperResourceCloser(getLogger());
        saveDefaultConfig();
        configurationLoader = new PaperConfigurationLoader();
        try {
            configurationSnapshot = configurationLoader.load(configurationFile(), dataDirectory());
        } catch (PaperConfigurationValidationException exception) {
            publishStartupConfigurationFailure(exception);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        saveResource("reason-policies.yml", false);
        boolean policiesReady = loadReasonPolicies();
        RestartRequiredConfiguration bootstrap = configurationSnapshot.restartRequired();
        workers = BoundedExecutorFactory.create(bootstrap.workerThreads(), bootstrap.workerQueueCapacity());
        initializeAlertController();
        if (policiesReady) {
            reloadCoordinator = new ConfigurationReloadCoordinator(
                    configurationSnapshot,
                    () -> configurationLoader.load(configurationFile(), dataDirectory()),
                    () -> new ReasonPolicyConfigurationLoader().load(reasonPolicyFile()),
                    reasonPolicies,
                    alertController,
                    this::logRejectedConfiguration,
                    this::publishReloadIssue
            );
        }
        runtimeComponents = createRuntimeComponents();
'''
plugin = replace_once(plugin, old_enable, new_enable, "plugin startup composition")
plugin = plugin.replace(
    "PaperCommandRegistrar.registerStatus(this, health);",
    "PaperCommandRegistrar.registerStatus(this, health, reloadAction());",
)

insertion_point = '''    public ExecutorService workers() {
        return workers;
    }

'''
configuration_methods = '''    public ExecutorService workers() {
        return workers;
    }

    private void initializeAlertController() {
        String owner = "paper:" + networkServerId() + ":" + UUID.randomUUID();
        alertController = new PunishmentRequestAlertController(
                owner,
                configurationSnapshot.punishmentRequestAlerts(),
                (leaseOwner, settings, storage) -> new PunishmentRequestAlertLifecycle(
                        this,
                        Clock.systemUTC(),
                        leaseOwner,
                        settings,
                        storage.alerts(),
                        storage.requests(),
                        storage.players(),
                        workers,
                        lifecycle::stopping
                ),
                this::publishAlertStatus
        );
    }

    private Path dataDirectory() {
        return getDataFolder().toPath().toAbsolutePath().normalize();
    }

    private Path configurationFile() {
        return dataDirectory().resolve("config.yml");
    }

    private Path reasonPolicyFile() {
        return dataDirectory().resolve("reason-policies.yml");
    }

    private void publishStartupConfigurationFailure(PaperConfigurationValidationException exception) {
        featureIssues.put("configuration", "Startup configuration validation failed");
        health.update(OperationalMode.DEGRADED, Map.copyOf(featureIssues));
        getLogger().severe("EnthusiaStaff configuration is invalid; the plugin will be disabled");
        for (String error : exception.errors()) {
            getLogger().severe("Configuration error: " + error);
        }
    }

    private ConfigurationReloadAction reloadAction() {
        ConfigurationReloadCoordinator coordinator = reloadCoordinator;
        if (coordinator != null) {
            return coordinator;
        }
        return () -> new ConfigurationReloadResult(
                ConfigurationReloadResult.Outcome.APPLY_FAILED,
                "Reload is unavailable because initial reason-policy validation did not succeed",
                List.of("Correct reason-policies.yml and perform a full server restart"),
                false
        );
    }

    private void logRejectedConfiguration(List<String> details) {
        getLogger().warning("EnthusiaStaff configuration reload was rejected");
        for (String detail : details) {
            getLogger().warning("Reload detail: " + detail);
        }
    }

    private void publishReloadIssue(String issue) {
        if (issue == null || issue.isBlank()) {
            featureIssues.remove("configuration-reload");
        } else {
            featureIssues.put("configuration-reload", issue);
        }
        refreshHealth(mode.get());
    }

    private void publishAlertStatus(PunishmentRequestAlertController.Status status) {
        if (status.state() == PunishmentRequestAlertController.State.ACTIVE
                || status.state() == PunishmentRequestAlertController.State.CLOSED) {
            featureIssues.remove("punishment-request-alerts");
        } else {
            featureIssues.put("punishment-request-alerts", status.issue());
        }
        refreshHealth(mode.get());
    }

'''
plugin = replace_once(plugin, insertion_point, configuration_methods, "plugin configuration methods")
plugin = replace_once(
    plugin,
    '''        promoteAfterBootstrap(bindings.runtime());
        if (lifecycle.stopping()) {
            return;
        }
''',
    '''        attachPunishmentRequestAlertStorage(bindings);
        if (lifecycle.stopping()) {
            return;
        }
        promoteAfterBootstrap(bindings.runtime());
        if (lifecycle.stopping()) {
            return;
        }
''',
    "alert storage attachment",
)
marker = '''    private void registerOperationalStateTask() {
'''
attach_method = '''    private void attachPunishmentRequestAlertStorage(PaperStorageBindings bindings) {
        if (alertController == null || lifecycle.stopping()) {
            return;
        }
        PunishmentRequestAlertController.ApplyResult result = alertController.attachStorage(
                new PunishmentRequestAlertController.Storage(
                        bindings.punishmentRequestAlertStore(),
                        bindings.punishmentRequestStore(),
                        bindings.playerDirectory()
                )
        );
        if (result.failure() != null) {
            getLogger().log(Level.SEVERE, "Punishment-request alert subsystem startup failed", result.failure());
        }
    }

'''
plugin = replace_once(plugin, marker, attach_method + marker, "alert attach method")
plugin = replace_once(
    plugin,
    '''        if (removed.isPresent()) {
            cancelOperationalStateTask();
''',
    '''        if (removed.isPresent()) {
            if (alertController != null) {
                alertController.detachStorage();
            }
            cancelOperationalStateTask();
''',
    "failed storage alert detach",
)
plugin = replace_once(
    plugin,
    '''        cancelOperationalStateTask();
        closeChannelClient();
    }
''',
    '''        resources.close("punishment-request alert controller", alertController);
        cancelOperationalStateTask();
        closeChannelClient();
    }
''',
    "alert shutdown ordering",
)
plugin = replace_once(
    plugin,
    '''    private void publishHealth(OperationalMode currentMode, Map<String, String> dynamicIssues) {
        Map<String, String> combined = new java.util.LinkedHashMap<>(featureIssues);
        combined.putAll(dynamicIssues);
        health.update(currentMode, combined);
    }
''',
    '''    private void publishHealth(OperationalMode currentMode, Map<String, String> dynamicIssues) {
        operationalIssues.set(Map.copyOf(dynamicIssues));
        refreshHealth(currentMode);
    }

    private void refreshHealth(OperationalMode currentMode) {
        Map<String, String> combined = new java.util.LinkedHashMap<>(featureIssues);
        combined.putAll(operationalIssues.get());
        health.update(currentMode, combined);
    }
''',
    "health issue composition",
)
plugin = replace_once(
    plugin,
    '''    private String networkServerId() {
        return getConfig().getString("network.server-id", "SMP");
    }

    private String inventoryScopeId() {
        return getConfig().getString("inventory.scope-id", networkServerId());
    }
''',
    '''    private String networkServerId() {
        return configurationSnapshot.restartRequired().networkServerId();
    }

    private String inventoryScopeId() {
        return configurationSnapshot.restartRequired().inventoryScopeId();
    }
''',
    "validated runtime identifiers",
)
plugin = replace_once(
    plugin,
    '''        PaperPersistentChannelFactory.start(
                this,
                (backendId, envelope) -> messageHandler.handle(runtime.networkOutboxStore(), backendId, envelope),
''',
    '''        PaperPersistentChannelFactory.Settings channel = PaperPersistentChannelFactory.snapshot(
                configurationSnapshot.restartRequired(),
                dataDirectory()
        );
        if (!channel.enabled()) {
            getLogger().warning("Persistent Velocity channel is disabled; new punishment writes remain disabled");
        }
        PaperPersistentChannelFactory.start(
                channel,
                (backendId, envelope) -> messageHandler.handle(runtime.networkOutboxStore(), backendId, envelope),
''',
    "validated channel startup",
)
plugin_path.write_text(plugin, encoding="utf-8")

runtime_path = Path("persistence/src/main/java/net/enthusia/staff/persistence/MariaDbRuntime.java")
runtime = runtime_path.read_text(encoding="utf-8")
start = runtime.index("<<<<<<< ours")
end = runtime.index(">>>>>>> theirs", start) + len(">>>>>>> theirs")
resolved = '''        if (protector == null) {
            throw new IllegalArgumentException("network identity protector must be present");
        }
        return new LiteBansMigrationService(dataSource, jsonMapper(), Clock.systemUTC(), protector);'''
runtime_path.write_text(runtime[:start] + resolved + runtime[end:], encoding="utf-8")

registrar_path = Path("paper/src/main/java/net/enthusia/staff/paper/PaperCommandRegistrar.java")
registrar = registrar_path.read_text(encoding="utf-8")
registrar = replace_once(
    registrar,
    "import net.enthusia.staff.paper.command.VanishCommand;\n",
    "import net.enthusia.staff.paper.command.VanishCommand;\n"
    "import net.enthusia.staff.paper.config.reload.ConfigurationReloadAction;\n",
    "registrar reload import",
)
old_status = '''    static void registerStatus(JavaPlugin plugin, RuntimeHealth health) {
        PluginCommand command = Objects.requireNonNull(
                plugin.getCommand("estaff"),
                "estaff command is missing from plugin.yml"
        );
        command.setExecutor(new EstaffCommand(health));
    }
'''
new_status = '''    static void registerStatus(JavaPlugin plugin, RuntimeHealth health) {
        registerStatus(plugin, health, new EstaffCommand(health));
    }

    static void registerStatus(
            JavaPlugin plugin,
            RuntimeHealth health,
            ConfigurationReloadAction reloadAction
    ) {
        registerStatus(plugin, health, new EstaffCommand(health, reloadAction));
    }

    private static void registerStatus(JavaPlugin plugin, RuntimeHealth health, EstaffCommand executor) {
        Objects.requireNonNull(health, "health");
        PluginCommand command = Objects.requireNonNull(
                plugin.getCommand("estaff"),
                "estaff command is missing from plugin.yml"
        );
        command.setExecutor(executor);
        command.setTabCompleter(executor);
    }
'''
registrar_path.write_text(
    replace_once(registrar, old_status, new_status, "registrar status wiring"),
    encoding="utf-8",
)
