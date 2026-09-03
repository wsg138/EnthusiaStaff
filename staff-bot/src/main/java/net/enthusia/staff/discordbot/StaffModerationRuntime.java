package net.enthusia.staff.discordbot;

import java.security.SecureRandom;
import java.time.Clock;
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

    static Optional<StaffModerationRuntime> openFromEnvironment(
            int interactionCapacity,
            java.time.Duration interactionTtl
    ) {
        return StaffModerationConfiguration.fromSystemEnvironment().map(configuration -> {
            Clock clock = Clock.systemUTC();
            DiscordStaffReadRuntime data = DiscordStaffReadRuntime.open(configuration.database(), clock);
            try {
                StaffModerationReadService reads = new StaffModerationReadService(data, clock);
                StaffAuthorityClient authority = new HttpStaffAuthorityClient(
                        configuration.authorityUri(),
                        configuration.authoritySecret()
                );
                InteractionReplayGuard componentReplay = new InteractionReplayGuard(
                        interactionCapacity,
                        interactionTtl
                );
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
        });
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
