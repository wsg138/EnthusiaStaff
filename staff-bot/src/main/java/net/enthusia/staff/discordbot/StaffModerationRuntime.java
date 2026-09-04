package net.enthusia.staff.discordbot;

import java.nio.file.Path;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.util.Optional;
import net.enthusia.staff.persistence.DiscordStaffReadRuntime;

/** Owns every D06 database/authority/component resource. */
final class StaffModerationRuntime implements AutoCloseable {
    private final DiscordStaffReadRuntime data;
    private final StaffModerationReadService readService;
    private final LinkedStaffActorResolver actorResolver;
    private final StaffReadAuthorization readAuthorization;
    private final SignedComponentCodec componentCodec;

    private StaffModerationRuntime(
            DiscordStaffReadRuntime data,
            StaffModerationReadService reads,
            LinkedStaffActorResolver actors,
            StaffReadAuthorization authorization,
            SignedComponentCodec components
    ) {
        this.data = data;
        this.readService = reads;
        this.actorResolver = actors;
        this.readAuthorization = authorization;
        this.componentCodec = components;
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
        try {
            StaffModerationReadService reads = new StaffModerationReadService(data, clock);
            StaffAuthorityClient authority = new HttpStaffAuthorityClient(
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
            return new StaffModerationRuntime(
                    data,
                    reads,
                    new LinkedStaffActorResolver(reads, authority),
                    new StaffReadAuthorization(),
                    components
            );
        } catch (RuntimeException exception) {
            data.close();
            throw exception;
        }
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

    @Override
    public void close() {
        data.close();
    }
}
