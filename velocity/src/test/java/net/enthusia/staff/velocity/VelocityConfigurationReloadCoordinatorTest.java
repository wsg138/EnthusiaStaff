package net.enthusia.staff.velocity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class VelocityConfigurationReloadCoordinatorTest {
    @Test
    void reloadableValuesPublishTogether() {
        VelocityConfiguration initial = configuration(true, "https://example.test/appeals", "velocity");
        VelocityConfiguration candidate = configuration(false, "https://example.test/new-appeals", "velocity");
        AtomicReference<VelocityConfiguration> published = new AtomicReference<>(initial);
        VelocityConfigurationReloadCoordinator coordinator = new VelocityConfigurationReloadCoordinator(
                initial,
                () -> candidate,
                published::set,
                () -> false
        );

        VelocityConfigurationReloadResult result = coordinator.reload();

        assertEquals(VelocityConfigurationReloadResult.Outcome.APPLIED, result.outcome());
        assertSame(candidate, coordinator.active());
        assertSame(candidate, published.get());
        assertFalse(coordinator.active().failClosedWhileActive());
        assertEquals("https://example.test/new-appeals", coordinator.active().appealsUrl());
    }

    @Test
    void restartRequiredCandidateLeavesLiveConfigurationUntouched() {
        VelocityConfiguration initial = configuration(true, "https://example.test/appeals", "velocity-a");
        VelocityConfiguration candidate = configuration(false, "https://example.test/new-appeals", "velocity-b");
        AtomicReference<VelocityConfiguration> published = new AtomicReference<>(initial);
        VelocityConfigurationReloadCoordinator coordinator = new VelocityConfigurationReloadCoordinator(
                initial,
                () -> candidate,
                published::set,
                () -> false
        );

        VelocityConfigurationReloadResult result = coordinator.reload();

        assertEquals(VelocityConfigurationReloadResult.Outcome.RESTART_REQUIRED, result.outcome());
        assertTrue(result.details().stream().anyMatch(value -> value.startsWith("server.id")));
        assertSame(initial, coordinator.active());
        assertSame(initial, published.get());
    }

    @Test
    void invalidCandidateLeavesLiveConfigurationUntouched() {
        VelocityConfiguration initial = configuration(true, "https://example.test/appeals", "velocity");
        AtomicReference<VelocityConfiguration> published = new AtomicReference<>(initial);
        VelocityConfigurationReloadCoordinator coordinator = new VelocityConfigurationReloadCoordinator(
                initial,
                () -> {
                    throw new IOException("private path detail");
                },
                published::set,
                () -> false
        );

        VelocityConfigurationReloadResult result = coordinator.reload();

        assertEquals(VelocityConfigurationReloadResult.Outcome.VALIDATION_FAILED, result.outcome());
        assertEquals("Configuration file could not be read", result.details().getFirst());
        assertSame(initial, coordinator.active());
        assertSame(initial, published.get());
    }

    @Test
    void publicationFailureRestoresPreviousConfiguration() {
        VelocityConfiguration initial = configuration(true, "https://example.test/appeals", "velocity");
        VelocityConfiguration candidate = configuration(false, "https://example.test/new-appeals", "velocity");
        AtomicReference<VelocityConfiguration> published = new AtomicReference<>(initial);
        AtomicBoolean rejectCandidate = new AtomicBoolean(true);
        VelocityConfigurationReloadCoordinator coordinator = new VelocityConfigurationReloadCoordinator(
                initial,
                () -> candidate,
                value -> {
                    if (value == candidate && rejectCandidate.getAndSet(false)) {
                        published.set(value);
                        throw new IllegalStateException("candidate publication failed");
                    }
                    published.set(value);
                },
                () -> false
        );

        VelocityConfigurationReloadResult result = coordinator.reload();

        assertEquals(VelocityConfigurationReloadResult.Outcome.UNAVAILABLE, result.outcome());
        assertSame(initial, coordinator.active());
        assertSame(initial, published.get());
    }

    @Test
    void repeatedReloadOfSameCandidateIsNoOp() {
        VelocityConfiguration initial = configuration(true, "https://example.test/appeals", "velocity");
        VelocityConfiguration candidate = configuration(false, "https://example.test/new-appeals", "velocity");
        AtomicReference<VelocityConfiguration> published = new AtomicReference<>(initial);
        VelocityConfigurationReloadCoordinator coordinator = new VelocityConfigurationReloadCoordinator(
                initial,
                () -> candidate,
                published::set,
                () -> false
        );

        assertEquals(VelocityConfigurationReloadResult.Outcome.APPLIED, coordinator.reload().outcome());
        assertEquals(VelocityConfigurationReloadResult.Outcome.NO_CHANGES, coordinator.reload().outcome());
        assertSame(candidate, published.get());
    }

    @Test
    void shutdownRejectsCandidateBeforeLoading() {
        VelocityConfiguration initial = configuration(true, "https://example.test/appeals", "velocity");
        AtomicBoolean loaded = new AtomicBoolean();
        VelocityConfigurationReloadCoordinator coordinator = new VelocityConfigurationReloadCoordinator(
                initial,
                () -> {
                    loaded.set(true);
                    return initial;
                },
                ignored -> {
                },
                () -> true
        );

        VelocityConfigurationReloadResult result = coordinator.reload();

        assertEquals(VelocityConfigurationReloadResult.Outcome.SHUTTING_DOWN, result.outcome());
        assertFalse(loaded.get());
    }

    @Test
    void shutdownAfterCandidateLoadRejectsPublication() {
        VelocityConfiguration initial = configuration(true, "https://example.test/appeals", "velocity");
        VelocityConfiguration candidate = configuration(false, "https://example.test/new-appeals", "velocity");
        AtomicBoolean stopping = new AtomicBoolean();
        AtomicReference<VelocityConfiguration> published = new AtomicReference<>(initial);
        VelocityConfigurationReloadCoordinator coordinator = new VelocityConfigurationReloadCoordinator(
                initial,
                () -> {
                    stopping.set(true);
                    return candidate;
                },
                published::set,
                stopping::get
        );

        VelocityConfigurationReloadResult result = coordinator.reload();

        assertEquals(VelocityConfigurationReloadResult.Outcome.SHUTTING_DOWN, result.outcome());
        assertSame(initial, coordinator.active());
        assertSame(initial, published.get());
    }

    private static VelocityConfiguration configuration(
            boolean failClosed,
            String appealsUrl,
            String serverId
    ) {
        return new VelocityConfiguration(
                "ES_DB_URL",
                "ES_DB_USER",
                "ES_DB_PASSWORD",
                8,
                5_000L,
                failClosed,
                appealsUrl,
                serverId,
                true,
                "127.0.0.1",
                18_080,
                "ES_WEBSITE_TOKEN",
                "ES_WEBSITE_HMAC",
                1,
                "ES_WEBSITE_CODE",
                300,
                65_536,
                2,
                64,
                true,
                "127.0.0.1",
                19_000,
                "velocity",
                "ES_CHANNEL_PROXY",
                Path.of("channel.p12"),
                "ES_CHANNEL_PASSWORD",
                Map.of("survival", "ES_CHANNEL_SURVIVAL"),
                true,
                1,
                "ES_IDENTITY_HMAC",
                1,
                "ES_IDENTITY_ENCRYPTION",
                true,
                Map.of(
                        "punishments", "ES_DISCORD_PUNISHMENTS",
                        "reports", "ES_DISCORD_REPORTS",
                        "logs-staffmode", "ES_DISCORD_STAFF",
                        "alerts", "ES_DISCORD_ALERTS"
                ),
                5,
                3,
                60,
                5_000,
                "ES_LITEBANS_URL",
                "ES_LITEBANS_USER",
                "ES_LITEBANS_PASSWORD",
                2,
                5_000L,
                "litebans_",
                500,
                false,
                24
        );
    }
}
