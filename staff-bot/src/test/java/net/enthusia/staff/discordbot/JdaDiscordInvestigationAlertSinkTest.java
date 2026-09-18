package net.enthusia.staff.discordbot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
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
