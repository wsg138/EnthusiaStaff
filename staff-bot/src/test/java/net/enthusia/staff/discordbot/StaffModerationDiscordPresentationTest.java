package net.enthusia.staff.discordbot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import net.dv8tion.jda.api.components.buttons.ButtonStyle;
import org.junit.jupiter.api.Test;

class StaffModerationDiscordPresentationTest {
    @Test
    void profileRendersAsPrivateDashboardWithSelectedOverview() {
        String content = "**Moderation profile**\nDiscord ID: `123`\nRecent cases: 2";
        var overviewSource = new StaffModerationController.Button("Refresh", "overview-id");
        var accountsSource = new StaffModerationController.Button("Linked", "accounts-id");

        var embed = StaffModerationDiscordPresentation.embed(content);
        var overview = StaffModerationDiscordPresentation.button(overviewSource, content);
        var accounts = StaffModerationDiscordPresentation.button(accountsSource, content);

        assertEquals("🛡️ Moderation • Overview", embed.getTitle());
        assertEquals("Discord ID: `123`\nRecent cases: 2", embed.getDescription());
        assertEquals("Private staff view • Read only", embed.getFooter().getText());
        assertFalse(embed.getDescription().contains("Moderation profile"));
        assertEquals("Overview", overview.getLabel());
        assertEquals(ButtonStyle.PRIMARY, overview.getStyle());
        assertNotNull(overview.getEmoji());
        assertEquals("Accounts", accounts.getLabel());
        assertEquals(ButtonStyle.SECONDARY, accounts.getStyle());
    }

    @Test
    void historyFilterHighlightsCurrentPlatform() {
        String content = "**History — Discord**\nNo Discord punishment history exists before D07.";
        var discordSource = new StaffModerationController.Button("Discord", "discord-id");
        var minecraftSource = new StaffModerationController.Button("Minecraft", "minecraft-id");

        var embed = StaffModerationDiscordPresentation.embed(content);
        var discord = StaffModerationDiscordPresentation.button(discordSource, content);
        var minecraft = StaffModerationDiscordPresentation.button(minecraftSource, content);

        assertEquals("🛡️ Moderation • Discord History", embed.getTitle());
        assertEquals(ButtonStyle.PRIMARY, discord.getStyle());
        assertEquals(ButtonStyle.SECONDARY, minecraft.getStyle());
    }

    @Test
    void unstructuredNoticeStillUsesSafeModerationCard() {
        String content = "The read-only moderation view is temporarily unavailable.";

        var embed = StaffModerationDiscordPresentation.embed(content);

        assertEquals("🛡️ Moderation", embed.getTitle());
        assertEquals(content, embed.getDescription());
    }
}
