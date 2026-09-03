package net.enthusia.staff.discordbot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import java.util.stream.Collectors;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import org.junit.jupiter.api.Test;

class JdaStaffModerationListenerTest {
    @Test
    void staffReadCommandsAreCompleteAndDefaultDisabledForDiscovery() {
        var commands = JdaStaffModerationListener.commands();

        assertEquals(8, commands.size());
        assertEquals(Set.of(
                "moderate",
                "Moderate User",
                "Moderate Message",
                "moderate-minecraft",
                "linked",
                "history",
                "notes",
                "case"
        ), commands.stream().map(CommandData::getName).collect(Collectors.toSet()));
        assertTrue(commands.stream().allMatch(command ->
                DefaultMemberPermissions.DISABLED.equals(command.getDefaultPermissions())));

        assertEquals(Command.Type.USER, command(commands, "Moderate User").getType());
        assertEquals(Command.Type.MESSAGE, command(commands, "Moderate Message").getType());
        assertEquals(Command.Type.SLASH, command(commands, "moderate").getType());
    }

    private static CommandData command(java.util.List<CommandData> commands, String name) {
        return commands.stream().filter(command -> command.getName().equals(name)).findFirst().orElseThrow();
    }
}
