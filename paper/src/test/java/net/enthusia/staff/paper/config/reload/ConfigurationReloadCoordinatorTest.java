package net.enthusia.staff.paper.config.reload;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.time.Duration;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import net.enthusia.staff.domain.auth.StaffRank;
import net.enthusia.staff.domain.escalation.AltInheritanceMode;
import net.enthusia.staff.domain.escalation.PunishmentStep;
import net.enthusia.staff.domain.escalation.ReasonPolicy;
import net.enthusia.staff.domain.ports.AtomicReasonPolicyRepository;
import net.enthusia.staff.domain.ports.PlayerDirectory;
import net.enthusia.staff.domain.ports.PunishmentRequestAlertStore;
import net.enthusia.staff.domain.ports.PunishmentRequestStore;
import net.enthusia.staff.domain.sanction.SanctionActionLimits;
import net.enthusia.staff.domain.sanction.SanctionLength;
import net.enthusia.staff.domain.sanction.SanctionSpec;
import net.enthusia.staff.domain.sanction.SanctionType;
import net.enthusia.staff.paper.alert.PunishmentRequestAlertController;
import net.enthusia.staff.paper.alert.PunishmentRequestAlertManagedLifecycle;
import net.enthusia.staff.paper.alert.PunishmentRequestAlertWorkerSettings;
import net.enthusia.staff.paper.config.ModerationFeatureSettings;
import net.enthusia.staff.paper.config.PaperConfigurationSnapshot;
import net.enthusia.staff.paper.config.PaperConfigurationValidationException;
import net.enthusia.staff.paper.config.ReasonPolicyConfigurationLoader;
import net.enthusia.staff.paper.config.RestartRequiredConfiguration;
import org.junit.jupiter.api.Test;

class ConfigurationReloadCoordinatorTest {
    @Test
    void invalidCandidateRetainsSettingsPoliciesAndActiveWorker() {
        CountingFactory factory = new CountingFactory();
        PaperConfigurationSnapshot active = snapshot(enabled(10), restart(4, 256, "SMP"));
        PunishmentRequestAlertController controller = controller(active, factory);
        controller.attachStorage(storage());
        AtomicReasonPolicyRepository policies = policies("v1", "reason.one");
        List<List<String>> logged = new ArrayList<>();
        AtomicReference<String> issue = new AtomicReference<>();
        ConfigurationReloadCoordinator coordinator = new ConfigurationReloadCoordinator(
                active,
                () -> {
                    throw new PaperConfigurationValidationException(List.of("first invalid", "second invalid"));
                },
                () -> loaded("v2", "reason.two"),
                policies,
                controller,
                logged::add,
                issue::set
        );

        ConfigurationReloadResult result = coordinator.reload();

        assertEquals(ConfigurationReloadResult.Outcome.VALIDATION_FAILED, result.outcome());
        assertSame(active, coordinator.activeSnapshot());
        assertEquals("v1", policies.activeVersion());
        assertEquals(active.punishmentRequestAlerts(), controller.currentSettings());
        assertEquals(1, factory.created.get());
        assertEquals(0, factory.lifecycles.getFirst().closed.get());
        assertEquals(List.of("first invalid", "second invalid"), logged.getFirst());
        assertEquals("Configuration validation failed while the previous runtime remains active", issue.get());
    }

    @Test
    void restartRequiredDifferenceRetainsAllOldStateAndIdentifiesPath() {
        CountingFactory factory = new CountingFactory();
        PaperConfigurationSnapshot active = snapshot(disabled(), restart(4, 256, "SMP"));
        AtomicReasonPolicyRepository policies = policies("v1", "reason.one");
        ConfigurationReloadCoordinator coordinator = new ConfigurationReloadCoordinator(
                active,
                () -> snapshot(enabled(10), restart(4, 257, "SMP")),
                () -> loaded("v2", "reason.two"),
                policies,
                controller(active, factory),
                ignored -> { },
                ignored -> { }
        );

        ConfigurationReloadResult result = coordinator.reload();

        assertEquals(ConfigurationReloadResult.Outcome.RESTART_REQUIRED, result.outcome());
        assertEquals(List.of("workers.queue-capacity requires a full server restart"), result.details());
        assertSame(active, coordinator.activeSnapshot());
        assertEquals("v1", policies.activeVersion());
        assertEquals(0, factory.created.get());
    }

    @Test
    void validCandidatePublishesLatestDesiredSettingsBeforeStorageAndReplacesPoliciesAtomically() {
        CountingFactory factory = new CountingFactory();
        PaperConfigurationSnapshot active = snapshot(disabled(), restart(4, 256, "SMP"));
        PaperConfigurationSnapshot candidate = snapshot(enabled(20), restart(4, 256, "SMP"));
        AtomicReasonPolicyRepository policies = policies("v1", "reason.one");
        AtomicReference<String> issue = new AtomicReference<>("old issue");
        PunishmentRequestAlertController controller = controller(active, factory);
        ConfigurationReloadCoordinator coordinator = new ConfigurationReloadCoordinator(
                active,
                () -> candidate,
                () -> loaded("v2", "reason.two"),
                policies,
                controller,
                ignored -> { },
                issue::set
        );

        ConfigurationReloadResult result = coordinator.reload();

        assertEquals(ConfigurationReloadResult.Outcome.WAITING_FOR_STORAGE, result.outcome());
        assertTrue(result.reasonPoliciesReloaded());
        assertSame(candidate, coordinator.activeSnapshot());
        assertEquals(candidate.punishmentRequestAlerts(), controller.currentSettings());
        assertEquals("v2", policies.activeVersion());
        assertTrue(policies.find("reason.two").isPresent());
        assertEquals("", issue.get());
        assertEquals(0, factory.created.get());
    }

    @Test
    void unchangedConfigurationDoesNotRestartWorkerOrReplacePolicies() {
        CountingFactory factory = new CountingFactory();
        PaperConfigurationSnapshot active = snapshot(enabled(10), restart(4, 256, "SMP"));
        PunishmentRequestAlertController controller = controller(active, factory);
        controller.attachStorage(storage());
        AtomicReasonPolicyRepository policies = policies("v1", "reason.one");
        ConfigurationReloadCoordinator coordinator = new ConfigurationReloadCoordinator(
                active,
                () -> active,
                () -> loaded("v1", "reason.one"),
                policies,
                controller,
                ignored -> { },
                ignored -> { }
        );

        ConfigurationReloadResult result = coordinator.reload();

        assertEquals(ConfigurationReloadResult.Outcome.NO_CHANGES, result.outcome());
        assertFalse(result.reasonPoliciesReloaded());
        assertEquals(1, factory.created.get());
        assertEquals(0, factory.lifecycles.getFirst().closed.get());
    }

    @Test
    void moderationPresentationOnlyChangeDoesNotRestartWorkerOrResetRuntimeServices() {
        CountingFactory factory = new CountingFactory();
        PaperConfigurationSnapshot active = snapshot(enabled(10), restart(4, 256, "SMP"));
        PunishmentRequestAlertController controller = controller(active, factory);
        controller.attachStorage(storage());
        ModerationFeatureSettings changedSettings = new ModerationFeatureSettings(
                12,
                false,
                true,
                ZoneId.of("America/Chicago"),
                new SanctionActionLimits(5, 250, false)
        );
        PaperConfigurationSnapshot candidate = new PaperConfigurationSnapshot(
                1,
                active.restartRequired(),
                active.punishmentRequestAlerts(),
                changedSettings
        );
        AtomicReasonPolicyRepository policies = policies("v1", "reason.one");
        ConfigurationReloadCoordinator coordinator = new ConfigurationReloadCoordinator(
                active,
                () -> candidate,
                () -> loaded("v1", "reason.one"),
                policies,
                controller,
                ignored -> { },
                ignored -> { }
        );

        ConfigurationReloadResult result = coordinator.reload();

        assertEquals(ConfigurationReloadResult.Outcome.NO_CHANGES, result.outcome());
        assertSame(candidate, coordinator.activeSnapshot());
        assertEquals(changedSettings, coordinator.activeSnapshot().moderationFeatures());
        assertEquals(1, factory.created.get());
        assertEquals(0, factory.lifecycles.getFirst().closed.get());
        assertEquals(active.punishmentRequestAlerts(), controller.currentSettings());
    }

    @Test
    void policyOnlyChangeUsesOneWholeRepositoryReplacement() {
        CountingFactory factory = new CountingFactory();
        PaperConfigurationSnapshot active = snapshot(disabled(), restart(4, 256, "SMP"));
        AtomicReasonPolicyRepository policies = policies("v1", "reason.one");
        ConfigurationReloadCoordinator coordinator = new ConfigurationReloadCoordinator(
                active,
                () -> active,
                () -> loaded("v2", "reason.two"),
                policies,
                controller(active, factory),
                ignored -> { },
                ignored -> { }
        );

        ConfigurationReloadResult result = coordinator.reload();

        assertEquals(ConfigurationReloadResult.Outcome.APPLIED, result.outcome());
        assertTrue(result.reasonPoliciesReloaded());
        assertEquals("v2", policies.activeVersion());
        assertTrue(policies.find("reason.two").isPresent());
        assertTrue(policies.find("reason.one").isEmpty());
    }

    private static PunishmentRequestAlertController controller(
            PaperConfigurationSnapshot snapshot,
            CountingFactory factory
    ) {
        return new PunishmentRequestAlertController(
                "paper:SMP:stable-owner",
                snapshot.punishmentRequestAlerts(),
                factory,
                ignored -> { }
        );
    }

    private static PaperConfigurationSnapshot snapshot(
            PunishmentRequestAlertWorkerSettings settings,
            RestartRequiredConfiguration restart
    ) {
        return new PaperConfigurationSnapshot(1, restart, settings);
    }

    private static RestartRequiredConfiguration restart(int threads, int queue, String serverId) {
        return new RestartRequiredConfiguration(
                "ES_DATABASE_URL",
                "ES_DATABASE_USER",
                "ES_DATABASE_PASSWORD",
                8,
                5_000,
                threads,
                queue,
                serverId,
                serverId,
                false,
                "127.0.0.1",
                28_765,
                "VELOCITY",
                "ES_CHANNEL_BACKEND_SECRET",
                "ES_CHANNEL_PROXY_SECRET",
                "channel-trust.p12",
                "ES_CHANNEL_TLS_TRUSTSTORE_PASSWORD"
        );
    }

    private static PunishmentRequestAlertWorkerSettings disabled() {
        return PunishmentRequestAlertWorkerSettings.safeDefaults(false);
    }

    private static PunishmentRequestAlertWorkerSettings enabled(long pollSeconds) {
        PunishmentRequestAlertWorkerSettings defaults = PunishmentRequestAlertWorkerSettings.safeDefaults(true);
        return new PunishmentRequestAlertWorkerSettings(
                true,
                Duration.ofSeconds(pollSeconds),
                defaults.recipientLimit(),
                defaults.directBatch(),
                defaults.reviewerBatch(),
                defaults.operationalBatch(),
                defaults.totalClaimLimit(),
                defaults.presentationLimit(),
                Duration.ofSeconds(Math.max(45, pollSeconds * 2 + 5)),
                defaults.maximumAttempts(),
                defaults.retryBase(),
                defaults.retryMaximum(),
                defaults.joinDelay(),
                defaults.requestExpirationInterval(),
                defaults.intentExpirationInterval(),
                defaults.leaseReclaimInterval(),
                defaults.retentionInterval(),
                defaults.requestExpirationBatch(),
                defaults.intentExpirationBatch(),
                defaults.leaseReclaimBatch(),
                defaults.retentionBatch(),
                defaults.retentionDuration()
        );
    }

    private static AtomicReasonPolicyRepository policies(String version, String id) {
        return new AtomicReasonPolicyRepository(version, List.of(policy(id)));
    }

    private static ReasonPolicyConfigurationLoader.LoadedPolicies loaded(String version, String id) {
        return new ReasonPolicyConfigurationLoader.LoadedPolicies(version, List.of(policy(id)));
    }

    private static ReasonPolicy policy(String id) {
        return new ReasonPolicy(
                id,
                id,
                "Test reason",
                1,
                false,
                List.of(new PunishmentStep(
                        0,
                        "Warning",
                        List.of(new SanctionSpec(SanctionType.WARNING, SanctionLength.instant()))
                )),
                List.of(),
                true,
                true,
                false,
                StaffRank.MOD,
                false,
                AltInheritanceMode.NONE
        );
    }

    private static PunishmentRequestAlertController.Storage storage() {
        return new PunishmentRequestAlertController.Storage(
                proxy(PunishmentRequestAlertStore.class),
                proxy(PunishmentRequestStore.class),
                proxy(PlayerDirectory.class)
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

    private static final class CountingFactory implements PunishmentRequestAlertController.LifecycleFactory {
        private final AtomicInteger created = new AtomicInteger();
        private final List<FakeLifecycle> lifecycles = new ArrayList<>();

        @Override
        public PunishmentRequestAlertManagedLifecycle create(
                String owner,
                PunishmentRequestAlertWorkerSettings settings,
                PunishmentRequestAlertController.Storage storage
        ) {
            created.incrementAndGet();
            FakeLifecycle lifecycle = new FakeLifecycle();
            lifecycles.add(lifecycle);
            return lifecycle;
        }
    }

    private static final class FakeLifecycle implements PunishmentRequestAlertManagedLifecycle {
        private final AtomicInteger closed = new AtomicInteger();
        private boolean active;

        @Override
        public boolean start() {
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
}
