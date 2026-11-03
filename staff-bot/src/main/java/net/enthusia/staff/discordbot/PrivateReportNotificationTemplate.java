package net.enthusia.staff.discordbot;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.enthusia.staff.domain.report.ReportReasonLabel;
import net.enthusia.staff.persistence.DiscordStaffReadRuntime;

/**
 * Inert preparation for an owner-approved, private StaffBot report notification.
 * This code constructs text only: it never opens a Discord channel or sends a message.
 */
final class PrivateReportNotificationTemplate {
    private static final int MAX_REASON_CODEPOINTS = 80;
    private static final String NO_DESCRIPTION = "(No report description provided)";

    private PrivateReportNotificationTemplate() { }

    /**
     * A snapshot produced by a trusted runtime immediately before a future send.
     * The five channel permission facts must come from fresh JDA effective-permission
     * checks; these booleans alone are not authentication or a reusable capability.
     */
    record DestinationProof(
            boolean ownerEnabled,
            String expectedGuildId,
            String actualGuildId,
            boolean channelResolved,
            boolean staffRolePresent,
            boolean everyoneCanView,
            boolean staffCanView,
            boolean otherRoleCanView,
            boolean memberOverrideCanView
    ) {
        boolean eligible() {
            return ownerEnabled && channelResolved
                    && expectedGuildId != null && expectedGuildId.matches("[1-9][0-9]{5,19}")
                    && expectedGuildId.equals(actualGuildId)
                    && JdaDiscordInvestigationAlertSink.channelPolicyError(
                            staffRolePresent, everyoneCanView, staffCanView,
                            otherRoleCanView, memberOverrideCanView).isEmpty();
        }
    }

    /**
     * Fail closed for disabled/unverified channels. The returned string remains
     * staging-only; any later sender must separately revalidate all JDA permissions,
     * apply no allowed mentions, and use a durable idempotent report outbox.
     */
    static Optional<String> compose(DiscordStaffReadRuntime.PendingReport report,
                                    DestinationProof destination) {
        Objects.requireNonNull(report, "report");
        Objects.requireNonNull(destination, "destination");
        if (!destination.eligible()) {
            return Optional.empty();
        }
        UUID reportId = Objects.requireNonNull(report.reportId(), "reportId");
        String description;
        if (DiscordReportReviewCard.restrictedReason(report.reasonId())) {
            description = "Sensitive report. View the full description in-game.";
        } else if (report.description() == null || report.description().isBlank()) {
            description = NO_DESCRIPTION;
        } else {
            description = DiscordReportReviewCard.safeDescription(report.description());
        }
        String reason = DiscordReportReviewCard.safeDescription(
                ReportReasonLabel.display(report.reasonId()));
        if (reason.codePointCount(0, reason.length()) > MAX_REASON_CODEPOINTS) {
            reason = reason.substring(0, reason.offsetByCodePoints(0, MAX_REASON_CODEPOINTS)) + "…";
        }
        return Optional.of("Staff report awaiting review"
                + "\nReported player: " + safePlayerName(report.targetName())
                + "\nReported by: " + safePlayerName(report.reporterName())
                + "\nReason: " + reason
                + "\nDescription: " + description
                + "\nReference: " + reportId
                + "\nFull report: /reports view " + reportId
                + "\nCase state: " + safeState(report.state()));
    }

    private static String safePlayerName(String name) {
        return name != null && name.matches("\\*?[A-Za-z0-9_]{1,32}")
                ? name : "Unknown player";
    }

    private static String safeState(String state) {
        return switch (state == null ? "" : state) {
            case "OPEN" -> "Open";
            case "CLAIMED" -> "Claimed";
            case "AWAITING_REVIEW" -> "Awaiting review";
            default -> "Review";
        };
    }
}
