package net.enthusia.staff.paper.punishment.policyv2;

import java.nio.file.Path;
import java.time.Clock;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Level;
import net.enthusia.staff.domain.auth.Actor;
import net.enthusia.staff.domain.auth.AuthorizationPolicy;
import net.enthusia.staff.domain.player.PlayerIdentity;
import net.enthusia.staff.domain.ports.PlayerDirectory;
import net.enthusia.staff.domain.policyv2.enforcement.PolicyV2EnforcementStore;
import net.enthusia.staff.domain.policyv2.legacy.PolicyV1BehavioralHistorySource;
import net.enthusia.staff.domain.policyv2.persistence.PolicyV2Store;
import net.enthusia.staff.paper.auth.PaperActorResolver;
import net.enthusia.staff.paper.command.PolicyV2ShadowAccess;
import net.enthusia.staff.paper.config.policyv2.PolicyV2PublicationService;
import net.enthusia.staff.paper.presentation.StaffMessageStyle;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class PolicyV2ShadowRuntime implements PolicyV2ShadowAccess {
    private final JavaPlugin plugin;
    private final PolicyV2PublicationService publications;
    private final Supplier<PlayerDirectory> players;
    private final ExecutorService workers;
    private final PolicyV2ManualWorkflow workflow;
    private final PolicyV2GuiController gui;

    public PolicyV2ShadowRuntime(
            JavaPlugin plugin,
            Clock clock,
            Path configurationFile,
            StoreSuppliers stores,
            RuntimeServices services
    ) {
        this.plugin = java.util.Objects.requireNonNull(plugin, "plugin");
        StoreSuppliers storeSuppliers = java.util.Objects.requireNonNull(stores, "stores");
        RuntimeServices runtimeServices = java.util.Objects.requireNonNull(services, "services");
        this.players = storeSuppliers.players();
        this.workers = runtimeServices.workers();
        this.publications = new PolicyV2PublicationService(
                configurationFile,
                plugin.getLogger(),
                runtimeServices.issueSink()
        );
        publications.loadInitial();
        PolicyV2ShadowEnforcementRuntime enforcementRuntime = new PolicyV2ShadowEnforcementRuntime(
                publications::shadowEnabled,
                storeSuppliers.policyStores(),
                storeSuppliers.enforcementStores(),
                runtimeServices.authorization(),
                clock
        );
        plugin.getServer().getPluginManager().registerEvents(
                new PolicyV2ShadowComplianceListener(plugin, workers, enforcementRuntime),
                plugin
        );
        PolicyV2StoreAdapter adapter = new PolicyV2StoreAdapter(
                storeSuppliers.policyStores(),
                storeSuppliers.legacyHistorySources()
        );
        this.workflow = new PolicyV2ManualWorkflow(
                publications::activeSnapshot,
                adapter,
                adapter,
                runtimeServices.authorization(),
                clock
        );
        this.gui = new PolicyV2GuiController(
                plugin,
                clock,
                workflow,
                workers,
                publications::shadowEnabled
        );
        gui.register();
    }

    public record StoreSuppliers(
            Supplier<PolicyV2Store> policyStores,
            Supplier<PolicyV2EnforcementStore> enforcementStores,
            Supplier<PlayerDirectory> players,
            Supplier<PolicyV1BehavioralHistorySource> legacyHistorySources
    ) {
        public StoreSuppliers(
                Supplier<PolicyV2Store> policyStores,
                Supplier<PolicyV2EnforcementStore> enforcementStores,
                Supplier<PlayerDirectory> players
        ) {
            this(
                    policyStores,
                    enforcementStores,
                    players,
                    () -> PolicyV1BehavioralHistorySource.empty()
            );
        }

        public StoreSuppliers {
            java.util.Objects.requireNonNull(policyStores, "policyStores");
            java.util.Objects.requireNonNull(enforcementStores, "enforcementStores");
            java.util.Objects.requireNonNull(players, "players");
            java.util.Objects.requireNonNull(legacyHistorySources, "legacyHistorySources");
        }
    }

    public record RuntimeServices(
            AuthorizationPolicy authorization,
            ExecutorService workers,
            Consumer<Optional<String>> issueSink
    ) {
        public RuntimeServices {
            java.util.Objects.requireNonNull(authorization, "authorization");
            java.util.Objects.requireNonNull(workers, "workers");
            java.util.Objects.requireNonNull(issueSink, "issueSink");
        }
    }

    @Override
    public boolean enabled() {
        return publications.shadowEnabled();
    }

    @Override
    public void reload() {
        publications.reload();
    }

    @Override
    public void open(CommandSender sender, String targetQuery) {
        if (!enabled()) {
            return;
        }
        if (!(sender instanceof Player viewer)) {
            sender.sendMessage(StaffMessageStyle.style(Component.text(
                    "Policy v2 shadow review requires an in-game staff player."
            )));
            return;
        }
        Actor actor = PaperActorResolver.resolve(viewer).orElse(null);
        if (actor == null || !workflow.mayStart(actor)) {
            viewer.sendMessage(StaffMessageStyle.style(Component.text(
                    "You do not have Policy v2 shadow-review authority."
            )));
            return;
        }
        if (targetQuery == null || targetQuery.isBlank()) {
            viewer.sendMessage(StaffMessageStyle.usage("Usage: /estaff policyv2 <player>"));
            return;
        }
        submit(viewer, targetQuery.trim());
    }

    private void submit(Player viewer, String targetQuery) {
        try {
            workers.execute(() -> resolveAndOpen(viewer, targetQuery));
        } catch (RejectedExecutionException exception) {
            message(viewer, "The moderation work queue is full; Policy v2 was not opened.");
        }
    }

    private void resolveAndOpen(Player viewer, String targetQuery) {
        try {
            if (!enabled()) {
                message(viewer, "Policy v2 shadow mode was disabled before the review opened.");
                return;
            }
            PlayerDirectory directory = players.get();
            if (directory == null) {
                message(viewer, "Moderation storage is not ready; Policy v2 was not opened.");
                return;
            }
            PlayerIdentity target = directory.find(targetQuery).orElse(null);
            if (target == null) {
                message(viewer, "No known player matched " + targetQuery + '.');
                return;
            }
            onEntity(viewer, () -> gui.open(viewer, target));
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.SEVERE, "Policy v2 target resolution failed", exception);
            message(viewer, "Policy v2 could not resolve that player; no live punishment was applied.");
        }
    }

    private void message(Player viewer, String value) {
        onEntity(viewer, () -> viewer.sendMessage(StaffMessageStyle.style(Component.text(value))));
    }

    private void onEntity(Player viewer, Runnable task) {
        viewer.getScheduler().execute(plugin, task, null, 1L);
    }
}
