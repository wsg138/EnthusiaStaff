package net.enthusia.staff.discordbot;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** Supervises the panel-uploaded cloudflared connector without putting its token on the command line. */
final class CloudflaredStagingTunnel implements StagingTunnel {
    private static final Duration STOP_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration FORCE_TIMEOUT = Duration.ofSeconds(2);
    private static final String BINARY_NAME = "cloudflared";
    private static final String TOKEN_FILE_NAME = "cloudflared-token.txt";
    private static final String TRANSPORT_PROTOCOL = "http2";

    private final Path runtimeDirectory;
    private final ProcessStarter processStarter;
    private final AtomicBoolean started = new AtomicBoolean();
    private final AtomicBoolean closing = new AtomicBoolean();
    private volatile Process process;

    CloudflaredStagingTunnel(Path binaryFile, Path tokenFile) {
        this(binaryFile, tokenFile, CloudflaredStagingTunnel::startProcess);
    }

    CloudflaredStagingTunnel(Path binaryFile, Path tokenFile, ProcessStarter processStarter) {
        Path binary = normalizedFile(binaryFile, "cloudflared binary", BINARY_NAME);
        Path token = normalizedFile(tokenFile, "cloudflared token file", TOKEN_FILE_NAME);
        if (!Objects.equals(binary.getParent(), token.getParent())) {
            throw new IllegalArgumentException("cloudflared binary and token file must share one runtime directory");
        }
        this.runtimeDirectory = binary.getParent();
        this.processStarter = Objects.requireNonNull(processStarter, "processStarter");
        ensureExecutable(binary);
    }

    @Override
    public void start(Runnable unexpectedExit) throws IOException {
        Objects.requireNonNull(unexpectedExit, "unexpectedExit");
        if (!started.compareAndSet(false, true)) {
            throw new IllegalStateException("staging tunnel already started");
        }
        Process candidate = processStarter.start(runtimeDirectory);
        process = candidate;
        if (!candidate.isAlive()) {
            throw new IOException("cloudflared exited during startup");
        }
        candidate.onExit().thenRun(() -> {
            if (!closing.get()) {
                unexpectedExit.run();
            }
        });
    }

    List<String> command() {
        return List.of(
                "./cloudflared",
                "tunnel",
                "--protocol",
                TRANSPORT_PROTOCOL,
                "--no-autoupdate",
                "run",
                "--token-file",
                TOKEN_FILE_NAME
        );
    }

    Path runtimeDirectory() {
        return runtimeDirectory;
    }

    @Override
    public void close() {
        closing.set(true);
        Process current = process;
        if (current == null || !current.isAlive()) {
            return;
        }
        current.destroy();
        if (await(current, STOP_TIMEOUT)) {
            return;
        }
        current.destroyForcibly();
        await(current, FORCE_TIMEOUT);
    }

    private static Path normalizedFile(Path path, String label, String expectedName) {
        if (path == null || !Files.isRegularFile(path)) {
            throw new IllegalArgumentException(label + " must be a regular file");
        }
        Path normalized = path.toAbsolutePath().normalize();
        if (!expectedName.equals(normalized.getFileName().toString())) {
            throw new IllegalArgumentException(label + " must use the fixed staging filename");
        }
        return normalized;
    }

    private static void ensureExecutable(Path binary) {
        if (Files.isExecutable(binary)) {
            return;
        }
        if (!binary.toFile().setExecutable(true, true) || !Files.isExecutable(binary)) {
            throw new IllegalArgumentException("cloudflared binary is not executable");
        }
    }

    private static Process startProcess(Path directory) throws IOException {
        return new ProcessBuilder(
                "./cloudflared",
                "tunnel",
                "--protocol",
                TRANSPORT_PROTOCOL,
                "--no-autoupdate",
                "run",
                "--token-file",
                TOKEN_FILE_NAME)
                .directory(directory.toFile())
                .redirectOutput(ProcessBuilder.Redirect.INHERIT)
                .redirectError(ProcessBuilder.Redirect.INHERIT)
                .start();
    }

    private static boolean await(Process current, Duration timeout) {
        try {
            return current.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    @FunctionalInterface
    interface ProcessStarter {
        Process start(Path runtimeDirectory) throws IOException;
    }
}
