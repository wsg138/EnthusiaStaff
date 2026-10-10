package net.enthusia.staff.discordbot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.cache.CacheFlag;
import net.enthusia.staff.protocol.ChatBridgeOutboundMessage;
import net.enthusia.staff.protocol.ChatBridgeRenderedMessage;
import org.junit.jupiter.api.Test;

class JdaDiscordGatewayTest {
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
    void chatContentIncludesSourceAndSenderAndHonorsDiscordLimit() {
        ChatBridgeOutboundMessage shortMessage = chatMessage("hello");
        assertEquals("[SMP] Player: hello", JdaDiscordGateway.chatContent(shortMessage));

        ChatBridgeOutboundMessage longMessage = chatMessage("x".repeat(2_000));
        String rendered = JdaDiscordGateway.chatContent(longMessage);
        assertEquals(2_000, rendered.length());
        assertTrue(rendered.startsWith("[SMP] Player: "));

        ChatBridgeOutboundMessage unicodeBoundary = chatMessage("x".repeat(1_985) + "\uD83D\uDE00");
        String unicodeRendered = JdaDiscordGateway.chatContent(unicodeBoundary);
        assertFalse(Character.isHighSurrogate(unicodeRendered.charAt(unicodeRendered.length() - 1)));
        assertTrue(unicodeRendered.length() <= 2_000);
    }

    @Test
    void linkedSenderPresentationIsAdditiveAndSafeAcrossFallbacks() {
        ChatBridgeOutboundMessage plain = chatMessage("hello");
        assertEquals(
                "[SMP · @DiscordName] Player: hello",
                JdaDiscordGateway.chatContent(plain, java.util.Optional.of("@DiscordName"))
        );

        ChatBridgeRenderedMessage rendered = renderedMessage(
                "hello",
                "**[VIP] Player:** *hello*",
                "[VIP] Player: hello"
        );
        assertEquals(
                "[SMP · @DiscordName] **[VIP] Player:** *hello*",
                JdaDiscordGateway.renderedChatContent(
                        rendered,
                        java.util.Optional.of("@DiscordName")
                )
        );
    }

    @Test
    void linkedPrefixStillHonorsDiscordContentLimit() {
        ChatBridgeRenderedMessage rendered = renderedMessage(
                "hello",
                "*".repeat(2_100),
                "[VIP] Player: " + "x".repeat(2_100)
        );

        String content = JdaDiscordGateway.renderedChatContent(
                rendered,
                java.util.Optional.of("@DiscordName")
        );

        assertEquals(2_000, content.length());
        assertTrue(content.startsWith("[SMP · @DiscordName] [VIP] Player: "));
        assertFalse(Character.isHighSurrogate(content.charAt(content.length() - 1)));
    }

    @Test
    void discordDisplayNamesAreEscapedBeforeMarkdownPresentation() {
        assertEquals(
                "Name\\*With\\_Markdown\\|",
                JdaDiscordGateway.escapeDiscordMarkdown("Name*With_Markdown|")
        );
        assertEquals("", JdaDiscordGateway.escapeDiscordMarkdown("\n\t"));
    }

    @Test
    void uncachedDiscordAccountUsesNonPingingMentionInsteadOfLinkedLabel() {
        String discordId = "1".repeat(18);
        assertEquals(
                java.util.Optional.of("<@" + discordId + ">"),
                JdaDiscordGateway.discordProfileMention(discordId)
        );
        assertEquals(
                "[SMP · <@" + discordId + ">] Player: hello",
                JdaDiscordGateway.chatContent(
                        chatMessage("hello"),
                        JdaDiscordGateway.discordProfileMention(discordId)
                )
        );
        assertEquals(java.util.Optional.empty(), JdaDiscordGateway.discordProfileMention("bad"));
        assertEquals(java.util.Optional.empty(), JdaDiscordGateway.discordProfileMention(null));
        assertEquals(
                "[SMP] Player: hello",
                JdaDiscordGateway.chatContent(chatMessage("hello"),
                        JdaDiscordGateway.discordProfileMention("bad"))
        );
    }

    @Test
    void renderedChatUsesResolvedMarkdownWithoutDuplicatingSender() {
        ChatBridgeRenderedMessage message = renderedMessage(
                "hello",
                "**[VIP] Player:** *hello*",
                "[VIP] Player: hello"
        );

        assertEquals(
                "[SMP] **[VIP] Player:** *hello*",
                JdaDiscordGateway.renderedChatContent(message)
        );
    }

    @Test
    void renderedChatFallsBackToPlainBeforeTruncatingBrokenMarkdown() {
        ChatBridgeRenderedMessage message = renderedMessage(
                "hello",
                "*".repeat(2_100),
                "[VIP] Player: " + "x".repeat(2_100)
        );

        String rendered = JdaDiscordGateway.renderedChatContent(message);
        assertEquals(2_000, rendered.length());
        assertTrue(rendered.startsWith("[SMP] [VIP] Player: "));
        assertFalse(Character.isHighSurrogate(rendered.charAt(rendered.length() - 1)));
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
                "Player",
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
                "Player",
                text
        );
    }

}
