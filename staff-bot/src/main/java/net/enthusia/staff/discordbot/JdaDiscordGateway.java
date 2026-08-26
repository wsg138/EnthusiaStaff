package net.enthusia.staff.discordbot;

import java.time.Duration;
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
import net.dv8tion.jda.api.utils.ChunkingFilter;
import net.dv8tion.jda.api.utils.MemberCachePolicy;

/** JDA 6.5 adapter. JDA owns Discord REST bucket/global rate limits and Gateway reconnect scheduling. */
final class JdaDiscordGateway implements DiscordGateway {
    private static final System.Logger LOGGER = System.getLogger(JdaDiscordGateway.class.getName());

    private final StaffBotConfiguration configuration;
    private final Object lifecycleLock = new Object();
    private JDA jda;

    JdaDiscordGateway(StaffBotConfiguration configuration) {
        this.configuration = configuration;
    }

    @Override
    public void start(DiscordGatewayObserver observer) {
        synchronized (lifecycleLock) {
            if (jda != null) {
                throw new IllegalStateException("Discord gateway already started");
            }
            SessionListener listener = new SessionListener(configuration.environment(), observer);
            jda = JDABuilder.createLight(configuration.discordToken(), Set.of())
                    .setMemberCachePolicy(MemberCachePolicy.NONE)
                    .setChunkingFilter(ChunkingFilter.NONE)
                    .setAutoReconnect(true)
                    .setMaxReconnectDelay(configuration.maxReconnectDelaySeconds())
                    .setEnableShutdownHook(false)
                    .setEventPassthrough(false)
                    .addEventListeners(listener)
                    .build();
        }
    }

    @Override
    public void shutdown() {
        synchronized (lifecycleLock) {
            if (jda != null) {
                jda.shutdown();
            }
        }
    }

    @Override
    public void shutdownNow() {
        synchronized (lifecycleLock) {
            if (jda != null) {
                jda.shutdownNow();
            }
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
        private final CallbackFence identityCallbacks = new CallbackFence();

        private SessionListener(StaffBotEnvironment environment, DiscordGatewayObserver observer) {
            this.environment = environment;
            this.observer = observer;
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
            identityCallbacks.invalidate();
            observer.onDisconnected();
        }

        @Override
        public void onShutdown(ShutdownEvent event) {
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
                            generation,
                            () -> observer.onIdentityResolved(snapshot(api, applicationInfo))),
                    failure -> identityCallbacks.runIfCurrent(
                            generation,
                            () -> observer.onFatal("application_info_request_failed")));
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
                            channel,
                            Permission.VIEW_CHANNEL,
                            Permission.MESSAGE_SEND);
                }
            }

            return new DiscordRuntimeIdentity(
                    applicationInfo.getIdLong(),
                    applicationInfo.isBotPublic(),
                    guildIds,
                    channelPresent,
                    channelOperational);
        }
    }
}
