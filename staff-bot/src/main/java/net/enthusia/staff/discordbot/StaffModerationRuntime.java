package net.enthusia.staff.discordbot;

import java.nio.file.Path;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import net.dv8tion.jda.api.JDA;
import net.enthusia.staff.persistence.DiscordStaffReadRuntime;

/** Owns every D06/D07/D09/D16 database, authority, component, and enforcement resource. */
final class StaffModerationRuntime implements AutoCloseable {
    private final DiscordStaffReadRuntime data;
    private final StaffModerationReadService readService;
    private final LinkedStaffActorResolver actorResolver;
    private final StaffReadAuthorization readAuthorization;
    private final SignedComponentCodec componentCodec;
    private final MinecraftProfileLookup minecraftProfiles;
    private final Optional<DiscordPunishmentRuntime> punishments;
    private final Optional<DiscordInvestigationRuntime> investigations;

    private StaffModerationRuntime(
            DiscordStaffReadRuntime data,
            StaffModerationReadService reads,
            LinkedStaffActorResolver actors,
            StaffReadAuthorization authorization,
            SignedComponentCodec components,
            MinecraftProfileLookup profiles,
            Optional<DiscordPunishmentRuntime> punishments,
            Optional<DiscordInvestigationRuntime> investigations
    ) {
        this.data = data;
        this.readService = reads;
        this.actorResolver = actors;
        this.readAuthorization = authorization;
        this.componentCodec = components;
        this.minecraftProfiles = profiles;
        this.punishments = punishments;
        this.investigations = investigations;
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
        Optional<DiscordInvestigationConfiguration> investigationConfiguration =
                DiscordInvestigationConfiguration.fromEnvironment(values);
        validateDependencies(configuration, punishmentConfiguration, investigationConfiguration);
        return configuration.map(value -> open(
                value,
                punishmentConfiguration,
                investigationConfiguration,
                guildId,
                interactionCapacity,
                interactionTtl
        ));
    }

    private static void validateDependencies(
            Optional<StaffModerationConfiguration> moderation,
            Optional<DiscordPunishmentConfiguration> punishment,
            Optional<DiscordInvestigationConfiguration> investigation
    ) {
        if (moderation.isEmpty() && (punishment.isPresent() || investigation.isPresent())) {
            throw new IllegalArgumentException("Discord writes require the staff moderation runtime");
        }
        if (investigation.isPresent() && punishment.isEmpty()) {
            throw new IllegalArgumentException("D09 investigations require D07 Discord enforcement");
        }
    }

    private static StaffModerationRuntime open(
            StaffModerationConfiguration configuration,
            Optional<DiscordPunishmentConfiguration> punishmentConfiguration,
            Optional<DiscordInvestigationConfiguration> investigationConfiguration,
            long guildId,
            int interactionCapacity,
            Duration interactionTtl
    ) {
        Clock clock = Clock.systemUTC();
        DiscordStaffReadRuntime data = DiscordStaffReadRuntime.open(configuration.database(), clock);
        MinecraftProfileLookup profiles = null;
        Optional<DiscordPunishmentRuntime> punishments = Optional.empty();
        Optional<DiscordInvestigationRuntime> investigations = Optional.empty();
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
            LinkedStaffActorResolver actors = new LinkedStaffActorResolver(reads, authority);
            StaffReadAuthorization authorization = new StaffReadAuthorization();
            profiles = MinecraftProfileLookup.mojang();
            punishments = punishmentConfiguration.map(value -> DiscordPunishmentRuntime.open(
                    configuration.database(), value, reads, actors, guildId, interactionCapacity, interactionTtl));
            investigations = investigationConfiguration.map(value -> DiscordInvestigationRuntime.open(
                    investigationDependencies(configuration, punishmentConfiguration, reads, actors), value, guildId));
            return new StaffModerationRuntime(
                    data, reads, actors, authorization, components, profiles, punishments, investigations
            );
        } catch (RuntimeException exception) {
            investigations.ifPresent(DiscordInvestigationRuntime::close);
            punishments.ifPresent(DiscordPunishmentRuntime::close);
            if (profiles != null) {
                profiles.close();
            }
            data.close();
            throw exception;
        }
    }

    private static DiscordInvestigationRuntime.Dependencies investigationDependencies(
            StaffModerationConfiguration configuration,
            Optional<DiscordPunishmentConfiguration> punishmentConfiguration,
            StaffModerationReadService reads,
            LinkedStaffActorResolver actors
    ) {
        DiscordPunishmentConfiguration punishment = punishmentConfiguration.orElseThrow();
        return new DiscordInvestigationRuntime.Dependencies(
                configuration.database(), configuration, punishment.authorizationLimits(), reads, actors
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

    Optional<DiscordPunishmentService> punishmentService() {
        return punishments.map(DiscordPunishmentRuntime::service);
    }

    Optional<DiscordInvestigationService> investigationService() {
        return investigations.map(DiscordInvestigationRuntime::service);
    }

    boolean investigationsEnabled() {
        return investigations.isPresent();
    }

    void resumePunishments(JDA jda) {
        punishments.ifPresent(runtime -> runtime.resume(jda));
        investigations.ifPresent(runtime -> runtime.resume(jda));
    }

    void pausePunishments() {
        investigations.ifPresent(DiscordInvestigationRuntime::pause);
        punishments.ifPresent(DiscordPunishmentRuntime::pause);
    }

    @Override
    public void close() {
        try {
            investigations.ifPresent(DiscordInvestigationRuntime::close);
        } finally {
            try {
                punishments.ifPresent(DiscordPunishmentRuntime::close);
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
