package net.enthusia.staff.paper.integration;

import dev.rosewood.rosechat.api.RoseChatAPI;
import dev.rosewood.rosechat.api.chatbridge.InboundChatMessage;
import java.time.Clock;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import net.enthusia.staff.protocol.ChatBridgeInboundMessage;
import net.enthusia.staff.protocol.ChatBridgeMessages;
import net.enthusia.staff.protocol.ProtocolEnvelope;
import org.bukkit.Server;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Best-effort authenticated Velocity -> RoseChat Discord-origin ingress.
 *
 * <p>Admission happens off-thread and is bounded/deduplicated. Actual RoseChat dispatch is always
 * scheduled on the server's global region. No player or world state is accessed here.
 * RoseChat provider compatibility with Folia still requires separate runtime validation.
 * No durable inbox/outbox is used.</p>
 */
public final class RoseChatInboundBridgeIntegration implements AutoCloseable {
    static final int MAXIMUM_QUEUED_MESSAGES = 256;
    static final int MAXIMUM_DEDUPE_ENTRIES = 4_096;

    @FunctionalInterface
    interface Scheduler {
        void execute(Runnable task);
    }

    @FunctionalInterface
    interface Dispatcher {
        boolean dispatch(InboundChatMessage message);
    }

    private record Admission(boolean accepted, boolean duplicate, long expiresAt) {
    }

    private final String serverId;
    private final Clock clock;
    private final Scheduler scheduler;
    private final Dispatcher dispatcher;
    private final int maximumQueuedMessages;
    private final int maximumDedupeEntries;
    private final Object admissionLock = new Object();
    private final Map<UUID, Long> dedupeUntil = new HashMap<>();
    private final AtomicBoolean active = new AtomicBoolean(true);
    private int queued;

    private RoseChatInboundBridgeIntegration(JavaPlugin plugin, String serverId, Clock clock) {
        this(
                serverId,
                clock,
                globalIngressScheduler(plugin.getServer(), plugin),
                message -> RoseChatAPI.getInstance().dispatchInboundChat(message),
                MAXIMUM_QUEUED_MESSAGES,
                MAXIMUM_DEDUPE_ENTRIES
        );
    }

    /** Server-wide ingress must not use Bukkit's legacy primary-thread scheduler on Folia. */
    static Scheduler globalIngressScheduler(Server server, Plugin plugin) {
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(plugin, "plugin");
        return task -> server.getGlobalRegionScheduler().execute(plugin, task);
    }

    RoseChatInboundBridgeIntegration(
            String serverId,
            Clock clock,
            Scheduler scheduler,
            Dispatcher dispatcher,
            int maximumQueuedMessages,
            int maximumDedupeEntries
    ) {
        this.serverId = requireText(serverId, "serverId");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.dispatcher = Objects.requireNonNull(dispatcher, "dispatcher");
        if (maximumQueuedMessages < 1 || maximumDedupeEntries < 1) {
            throw new IllegalArgumentException("RoseChat inbound bridge bounds must be positive");
        }
        this.maximumQueuedMessages = maximumQueuedMessages;
        this.maximumDedupeEntries = maximumDedupeEntries;
    }

    public static Discovery discover(JavaPlugin plugin, String serverId, Clock clock) {
        try {
            RoseChatAPI.class.getMethod("dispatchInboundChat", InboundChatMessage.class);
            return new Discovery(
                    Optional.of(new RoseChatInboundBridgeIntegration(plugin, serverId, clock)),
                    ""
            );
        } catch (NoSuchMethodException | RuntimeException | LinkageError failure) {
            return Discovery.unavailable(
                    "RoseChat inbound bridge API could not be installed: "
                            + failure.getClass().getSimpleName()
            );
        }
    }

    public boolean accept(String expectedProxyId, ProtocolEnvelope envelope) {
        Objects.requireNonNull(envelope, "envelope");
        if (!active.get()
                || !ChatBridgeMessages.INBOUND.equals(envelope.messageType())
                || !requireText(expectedProxyId, "expectedProxyId").equals(envelope.serverId())) {
            return false;
        }

        Optional<ChatBridgeInboundMessage> decoded = decode(envelope.payloadJson());
        if (decoded.isEmpty()) {
            return false;
        }
        ChatBridgeInboundMessage message = decoded.orElseThrow();
        long now = clock.millis();
        if (!message.eventId().equals(envelope.messageId())
                || !serverId.equals(message.targetServerId())
                || message.isExpired(now)) {
            return false;
        }

        Admission admission = reserve(message, now);
        if (!admission.accepted()) {
            return admission.duplicate();
        }
        try {
            scheduler.execute(() -> dispatch(message));
            return true;
        } catch (RuntimeException failure) {
            release(message.eventId(), admission.expiresAt());
            return false;
        }
    }

    private void dispatch(ChatBridgeInboundMessage message) {
        try {
            if (!active.get() || message.isExpired(clock.millis())) {
                return;
            }
            dispatcher.dispatch(new InboundChatMessage(
                    message.eventId(),
                    message.externalMessageId(),
                    message.canonicalMessageId(),
                    message.createdAtEpochMillis(),
                    message.expiresAtEpochMillis(),
                    message.logicalChannelId(),
                    message.displayName(),
                    message.plainText()
            ));
        } catch (RuntimeException | LinkageError ignored) {
            // Ephemeral Discord chat drops on provider failure.
        } finally {
            synchronized (admissionLock) {
                queued = Math.max(0, queued - 1);
            }
        }
    }

    private Admission reserve(ChatBridgeInboundMessage message, long now) {
        synchronized (admissionLock) {
            Long existing = dedupeUntil.get(message.eventId());
            if (existing != null && existing >= now) {
                return new Admission(false, true, existing);
            }
            if (existing != null) {
                dedupeUntil.remove(message.eventId());
            }
            dedupeUntil.entrySet().removeIf(entry -> entry.getValue() < now);
            if (queued >= maximumQueuedMessages || dedupeUntil.size() >= maximumDedupeEntries) {
                return new Admission(false, false, -1L);
            }
            queued++;
            dedupeUntil.put(message.eventId(), message.expiresAtEpochMillis());
            return new Admission(true, false, message.expiresAtEpochMillis());
        }
    }

    private void release(UUID eventId, long expiresAt) {
        synchronized (admissionLock) {
            dedupeUntil.remove(eventId, expiresAt);
            queued = Math.max(0, queued - 1);
        }
    }

    private static Optional<ChatBridgeInboundMessage> decode(String payloadJson) {
        try {
            return Optional.of(ChatBridgeMessages.decodeInbound(payloadJson));
        } catch (IllegalArgumentException failure) {
            return Optional.empty();
        }
    }

    @Override
    public void close() {
        active.set(false);
        synchronized (admissionLock) {
            dedupeUntil.clear();
        }
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value;
    }

    public record Discovery(Optional<RoseChatInboundBridgeIntegration> integration, String issue) {
        public Discovery {
            integration = Objects.requireNonNull(integration, "integration");
            issue = Objects.requireNonNull(issue, "issue");
            if (integration.isPresent() == !issue.isEmpty()) {
                throw new IllegalArgumentException(
                        "successful RoseChat inbound discovery cannot contain an issue");
            }
        }

        private static Discovery unavailable(String issue) {
            return new Discovery(Optional.empty(), issue);
        }
    }
}
