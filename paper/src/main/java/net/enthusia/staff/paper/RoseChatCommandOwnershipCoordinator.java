package net.enthusia.staff.paper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import org.bukkit.command.Command;
import org.bukkit.command.CommandMap;
import org.bukkit.plugin.java.JavaPlugin;

final class RoseChatCommandOwnershipCoordinator {
    private static final List<String> CONTROLLED_LABELS = List.of("mute", "staff");
    private static final String ROSECHAT_CUSTOM_CHANNEL_COMMAND =
            "dev.rosewood.rosechat.command.command.CustomChannelCommand";

    private final CommandRegistry registry;
    private final Function<String, Command> staffCommands;
    private final Predicate<Command> roseChatChannelCommand;
    private final Consumer<String> logger;

    RoseChatCommandOwnershipCoordinator(
            CommandRegistry registry,
            Function<String, Command> staffCommands,
            Predicate<Command> roseChatChannelCommand,
            Consumer<String> logger
    ) {
        this.registry = Objects.requireNonNull(registry, "registry");
        this.staffCommands = Objects.requireNonNull(staffCommands, "staffCommands");
        this.roseChatChannelCommand = Objects.requireNonNull(roseChatChannelCommand, "roseChatChannelCommand");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    static RoseChatCommandOwnershipCoordinator forPlugin(JavaPlugin plugin) {
        Objects.requireNonNull(plugin, "plugin");
        return new RoseChatCommandOwnershipCoordinator(
                new PaperCommandRegistry(plugin.getServer().getCommandMap()),
                plugin::getCommand,
                RoseChatCommandOwnershipCoordinator::isRoseChatCustomChannelCommand,
                plugin.getLogger()::warning
        );
    }

    List<String> reconcile() {
        List<String> conflicts = new ArrayList<>();
        for (String label : CONTROLLED_LABELS) {
            if (!reconcile(label)) {
                conflicts.add(label);
            }
        }
        return List.copyOf(conflicts);
    }

    private boolean reconcile(String label) {
        Command staffCommand = Objects.requireNonNull(
                staffCommands.apply(label),
                label + " command is missing from EnthusiaStaff plugin metadata"
        );
        Command active = registry.command(label);
        if (active == staffCommand) {
            return true;
        }
        if (active != null && !isRoseChatOwned(label, active)) {
            logger.accept("Command /" + label + " is owned by " + active.getClass().getName()
                    + "; EnthusiaStaff will not replace an unrelated command owner");
            return false;
        }
        if (!registry.claim(label, active, staffCommand)) {
            logger.accept("Command /" + label + " changed ownership while RoseChat reconciliation was running");
            return false;
        }
        logger.accept("Restored EnthusiaStaff ownership of /" + label + " after RoseChat command registration");
        return true;
    }

    private boolean isRoseChatOwned(String label, Command active) {
        Command namespaced = registry.command("rosechat:" + label);
        return namespaced == active || ("staff".equals(label) && roseChatChannelCommand.test(active));
    }

    static boolean isRoseChatCustomChannelCommand(Command command) {
        return command != null && ROSECHAT_CUSTOM_CHANNEL_COMMAND.equals(command.getClass().getName());
    }

    interface CommandRegistry {
        Command command(String label);

        boolean claim(String label, Command expected, Command replacement);
    }

    private record PaperCommandRegistry(CommandMap commandMap) implements CommandRegistry {
        private PaperCommandRegistry {
            Objects.requireNonNull(commandMap, "commandMap");
        }

        @Override
        public Command command(String label) {
            return commandMap.getCommand(label);
        }

        @Override
        public boolean claim(String label, Command expected, Command replacement) {
            Map<String, Command> commands = commandMap.getKnownCommands();
            if (expected == null) {
                return commands.putIfAbsent(label, replacement) == null;
            }
            return commands.replace(label, expected, replacement);
        }
    }
}
