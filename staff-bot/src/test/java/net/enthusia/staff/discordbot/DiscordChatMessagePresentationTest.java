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
    @Test
    void onlyTheExplicitMainMinecraftAccountMayMentionItsLinkedDiscordIdentity() {
        UUID main = UUID.randomUUID();
        UUID alt = UUID.randomUUID();
        var selected = Optional.of(new MainMinecraftAccount(main, MainAccountSelectionSource.AUTOMATIC));
        var discord = Set.of(new DiscordUserId("123456789012345678"));
        assertEquals(Optional.of("123456789012345678"),
                DiscordChatMessagePresentation.mainDiscordUserId(main, selected, discord));
        assertTrue(DiscordChatMessagePresentation.mainDiscordUserId(alt, selected, discord).isEmpty());
        assertTrue(DiscordChatMessagePresentation.mainDiscordUserId(main, Optional.empty(), discord).isEmpty());
        assertTrue(DiscordChatMessagePresentation.mainDiscordUserId(main, selected, Set.of()).isEmpty());
        assertTrue(DiscordChatMessagePresentation.mainDiscordUserId(main, selected,
                Set.of(new DiscordUserId("123456789012345678"),
                        new DiscordUserId("223456789012345678"))).isEmpty());
    }

    @Test
    void unlinkedAndAltPlayersShowOnlyTheirCleanMinecraftName() {
        assertEquals("Toxik", DiscordChatMessagePresentation.minecraftName(
                Optional.empty(), "✧ **[Admin]<reset> Toxik"));
        assertEquals("FireFlyy", DiscordChatMessagePresentation.minecraftName(
                Optional.of("FireFlyy"), "✧ **[Avid]<reset> SomethingElse"));
        assertEquals("FireFlyy", DiscordChatMessagePresentation.sender(Optional.empty(), "FireFlyy"));
        assertEquals("<@123456789012345678>",
                DiscordChatMessagePresentation.sender(Optional.of("123456789012345678"), "FireFlyy"));
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
