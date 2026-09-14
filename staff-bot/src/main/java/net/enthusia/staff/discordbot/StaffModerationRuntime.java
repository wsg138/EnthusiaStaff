package net.enthusia.staff.discordbot;

import java.nio.file.Path;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.util.Optional;
import net.enthusia.staff.persistence.DiscordRoleSyncPersistenceRuntime;
import net.enthusia.staff.persistence.DiscordStaffReadRuntime;

/** Owns every D06/D13/D16 database, authority, component and role-sync resource. */
final class StaffModerationRuntime implements AutoCloseable {
    private final DiscordStaffReadRuntime data;
    private final StaffModerationReadService readService;
    private final LinkedStaffActorResolver actorResolver;
    private final StaffReadAuthorization readAuthorization;
    private final SignedComponentCodec componentCodec;
    private final MinecraftProfileLookup minecraftProfiles;
    private final Optional<DiscordRoleSyncService> roleSyncService;
    private final Optional<DiscordRoleSyncPersistenceRuntime> roleSyncPersistence;

    private StaffModerationRuntime(
            DiscordStaffReadRuntime data,
            StaffModerationReadService reads,
            LinkedStaffActorResolver actors,
            StaffReadAuthorization authorization,
            SignedComponentCodec components,
            MinecraftProfileLookup profiles,
            Optional<DiscordRoleSyncService> roleSync,
            Optional<DiscordRoleSyncPersistenceRuntime> rolePersistence
    ) {
        this.data = data;
        this.readService = reads;
        this.actorResolver = actors;
        this.readAuthorization = authorization;
        this.componentCodec = components;
        this.minecraftProfiles = profiles;
        this.roleSyncService = roleSync;
        this.roleSyncPersistence = rolePersistence;
    }

    static Optional<StaffModerationRuntime> open(
            Optional<Path> configFile,
            int interactionCapacity,
            Duration interactionTtl
    ) {
        Optional<StaffModerationConfiguration> configuration = configFile.isPresent()
                ? Optional.of(StaffModerationConfiguration.fromFile(configFile.orElseThrow()))
                : StaffModerationConfiguration.fromSystemEnvironment();
        return configuration.map(value -> open(value, interactionCapacity, interactionTtl));
    }

    private static StaffModerationRuntime open(
            StaffModerationConfiguration configuration,
            int interactionCapacity,
            Duration interactionTtl
    ) {
        Clock clock = Clock.systemUTC();
        DiscordStaffReadRuntime data = DiscordStaffReadRuntime.open(configuration.database(), clock);
        DiscordRoleSyncPersistenceRuntime rolePersistence = null;
        try {
            StaffModerationReadService reads = new StaffModerationReadService(data, clock);
            HttpStaffAuthorityClient authority = new HttpStaffAuthorityClient(
                    configuration.authorityUri(),
                    configuration.authoritySecret(),
                    configuration.authorityTransport());
            SignedComponentCodec components = componentCodec(clock, interactionCapacity, interactionTtl, configuration);
            LinkedStaffActorResolver actors = new LinkedStaffActorResolver(reads, authority);
            Optional<DiscordRoleSyncService> roleSync = Optional.empty();
            if (configuration.roleSync().isPresent()) {
                rolePersistence = DiscordRoleSyncPersistenceRuntime.open(configuration.roleSyncDatabase().orElseThrow());
                roleSync = Optional.of(new DiscordRoleSyncService(
                        rolePersistence,
                        authority,
                        configuration.roleSync().orElseThrow(),
                        clock
                ));
            }
            MinecraftProfileLookup profiles = MinecraftProfileLookup.mojang();
            return new StaffModerationRuntime(
                    data,
                    reads,
                    actors,
                    new StaffReadAuthorization(),
                    components,
                    profiles,
                    roleSync,
                    Optional.ofNullable(rolePersistence)
            );
        } catch (RuntimeException exception) {
            if (rolePersistence != null) {
                rolePersistence.close();
            }
            data.close();
            throw exception;
        }
    }

    private static SignedComponentCodec componentCodec(
            Clock clock,
            int interactionCapacity,
            Duration interactionTtl,
            StaffModerationConfiguration configuration
    ) {
        InteractionReplayGuard componentReplay = new InteractionReplayGuard(interactionCapacity, interactionTtl);
        return new SignedComponentCodec(
                clock,
                interactionTtl,
                configuration.componentSecret(),
                new SecureRandom(),
                componentReplay
        );
    }

    StaffModerationReadService reads() {
        return readService;
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

    @Override
    public void close() {
        try {
            minecraftProfiles.close();
        } finally {
            try {
                roleSyncPersistence.ifPresent(DiscordRoleSyncPersistenceRuntime::close);
            } finally {
                data.close();
            }
        }
    }
}
