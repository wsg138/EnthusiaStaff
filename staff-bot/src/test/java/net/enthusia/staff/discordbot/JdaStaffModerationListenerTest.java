package net.enthusia.staff.discordbot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import java.util.stream.Collectors;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import org.junit.jupiter.api.Test;

class JdaStaffModerationListenerTest {
    private static final String USER_ID_OPTION = "user-id";
    @Test
    void staffReadCommandsAreCompleteAndDefaultDisabledForDiscovery() {
        var commands = JdaStaffModerationListener.commands();

        assertEquals(11, commands.size());
        assertEquals(Set.of(
                "moderate",
                "punish",
                "Moderate User",
                "Moderate Message",
                "moderate-minecraft",
                "linked",
                "history",
                "notes",
                "case",
                "review-request",
                "review-queue"
        ), names(commands));
        assertTrue(commands.stream().allMatch(command ->
                DefaultMemberPermissions.DISABLED.equals(command.getDefaultPermissions())));

        assertEquals(Command.Type.USER, command(commands, "Moderate User").getType());
        assertEquals(Command.Type.MESSAGE, command(commands, "Moderate Message").getType());
        assertEquals(Command.Type.SLASH, command(commands, "moderate").getType());
        assertEquals(Command.Type.SLASH, command(commands, "punish").getType());
    }

    @Test
    void productionWebKeepsStaffCommandsAndAllowsChannelLaunch() {
        var commands = JdaStaffModerationListener.commands(false, true);
        assertEquals(11, commands.size());
        assertTrue(commands.stream().allMatch(command ->
                DefaultMemberPermissions.DISABLED.equals(command.getDefaultPermissions())));
        for (String name : java.util.List.of("moderate", "punish")) {
            SlashCommandData launch = (SlashCommandData) command(commands, name);
            assertEquals(1, launch.getOptions().size());
            assertEquals(OptionType.USER, launch.getOptions().getFirst().getType());
            assertTrue(!launch.getOptions().getFirst().isRequired());
        }
    }

    @Test
    void enforcementRuntimeAddsApprovedQuickCommandsAndSelfPreview() {
        var commands = JdaStaffModerationListener.commands(true);

        assertEquals(20, commands.size());
        assertTrue(names(commands).containsAll(Set.of(
                "warn", "mute", "unmute", "kick", "ban", "unban", "restrict", "unrestrict",
                "notification-test"
        )));
        assertTrue(commands.stream().allMatch(command ->
                DefaultMemberPermissions.DISABLED.equals(command.getDefaultPermissions())));
    }

    @Test
    void reviewQueueUsesDefaultDisabledPermissionAndNoArguments() {
        SlashCommandData queue = (SlashCommandData) command(
                JdaStaffModerationListener.commands(), "review-queue");
        assertEquals(0, queue.getOptions().size());
        assertEquals(DefaultMemberPermissions.DISABLED, queue.getDefaultPermissions());
    }

    @Test
    void minecraftReviewCommandRequiresExactIdAndExplicitApproveOrDenyChoice() {
        SlashCommandData command = (SlashCommandData) command(
                JdaStaffModerationListener.commands(), "review-request"
        );
        assertEquals(java.util.List.of("request-id", "decision", "note"),
                command.getOptions().stream().map(option -> option.getName()).toList());
        assertTrue(command.getOptions().getFirst().isRequired());
        assertTrue(command.getOptions().get(1).isRequired());
        assertTrue(!command.getOptions().get(2).isRequired());
        assertEquals(Set.of("approve", "deny"),
                command.getOptions().get(1).getChoices().stream()
                        .map(choice -> choice.getAsString()).collect(Collectors.toSet()));
    }

    @Test
    void notificationTestIsSelfOnlyAndUsesFixedPreviewChoices() {
        SlashCommandData preview = (SlashCommandData) command(
                JdaStaffModerationListener.commands(true), "notification-test"
        );

        assertEquals(1, preview.getOptions().size());
        assertEquals("type", preview.getOptions().getFirst().getName());
        assertEquals(OptionType.STRING, preview.getOptions().getFirst().getType());
        assertTrue(preview.getOptions().getFirst().isRequired());
        assertEquals(
                Set.of(
                        "warning", "mute", "ban",
                        "minecraft-warning", "minecraft-mute", "minecraft-ban"
                ),
                preview.getOptions().getFirst().getChoices().stream()
                        .map(choice -> choice.getAsString())
                        .collect(Collectors.toSet())
        );
    }

    @Test
    void commandBridgeAddsOnlyThePrivateConsoleCommand() {
        var commands = JdaStaffModerationListener.commands(false, false, true);

        assertEquals(12, commands.size());
        SlashCommandData console = (SlashCommandData) command(commands, "console");
        assertEquals(
                java.util.List.of("server", "command"),
                console.getOptions().stream().map(option -> option.getName()).toList()
        );
        assertTrue(console.getOptions().stream().allMatch(option ->
                option.getType() == OptionType.STRING && option.isRequired()));
        assertEquals(DefaultMemberPermissions.DISABLED, console.getDefaultPermissions());
    }

    @Test
    void removalCommandsUseExactDiscordUserIds() {
        for (String name : java.util.List.of("unmute", "unban", "unrestrict")) {
            SlashCommandData command = (SlashCommandData) command(JdaStaffModerationListener.commands(true), name);
            assertEquals(USER_ID_OPTION, command.getOptions().getFirst().getName());
            assertEquals(OptionType.STRING, command.getOptions().getFirst().getType());
            assertTrue(command.getOptions().getFirst().isRequired());
        }
    }

    @Test
    void unrestrictRequiresExactScopeId() {
        SlashCommandData unrestrict = (SlashCommandData) command(
                JdaStaffModerationListener.commands(true), "unrestrict"
        );

        assertEquals(
                java.util.List.of(USER_ID_OPTION, "scope-id"),
                unrestrict.getOptions().stream().map(option -> option.getName()).toList()
        );
        assertTrue(unrestrict.getOptions().stream().allMatch(option -> option.isRequired()));
    }

    private static Set<String> names(java.util.List<CommandData> commands) {
        return commands.stream().map(CommandData::getName).collect(Collectors.toSet());
    }

    private static CommandData command(java.util.List<CommandData> commands, String name) {
        return commands.stream().filter(command -> command.getName().equals(name)).findFirst().orElseThrow();
    }
}
