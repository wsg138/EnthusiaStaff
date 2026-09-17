package net.enthusia.staff.discordbot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import net.dv8tion.jda.api.entities.ApplicationInfo;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import org.junit.jupiter.api.Test;

class JdaModerationUiPreviewListenerTest {
    @Test
    void previewRegistersOptionalTargetSlashAndContextCommandsDisabledByDefault() {
        Map<String, CommandData> commands = JdaModerationUiPreviewListener.commands().stream()
                .collect(Collectors.toMap(CommandData::getName, Function.identity()));
        SlashCommandData slash = (SlashCommandData) commands.get("moderate-preview");

        assertEquals(3, commands.size());
        assertEquals(Command.Type.SLASH, slash.getType());
        assertEquals(1, slash.getOptions().size());
        assertFalse(slash.getOptions().getFirst().isRequired());
        assertEquals(Command.Type.USER, commands.get("Moderate Preview").getType());
        assertEquals(Command.Type.MESSAGE, commands.get("Moderate Message Preview").getType());
        assertTrue(commands.values().stream().allMatch(command ->
                DefaultMemberPermissions.DISABLED.equals(command.getDefaultPermissions())));
    }

    @Test
    void realMessagePreviewRequiresApplicationMessageContentEntitlement() {
        assertTrue(JdaModerationUiPreviewListener.hasMessageContentEntitlement(
                Set.of(ApplicationInfo.Flag.GATEWAY_MESSAGE_CONTENT)));
        assertTrue(JdaModerationUiPreviewListener.hasMessageContentEntitlement(
                Set.of(ApplicationInfo.Flag.GATEWAY_MESSAGE_CONTENT_LIMITED)));
        assertFalse(JdaModerationUiPreviewListener.hasMessageContentEntitlement(Set.of()));
        assertFalse(JdaModerationUiPreviewListener.hasMessageContentEntitlement(null));
    }
}
