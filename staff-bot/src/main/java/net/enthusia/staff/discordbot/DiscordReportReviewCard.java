package net.enthusia.staff.discordbot;

import java.util.List;
import java.util.Objects;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.enthusia.staff.domain.report.ReportReasonLabel;
import net.enthusia.staff.persistence.DiscordStaffReadRuntime;

/**
 * Ephemeral report-only review card. Free-form previews are Mod+ only and
 * intentionally never flow through Velocity's public report webhook.
 */
final class DiscordReportReviewCard {
    private static final int COLOR = 0xD69C42;
    private static final int MAX_REPORTS = 5;
    private static final int MAX_DESCRIPTION_CODEPOINTS = 240;
    private static final char FULLWIDTH_AT = '＠';
    private static final int ASCII_AT = '@';
    private static final String MARKUP_CHARS = "<>*_|~" + (char) 96;

    private DiscordReportReviewCard() { }

    static MessageEmbed render(DiscordStaffReadRuntime.ReviewPulse pulse,
            List<DiscordStaffReadRuntime.PendingReport> reports) {
        return render(pulse, reports, true);
    }

    static MessageEmbed render(DiscordStaffReadRuntime.ReviewPulse pulse,
            List<DiscordStaffReadRuntime.PendingReport> reports, boolean actionsEnabled) {
        Objects.requireNonNull(pulse, "pulse");
        Objects.requireNonNull(reports, "reports");
        if (reports.size() > MAX_REPORTS) {
            throw new IllegalArgumentException("report card exceeds review queue limit");
        }
        EmbedBuilder builder = new EmbedBuilder()
                .setColor(COLOR)
                .setTitle("📋 Player Reports • Staff Review")
                .setDescription(actionsEnabled
                        ? "Private report queue • Use the corresponding buttons below to claim or resolve. "
                                + "Actions also require active Minecraft Staff Mode and current authority. "
                                + "Reports are investigation leads, not evidence of guilt."
                        : "Private report queue • Read-only for this Minecraft rank. "
                                + "A Moderator or above must claim or resolve reports. "
                                + "Reports are investigation leads, not evidence of guilt.")
                .addField("Open", Long.toString(pulse.openReports()), true)
                .addField("Claimed", Long.toString(pulse.claimedReports()), true)
                .setFooter(actionsEnabled
                        ? "Up to five reports • Actions recheck current Minecraft authority"
                        : "Up to five reports • No report actions available for this rank");
        if (reports.isEmpty()) {
            builder.addField("Queue Clear", "No reports are awaiting action in this view.", false);
        }
        int index = 1;
        for (var report : reports) {
            String reason = ReportReasonLabel.display(report.reasonId());
            String state = safeState(report.state());
            builder.addField("Report " + index + " • " + reason + " • " + state,
                    reportField(report, actionsEnabled), false);
            index++;
        }
        return builder.build();
    }

    private static String reportField(DiscordStaffReadRuntime.PendingReport report,
                                      boolean actionsEnabled) {
        StringBuilder details = new StringBuilder(256)
                .append("**Reported player:** ").append(safeName(report.targetName()))
                .append("\n**Reported by:** ").append(safeName(report.reporterName()))
                .append("\n**Reference:** ").append(report.reportId().toString(), 0, 8);
        if (actionsEnabled && !report.description().isBlank()) {
            details.append("\n**Report text:** ")
                    .append(restrictedReason(report.reasonId())
                            ? "Sensitive report — view full text in-game."
                            : safeDescription(report.description()));
        }
        return details.toString();
    }

    static boolean restrictedReason(String reasonId) {
        if (reasonId == null) {
            return true;
        }
        return reasonId.startsWith("safety.") || reasonId.startsWith("privacy.")
                || reasonId.startsWith("account.") || reasonId.startsWith("personal.")
                || reasonId.startsWith("harassment.sexual");
    }

    /** Bounded plain text; never emit runnable Discord mentions or Markdown. */
    static String safeDescription(String description) {
        if (description == null || description.isBlank()) {
            return "(No report text)";
        }
        StringBuilder cleaned = new StringBuilder();
        int count = 0;
        int offset = 0;
        while (offset < description.length() && count < MAX_DESCRIPTION_CODEPOINTS) {
            int codePoint = description.codePointAt(offset);
            offset += Character.charCount(codePoint);
            count++;
            appendNeutralized(cleaned, codePoint);
        }
        String safe = cleaned.toString().trim().replaceAll("\\s+", " ");
        return offset < description.length() ? safe + "…" : safe;
    }

    private static void appendNeutralized(StringBuilder result, int codePoint) {
        if (Character.isISOControl(codePoint) || Character.isWhitespace(codePoint)
                || Character.getType(codePoint) == Character.FORMAT) {
            result.append(' ');
        } else if (codePoint == ASCII_AT) {
            result.append(FULLWIDTH_AT);
        } else if (MARKUP_CHARS.indexOf(codePoint) >= 0) {
            result.append(' ');
        } else {
            result.appendCodePoint(codePoint);
        }
    }

    private static String safeName(String name) {
        return name == null || !name.matches("\\*?[A-Za-z0-9_]{1,32}")
                ? "Unknown player" : name;
    }

    private static String safeState(String state) {
        return switch (state == null ? "" : state) {
            case "OPEN" -> "Open";
            case "CLAIMED" -> "Claimed";
            case "AWAITING_REVIEW" -> "Awaiting Review";
            default -> "Review";
        };
    }
}
