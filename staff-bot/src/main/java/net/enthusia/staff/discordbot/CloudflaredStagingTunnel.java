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

    private final Path binaryFile;
    private final Path tokenFile;
    private final ProcessStarter processStarter;
    private final AtomicBoolean started = new AtomicBoolean();
    private final AtomicBoolean closing = new AtomicBoolean();
    private volatile Process process;

    CloudflaredStagingTunnel(Path binaryFile, Path tokenFile) {
        this(binaryFile, tokenFile, CloudflaredStagingTunnel::startProcess);
    }

    CloudflaredStagingTunnel(Path binaryFile, Path tokenFile, ProcessStarter processStarter) {
        this.binaryFile = regularFile(binaryFile, "cloudflared binary").toAbsolutePath().normalize();
        this.tokenFile = regularFile(tokenFile, "cloudflared token file").toAbsolutePath().normalize();
        this.processStarter = Objects.requireNonNull(processStarter, "processStarter");
        if (this.binaryFile.equals(this.tokenFile)) {
            throw new IllegalArgumentException("cloudflared binary and token file must differ");
        }
        ensureExecutable(this.binaryFile);
    }

    @Override
    public void start(Runnable unexpectedExit) throws IOException {
        Objects.requireNonNull(unexpectedExit, "unexpectedExit");
        if (!started.compareAndSet(false, true)) {
            throw new IllegalStateException("staging tunnel already started");
        }
        Process candidate = processStarter.start(command());
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
                binaryFile.toString(),
                "tunnel",
                "--no-autoupdate",
                "run",
                "--token-file",
                tokenFile.toString()
        );
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

    private static Path regularFile(Path path, String label) {
        if (path == null || !Files.isRegularFile(path)) {
            throw new IllegalArgumentException(label + " must be a regular file");
        }
        return path;
    }

    private static void ensureExecutable(Path binary) {
        if (Files.isExecutable(binary)) {
            return;
        }
        if (!binary.toFile().setExecutable(true, true) || !Files.isExecutable(binary)) {
            throw new IllegalArgumentException("cloudflared binary is not executable");
        }
    }

    private static Process startProcess(List<String> command) throws IOException {
        return new ProcessBuilder(command)
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
        Process start(List<String> command) throws IOException;
    }
}
