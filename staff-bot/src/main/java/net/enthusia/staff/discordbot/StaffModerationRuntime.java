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
    private final HttpStaffAuthorityClient authority;

    private StaffModerationRuntime(
            RuntimeCore core,
            Optional<DiscordPunishmentRuntime> punishments,
            Optional<DiscordInvestigationRuntime> investigations
    ) {
        this.data = core.data();
        this.readService = core.reads();
        this.actorResolver = core.actors();
        this.readAuthorization = core.authorization();
        this.componentCodec = core.components();
        this.minecraftProfiles = core.profiles();
        this.punishments = punishments;
        this.investigations = investigations;
        this.authority = core.authority();
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
        RuntimeCore core = createCore(data, configuration, clock, interactionCapacity, interactionTtl);
        Optional<DiscordPunishmentRuntime> punishments = Optional.empty();
        Optional<DiscordInvestigationRuntime> investigations = Optional.empty();
        try {
            punishments = punishmentConfiguration.map(value -> DiscordPunishmentRuntime.open(
                    configuration.database(), value, core.reads(), core.actors(),
                    new HttpMinecraftPunishmentPreparer(core.authority()),
                    guildId, interactionCapacity, interactionTtl
            ));
            investigations = investigationConfiguration.map(value -> DiscordInvestigationRuntime.open(
                    investigationDependencies(
                            configuration, punishmentConfiguration, core.reads(), core.actors()
                    ),
                    value,
                    guildId
            ));
            return new StaffModerationRuntime(core, punishments, investigations);
        } catch (RuntimeException exception) {
            closeFailedOpen(core, punishments, investigations);
            throw exception;
        }
    }

    private static RuntimeCore createCore(
            DiscordStaffReadRuntime data,
            StaffModerationConfiguration configuration,
            Clock clock,
            int interactionCapacity,
            Duration interactionTtl
    ) {
        try {
            StaffModerationReadService reads = new StaffModerationReadService(data, clock);
            HttpStaffAuthorityClient authority = new HttpStaffAuthorityClient(
                    configuration.authorityUri(),
                    configuration.authoritySecret(),
                    configuration.authorityTransport()
            );
            SignedComponentCodec components = new SignedComponentCodec(
                    clock, interactionTtl, configuration.componentSecret(), new SecureRandom(),
                    new InteractionReplayGuard(interactionCapacity, interactionTtl)
            );
            LinkedStaffActorResolver actors = new LinkedStaffActorResolver(reads, authority);
            return new RuntimeCore(
                    data, reads, actors, new StaffReadAuthorization(), components,
                    MinecraftProfileLookup.mojang(), authority
            );
        } catch (RuntimeException exception) {
            data.close();
            throw exception;
        }
    }

    private static void closeFailedOpen(
            RuntimeCore core,
            Optional<DiscordPunishmentRuntime> punishments,
            Optional<DiscordInvestigationRuntime> investigations
    ) {
        investigations.ifPresent(DiscordInvestigationRuntime::close);
        punishments.ifPresent(DiscordPunishmentRuntime::close);
        core.profiles().close();
        core.data().close();
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

    private record RuntimeCore(
            DiscordStaffReadRuntime data,
            StaffModerationReadService reads,
            LinkedStaffActorResolver actors,
            StaffReadAuthorization authorization,
            SignedComponentCodec components,
            MinecraftProfileLookup profiles,
            HttpStaffAuthorityClient authority
    ) {
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

    Optional<DiscordPunishmentService> punishmentService() {
        return punishments.map(DiscordPunishmentRuntime::service);
    }

    Optional<CrossPlatformActionService> crossPlatformActions() {
        return punishments.map(DiscordPunishmentRuntime::crossPlatformActions);
    }

    Optional<DiscordInvestigationService> investigationService() {
        return investigations.map(DiscordInvestigationRuntime::service);
    }

    boolean investigationsEnabled() {
        return investigations.isPresent();
    }

    HttpStaffAuthorityClient authority() {
        return authority;
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
