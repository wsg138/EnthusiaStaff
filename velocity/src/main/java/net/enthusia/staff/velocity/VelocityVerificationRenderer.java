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
            NetworkVerificationState.Cutover cutover
    ) {
        List<Component> lines = new ArrayList<>();
        lines.add(VelocityMessageStyle.header("EnthusiaStaff • Network Verify"));
        lines.add(modeLine(snapshot.mode()));
        appendCore(lines, snapshot);
        appendBackends(lines, snapshot, backendReports);
        appendProviders(lines, snapshot.expectedBackends(), backendReports);
        appendDiscordAndWebsite(lines, snapshot);
        appendCutover(lines, cutover);
        appendConclusion(lines, snapshot, backendReports, cutover);
        return List.copyOf(lines);
    }

    private static void appendCore(List<Component> lines, NetworkVerificationState.Snapshot snapshot) {
        lines.add(VelocityMessageStyle.section("Core"));
        lines.add(status(snapshot.runtime() != null, "MariaDB", "Connected", "Runtime unavailable"));
        int expected = snapshot.expectedBackends().size();
        int connected = snapshot.connectedBackends().size();
        boolean allConnected = expected > 0 && snapshot.connectedBackends().containsAll(snapshot.expectedBackends());
        lines.add(status(
                allConnected,
                "Velocity channel",
                connected + "/" + expected + " connected",
                connected + "/" + expected + " backends connected"
        ));
        lines.add(status(snapshot.networkIdentityReady(), "Protected identity", "Enabled", "Not ready"));
    }

    private static void appendBackends(
            List<Component> lines,
            NetworkVerificationState.Snapshot snapshot,
            Map<String, BackendVerificationReport> reports
    ) {
        lines.add(VelocityMessageStyle.section("Backends"));
        if (snapshot.expectedBackends().isEmpty()) {
            lines.add(warning("Backends", "Disabled", "None configured"));
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
            return critical(backend, "Offline", "Not authenticated / connected");
        }
        BackendVerificationReport report = reports.get(backend);
        if (report == null) {
            return warning(backend, "Unverified", "Connected, but no fresh verification report");
        }
        if (!report.storageReady()) {
            return critical(backend, "Failed", "Staff storage is not ready");
        }
        return pass(backend, "Healthy", report.operationalMode() + " / report fresh");
    }

    private static void appendProviders(
            List<Component> lines,
            Set<String> expectedBackends,
            Map<String, BackendVerificationReport> reports
    ) {
        lines.add(VelocityMessageStyle.section("Provider APIs"));
        if (expectedBackends.isEmpty()) {
            lines.add(warning("Provider APIs", "Unavailable", "No backend configured"));
            return;
        }
        IMPORTANT_PROVIDERS.forEach(provider -> lines.add(providerLine(provider, expectedBackends, reports)));
        lines.add(note("Optional extras", reports.size() + " backend report(s); details stay local"));
    }

    private static Component providerLine(
            String provider,
            Set<String> expected,
            Map<String, BackendVerificationReport> reports
    ) {
        ProviderSummary summary = providerSummary(provider, expected, reports);
        if (summary.presentCount() == 0 && summary.problems().isEmpty()) {
            return disabled(provider, "Optional / not installed on connected backends");
        }
        if (summary.problems().isEmpty()) {
            return pass(provider, "Healthy", summary.passCount() + " backend(s) healthy");
        }
        return warning(
                provider,
                "Warning",
                summary.passCount() + "/" + summary.presentCount() + " healthy; " + summary.problems().getFirst()
        );
    }

    private static ProviderSummary providerSummary(
            String provider,
            Set<String> expected,
            Map<String, BackendVerificationReport> reports
    ) {
        int present = 0;
        int pass = 0;
        List<String> problems = new ArrayList<>();
        for (String backend : expected) {
            BackendVerificationReport report = reports.get(backend);
            if (report == null) {
                problems.add(backend + ": unverified");
                continue;
            }
            BackendVerificationReport.Check check = report.integrations().get(provider);
            if (check == null || check.state() == BackendVerificationReport.State.DISABLED) {
                continue;
            }
            present++;
            if (check.state() == BackendVerificationReport.State.PASS) {
                pass++;
            } else {
                problems.add(backend + ": " + shorten(check.detail()));
            }
        }
        return new ProviderSummary(present, pass, List.copyOf(problems));
    }

    private static void appendDiscordAndWebsite(
            List<Component> lines,
            NetworkVerificationState.Snapshot snapshot
    ) {
        lines.add(VelocityMessageStyle.section("Discord & Website"));
        lines.add(snapshot.discordWebhookReady()
                ? pass("Discord webhooks", "Enabled", "Durable delivery worker running")
                : warning("Discord webhooks", "Disabled", "Durable delivery worker unavailable"));
        lines.add(snapshot.websiteBridgeReady()
                ? pass("Website bridge", "Enabled", "Private bridge listening")
                : warning("Website bridge", "Disabled", "Private bridge unavailable"));
    }

    private static void appendCutover(List<Component> lines, NetworkVerificationState.Cutover cutover) {
        lines.add(VelocityMessageStyle.section("Migration / Cutover"));
        if (cutover.committed()) {
            lines.add(pass("Authority cutover", "Committed", "durable activation receipt verified"));
            return;
        }
        lines.add(cutover.evidencePresent()
                ? pass("Shadow evidence", "Ready", "Durable comparison evidence found")
                : warning("Shadow evidence", "Pending", "No complete evidence available"));
        lines.add(cutover.allowed()
                ? pass("Cutover gate", "Ready", "All current blockers cleared")
                : warning("Cutover gate", "Gated", cutover.blockers().size() + " blocker(s)"));
    }

    private static void appendConclusion(
            List<Component> lines,
            NetworkVerificationState.Snapshot snapshot,
            Map<String, BackendVerificationReport> reports,
            NetworkVerificationState.Cutover cutover
    ) {
        List<String> blockers = blockers(snapshot, reports, cutover);
        appendBlockers(lines, blockers, snapshot.mode());
        lines.add(Component.text("────────────────────────", NamedTextColor.DARK_GRAY));
        lines.add(verdict(snapshot.mode(), blockers));
    }

    private static void appendBlockers(List<Component> lines, List<String> blockers, OperationalMode mode) {
        if (blockers.isEmpty()) {
            return;
        }
        lines.add(VelocityMessageStyle.section(mode == OperationalMode.ACTIVE ? "Outstanding checks" : "Why not ACTIVE"));
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
        if (mode == OperationalMode.ACTIVE) {
            return Component.text("⚠ ACTIVE • FOLLOW-UP REQUIRED", NamedTextColor.YELLOW, TextDecoration.BOLD);
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
        appendCoreBlockers(blockers, snapshot, reports);
        appendIntegrationBlockers(blockers, snapshot, reports);
        cutover.blockers().stream().filter(value -> value != null && !value.isBlank()).forEach(blockers::add);
        return blockers.stream().sorted(Comparator.naturalOrder()).toList();
    }

    private static void appendCoreBlockers(
            Set<String> blockers,
            NetworkVerificationState.Snapshot snapshot,
            Map<String, BackendVerificationReport> reports
    ) {
        if (snapshot.runtime() == null) {
            blockers.add("MariaDB runtime is unavailable");
        }
        appendBackendBlockers(blockers, snapshot, reports);
        if (!snapshot.networkIdentityReady()) {
            blockers.add("Protected network identity support is not ready");
        }
    }

    private static void appendIntegrationBlockers(
            Set<String> blockers,
            NetworkVerificationState.Snapshot snapshot,
            Map<String, BackendVerificationReport> reports
    ) {
        if (!snapshot.discordWebhookReady()) {
            blockers.add("Discord webhook delivery worker is unavailable");
        }
        if (!snapshot.websiteBridgeReady()) {
            blockers.add("Private website bridge is unavailable");
        }
        appendProviderProblems(blockers, snapshot.expectedBackends(), reports);
    }

    private static void appendProviderProblems(
            Set<String> blockers,
            Set<String> expectedBackends,
            Map<String, BackendVerificationReport> reports
    ) {
        for (String provider : IMPORTANT_PROVIDERS) {
            ProviderSummary summary = providerSummary(provider, expectedBackends, reports);
            if (summary.presentCount() > 0 && !summary.problems().isEmpty()) {
                blockers.add(provider + " API: " + summary.problems().getFirst());
            }
        }
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
        VelocityMessageStyle.Tone tone = switch (mode) {
            case ACTIVE -> VelocityMessageStyle.Tone.SUCCESS;
            case SHADOW_MIGRATION -> VelocityMessageStyle.Tone.WARNING;
            case DEGRADED, READ_ONLY_FAILURE -> VelocityMessageStyle.Tone.ERROR;
            default -> VelocityMessageStyle.Tone.WARNING;
        };
        return VelocityMessageStyle.statusRow(
                "Mode",
                VelocityMessageStyle.displayMode(mode),
                "Velocity authority",
                tone
        );
    }

    private static Component status(boolean healthy, String label, String passDetail, String failDetail) {
        return healthy
                ? pass(label, "Healthy", passDetail)
                : critical(label, "Failed", failDetail);
    }

    private static Component pass(String label, String status, String detail) {
        return VelocityMessageStyle.statusRow(label, status, detail, VelocityMessageStyle.Tone.SUCCESS);
    }

    private static Component warning(String label, String status, String detail) {
        return VelocityMessageStyle.statusRow(label, status, detail, VelocityMessageStyle.Tone.WARNING);
    }

    private static Component disabled(String label, String detail) {
        return VelocityMessageStyle.statusRow(label, "Optional", detail, VelocityMessageStyle.Tone.MUTED);
    }

    private static Component critical(String label, String status, String detail) {
        return VelocityMessageStyle.statusRow(label, status, detail, VelocityMessageStyle.Tone.ERROR);
    }

    private static Component note(String label, String detail) {
        return VelocityMessageStyle.statusRow(label, "Info", detail, VelocityMessageStyle.Tone.MUTED);
    }

    private static String shorten(String value) {
        if (value == null || value.isBlank()) {
            return "no detail";
        }
        String singleLine = value.replace('\n', ' ').trim();
        return singleLine.length() <= 96 ? singleLine : singleLine.substring(0, 93) + "...";
    }

    private record ProviderSummary(int presentCount, int passCount, List<String> problems) {
    }
}
