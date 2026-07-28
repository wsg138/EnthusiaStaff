package net.enthusia.staff.protocol;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import javax.crypto.SecretKey;

public final class PersistentChannelServer implements AutoCloseable {
    public enum DeliveryStatus {
        ACKNOWLEDGED,
        NOT_CONNECTED,
        TIMED_OUT,
        REJECTED
    }

    private static final int PROTOCOL_VERSION = 1;

    private final String proxyId;
    private final InetSocketAddress bindAddress;
    private final EnvelopeAuthenticator verifier;
    private final EnvelopeAuthenticator signer;
    private final EnvelopeCodec codec = new EnvelopeCodec();
    private final ExecutorService connections;
    private final Map<String, Session> sessions = new ConcurrentHashMap<>();
    private final ChannelMessageHandler inboundHandler;
    private final Consumer<String> warningSink;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();
    private final AtomicBoolean running = new AtomicBoolean();
    private final Object lifecycleLock = new Object();
    private final AtomicReference<ServerSocket> serverSocket = new AtomicReference<>();

    public PersistentChannelServer(
            String proxyId,
            InetAddress bindAddress,
            int port,
            Map<String, SecretKey> backendKeys,
            SecretKey proxyKey,
            Clock clock,
            int maximumConnections,
            ChannelMessageHandler inboundHandler,
            Consumer<String> warningSink
    ) {
        if (proxyId == null || proxyId.isBlank() || bindAddress == null || port < 0 || port > 65_535
                || backendKeys == null || backendKeys.isEmpty() || proxyKey == null || clock == null
                || maximumConnections < 1 || inboundHandler == null || warningSink == null) {
            throw new IllegalArgumentException("persistent channel server configuration is invalid");
        }
        this.proxyId = proxyId;
        this.bindAddress = new InetSocketAddress(bindAddress, port);
        this.clock = clock;
        this.inboundHandler = inboundHandler;
        this.warningSink = warningSink;
        this.verifier = new EnvelopeAuthenticator(
                PROTOCOL_VERSION, clock, Duration.ofMinutes(2), Duration.ofSeconds(15), backendKeys,
                new ReplayGuard(100_000, Duration.ofMinutes(3))
        );
        this.signer = new EnvelopeAuthenticator(
                PROTOCOL_VERSION, clock, Duration.ofMinutes(2), Duration.ofSeconds(15), Map.of(proxyId, proxyKey),
                new ReplayGuard(1, Duration.ofMinutes(1))
        );
        AtomicInteger sequence = new AtomicInteger();
        ThreadFactory factory = runnable -> {
            Thread thread = new Thread(runnable, "EnthusiaStaff-Channel-Server-" + sequence.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
        this.connections = new ThreadPoolExecutor(
                maximumConnections,
                maximumConnections,
                0L,
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(maximumConnections),
                factory,
                new ThreadPoolExecutor.AbortPolicy()
        );
    }

    @SuppressWarnings("PMD.CloseResource") // Transfers the bound socket to the server lifecycle; close releases it.
    public void start() throws IOException {
        synchronized (lifecycleLock) {
            if (!running.compareAndSet(false, true)) {
                return;
            }
            try {
                ServerSocket opened = openBoundServerSocket();
                serverSocket.set(opened);
                Thread acceptThread = new Thread(this::acceptLoop, "EnthusiaStaff-Channel-Acceptor");
                acceptThread.setDaemon(true);
                acceptThread.start();
            } catch (IOException | RuntimeException exception) {
                running.set(false);
                closeServerSocket(serverSocket.getAndSet(null));
                throw exception;
            }
        }
    }

    private ServerSocket openBoundServerSocket() throws IOException {
        ServerSocket opened = new ServerSocket();
        try {
            opened.setReuseAddress(true);
            opened.bind(bindAddress);
            return opened;
        } catch (IOException | RuntimeException exception) {
            closeServerSocket(opened);
            throw exception;
        }
    }

    public Set<String> connectedServers() {
        return Set.copyOf(sessions.keySet());
    }

    @SuppressWarnings("PMD.CloseResource") // Borrows the lifecycle-owned listening socket without opening one.
    public int boundPort() {
        synchronized (lifecycleLock) {
            ServerSocket current = serverSocket.get();
            if (current == null || !current.isBound()) {
                throw new IllegalStateException("persistent channel server is not bound");
            }
            return current.getLocalPort();
        }
    }

    public CompletableFuture<DeliveryStatus> send(
            String backendId,
            UUID messageId,
            String messageType,
            String payloadJson,
            Duration timeout
    ) {
        Session session = sessions.get(backendId);
        if (session == null) {
            return CompletableFuture.completedFuture(DeliveryStatus.NOT_CONNECTED);
        }
        ProtocolEnvelope envelope = signer.sign(new UnsignedEnvelope(
                PROTOCOL_VERSION,
                messageId,
                proxyId,
                messageType,
                clock.millis(),
                nonce(),
                payloadJson
        ));
        return session.send(envelope, timeout);
    }

    private void acceptLoop() {
        while (running.get()) {
            try {
                acceptConnection();
            } catch (java.util.concurrent.RejectedExecutionException exception) {
                warningSink.accept("Persistent channel connection limit reached");
            } catch (SocketException exception) {
                if (running.get()) {
                    warningSink.accept("Persistent channel accept failed");
                }
            } catch (IOException exception) {
                warningSink.accept("Persistent channel accept failed");
            }
        }
    }

    @SuppressWarnings("PMD.CloseResource") // Ownership moves to serve; rejected sockets close in finally.
    private void acceptConnection() throws IOException {
        ServerSocket listening = serverSocket.get();
        if (listening == null) {
            throw new SocketException("persistent channel server is not bound");
        }
        Socket accepted = listening.accept();
        boolean transferred = false;
        try {
            accepted.setKeepAlive(true);
            accepted.setTcpNoDelay(true);
            accepted.setSoTimeout(120_000);
            connections.execute(() -> serve(accepted));
            transferred = true;
        } finally {
            if (!transferred) {
                closeSocket(accepted);
            }
        }
    }

    private void serve(Socket socket) {
        Session session = null;
        try (socket;
             DataInputStream input = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
             DataOutputStream output = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()))) {
            ProtocolEnvelope hello = FrameTransport.read(input, codec);
            VerificationResult helloResult = verifier.verify(hello);
            if (!helloResult.accepted() || !"HELLO".equals(hello.messageType())) {
                return;
            }
            session = new Session(hello.serverId(), socket, output);
            Session previous = sessions.put(hello.serverId(), session);
            if (previous != null) {
                previous.close();
            }
            session.acknowledge(hello.messageId());
            while (running.get() && !socket.isClosed()) {
                ProtocolEnvelope envelope = FrameTransport.read(input, codec);
                VerificationResult result = verifier.verify(envelope);
                if (!result.accepted() || !envelope.serverId().equals(session.backendId)) {
                    warningSink.accept("Rejected authenticated channel frame: " + result.status());
                    continue;
                }
                if ("ACK".equals(envelope.messageType())) {
                    session.receiveAck(envelope.payloadJson());
                } else if (inboundHandler.handle(envelope)) {
                    session.acknowledge(envelope.messageId());
                }
            }
        } catch (EOFException | SocketException ignored) {
            // Normal disconnect; the durable outbox keeps undelivered work pending.
        } catch (IOException | RuntimeException exception) {
            warningSink.accept("Persistent backend connection ended after a protocol or I/O failure");
        } finally {
            if (session != null) {
                sessions.remove(session.backendId, session);
                session.close();
            }
        }
    }

    private String nonce() {
        byte[] bytes = new byte[18];
        random.nextBytes(bytes);
        return java.util.HexFormat.of().formatHex(bytes);
    }

    private static void closeSocket(Socket socket) {
        if (socket == null) {
            return;
        }
        try {
            socket.close();
        } catch (IOException ignored) {
            // The peer owns no durable acknowledgement until a complete authenticated frame is accepted.
        }
    }

    private static void closeServerSocket(ServerSocket socket) {
        if (socket == null) {
            return;
        }
        try {
            socket.close();
        } catch (IOException ignored) {
            // A failed bind has no accepted durable work to recover.
        }
    }

    @Override
    public void close() {
        synchronized (lifecycleLock) {
            running.set(false);
            closeServerSocket(serverSocket.getAndSet(null));
            sessions.values().forEach(Session::close);
            sessions.clear();
            connections.shutdownNow();
        }
    }

    private final class Session {
        private final String backendId;
        private final Socket socket;
        private final DataOutputStream output;
        private final Map<UUID, CompletableFuture<DeliveryStatus>> pending = new ConcurrentHashMap<>();
        private final AtomicBoolean open = new AtomicBoolean(true);

        private Session(String backendId, Socket socket, DataOutputStream output) {
            this.backendId = backendId;
            this.socket = socket;
            this.output = output;
        }

        private CompletableFuture<DeliveryStatus> send(ProtocolEnvelope envelope, Duration timeout) {
            CompletableFuture<DeliveryStatus> future = new CompletableFuture<>();
            pending.put(envelope.messageId(), future);
            try {
                synchronized (output) {
                    FrameTransport.write(output, codec, envelope);
                }
            } catch (IOException exception) {
                pending.remove(envelope.messageId());
                future.complete(DeliveryStatus.REJECTED);
                close();
                return future;
            }
            return future.completeOnTimeout(DeliveryStatus.TIMED_OUT, timeout.toMillis(), TimeUnit.MILLISECONDS)
                    .whenComplete((ignored, failure) -> pending.remove(envelope.messageId()));
        }

        private void receiveAck(String payload) {
            try {
                UUID acknowledged = UUID.fromString(payload);
                CompletableFuture<DeliveryStatus> future = pending.remove(acknowledged);
                if (future != null) {
                    future.complete(DeliveryStatus.ACKNOWLEDGED);
                }
            } catch (IllegalArgumentException ignored) {
                warningSink.accept("Rejected malformed channel acknowledgement");
            }
        }

        private void acknowledge(UUID messageId) throws IOException {
            ProtocolEnvelope ack = signer.sign(new UnsignedEnvelope(
                    PROTOCOL_VERSION, UUID.randomUUID(), proxyId, "ACK", clock.millis(), nonce(), messageId.toString()
            ));
            synchronized (output) {
                FrameTransport.write(output, codec, ack);
            }
        }

        private void close() {
            if (!open.compareAndSet(true, false)) {
                return;
            }
            pending.values().forEach(future -> future.complete(DeliveryStatus.REJECTED));
            pending.clear();
            closeSocket(socket);
        }
    }
}
