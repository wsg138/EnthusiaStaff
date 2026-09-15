package net.enthusia.staff.discordbot;

import java.time.Clock;
import java.util.concurrent.atomic.AtomicBoolean;
import net.dv8tion.jda.api.JDA;
import net.enthusia.staff.persistence.DatabaseConfig;
import net.enthusia.staff.persistence.DiscordInvestigationPersistenceRuntime;

/** Owns D09 durable maintenance and independent Discord/Minecraft alert delivery resources. */
final class DiscordInvestigationRuntime implements AutoCloseable {
    private final DiscordInvestigationPersistenceRuntime persistence;
    private final JdaDiscordInvestigationAlertSink discordAlerts;
    private final DiscordInvestigationCoordinator coordinator;
    private final AtomicBoolean closed = new AtomicBoolean();

    private DiscordInvestigationRuntime(
            DiscordInvestigationPersistenceRuntime persistence,
            JdaDiscordInvestigationAlertSink discordAlerts,
            DiscordInvestigationCoordinator coordinator
    ) {
        this.persistence = persistence;
        this.discordAlerts = discordAlerts;
        this.coordinator = coordinator;
    }

    static DiscordInvestigationRuntime open(
            DatabaseConfig database,
            StaffModerationConfiguration moderation,
            DiscordInvestigationConfiguration configuration,
            long guildId
    ) {
        if (database == null || moderation == null || configuration == null || guildId <= 0) {
            throw new IllegalArgumentException("Discord investigation runtime configuration is invalid");
        }
        DiscordInvestigationPersistenceRuntime persistence = DiscordInvestigationPersistenceRuntime.open(database);
        try {
            return assemble(persistence, moderation, configuration, guildId);
        } catch (RuntimeException exception) {
            persistence.close();
            throw exception;
        }
    }

    private static DiscordInvestigationRuntime assemble(
            DiscordInvestigationPersistenceRuntime persistence,
            StaffModerationConfiguration moderation,
            DiscordInvestigationConfiguration configuration,
            long guildId
    ) {
        JdaDiscordInvestigationAlertSink discordAlerts = new JdaDiscordInvestigationAlertSink(
                guildId, configuration.alertChannelId(), configuration.staffRoleId());
        HttpMinecraftInvestigationAlertSink minecraftAlerts = new HttpMinecraftInvestigationAlertSink(
                moderation.authorityUri(), moderation.authoritySecret(), moderation.authorityTransport());
        DiscordInvestigationWorker worker = new DiscordInvestigationWorker(
                persistence.investigations(),
                discordAlerts,
                minecraftAlerts,
                Clock.systemUTC(),
                configuration.retryBase(),
                configuration.retryMaximum()
        );
        DiscordInvestigationCoordinator coordinator = new DiscordInvestigationCoordinator(
                worker, configuration.workerInterval());
        coordinator.start();
        return new DiscordInvestigationRuntime(persistence, discordAlerts, coordinator);
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
        try {
            coordinator.close();
        } finally {
            discordAlerts.unbind();
            persistence.close();
        }
    }
}
