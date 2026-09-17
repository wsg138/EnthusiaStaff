package net.enthusia.staff.paper.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.enthusia.staff.domain.ports.ReportStore;
import net.enthusia.staff.domain.report.ReportState;
import net.enthusia.staff.domain.report.ReportSummary;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

class InspectReportSectionTest {
    private static final Instant NOW = Instant.parse("2026-09-09T18:00:00Z");
    private static final UUID TARGET_ID = UUID.fromString("53000000-0000-0000-0000-000000000001");
    private static final UUID REPORTER_ID = UUID.fromString("53000000-0000-0000-0000-000000000002");
    private static final UUID ACTOR_ID = UUID.fromString("53000000-0000-0000-0000-000000000003");

    @Test
    void authorizedStaffSeeActiveReportDetailsAndExactCommands() {
        List<ReportSummary> reports = List.of(
                summary(10, ReportState.CLAIMED, Optional.of(ACTOR_ID)),
                summary(11, ReportState.OPEN, Optional.empty())
        );
        InspectReportSection section = section(() -> store(reports));

        List<Component> lines = section.render(TARGET_ID, true);

        assertEquals(3, lines.size());
        assertTrue(plain(lines.getFirst()).contains("Active reports: 2"));
        assertTrue(plain(lines.get(1)).contains("claimed"));
        assertTrue(plain(lines.get(2)).contains("open"));
        assertEquals(
                List.of(
                        "/reports",
                        "/reports view " + reports.getFirst().reportId(),
                        "/reports view " + reports.getLast().reportId()
                ),
                clickCommands(lines)
        );
    }

    @Test
    void resultRenderingIsBoundedAndSignalsAdditionalReports() {
        List<ReportSummary> reports = List.of(
                summary(20, ReportState.OPEN, Optional.empty()),
                summary(21, ReportState.OPEN, Optional.empty()),
                summary(22, ReportState.OPEN, Optional.empty()),
                summary(23, ReportState.OPEN, Optional.empty()),
                summary(24, ReportState.OPEN, Optional.empty()),
                summary(25, ReportState.OPEN, Optional.empty())
        );
        InspectReportSection section = section(() -> store(reports));

        List<Component> lines = section.render(TARGET_ID, true);

        assertEquals(6, lines.size());
        assertTrue(plain(lines.getFirst()).contains("Active reports: 5+"));
        assertFalse(plain(lines.getLast()).contains(reports.getLast().reportId().toString()));
    }

    @Test
    void reportStorageIsNotQueriedWithoutManagePermission() {
        AtomicBoolean requested = new AtomicBoolean();
        InspectReportSection section = section(() -> {
            requested.set(true);
            return store(List.of());
        });

        assertTrue(section.render(TARGET_ID, false).isEmpty());
        assertFalse(requested.get());
    }

    @Test
    void storageFailureDoesNotBreakTheRestOfTheInspector() {
        InspectReportSection section = section(() -> {
            throw new IllegalStateException("database unavailable");
        });

        List<Component> lines = section.render(TARGET_ID, true);

        assertEquals(1, lines.size());
        assertEquals("Reports: unavailable", plain(lines.getFirst()));
    }

    private static InspectReportSection section(java.util.function.Supplier<ReportStore> reports) {
        Logger logger = Logger.getAnonymousLogger();
        logger.setLevel(Level.OFF);
        return new InspectReportSection(reports, logger);
    }

    private static ReportStore store(List<ReportSummary> reports) {
        return (ReportStore) Proxy.newProxyInstance(
                Thread.currentThread().getContextClassLoader(),
                new Class<?>[]{ReportStore.class},
                (proxy, method, arguments) -> {
                    if ("listActiveForTarget".equals(method.getName())) {
                        assertEquals(TARGET_ID, arguments[0]);
                        assertEquals(6, arguments[1]);
                        return reports;
                    }
                    throw new AssertionError("Unexpected method: " + method.getName());
                }
        );
    }

    private static ReportSummary summary(int suffix, ReportState state, Optional<UUID> assignedTo) {
        UUID reportId = UUID.fromString("53000000-0000-0000-0000-0000000000" + suffix);
        return new ReportSummary(
                reportId,
                REPORTER_ID,
                TARGET_ID,
                "chat.abuse",
                state,
                assignedTo,
                "paper-survival",
                NOW.minusSeconds(60),
                NOW,
                2L
        );
    }

    private static List<String> clickCommands(List<Component> lines) {
        List<String> commands = new ArrayList<>();
        lines.forEach(line -> collect(line, commands));
        return commands;
    }

    private static void collect(Component component, List<String> commands) {
        ClickEvent event = component.clickEvent();
        if (event != null) {
            assertEquals(ClickEvent.Action.RUN_COMMAND, event.action());
            commands.add(((ClickEvent.Payload.Text) event.payload()).value());
        }
        component.children().forEach(child -> collect(child, commands));
    }

    private static String plain(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }
}
