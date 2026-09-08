package net.enthusia.staff.paper.tester;

import java.util.List;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/** Direct command entry point for the existing bounded fake-base controls. */
public final class FakeBaseCommand implements CommandExecutor, TabCompleter {
    private static final String ROUTE_PREFIX = "base";
    private static final String COMMAND_PREFIX = "/fakebase";

    private final FakeBaseCommandRouter router;

    public FakeBaseCommand(JavaPlugin plugin, FakeBaseManager manager) {
        this.router = new FakeBaseCommandRouter(plugin, manager, COMMAND_PREFIX);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] arguments) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Fake-base controls require an in-game staff session.");
            return true;
        }
        return router.handle(player, routedArguments(arguments));
    }

    @Override
    public List<String> onTabComplete(
            CommandSender sender,
            Command command,
            String alias,
            String[] arguments
    ) {
        if (!(sender instanceof Player player)) {
            return List.of();
        }
        return router.tabComplete(player, routedArguments(arguments));
    }

    static String[] routedArguments(String[] arguments) {
        String[] routed = new String[arguments.length + 1];
        routed[0] = ROUTE_PREFIX;
        System.arraycopy(arguments, 0, routed, 1, arguments.length);
        return routed;
    }
}
