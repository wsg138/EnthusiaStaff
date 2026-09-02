package net.enthusia.staff.discordbot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CloudflaredStagingTunnelTest {
    private static final String TOKEN_FILE_NAME = "cloudflared-token.txt";
    private static final String FIXTURE_CONTENT = Character.toString('x').repeat(5);

    @TempDir
    Path tempDir;

    @Test
    void startsFixedTokenFileCommandWithoutReadingTokenContents() throws Exception {
        Path binary = executable("cloudflared");
        Path token = Files.writeString(tempDir.resolve(TOKEN_FILE_NAME), "private-token-value");
        FakeProcess process = new FakeProcess();
        AtomicReference<Path> directory = new AtomicReference<>();
        CloudflaredStagingTunnel tunnel = new CloudflaredStagingTunnel(
                binary,
                token,
                value -> {
                    directory.set(value);
                    return process;
                }
        );

        tunnel.start(() -> { });

        assertEquals(tempDir.toAbsolutePath().normalize(), directory.get());
        assertEquals(List.of(
                "./cloudflared",
                "tunnel",
                "--no-autoupdate",
                "run",
                "--token-file",
                TOKEN_FILE_NAME
        ), tunnel.command());
        assertFalse(String.join(" ", tunnel.command()).contains("private-token-value"));
        tunnel.close();
    }

    @Test
    void unexpectedExitInvokesFailureCallbackButNormalCloseDoesNot() throws Exception {
        Path binary = executable("cloudflared");
        Path token = Files.writeString(tempDir.resolve(TOKEN_FILE_NAME), FIXTURE_CONTENT);
        FakeProcess unexpected = new FakeProcess();
        AtomicBoolean failed = new AtomicBoolean();
        CloudflaredStagingTunnel tunnel = new CloudflaredStagingTunnel(binary, token, ignored -> unexpected);
        tunnel.start(() -> failed.set(true));

        unexpected.completeExit(1);
        assertTrue(failed.get());

        FakeProcess normal = new FakeProcess();
        AtomicBoolean normalFailed = new AtomicBoolean();
        CloudflaredStagingTunnel normalTunnel =
                new CloudflaredStagingTunnel(binary, token, ignored -> normal);
        normalTunnel.start(() -> normalFailed.set(true));
        normalTunnel.close();
        assertFalse(normalFailed.get());
    }

    @Test
    void rejectsMissingWrongNamedSeparatedAndAlreadyExitedStartup() throws Exception {
        Path binary = executable("cloudflared");
        Path token = Files.writeString(tempDir.resolve(TOKEN_FILE_NAME), FIXTURE_CONTENT);
        assertThrows(IllegalArgumentException.class,
                () -> new CloudflaredStagingTunnel(tempDir.resolve("missing"), token));
        assertThrows(IllegalArgumentException.class,
                () -> new CloudflaredStagingTunnel(binary, tempDir.resolve("missing-token")));

        Path wrongBinary = executable("cloudflared-other");
        assertThrows(IllegalArgumentException.class,
                () -> new CloudflaredStagingTunnel(wrongBinary, token));
        Path wrongToken = Files.writeString(tempDir.resolve("token.txt"), FIXTURE_CONTENT);
        assertThrows(IllegalArgumentException.class,
                () -> new CloudflaredStagingTunnel(binary, wrongToken));

        Path otherDirectory = Files.createDirectory(tempDir.resolve("other"));
        Path separatedToken = Files.writeString(otherDirectory.resolve(TOKEN_FILE_NAME), FIXTURE_CONTENT);
        assertThrows(IllegalArgumentException.class,
                () -> new CloudflaredStagingTunnel(binary, separatedToken));

        FakeProcess exited = new FakeProcess();
        exited.completeExit(1);
        CloudflaredStagingTunnel tunnel = new CloudflaredStagingTunnel(binary, token, ignored -> exited);
        assertThrows(IOException.class, () -> tunnel.start(() -> { }));
    }

    private Path executable(String name) throws IOException {
        Path file = Files.writeString(tempDir.resolve(name), "binary");
        assertTrue(file.toFile().setExecutable(true, true));
        return file;
    }

    private static final class FakeProcess extends Process {
        private final CompletableFuture<Process> exit = new CompletableFuture<>();
        private final OutputStream output = new ByteArrayOutputStream();
        private final InputStream input = new ByteArrayInputStream(new byte[0]);
        private volatile boolean alive = true;
        private volatile int exitCode;

        void completeExit(int code) {
            exitCode = code;
            alive = false;
            exit.complete(this);
        }

        @Override
        public OutputStream getOutputStream() {
            return output;
        }

        @Override
        public InputStream getInputStream() {
            return input;
        }

        @Override
        public InputStream getErrorStream() {
            return input;
        }

        @Override
        public int waitFor() throws InterruptedException {
            try {
                return exit.get().exitValue();
            } catch (ExecutionException exception) {
                throw new IllegalStateException("fake process completion failed", exception);
            }
        }

        @Override
        public boolean waitFor(long timeout, TimeUnit unit) {
            return !alive;
        }

        @Override
        public int exitValue() {
            if (alive) {
                throw new IllegalThreadStateException();
            }
            return exitCode;
        }

        @Override
        public void destroy() {
            completeExit(0);
        }

        @Override
        public Process destroyForcibly() {
            completeExit(137);
            return this;
        }

        @Override
        public boolean isAlive() {
            return alive;
        }

        @Override
        public CompletableFuture<Process> onExit() {
            return exit;
        }
    }
}
