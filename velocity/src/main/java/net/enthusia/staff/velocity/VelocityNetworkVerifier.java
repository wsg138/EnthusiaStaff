package net.enthusia.staff.velocity;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import net.enthusia.staff.domain.OperationalMode;
import net.enthusia.staff.domain.migration.CutoverAssessment;
import net.enthusia.staff.persistence.MariaDbRuntime;
import net.enthusia.staff.protocol.BackendVerificationReport;
import net.enthusia.staff.protocol.PersistentChannelServer;
import net.enthusia.staff.protocol.ProtocolEnvelope;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

final class VelocityNetworkVerifier {
    static final String VERIFY_REQUEST = "VERIFY_REQUEST";
    static final String VERIFY_REPORT = "VERIFY_REPORT";
    private static final Duration BACKEND_TIMEOUT = Duration.ofSeconds(2);
    private static final int MAX_BLOCKERS_SHOWN = 5;
    private static final List<String> IMPORTANT_PROVIDERS =
            List.of("Currency", "Market", "Reputation", "RoseChat", "Enthusia AutoClicker");

    private final Dependencies dependencies;
    private final ObjectMapper json = new ObjectMapper();
    private final Clock clock;
    private final Map<String, TimedReport> reports = new ConcurrentHashMap<>();

    VelocityNetworkVerifier(Dependencies dependencies) {
        this(dependencies, Clock.systemUTC());
    }

    VelocityNetworkVerifier(Dependencies dependencies, Clock clock) {
        this.dependencies = java.util.Objects.requireNonNull(dependencies, "dependencies");
        this.clock = java.util.Objects.requireNonNull(clock, "clock");
    }

    boolean acceptReport(ProtocolEnvelope envelope) {
        if (!VERIFY_REPORT.equals(envelope.messageType())) {
            return false;
        }
        try {
            BackendVerificationReport report = json.readValue(
                    envelope.payloadJson(), BackendVerificationReport.class
            );
            if (!envelope.serverId().equals(report.backendId())) {
                return true;
            }
            reports.put(report.backendId(), new TimedReport(report, clock.instant()));
        } catch (RuntimeException | java.io.IOException ignored) {
            // The authenticated frame is consumed, but malformed report content never becomes trusted readiness evidence.
        }
        return true;
    }

    List<Component> verify() {
        Snapshot snapshot = snapshot();
        Instant started = clock.instant();
        Map<String, BackendVerificationReport> backendReports = collectBackendReports(snapshot, started);
        VerificationProbeConfiguration probes = probeConfiguration();
        ExternalReadinessProbe.Result staffBot = probe(probes.staffBotReadyUrl(), probes.timeoutMillis());
        ExternalReadinessProbe.Result website = probe(probes.websiteReadyUrl(), probes.timeoutMillis());
        CutoverView cutover = cutoverView(snapshot.runtime());
        return render(snapshot, backendReports, staffBot, website, cutover);
    }

    private Snapshot snapshot() {
        VelocityConfiguration configuration = dependencies.configuration().get();
        PersistentChannelServer channel = dependencies.channel().get();
        Set<String> expected = configuration == null
                ? Set.of()
                : configuration.backendSecretEnvironments().keySet();
        Set<String> connected = channel == null ? Set.of() : channel.connectedServers();
        return new Snapshot(
                dependencies.mode().get(),
                dependencies.runtime().get(),
                configuration,
                channel,
                Set.copyOf(expected),
                Set.copyOf(connected),
                dependencies.networkIdentityReady().getAsBoolean(),
                dependencies.discordWebhookReady().getAsBoolean(),
                dependencies.websiteBridgeReady().getAsBoolean()
        );
    }

    private Map<String, BackendVerificationReport> collectBackendReports(
            Snapshot snapshot,
            Instant started
    ) {
        if (snapshot.channel() == null || snapshot.expectedBackends().isEmpty()) {
            return Map.of();
        }
        List<CompletableFuture<PersistentChannelServer.DeliveryStatus>> requests = new ArrayList<>();
        for (String backend : snapshot.expectedBackends()) {
            requests.add(snapshot.channel().send(
                    backend,
                    UUID.randomUUID(),
                    VERIFY_REQUEST,
                    "{}",
                    BACKEND_TIMEOUT
            ));
        }
        awaitRequests(requests);
        waitForReports(snapshot.expectedBackends(), started);
        Map<String, BackendVerificationReport> fresh = new java.util.LinkedHashMap<>();
        for (String backend : snapshot.expectedBackends()) {
            TimedReport timed = reports.get(backend);
            if (timed != null && !timed.receivedAt().isBefore(started)) {
                fresh.put(backend, timed.report());
            }
        }
        return Map.copyOf(fresh);
    }

    private static void awaitRequests(
            List<CompletableFuture<PersistentChannelServer.DeliveryStatus>> requests
    ) {
        try {
            CompletableFuture.allOf(requests.toArray(CompletableFuture[]::new))
                    .orTimeout(BACKEND_TIMEOUT.toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS)
                    .join();
        } catch (RuntimeException ignored) {
            // Missing acknowledgements are reflected as missing fresh backend reports below.
        }
    }

    private void waitForReports(Set<String> expected, Instant started) {
        long deadline = System.nanoTime() + BACKEND_TIMEOUT.toNanos();
        while (System.nanoTime() < deadline && !hasFreshReports(expected, started)) {
            try {
                Thread.sleep(25L);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private boolean hasFreshReports(Set<String> expected, Instant started) {
        for (String backend : expected) {
            TimedReport report = reports.get(backend);
            if (report == null || report.receivedAt().isBefore(started)) {
                return false;
            }
        }
        return true;
    }

    private VerificationProbeConfiguration probeConfiguration() {
        try {
            return VerificationProbeConfiguration.load(dependencies.dataDirectory());
        } catch (RuntimeException ignored) {
            return new VerificationProbeConfiguration(Optional.empty(), Optional.empty(), 1_500);
        }
    }

    private static ExternalReadinessProbe.Result probe(Optional<java.net.URI> endpoint, int timeoutMillis) {
        return ExternalReadinessProbe.probe(endpoint.orElse(null), timeoutMillis);
    }

    private static CutoverView cutoverView(MariaDbRuntime runtime) {
        if (runtime == null) {
            return new CutoverView(false, false, List.of("MariaDB runtime is unavailable"));
        }
        try {
            var coordinator = runtime.cutoverCoordinator();
            CutoverAssessment assessment = coordinator.assess(Optional.empty());
            return new CutoverView(
                    assessment.allowed(),
                    coordinator.latestEvidence().isPresent(),
                    assessment.blockers()
            );
        } catch (RuntimeException exception) {
            return new CutoverView(false, false, List.of("Cutover evidence could not be read safely"));
        }
    }

    private List<Component> render(
            Snapshot snapshot,
            Map<String, BackendVerificationReport> backendReports,
            ExternalReadinessProbe.Result staffBot,
            ExternalReadinessProbe.Result website,
            CutoverView cutover
    ) {
        List<Component> lines = new ArrayList<>();
        lines.add(header("EnthusiaStaff • Network Verify"));
        lines.add(modeLine(snapshot.mode()));
        appendCore(lines, snapshot);
        appendBackends(lines, snapshot, backendReports);
        appendProviders(lines, snapshot, backendReports);
        appendExternal(lines, snapshot, staffBot, website);
        appendCutover(lines, cutover);
        appendConclusion(lines, snapshot, backendReports, cutover);
        return List.copyOf(lines);
    }

    private static void appendCore(List<Component> lines, Snapshot snapshot) {
        lines.add(section("Core"));
        lines.add(status(snapshot.runtime() != null, "MariaDB", "connected", "runtime unavailable"));
        boolean allConnected = !snapshot.expectedBackends().isEmpty()
                && snapshot.connectedBackends().containsAll(snapshot.expectedBackends());
        lines.add(status(
                allConnected,
                "Velocity channel",
                snapshot.connectedBackends().size() + "/" + snapshot.expectedBackends().size() + " backends",
                snapshot.connectedBackends().size() + "/" + snapshot.expectedBackends().size() + " backends connected"
        ));
        lines.add(status(
                snapshot.networkIdentityReady(),
                "Protected identity",
                "enabled",
                "not ready"
        ));
    }

    private static void appendBackends(
            List<Component> lines,
            Snapshot snapshot,
            Map<String, BackendVerificationReport> reports
    ) {
        lines.add(section("Backends"));
        if (snapshot.expectedBackends().isEmpty()) {
            lines.add(warning("Backends", "none configured"));
            return;
        }
        snapshot.expectedBackends().stream().sorted().forEach(backend -> {
            if (!snapshot.connectedBackends().contains(backend)) {
                lines.add(critical(backend, "not authenticated / connected"));
                return;
            }
            BackendVerificationReport report = reports.get(backend);
            if (report == null) {
                lines.add(warning(backend, "connected, but no fresh verification report"));
            } else if (!report.storageReady()) {
                lines.add(critical(backend, "Staff storage is not ready"));
            } else {
                lines.add(pass(backend, report.operationalMode() + " / report fresh"));
            }
        });
    }

    private static void appendProviders(
            List<Component> lines,
            Snapshot snapshot,
            Map<String, BackendVerificationReport> reports
    ) {
        lines.add(section("Provider APIs"));
        for (String provider : IMPORTANT_PROVIDERS) {
            lines.add(providerLine(provider, snapshot.expectedBackends(), reports));
        }
        long reported = reports.size();
        lines.add(note("Optional extras", reported + " backend report(s); detailed optional state stays local"));
    }

    private static Component providerLine(
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
        if (pass == expected.size() && !expected.isEmpty()) {
            return pass(provider, pass + "/" + expected.size() + " healthy");
        }
        if (problems.isEmpty()) {
            return disabled(provider, "optional / not installed on required backends");
        }
        return warning(provider, pass + "/" + expected.size() + " healthy; " + problems.getFirst());
    }

    private static void appendExternal(
            List<Component> lines,
            Snapshot snapshot,
            ExternalReadinessProbe.Result staffBot,
            ExternalReadinessProbe.Result website
    ) {
        lines.add(section("Discord & Website"));
        lines.add(external("Staff Bot", staffBot));
        lines.add(status(
                snapshot.discordWebhookReady(),
                "Discord webhooks",
                "durable delivery worker running",
                "delivery worker disabled / unavailable"
        ));
        lines.add(status(
                snapshot.websiteBridgeReady(),
                "Website bridge",
                "private Staff bridge listening",
                "private Staff bridge unavailable"
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

    private static void appendCutover(List<Component> lines, CutoverView cutover) {
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
            Snapshot snapshot,
            Map<String, BackendVerificationReport> backendReports,
            CutoverView cutover
    ) {
        List<String> blockers = blockers(snapshot, backendReports, cutover);
        if (!blockers.isEmpty()) {
            lines.add(section("Why not ACTIVE"));
            for (int index = 0; index < Math.min(MAX_BLOCKERS_SHOWN, blockers.size()); index++) {
                lines.add(Component.text("  " + (index + 1) + ". ", NamedTextColor.RED)
                        .append(Component.text(shorten(blockers.get(index)), NamedTextColor.GRAY)));
            }
            if (blockers.size() > MAX_BLOCKERS_SHOWN) {
                lines.add(Component.text("  … " + (blockers.size() - MAX_BLOCKERS_SHOWN)
                        + " more; see /estaff cutover status", NamedTextColor.DARK_GRAY));
            }
        }
        lines.add(Component.text("────────────────────────", NamedTextColor.DARK_GRAY));
        if (snapshot.mode() == OperationalMode.ACTIVE && blockers.isEmpty()) {
            lines.add(Component.text("✔ ACTIVE • NETWORK HEALTHY", NamedTextColor.GREEN, TextDecoration.BOLD));
        } else if (blockers.isEmpty()) {
            lines.add(Component.text("✔ READY FOR ACTIVE TESTING", NamedTextColor.GREEN, TextDecoration.BOLD));
        } else {
            lines.add(Component.text("✖ NOT READY FOR ACTIVE TESTING", NamedTextColor.RED, TextDecoration.BOLD));
        }
    }

    private static List<String> blockers(
            Snapshot snapshot,
            Map<String, BackendVerificationReport> reports,
            CutoverView cutover
    ) {
        LinkedHashSet<String> blockers = new LinkedHashSet<>();
        if (snapshot.runtime() == null) {
            blockers.add("MariaDB runtime is unavailable");
        }
        for (String backend : snapshot.expectedBackends()) {
            if (!snapshot.connectedBackends().contains(backend)) {
                blockers.add(backend + " is not authenticated and connected");
            } else if (!reports.containsKey(backend)) {
                blockers.add(backend + " did not return a fresh verification report");
            }
        }
        if (!snapshot.networkIdentityReady()) {
            blockers.add("Protected network identity support is not ready");
        }
        cutover.blockers().stream().filter(value -> value != null && !value.isBlank()).forEach(blockers::add);
        return blockers.stream().sorted(Comparator.naturalOrder()).toList();
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

    record Dependencies(
            Supplier<OperationalMode> mode,
            Supplier<MariaDbRuntime> runtime,
            Supplier<VelocityConfiguration> configuration,
            Supplier<PersistentChannelServer> channel,
            BooleanSupplier networkIdentityReady,
            BooleanSupplier discordWebhookReady,
            BooleanSupplier websiteBridgeReady,
            java.nio.file.Path dataDirectory
    ) {
        Dependencies {
            java.util.Objects.requireNonNull(mode, "mode");
            java.util.Objects.requireNonNull(runtime, "runtime");
            java.util.Objects.requireNonNull(configuration, "configuration");
            java.util.Objects.requireNonNull(channel, "channel");
            java.util.Objects.requireNonNull(networkIdentityReady, "networkIdentityReady");
            java.util.Objects.requireNonNull(discordWebhookReady, "discordWebhookReady");
            java.util.Objects.requireNonNull(websiteBridgeReady, "websiteBridgeReady");
            java.util.Objects.requireNonNull(dataDirectory, "dataDirectory");
        }
    }

    private record Snapshot(
            OperationalMode mode,
            MariaDbRuntime runtime,
            VelocityConfiguration configuration,
            PersistentChannelServer channel,
            Set<String> expectedBackends,
            Set<String> connectedBackends,
            boolean networkIdentityReady,
            boolean discordWebhookReady,
            boolean websiteBridgeReady
    ) {
    }

    private record TimedReport(BackendVerificationReport report, Instant receivedAt) {
    }

    private record CutoverView(boolean allowed, boolean evidencePresent, List<String> blockers) {
        CutoverView {
            blockers = blockers == null ? List.of() : List.copyOf(blockers);
        }
    }
}
