package net.enthusia.staff.discordbot;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
}
