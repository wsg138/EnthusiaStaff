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
            Supplier<PolicyV2Store> stores,
            Supplier<PlayerDirectory> players,
            AuthorizationPolicy authorization,
            ExecutorService workers,
            Consumer<Optional<String>> issueSink
    ) {
        this.plugin = java.util.Objects.requireNonNull(plugin, "plugin");
        this.players = java.util.Objects.requireNonNull(players, "players");
        this.workers = java.util.Objects.requireNonNull(workers, "workers");
        this.publications = new PolicyV2PublicationService(
                configurationFile,
                plugin.getLogger(),
                issueSink
        );
        publications.loadInitial();
        PolicyV2StoreAdapter adapter = new PolicyV2StoreAdapter(stores);
        this.workflow = new PolicyV2ManualWorkflow(
                publications::activeSnapshot,
                adapter,
                adapter,
                authorization,
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
