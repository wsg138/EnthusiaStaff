package net.enthusia.staff.paper;

import java.time.Clock;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.function.Supplier;
import java.util.logging.Level;
import net.enthusia.staff.domain.OperationalMode;
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
import net.enthusia.staff.paper.freeze.FreezeManager;
import net.enthusia.staff.paper.inventory.InventoryCoordinator;
import net.enthusia.staff.paper.inventory.InventoryOperationContext;
import net.enthusia.staff.paper.report.ReportEvidenceMaintenance;
import net.enthusia.staff.paper.staff.StaffModeManager;
import net.enthusia.staff.paper.staff.StaffToolTransferListener;
import net.enthusia.staff.paper.visibility.DefaultStaffVisibilityService;
import net.enthusia.staff.paper.visibility.VanishManager;
import org.bukkit.event.Listener;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

record PaperRuntimeComponents(
        ReportEvidenceMaintenance reportEvidenceMaintenance,
        FreezeManager freeze,
        StaffModeManager staffMode,
        DefaultStaffVisibilityService visibility,
        VanishManager vanish,
        InventoryOperationContext inventoryContext,
        InventoryCoordinator inventory
) {
    static PaperRuntimeComponents create(Dependencies dependencies) {
        ReportEvidenceMaintenance evidence = new ReportEvidenceMaintenance(
                dependencies.environment().clock(),
                dependencies.stores().reportStore(),
                dependencies.policy().reportPolicy(),
                dependencies.environment().plugin().getLogger()
        );
        FreezeManager freeze = createFreezeManager(dependencies);
        StaffModeManager staffMode = createStaffModeManager(dependencies);
        DefaultStaffVisibilityService visibility = createVisibilityService(dependencies);
        VanishManager vanish = createVanishManager(dependencies, staffMode, visibility);
        InventoryOperationContext inventoryContext = new InventoryOperationContext(
                dependencies.environment().clock(),
                dependencies.environment().inventoryScopeId(),
                dependencies.environment().serverId()
        );
        InventoryCoordinator inventory = createInventoryCoordinator(dependencies, inventoryContext);
        return new PaperRuntimeComponents(
                evidence, freeze, staffMode, visibility, vanish, inventoryContext, inventory
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
                staffMode::active,
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
        registerListener(plugin, staffMode);
        return staffMode;
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
        VanishManager vanish = new VanishManager(
                dependencies.environment().plugin(),
                dependencies.environment().clock(),
                visibility,
                dependencies.stores().vanishStore(),
                dependencies.stores().staffSessionStore(),
                staffMode,
                dependencies.environment().workers()
        );
        staffMode.setExitListener(vanish::staffModeExited);
        registerListener(dependencies.environment().plugin(), vanish);
        return vanish;
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
            Supplier<PlayerDirectory> playerDirectory
    ) {
    }
}
