package net.enthusia.staff.velocity;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** Owns an optional, administrator-installed connector for the loopback website API. */
final class WebsiteTunnelConnector implements AutoCloseable {
    private final Process process;
    private final AtomicBoolean closing = new AtomicBoolean();

    private WebsiteTunnelConnector(Process process, Runnable unexpectedExit) throws IOException {
        this.process = process;
        if (!process.isAlive()) {
            throw new IOException("Website connector exited during startup");
        }
        process.onExit().thenRun(() -> {
            if (!closing.get()) unexpectedExit.run();
        });
    }

    static Optional<WebsiteTunnelConnector> startIfInstalled(Path dataDirectory, Runnable unexpectedExit)
            throws IOException {
        Path directory = dataDirectory.resolve("website-tunnel").toAbsolutePath().normalize();
        Path token = directory.resolve("connector-token");
        if (!Files.exists(token, LinkOption.NOFOLLOW_LINKS)) return Optional.empty();
        Path binary = directory.resolve("cloudflared");
        if (!Files.isRegularFile(binary, LinkOption.NOFOLLOW_LINKS) || !Files.isExecutable(binary)
                || !Files.isRegularFile(token, LinkOption.NOFOLLOW_LINKS) || Files.size(token) > 8192
                || !Files.readString(token).trim().matches("[A-Za-z0-9._=-]{100,8192}")) {
            throw new IOException("Website connector installation is invalid");
        }
        Process process = new ProcessBuilder(
                "./cloudflared", "tunnel", "--protocol", "http2", "--no-autoupdate",
                "run", "--token-file", "connector-token")
                .directory(directory.toFile())
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(ProcessBuilder.Redirect.DISCARD).start();
        return Optional.of(new WebsiteTunnelConnector(process, unexpectedExit));
    }

    static List<String> command() {
        return List.of("./cloudflared",
                "tunnel", "--protocol", "http2", "--no-autoupdate", "run", "--token-file", "connector-token");
    }

    boolean running() {
        return !closing.get() && process.isAlive();
    }

    @Override
    public void close() {
        closing.set(true);
        if (!process.isAlive()) return;
        process.destroy();
        try {
            if (!process.waitFor(5, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                process.waitFor(2, TimeUnit.SECONDS);
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
        }
    }
}
