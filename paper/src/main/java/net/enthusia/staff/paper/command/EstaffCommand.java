package net.enthusia.staff.paper.command;

import java.util.Map;
import net.enthusia.staff.paper.RuntimeHealth;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

public final class EstaffCommand implements CommandExecutor {
    private final RuntimeHealth health;

    public EstaffCommand(RuntimeHealth health) {
        this.health = health;
    }

    @Override
    public boolean onCommand(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String label,
            @NotNull String[] args
    ) {
        String operation = args.length == 0 ? "status" : args[0].toLowerCase(java.util.Locale.ROOT);
        if (!operation.equals("status") && !operation.equals("verify")) {
            sender.sendMessage("EnthusiaStaff reload is unavailable until the modular configuration validator is active.");
            return true;
        }
        RuntimeHealth.Snapshot snapshot = health.snapshot();
        sender.sendMessage("EnthusiaStaff mode: " + snapshot.mode());
        if (snapshot.issues().isEmpty()) {
            sender.sendMessage("PASS: no active runtime health issues");
        } else {
            for (Map.Entry<String, String> issue : snapshot.issues().entrySet()) {
                sender.sendMessage("DISABLED " + issue.getKey() + ": " + issue.getValue());
            }
        }
        return true;
    }
}
