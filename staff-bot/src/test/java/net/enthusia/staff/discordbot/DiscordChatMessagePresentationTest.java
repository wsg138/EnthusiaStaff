package net.enthusia.staff.discordbot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.enthusia.staff.domain.moderation.DiscordUserId;
import net.enthusia.staff.domain.moderation.MainAccountSelectionSource;
import net.enthusia.staff.domain.moderation.MainMinecraftAccount;
import org.junit.jupiter.api.Test;

class DiscordChatMessagePresentationTest {
    private static final String MAIN_DISCORD_ID = "123456789012345678";
    private static final String ALT_MINECRAFT_NAME = "FireFlyy";
    @Test
    void onlyTheExplicitMainMinecraftAccountMayMentionItsLinkedDiscordIdentity() {
        UUID main = UUID.randomUUID();
        UUID alt = UUID.randomUUID();
        var selected = Optional.of(new MainMinecraftAccount(main, MainAccountSelectionSource.AUTOMATIC));
        var discord = Set.of(new DiscordUserId(MAIN_DISCORD_ID));
        assertEquals(Optional.of(MAIN_DISCORD_ID),
                DiscordChatMessagePresentation.mainDiscordUserId(main, selected, discord));
        assertTrue(DiscordChatMessagePresentation.mainDiscordUserId(alt, selected, discord).isEmpty());
        assertTrue(DiscordChatMessagePresentation.mainDiscordUserId(main, Optional.empty(), discord).isEmpty());
        assertTrue(DiscordChatMessagePresentation.mainDiscordUserId(main, selected, Set.of()).isEmpty());
        assertTrue(DiscordChatMessagePresentation.mainDiscordUserId(main, selected,
                Set.of(new DiscordUserId(MAIN_DISCORD_ID),
                        new DiscordUserId("223456789012345678"))).isEmpty());
    }

    @Test
    void unlinkedAndAltPlayersShowOnlyTheirCleanMinecraftName() {
        assertEquals("Toxik", DiscordChatMessagePresentation.minecraftName(
                Optional.empty(), "✧ **[Admin]<reset> Toxik"));
        assertEquals(ALT_MINECRAFT_NAME, DiscordChatMessagePresentation.minecraftName(
                Optional.of(ALT_MINECRAFT_NAME), "✧ **[Avid]<reset> SomethingElse"));
        assertEquals(ALT_MINECRAFT_NAME, DiscordChatMessagePresentation.sender(Optional.empty(), ALT_MINECRAFT_NAME));
        assertEquals("<@123456789012345678>",
                DiscordChatMessagePresentation.sender(Optional.of(MAIN_DISCORD_ID), ALT_MINECRAFT_NAME));
    }

    @Test
    void stripsTransportAndRankFormattingWithoutRemovingMessageMeaning() {
        assertEquals("hello, how are you?", DiscordChatMessagePresentation.body(
                "§6<gold>hello, how are you?<reset>"));
        assertEquals("Diamond Spear", DiscordChatMessagePresentation.body(
                "<chat=114583e7-f384-42c7-9352-4d34373c3df0:[i]:>Diamond Spear"));
        assertEquals("hi \\*everyone\\* and @everyone", DiscordChatMessagePresentation.body(
                "hi *everyone* and @everyone"));
    }
}
