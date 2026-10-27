package net.enthusia.staff.discordbot;

import java.time.Clock;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.concurrent.atomic.AtomicBoolean;
import net.enthusia.staff.protocol.ChatArtifactMessages;
import net.enthusia.staff.protocol.ChatBridgeArtifactBundle;
import net.enthusia.staff.protocol.ProtocolEnvelope;

/** Synchronous StaffBot admission boundary for artifact bundles that precede styled chat. */
final class StaffBotChatArtifactIngress implements AutoCloseable {
    private final Clock clock;
    private final Map<StaffBotChatBridgeConfiguration.Route, Long> routes;
    private final StaffBotChatArtifactStore store;
    private final Function<String, Optional<ChatBridgeArtifactBundle>> decoder;
    private final AtomicBoolean accepting = new AtomicBoolean();
    private final Object admissionLock = new Object();
    // Every pause fences decoded-but-not-yet-admitted packets, even across a
    // fast pause/resume cycle during a Discord gateway reconnect.
    private long admissionGeneration;

    StaffBotChatArtifactIngress(
            StaffBotChatBridgeConfiguration configuration,
            Clock clock,
            StaffBotChatArtifactStore store
    ) {
        this(configuration.routes(), clock, store);
    }

    StaffBotChatArtifactIngress(
            Map<StaffBotChatBridgeConfiguration.Route, Long> routes,
            Clock clock,
            StaffBotChatArtifactStore store
    ) {
        this(routes, clock, store, StaffBotChatArtifactIngress::decode);
    }

    // Decode seam permits a deterministic in-flight packet/pause race test.
    StaffBotChatArtifactIngress(
            Map<StaffBotChatBridgeConfiguration.Route, Long> routes,
            Clock clock,
            StaffBotChatArtifactStore store,
            Function<String, Optional<ChatBridgeArtifactBundle>> decoder
    ) {
        this.routes = Map.copyOf(Objects.requireNonNull(routes, "routes"));
        if (this.routes.isEmpty()) {
            throw new IllegalArgumentException("StaffBot chat artifact routes are required");
        }
        this.clock = Objects.requireNonNull(clock, "clock");
        this.store = Objects.requireNonNull(store, "store");
        this.decoder = Objects.requireNonNull(decoder, "decoder");
    }

    boolean accept(ProtocolEnvelope envelope) {
        Objects.requireNonNull(envelope, "envelope");
        if (!accepting.get()
                || !StaffBotChatBridgeConfiguration.PROXY_ID.equals(envelope.serverId())
                || !ChatArtifactMessages.ARTIFACTS.equals(envelope.messageType())) {
            return false;
        }
        long generation;
        synchronized (admissionLock) {
            if (!accepting.get()) {
                return false;
            }
            generation = admissionGeneration;
        }
        // Untrusted JSON parsing is outside the admission lock so disconnect
        // shutdown cannot be blocked behind an expensive artifact decode.
        Optional<ChatBridgeArtifactBundle> decoded = decoder.apply(envelope.payloadJson());
        if (decoded.isEmpty()) {
            return false;
        }
        ChatBridgeArtifactBundle bundle = decoded.orElseThrow();
        if (!ChatArtifactMessages.transportMessageId(bundle.eventId())
                .equals(envelope.messageId())
                || !hasRoute(bundle)) {
            return false;
        }
        synchronized (admissionLock) {
            if (!accepting.get() || generation != admissionGeneration) {
                return false;
            }
            return store.put(bundle, clock.millis());
        }
    }

    void resume() {
        synchronized (admissionLock) {
            accepting.set(true);
        }
    }

    void pause() {
        synchronized (admissionLock) {
            admissionGeneration++;
            accepting.set(false);
            store.clear();
        }
    }

    private boolean hasRoute(ChatBridgeArtifactBundle bundle) {
        try {
            return routes.containsKey(new StaffBotChatBridgeConfiguration.Route(
                    bundle.sourceServerId(),
                    bundle.logicalChannelId()
            ));
        } catch (IllegalArgumentException failure) {
            return false;
        }
    }

    private static Optional<ChatBridgeArtifactBundle> decode(String payloadJson) {
        try {
            return Optional.of(ChatArtifactMessages.decode(payloadJson));
        } catch (IllegalArgumentException failure) {
            return Optional.empty();
        }
    }

    @Override
    public void close() {
        pause();
    }
}
