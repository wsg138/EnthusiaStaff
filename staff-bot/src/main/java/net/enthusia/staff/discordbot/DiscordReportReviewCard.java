package net.enthusia.staff.discordbot;

import java.util.List;
import java.util.Objects;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.enthusia.staff.domain.report.ReportReasonLabel;
import net.enthusia.staff.persistence.DiscordStaffReadRuntime;

/** Report-only private review card. Report actions and authorization live elsewhere. */
final class DiscordReportReviewCard {
    private static final int COLOR = 0xD69C42;
    private static final int MAX_REPORTS = 5;

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
                    "**Reported player:** " + safeName(report.targetName())
                            + "\n**Reported by:** " + safeName(report.reporterName())
                            + "\n**Reference:** `" + report.reportId().toString().substring(0, 8) + "`",
                    false);
            index++;
        }
        return builder.build();
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
