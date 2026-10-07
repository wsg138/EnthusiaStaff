package net.enthusia.staff.discordbot;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.ApplicationInfo;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.ExceptionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.events.session.SessionDisconnectEvent;
import net.dv8tion.jda.api.events.session.SessionRecreateEvent;
import net.dv8tion.jda.api.events.session.SessionResumeEvent;
import net.dv8tion.jda.api.events.session.ShutdownEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.requests.restaction.MessageCreateAction;
import net.dv8tion.jda.api.utils.ChunkingFilter;
import net.dv8tion.jda.api.utils.FileUpload;
import net.dv8tion.jda.api.utils.MemberCachePolicy;
import net.dv8tion.jda.api.utils.cache.CacheFlag;
import net.enthusia.staff.protocol.ChatBridgeArtifact;
import net.enthusia.staff.protocol.ChatBridgeInboundMessage;
import net.enthusia.staff.protocol.ChatBridgeOutboundMessage;
import net.enthusia.staff.protocol.ChatBridgeRenderedMessage;

/** JDA 6.5 adapter. JDA owns Discord REST bucket/global rate limits and Gateway reconnect scheduling. */
final class JdaDiscordGateway implements DiscordGateway, DiscordChatEgress, DiscordRenderedChatEgress {
    private static final System.Logger LOGGER = System.getLogger(JdaDiscordGateway.class.getName());

    private final StaffBotConfiguration configuration;
    private final String discordToken;
    private final boolean staffInteractionsEnabled;
    private final StaffBotWorkerPool workers;
    private final InteractionReplayGuard interactions;
    private static final Duration INBOUND_CHAT_LIFETIME = Duration.ofSeconds(30);

    private final Optional<StaffModerationRuntime> moderation;
    private final Optional<StaffBotChatBridgeConfiguration> chatConfiguration;
    private final DiscordChatSenderIdentityResolver chatSenderIdentities;
    private final Object lifecycleLock = new Object();
    private DiscordChatIngress chatIngress;
    private JDA jda;
    private JdaStaffModerationListener moderationListener;
    private JdaModerationUiPreviewListener previewListener;
    private ModerationReadApiServer productionReadApi;
    private AiModerationReadApiServer aiModerationReadApi;
    private DiscordRoleSyncCoordinator roleSyncCoordinator;
    private ManagedRoleShadowCoordinator managedRoleShadowCoordinator;

    JdaDiscordGateway(StaffBotConfiguration configuration) {
        this(configuration, null, null, Optional.empty(), Optional.empty());
    }

    JdaDiscordGateway(
            StaffBotConfiguration configuration,
            StaffBotWorkerPool workers,
            InteractionReplayGuard interactions,
            Optional<StaffModerationRuntime> moderation
    ) {
        this(configuration, workers, interactions, moderation, Optional.empty());
    }

    JdaDiscordGateway(
            StaffBotConfiguration configuration,
            StaffBotWorkerPool workers,
            InteractionReplayGuard interactions,
            Optional<StaffModerationRuntime> moderation,
            Optional<StaffBotChatBridgeConfiguration> chatConfiguration
    ) {
        this(
                configuration,
                discordToken,
                true,
                workers,
                interactions,
                moderation,
                chatConfiguration
        );
    }

    static JdaDiscordGateway publicChat(
            StaffBotConfiguration configuration,
            PublicChatDiscordConfiguration publicChat,
            Optional<StaffModerationRuntime> identitySource,
            StaffBotChatBridgeConfiguration chatConfiguration
    ) {
        Objects.requireNonNull(publicChat, "publicChat");
        return new JdaDiscordGateway(
                configuration,
                publicChat.token(),
                false,
                null,
                null,
                identitySource,
                Optional.of(chatConfiguration)
        );
    }

    private JdaDiscordGateway(
            StaffBotConfiguration configuration,
            String discordToken,
            boolean staffInteractionsEnabled,
            StaffBotWorkerPool workers,
            InteractionReplayGuard interactions,
            Optional<StaffModerationRuntime> moderation,
            Optional<StaffBotChatBridgeConfiguration> chatConfiguration
    ) {
        this.configuration = Objects.requireNonNull(configuration, "configuration");
        this.discordToken = Objects.requireNonNull(discordToken, "discordToken");
        this.staffInteractionsEnabled = staffInteractionsEnabled;
        this.workers = workers;
        this.interactions = interactions;
        this.moderation = moderation == null ? Optional.empty() : moderation;
        this.chatConfiguration = chatConfiguration == null ? Optional.empty() : chatConfiguration;
        this.chatSenderIdentities = new DiscordChatSenderIdentityResolver(playerId ->
                this.moderation.flatMap(current ->
                        current.reads().minecraftTarget(playerId).discordId().map(value -> value.value())));
        validateInteractionResources();
        validateRoleSyncBoundary();
    }

    void installChatIngress(DiscordChatIngress ingress) {
        Objects.requireNonNull(ingress, "ingress");
        synchronized (lifecycleLock) {
            if (jda != null) {
                throw new IllegalStateException("Discord chat ingress must be installed before gateway start");
            }
            if (chatConfiguration.map(configuration -> configuration.ingressRoutes().isEmpty()).orElse(true)) {
                throw new IllegalStateException("Discord chat ingress is not configured");
            }
            if (chatIngress != null) {
                throw new IllegalStateException("Discord chat ingress is already installed");
            }
            chatIngress = ingress;
        }
    }

    private void validateInteractionResources() {
        if (!staffInteractionsEnabled) {
            return;
        }
        if (moderation.isPresent() && (workers == null || interactions == null)) {
            throw new IllegalArgumentException("moderation runtime requires bounded runtime resources");
        }
        if (configuration.uiPreviewEnabled() && interactions == null) {
            throw new IllegalArgumentException("UI preview requires replay protection");
        }
        if (configuration.aiReadToken().isPresent() && moderation.isEmpty()) {
            throw new IllegalArgumentException("AI moderation read API requires moderation runtime");
        }
    }

    private void validateRoleSyncBoundary() {
        if (!staffInteractionsEnabled) {
            return;
        }
        moderation.flatMap(StaffModerationRuntime::roleSync).ifPresent(service -> {
            if (!roleSyncModeAllowed(configuration.environment(), service.configuration().mode())) {
                throw new IllegalArgumentException("ES-D13 does not authorize production role-sync enforcement");
            }
        });
    }

    static boolean roleSyncModeAllowed(StaffBotEnvironment environment, DiscordRoleSyncConfiguration.Mode mode) {
        if (environment == null || mode == null) {
            throw new IllegalArgumentException("role-sync authorization inputs must be present");
        }
        return environment != StaffBotEnvironment.PRODUCTION || mode != DiscordRoleSyncConfiguration.Mode.ENFORCE;
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
            addChatIngressListener(builder);
            jda = builder.build();
        }
    }

    private JDABuilder baseBuilder(SessionListener listener) {
        return JDABuilder.createLight(discordToken, gatewayIntents())
                .enableCache(staffInteractionsEnabled ? requiredCacheFlags() : Set.of())
                .setMemberCachePolicy(MemberCachePolicy.NONE)
                .setChunkingFilter(ChunkingFilter.NONE)
                .setAutoReconnect(true)
                .setMaxReconnectDelay(configuration.maxReconnectDelaySeconds())
                .setEnableShutdownHook(false)
                .setEventPassthrough(false)
                .addEventListeners(listener);
    }

    private Set<GatewayIntent> gatewayIntents() {
        return gatewayIntents(
                staffInteractionsEnabled
                        && moderation.flatMap(StaffModerationRuntime::managedRoleShadow).isPresent(),
                chatConfiguration.map(configuration -> !configuration.ingressRoutes().isEmpty()).orElse(false)
        );
    }

    static Set<GatewayIntent> gatewayIntents(boolean managedRoleShadowEnabled) {
        return gatewayIntents(managedRoleShadowEnabled, false);
    }

    static Set<GatewayIntent> gatewayIntents(boolean managedRoleShadowEnabled, boolean discordChatIngressEnabled) {
        EnumSet<GatewayIntent> intents = EnumSet.noneOf(GatewayIntent.class);
        if (managedRoleShadowEnabled) {
            intents.add(GatewayIntent.GUILD_MEMBERS);
        }
        if (discordChatIngressEnabled) {
            intents.add(GatewayIntent.GUILD_MESSAGES);
            intents.add(GatewayIntent.MESSAGE_CONTENT);
        }
        return Set.copyOf(intents);
    }

    private void addChatIngressListener(JDABuilder builder) {
        Optional<StaffBotChatBridgeConfiguration> configured = chatConfiguration
                .filter(configuration -> !configuration.ingressRoutes().isEmpty());
        if (configured.isEmpty()) {
            return;
        }
        DiscordChatIngress ingress = chatIngress;
        if (ingress == null) {
            throw new IllegalStateException("configured Discord chat ingress has no transport sink");
        }
        builder.addEventListeners(new DiscordChatIngressListener(
                configuration.environment().guildId(),
                configured.orElseThrow().ingressRoutes(),
                ingress
        ));
    }

    private void addInteractionListener(JDABuilder builder) {
        if (!staffInteractionsEnabled) {
            return;
        }
        if (configuration.uiPreviewEnabled()) {
            previewListener = new JdaModerationUiPreviewListener(
                    configuration.environment().guildId(),
                    interactions,
                    configuration.interactionCapacity(),
                    configuration.previewWebConfig(),
                    discordToken,
                    moderation
            );
            previewListener.startWeb();
            builder.addEventListeners(previewListener);
            return;
        }
        moderation.ifPresent(runtime -> {
            moderationListener = new JdaStaffModerationListener(
                    configuration.environment().guildId(), workers, interactions, runtime,
                    configuration.moderationWebUri(), configuration.discordToken());
            builder.addEventListeners(moderationListener);
        });
    }

    @Override
    public void enableInteractions() {
        synchronized (lifecycleLock) {
            if (jda == null) {
                return;
            }
            moderation.ifPresent(runtime -> runtime.resumePunishments(jda));
            if (configuration.moderationWebUri().isPresent() && productionReadApi == null) {
                try {
                    ModerationReadApiService service = new ModerationReadApiService(
                            configuration.environment().guildId(), moderation.orElseThrow(), jda);
                    productionReadApi = new ModerationReadApiServer(discordToken, service,
                            configuration.moderationWebUri().orElseThrow().toString());
                    productionReadApi.start();
                } catch (java.io.IOException exception) {
                    throw new IllegalStateException("production moderation read API failed to start", exception);
                }
            }
            if (configuration.aiReadToken().isPresent() && aiModerationReadApi == null) {
                try {
                    AiModerationReadApiService service =
                            new AiModerationReadApiService(moderation.orElseThrow());
                    aiModerationReadApi = new AiModerationReadApiServer(
                            configuration.aiReadToken().orElseThrow(),
                            service
                    );
                    aiModerationReadApi.start();
                } catch (java.io.IOException exception) {
                    throw new IllegalStateException("AI moderation read API failed to start", exception);
                }
            }
            if (previewListener != null) {
                previewListener.enable(jda);
            } else if (moderationListener != null) {
                moderationListener.enable(jda);
            }
            enableRoleSync();
            enableManagedRoleShadow();
        }
    }

    private void enableRoleSync() {
        moderation.flatMap(StaffModerationRuntime::roleSync).ifPresent(service -> {
            Guild guild = jda.getGuildById(configuration.environment().guildId());
            if (guild == null) {
                throw new IllegalStateException("validated role-sync guild is unavailable");
            }
            if (roleSyncCoordinator == null) {
                roleSyncCoordinator = new DiscordRoleSyncCoordinator(service, workers);
            }
            roleSyncCoordinator.enable(new JdaDiscordRoleReconciler(guild, service.configuration()));
        });
    }

    private void enableManagedRoleShadow() {
        moderation.flatMap(StaffModerationRuntime::managedRoleShadow).ifPresent(service -> {
            Guild guild = jda.getGuildById(configuration.environment().guildId());
            if (guild == null) {
                throw new IllegalStateException("validated managed-role shadow guild is unavailable");
            }
            if (managedRoleShadowCoordinator == null) {
                managedRoleShadowCoordinator = new ManagedRoleShadowCoordinator(service, workers);
            }
            managedRoleShadowCoordinator.enable(guild);
        });
    }

    @SuppressWarnings("PMD.NullAssignment") // Clearing the closed API reference prevents later reuse.
    private void disableInteractions() {
        synchronized (lifecycleLock) {
            chatSenderIdentities.clear();
            moderation.ifPresent(StaffModerationRuntime::pausePunishments);
            if (previewListener != null) {
                previewListener.disable();
            }
            if (moderationListener != null) {
                moderationListener.disable();
            }
            if (roleSyncCoordinator != null) {
                roleSyncCoordinator.disable();
            }
            if (managedRoleShadowCoordinator != null) {
                managedRoleShadowCoordinator.disable();
            }
            if (productionReadApi != null) {
                productionReadApi.close();
                productionReadApi = null;
            }
            if (aiModerationReadApi != null) {
                aiModerationReadApi.close();
                aiModerationReadApi = null;
            }
        }
    }

    @Override
    public boolean send(long channelId, ChatBridgeOutboundMessage message) {
        if (message == null) {
            return false;
        }
        JDA api;
        synchronized (lifecycleLock) {
            api = jda;
        }
        if (api == null) {
            return false;
        }
        TextChannel channel = api.getTextChannelById(channelId);
        if (channel == null || channel.getGuild().getIdLong() != configuration.environment().guildId()) {
            return false;
        }
        if (!channel.getGuild().getSelfMember().hasPermission(
                channel, Permission.VIEW_CHANNEL, Permission.MESSAGE_SEND)) {
            return false;
        }

        String content = chatContent(
                message,
                chatSenderIdentity(api, channel.getGuild(), message.minecraftPlayerId())
        );
        try {
            channel.sendMessage(content)
                    .setAllowedMentions(List.of())
                    .complete();
            return true;
        } catch (RuntimeException failure) {
            return false;
        }
    }

    static String chatContent(ChatBridgeOutboundMessage message) {
        return chatContent(message, Optional.empty());
    }

    static String chatContent(
            ChatBridgeOutboundMessage message,
            Optional<String> linkedPresentation
    ) {
        Objects.requireNonNull(message, "message");
        String prefix = sourcePrefix(message.sourceServerId(), linkedPresentation)
                + message.displayName() + ": ";
        int available = Math.max(0, 2_000 - prefix.length());
        return prefix + truncateDiscordText(message.plainText(), available);
    }


    @Override
    public boolean sendRendered(
            long channelId,
            ChatBridgeRenderedMessage message,
            List<ChatBridgeArtifact> artifacts
    ) {
        if (message == null || artifacts == null) {
            return false;
        }
        JDA api;
        synchronized (lifecycleLock) {
            api = jda;
        }
        if (api == null) {
            return false;
        }
        TextChannel channel = api.getTextChannelById(channelId);
        if (channel == null || channel.getGuild().getIdLong() != configuration.environment().guildId()) {
            return false;
        }
        if (!channel.getGuild().getSelfMember().hasPermission(
                channel, Permission.VIEW_CHANNEL, Permission.MESSAGE_SEND)) {
            return false;
        }

        String content = renderedChatContent(
                message,
                chatSenderIdentity(api, channel.getGuild(), message.minecraftPlayerId())
        );
        if (artifacts.isEmpty()
                || !channel.getGuild().getSelfMember().hasPermission(
                        channel, Permission.MESSAGE_ATTACH_FILES)) {
            return sendRenderedText(channel, content);
        }

        List<FileUpload> uploads = artifactUploads(artifacts);
        if (uploads.isEmpty()) {
            return sendRenderedText(channel, content);
        }
        try {
            MessageCreateAction action = channel.sendMessage(content)
                    .setAllowedMentions(List.of())
                    .addFiles(uploads);
            action.complete();
            return true;
        } catch (RuntimeException failure) {
            // Delivery is ambiguous once the REST request is submitted; do not risk a duplicate
            // text-only message after an attachment send failure.
            return false;
        } finally {
            closeUploads(uploads);
        }
    }

    private static List<FileUpload> artifactUploads(List<ChatBridgeArtifact> artifacts) {
        List<ChatBridgeArtifact> ordered = artifacts.stream()
                .filter(Objects::nonNull)
                .sorted(Comparator.comparingInt(ChatBridgeArtifact::position)
                        .thenComparing(ChatBridgeArtifact::filename))
                .toList();
        List<FileUpload> uploads = new ArrayList<>(ordered.size());
        try {
            for (ChatBridgeArtifact artifact : ordered) {
                uploads.add(FileUpload.fromData(artifact.data(), artifact.filename())
                        .setDescription(artifact.altText()));
            }
            return uploads;
        } catch (RuntimeException failure) {
            closeUploads(uploads);
            return List.of();
        }
    }

    private static void closeUploads(List<FileUpload> uploads) {
        for (FileUpload upload : uploads) {
            try {
                upload.close();
            } catch (IOException ignored) {
                // FileUpload resources are ephemeral byte-array wrappers; cleanup is best effort.
            }
        }
    }

    private static boolean sendRenderedText(TextChannel channel, String content) {
        try {
            channel.sendMessage(content)
                    .setAllowedMentions(List.of())
                    .complete();
            return true;
        } catch (RuntimeException failure) {
            return false;
        }
    }

    static String renderedChatContent(ChatBridgeRenderedMessage message) {
        return renderedChatContent(message, Optional.empty());
    }

    static String renderedChatContent(
            ChatBridgeRenderedMessage message,
            Optional<String> linkedPresentation
    ) {
        Objects.requireNonNull(message, "message");
        String prefix = sourcePrefix(message.sourceServerId(), linkedPresentation);
        int available = Math.max(0, 2_000 - prefix.length());
        String markdown = message.lineMarkdown();
        if (markdown.length() <= available) {
            return prefix + markdown;
        }
        String plain = message.linePlainText();
        if (plain.length() <= available) {
            return prefix + plain;
        }
        return prefix + truncateDiscordText(plain, available);
    }

    private Optional<String> chatSenderIdentity(
            JDA api,
            Guild guild,
            UUID minecraftPlayerId
    ) {
        Optional<String> discordId = chatSenderIdentities.resolve(minecraftPlayerId);
        if (discordId.isEmpty()) {
            return Optional.empty();
        }
        String id = discordId.orElseThrow();
        Member member = guild.getMemberById(id);
        if (member != null) {
            return Optional.of("@" + escapeDiscordMarkdown(member.getEffectiveName()));
        }
        User user = api.getUserById(id);
        if (user != null) {
            return Optional.of("@" + escapeDiscordMarkdown(
                    ModerationDiscordMessageMapper.displayName(user)));
        }
        return Optional.of("linked");
    }

    private static String sourcePrefix(
            String sourceServerId,
            Optional<String> linkedPresentation
    ) {
        Objects.requireNonNull(sourceServerId, "sourceServerId");
        Objects.requireNonNull(linkedPresentation, "linkedPresentation");
        return linkedPresentation
                .map(value -> "[" + sourceServerId + " · " + value + "] ")
                .orElseGet(() -> "[" + sourceServerId + "] ");
    }

    static String escapeDiscordMarkdown(String value) {
        if (value == null || value.isBlank()) {
            return "linked";
        }
        StringBuilder escaped = new StringBuilder(Math.min(value.length() * 2, 128));
        int limit = Math.min(value.length(), 64);
        for (int index = 0; index < limit; index++) {
            char character = value.charAt(index);
            if ("\\*_~`>|".indexOf(character) >= 0) {
                escaped.append('\\');
            }
            if (!Character.isISOControl(character)) {
                escaped.append(character);
            }
        }
        return escaped.isEmpty() ? "linked" : escaped.toString();
    }

    private static String truncateDiscordText(String text, int maximumLength) {
        if (text.length() <= maximumLength) {
            return text;
        }
        int end = Math.max(0, maximumLength);
        if (end > 0
                && end < text.length()
                && Character.isHighSurrogate(text.charAt(end - 1))
                && Character.isLowSurrogate(text.charAt(end))) {
            end--;
        }
        return text.substring(0, end);
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

    @SuppressWarnings("PMD.NullAssignment") // Clearing the closed API reference prevents later reuse.
    private void closeListeners() {
        chatSenderIdentities.clear();
        moderation.ifPresent(StaffModerationRuntime::pausePunishments);
        if (roleSyncCoordinator != null) {
            roleSyncCoordinator.close();
        }
        if (managedRoleShadowCoordinator != null) {
            managedRoleShadowCoordinator.close();
        }
        if (previewListener != null) {
            previewListener.close();
        }
        if (productionReadApi != null) {
            productionReadApi.close();
            productionReadApi = null;
        }
        if (aiModerationReadApi != null) {
            aiModerationReadApi.close();
            aiModerationReadApi = null;
        }
        if (moderationListener != null) {
            moderationListener.disable();
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

    private static final class DiscordChatIngressListener extends ListenerAdapter {
        private final long guildId;
        private final Map<Long, StaffBotChatBridgeConfiguration.Route> routes;
        private final DiscordChatIngress ingress;

        private DiscordChatIngressListener(
                long guildId,
                Map<Long, StaffBotChatBridgeConfiguration.Route> routes,
                DiscordChatIngress ingress
        ) {
            this.guildId = guildId;
            this.routes = Map.copyOf(routes);
            this.ingress = Objects.requireNonNull(ingress, "ingress");
        }

        @Override
        public void onMessageReceived(MessageReceivedEvent event) {
            if (!event.isFromGuild()
                    || event.getGuild().getIdLong() != guildId
                    || event.isWebhookMessage()
                    || event.getAuthor().isBot()) {
                return;
            }
            StaffBotChatBridgeConfiguration.Route route = routes.get(event.getChannel().getIdLong());
            if (route == null) {
                return;
            }
            String plainText = normalizeDiscordText(event.getMessage().getContentDisplay());
            if (plainText.isBlank()) {
                return;
            }
            String messageId = event.getMessageId();
            long createdAt = event.getMessage().getTimeCreated().toInstant().toEpochMilli();
            UUID eventId = UUID.nameUUIDFromBytes(
                    ("discord-chat:" + messageId).getBytes(StandardCharsets.UTF_8));
            String displayName = event.getMember() == null
                    ? event.getAuthor().getName()
                    : event.getMember().getEffectiveName();
            try {
                ingress.offer(new ChatBridgeInboundMessage(
                        eventId,
                        "discord-" + messageId,
                        "discord-canonical-" + messageId,
                        createdAt,
                        createdAt + INBOUND_CHAT_LIFETIME.toMillis(),
                        event.getChannel().getIdLong(),
                        event.getAuthor().getId(),
                        displayName,
                        route.sourceServerId(),
                        route.logicalChannelId(),
                        plainText
                ));
            } catch (IllegalArgumentException ignored) {
                // Malformed/stale Discord content is dropped at the provider-neutral boundary.
            }
        }

        private static String normalizeDiscordText(String content) {
            if (content == null) {
                return "";
            }
            return content.replace('\r', ' ').replace('\n', ' ');
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
