package net.enthusia.staff.paper.command;

import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Supplier;
import java.util.logging.Level;
import net.enthusia.staff.common.CaseId;
import net.enthusia.staff.domain.ports.CaseLookup;
import net.enthusia.staff.paper.inventory.ConfiscationCoordinator;
import net.kyori.adventure.text.Component;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class CaseCommand implements CommandExecutor {
    private final JavaPlugin plugin;
    private final Supplier<CaseLookup> cases;
    private final Supplier<ConfiscationCoordinator> confiscation;
    private final ExecutorService workers;

    public CaseCommand(
            JavaPlugin plugin,
            Supplier<CaseLookup> cases,
            Supplier<ConfiscationCoordinator> confiscation,
            ExecutorService workers
    ) {
        this.plugin = java.util.Objects.requireNonNull(plugin, "plugin");
        this.cases = java.util.Objects.requireNonNull(cases, "cases");
        this.confiscation = java.util.Objects.requireNonNull(confiscation, "confiscation");
        this.workers = java.util.Objects.requireNonNull(workers, "workers");
    }

    @Override
    public boolean onCommand(
            CommandSender sender,
            Command command,
            String label,
            String[] arguments
    ) {
        if (!(sender instanceof Player viewer)) {
            sender.sendMessage("Confiscated-item restoration requires an in-game staff actor.");
            return true;
        }
        if (arguments.length != 2 || !arguments[0].equalsIgnoreCase("restoreitems")) {
            viewer.sendMessage(Component.text("Usage: /" + label + " restoreitems <case-id>"));
            return true;
        }
        CaseId caseId;
        try {
            caseId = new CaseId(arguments[1]);
        } catch (IllegalArgumentException exception) {
            viewer.sendMessage(Component.text("Invalid case ID: " + exception.getMessage()));
            return true;
        }
        try {
            workers.execute(() -> resolveAndRestore(viewer, caseId));
        } catch (RejectedExecutionException exception) {
            viewer.sendMessage(Component.text("The moderation work queue is full; nothing changed."));
        }
        return true;
    }

    private void resolveAndRestore(Player viewer, CaseId caseId) {
        CaseLookup loadedCases = cases.get();
        ConfiscationCoordinator coordinator = confiscation.get();
        if (loadedCases == null) {
            message(viewer, "Case storage is not ready; nothing changed.");
            return;
        }
        if (coordinator == null) {
            message(viewer, "Confiscated-item restoration integration is unavailable.");
            return;
        }
        try {
            UUID targetId = loadedCases.target(caseId).orElse(null);
            if (targetId == null) {
                message(viewer, "That case does not exist.");
                return;
            }
            viewer.getScheduler().execute(plugin, () -> {
                Player target = plugin.getServer().getPlayer(targetId);
                if (target == null) {
                    viewer.sendMessage(Component.text(
                            "Confiscated-item restoration requires the case target on this backend."
                    ));
                    return;
                }
                coordinator.restore(viewer, target, caseId);
            }, null, 1L);
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.SEVERE, "Case restoration lookup failed", exception);
            message(viewer, "Case restoration lookup failed; nothing changed.");
        }
    }

    private void message(Player viewer, String body) {
        viewer.getScheduler().execute(
                plugin,
                () -> viewer.sendMessage(Component.text(body)),
                null,
                1L
        );
    }
}
