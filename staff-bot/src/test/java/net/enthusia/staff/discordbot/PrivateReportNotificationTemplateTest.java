package net.enthusia.staff.discordbot;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import net.enthusia.staff.persistence.DiscordStaffReadRuntime;
import org.junit.jupiter.api.Test;

final class PrivateReportNotificationTemplateTest {
    private static final String GUILD = "123456789012345678";
    private static final UUID REPORT_ID =
            UUID.fromString("12345678-1234-1234-1234-123456789abc");

    private static DiscordStaffReadRuntime.PendingReport report(String reason, String body) {
        return new DiscordStaffReadRuntime.PendingReport(
                REPORT_ID, UUID.randomUUID(), "Target", "Reporter",
                reason, "OPEN", 1, body);
    }

    private static PrivateReportNotificationTemplate.DestinationProof route(
            boolean enabled, boolean everyone, boolean staff,
            boolean otherRole, boolean memberOverride) {
        return new PrivateReportNotificationTemplate.DestinationProof(
                enabled, GUILD, GUILD, true, true,
                everyone, staff, otherRole, memberOverride);
    }

    @Test
    void optInIsMandatoryEvenForAValidPrivateStaffChannel() {
        assertFalse(PrivateReportNotificationTemplate.compose(report("chat.spam", "text"),
                route(false, false, true, false, false)).isPresent());
    }

    @Test
    void wrongGuildAndUnverifiedChannelFailClosed() {
        var valid = route(true, false, true, false, false);
        var mismatch = new PrivateReportNotificationTemplate.DestinationProof(
                true, GUILD, "987654321098765432", true, true,
                false, true, false, false);
        var absent = new PrivateReportNotificationTemplate.DestinationProof(
                true, GUILD, GUILD, false, true,
                false, true, false, false);
        assertTrue(valid.eligible());
        assertFalse(mismatch.eligible());
        assertFalse(absent.eligible());
        assertFalse(PrivateReportNotificationTemplate.compose(report("chat.spam", "text"),
                mismatch).isPresent());
    }

    @Test
    void publicRoleAndUnexpectedViewersCannotReceiveReportText() {
        var incident = report("chat.spam", "sensitive body");
        assertFalse(PrivateReportNotificationTemplate.compose(incident,
                route(true, true, true, false, false)).isPresent());
        assertFalse(PrivateReportNotificationTemplate.compose(incident,
                route(true, false, false, false, false)).isPresent());
        assertFalse(PrivateReportNotificationTemplate.compose(incident,
                route(true, false, true, true, false)).isPresent());
        assertFalse(PrivateReportNotificationTemplate.compose(incident,
                route(true, false, true, false, true)).isPresent());
    }

    @Test
    void approvedPrivateDestinationGetsReadableSafeReportTextAndFullReference() {
        var incident = report("chat.spam", "[click](https://example.invalid) @everyone 😀");
        String message = PrivateReportNotificationTemplate.compose(incident,
                route(true, false, true, false, false)).orElseThrow();
        assertTrue(message.contains("Reported player: Target"));
        assertTrue(message.contains("Reported by: Reporter"));
        assertTrue(message.contains("Reason: Chat Spam"));
        assertTrue(message.contains("Reference: " + REPORT_ID));
        assertTrue(message.contains("/reports view " + REPORT_ID));
        assertTrue(message.contains("😀"));
        assertFalse(message.contains("@everyone"));
        assertFalse(message.contains("https://"));
        assertFalse(message.contains("]("));
        assertTrue(message.length() < 1200);
    }

    @Test
    void sensitiveReasonsNeverIncludeUntrustedDescriptions() {
        for (String reason : new String[]{
                "safety.threat", "privacy.dox", "account.compromise",
                "personal.information", "harassment.sexual"}) {
            var incident = report(reason, "HIGHLY-PRIVATE-TEXT");
            String message = PrivateReportNotificationTemplate.compose(incident,
                    route(true, false, true, false, false)).orElseThrow();
            assertFalse(message.contains("HIGHLY-PRIVATE-TEXT"));
            assertTrue(message.contains("Sensitive report"));
        }
    }

    @Test
    void unusualNamesAndStateDoNotBecomeUserMentions() {
        var incident = new DiscordStaffReadRuntime.PendingReport(
                REPORT_ID, UUID.randomUUID(), "@everyone", "<@&123>",
                "chat.spam", "@everyone", 1, "report");
        String message = PrivateReportNotificationTemplate.compose(incident,
                route(true, false, true, false, false)).orElseThrow();
        assertFalse(message.contains("@everyone"));
        assertFalse(message.contains("<@&123>"));
        assertTrue(message.contains("Unknown player"));
        assertTrue(message.contains("Case state: Review"));
    }
}
