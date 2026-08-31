package net.enthusia.staff.discordbot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import org.junit.jupiter.api.Test;

class JdaModerationUiPreviewListenerTest {
    @Test
    void previewRegistersTargetedSlashAndContextCommandsDisabledByDefault() {
        Map<String, CommandData> commands = JdaModerationUiPreviewListener.commands().stream()
                .collect(Collectors.toMap(CommandData::getName, Function.identity()));

        assertEquals(3, commands.size());
        assertEquals(Command.Type.SLASH, commands.get("moderate-preview").getType());
        assertEquals(Command.Type.USER, commands.get("Moderate Preview").getType());
        assertEquals(Command.Type.MESSAGE, commands.get("Moderate Message Preview").getType());
        assertTrue(commands.values().stream().allMatch(command ->
                DefaultMemberPermissions.DISABLED.equals(command.getDefaultPermissions())));
    }
}
