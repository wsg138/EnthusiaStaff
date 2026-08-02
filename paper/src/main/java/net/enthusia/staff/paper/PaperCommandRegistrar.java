package net.enthusia.staff.paper;

import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.function.Function;
import java.util.function.Supplier;
import net.enthusia.staff.domain.OperationalMode;
import net.enthusia.staff.domain.application.PunishmentDraftWorkflow;
import net.enthusia.staff.domain.application.PunishmentRequestService;
import net.enthusia.staff.domain.application.SanctionChangeService;
import net.enthusia.staff.domain.auth.AuthorizationPolicy;
import net.enthusia.staff.domain.ports.AtomicReasonPolicyRepository;
import net.enthusia.staff.domain.ports.CaseLookup;
import net.enthusia.staff.domain.ports.ModerationHistoryStore;
import net.enthusia.staff.domain.ports.PlayerDirectory;
import net.enthusia.staff.paper.client.ClientEvidenceCollector;
import net.enthusia.staff.paper.command.CaseCommand;
import net.enthusia.staff.paper.command.ClientCommand;
import net.enthusia.staff.paper.command.EstaffCommand;
import net.enthusia.staff.paper.command.FreezeCommand;
import net.enthusia.staff.paper.command.HistoryCommand;
import net.enthusia.staff.paper.command.InspectCommand;
import net.enthusia.staff.paper.command.InventoryCommand;
import net.enthusia.staff.paper.command.PunishmentCommand;
import net.enthusia.staff.paper.command.PunishmentRequestCommandHandler;
import net.enthusia.staff.paper.command.ReportCommand;
import net.enthusia.staff.paper.command.ReportsCommand;
import net.enthusia.staff.paper.command.SanctionChangeCommand;
import net.enthusia.staff.paper.command.SanctionLifecycleCommand;
import net.enthusia.staff.paper.command.StaffChatCommand;
import net.enthusia.staff.paper.command.StaffModeCommand;
import net.enthusia.staff.paper.command.VanishCommand;
import net.enthusia.staff.paper.config.ModerationFeatureSettings;
import net.enthusia.staff.paper.config.ReloadableModerationFeatureSettings;
import net.enthusia.staff.paper.config.reload.ConfigurationReloadAction;
import net.enthusia.staff.paper.economy.EconomyCoordinator;
import net.enthusia.staff.paper.freeze.FreezeManager;
import net.enthusia.staff.paper.integration.MarketIntegration;
import net.enthusia.staff.paper.integration.ReputationIntegration;
import net.enthusia.staff.paper.integration.RoseChatIntegration;
import net.enthusia.staff.paper.inventory.ConfiscationCoordinator;
import net.enthusia.staff.paper.inventory.InventoryCoordinator;
import net.enthusia.staff.paper.punishment.PunishmentGuiController;
import net.enthusia.staff.paper.punishment.PunishmentRequestGuiController;
import net.enthusia.staff.paper.report.ChatContextBuffer;
import net.enthusia.staff.paper.sanction.SanctionChangeGuiController;
import net.enthusia.staff.paper.staff.StaffModeManager;
import net.enthusia.staff.paper.visibility.VanishManager;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.plugin.java.JavaPlugin;

final class PaperCommandRegistrar {
    private static final List<String> PUNISHMENT_COMMANDS =
            List.of("punish", "ban", "mute", "warn", "kick", "ipban");
    private static final List<String> SANCTION_CHANGE_COMMANDS =
            List.of("removepunishment", "unban", "unmute", "removewarning", "unwarn");
    private static final List<String> INVENTORY_COMMANDS = List.of("invsee", "endersee");

    private final Dependencies dependencies;
    private final ReloadableModerationFeatureSettings moderationSettings;

    PaperCommandRegistrar(Dependencies dependencies) {
        this.dependencies = Objects.requireNonNull(dependencies, "dependencies");
        this.moderationSettings = new ReloadableModerationFeatureSettings(
                Objects.requireNonNull(
                        dependencies.environment().moderationFeatures().get(),
                        "validated moderation features"
                )
        );
    }

    static void registerStatus(JavaPlugin plugin, RuntimeHealth health) {
        registerStatus(plugin, health, new EstaffCommand(health));
    }

    static void registerStatus(
            JavaPlugin plugin,
            RuntimeHealth health,
            ConfigurationReloadAction reloadAction
    ) {
        registerStatus(plugin, health, new EstaffCommand(plugin, health, reloadAction));
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

    void register() {
        configureEstaff();
        registerPunishmentCommands();
        registerSanctionChangeCommands();
        registerReportCommands();
        registerStaffCommands();
        registerInventoryCommands();
        registerInspectionCommands();
    }

    private void configureEstaff() {
        PluginCommand command = requiredCommand("estaff");
        if (!(command.getExecutor() instanceof EstaffCommand estaff)) {
            throw new IllegalStateException("estaff command executor was not registered before feature commands");
        }
        estaff.addSuccessfulReloadHook(() -> moderationSettings.reloadFrom(
                dependencies.environment().moderationFeatures().get()
        ));
        estaff.configureSanctionLifecycle(new SanctionLifecycleCommand(
                plugin(),
                clock(),
                dependencies.environment().serverId(),
                writeMode(),
                storage(PaperStorageBindings::sanctionChangeService),
                moderationSettings::current,
                workers()
        ));
    }

    private void registerPunishmentCommands() {
        Supplier<PunishmentDraftWorkflow> drafts = storage(PaperStorageBindings::punishmentDraftWorkflow);
        Supplier<PunishmentRequestService> requests = storage(PaperStorageBindings::punishmentRequestService);
        Supplier<PlayerDirectory> players = storage(PaperStorageBindings::playerDirectory);
        PunishmentGuiController punishmentGui = new PunishmentGuiController(
                plugin(), writeMode(), drafts, players, authorization(), reasons(), workers()
        );
        plugin().getServer().getPluginManager().registerEvents(punishmentGui, plugin());
        PunishmentRequestGuiController requestGui = new PunishmentRequestGuiController(
                plugin(), requests, players, authorization(), workers()
        );
        requestGui.register();
        PunishmentRequestCommandHandler requestHandler = new PunishmentRequestCommandHandler(
                plugin(), requests, authorization(), requestGui, workers()
        );
        PunishmentCommand command = new PunishmentCommand(
                plugin(), writeMode(), drafts, players, authorization(), punishmentGui, requestHandler, workers()
        );
        PUNISHMENT_COMMANDS.forEach(name -> bindCompleting(name, command, command));
    }

    private void registerSanctionChangeCommands() {
        Supplier<SanctionChangeService> changes = storage(PaperStorageBindings::sanctionChangeService);
        Supplier<PlayerDirectory> players = storage(PaperStorageBindings::playerDirectory);
        Supplier<CaseLookup> cases = storage(PaperStorageBindings::caseLookup);
        SanctionChangeGuiController changeGui = new SanctionChangeGuiController(
                plugin(), clock(), writeMode(), changes, players, cases,
                storage(PaperStorageBindings::caseReviewStore), authorization(), workers()
        );
        plugin().getServer().getPluginManager().registerEvents(changeGui, plugin());
        SanctionChangeCommand command = new SanctionChangeCommand(
                plugin(), writeMode(), changes, players, cases, authorization(), workers(), changeGui
        );
        SANCTION_CHANGE_COMMANDS.forEach(name -> bindCompleting(name, command, command));
    }

    private void registerReportCommands() {
        ReportCommand report = new ReportCommand(
                new ReportCommand.Dependencies(
                        plugin(), clock(), dependencies.environment().serverId(), authoritativeMode(),
                        storage(PaperStorageBindings::playerDirectory),
                        storage(PaperStorageBindings::reportStore),
                        storage(PaperStorageBindings::sanctionLookup),
                        reasons(), dependencies.evidence().chatContext(), dependencies.evidence().clientEvidence()
                ),
                workers()
        );
        bindCompleting("report", report, report);
        ClientCommand client = new ClientCommand(
                plugin(), dependencies.evidence().clientEvidence(),
                storage(PaperStorageBindings::clientEvidenceStore), workers()
        );
        bindCompleting("client", client, client);
        ReportsCommand reports = new ReportsCommand(
                plugin(), clock(), storage(PaperStorageBindings::reportStore), workers()
        );
        bindCompleting("reports", reports, reports);
    }

    private void registerStaffCommands() {
        FreezeCommand freezes = new FreezeCommand(
                plugin(), clock(), writeMode(), storage(PaperStorageBindings::playerDirectory),
                storage(PaperStorageBindings::freezeStore), dependencies.players().freeze(), workers()
        );
        bind("freeze", freezes);
        bind("unfreeze", freezes);
        bind("staff", new StaffModeCommand(writeMode(), dependencies.players().staffMode()));
        bind("vanish", new VanishCommand(writeMode(), dependencies.players().vanish()));
        bind("staffchat", new StaffChatCommand(dependencies.integrations().roseChat()));
    }

    private void registerInventoryCommands() {
        Supplier<PlayerDirectory> players = storage(PaperStorageBindings::playerDirectory);
        InventoryCommand command = new InventoryCommand(
                plugin(), clock(), players, dependencies.players().inventory(), workers()
        );
        INVENTORY_COMMANDS.forEach(name -> bindCompleting(name, command, command));
    }

    private void registerInspectionCommands() {
        Supplier<PlayerDirectory> players = storage(PaperStorageBindings::playerDirectory);
        Supplier<CaseLookup> cases = storage(PaperStorageBindings::caseLookup);
        Supplier<ModerationHistoryStore> histories = storage(PaperStorageBindings::moderationHistoryStore);
        InspectCommand inspect = new InspectCommand(
                plugin(), clock(), players, cases,
                dependencies.integrations().economy(), dependencies.integrations().confiscation(),
                dependencies.players().inventory(), authorization(), dependencies.integrations().market(),
                dependencies.integrations().reputation(), workers()
        );
        bindCompleting("inspect", inspect, inspect);
        HistoryCommand history = new HistoryCommand(
                plugin(), players, histories, moderationSettings::current, workers()
        );
        bindCompleting("history", history, history);
        bind("case", new CaseCommand(
                plugin(), cases, dependencies.integrations().confiscation(), histories,
                moderationSettings::current, authorization(), workers()
        ));
    }

    private void bind(String name, CommandExecutor executor) {
        requiredCommand(name).setExecutor(executor);
    }

    private void bindCompleting(String name, CommandExecutor executor, TabCompleter completer) {
        PluginCommand command = requiredCommand(name);
        command.setExecutor(executor);
        command.setTabCompleter(completer);
    }

    private PluginCommand requiredCommand(String name) {
        return Objects.requireNonNull(
                plugin().getCommand(name),
                name + " command is missing from plugin.yml"
        );
    }

    private <T> Supplier<T> storage(Function<PaperStorageBindings, T> selector) {
        return () -> dependencies.storage().get().map(selector).orElse(null);
    }

    private JavaPlugin plugin() {
        return dependencies.environment().plugin();
    }

    private Clock clock() {
        return dependencies.environment().clock();
    }

    private ExecutorService workers() {
        return dependencies.environment().workers();
    }

    private Supplier<OperationalMode> authoritativeMode() {
        return dependencies.policy().authoritativeMode();
    }

    private Supplier<OperationalMode> writeMode() {
        return dependencies.policy().writeMode();
    }

    private AuthorizationPolicy authorization() {
        return dependencies.policy().authorization();
    }

    private AtomicReasonPolicyRepository reasons() {
        return dependencies.policy().reasons();
    }

    record Dependencies(
            Environment environment,
            Policy policy,
            Supplier<Optional<PaperStorageBindings>> storage,
            PlayerComponents players,
            IntegrationSuppliers integrations,
            EvidenceComponents evidence
    ) {
    }

    record Environment(
            JavaPlugin plugin,
            Clock clock,
            String serverId,
            ExecutorService workers,
            Supplier<ModerationFeatureSettings> moderationFeatures
    ) {
    }

    record Policy(
            Supplier<OperationalMode> authoritativeMode,
            Supplier<OperationalMode> writeMode,
            AuthorizationPolicy authorization,
            AtomicReasonPolicyRepository reasons
    ) {
    }

    record PlayerComponents(
            FreezeManager freeze,
            StaffModeManager staffMode,
            VanishManager vanish,
            InventoryCoordinator inventory
    ) {
    }

    record IntegrationSuppliers(
            Supplier<EconomyCoordinator> economy,
            Supplier<ConfiscationCoordinator> confiscation,
            Supplier<RoseChatIntegration> roseChat,
            Supplier<MarketIntegration> market,
            Supplier<ReputationIntegration> reputation
    ) {
    }

    record EvidenceComponents(ChatContextBuffer chatContext, ClientEvidenceCollector clientEvidence) {
    }
}
