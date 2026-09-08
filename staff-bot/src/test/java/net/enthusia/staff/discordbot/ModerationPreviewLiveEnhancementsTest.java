package net.enthusia.staff.discordbot;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class ModerationPreviewLiveEnhancementsTest {
    private static final String RESOURCE = "/moderation-preview/live-enhancements.js";

    @Test
    void liveMessageActionsUseAvatarsMenusAndBoundedTwoMinuteContext() throws IOException {
        String source = resourceText();

        assertTrue(source.contains("message?.author?.avatarUrl"));
        assertTrue(source.contains("message-avatar-image"));
        assertTrue(source.contains("Show context"));
        assertTrue(source.contains("Delete on confirm (simulation)"));
        assertTrue(source.contains("fetchContextPage(trigger.channelId, 'before', id)"));
        assertTrue(source.contains("fetchContextPage(trigger.channelId, 'after', id)"));
        assertTrue(source.contains("<= 120_000"));
        assertTrue(source.contains("context-focus"));
    }

    @Test
    void workflowResumeAndDiscordVersusInGamePolicyTabsAreExplicit() throws IOException {
        String source = resourceText();

        assertTrue(source.contains("if (!state.workflow)"));
        assertTrue(source.contains("openOrResumeWorkflow"));
        assertTrue(source.contains("Discord / chat"));
        assertTrue(source.contains("In-game"));
        assertTrue(source.contains("key === 'cheating'"));
        assertTrue(source.contains("key !== 'cheating'"));
    }

    @Test
    void liveIdentityFallsBackToLinkedAccountAndUsesCanonicalServerLogo() throws IOException {
        String source = resourceText();

        assertTrue(source.contains("liveModeration.accounts.find((account) => account.main) || liveModeration.accounts[0]"));
        assertTrue(source.contains("https://enthusia.info/assets/enthusia-logo-v2.png"));
        assertTrue(source.contains("sessionBoundChannelId"));
        assertTrue(source.contains("discord-channel"));
    }

    private String resourceText() throws IOException {
        try (InputStream input = getClass().getResourceAsStream(RESOURCE)) {
            assertNotNull(input, RESOURCE);
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
