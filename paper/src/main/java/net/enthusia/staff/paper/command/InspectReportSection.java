package net.enthusia.staff.paper.command;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.enthusia.staff.domain.ports.ReportStore;
import net.enthusia.staff.domain.report.ReportSummary;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;

final class InspectReportSection {
    private static final int DISPLAY_LIMIT = 5;
    private static final int QUERY_LIMIT = DISPLAY_LIMIT + 1;
    private static final DateTimeFormatter TIMESTAMP =
            ModerationTimestampFormatter.inZone(ZoneId.of("UTC"));

    private final Supplier<ReportStore> reports;
    private final Logger logger;

    InspectReportSection(Supplier<ReportStore> reports, Logger logger) {
        this.reports = java.util.Objects.requireNonNull(reports, "reports");
        this.logger = java.util.Objects.requireNonNull(logger, "logger");
    }

    List<Component> render(UUID targetId, boolean canManage) {
        if (!canManage) {
            return List.of();
        }
        try {
            ReportStore store = reports.get();
            if (store == null) {
                return unavailable();
            }
            return activeReports(store.listActiveForTarget(targetId, QUERY_LIMIT));
        } catch (RuntimeException exception) {
            if (logger.isLoggable(Level.WARNING)) {
                logger.log(Level.WARNING, "Inspector report lookup failed for " + targetId, exception);
            }
            return unavailable();
        }
    }

    private static List<Component> activeReports(List<ReportSummary> reports) {
        if (reports.isEmpty()) {
            return List.of(Component.text("Reports: none active", NamedTextColor.GREEN));
        }
        boolean hasMore = reports.size() > DISPLAY_LIMIT;
        String count = hasMore ? DISPLAY_LIMIT + "+" : Integer.toString(reports.size());
        List<Component> lines = new ArrayList<>();
        lines.add(Component.text("Active reports: " + count, NamedTextColor.GOLD)
                .append(Component.space())
                .append(action("[Queue]", "/reports")));
        reports.stream().limit(DISPLAY_LIMIT).map(InspectReportSection::reportLine).forEach(lines::add);
        return List.copyOf(lines);
    }

    private static Component reportLine(ReportSummary report) {
        String command = "/reports view " + report.reportId();
        return Component.text(
                "Report " + report.reportId()
                        + " | " + human(report.state().name())
                        + " | " + report.reasonId()
                        + " | updated " + TIMESTAMP.format(report.updatedAt()),
                NamedTextColor.GRAY
        ).append(Component.space()).append(action("[View]", command));
    }

    private static Component action(String label, String command) {
        return Component.text(label, NamedTextColor.AQUA)
                .clickEvent(ClickEvent.runCommand(command))
                .hoverEvent(HoverEvent.showText(Component.text(command, NamedTextColor.GRAY)));
    }

    private static String human(String value) {
        return value.toLowerCase(Locale.ROOT).replace('_', ' ');
    }

    private static List<Component> unavailable() {
        return List.of(Component.text("Reports: unavailable", NamedTextColor.YELLOW));
    }
}
