package net.enthusia.staff.discordbot;

import java.time.Clock;
import java.util.concurrent.atomic.AtomicBoolean;
import net.dv8tion.jda.api.JDA;
import net.enthusia.staff.domain.auth.DiscordAuthorizationLimits;
import net.enthusia.staff.persistence.DatabaseConfig;
import net.enthusia.staff.persistence.DiscordInvestigationPersistenceRuntime;

/** Owns D09 durable maintenance, application service, and independent alert delivery resources. */
final class DiscordInvestigationRuntime implements AutoCloseable {
    record Dependencies(
            DatabaseConfig database,
            StaffModerationConfiguration moderation,
            DiscordAuthorizationLimits authorizationLimits,
            StaffModerationReadService reads,
            LinkedStaffActorResolver actors
    ) {
        Dependencies {
            if (database == null || moderation == null || authorizationLimits == null || reads == null || actors == null) {
                throw new IllegalArgumentException("Discord investigation runtime dependencies are invalid");
            }
        }
    }

    private final DiscordInvestigationPersistenceRuntime persistence;
    private final JdaDiscordInvestigationAlertSink discordAlerts;
    private final DiscordInvestigationCoordinator coordinator;
    private final DiscordInvestigationService service;
    private final AtomicBoolean closed = new AtomicBoolean();

    private DiscordInvestigationRuntime(
            DiscordInvestigationPersistenceRuntime persistence,
            JdaDiscordInvestigationAlertSink discordAlerts,
            DiscordInvestigationCoordinator coordinator,
            DiscordInvestigationService service
    ) {
        this.persistence = persistence;
        this.discordAlerts = discordAlerts;
        this.coordinator = coordinator;
        this.service = service;
    }

    static DiscordInvestigationRuntime open(
            Dependencies dependencies,
            DiscordInvestigationConfiguration configuration,
            long guildId
    ) {
        if (dependencies == null || configuration == null || guildId <= 0) {
            throw new IllegalArgumentException("Discord investigation runtime configuration is invalid");
        }
        DiscordInvestigationPersistenceRuntime persistence =
                DiscordInvestigationPersistenceRuntime.open(dependencies.database());
        try {
            return assemble(persistence, dependencies, configuration, guildId);
        } catch (RuntimeException exception) {
            persistence.close();
            throw exception;
        }
    }

    private static DiscordInvestigationRuntime assemble(
            DiscordInvestigationPersistenceRuntime persistence,
            Dependencies dependencies,
            DiscordInvestigationConfiguration configuration,
            long guildId
    ) {
        Clock clock = Clock.systemUTC();
        JdaDiscordInvestigationAlertSink discordAlerts = new JdaDiscordInvestigationAlertSink(
                guildId, configuration.alertChannelId(), configuration.staffRoleId());
        HttpMinecraftInvestigationAlertSink minecraftAlerts = new HttpMinecraftInvestigationAlertSink(
                dependencies.moderation().authorityUri(),
                dependencies.moderation().authoritySecret(),
                dependencies.moderation().authorityTransport());
        DiscordInvestigationWorker worker = new DiscordInvestigationWorker(
                persistence.investigations(), discordAlerts, minecraftAlerts, clock,
                configuration.retryBase(), configuration.retryMaximum()
        );
        DiscordInvestigationCoordinator coordinator = new DiscordInvestigationCoordinator(
                worker, configuration.workerInterval());
        DiscordInvestigationService service = new DiscordInvestigationService(
                persistence.investigations(),
                dependencies.reads(),
                dependencies.actors(),
                new DiscordInvestigationAuthorization(dependencies.authorizationLimits()),
                persistence::ensureDiscordSubject,
                clock
        );
        coordinator.start();
        return new DiscordInvestigationRuntime(persistence, discordAlerts, coordinator, service);
    }

    DiscordInvestigationService service() {
        return service;
    }

    void resume(JDA jda) {
        if (closed.get()) {
            throw new IllegalStateException("Discord investigation runtime is closed");
        }
        coordinator.pause();
        discordAlerts.unbind();
        discordAlerts.bind(jda);
        coordinator.resume();
    }

    void pause() {
        coordinator.pause();
        discordAlerts.unbind();
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        RuntimeException shutdownFailure = null;
        try {
            coordinator.close();
        } catch (RuntimeException exception) {
            shutdownFailure = exception;
        }
        discordAlerts.unbind();
        if (shutdownFailure == null) {
            persistence.close();
            return;
        }
        coordinator.runAfterTermination(persistence::close);
        throw shutdownFailure;
    }
}
