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

/** Owns D06/D07/D09/D13/D16 authority, investigation, role-sync and review resources. */
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
    private final Optional<ManagedRoleShadowService> managedRoleShadow;
    private final Optional<DiscordPunishmentRuntime> punishments;
    private final HttpStaffAuthorityClient authority;
    private final Optional<DiscordInvestigationRuntime> investigations;

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
            Optional<ManagedRoleShadowService> managedRoleShadow,
            Optional<DiscordPunishmentRuntime> punishments,
            HttpStaffAuthorityClient authority,
            Optional<DiscordInvestigationRuntime> investigations
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
        this.managedRoleShadow = managedRoleShadow;
        this.punishments = punishments;
        this.authority = authority;
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
        Optional<DiscordCommandBridgeConfiguration> commandConfiguration =
                DiscordCommandBridgeConfiguration.fromEnvironment(values);
        Optional<DiscordInvestigationConfiguration> investigationConfiguration =
                DiscordInvestigationConfiguration.fromEnvironment(values);
        validateDependencies(configuration, punishmentConfiguration, investigationConfiguration);
        if (configuration.isEmpty() && commandConfiguration.isPresent()) {
            throw new IllegalArgumentException("Discord command bridge requires the staff moderation runtime");
        }
        return configuration.map(value -> open(
                value,
                punishmentConfiguration,
                commandConfiguration,
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
            Optional<DiscordCommandBridgeConfiguration> commandConfiguration,
            Optional<DiscordInvestigationConfiguration> investigationConfiguration,
            long guildId,
            int interactionCapacity,
            Duration interactionTtl
    ) {
        Clock clock = Clock.systemUTC();
        DiscordStaffReadRuntime data = DiscordStaffReadRuntime.open(configuration.database(), clock);
        MinecraftProfileLookup profiles = null;
        DiscordRoleSyncPersistenceRuntime rolePersistence = null;
        Optional<DiscordPunishmentRuntime> punishments = Optional.empty();
        Optional<DiscordInvestigationRuntime> investigations = Optional.empty();
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
            Optional<ManagedRoleShadowService> managedRoleShadow = configuration.managedRoleShadow().map(value ->
                    new ManagedRoleShadowService(data, value, new com.fasterxml.jackson.databind.ObjectMapper()));
            punishments = punishmentConfiguration.map(value -> DiscordPunishmentRuntime.open(
                    configuration.database(), value, reads, actors, guildId, interactionCapacity, interactionTtl));
            investigations = investigationConfiguration.map(value -> DiscordInvestigationRuntime.open(
                    investigationDependencies(configuration, punishmentConfiguration, reads, actors), value, guildId));
            return new StaffModerationRuntime(
                    data, reads, actors, authorization, components, profiles,
                    roleSync, Optional.ofNullable(rolePersistence), console, managedRoleShadow,
                    punishments, authority, investigations
            );
        } catch (RuntimeException exception) {
            investigations.ifPresent(DiscordInvestigationRuntime::close);
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

    net.enthusia.staff.persistence.DiscordPunishmentHistoryReader.Page discordHistory(long guildId, long userId) {
        return data.discordHistory(
                new net.enthusia.staff.domain.moderation.DiscordGuildId(Long.toUnsignedString(guildId)),
                new net.enthusia.staff.domain.moderation.DiscordUserId(Long.toUnsignedString(userId)), 50);
    }

    net.enthusia.staff.persistence.DiscordStaffReadRuntime.ReviewPulse reviewPulse() {
        return data.reviewPulse(java.time.Instant.now());
    }

    java.util.List<net.enthusia.staff.persistence.DiscordStaffReadRuntime.PendingReview> pendingReviews(int limit) {
        return data.pendingReviews(java.time.Instant.now(), limit);
    }

    java.util.List<net.enthusia.staff.persistence.DiscordStaffReadRuntime.PendingReport> pendingReports(int limit) {
        return data.pendingReports(limit);
    }

    java.util.List<net.enthusia.staff.persistence.DiscordStaffReadRuntime.PendingAltAlert> pendingAltAlerts(int limit) {
        if (investigations.isEmpty()) {
            return java.util.List.of();
        }
        return data.pendingAltAlerts(limit);
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

    Optional<ManagedRoleShadowService> managedRoleShadow() {
        return managedRoleShadow;
    }

    Optional<DiscordPunishmentService> punishmentService() {
        return punishments.map(DiscordPunishmentRuntime::service);
    }

    HttpStaffAuthorityClient authority() {
        return authority;
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
}
