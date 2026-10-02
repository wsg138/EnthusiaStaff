package net.enthusia.staff.discordbot;

import java.nio.file.Path;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import net.dv8tion.jda.api.JDA;
import net.enthusia.staff.persistence.DiscordRoleSyncPersistenceRuntime;
import net.enthusia.staff.persistence.DiscordStaffReadRuntime;

/** Owns every D06/D07/D13/D16 database, authority, component, enforcement, and role-sync resource. */
final class StaffModerationRuntime implements AutoCloseable {
    private final DiscordStaffReadRuntime data;
    private final StaffModerationReadService readService;
    private final LinkedStaffActorResolver actorResolver;
    private final StaffReadAuthorization readAuthorization;
    private final SignedComponentCodec componentCodec;
    private final MinecraftProfileLookup minecraftProfiles;
    private final Optional<DiscordRoleSyncService> roleSyncService;
    private final Optional<DiscordRoleSyncPersistenceRuntime> roleSyncPersistence;
    private final Optional<DiscordCommandBridgeCoordinator> commandBridge;
    private final Optional<DiscordPunishmentRuntime> punishments;
    private final HttpStaffAuthorityClient authority;

    private StaffModerationRuntime(
            DiscordStaffReadRuntime data,
            StaffModerationReadService reads,
            LinkedStaffActorResolver actors,
            StaffReadAuthorization authorization,
            SignedComponentCodec components,
            MinecraftProfileLookup profiles,
            Optional<DiscordRoleSyncService> roleSync,
            Optional<DiscordRoleSyncPersistenceRuntime> rolePersistence,
            Optional<DiscordCommandBridgeCoordinator> console,
            Optional<DiscordPunishmentRuntime> punishments,
            HttpStaffAuthorityClient authority
    ) {
        this.data = data;
        this.readService = reads;
        this.actorResolver = actors;
        this.readAuthorization = authorization;
        this.componentCodec = components;
        this.minecraftProfiles = profiles;
        this.roleSyncService = roleSync;
        this.roleSyncPersistence = rolePersistence;
        this.commandBridge = console;
        this.punishments = punishments;
        this.authority = authority;
    }

    static Optional<StaffModerationRuntime> open(
            Optional<Path> configFile,
            long guildId,
            int interactionCapacity,
            Duration interactionTtl
    ) {
        Map<String, String> values = configFile.isPresent()
                ? StaffModerationConfigFile.read(configFile.orElseThrow())
                : System.getenv();
        Optional<StaffModerationConfiguration> configuration = StaffModerationConfiguration.fromEnvironment(values);
        Optional<DiscordPunishmentConfiguration> punishmentConfiguration =
                DiscordPunishmentConfiguration.fromEnvironment(values);
        Optional<DiscordCommandBridgeConfiguration> commandConfiguration =
                DiscordCommandBridgeConfiguration.fromEnvironment(values);
        if (configuration.isEmpty() && (punishmentConfiguration.isPresent() || commandConfiguration.isPresent())) {
            throw new IllegalArgumentException("Discord actions require the staff moderation runtime");
        }
        return configuration.map(value -> open(
                value,
                punishmentConfiguration,
                commandConfiguration,
                guildId,
                interactionCapacity,
                interactionTtl
        ));
    }

    private static StaffModerationRuntime open(
            StaffModerationConfiguration configuration,
            Optional<DiscordPunishmentConfiguration> punishmentConfiguration,
            Optional<DiscordCommandBridgeConfiguration> commandConfiguration,
            long guildId,
            int interactionCapacity,
            Duration interactionTtl
    ) {
        Clock clock = Clock.systemUTC();
        DiscordStaffReadRuntime data = DiscordStaffReadRuntime.open(configuration.database(), clock);
        MinecraftProfileLookup profiles = null;
        DiscordRoleSyncPersistenceRuntime rolePersistence = null;
        Optional<DiscordPunishmentRuntime> punishments = Optional.empty();
        try {
            StaffModerationReadService reads = new StaffModerationReadService(data, clock);
            HttpStaffAuthorityClient authority = new HttpStaffAuthorityClient(
                    configuration.authorityUri(),
                    configuration.authoritySecret(),
                    configuration.authorityTransport());
            InteractionReplayGuard componentReplay = new InteractionReplayGuard(interactionCapacity, interactionTtl);
            SignedComponentCodec components = new SignedComponentCodec(
                    clock,
                    interactionTtl,
                    configuration.componentSecret(),
                    new SecureRandom(),
                    componentReplay
            );
            LinkedStaffActorResolver actors = new LinkedStaffActorResolver(reads, authority);
            StaffReadAuthorization authorization = new StaffReadAuthorization();
            profiles = MinecraftProfileLookup.mojang();
            Optional<DiscordRoleSyncService> roleSync = Optional.empty();
            if (configuration.roleSync().isPresent()) {
                rolePersistence = DiscordRoleSyncPersistenceRuntime.open(configuration.roleSyncDatabase().orElseThrow());
                roleSync = Optional.of(new DiscordRoleSyncService(
                        rolePersistence, authority, configuration.roleSync().orElseThrow(), clock));
            }
            Optional<DiscordCommandBridgeCoordinator> console = commandConfiguration.map(value ->
                    new DiscordCommandBridgeCoordinator(
                            new DiscordCommandActorResolver(data::subjectForDiscord),
                            new HttpMinecraftCommandBridgeClient(
                                    value.endpoints(), value.credential(), value.timeout())
                    ));
            punishments = punishmentConfiguration.map(value -> DiscordPunishmentRuntime.open(
                    configuration.database(),
                    value,
                    reads,
                    actors,
                    guildId,
                    interactionCapacity,
                    interactionTtl
            ));
            return new StaffModerationRuntime(
                    data, reads, actors, authorization, components, profiles,
                    roleSync, Optional.ofNullable(rolePersistence), console, punishments, authority
            );
        } catch (RuntimeException exception) {
            punishments.ifPresent(DiscordPunishmentRuntime::close);
            if (rolePersistence != null) {
                rolePersistence.close();
            }
            if (profiles != null) {
                profiles.close();
            }
            data.close();
            throw exception;
        }
    }

    StaffModerationReadService reads() {
        return readService;
    }

    net.enthusia.staff.persistence.DiscordPunishmentHistoryReader.Page discordHistory(long guildId, long userId) {
        return data.discordHistory(
                new net.enthusia.staff.domain.moderation.DiscordGuildId(Long.toUnsignedString(guildId)),
                new net.enthusia.staff.domain.moderation.DiscordUserId(Long.toUnsignedString(userId)), 50);
    }

    LinkedStaffActorResolver actors() {
        return actorResolver;
    }

    StaffReadAuthorization authorization() {
        return readAuthorization;
    }

    SignedComponentCodec components() {
        return componentCodec;
    }

    MinecraftProfileLookup minecraftProfiles() {
        return minecraftProfiles;
    }

    Optional<DiscordRoleSyncService> roleSync() {
        return roleSyncService;
    }

    Optional<DiscordCommandBridgeCoordinator> commandBridge() {
        return commandBridge;
    }

    Optional<DiscordPunishmentService> punishmentService() {
        return punishments.map(DiscordPunishmentRuntime::service);
    }

    HttpStaffAuthorityClient authority() {
        return authority;
    }

    void resumePunishments(JDA jda) {
        punishments.ifPresent(runtime -> runtime.resume(jda));
    }

    void pausePunishments() {
        punishments.ifPresent(DiscordPunishmentRuntime::pause);
    }

    @Override
    public void close() {
        try {
            punishments.ifPresent(DiscordPunishmentRuntime::close);
        } finally {
            try {
                roleSyncPersistence.ifPresent(DiscordRoleSyncPersistenceRuntime::close);
            } finally {
                try {
                    minecraftProfiles.close();
                } finally {
                    data.close();
                }
            }
        }
    }
}
