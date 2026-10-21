package net.enthusia.staff.discordbot;

import java.time.Clock;
import java.time.Duration;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import javax.net.ssl.SSLContext;
import net.enthusia.staff.protocol.ChatBridgeInboundMessage;
import net.enthusia.staff.protocol.ChatBridgeMessages;
import net.enthusia.staff.protocol.PersistentChannelClient;
import net.enthusia.staff.protocol.TlsContextLoader;

/** Outbound-connecting authenticated StaffBot peer for ephemeral chat frames. */
final class StaffBotChatTransport implements StaffBotChatLifecycle, DiscordChatIngress {
    private static final System.Logger LOGGER = System.getLogger(StaffBotChatTransport.class.getName());

    private static final Duration ACK_TIMEOUT = Duration.ofSeconds(2);

    private final StaffBotChatIngress ingress;
    private final StaffBotChatArtifactIngress artifactIngress;
    private final StaffBotRenderedChatIngress renderedIngress;
    private final Map<Long, StaffBotChatBridgeConfiguration.Route> ingressRoutes;
    private final PersistentChannelClient client;
    private final StaffBotChatReadinessPublisher readinessPublisher;
    private final ThreadPoolExecutor discordSender;
    private final AtomicBoolean started = new AtomicBoolean();
    private final AtomicBoolean acceptingDiscord = new AtomicBoolean();
    private final AtomicBoolean closed = new AtomicBoolean();
    private final AtomicLong generation = new AtomicLong();

    private StaffBotChatTransport(
            StaffBotChatIngress ingress,
            StaffBotChatArtifactIngress artifactIngress,
            StaffBotRenderedChatIngress renderedIngress,
            Map<Long, StaffBotChatBridgeConfiguration.Route> ingressRoutes,
            PersistentChannelClient client,
            StaffBotChatReadinessPublisher readinessPublisher,
            int queueCapacity
    ) {
        this.ingress = Objects.requireNonNull(ingress, "ingress");
        this.artifactIngress = Objects.requireNonNull(artifactIngress, "artifactIngress");
        this.renderedIngress = Objects.requireNonNull(renderedIngress, "renderedIngress");
        this.ingressRoutes = Map.copyOf(Objects.requireNonNull(ingressRoutes, "ingressRoutes"));
        this.client = Objects.requireNonNull(client, "client");
        this.readinessPublisher = Objects.requireNonNull(readinessPublisher, "readinessPublisher");
        this.discordSender = new ThreadPoolExecutor(
                1,
                1,
                0L,
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(queueCapacity),
                runnable -> {
                    Thread thread = new Thread(runnable, "EnthusiaStaff-StaffBot-Discord-Ingress");
                    thread.setDaemon(true);
                    return thread;
                },
                new ThreadPoolExecutor.AbortPolicy()
        );
    }

    // This peer ID cannot collide with the live StaffBot's STAFFBOT connection.
    // Velocity must explicitly authorize its own separate HMAC peer before any
    // isolated staging run can connect; an unconfigured peer fails closed.
    static final String ISOLATED_TEST_PEER_ID = "STAFFBOT_TEST";

    static StaffBotChatTransport create(
            StaffBotChatBridgeConfiguration configuration,
            DiscordChatEgress egress,
            DiscordRenderedChatEgress renderedEgress
    ) {
        return createForPeer(
                configuration, egress, renderedEgress, StaffBotChatBridgeConfiguration.PEER_ID);
    }

    static StaffBotChatTransport createIsolatedTest(
            StaffBotChatBridgeConfiguration configuration,
            DiscordChatEgress egress,
            DiscordRenderedChatEgress renderedEgress
    ) {
        if (configuration.mode() != StaffBotChatBridgeConfiguration.Mode.SHADOW
                || !configuration.ingressRoutes().isEmpty()) {
            throw new IllegalArgumentException("isolated chat transport requires outbound-only SHADOW");
        }
        return createForPeer(configuration, egress, renderedEgress, ISOLATED_TEST_PEER_ID);
    }

    private static StaffBotChatTransport createForPeer(
            StaffBotChatBridgeConfiguration configuration,
            DiscordChatEgress egress,
            DiscordRenderedChatEgress renderedEgress,
            String peerId
    ) {
        Objects.requireNonNull(peerId, "peerId");
        Objects.requireNonNull(configuration, "configuration");
        Objects.requireNonNull(egress, "egress");
        Objects.requireNonNull(renderedEgress, "renderedEgress");

        Clock clock = Clock.systemUTC();
        StaffBotChatIngress ingress = new StaffBotChatIngress(configuration, clock, egress);
        StaffBotChatArtifactStore artifactStore = new StaffBotChatArtifactStore();
        StaffBotChatArtifactIngress artifactIngress = new StaffBotChatArtifactIngress(
                configuration, clock, artifactStore);
        StaffBotRenderedChatIngress renderedIngress = new StaffBotRenderedChatIngress(
                configuration, clock, renderedEgress, artifactStore);
        try {
            PersistentChannelClient client = createChannelClient(
                    configuration, peerId, clock, ingress, artifactIngress, renderedIngress);
            StaffBotChatReadinessPublisher readinessPublisher =
                    new StaffBotChatReadinessPublisher(
                            clock,
                            client,
                            configuration.mode() == StaffBotChatBridgeConfiguration.Mode.AUTHORITATIVE
                    );
            return new StaffBotChatTransport(
                    ingress,
                    artifactIngress,
                    renderedIngress,
                    configuration.ingressRoutes(),
                    client,
                    readinessPublisher,
                    configuration.queueCapacity()
            );
        } catch (RuntimeException failure) {
            renderedIngress.close();
            artifactIngress.close();
            ingress.close();
            throw failure;
        }
    }

    private static PersistentChannelClient createChannelClient(
            StaffBotChatBridgeConfiguration configuration,
            String peerId,
            Clock clock,
            StaffBotChatIngress ingress,
            StaffBotChatArtifactIngress artifactIngress,
            StaffBotRenderedChatIngress renderedIngress
    ) {
        SSLContext tls = tlsContext(configuration);
        return new PersistentChannelClient(
                new PersistentChannelClient.Configuration(
                        peerId,
                        configuration.host(),
                        configuration.port(),
                        configuration.clientKey(),
                        StaffBotChatBridgeConfiguration.PROXY_ID,
                        configuration.proxyKey(),
                        tls),
                clock,
                envelope -> artifactIngress.accept(envelope)
                        || ingress.accept(envelope)
                        || renderedIngress.accept(envelope),
                StaffBotChatTransport::connectionState);
    }

    boolean connected() {
        return started.get() && !closed.get() && client.connected();
    }

    @Override
    public void start() {
        if (closed.get()) {
            throw new IllegalStateException("StaffBot chat transport is closed");
        }
        if (!started.compareAndSet(false, true)) {
            return;
        }
        try {
            client.start();
            readinessPublisher.start();
        } catch (RuntimeException failure) {
            started.set(false);
            throw failure;
        }
    }

    @Override
    public void resume() {
        if (!closed.get()) {
            generation.incrementAndGet();
            acceptingDiscord.set(true);
            ingress.resume();
            artifactIngress.resume();
            renderedIngress.resume();
            readinessPublisher.resume();
        }
    }

    @Override
    public void pause() {
        acceptingDiscord.set(false);
        generation.incrementAndGet();
        discordSender.getQueue().clear();
        ingress.pause();
        artifactIngress.pause();
        renderedIngress.pause();
        readinessPublisher.pause();
    }

    @Override
    public boolean offer(ChatBridgeInboundMessage message) {
        Objects.requireNonNull(message, "message");
        if (!acceptingDiscord.get() || !started.get() || closed.get() || message.isExpired(System.currentTimeMillis())) {
            return false;
        }
        StaffBotChatBridgeConfiguration.Route expected = ingressRoutes.get(message.sourceDiscordChannelId());
        if (expected == null
                || !expected.sourceServerId().equals(message.targetServerId())
                || !expected.logicalChannelId().equals(message.logicalChannelId())
                || !client.connected()) {
            return false;
        }
        String payload;
        try {
            payload = ChatBridgeMessages.encodeInbound(message);
        } catch (IllegalArgumentException failure) {
            return false;
        }
        long expectedGeneration = generation.get();
        try {
            discordSender.execute(() -> sendDiscordInbound(expectedGeneration, message, payload));
            return true;
        } catch (RejectedExecutionException failure) {
            return false;
        }
    }

    private void sendDiscordInbound(
            long expectedGeneration,
            ChatBridgeInboundMessage message,
            String payload
    ) {
        if (!acceptingDiscord.get()
                || generation.get() != expectedGeneration
                || !client.connected()
                || message.isExpired(System.currentTimeMillis())) {
            return;
        }
        try {
            client.send(message.eventId(), ChatBridgeMessages.INBOUND, payload, ACK_TIMEOUT).join();
        } catch (RuntimeException ignored) {
            // Best-effort chat drops on transport failure; serial ACK wait preserves queue backpressure.
        }
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        ingress.pause();
        artifactIngress.pause();
        renderedIngress.pause();
        acceptingDiscord.set(false);
        generation.incrementAndGet();
        discordSender.shutdownNow();
        readinessPublisher.close();
        client.close();
        renderedIngress.close();
        artifactIngress.close();
        ingress.close();
    }

    private static SSLContext tlsContext(StaffBotChatBridgeConfiguration configuration) {
        char[] password = configuration.trustStorePassword();
        try {
            return TlsContextLoader.client(configuration.trustStore(), password);
        } finally {
            Arrays.fill(password, '\0');
        }
    }

    private static void connectionState(String state) {
        if (LOGGER.isLoggable(System.Logger.Level.INFO)) {
            LOGGER.log(System.Logger.Level.INFO, "staff_bot_chat_transport state={0}", state);
        }
    }
}
