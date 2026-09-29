package net.enthusia.staff.velocity;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
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

final class VelocityNetworkVerifier {
    static final String VERIFY_REQUEST = "VERIFY_REQUEST";
    static final String VERIFY_REPORT = "VERIFY_REPORT";
    private static final Duration BACKEND_TIMEOUT = Duration.ofSeconds(2);

    private final Dependencies dependencies;
    private final ObjectMapper json = new ObjectMapper();
    private final Clock clock;
    private final VelocityVerificationRenderer renderer = new VelocityVerificationRenderer();
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
            if (envelope.serverId().equals(report.backendId())) {
                reports.put(report.backendId(), new TimedReport(report, clock.instant()));
            }
        } catch (RuntimeException | java.io.IOException ignored) {
            // The authenticated frame is consumed, but malformed content never becomes readiness evidence.
        }
        return true;
    }

    List<Component> verify() {
        NetworkVerificationState.Snapshot snapshot = snapshot();
        Map<String, BackendVerificationReport> backendReports = collectBackendReports(snapshot, clock.instant());
        VerificationProbeConfiguration probes = probeConfiguration();
        ExternalReadinessProbe.Result staffBot = probe(probes.staffBotReadyUrl(), probes.timeoutMillis());
        ExternalReadinessProbe.Result website = probe(probes.websiteReadyUrl(), probes.timeoutMillis());
        return renderer.render(snapshot, backendReports, staffBot, website, cutoverView(snapshot.runtime()));
    }

    private NetworkVerificationState.Snapshot snapshot() {
        VelocityConfiguration configuration = dependencies.configuration().get();
        PersistentChannelServer channel = dependencies.channel().get();
        Set<String> expected = configuration == null ? Set.of() : configuration.backendSecretEnvironments().keySet();
        Set<String> connected = channel == null ? Set.of() : channel.connectedServers();
        return new NetworkVerificationState.Snapshot(
                dependencies.mode().get(),
                dependencies.runtime().get(),
                channel,
                expected,
                connected,
                dependencies.networkIdentityReady().getAsBoolean(),
                dependencies.discordWebhookReady().getAsBoolean(),
                dependencies.websiteBridgeReady().getAsBoolean()
        );
    }

    private Map<String, BackendVerificationReport> collectBackendReports(
            NetworkVerificationState.Snapshot snapshot,
            Instant started
    ) {
        if (snapshot.channel() == null || snapshot.expectedBackends().isEmpty()) {
            return Map.of();
        }
        requestReports(snapshot);
        waitForReports(snapshot.expectedBackends(), started);
        return freshReports(snapshot.expectedBackends(), started);
    }

    private void requestReports(NetworkVerificationState.Snapshot snapshot) {
        for (String backend : snapshot.expectedBackends()) {
            var delivery = snapshot.channel().send(
                    backend,
                    UUID.randomUUID(),
                    VERIFY_REQUEST,
                    "{}",
                    BACKEND_TIMEOUT
            );
            if (delivery.getNow(null) == PersistentChannelServer.DeliveryStatus.NOT_CONNECTED) {
                reports.remove(backend);
            }
        }
    }

    private void waitForReports(Set<String> expected, Instant started) {
        long deadline = System.nanoTime() + BACKEND_TIMEOUT.toNanos();
        while (System.nanoTime() < deadline && !hasFreshReports(expected, started)) {
            if (!sleepBriefly()) {
                return;
            }
        }
    }

    private static boolean sleepBriefly() {
        try {
            Thread.sleep(25L);
            return true;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private boolean hasFreshReports(Set<String> expected, Instant started) {
        return expected.stream().allMatch(backend -> isFresh(reports.get(backend), started));
    }

    private Map<String, BackendVerificationReport> freshReports(Set<String> expected, Instant started) {
        Map<String, BackendVerificationReport> fresh = new LinkedHashMap<>();
        for (String backend : expected) {
            TimedReport timed = reports.get(backend);
            if (isFresh(timed, started)) {
                fresh.put(backend, timed.report());
            }
        }
        return Map.copyOf(fresh);
    }

    private static boolean isFresh(TimedReport report, Instant started) {
        return report != null && !report.receivedAt().isBefore(started);
    }

    private VerificationProbeConfiguration probeConfiguration() {
        try {
            return VerificationProbeConfiguration.load(dependencies.dataDirectory());
        } catch (RuntimeException ignored) {
            return new VerificationProbeConfiguration(Optional.empty(), Optional.empty(), 1_500);
        }
    }

    private static ExternalReadinessProbe.Result probe(
            Optional<PrivateReadinessEndpoint> endpoint,
            int timeoutMillis
    ) {
        return ExternalReadinessProbe.probe(endpoint.orElse(null), timeoutMillis);
    }

    private static NetworkVerificationState.Cutover cutoverView(MariaDbRuntime runtime) {
        if (runtime == null) {
            return new NetworkVerificationState.Cutover(false, false, List.of("MariaDB runtime is unavailable"));
        }
        try {
            var coordinator = runtime.cutoverCoordinator();
            CutoverAssessment assessment = coordinator.assess(Optional.empty());
            return new NetworkVerificationState.Cutover(
                    assessment.allowed(),
                    coordinator.latestEvidence().isPresent(),
                    assessment.blockers()
            );
        } catch (RuntimeException exception) {
            return new NetworkVerificationState.Cutover(
                    false,
                    false,
                    List.of("Cutover evidence could not be read safely")
            );
        }
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

    private record TimedReport(BackendVerificationReport report, Instant receivedAt) {
    }
}
