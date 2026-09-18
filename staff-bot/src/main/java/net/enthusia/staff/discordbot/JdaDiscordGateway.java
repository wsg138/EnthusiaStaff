package net.enthusia.staff.discordbot;

import java.time.Duration;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.ApplicationInfo;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.ExceptionEvent;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.events.session.SessionDisconnectEvent;
import net.dv8tion.jda.api.events.session.SessionRecreateEvent;
import net.dv8tion.jda.api.events.session.SessionResumeEvent;
import net.dv8tion.jda.api.events.session.ShutdownEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.ChunkingFilter;
import net.dv8tion.jda.api.utils.MemberCachePolicy;
import net.dv8tion.jda.api.utils.cache.CacheFlag;

/** JDA 6.5 adapter. JDA owns Discord REST bucket/global rate limits and Gateway reconnect scheduling. */
final class JdaDiscordGateway implements DiscordGateway {
    private static final System.Logger LOGGER = System.getLogger(JdaDiscordGateway.class.getName());

    private final StaffBotConfiguration configuration;
    private final StaffBotWorkerPool workers;
    private final InteractionReplayGuard interactions;
    private final Optional<StaffModerationRuntime> moderation;
    private final Object lifecycleLock = new Object();
    private JDA jda;
    private JdaStaffModerationListener moderationListener;
    private JdaDiscordInvestigationEvidenceListener investigationEvidenceListener;
    private JdaModerationUiPreviewListener previewListener;

    JdaDiscordGateway(StaffBotConfiguration configuration) {
        this(configuration, null, null, Optional.empty());
    }

    JdaDiscordGateway(
            StaffBotConfiguration configuration,
            StaffBotWorkerPool workers,
            InteractionReplayGuard interactions,
            Optional<StaffModerationRuntime> moderation
    ) {
        this.configuration = configuration;
        this.workers = workers;
        this.interactions = interactions;
        this.moderation = moderation == null ? Optional.empty() : moderation;
        validateInteractionResources();
    }

    private void validateInteractionResources() {
        if (moderation.isPresent() && (workers == null || interactions == null)) {
            throw new IllegalArgumentException("moderation runtime requires bounded runtime resources");
        }
        if (configuration.uiPreviewEnabled() && interactions == null) {
            throw new IllegalArgumentException("UI preview requires replay protection");
        }
    }

    @Override
    public void start(DiscordGatewayObserver observer) {
        synchronized (lifecycleLock) {
            if (jda != null) {
                throw new IllegalStateException("Discord gateway already started");
            }
            SessionListener listener = new SessionListener(
                    configuration.environment(), observer, this::disableInteractions);
            JDABuilder builder = baseBuilder(listener);
            addInteractionListener(builder);
            jda = builder.build();
        }
    }

    private JDABuilder baseBuilder(SessionListener listener) {
        boolean investigationsEnabled = moderation.map(StaffModerationRuntime::investigationsEnabled).orElse(false);
        return JDABuilder.createLight(configuration.discordToken(), requiredGatewayIntents(investigationsEnabled))
                .enableCache(requiredCacheFlags())
                .setMemberCachePolicy(MemberCachePolicy.NONE)
                .setChunkingFilter(ChunkingFilter.NONE)
                .setAutoReconnect(true)
                .setMaxReconnectDelay(configuration.maxReconnectDelaySeconds())
                .setEnableShutdownHook(false)
                .setEventPassthrough(false)
                .addEventListeners(listener);
    }

    private void addInteractionListener(JDABuilder builder) {
        if (configuration.uiPreviewEnabled()) {
            previewListener = new JdaModerationUiPreviewListener(
                    configuration.environment().guildId(),
                    interactions,
                    configuration.interactionCapacity(),
                    configuration.previewWebConfig(),
                    configuration.discordToken(),
                    moderation
            );
            previewListener.startWeb();
            builder.addEventListeners(previewListener);
            return;
        }
        moderation.ifPresent(runtime -> addModerationListeners(builder, runtime));
    }

    private void addModerationListeners(JDABuilder builder, StaffModerationRuntime runtime) {
        moderationListener = new JdaStaffModerationListener(
                configuration.environment().guildId(), workers, interactions, runtime);
        builder.addEventListeners(moderationListener);
        runtime.investigationService().ifPresent(service -> {
            investigationEvidenceListener = new JdaDiscordInvestigationEvidenceListener(
                    configuration.environment().guildId(), workers, service);
            builder.addEventListeners(investigationEvidenceListener);
        });
    }

    @Override
    public void enableInteractions() {
        synchronized (lifecycleLock) {
            if (jda == null) {
                return;
            }
            moderation.ifPresent(runtime -> runtime.resumePunishments(jda));
            if (previewListener != null) {
                previewListener.enable(jda);
            } else if (moderationListener != null) {
                moderationListener.enable(jda);
            }
            if (investigationEvidenceListener != null) {
                investigationEvidenceListener.enable();
            }
        }
    }

    private void disableInteractions() {
        synchronized (lifecycleLock) {
            moderation.ifPresent(StaffModerationRuntime::pausePunishments);
            if (previewListener != null) {
                previewListener.disable();
            }
            if (moderationListener != null) {
                moderationListener.disable();
            }
            if (investigationEvidenceListener != null) {
                investigationEvidenceListener.disable();
            }
        }
    }

    @Override
    public void shutdown() {
        synchronized (lifecycleLock) {
            closeListeners();
            if (jda != null) {
                jda.shutdown();
            }
        }
    }

    @Override
    public void shutdownNow() {
        synchronized (lifecycleLock) {
            closeListeners();
            if (jda != null) {
                jda.shutdownNow();
            }
        }
    }

    private void closeListeners() {
        moderation.ifPresent(StaffModerationRuntime::pausePunishments);
        if (previewListener != null) {
            previewListener.close();
        }
        if (moderationListener != null) {
            moderationListener.disable();
        }
        if (investigationEvidenceListener != null) {
            investigationEvidenceListener.disable();
        }
    }

    @Override
    public boolean awaitShutdown(Duration timeout) throws InterruptedException {
        JDA current;
        synchronized (lifecycleLock) {
            current = jda;
        }
        return current == null || current.awaitShutdown(timeout.toMillis(), TimeUnit.MILLISECONDS);
    }

    static Set<GatewayIntent> requiredGatewayIntents(boolean investigationsEnabled) {
        return investigationsEnabled
                ? Set.of(GatewayIntent.GUILD_MESSAGES, GatewayIntent.MESSAGE_CONTENT)
                : Set.of();
    }

    static Set<CacheFlag> requiredCacheFlags() {
        return Set.of(CacheFlag.MEMBER_OVERRIDES);
    }

    static final class CallbackFence {
        private final Object lock = new Object();
        private long generation;

        long beginResolution() {
            synchronized (lock) {
                return ++generation;
            }
        }

        void invalidate() {
            synchronized (lock) {
                generation++;
            }
        }

        boolean runIfCurrent(long expectedGeneration, Runnable callback) {
            synchronized (lock) {
                if (generation != expectedGeneration) {
                    return false;
                }
                callback.run();
                return true;
            }
        }
    }

    private static final class SessionListener extends ListenerAdapter {
        private final StaffBotEnvironment environment;
        private final DiscordGatewayObserver observer;
        private final Runnable disableInteractions;
        private final CallbackFence identityCallbacks = new CallbackFence();

        private SessionListener(
                StaffBotEnvironment environment,
                DiscordGatewayObserver observer,
                Runnable disableInteractions
        ) {
            this.environment = environment;
            this.observer = observer;
            this.disableInteractions = disableInteractions;
        }

        @Override
        public void onReady(ReadyEvent event) {
            resolveIdentity(event.getJDA());
        }

        @Override
        public void onSessionResume(SessionResumeEvent event) {
            resolveIdentity(event.getJDA());
        }

        @Override
        public void onSessionRecreate(SessionRecreateEvent event) {
            resolveIdentity(event.getJDA());
        }

        @Override
        public void onSessionDisconnect(SessionDisconnectEvent event) {
            disableInteractions.run();
            identityCallbacks.invalidate();
            observer.onDisconnected();
        }

        @Override
        public void onShutdown(ShutdownEvent event) {
            disableInteractions.run();
            identityCallbacks.invalidate();
            observer.onShutdown();
        }

        @Override
        public void onException(ExceptionEvent event) {
            Throwable cause = event.getCause();
            String type = cause == null ? "unknown" : cause.getClass().getSimpleName();
            if (LOGGER.isLoggable(System.Logger.Level.WARNING)) {
                LOGGER.log(System.Logger.Level.WARNING, "discord_gateway_exception type={0}", type);
            }
        }

        private void resolveIdentity(JDA api) {
            long generation = identityCallbacks.beginResolution();
            api.retrieveApplicationInfo().queue(
                    applicationInfo -> identityCallbacks.runIfCurrent(
                            generation, () -> observer.onIdentityResolved(snapshot(api, applicationInfo))),
                    failure -> identityCallbacks.runIfCurrent(
                            generation, () -> observer.onFatal("application_info_request_failed")));
        }

        private DiscordRuntimeIdentity snapshot(JDA api, ApplicationInfo applicationInfo) {
            Set<Long> guildIds = api.getGuilds().stream()
                    .map(Guild::getIdLong)
                    .collect(Collectors.toUnmodifiableSet());

            boolean channelPresent = false;
            boolean channelOperational = false;
            if (environment.testChannelId().isPresent()) {
                long channelId = environment.testChannelId().getAsLong();
                TextChannel channel = api.getTextChannelById(channelId);
                channelPresent = channel != null && channel.getGuild().getIdLong() == environment.guildId();
                if (channelPresent) {
                    channelOperational = channel.getGuild().getSelfMember().hasPermission(
                            channel, Permission.VIEW_CHANNEL, Permission.MESSAGE_SEND);
                }
            }

            return new DiscordRuntimeIdentity(
                    applicationInfo.getIdLong(), applicationInfo.isBotPublic(), guildIds,
                    channelPresent, channelOperational);
        }
    }
}
