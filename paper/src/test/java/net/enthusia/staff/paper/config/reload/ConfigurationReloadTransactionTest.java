package net.enthusia.staff.paper.config.reload;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicInteger;
import net.enthusia.staff.domain.auth.StaffRank;
import net.enthusia.staff.domain.escalation.AltInheritanceMode;
import net.enthusia.staff.domain.escalation.PunishmentStep;
import net.enthusia.staff.domain.escalation.ReasonPolicy;
import net.enthusia.staff.domain.ports.PlayerDirectory;
import net.enthusia.staff.domain.ports.PunishmentRequestAlertStore;
import net.enthusia.staff.domain.ports.PunishmentRequestStore;
import net.enthusia.staff.domain.sanction.SanctionLength;
import net.enthusia.staff.domain.sanction.SanctionSpec;
import net.enthusia.staff.domain.sanction.SanctionType;
import net.enthusia.staff.paper.alert.PunishmentRequestAlertController;
import net.enthusia.staff.paper.alert.PunishmentRequestAlertManagedLifecycle;
import net.enthusia.staff.paper.alert.PunishmentRequestAlertWorkerSettings;
import net.enthusia.staff.paper.config.PaperConfigurationSnapshot;
import net.enthusia.staff.paper.config.ReasonPolicyConfigurationLoader;
import net.enthusia.staff.paper.config.RestartRequiredConfiguration;
import org.junit.jupiter.api.Test;

class ConfigurationReloadTransactionTest {
    @Test
    void candidateAlertReplacementAndPolicyPublicationCommitTogether() {
        Harness harness = Harness.active();
        harness.factory.enqueue(Start.SUCCESS);

        ConfigurationReloadResult result = harness.reload();

        assertEquals(ConfigurationReloadResult.Outcome.APPLIED, result.outcome());
        harness.assertCandidateActive();
    }

    @Test
    void policyPublicationFailureOccursBeforeAnyAlertMutation() {
        Harness harness = Harness.active();
        harness.factory.enqueue(Start.SUCCESS);
        harness.publisher.failPublishBeforeMutation = true;
        FakeLifecycle original = harness.factory.lifecycles.getFirst();

        ConfigurationReloadResult result = harness.reload();

        assertEquals(ConfigurationReloadResult.Outcome.RESTORED, result.outcome());
        harness.assertPreviousActive();
        assertEquals(0, original.closed.get());
        FakeLifecycle prepared = harness.factory.lifecycles.get(1);
        assertEquals(0, prepared.starts.get());
        assertEquals(1, prepared.closed.get());
    }

    @Test
    void alertReplacementFailureRestoresBothPreviousComponents() {
        Harness harness = Harness.active();
        harness.factory.enqueue(Start.FAIL);
        harness.factory.enqueue(Start.SUCCESS);

        ConfigurationReloadResult result = harness.reload();

        assertEquals(ConfigurationReloadResult.Outcome.RESTORED, result.outcome());
        harness.assertPreviousActive();
    }

    @Test
    void alertReplacementFailureAndPolicyRestorationFailureConvergeOnCandidate() {
        Harness harness = Harness.active();
        harness.factory.enqueue(Start.FAIL);
        harness.factory.enqueue(Start.SUCCESS);
        harness.publisher.failRestore = true;
        harness.factory.enqueue(Start.SUCCESS);

        ConfigurationReloadResult result = harness.reload();

        assertEquals(ConfigurationReloadResult.Outcome.APPLIED, result.outcome());
        harness.assertCandidateActive();
    }

    @Test
    void rollbackReplacementFailureRetainsCandidateWithoutFalseUnavailableStatus() {
        Harness harness = Harness.active();
        harness.factory.enqueue(Start.SUCCESS);
        harness.publisher.returnPreviousOnSnapshotNumber = 4;
        harness.factory.enqueue(Start.FAIL);
        harness.factory.enqueue(Start.SUCCESS);

        ConfigurationReloadResult result = harness.reload();

        assertEquals(ConfigurationReloadResult.Outcome.APPLIED, result.outcome());
        harness.assertCandidateActive();
        assertTrue(harness.controller.active());
        assertFalse(result.message().contains("unavailable"));
    }

    @Test
    void rollbackReplacementAndRestorationFailureProduceAccurateUnavailableState() {
        Harness harness = Harness.active();
        harness.factory.enqueue(Start.SUCCESS);
        harness.publisher.returnPreviousOnSnapshotNumber = 4;
        harness.factory.enqueue(Start.FAIL);
        harness.factory.enqueue(Start.FAIL);

        ConfigurationReloadResult result = harness.reload();

        assertEquals(ConfigurationReloadResult.Outcome.UNAVAILABLE, result.outcome());
        assertFalse(harness.controller.active());
        assertEquals(harness.previous.punishmentRequestAlerts(), harness.controller.currentSettings());
        assertSame(harness.previous, harness.coordinator.activeSnapshot());
        assertTrue(harness.coordinator.activeReasonPolicies().sameAs(harness.publisher.previous));
    }

    @Test
    void shutdownBeforePreparedCommitPublishesNoCandidateSnapshot() {
        Harness harness = Harness.active();
        harness.factory.enqueue(Start.SUCCESS);
        harness.publisher.afterPublish = harness.controller::close;

        ConfigurationReloadResult result = harness.reload();

        assertEquals(ConfigurationReloadResult.Outcome.SHUTTING_DOWN, result.outcome());
        assertSame(harness.previous, harness.coordinator.activeSnapshot());
        assertTrue(harness.coordinator.activeReasonPolicies().sameAs(harness.publisher.previous));
    }

    @Test
    void shutdownDuringAlertCommitPublishesNoCandidateSnapshot() {
        Harness harness = Harness.active();
        harness.factory.enqueue(new Start(true, harness.controller::close));

        ConfigurationReloadResult result = harness.reload();

        assertEquals(ConfigurationReloadResult.Outcome.SHUTTING_DOWN, result.outcome());
        assertSame(harness.previous, harness.coordinator.activeSnapshot());
        assertTrue(harness.coordinator.activeReasonPolicies().sameAs(harness.publisher.previous));
    }

    private static final class Harness {
        private final PaperConfigurationSnapshot previous = snapshot(enabled(10));
        private final PaperConfigurationSnapshot candidate = snapshot(enabled(20));
        private final ScriptedFactory factory = new ScriptedFactory();
        private final PunishmentRequestAlertController controller = new PunishmentRequestAlertController(
                "paper:SMP:reload-transaction",
                previous.punishmentRequestAlerts(),
                factory,
                ignored -> { }
        );
        private final ScriptedPublisher publisher = new ScriptedPublisher(
                new ReasonPolicyPublisher.Snapshot("v1", List.of(policy("reason.one"))),
                loaded("v2", "reason.two")
        );
        private final ConfigurationReloadCoordinator coordinator = new ConfigurationReloadCoordinator(
                previous,
                () -> candidate,
                () -> publisher.candidate,
                publisher,
                controller,
                ignored -> { },
                ignored -> { }
        );

        private static Harness active() {
            Harness harness = new Harness();
            harness.factory.enqueue(Start.SUCCESS);
            controllerStorage(harness.controller);
            return harness;
        }

        private ConfigurationReloadResult reload() {
            return coordinator.reload();
        }

        private void assertCandidateActive() {
            assertSame(candidate, coordinator.activeSnapshot());
            assertEquals(candidate.punishmentRequestAlerts(), controller.currentSettings());
            assertTrue(coordinator.activeReasonPolicies().matches(publisher.candidate));
            assertTrue(controller.active());
        }

        private void assertPreviousActive() {
            assertSame(previous, coordinator.activeSnapshot());
            assertEquals(previous.punishmentRequestAlerts(), controller.currentSettings());
            assertTrue(coordinator.activeReasonPolicies().sameAs(publisher.previous));
            assertTrue(controller.active());
        }
    }

    private record Start(boolean succeeds, Runnable hook) {
        private static final Start SUCCESS = new Start(true, () -> { });
        private static final Start FAIL = new Start(false, () -> { });
    }

    private static final class ScriptedFactory implements PunishmentRequestAlertController.LifecycleFactory {
        private final Queue<Start> starts = new ArrayDeque<>();
        private final List<FakeLifecycle> lifecycles = new ArrayList<>();

        private void enqueue(Start start) {
            starts.add(start);
        }

        @Override
        public PunishmentRequestAlertManagedLifecycle create(
                String owner,
                PunishmentRequestAlertWorkerSettings settings,
                PunishmentRequestAlertController.Storage storage
        ) {
            Start start = starts.remove();
            FakeLifecycle lifecycle = new FakeLifecycle(start);
            lifecycles.add(lifecycle);
            return lifecycle;
        }
    }

    private static final class FakeLifecycle implements PunishmentRequestAlertManagedLifecycle {
        private final Start behavior;
        private final AtomicInteger starts = new AtomicInteger();
        private final AtomicInteger closed = new AtomicInteger();
        private boolean active;

        private FakeLifecycle(Start behavior) {
            this.behavior = behavior;
        }

        @Override
        public boolean start() {
            starts.incrementAndGet();
            behavior.hook().run();
            if (!behavior.succeeds()) {
                throw new IllegalStateException("simulated lifecycle start failure");
            }
            active = true;
            return true;
        }

        @Override
        public boolean active() {
            return active && closed.get() == 0;
        }

        @Override
        public void close() {
            active = false;
            closed.incrementAndGet();
        }
    }

    private static final class ScriptedPublisher implements ReasonPolicyPublisher {
        private final Snapshot previous;
        private final ReasonPolicyConfigurationLoader.LoadedPolicies candidate;
        private Snapshot current;
        private boolean failPublishBeforeMutation;
        private boolean failRestore;
        private int snapshotCalls;
        private int returnPreviousOnSnapshotNumber = -1;
        private Runnable afterPublish = () -> { };

        private ScriptedPublisher(
                Snapshot previous,
                ReasonPolicyConfigurationLoader.LoadedPolicies candidate
        ) {
            this.previous = previous;
            this.current = previous;
            this.candidate = candidate;
        }

        @Override
        public Snapshot snapshot() {
            snapshotCalls++;
            if (snapshotCalls == returnPreviousOnSnapshotNumber) {
                return previous;
            }
            return current;
        }

        @Override
        public void publish(ReasonPolicyConfigurationLoader.LoadedPolicies policies) {
            if (failPublishBeforeMutation) {
                failPublishBeforeMutation = false;
                throw new IllegalStateException("simulated pre-publication failure");
            }
            current = new Snapshot(policies.version(), policies.policies());
            afterPublish.run();
        }

        @Override
        public void restore(Snapshot snapshot) {
            if (failRestore) {
                throw new IllegalStateException("simulated restoration failure");
            }
            current = snapshot;
        }
    }

    private static void controllerStorage(PunishmentRequestAlertController controller) {
        controller.attachStorage(new PunishmentRequestAlertController.Storage(
                proxy(PunishmentRequestAlertStore.class),
                proxy(PunishmentRequestStore.class),
                proxy(PlayerDirectory.class)
        ));
    }

    private static PaperConfigurationSnapshot snapshot(PunishmentRequestAlertWorkerSettings settings) {
        return new PaperConfigurationSnapshot(1, restart(), settings);
    }

    private static RestartRequiredConfiguration restart() {
        return new RestartRequiredConfiguration(
                "ES_DATABASE_URL", "ES_DATABASE_USER", "ES_DATABASE_PASSWORD",
                8, 5_000, 4, 256, "SMP", "SMP", false,
                "127.0.0.1", 28_765, "VELOCITY",
                "ES_CHANNEL_BACKEND_SECRET", "ES_CHANNEL_PROXY_SECRET",
                "channel-trust.p12", "ES_CHANNEL_TLS_TRUSTSTORE_PASSWORD"
        );
    }

    private static PunishmentRequestAlertWorkerSettings enabled(long pollSeconds) {
        PunishmentRequestAlertWorkerSettings defaults = PunishmentRequestAlertWorkerSettings.safeDefaults(true);
        return new PunishmentRequestAlertWorkerSettings(
                true, Duration.ofSeconds(pollSeconds), defaults.recipientLimit(),
                defaults.directBatch(), defaults.reviewerBatch(), defaults.operationalBatch(),
                defaults.totalClaimLimit(), defaults.presentationLimit(),
                Duration.ofSeconds(Math.max(45, pollSeconds * 2 + 5)),
                defaults.maximumAttempts(), defaults.retryBase(), defaults.retryMaximum(),
                defaults.joinDelay(), defaults.requestExpirationInterval(),
                defaults.intentExpirationInterval(), defaults.leaseReclaimInterval(),
                defaults.retentionInterval(), defaults.requestExpirationBatch(),
                defaults.intentExpirationBatch(), defaults.leaseReclaimBatch(),
                defaults.retentionBatch(), defaults.retentionDuration()
        );
    }

    private static ReasonPolicyConfigurationLoader.LoadedPolicies loaded(String version, String id) {
        return new ReasonPolicyConfigurationLoader.LoadedPolicies(version, List.of(policy(id)));
    }

    private static ReasonPolicy policy(String id) {
        return new ReasonPolicy(
                id, id, "Test reason", 1, false,
                List.of(new PunishmentStep(0, "Warning", List.of(
                        new SanctionSpec(SanctionType.WARNING, SanctionLength.instant())))),
                List.of(), true, true, false, StaffRank.MOD, false, AltInheritanceMode.NONE
        );
    }

    private static <T> T proxy(Class<T> type) {
        return type.cast(Proxy.newProxyInstance(
                Thread.currentThread().getContextClassLoader(),
                new Class<?>[]{type},
                (instance, method, arguments) -> defaultValue(method.getReturnType())
        ));
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) {
            return null;
        }
        if (type == boolean.class) {
            return false;
        }
        if (type == char.class) {
            return '\0';
        }
        if (type == long.class) {
            return 0L;
        }
        if (type == double.class) {
            return 0D;
        }
        if (type == float.class) {
            return 0F;
        }
        if (type == byte.class) {
            return (byte) 0;
        }
        if (type == short.class) {
            return (short) 0;
        }
        return 0;
    }
}
