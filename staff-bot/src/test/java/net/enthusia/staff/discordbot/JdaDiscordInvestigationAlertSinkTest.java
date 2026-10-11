package net.enthusia.staff.discordbot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;

class JdaDiscordInvestigationAlertSinkTest {
    @Test
    void privateAlertPresentationCarriesInvestigationContextWithoutCreatingTargetMentions() {
        String content = JdaDiscordInvestigationAlertSink.content(DiscordInvestigationAlertControlsTest.alert());

        assertTrue(content.contains("18446744073709551614"));
        assertTrue(content.contains("LinkedAlt"));
        assertTrue(content.contains("BAN"));
        assertTrue(content.contains("Ban reason"));
        assertTrue(content.contains("survival"));
        assertTrue(content.contains("No automatic punishment"));
        assertFalse(content.contains("<@18446744073709551614>"));
    }

    @Test
    void privateAlertsFailClosedUnlessOnlyStaffCanView() {
        assertPolicy("DISCORD_ALERT_STAFF_ROLE_UNAVAILABLE", false, false, false, false, false);
        assertPolicy("DISCORD_ALERT_CHANNEL_NOT_PRIVATE", true, true, true, false, false);
        assertPolicy("DISCORD_ALERT_STAFF_ROLE_CANNOT_VIEW", true, false, false, false, false);
        assertPolicy("DISCORD_ALERT_CHANNEL_OTHER_ROLE_CAN_VIEW", true, false, true, true, false);
        assertPolicy("DISCORD_ALERT_CHANNEL_MEMBER_OVERRIDE_CAN_VIEW", true, false, true, false, true);
        assertTrue(JdaDiscordInvestigationAlertSink.channelPolicyError(
                true, false, true, false, false).isEmpty());
    }

    @Test
    void administratorRoleWithoutConfiguredStaffRoleFailsPrivateChannelFence() {
        assertTrue(JdaDiscordInvestigationAlertSink.unexpectedRoleCanView(
                false, false, false, true, false));
        assertFalse(JdaDiscordInvestigationAlertSink.unexpectedRoleCanView(
                false, true, false, true, true));
        assertFalse(JdaDiscordInvestigationAlertSink.unexpectedRoleCanView(
                false, false, true, true, true));
    }

    @Test
    void onlyFirstRecordedDeliveryAttemptMayPingStaff() {
        var initial = DiscordInvestigationAlertControlsTest.alert();
        assertTrue(JdaDiscordInvestigationAlertSink.shouldPingStaff(initial));
        assertTrue(JdaDiscordInvestigationAlertSink.alertContent(initial, "123").startsWith("<@&123> "));

        var retry = new net.enthusia.staff.domain.investigation.EvasionAlert(
                initial.alertId(), initial.operationKey(), initial.context(), initial.state(),
                net.enthusia.staff.domain.investigation.EvasionAlert.DeliveryState.RETRY,
                initial.minecraftDelivery(), 1, initial.minecraftAttempts(),
                Optional.of("DISCORD_ALERT_SEND_TIMEOUT"), initial.minecraftErrorCode(),
                initial.discordNextAttemptAt(), initial.minecraftNextAttemptAt(),
                initial.createdAt(), initial.updatedAt(), initial.revision() + 1, false);
        assertFalse(JdaDiscordInvestigationAlertSink.shouldPingStaff(retry));
        String retryContent = JdaDiscordInvestigationAlertSink.alertContent(retry, "123");
        assertTrue(retryContent.startsWith("**Linked-alt investigation alert**"));
        assertFalse(retryContent.contains("<@&123>"));
        assertFalse(JdaDiscordInvestigationAlertSink.shouldPingStaff(null));
    }

    @Test
    void timedOutSendCancelsPendingDiscordRequest() {
        CompletableFuture<Object> request = new CompletableFuture<>();

        DiscordInvestigationAlertSink.Delivery delivery =
                JdaDiscordInvestigationAlertSink.awaitSend(request, Duration.ofMillis(1));

        assertFalse(delivery.delivered());
        assertEquals("DISCORD_ALERT_SEND_TIMEOUT", delivery.errorCode());
        assertTrue(request.isCancelled());
    }

    private static void assertPolicy(
            String expected,
            boolean staffRolePresent,
            boolean everyoneCanView,
            boolean staffCanView,
            boolean otherRoleCanView,
            boolean memberOverrideCanView
    ) {
        assertEquals(
                Optional.of(expected),
                JdaDiscordInvestigationAlertSink.channelPolicyError(
                        staffRolePresent, everyoneCanView, staffCanView, otherRoleCanView, memberOverrideCanView)
        );
    }
}
