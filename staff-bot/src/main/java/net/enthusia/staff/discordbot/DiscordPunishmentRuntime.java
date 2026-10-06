package net.enthusia.staff.discordbot;

import java.time.Clock;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import net.dv8tion.jda.api.JDA;
import net.enthusia.staff.domain.application.CrossPlatformPunishmentService;
import net.enthusia.staff.domain.application.MinecraftPunishmentGateway;
import net.enthusia.staff.domain.auth.DiscordModerationAuthorizationService;
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
    private final CrossPlatformPunishmentService crossPlatformService;
    private final CrossPlatformModerationActionService crossPlatformActions;
    private final AtomicBoolean closed = new AtomicBoolean();

    private DiscordPunishmentRuntime(
            DiscordPunishmentPersistenceRuntime persistence,
            JdaDiscordPunishmentGateway gateway,
            DiscordPunishmentCoordinator coordinator,
            DiscordPunishmentService service,
            CrossPlatformPunishmentService crossPlatformService,
            CrossPlatformModerationActionService crossPlatformActions
    ) {
        this.persistence = persistence;
        this.gateway = gateway;
        this.coordinator = coordinator;
        this.service = service;
        this.crossPlatformService = crossPlatformService;
        this.crossPlatformActions = crossPlatformActions;
    }

    static DiscordPunishmentRuntime open(
            DatabaseConfig database,
            DiscordPunishmentConfiguration configuration,
            StaffModerationReadService reads,
            LinkedStaffActorResolver actors,
            MinecraftPunishmentGateway minecraftGateway,
            long guildId,
            int interactionCapacity,
            Duration interactionTtl
    ) {
        validateOpen(database, configuration, reads, actors, minecraftGateway,
                guildId, interactionCapacity, interactionTtl);
        DiscordPunishmentPersistenceRuntime persistence = DiscordPunishmentPersistenceRuntime.open(database);
        try {
            return assemble(
                    persistence, configuration, reads, actors, minecraftGateway,
                    guildId, interactionCapacity, interactionTtl
            );
        } catch (RuntimeException exception) {
            persistence.close();
            throw exception;
        }
    }

    private static DiscordPunishmentRuntime assemble(
            DiscordPunishmentPersistenceRuntime persistence,
            DiscordPunishmentConfiguration configuration,
            StaffModerationReadService reads,
            LinkedStaffActorResolver actors,
            MinecraftPunishmentGateway minecraftGateway,
            long guildId,
            int interactionCapacity,
            Duration interactionTtl
    ) {
        Clock clock = Clock.systemUTC();
        JdaDiscordPunishmentGateway gateway = new JdaDiscordPunishmentGateway(configuration);
        Duration confirmationTtl = interactionTtl.compareTo(MAX_CONFIRMATION_TTL) > 0
                ? MAX_CONFIRMATION_TTL : interactionTtl;
        DiscordPunishmentConfirmationStore confirmations = new DiscordPunishmentConfirmationStore(
                clock, confirmationTtl, interactionCapacity
        );
        DiscordPunishmentAuthorization authorization = new DiscordPunishmentAuthorization(
                configuration.authorizationLimits()
        );
        DiscordGuildId discordGuildId = new DiscordGuildId(Long.toUnsignedString(guildId));
        DiscordPunishmentService service = createService(
                persistence, reads, actors, authorization, confirmations, gateway, discordGuildId, clock
        );
        CrossPlatformPunishmentService crossPlatformService = new CrossPlatformPunishmentService(
                minecraftGateway,
                persistence.crossPlatformPunishments(),
                persistence.crossPlatformIdentities(),
                new DiscordModerationAuthorizationService(configuration.authorizationLimits())
        );
        CrossPlatformModerationActionService crossPlatformActions = new CrossPlatformModerationActionService(
                reads,
                actors,
                minecraftGateway,
                crossPlatformService,
                service,
                gateway,
                persistence.crossPlatformStatus(),
                discordGuildId,
                clock
        );
        DiscordPunishmentWorker worker = new DiscordPunishmentWorker(
                persistence.punishments(), gateway, clock, "d07-" + UUID.randomUUID(),
                configuration.reconciliationInterval()
        );
        MinecraftBanDiscordNotificationWorker minecraftNotifications =
                new MinecraftBanDiscordNotificationWorker(
                        persistence.minecraftBanNotifications(),
                        gateway,
                        clock,
                        MinecraftBanDiscordNotificationWorker.workerId()
                );
        DiscordPunishmentCoordinator coordinator = new DiscordPunishmentCoordinator(
                () -> {
                    worker.runCycle();
                    minecraftNotifications.runCycle();
                },
                configuration.workerInterval()
        );
        coordinator.start();
        return new DiscordPunishmentRuntime(
                persistence, gateway, coordinator, service, crossPlatformService, crossPlatformActions);
    }

    private static DiscordPunishmentService createService(
            DiscordPunishmentPersistenceRuntime persistence,
            StaffModerationReadService reads,
            LinkedStaffActorResolver actors,
            DiscordPunishmentAuthorization authorization,
            DiscordPunishmentConfirmationStore confirmations,
            JdaDiscordPunishmentGateway gateway,
            DiscordGuildId guildId,
            Clock clock
    ) {
        DiscordPunishmentService.Dependencies dependencies = new DiscordPunishmentService.Dependencies(
                reads,
                actors,
                authorization,
                confirmations,
                persistence.punishments(),
                persistence::ensureDiscordSubject
        );
        return new DiscordPunishmentService(dependencies, gateway, guildId, clock);
    }

    private static void validateOpen(
            DatabaseConfig database,
            DiscordPunishmentConfiguration configuration,
            StaffModerationReadService reads,
            LinkedStaffActorResolver actors,
            MinecraftPunishmentGateway minecraftGateway,
            long guildId,
            int interactionCapacity,
            Duration interactionTtl
    ) {
        requirePresent(database);
        requirePresent(configuration);
        requirePresent(reads);
        requirePresent(actors);
        requirePresent(minecraftGateway);
        if (guildId <= 0 || interactionCapacity < 1) {
            throw invalidConfiguration();
        }
        requirePositive(interactionTtl);
    }

    private static void requirePresent(Object value) {
        if (value == null) {
            throw invalidConfiguration();
        }
    }

    private static void requirePositive(Duration value) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw invalidConfiguration();
        }
    }

    private static IllegalArgumentException invalidConfiguration() {
        return new IllegalArgumentException("Discord punishment runtime configuration is invalid");
    }

    DiscordPunishmentService service() {
        return service;
    }

    CrossPlatformPunishmentService crossPlatformService() {
        return crossPlatformService;
    }

    CrossPlatformModerationActionService crossPlatformActions() {
        return crossPlatformActions;
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
