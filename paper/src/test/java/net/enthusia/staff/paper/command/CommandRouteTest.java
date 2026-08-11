package net.enthusia.staff.paper.command;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.Test;

final class CommandRouteTest {
    @Test
    void canonicalNameUsesRegisteredIdentityAndNormalizesCase() {
        Command command = command("UnFreeze");

        assertEquals("unfreeze", CommandRoute.canonicalName(command));
    }

    private static Command command(String name) {
        return new Command(name) {
            @Override
            public boolean execute(CommandSender sender, String label, String[] arguments) {
                return true;
            }

            @Override
            public List<String> tabComplete(CommandSender sender, String alias, String[] arguments) {
                return List.of();
            }
        };
    }
}
