package net.enthusia.staff.paper;

import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.function.Function;
import java.util.function.Supplier;
import net.enthusia.staff.domain.OperationalMode;
import net.enthusia.staff.domain.application.ActivePlaytimeProvider;
import net.enthusia.staff.domain.application.PunishmentDraftWorkflow;
import net.enthusia.staff.domain.application.PunishmentRequestService;
import net.enthusia.staff.domain.application.PunishmentService;
import net.enthusia.staff.domain.application.SanctionChangeService;
import net.enthusia.staff.domain.auth.AuthorizationPolicy;
import net.enthusia.staff.domain.ports.AtomicReasonPolicyRepository;
import net.enthusia.staff.domain.ports.CaseLookup;
import net.enthusia.staff.domain.ports.CaseReviewStore;
import net.enthusia.staff.domain.ports.FreezeStore;
import net.enthusia.staff.domain.ports.ModerationHistoryStore;
import net.enthusia.staff.domain.ports.PlayerDirectory;
import net.enthusia.staff.domain.ports.ReportStore;
import net.enthusia.staff.domain.ports.SanctionLookup;
import net.enthusia.staff.paper.account.PaperOnlinePlayerVerifier;
import net.enthusia.staff.paper.auth.ActiveDutyAuthorizationPolicy;
import net.enthusia.staff.paper.auth.LuckPermsStaffActorLookup;
import net.enthusia.staff.paper.client.ClientEvidenceCollector;
import net.enthusia.staff.paper.command.AccountLinkCommand;
import net.enthusia.staff.paper.command.CaseCommand;
import net.enthusia.staff.paper.command.CaseRecoveryCommand;
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
import net.enthusia.staff.paper.command.StaffApiCommand;
import net.enthusia.staff.paper.command.StaffChatCommand;
import net.enthusia.staff.paper.command.StaffModeCommand;
import net.enthusia.staff.paper.command.StaffModeVanishEntryCoordinator;
import net.enthusia.staff.paper.command.StaffWhoCommand;
import net.enthusia.staff.paper.command.VanishCommand;
import net.enthusia.staff.paper.config.ModerationFeatureSettings;
import net.enthusia.staff.paper.config.ReloadableModerationFeatureSettings;
import net.enthusia.staff.paper.config.ReportConfigurationRuntime;
import net.enthusia.staff.paper.config.ReportConfigurationSnapshot;
import net.enthusia.staff.paper.config.reload.ConfigurationReloadAction;
import net.enthusia.staff.paper.economy.EconomyCoordinator;
import net.enthusia.staff.paper.freeze.FreezeManager;
import net.enthusia.staff.paper.freeze.FreezeNoticeSink;
import net.enthusia.staff.paper.integration.DiscordSrvLinkProviderAdapter;
import net.enthusia.staff.paper.integration.MarketIntegration;
import net.enthusia.staff.paper.integration.PlayTimeActivePlaytimeProvider;
import net.enthusia.staff.paper.integration.ReputationIntegration;
import net.enthusia.staff.paper.integration.RoseChatIntegration;
import net.enthusia.staff.paper.inventory.ConfiscationCoordinator;
import net.enthusia.staff.paper.inventory.InventoryCoordinator;
import net.enthusia.staff.paper.inventory.InventoryRecoveryCoordinator;
import net.enthusia.staff.paper.punishment.PaperCrossPlatformConfiguration;
import net.enthusia.staff.paper.punishment.PaperCrossPlatformPunishmentService;
import net.enthusia.staff.paper.punishment.PunishmentGuiController;
import net.enthusia.staff.paper.punishment.PunishmentRequestGuiController;
import net.enthusia.staff.paper.report.ChatContextBuffer;
import net.enthusia.staff.paper.report.ReportGuiController;
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
        registerStatus(plugin, health, new EstaffCommand(plugin, health));
    }

    static void registerStatus(
            JavaPlugin plugin,
            RuntimeHealth health,
            ConfigurationReloadAction reloadAction
    ) {
        ConfigurationReloadAction reportAware = ReportConfigurationRuntime.initialize(plugin, reloadAction);
        registerStatus(plugin, health, new EstaffCommand(plugin, health, reportAware));
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
        ConsoleCommandAuthority.install(plugin());
        configureEstaff();
        registerAccountLinkCommands();
        registerPunishmentCommands();
        registerStaffApiCommands();
        registerSanctionChangeCommands();
        registerReportCommands();
        registerStaffCommands();
        registerInventoryCommands();
        registerInspectionCommands();
        new net.enthusia.staff.paper.command.PlayerNameCompletion(
                dependencies.players().vanish()::canSee, dependencies.players().staffMode()).install(plugin());
    }

    private void configureEstaff() {
        PluginCommand command = requiredCommand("estaff");
        if (!(command.getExecutor() instanceof EstaffCommand estaff)) {
            throw new IllegalStateException("estaff command executor was not registered before feature commands");
        }
        estaff.configureStorageAvailability(() -> dependencies.storage().get().isPresent());
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

    private void registerAccountLinkCommands() {
        ActivePlaytimeProvider playtime = PlayTimeActivePlaytimeProvider.discover(plugin());
        Optional<DiscordSrvLinkProviderAdapter> discordSrv = DiscordSrvLinkProviderAdapter.discover(plugin());
        PaperOnlinePlayerVerifier online = PaperOnlinePlayerVerifier.register(plugin());
        AuthorizationPolicy linkAuthorization = authorization();
        AccountLinkCommand command = new AccountLinkCommand(
                plugin(),
                storage(bindings -> bindings.accountLinks(linkAuthorization, playtime, online, discordSrv)),
                workers()
        );
        bind("link", command);
        bind("unlink", command);
    }

    private void registerPunishmentCommands() {
        Supplier<PunishmentDraftWorkflow> drafts = storage(PaperStorageBindings::punishmentDraftWorkflow);
        Supplier<PunishmentRequestService> requests = storage(PaperStorageBindings::punishmentRequestService);
        Supplier<PlayerDirectory> players = storage(PaperStorageBindings::playerDirectory);
        Supplier<ModerationHistoryStore> histories = storage(PaperStorageBindings::moderationHistoryStore);
        Supplier<CaseReviewStore> cases = storage(PaperStorageBindings::caseReviewStore);
        Supplier<SanctionLookup> sanctions = storage(PaperStorageBindings::sanctionLookup);
        Supplier<ReportStore> reports = storage(PaperStorageBindings::reportStore);
        AuthorizationPolicy activeAuthorization = activeAuthorization();
        Optional<PaperCrossPlatformConfiguration> crossPlatformConfiguration =
                PaperCrossPlatformConfiguration.fromSystemEnvironment();
        java.util.function.Function<java.util.UUID, Optional<net.enthusia.staff.domain.auth.Actor>> targetStaff =
                LuckPermsStaffActorLookup.discover(plugin());
        Supplier<PaperCrossPlatformPunishmentService> crossPlatform =
                crossPlatformPunishments(crossPlatformConfiguration, activeAuthorization, targetStaff);
        PunishmentGuiController punishmentGui = new PunishmentGuiController(
                new PunishmentGuiController.Dependencies(
                        plugin(),
                        clock(),
                        writeMode(),
                        drafts,
                        players,
                        activeAuthorization,
                        reasons(),
                        histories,
                        cases,
                        sanctions,
                        reports,
                        moderationSettings::current,
                        crossPlatform,
                        workers()
                )
        );
        plugin().getServer().getPluginManager().registerEvents(punishmentGui, plugin());
        PunishmentRequestGuiController requestGui = new PunishmentRequestGuiController(
                plugin(), requests, players, activeAuthorization, workers()
        );
        requestGui.register();
        PunishmentRequestCommandHandler requestHandler = new PunishmentRequestCommandHandler(
                plugin(), requests, activeAuthorization, requestGui, workers()
        );
        PunishmentCommand command = new PunishmentCommand(
                plugin(), writeMode(), drafts, players, activeAuthorization, punishmentGui, requestHandler, workers()
        );
        PUNISHMENT_COMMANDS.forEach(name -> bindCompleting(name, command, command));
    }

    private void registerStaffApiCommands() {
        Supplier<PunishmentService> punishments = storage(PaperStorageBindings::punishmentService);
        Supplier<PlayerDirectory> players = storage(PaperStorageBindings::playerDirectory);
        StaffApiCommand command = new StaffApiCommand(
                punishments,
                players,
                authoritativeMode(),
                clock()
        );
        bindCompleting("staffapi", command, command);
    }

    private void registerSanctionChangeCommands() {
        Supplier<SanctionChangeService> changes = storage(PaperStorageBindings::sanctionChangeService);
        Supplier<PlayerDirectory> players = storage(PaperStorageBindings::playerDirectory);
        Supplier<CaseLookup> cases = storage(PaperStorageBindings::caseLookup);
        AuthorizationPolicy activeAuthorization = activeAuthorization();
        SanctionChangeGuiController changeGui = new SanctionChangeGuiController(
                plugin(), clock(), writeMode(), changes, players, cases,
                storage(PaperStorageBindings::caseReviewStore), activeAuthorization, workers()
        );
        plugin().getServer().getPluginManager().registerEvents(changeGui, plugin());
        SanctionChangeCommand command = new SanctionChangeCommand(
                plugin(), writeMode(), changes, players, cases, activeAuthorization, workers(), changeGui
        );
        SANCTION_CHANGE_COMMANDS.forEach(name -> bindCompleting(name, command, command));
    }

    private void registerReportCommands() {
        Supplier<ReportStore> reportStore = storage(PaperStorageBindings::reportStore);
        Supplier<ReportStore> activeReportStore = () -> {
            ReportStore loaded = reportStore.get();
            return loaded == null ? null : new net.enthusia.staff.paper.report.ActiveDutyReportStore(
                    loaded,
                    dependencies.players().staffMode()::authorityActive
            );
        };
        ReportCommand report = new ReportCommand(
                new ReportCommand.Dependencies(
                        plugin(), clock(), dependencies.environment().serverId(), authoritativeMode(),
                        storage(PaperStorageBindings::playerDirectory),
                        reportStore,
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
        ReportGuiController reportGui = new ReportGuiController(
                plugin(),
                clock(),
                activeReportStore,
                dependencies.environment().reportConfiguration(),
                workers()
        );
        plugin().getServer().getPluginManager().registerEvents(reportGui, plugin());
        ReportsCommand reports = new ReportsCommand(plugin(), clock(), activeReportStore, workers(), reportGui);
        bindCompleting("reports", reports, reports);
    }

    private void registerStaffCommands() {
        FreezeCommand freezes = FreezeCommand.createRuntime(
                plugin(), clock(), writeMode(), storage(PaperStorageBindings::playerDirectory),
                storage(PaperStorageBindings::freezeStore), dependencies.players().freeze(), workers(),
                dependencies.players().freezeNotices()
        );
        bindCompleting("freeze", freezes, freezes);
        bindCompleting("unfreeze", freezes, freezes);
        StaffModeVanishEntryCoordinator staffEntry = new StaffModeVanishEntryCoordinator(
                plugin(),
                writeMode(),
                dependencies.players().staffMode(),
                dependencies.players().vanish(),
                storage(PaperStorageBindings::vanishStore),
                workers()
        );
        StaffModeCommand staffMode = new StaffModeCommand(
                writeMode(),
                dependencies.players().staffMode(),
                staffEntry
        );
        bindCompleting("staff", staffMode, staffMode);
        bind("vanish", new VanishCommand(writeMode(), dependencies.players().vanish()));
        bind("staffchat", new StaffChatCommand(dependencies.integrations().roseChat()));
        bind("staffwho", new StaffWhoCommand(
                plugin(),
                storage(PaperStorageBindings::punishmentRequestService),
                dependencies.players().staffMode(),
                dependencies.players().vanish(),
                workers()
        ));
    }

    private void registerInventoryCommands() {
        Supplier<PlayerDirectory> players = storage(PaperStorageBindings::playerDirectory);
        InventoryCommand command = new InventoryCommand(
                plugin(), clock(), players, dependencies.players().inventory(), workers()
        );
        INVENTORY_COMMANDS.forEach(name -> bindCompleting(name, command, command));
    }

    private void registerInspectionCommands() {
        var activity = new net.enthusia.staff.paper.staff.PlayerActivityListener(clock());
        plugin().getServer().getPluginManager().registerEvents(activity, plugin());
        plugin().getServer().getServicesManager().register(
                net.enthusia.staff.paper.staff.PlayerActivityListener.class, activity, plugin(),
                org.bukkit.plugin.ServicePriority.Normal);
        Supplier<PlayerDirectory> players = storage(PaperStorageBindings::playerDirectory);
        Supplier<CaseLookup> cases = storage(PaperStorageBindings::caseLookup);
        Supplier<FreezeStore> freezes = storage(PaperStorageBindings::freezeStore);
        Supplier<ReportStore> reports = storage(PaperStorageBindings::reportStore);
        Supplier<ModerationHistoryStore> histories = storage(PaperStorageBindings::moderationHistoryStore);
        AuthorizationPolicy activeAuthorization = activeAuthorization();
        InspectCommand inspect = new InspectCommand(
                plugin(), clock(), players, cases, freezes, reports,
                dependencies.integrations().economy(), dependencies.integrations().confiscation(),
                dependencies.players().inventory(), activeAuthorization, dependencies.integrations().market(),
                dependencies.integrations().reputation(), workers()
        );
        bindCompleting("inspect", inspect, inspect);
        Supplier<net.enthusia.staff.domain.ports.InvestigationFlagStore> flags = storage(
                bindings -> new net.enthusia.staff.persistence.JdbcInvestigationFlagStore(bindings.runtime().dataSource()));
        var flagCommand = new net.enthusia.staff.paper.command.InvestigationCommand(
                plugin(), clock(), flags, players, cases, writeMode(), workers(), storage(
                        bindings -> new net.enthusia.staff.persistence.JdbcStaffNoteStore(bindings.runtime().dataSource())));
        bind("staffflags", flagCommand);
        plugin().getServer().getServicesManager().register(
                net.enthusia.staff.paper.command.InvestigationCommand.class, flagCommand, plugin(),
                org.bukkit.plugin.ServicePriority.Normal);
        var joinAlerts = new net.enthusia.staff.paper.staff.InvestigationJoinListener(
                plugin(), clock(), workers(), flags, storage(
                        bindings -> new net.enthusia.staff.persistence.JdbcStaffNoteStore(bindings.runtime().dataSource())));
        plugin().getServer().getPluginManager().registerEvents(joinAlerts, plugin());
        plugin().getServer().getServicesManager().register(
                net.enthusia.staff.paper.staff.InvestigationJoinListener.class, joinAlerts, plugin(),
                org.bukkit.plugin.ServicePriority.Normal);

        HistoryCommand history = new HistoryCommand(
                plugin(), players, histories, moderationSettings::current, workers()
        );
        bindCompleting("history", history, history);
        CaseCommand caseCommand = new CaseCommand(
                plugin(), cases, dependencies.integrations().confiscation(), histories,
                moderationSettings::current, activeAuthorization, workers()
        );
        InventoryRecoveryCoordinator recovery = new InventoryRecoveryCoordinator(
                clock(), storage(PaperStorageBindings::inventoryRecoveryStore), activeAuthorization
        );
        bind("case", new CaseRecoveryCommand(plugin(), caseCommand, recovery, workers()));
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

    private Supplier<PaperCrossPlatformPunishmentService> crossPlatformPunishments(
            Optional<PaperCrossPlatformConfiguration> configuration,
            AuthorizationPolicy activeAuthorization,
            java.util.function.Function<java.util.UUID, Optional<net.enthusia.staff.domain.auth.Actor>> targetStaff
    ) {
        return () -> {
            PaperCrossPlatformConfiguration active = configuration.orElse(null);
            PaperStorageBindings bindings = dependencies.storage().get().orElse(null);
            if (active == null || bindings == null) {
                return null;
            }
            return new PaperCrossPlatformPunishmentService(
                    clock(),
                    writeMode(),
                    bindings.punishmentService(),
                    reasons(),
                    bindings.runtime().discordModerationPersistenceStore(),
                    bindings.runtime().crossPlatformPunishmentStore(),
                    bindings.runtime().crossPlatformIdentityLookup(),
                    bindings.runtime().discordPunishmentRepository(),
                    bindings.runtime().crossPlatformPunishmentStatus(),
                    active,
                    activeAuthorization,
                    targetStaff
            );
        };
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

    private AuthorizationPolicy activeAuthorization() {
        return new ActiveDutyAuthorizationPolicy(
                authorization(),
                dependencies.players().staffMode()::authorityActive
        );
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
            Supplier<ModerationFeatureSettings> moderationFeatures,
            Supplier<ReportConfigurationSnapshot> reportConfiguration
    ) {
        Environment(
                JavaPlugin plugin,
                Clock clock,
                String serverId,
                ExecutorService workers,
                Supplier<ModerationFeatureSettings> moderationFeatures
        ) {
            this(
                    plugin,
                    clock,
                    serverId,
                    workers,
                    moderationFeatures,
                    ReportConfigurationRuntime::snapshot
            );
        }
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
            FreezeNoticeSink freezeNotices,
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
