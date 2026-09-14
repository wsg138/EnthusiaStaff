package net.enthusia.staff.discordbot;

import java.time.Clock;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import net.dv8tion.jda.api.JDA;
import net.enthusia.staff.domain.moderation.DiscordGuildId;
import net.enthusia.staff.persistence.DatabaseConfig;
import net.enthusia.staff.persistence.DiscordPunishmentPersistenceRuntime;

/** Owns the D07 write pool, enforcement adapter, durable worker, and mutation service. */
final class DiscordPunishmentRuntime implements AutoCloseable {
    private static final Duration MAX_CONFIRMATION_TTL = Duration.ofMinutes(5);

    private final DiscordPunishmentPersistenceRuntime persistence;
    private final JdaDiscordPunishmentGateway gateway;
    private final DiscordPunishmentCoordinator coordinator;
    private final DiscordPunishmentService service;
    private final AtomicBoolean closed = new AtomicBoolean();

    private DiscordPunishmentRuntime(
            DiscordPunishmentPersistenceRuntime persistence,
            JdaDiscordPunishmentGateway gateway,
            DiscordPunishmentCoordinator coordinator,
            DiscordPunishmentService service
    ) {
        this.persistence = persistence;
        this.gateway = gateway;
        this.coordinator = coordinator;
        this.service = service;
    }

    static DiscordPunishmentRuntime open(
            DatabaseConfig database,
            DiscordPunishmentConfiguration configuration,
            StaffModerationReadService reads,
            LinkedStaffActorResolver actors,
            long guildId,
            int interactionCapacity,
            Duration interactionTtl
    ) {
        if (database == null || configuration == null || reads == null || actors == null
                || guildId <= 0 || interactionCapacity < 1 || interactionTtl == null
                || interactionTtl.isZero() || interactionTtl.isNegative()) {
            throw new IllegalArgumentException("Discord punishment runtime configuration is invalid");
        }
        Clock clock = Clock.systemUTC();
        DiscordPunishmentPersistenceRuntime persistence = DiscordPunishmentPersistenceRuntime.open(database);
        try {
            JdaDiscordPunishmentGateway gateway = new JdaDiscordPunishmentGateway(configuration);
            Duration confirmationTtl = interactionTtl.compareTo(MAX_CONFIRMATION_TTL) > 0
                    ? MAX_CONFIRMATION_TTL
                    : interactionTtl;
            DiscordPunishmentConfirmationStore confirmations = new DiscordPunishmentConfirmationStore(
                    clock, confirmationTtl, interactionCapacity
            );
            DiscordPunishmentAuthorization authorization = new DiscordPunishmentAuthorization(
                    configuration.authorizationLimits()
            );
            DiscordGuildId discordGuildId = new DiscordGuildId(Long.toUnsignedString(guildId));
            DiscordPunishmentService service = new DiscordPunishmentService(
                    reads,
                    actors,
                    authorization,
                    confirmations,
                    persistence.punishments(),
                    persistence::ensureDiscordSubject,
                    gateway,
                    discordGuildId,
                    clock
            );
            DiscordPunishmentWorker worker = new DiscordPunishmentWorker(
                    persistence.punishments(),
                    gateway,
                    clock,
                    "d07-" + UUID.randomUUID(),
                    configuration.reconciliationInterval()
            );
            DiscordPunishmentCoordinator coordinator = new DiscordPunishmentCoordinator(
                    worker, configuration.workerInterval()
            );
            coordinator.start();
            return new DiscordPunishmentRuntime(persistence, gateway, coordinator, service);
        } catch (RuntimeException exception) {
            persistence.close();
            throw exception;
        }
    }

    DiscordPunishmentService service() {
        return service;
    }

    void resume(JDA jda) {
        if (closed.get()) {
            throw new IllegalStateException("Discord punishment runtime is closed");
        }
        coordinator.pause();
        gateway.unbind();
        gateway.bind(jda);
        coordinator.resume();
    }

    void pause() {
        coordinator.pause();
        gateway.unbind();
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        try {
            coordinator.close();
        } finally {
            gateway.unbind();
            persistence.close();
        }
    }
}
