package net.enthusia.staff.discordbot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.cache.CacheFlag;
import net.enthusia.staff.protocol.ChatBridgeOutboundMessage;
import net.enthusia.staff.protocol.ChatBridgeRenderedMessage;
import org.junit.jupiter.api.Test;

class JdaDiscordGatewayTest {
    private static final String CHAT_PLAYER_NAME = "Player";
    @Test
    void restrictionRuntimeEnablesOnlyMemberOverrideCache() {
        assertEquals(Set.of(CacheFlag.MEMBER_OVERRIDES), JdaDiscordGateway.requiredCacheFlags());
    }

    @Test
    void managedRoleShadowRequestsOnlyGuildMembersIntent() {
        assertEquals(Set.of(), JdaDiscordGateway.gatewayIntents(false));
        assertEquals(Set.of(GatewayIntent.GUILD_MEMBERS), JdaDiscordGateway.gatewayIntents(true));
    }

    @Test
    void DiscordIngressRequestsMessageIntentsOnlyWhenEnabled() {
        assertEquals(
                Set.of(GatewayIntent.GUILD_MESSAGES, GatewayIntent.MESSAGE_CONTENT),
                JdaDiscordGateway.gatewayIntents(false, true));
        assertEquals(
                Set.of(GatewayIntent.GUILD_MEMBERS, GatewayIntent.GUILD_MESSAGES, GatewayIntent.MESSAGE_CONTENT),
                JdaDiscordGateway.gatewayIntents(true, true));
    }

    @Test
    void D09MessageEditTrackingRequestsMessageContentOnlyWhenEnabled() {
        assertEquals(Set.of(), JdaDiscordGateway.gatewayIntents(false, false, false));
        assertEquals(
                Set.of(GatewayIntent.GUILD_MESSAGES, GatewayIntent.MESSAGE_CONTENT),
                JdaDiscordGateway.gatewayIntents(false, false, true));
        assertEquals(
                Set.of(GatewayIntent.GUILD_MEMBERS, GatewayIntent.GUILD_MESSAGES, GatewayIntent.MESSAGE_CONTENT),
                JdaDiscordGateway.gatewayIntents(true, false, true));
    }

    @Test
    void productionRoleSyncEnforcementIsRejectedWhileSafeModesRemainAllowed() {
        assertFalse(JdaDiscordGateway.roleSyncModeAllowed(
                StaffBotEnvironment.PRODUCTION, DiscordRoleSyncConfiguration.Mode.ENFORCE));
        assertTrue(JdaDiscordGateway.roleSyncModeAllowed(
                StaffBotEnvironment.PRODUCTION, DiscordRoleSyncConfiguration.Mode.SHADOW));
        assertTrue(JdaDiscordGateway.roleSyncModeAllowed(
                StaffBotEnvironment.STAGING, DiscordRoleSyncConfiguration.Mode.ENFORCE));
        assertThrows(IllegalArgumentException.class, () -> JdaDiscordGateway.roleSyncModeAllowed(
                null, DiscordRoleSyncConfiguration.Mode.SHADOW));
        assertThrows(IllegalArgumentException.class, () -> JdaDiscordGateway.roleSyncModeAllowed(
                StaffBotEnvironment.STAGING, null));
    }

    @Test
    void staleIdentityCallbacksAreRejectedAfterDisconnectOrNewResolution() {
        JdaDiscordGateway.CallbackFence fence = new JdaDiscordGateway.CallbackFence();
        AtomicBoolean invoked = new AtomicBoolean();

        long disconnectedSession = fence.beginResolution();
        fence.invalidate();
        assertFalse(fence.runIfCurrent(disconnectedSession, () -> invoked.set(true)));
        assertFalse(invoked.get());

        long supersededSession = fence.beginResolution();
        long currentSession = fence.beginResolution();
        assertFalse(fence.runIfCurrent(supersededSession, () -> invoked.set(true)));
        assertFalse(invoked.get());
        assertTrue(fence.runIfCurrent(currentSession, () -> invoked.set(true)));
        assertTrue(invoked.get());
    }
    @Test
    void chatContentIsOnlySenderAndCanonicalMessageWithoutSourceOrRanks() {
        ChatBridgeOutboundMessage shortMessage = chatMessage("hello");
        assertEquals("Player: hello", JdaDiscordGateway.chatContent(shortMessage));

        String rendered = JdaDiscordGateway.chatContent(chatMessage("x".repeat(2_000)));
        assertEquals(2_000, rendered.length());
        assertTrue(rendered.startsWith("Player: "));

        String unicodeRendered = JdaDiscordGateway.chatContent(
                chatMessage("x".repeat(1_990) + "\\uD83D\\uDE00"));
        assertTrue(unicodeRendered.length() <= 2_000);
        assertFalse(Character.isHighSurrogate(unicodeRendered.charAt(unicodeRendered.length() - 1)));
    }

    @Test
    void primaryLinkedAccountGetsMentionButAltUsesMinecraftUsername() {
        ChatBridgeOutboundMessage message = chatMessage("hello");
        assertEquals("<@123456789012345678>: hello",
                JdaDiscordGateway.chatContent(message,
                        Optional.of("123456789012345678"), CHAT_PLAYER_NAME));
        assertEquals("AltPlayer: hello",
                JdaDiscordGateway.chatContent(message, Optional.empty(), "AltPlayer"));
    }

    @Test
    void renderedChatUsesOnlyBodyNotWholeDecoratedRoseChatLine() {
        ChatBridgeRenderedMessage message = renderedMessage(
                "hello", "**[VIP] Player:** *hello*", "[VIP] Player: hello");
        assertEquals("Player: hello", JdaDiscordGateway.renderedChatContent(message));
        assertEquals("<@123456789012345678>: hello",
                JdaDiscordGateway.renderedChatContent(
                        message, Optional.of("123456789012345678"), CHAT_PLAYER_NAME));
    }

    @Test
    void canonicalBodyFallsBackAfterDecoratedTransportMarkersAreStripped() {
        ChatBridgeRenderedMessage message = renderedMessage(
                "hello", "<reset>**[VIP] Player:** *hello*", "[VIP] Player: hello");
        assertEquals("Player: hello", JdaDiscordGateway.renderedChatContent(message));
    }

    @Test
    void discordDisplayNamesAreEscapedBeforeMarkdownPresentation() {
        assertEquals("Name\\\\*With\\\\_Markdown\\\\|",
                JdaDiscordGateway.escapeDiscordMarkdown("Name*With_Markdown|"));
    }

    private static ChatBridgeRenderedMessage renderedMessage(
            String canonical,
            String lineMarkdown,
            String linePlain
    ) {
        UUID eventId = UUID.randomUUID();
        return new ChatBridgeRenderedMessage(
                eventId,
                "rosechat-mc-" + eventId,
                "rosechat-canonical-" + eventId,
                1_800_000_000_000L,
                1_800_000_030_000L,
                "SMP",
                "global",
                UUID.randomUUID(),
                CHAT_PLAYER_NAME,
                canonical,
                canonical,
                canonical,
                "{\"text\":\"" + canonical + "\",\"color\":\"#12ABEF\"}",
                linePlain,
                lineMarkdown,
                "{\"text\":\"" + linePlain.substring(0, Math.min(linePlain.length(), 100)) + "\"}"
        );
    }

    private static ChatBridgeOutboundMessage chatMessage(String text) {
        UUID eventId = UUID.randomUUID();
        return new ChatBridgeOutboundMessage(
                eventId,
                "rosechat-mc-" + eventId,
                "rosechat-canonical-" + eventId,
                1_800_000_000_000L,
                1_800_000_030_000L,
                "SMP",
                "global",
                UUID.randomUUID(),
                CHAT_PLAYER_NAME,
                text
        );
    }

}
