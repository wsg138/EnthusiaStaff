package net.enthusia.staff.discordbot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.List;
import java.util.UUID;
import net.enthusia.staff.persistence.DiscordStaffReadRuntime;
import org.junit.jupiter.api.Test;

class DiscordReportReviewCardTest {
    private static final DiscordStaffReadRuntime.ReviewPulse PULSE =
            new DiscordStaffReadRuntime.ReviewPulse(3, 2, 9, 5);

    @Test void privateReportsAreReadableAndDoNotDisplayPlayerUuidsOrUnrelatedCounters() {
        UUID reportId = UUID.fromString("11111111-2222-3333-4444-555555555555");
        UUID playerId = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
        var pending = new DiscordStaffReadRuntime.PendingReport(
                reportId, playerId, "Steve_123", "Alex", "combat.cheating", "OPEN", 4L);
        var embed = DiscordReportReviewCard.render(PULSE, List.of(pending));
        assertEquals("📋 Player Reports • Staff Review", embed.getTitle());
        assertEquals(0xD69C42, embed.getColorRaw());
        assertEquals("Open", embed.getFields().get(0).getName());
        assertEquals("3", embed.getFields().get(0).getValue());
        assertEquals("Claimed", embed.getFields().get(1).getName());
        assertTrue(embed.getFields().get(2).getName().contains("Combat Cheating"));
        assertTrue(embed.getFields().get(2).getName().contains("Open"));
        String body = embed.getFields().get(2).getValue();
        assertTrue(body.contains("Steve_123"));
        assertTrue(body.contains("Alex"));
        assertTrue(body.contains("11111111"));
        assertFalse(body.contains(playerId.toString()));
        assertFalse(embed.getDescription().contains("9"));
        assertFalse(embed.getDescription().contains("5"));
    }

    @Test void emptyReportQueueStillRendersAnEmbedAndDangerousNamesAreNotReflected() {
        var empty = DiscordReportReviewCard.render(PULSE, List.of());
        assertEquals("Queue Clear", empty.getFields().get(2).getName());

        var pending = new DiscordStaffReadRuntime.PendingReport(
                UUID.randomUUID(), UUID.randomUUID(), "@everyone", "bad\u0060name",
                "chat.spam", "AWAITING_REVIEW", 1L);
        var embed = DiscordReportReviewCard.render(PULSE, List.of(pending));
        String body = embed.getFields().get(2).getValue();
        assertTrue(body.contains("Unknown player"));
        assertFalse(body.contains("@everyone"));
        assertFalse(body.contains("bad\u0060name"));
        assertTrue(embed.getFields().get(2).getName().contains("Chat Spam"));
        assertTrue(embed.getFields().get(2).getName().contains("Awaiting Review"));
    }

    @Test void helperAndDeveloperViewsExplicitlyDescribeReadOnlyReportTriage() {
        var item = new DiscordStaffReadRuntime.PendingReport(
                UUID.randomUUID(), UUID.randomUUID(), "Target", "Reporter",
                "chat.spam", "OPEN", 1L);
        var readOnly = DiscordReportReviewCard.render(PULSE, List.of(item), false);
        assertTrue(readOnly.getDescription().contains("Read-only"));
        assertTrue(readOnly.getDescription().contains("Moderator or above"));
        assertTrue(readOnly.getFooter().getText().contains("No report actions"));
        assertFalse(readOnly.getDescription().contains("Use the corresponding buttons"));
        assertTrue(readOnly.getFields().get(2).getValue().contains("Target"));

        var actionable = DiscordReportReviewCard.render(PULSE, List.of(item), true);
        assertTrue(actionable.getDescription().contains("active Minecraft Staff Mode"));
        assertTrue(actionable.getDescription().contains("buttons below"));
    }

    @Test void moderatorEphemeralReportViewIncludesSafeUsefulTextButHelperDoesNot() {
        var item = new DiscordStaffReadRuntime.PendingReport(
                UUID.randomUUID(), UUID.randomUUID(), "Target", "Reporter",
                "chat.spam", "OPEN", 1L, "Harassment by @everyone <@&123> **oops**");
        var mod = DiscordReportReviewCard.render(PULSE, List.of(item), true);
        String value = mod.getFields().get(2).getValue();
        assertTrue(value.contains("Report text"));
        assertTrue(value.contains("Harassment by"));
        assertFalse(value.contains("@everyone"));
        assertFalse(value.contains("<@&123>"));
        assertFalse(value.contains("**oops**"));
        assertTrue(value.contains("＠everyone"));

        var helper = DiscordReportReviewCard.render(PULSE, List.of(item), false);
        assertFalse(helper.getFields().get(2).getValue().contains("Harassment by"));
        assertFalse(helper.getFields().get(2).getValue().contains("Report text"));
    }

    @Test void sensitiveCategoriesRemainInGameOnly() {
        for (String reason : List.of("safety.threat", "privacy.dox", "account.compromise",
                "personal.information", "harassment.sexual")) {
            var item = new DiscordStaffReadRuntime.PendingReport(
                    UUID.randomUUID(), UUID.randomUUID(), "Target", "Reporter",
                    reason, "OPEN", 1L, "private-secret-do-not-echo");
            var card = DiscordReportReviewCard.render(PULSE, List.of(item), true);
            String body = card.getFields().get(2).getValue();
            assertTrue(body.contains("Sensitive report"));
            assertFalse(body.contains("private-secret-do-not-echo"));
        }
    }

    @Test void veryLongUnicodeAndFormattedTextRemainWithinDiscordFieldBounds() {
        var item = new DiscordStaffReadRuntime.PendingReport(
                UUID.randomUUID(), UUID.randomUUID(), "Target", "Reporter",
                "chat.spam", "OPEN", 1L, "😀".repeat(2000) + "\n@here");
        var result = DiscordReportReviewCard.render(PULSE, List.of(item), true);
        String body = result.getFields().get(2).getValue();
        assertTrue(body.length() <= 1024);
        assertTrue(body.contains("…"));
        assertFalse(body.contains("@here"));
    }

    @Test void invalidUnboundedQueueFailsClosed() {
        var item = new DiscordStaffReadRuntime.PendingReport(
                UUID.randomUUID(), UUID.randomUUID(), "Steve", "Alex",
                "chat.spam", "OPEN", 1L);
        assertThrows(IllegalArgumentException.class,
                () -> DiscordReportReviewCard.render(PULSE, java.util.Collections.nCopies(6, item)));
    }
}
