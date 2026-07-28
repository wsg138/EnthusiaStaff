package net.enthusia.staff.protocol;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import javax.crypto.SecretKey;

public final class PersistentChannelClient implements AutoCloseable {
    private static final int PROTOCOL_VERSION = 1;

    private final String backendId;
    private final InetSocketAddress remoteAddress;
    private final EnvelopeAuthenticator verifier;
    private final EnvelopeAuthenticator signer;
    private final EnvelopeCodec codec = new EnvelopeCodec();
    private final ChannelMessageHandler handler;
    private final Consumer<String> stateSink;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();
    private final AtomicBoolean running = new AtomicBoolean();
    private final Map<UUID, CompletableFuture<Boolean>> pending = new ConcurrentHashMap<>();
    private volatile Socket socket;
    private volatile DataOutputStream output;
    private Thread connectionThread;

    public PersistentChannelClient(
            String backendId,
            String host,
            int port,
            SecretKey backendKey,
            String proxyId,
            SecretKey proxyKey,
            Clock clock,
            ChannelMessageHandler handler,
            Consumer<String> stateSink
    ) {
        if (backendId == null || backendId.isBlank() || host == null || host.isBlank()
                || port < 1 || port > 65_535 || backendKey == null || proxyId == null || proxyId.isBlank()
                || proxyKey == null || clock == null || handler == null || stateSink == null) {
            throw new IllegalArgumentException("persistent channel client configuration is invalid");
        }
        this.backendId = backendId;
        this.remoteAddress = new InetSocketAddress(host, port);
        this.clock = clock;
        this.handler = handler;
        this.stateSink = stateSink;
        this.signer = new EnvelopeAuthenticator(
                PROTOCOL_VERSION, clock, Duration.ofMinutes(2), Duration.ofSeconds(15), Map.of(backendId, backendKey),
                new ReplayGuard(1, Duration.ofMinutes(1))
        );
        this.verifier = new EnvelopeAuthenticator(
                PROTOCOL_VERSION, clock, Duration.ofMinutes(2), Duration.ofSeconds(15), Map.of(proxyId, proxyKey),
                new ReplayGuard(100_000, Duration.ofMinutes(3))
        );
    }

    public synchronized void start() {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        connectionThread = new Thread(this::connectionLoop, "EnthusiaStaff-Channel-Client");
        connectionThread.setDaemon(true);
        connectionThread.start();
    }

    public boolean connected() {
        Socket current = socket;
        return current != null && current.isConnected() && !current.isClosed();
    }

    public CompletableFuture<Boolean> send(
            UUID messageId,
            String messageType,
            String payloadJson,
            Duration timeout
    ) {
        DataOutputStream current = output;
        if (current == null) {
            return CompletableFuture.completedFuture(false);
        }
        ProtocolEnvelope envelope = signer.sign(new UnsignedEnvelope(
                PROTOCOL_VERSION, messageId, backendId, messageType, clock.millis(), nonce(), payloadJson
        ));
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        pending.put(messageId, future);
        try {
            synchronized (current) {
                FrameTransport.write(current, codec, envelope);
            }
        } catch (IOException exception) {
            pending.remove(messageId);
            future.complete(false);
            disconnect();
        }
        return future.completeOnTimeout(false, timeout.toMillis(), TimeUnit.MILLISECONDS)
                .whenComplete((ignored, failure) -> pending.remove(messageId));
    }

    private void connectionLoop() {
        long backoffMillis = 1_000;
        while (running.get()) {
            try {
                connectAndRead();
                backoffMillis = 1_000;
            } catch (EOFException | SocketException ignored) {
                stateSink.accept("DISCONNECTED");
            } catch (IOException | RuntimeException exception) {
                stateSink.accept("CONNECTION_FAILED");
            } finally {
                disconnect();
            }
            if (!running.get()) {
                return;
            }
            try {
                Thread.sleep(backoffMillis);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                return;
            }
            backoffMillis = Math.min(backoffMillis * 2, 30_000);
        }
    }

    private void connectAndRead() throws IOException {
        Socket opened = new Socket();
        try (opened) {
            opened.connect(remoteAddress, 5_000);
            opened.setKeepAlive(true);
            opened.setTcpNoDelay(true);
            opened.setSoTimeout(120_000);
            try (DataOutputStream openedOutput =
                         new DataOutputStream(new BufferedOutputStream(opened.getOutputStream()));
                 DataInputStream input =
                         new DataInputStream(new BufferedInputStream(opened.getInputStream()))) {
                socket = opened;
                output = openedOutput;
                ProtocolEnvelope hello = signer.sign(new UnsignedEnvelope(
                        PROTOCOL_VERSION, UUID.randomUUID(), backendId, "HELLO", clock.millis(), nonce(), "{}"
                ));
                synchronized (openedOutput) {
                    FrameTransport.write(openedOutput, codec, hello);
                }
                stateSink.accept("CONNECTED");
                while (running.get()) {
                    ProtocolEnvelope envelope = FrameTransport.read(input, codec);
                    VerificationResult result = verifier.verify(envelope);
                    if (!result.accepted()) {
                        stateSink.accept("REJECTED_" + result.status());
                        continue;
                    }
                    if ("ACK".equals(envelope.messageType())) {
                        receiveAck(envelope.payloadJson());
                    } else if (handler.handle(envelope)) {
                        acknowledge(envelope.messageId());
                    }
                }
            }
        }
    }

    private void receiveAck(String payload) {
        try {
            UUID acknowledged = UUID.fromString(payload);
            CompletableFuture<Boolean> future = pending.remove(acknowledged);
            if (future != null) {
                future.complete(true);
            }
        } catch (IllegalArgumentException ignored) {
            stateSink.accept("MALFORMED_ACK");
        }
    }

    private void acknowledge(UUID messageId) throws IOException {
        DataOutputStream current = output;
        if (current == null) {
            throw new IOException("channel disconnected before acknowledgement");
        }
        ProtocolEnvelope acknowledgement = signer.sign(new UnsignedEnvelope(
                PROTOCOL_VERSION, UUID.randomUUID(), backendId, "ACK", clock.millis(), nonce(), messageId.toString()
        ));
        synchronized (current) {
            FrameTransport.write(current, codec, acknowledgement);
        }
    }

    private String nonce() {
        byte[] bytes = new byte[18];
        random.nextBytes(bytes);
        return java.util.HexFormat.of().formatHex(bytes);
    }

    private void disconnect() {
        output = null;
        Socket current = socket;
        socket = null;
        if (current != null) {
            try {
                current.close();
            } catch (IOException ignored) {
                // Reconnect loop owns recovery.
            }
        }
        pending.values().forEach(future -> future.complete(false));
        pending.clear();
    }

    @Override
    public synchronized void close() {
        running.set(false);
        disconnect();
        if (connectionThread != null) {
            connectionThread.interrupt();
        }
    }
}
