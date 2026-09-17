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
    void privateAlertsFailClosedUnlessStaffCanViewAndEveryoneCannot() {
        assertEquals(
                Optional.of("DISCORD_ALERT_STAFF_ROLE_UNAVAILABLE"),
                JdaDiscordInvestigationAlertSink.channelPolicyError(false, false, false)
        );
        assertEquals(
                Optional.of("DISCORD_ALERT_CHANNEL_NOT_PRIVATE"),
                JdaDiscordInvestigationAlertSink.channelPolicyError(true, true, true)
        );
        assertEquals(
                Optional.of("DISCORD_ALERT_STAFF_ROLE_CANNOT_VIEW"),
                JdaDiscordInvestigationAlertSink.channelPolicyError(true, false, false)
        );
        assertTrue(JdaDiscordInvestigationAlertSink.channelPolicyError(true, false, true).isEmpty());
    }
}
