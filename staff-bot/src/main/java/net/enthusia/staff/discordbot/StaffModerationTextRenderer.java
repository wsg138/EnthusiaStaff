package net.enthusia.staff.discordbot;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;
import java.util.Locale;
import java.util.Optional;
import net.enthusia.staff.common.CaseId;
import net.enthusia.staff.domain.casefile.CaseReview;
import net.enthusia.staff.domain.history.ModerationHistoryEntry;
import net.enthusia.staff.domain.ports.StaffNoteStore.StaffNote;

/** Builds bounded, escaped user-visible text for the D06 read-only Discord surfaces. */
final class StaffModerationTextRenderer {
    private static final int MAX_CONTENT = 1_900;
    private static final int ITEM_TEXT = 180;
    private static final int CASE_REASON_LIMIT = 300;
    private static final String ITEM_SEPARATOR = " — ";
    private static final DateTimeFormatter TIME = new DateTimeFormatterBuilder()
            .appendValue(ChronoField.YEAR, 4)
            .appendLiteral('-')
            .appendValue(ChronoField.MONTH_OF_YEAR, 2)
            .appendLiteral('-')
            .appendValue(ChronoField.DAY_OF_MONTH, 2)
            .appendLiteral(' ')
            .appendValue(ChronoField.HOUR_OF_DAY, 2)
            .appendLiteral(':')
            .appendValue(ChronoField.MINUTE_OF_HOUR, 2)
            .appendLiteral(" UTC")
            .toFormatter(Locale.ROOT)
            .withZone(ZoneOffset.UTC);

    private StaffModerationTextRenderer() {
    }

    static String profile(StaffModerationReadService.Snapshot snapshot) {
        StringBuilder content = new StringBuilder(MAX_CONTENT).append("**Moderation profile**\n");
        appendIdentity(content, snapshot);
        content.append("Active Discord sanctions: not available before Discord enforcement (D07)\n")
                .append("Active Minecraft sanctions: ").append(snapshot.activeMinecraftSanctions().size())
                .append("\nRecent history entries: ").append(snapshot.recentHistory().size())
                .append("\nRecent cases: ").append(snapshot.recentCases().size())
                .append("\nRecent notes: ").append(snapshot.recentNotes().size());
        return limit(content);
    }

    static String linked(StaffModerationReadService.Snapshot snapshot) {
        StringBuilder content = new StringBuilder(MAX_CONTENT).append("**Linked accounts**\n");
        appendLinkedAccounts(content, snapshot);
        content.append("Historical link records: ").append(snapshot.historicalLinkCount());
        return limit(content);
    }

    static String historyAll(StaffModerationReadService.Snapshot snapshot) {
        StringBuilder content = new StringBuilder(MAX_CONTENT).append("**History — All**\n");
        appendMinecraftHistory(content, snapshot);
        content.append("Discord: no Discord punishment history exists before D07; D06 does not invent entries.\n")
                .append("Cases: ").append(snapshot.recentCases().size())
                .append(" | Notes: ").append(snapshot.recentNotes().size());
        return limit(content);
    }

    static String historyDiscordOnly() {
        return "**History — Discord**\nNo Discord punishment history exists before D07. "
                + "D06 does not create or invent Discord sanctions.";
    }

    static String historyMinecraft(StaffModerationReadService.Snapshot snapshot) {
        StringBuilder content = new StringBuilder(MAX_CONTENT).append("**History — Minecraft**\n");
        appendMinecraftHistory(content, snapshot);
        return limit(content);
    }

    static String notes(StaffModerationReadService.Snapshot snapshot) {
        StringBuilder content = new StringBuilder(MAX_CONTENT).append("**Recent staff notes**\n");
        appendNotes(content, snapshot);
        return limit(content);
    }

    static String cases(StaffModerationReadService.Snapshot snapshot) {
        StringBuilder content = new StringBuilder(MAX_CONTENT).append("**Recent cases**\n");
        appendCases(content, snapshot);
        return limit(content);
    }

    static String caseView(CaseId caseId, CaseReview review) {
        StringBuilder content = new StringBuilder(MAX_CONTENT).append("**Case `").append(caseId).append("`**\n")
                .append("State: ").append(review.state()).append('\n')
                .append("Issued: ").append(TIME.format(review.issuedAt())).append('\n')
                .append("Reason: ").append(shorten(escape(review.publicReason()), CASE_REASON_LIMIT)).append('\n')
                .append("Sanctions: ").append(review.sanctions().size());
        return limit(content);
    }

    private static void appendIdentity(StringBuilder content, StaffModerationReadService.Snapshot snapshot) {
        snapshot.target().discordId().ifPresentOrElse(
                id -> content.append("Discord ID: `").append(id.value()).append("`\n"),
                () -> content.append("Discord: not currently linked\n")
        );
        Optional<StaffModerationReadService.LinkedMinecraft> main = snapshot.linkedMinecraft().stream()
                .filter(StaffModerationReadService.LinkedMinecraft::main)
                .findFirst();
        main.ifPresentOrElse(
                linked -> content.append("Main Minecraft: ")
                        .append(escape(linked.username().orElse(linked.playerId().toString())))
                        .append(" (").append(linked.platform()).append(")\n"),
                () -> content.append("Main Minecraft: none\n")
        );
        content.append("Linked Minecraft accounts: ").append(snapshot.linkedMinecraft().size()).append('\n');
    }

    private static void appendLinkedAccounts(
            StringBuilder content,
            StaffModerationReadService.Snapshot snapshot
    ) {
        if (snapshot.linkedMinecraft().isEmpty()) {
            content.append("No current Minecraft accounts are linked. This is a Discord-only or unlinked target.\n");
            return;
        }
        for (StaffModerationReadService.LinkedMinecraft linked : snapshot.linkedMinecraft()) {
            content.append(linked.main() ? "• **Main:** " : "• ")
                    .append(escape(linked.username().orElse(linked.playerId().toString())))
                    .append(ITEM_SEPARATOR).append(linked.platform())
                    .append(" (`").append(linked.playerId()).append("`)\n");
        }
    }

    private static void appendMinecraftHistory(
            StringBuilder content,
            StaffModerationReadService.Snapshot snapshot
    ) {
        if (snapshot.recentHistory().isEmpty()) {
            content.append("Minecraft: no moderation history is available for the current linked identities.\n");
            return;
        }
        for (ModerationHistoryEntry entry : snapshot.recentHistory()) {
            content.append("• ").append(TIME.format(entry.occurredAt())).append(ITEM_SEPARATOR)
                    .append(entry.eventType()).append(" / ").append(escape(entry.status()))
                    .append(ITEM_SEPARATOR).append(shorten(escape(entry.publicReason()), ITEM_TEXT)).append('\n');
        }
    }

    private static void appendNotes(StringBuilder content, StaffModerationReadService.Snapshot snapshot) {
        if (snapshot.recentNotes().isEmpty()) {
            content.append("No staff notes are available for the current linked Minecraft identities.");
            return;
        }
        for (StaffNote note : snapshot.recentNotes()) {
            content.append("• ").append(TIME.format(note.createdAt())).append(ITEM_SEPARATOR)
                    .append(shorten(escape(note.noteText()), ITEM_TEXT)).append('\n');
        }
    }

    private static void appendCases(StringBuilder content, StaffModerationReadService.Snapshot snapshot) {
        if (snapshot.recentCases().isEmpty()) {
            content.append("No cases are available for the current linked Minecraft identities.");
            return;
        }
        for (CaseReview review : snapshot.recentCases()) {
            content.append("• `").append(review.caseId()).append("` — ")
                    .append(review.state()).append(ITEM_SEPARATOR)
                    .append(shorten(escape(review.publicReason()), ITEM_TEXT)).append('\n');
        }
    }

    private static String limit(StringBuilder content) {
        return shorten(content.toString(), MAX_CONTENT);
    }

    private static String shorten(String value, int max) {
        if (value.length() <= max) {
            return value;
        }
        return value.substring(0, Math.max(0, max - 1)).concat("…");
    }

    private static String escape(String value) {
        return value.replace("`", "'").replace("@", "＠");
    }
}
