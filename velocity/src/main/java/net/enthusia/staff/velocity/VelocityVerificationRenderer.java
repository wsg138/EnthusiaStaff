package net.enthusia.staff.velocity;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.enthusia.staff.domain.OperationalMode;
import net.enthusia.staff.protocol.BackendVerificationReport;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

final class VelocityVerificationRenderer {
    private static final int MAX_BLOCKERS_SHOWN = 5;
    private static final List<String> IMPORTANT_PROVIDERS =
            List.of("Currency", "Market", "Reputation", "RoseChat", "Enthusia AutoClicker");

    List<Component> render(
            NetworkVerificationState.Snapshot snapshot,
            Map<String, BackendVerificationReport> backendReports,
            ExternalReadinessProbe.Result staffBot,
            ExternalReadinessProbe.Result website,
            NetworkVerificationState.Cutover cutover
    ) {
        List<Component> lines = new ArrayList<>();
        lines.add(header("EnthusiaStaff • Network Verify"));
        lines.add(modeLine(snapshot.mode()));
        appendCore(lines, snapshot);
        appendBackends(lines, snapshot, backendReports);
        appendProviders(lines, snapshot.expectedBackends(), backendReports);
        appendExternal(lines, snapshot, staffBot, website);
        appendCutover(lines, cutover);
        appendConclusion(lines, snapshot, backendReports, cutover);
        return List.copyOf(lines);
    }

    private static void appendCore(List<Component> lines, NetworkVerificationState.Snapshot snapshot) {
        lines.add(section("Core"));
        lines.add(status(snapshot.runtime() != null, "MariaDB", "connected", "runtime unavailable"));
        int expected = snapshot.expectedBackends().size();
        int connected = snapshot.connectedBackends().size();
        boolean allConnected = expected > 0 && snapshot.connectedBackends().containsAll(snapshot.expectedBackends());
        lines.add(status(
                allConnected,
                "Velocity channel",
                connected + "/" + expected + " backends",
                connected + "/" + expected + " backends connected"
        ));
        lines.add(status(snapshot.networkIdentityReady(), "Protected identity", "enabled", "not ready"));
    }

    private static void appendBackends(
            List<Component> lines,
            NetworkVerificationState.Snapshot snapshot,
            Map<String, BackendVerificationReport> reports
    ) {
        lines.add(section("Backends"));
        if (snapshot.expectedBackends().isEmpty()) {
            lines.add(warning("Backends", "none configured"));
            return;
        }
        snapshot.expectedBackends().stream().sorted().forEach(backend ->
                lines.add(backendLine(backend, snapshot.connectedBackends(), reports)));
    }

    private static Component backendLine(
            String backend,
            Set<String> connected,
            Map<String, BackendVerificationReport> reports
    ) {
        if (!connected.contains(backend)) {
            return critical(backend, "not authenticated / connected");
        }
        BackendVerificationReport report = reports.get(backend);
        if (report == null) {
            return warning(backend, "connected, but no fresh verification report");
        }
        if (!report.storageReady()) {
            return critical(backend, "Staff storage is not ready");
        }
        return pass(backend, report.operationalMode() + " / report fresh");
    }

    private static void appendProviders(
            List<Component> lines,
            Set<String> expectedBackends,
            Map<String, BackendVerificationReport> reports
    ) {
        lines.add(section("Provider APIs"));
        IMPORTANT_PROVIDERS.forEach(provider -> lines.add(providerLine(provider, expectedBackends, reports)));
        lines.add(note("Optional extras", reports.size() + " backend report(s); details stay local"));
    }

    private static Component providerLine(
            String provider,
            Set<String> expected,
            Map<String, BackendVerificationReport> reports
    ) {
        ProviderSummary summary = providerSummary(provider, expected, reports);
        if (summary.passCount() == expected.size() && !expected.isEmpty()) {
            return pass(provider, summary.passCount() + "/" + expected.size() + " healthy");
        }
        if (summary.problems().isEmpty()) {
            return disabled(provider, "optional / not installed on required backends");
        }
        return warning(
                provider,
                summary.passCount() + "/" + expected.size() + " healthy; " + summary.problems().getFirst()
        );
    }

    private static ProviderSummary providerSummary(
            String provider,
            Set<String> expected,
            Map<String, BackendVerificationReport> reports
    ) {
        int pass = 0;
        List<String> problems = new ArrayList<>();
        for (String backend : expected) {
            BackendVerificationReport report = reports.get(backend);
            BackendVerificationReport.Check check = report == null ? null : report.integrations().get(provider);
            if (check == null) {
                problems.add(backend + ": unverified");
            } else if (check.state() == BackendVerificationReport.State.PASS) {
                pass++;
            } else if (check.state() != BackendVerificationReport.State.DISABLED) {
                problems.add(backend + ": " + shorten(check.detail()));
            }
        }
        return new ProviderSummary(pass, List.copyOf(problems));
    }

    private static void appendExternal(
            List<Component> lines,
            NetworkVerificationState.Snapshot snapshot,
            ExternalReadinessProbe.Result staffBot,
            ExternalReadinessProbe.Result website
    ) {
        lines.add(section("Discord & Website"));
        lines.add(external("Staff Bot", staffBot));
        lines.add(status(
                snapshot.discordWebhookReady(),
                "Discord webhooks",
                "delivery worker running",
                "delivery worker unavailable"
        ));
        lines.add(status(
                snapshot.websiteBridgeReady(),
                "Website bridge",
                "private bridge listening",
                "private bridge unavailable"
        ));
        lines.add(external("Public website", website));
    }

    private static Component external(String label, ExternalReadinessProbe.Result result) {
        return switch (result.state()) {
            case PASS -> pass(label, result.detail());
            case WARNING -> warning(label, result.detail());
            case DISABLED -> disabled(label, result.detail());
        };
    }

    private static void appendCutover(List<Component> lines, NetworkVerificationState.Cutover cutover) {
        lines.add(section("Migration / Cutover"));
        lines.add(cutover.evidencePresent()
                ? pass("Shadow evidence", "durable comparison evidence found")
                : warning("Shadow evidence", "no complete evidence available"));
        lines.add(cutover.allowed()
                ? pass("Cutover gate", "all current blockers cleared")
                : warning("Cutover gate", cutover.blockers().size() + " blocker(s)"));
    }

    private static void appendConclusion(
            List<Component> lines,
            NetworkVerificationState.Snapshot snapshot,
            Map<String, BackendVerificationReport> reports,
            NetworkVerificationState.Cutover cutover
    ) {
        List<String> blockers = blockers(snapshot, reports, cutover);
        appendBlockers(lines, blockers);
        lines.add(Component.text("────────────────────────", NamedTextColor.DARK_GRAY));
        lines.add(verdict(snapshot.mode(), blockers));
    }

    private static void appendBlockers(List<Component> lines, List<String> blockers) {
        if (blockers.isEmpty()) {
            return;
        }
        lines.add(section("Why not ACTIVE"));
        int shown = Math.min(MAX_BLOCKERS_SHOWN, blockers.size());
        for (int index = 0; index < shown; index++) {
            lines.add(Component.text("  " + (index + 1) + ". ", NamedTextColor.RED)
                    .append(Component.text(shorten(blockers.get(index)), NamedTextColor.GRAY)));
        }
        if (blockers.size() > shown) {
            lines.add(Component.text(
                    "  … " + (blockers.size() - shown) + " more; see /estaff cutover status",
                    NamedTextColor.DARK_GRAY
            ));
        }
    }

    private static Component verdict(OperationalMode mode, List<String> blockers) {
        if (mode == OperationalMode.ACTIVE && blockers.isEmpty()) {
            return Component.text("✔ ACTIVE • NETWORK HEALTHY", NamedTextColor.GREEN, TextDecoration.BOLD);
        }
        if (blockers.isEmpty()) {
            return Component.text("✔ READY FOR ACTIVE TESTING", NamedTextColor.GREEN, TextDecoration.BOLD);
        }
        return Component.text("✖ NOT READY FOR ACTIVE TESTING", NamedTextColor.RED, TextDecoration.BOLD);
    }

    private static List<String> blockers(
            NetworkVerificationState.Snapshot snapshot,
            Map<String, BackendVerificationReport> reports,
            NetworkVerificationState.Cutover cutover
    ) {
        LinkedHashSet<String> blockers = new LinkedHashSet<>();
        if (snapshot.runtime() == null) {
            blockers.add("MariaDB runtime is unavailable");
        }
        appendBackendBlockers(blockers, snapshot, reports);
        if (!snapshot.networkIdentityReady()) {
            blockers.add("Protected network identity support is not ready");
        }
        cutover.blockers().stream().filter(value -> value != null && !value.isBlank()).forEach(blockers::add);
        return blockers.stream().sorted(Comparator.naturalOrder()).toList();
    }

    private static void appendBackendBlockers(
            Set<String> blockers,
            NetworkVerificationState.Snapshot snapshot,
            Map<String, BackendVerificationReport> reports
    ) {
        for (String backend : snapshot.expectedBackends()) {
            if (!snapshot.connectedBackends().contains(backend)) {
                blockers.add(backend + " is not authenticated and connected");
            } else if (!reports.containsKey(backend)) {
                blockers.add(backend + " did not return a fresh verification report");
            }
        }
    }

    private static Component modeLine(OperationalMode mode) {
        NamedTextColor color = switch (mode) {
            case ACTIVE -> NamedTextColor.GREEN;
            case SHADOW_MIGRATION -> NamedTextColor.YELLOW;
            case DEGRADED, READ_ONLY_FAILURE -> NamedTextColor.RED;
            default -> NamedTextColor.GOLD;
        };
        return Component.text("Mode: ", NamedTextColor.GRAY)
                .append(Component.text(mode.name(), color, TextDecoration.BOLD));
    }

    private static Component header(String value) {
        return Component.text("──────── ", NamedTextColor.DARK_GRAY)
                .append(Component.text(value, NamedTextColor.AQUA, TextDecoration.BOLD))
                .append(Component.text(" ────────", NamedTextColor.DARK_GRAY));
    }

    private static Component section(String value) {
        return Component.text("▸ ", NamedTextColor.GOLD)
                .append(Component.text(value, NamedTextColor.YELLOW, TextDecoration.BOLD));
    }

    private static Component status(boolean healthy, String label, String passDetail, String failDetail) {
        return healthy ? pass(label, passDetail) : critical(label, failDetail);
    }

    private static Component pass(String label, String detail) {
        return line("✔", NamedTextColor.GREEN, label, detail, NamedTextColor.GRAY);
    }

    private static Component warning(String label, String detail) {
        return line("⚠", NamedTextColor.YELLOW, label, detail, NamedTextColor.GRAY);
    }

    private static Component disabled(String label, String detail) {
        return line("○", NamedTextColor.DARK_GRAY, label, detail, NamedTextColor.DARK_GRAY);
    }

    private static Component critical(String label, String detail) {
        return line("✖", NamedTextColor.RED, label, detail, NamedTextColor.RED);
    }

    private static Component note(String label, String detail) {
        return line("•", NamedTextColor.GRAY, label, detail, NamedTextColor.DARK_GRAY);
    }

    private static Component line(
            String symbol,
            NamedTextColor symbolColor,
            String label,
            String detail,
            NamedTextColor detailColor
    ) {
        return Component.text("  " + symbol + " ", symbolColor)
                .append(Component.text(label, NamedTextColor.WHITE))
                .append(Component.text(" — ", NamedTextColor.DARK_GRAY))
                .append(Component.text(shorten(detail), detailColor));
    }

    private static String shorten(String value) {
        if (value == null || value.isBlank()) {
            return "no detail";
        }
        String singleLine = value.replace('\n', ' ').trim();
        return singleLine.length() <= 96 ? singleLine : singleLine.substring(0, 93) + "...";
    }

    private record ProviderSummary(int passCount, List<String> problems) {
    }
}
