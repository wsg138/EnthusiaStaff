package net.enthusia.staff.discordbot;

import static org.junit.jupiter.api.Assertions.assertEquals;

import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import org.junit.jupiter.api.Test;

class JdaModerationUiPreviewListenerTest {
    @Test
    void previewCommandIsDisabledSlashCommandForStagingDiscovery() {
        var command = JdaModerationUiPreviewListener.command();

        assertEquals("moderate-preview", command.getName());
        assertEquals(Command.Type.SLASH, command.getType());
        assertEquals(DefaultMemberPermissions.DISABLED, command.getDefaultPermissions());
    }
}
