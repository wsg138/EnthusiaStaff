package net.enthusia.staff.paper;

import java.time.Clock;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.function.Supplier;
import java.util.logging.Level;
import javax.sql.DataSource;
import net.enthusia.staff.domain.OperationalMode;
import net.enthusia.staff.domain.ports.CheatTesterJournalStore;
import net.enthusia.staff.domain.ports.FakeBaseAuditStore;
import net.enthusia.staff.domain.ports.FreezeStore;
import net.enthusia.staff.domain.ports.InventoryJournalStore;
import net.enthusia.staff.domain.ports.PlayerDirectory;
import net.enthusia.staff.domain.ports.ReportStore;
import net.enthusia.staff.domain.ports.StaffSessionStore;
import net.enthusia.staff.domain.ports.VanishStore;
import net.enthusia.staff.domain.report.ReportPolicy;
import net.enthusia.staff.domain.report.ReportPolicyRuntime;
import net.enthusia.staff.paper.api.InventoryLockService;
import net.enthusia.staff.paper.api.StaffModeQueryService;
import net.enthusia.staff.paper.api.StaffSessionService;
import net.enthusia.staff.paper.api.StaffVisibilityService;
import net.enthusia.staff.paper.audit.StaffActionAuditListener;
import net.enthusia.staff.paper.audit.StaffActionLogger;
import net.enthusia.staff.paper.freeze.FreezeManager;
import net.enthusia.staff.paper.freeze.FreezeNetworkReconciler;
import net.enthusia.staff.paper.freeze.FreezeNoticeService;
import net.enthusia.staff.paper.inventory.InventoryCoordinator;
import net.enthusia.staff.paper.inventory.InventoryOperationContext;
import net.enthusia.staff.paper.inventory.InventoryRecoveryGuard;
import net.enthusia.staff.paper.report.ReportEvidenceMaintenance;
import net.enthusia.staff.paper.staff.HelperObserverProtectionListener;
import net.enthusia.staff.paper.staff.StaffCombatProtectionListener;
import net.enthusia.staff.paper.staff.StaffDoubleCrouchListener;
import net.enthusia.staff.paper.staff.StaffModeDeathListener;
import net.enthusia.staff.paper.staff.StaffModeManager;
import net.enthusia.staff.paper.staff.StaffModeWorldInteractionListener;
import net.enthusia.staff.paper.staff.StaffStatePresentation;
import net.enthusia.staff.paper.staff.StaffToolDispatcher;
import net.enthusia.staff.paper.staff.StaffToolTransferListener;
import net.enthusia.staff.paper.staff.StaffTransferJoinListener;
import net.enthusia.staff.paper.staff.StaffTransferSnapshotCoordinator;
import net.enthusia.staff.paper.tester.CheatTesterCommand;
import net.enthusia.staff.paper.tester.CheatTesterManager;
import net.enthusia.staff.paper.tester.CheatTesterSettings;
import net.enthusia.staff.paper.tester.FakeBaseCommand;
import net.enthusia.staff.paper.tester.FakeBaseManager;
import net.enthusia.staff.paper.visibility.DefaultStaffVisibilityService;
import net.enthusia.staff.paper.visibility.VanishBroadcastListener;
import net.enthusia.staff.paper.visibility.VanishManager;
import net.enthusia.staff.paper.visibility.VanishTargetingGuard;
import net.enthusia.staff.paper.auth.LuckPermsStaffDutyContext;
import net.enthusia.staff.paper.visibility.PrivateMessagePresenceListener;
import org.bukkit.event.Listener;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

record PaperRuntimeComponents(
        ReportEvidenceMaintenance reportEvidenceMaintenance,
        FreezeManager freeze,
        FreezeNoticeService freezeNotices,
        FreezeNetworkReconciler freezeNetworkReconciler,
        StaffModeManager staffMode,
        CheatTesterManager cheatTester,
        FakeBaseManager fakeBases,
        StaffToolDispatcher staffTools,
        DefaultStaffVisibilityService visibility,
        VanishManager vanish,
        InventoryOperationContext inventoryContext,
        InventoryCoordinator inventory,
        StaffTransferSnapshotCoordinator transferSnapshots,
        StaffActionLogger staffActionLogger
) {
    static PaperRuntimeComponents create(Dependencies dependencies) {
        ReportEvidenceMaintenance evidence = new ReportEvidenceMaintenance(
                dependencies.environment().clock(),
                dependencies.stores().reportStore(),
                dependencies.policy().reportPolicy(),
                dependencies.environment().plugin().getLogger()
        );
        FreezeManager freeze = createFreezeManager(dependencies);
        FreezeNoticeService freezeNotices = createFreezeNoticeService(dependencies, freeze);
        FreezeNetworkReconciler freezeNetworkReconciler = new FreezeNetworkReconciler(
                dependencies.environment().plugin(),
                dependencies.environment().clock(),
                dependencies.stores().freezeStore(),
                dependencies.environment().workers(),
                freeze
        );
        StaffModeManager staffMode = createStaffModeManager(dependencies);
        registerStaffDutyContext(dependencies, staffMode);
        DefaultStaffVisibilityService visibility = createVisibilityService(dependencies);
        VanishManager vanish = createVanishManager(dependencies, staffMode, visibility);
        StaffActionLogger staffActionLogger = createStaffActionLogger(dependencies, staffMode, vanish);
        StaffTransferSnapshotCoordinator transferSnapshots = new StaffTransferSnapshotCoordinator(
                dependencies.environment().plugin(),
                dependencies.environment().clock(),
                dependencies.environment().serverId(),
                staffMode,
                vanish
        );
        StaffStatePresentation statePresentation = new StaffStatePresentation(
                dependencies.environment().plugin(), staffMode, vanish
        );
        registerListener(dependencies.environment().plugin(), statePresentation);
        statePresentation.start();
        registerOperationalListeners(dependencies, vanish, transferSnapshots);
        // Owner-mandated: no staff member may ever be combat-tagged while on duty or vanished.
        new StaffCombatProtectionListener(
                dependencies.environment().plugin(),
                staffMode,
                vanish,
                staffMode.combat()
        ).start();
        InventoryOperationContext inventoryContext = new InventoryOperationContext(
                dependencies.environment().clock(),
                dependencies.environment().inventoryScopeId(),
                dependencies.environment().serverId()
        );
        InventoryCoordinator inventory = createInventoryCoordinator(dependencies, inventoryContext);
        FakeBaseManager fakeBases = createFakeBaseManager(dependencies, staffMode);
        CheatTesterManager cheatTester = createCheatTesterManager(dependencies, staffMode, inventory, fakeBases);
        StaffToolDispatcher staffTools = createStaffToolDispatcher(
                dependencies,
                staffMode,
                vanish,
                freeze,
                cheatTester
        );
        staffMode.startRankReconciliation();
        vanish.startRankReconciliation();
        dependencies.environment().plugin().getServer().getGlobalRegionScheduler().runAtFixedRate(
                dependencies.environment().plugin(),
                ignored -> cheatTester.recoverOnlinePlayers(),
                40L,
                6000L
        );
        return new PaperRuntimeComponents(
                evidence,
                freeze,
                freezeNotices,
                freezeNetworkReconciler,
                staffMode,
                cheatTester,
                fakeBases,
                staffTools,
                visibility,
                vanish,
                inventoryContext,
                inventory,
                transferSnapshots,
                staffActionLogger
        );
    }

    void registerServices(JavaPlugin plugin) {
        plugin.getServer().getServicesManager().register(
                StaffVisibilityService.class,
                visibility,
                plugin,
                ServicePriority.Normal
        );
        plugin.getServer().getServicesManager().register(
                InventoryLockService.class,
                inventory,
                plugin,
                ServicePriority.Normal
        );
        plugin.getServer().getServicesManager().register(
                StaffModeQueryService.class,
                staffMode::active,
                plugin,
                ServicePriority.Normal
        );
        plugin.getServer().getServicesManager().register(
                StaffSessionService.class,
                staffMode::authorityActive,
                plugin,
                ServicePriority.Normal
        );
        plugin.getServer().getServicesManager().register(
                FreezeNetworkReconciler.class,
                freezeNetworkReconciler,
                plugin,
                ServicePriority.Normal
        );
    }

    private static FreezeManager createFreezeManager(Dependencies dependencies) {
        FreezeManager freeze = new FreezeManager(
                dependencies.environment().plugin(),
                dependencies.environment().clock(),
                dependencies.stores().freezeStore(),
                dependencies.environment().workers()
        );
        registerListener(dependencies.environment().plugin(), freeze);
        return freeze;
    }

    private static FreezeNoticeService createFreezeNoticeService(
            Dependencies dependencies,
            FreezeManager freeze
    ) {
        JavaPlugin plugin = dependencies.environment().plugin();
        FreezeNoticeService notices = new FreezeNoticeService(
                plugin, dependencies.stores().playerDirectory(), dependencies.environment().workers(), freeze
        );
        freeze.setNoticeSink(notices);
        return notices;
    }

    private static StaffModeManager createStaffModeManager(Dependencies dependencies) {
        JavaPlugin plugin = dependencies.environment().plugin();
        StaffModeManager staffMode = new StaffModeManager(
                plugin,
                dependencies.environment().clock(),
                dependencies.environment().serverId(),
                dependencies.stores().staffSessionStore(),
                dependencies.environment().workers()
        );
        registerListener(plugin, new StaffToolTransferListener(plugin, staffMode));
        registerListener(plugin, new HelperObserverProtectionListener(staffMode));
        registerListener(plugin, new StaffModeDeathListener(staffMode));
        registerListener(plugin, new StaffModeWorldInteractionListener(staffMode));
        registerListener(plugin, new StaffDoubleCrouchListener(plugin, staffMode));
        registerListener(plugin, staffMode);
        return staffMode;
    }

    private static void registerStaffDutyContext(Dependencies dependencies, StaffModeManager staffMode) {
        JavaPlugin plugin = dependencies.environment().plugin();
        if (plugin.getServer().getPluginManager().getPlugin("LuckPerms") == null) {
            dependencies.featureIssues().put("staff-duty-context",
                    "LuckPerms is unavailable; Staff Mode active-duty permission context is disabled");
            return;
        }
        try {
            LuckPermsStaffDutyContext.install(plugin, staffMode);
            dependencies.featureIssues().remove("staff-duty-context");
        } catch (IllegalStateException | LinkageError exception) {
            dependencies.featureIssues().put("staff-duty-context", "LuckPerms Staff Mode context could not be registered");
            plugin.getLogger().log(Level.WARNING, "Staff Mode LuckPerms active-duty context registration failed", exception);
        }
    }

    private static DefaultStaffVisibilityService createVisibilityService(Dependencies dependencies) {
        try {
            return new DefaultStaffVisibilityService(new VisibilityMatrixLoader().load(
                    dependencies.environment().plugin().getConfig()::getStringList
            ));
        } catch (IllegalArgumentException exception) {
            dependencies.featureIssues().put(
                    "visibility",
                    "Configured rank visibility matrix is invalid; safe defaults are active"
            );
            dependencies.environment().plugin().getLogger().log(
                    Level.SEVERE,
                    "Vanish visibility matrix validation failed",
                    exception
            );
            return new DefaultStaffVisibilityService(DefaultStaffVisibilityService.defaultMatrix());
        }
    }

    private static VanishManager createVanishManager(
            Dependencies dependencies,
            StaffModeManager staffMode,
            DefaultStaffVisibilityService visibility
    ) {
        JavaPlugin plugin = dependencies.environment().plugin();
        VanishTargetingGuard targeting = new VanishTargetingGuard(plugin, visibility::isVanished);
        visibility.setVanishEnabledListener(playerId ->
                scheduleVanishTargetingReconciliation(plugin, targeting, playerId));
        registerListener(plugin, targeting);
        VanishManager vanish = new VanishManager(
                plugin,
                dependencies.environment().clock(),
                visibility,
                dependencies.stores().vanishStore(),
                dependencies.stores().staffSessionStore(),
                staffMode,
                dependencies.environment().workers()
        );
        staffMode.setExitListener(vanish::staffModeExited);
        staffMode.setGameModeTransitionGuard(
                vanish::beginPluginGameModeApplication,
                vanish::endPluginGameModeApplication
        );
        registerListener(plugin, vanish);
        return vanish;
    }

    /**
     * Builds and wires the staff-action audit pipeline (overnight permission model):
     * per-server JSON-lines file log, Discord-bot outbox file, best-effort {@code discord_outbox}
     * insert, plus the vanished/on-duty command+teleport+gamemode audit listener.
     */
    private static StaffActionLogger createStaffActionLogger(
            Dependencies dependencies,
            StaffModeManager staffMode,
            VanishManager vanish
    ) {
        JavaPlugin plugin = dependencies.environment().plugin();
        StaffActionLogger logger = new StaffActionLogger(
                plugin.getLogger(),
                dependencies.environment().workers(),
                dependencies.stores().dataSource(),
                dependencies.environment().serverId(),
                plugin.getConfig().getString("discord.log-forward-channel", ""),
                plugin.getDataFolder().toPath()
        );
        staffMode.setActionLogger(logger);
        staffMode.setVanishedLookup(vanish::isVanished);
        registerListener(plugin, new StaffActionAuditListener(logger, staffMode, vanish));
        return logger;
    }

    private static void scheduleVanishTargetingReconciliation(
            JavaPlugin plugin,
            VanishTargetingGuard targeting,
            UUID playerId
    ) {
        try {
            plugin.getServer().getGlobalRegionScheduler().execute(
                    plugin,
                    () -> scheduleVanishTargetingForPlayer(plugin, targeting, playerId)
            );
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.WARNING, "Vanish targeting reconciliation could not be scheduled", exception);
        }
    }

    private static void scheduleVanishTargetingForPlayer(
            JavaPlugin plugin,
            VanishTargetingGuard targeting,
            UUID playerId
    ) {
        var player = plugin.getServer().getPlayer(playerId);
        if (player == null) {
            return;
        }
        try {
            if (!player.getScheduler().execute(plugin, () -> targeting.reconcile(player), null, 1L)) {
                plugin.getLogger().fine("Vanish targeting reconciliation retired before execution");
            }
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.WARNING, "Vanish targeting reconciliation scheduling failed", exception);
        }
    }

    private static void registerOperationalListeners(
            Dependencies dependencies,
            VanishManager vanish,
            StaffTransferSnapshotCoordinator transferSnapshots
    ) {
        JavaPlugin plugin = dependencies.environment().plugin();
        registerListener(plugin, new PaperPresenceListener(
                plugin,
                dependencies.environment().clock(),
                dependencies.environment().serverId(),
                dependencies.stores().playerDirectory(),
                dependencies.environment().workers()
        ));
        registerListener(plugin, new VanishBroadcastListener(vanish));
        registerListener(plugin, new PrivateMessagePresenceListener(plugin, vanish));
        registerListener(plugin, new StaffTransferJoinListener(plugin, transferSnapshots, vanish));
    }

    private static FakeBaseManager createFakeBaseManager(
            Dependencies dependencies,
            StaffModeManager staffMode
    ) {
        JavaPlugin plugin = dependencies.environment().plugin();
        Supplier<FakeBaseAuditStore> auditStore = () -> {
            InventoryJournalStore storage = dependencies.stores().inventoryJournalStore().get();
            return storage instanceof FakeBaseAuditStore fakeBaseAuditStore ? fakeBaseAuditStore : null;
        };
        FakeBaseManager manager = new FakeBaseManager(
                plugin,
                dependencies.environment().clock(),
                dependencies.environment().serverId(),
                staffMode,
                auditStore,
                dependencies.environment().workers()
        );
        registerListener(plugin, manager);
        return manager;
    }

    private static CheatTesterManager createCheatTesterManager(
            Dependencies dependencies,
            StaffModeManager staffMode,
            InventoryCoordinator inventory,
            FakeBaseManager fakeBases
    ) {
        JavaPlugin plugin = dependencies.environment().plugin();
        Supplier<CheatTesterJournalStore> testerStore = () -> {
            InventoryJournalStore storage = dependencies.stores().inventoryJournalStore().get();
            return storage instanceof CheatTesterJournalStore testerJournal ? testerJournal : null;
        };
        CheatTesterManager manager = new CheatTesterManager(
                plugin,
                dependencies.environment().clock(),
                dependencies.environment().serverId(),
                staffMode,
                inventory,
                testerStore,
                dependencies.environment().workers(),
                CheatTesterSettings.load(plugin.getConfig().getConfigurationSection("staff-tools.cheat-tester"))
        );
        registerListener(plugin, manager);
        CheatTesterCommand commandHandler = new CheatTesterCommand(plugin, manager, fakeBases);
        var command = java.util.Objects.requireNonNull(
                plugin.getCommand("cheattester"),
                "cheattester command is missing from plugin.yml"
        );
        command.setExecutor(commandHandler);
        command.setTabCompleter(commandHandler);
        FakeBaseCommand fakeBaseHandler = new FakeBaseCommand(plugin, fakeBases);
        var fakeBaseCommand = java.util.Objects.requireNonNull(
                plugin.getCommand("fakebase"),
                "fakebase command is missing from plugin.yml"
        );
        fakeBaseCommand.setExecutor(fakeBaseHandler);
        fakeBaseCommand.setTabCompleter(fakeBaseHandler);
        return manager;
    }

    private static StaffToolDispatcher createStaffToolDispatcher(
            Dependencies dependencies,
            StaffModeManager staffMode,
            VanishManager vanish,
            FreezeManager freeze,
            CheatTesterManager cheatTester
    ) {
        JavaPlugin plugin = dependencies.environment().plugin();
        StaffToolDispatcher dispatcher = new StaffToolDispatcher(
                plugin,
                dependencies.environment().clock(),
                dependencies.environment().serverId(),
                staffMode,
                vanish,
                freeze,
                cheatTester
        );
        registerListener(plugin, dispatcher);
        registerListener(plugin, dispatcher.menuListener());
        var command = java.util.Objects.requireNonNull(
                plugin.getCommand("stafftools"),
                "stafftools command is missing from plugin.yml"
        );
        command.setExecutor(dispatcher);
        command.setTabCompleter(dispatcher);
        return dispatcher;
    }

    private static InventoryCoordinator createInventoryCoordinator(
            Dependencies dependencies,
            InventoryOperationContext context
    ) {
        InventoryCoordinator inventory = new InventoryCoordinator(
                dependencies.environment().plugin(),
                context,
                dependencies.policy().writeMode(),
                dependencies.stores().inventoryJournalStore(),
                dependencies.stores().playerDirectory(),
                dependencies.environment().workers()
        );
        registerListener(dependencies.environment().plugin(), inventory);
        registerListener(dependencies.environment().plugin(), new InventoryRecoveryGuard(inventory));
        return inventory;
    }

    private static void registerListener(JavaPlugin plugin, Listener listener) {
        plugin.getServer().getPluginManager().registerEvents(listener, plugin);
    }

    record Dependencies(
            Environment environment,
            Policy policy,
            Stores stores,
            Map<String, String> featureIssues
    ) {
    }

    record Environment(
            JavaPlugin plugin,
            Clock clock,
            String serverId,
            String inventoryScopeId,
            ExecutorService workers
    ) {
    }

    record Policy(
            Supplier<OperationalMode> writeMode,
            Supplier<ReportPolicy> reportPolicy
    ) {
        Policy(Supplier<OperationalMode> writeMode) {
            this(writeMode, ReportPolicyRuntime::current);
        }
    }

    record Stores(
            Supplier<ReportStore> reportStore,
            Supplier<FreezeStore> freezeStore,
            Supplier<StaffSessionStore> staffSessionStore,
            Supplier<VanishStore> vanishStore,
            Supplier<InventoryJournalStore> inventoryJournalStore,
            Supplier<PlayerDirectory> playerDirectory,
            Supplier<DataSource> dataSource
    ) {
    }
}
