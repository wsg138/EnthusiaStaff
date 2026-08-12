package net.enthusia.staff.paper.command;

import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.logging.Level;
import net.enthusia.staff.common.CaseId;
import net.enthusia.staff.domain.auth.Actor;
import net.enthusia.staff.domain.inventory.InventoryRecoveryResult;
import net.enthusia.staff.paper.auth.PaperActorResolver;
import net.enthusia.staff.paper.inventory.InventoryRecoveryCoordinator;
import net.kyori.adventure.text.Component;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

public final class CaseRecoveryCommand implements CommandExecutor {
    private static final String RECOVERY_PERMISSION = "enthusiastaff.owner.recovery";

    private final JavaPlugin plugin;
    private final CaseCommand delegate;
    private final InventoryRecoveryCoordinator recovery;
    private final ExecutorService workers;
    private final CommandResponseDispatcher responses;

    public CaseRecoveryCommand(
            JavaPlugin plugin,
            CaseCommand delegate,
            InventoryRecoveryCoordinator recovery,
            ExecutorService workers
    ) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.recovery = Objects.requireNonNull(recovery, "recovery");
        this.workers = Objects.requireNonNull(workers, "workers");
        this.responses = new CommandResponseDispatcher(plugin);
    }

    @Override
    public boolean onCommand(
            CommandSender sender,
            Command command,
            String label,
            String[] arguments
    ) {
        if (arguments.length == 0 || !arguments[0].equalsIgnoreCase("recoveritems")) {
            return delegate.onCommand(sender, command, label, arguments);
        }
        return recover(sender, label, arguments);
    }

    private boolean recover(CommandSender sender, String label, String[] arguments) {
        if (!CommandPermissionGate.require(
                sender,
                RECOVERY_PERMISSION,
                "You do not have permission to authorize quarantined item recovery."
        )) {
            return true;
        }
        if (arguments.length != 2) {
            sender.sendMessage(Component.text("Usage: /" + label + " recoveritems <case-id>"));
            return true;
        }
        Actor actor = PaperActorResolver.resolve(sender).orElse(null);
        CaseId caseId;
        try {
            caseId = new CaseId(arguments[1]);
        } catch (IllegalArgumentException exception) {
            sender.sendMessage(Component.text("Invalid case ID: " + sanitized(exception.getMessage())));
            return true;
        }
        try {
            workers.execute(() -> executeRecovery(sender, actor, caseId));
        } catch (RejectedExecutionException exception) {
            sender.sendMessage(Component.text("The moderation work queue is full; nothing changed."));
        }
        return true;
    }

    private void executeRecovery(CommandSender sender, Actor actor, CaseId caseId) {
        try {
            InventoryRecoveryResult result = recovery.recover(actor, caseId);
            responses.send(sender, Component.text(message(result)));
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.SEVERE, "Case-linked item recovery authorization failed", exception);
            responses.send(sender, Component.text("Item recovery authorization failed; nothing changed."));
        }
    }

    private static String message(InventoryRecoveryResult result) {
        return switch (result.status()) {
            case REQUEUED -> "Item recovery retry authorized for operation "
                    + result.operationId().orElseThrow()
                    + ". No inventory was changed by this command; normal checksum verification must apply it.";
            case REPLAYED -> "Item recovery retry was already authorized for operation "
                    + result.operationId().orElseThrow() + '.';
            case NOT_FOUND -> "No unresolved quarantined item operation matches that case.";
            case AMBIGUOUS -> result.detail();
            case UNAUTHORIZED -> "Only the Founder may authorize quarantined item recovery.";
            case STORAGE_UNAVAILABLE -> "Inventory recovery storage is unavailable; nothing changed.";
        };
    }

    private static String sanitized(String message) {
        return message == null || message.isBlank()
                ? "invalid value"
                : message.lines().findFirst().orElse("invalid value");
    }
}
