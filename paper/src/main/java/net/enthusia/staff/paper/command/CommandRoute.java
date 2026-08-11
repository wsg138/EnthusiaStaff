package net.enthusia.staff.paper.command;

import java.util.Locale;
import java.util.Objects;
import org.bukkit.command.Command;

final class CommandRoute {
    private CommandRoute() {
    }

    static String canonicalName(Command command) {
        return Objects.requireNonNull(command, "command").getName().toLowerCase(Locale.ROOT);
    }
}
