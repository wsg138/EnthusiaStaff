import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.util.Comparator;
import java.util.stream.Stream;

/**
 * Standalone no-framework regression tests for the one-target TLS preflight.
 * Every case fails before DNS/TCP: this suite makes no network connection.
 */
@SuppressWarnings("PMD.TestClassWithoutTestCases")
public final class StaffBotTlsProbeTest {
    private static final String PREFIX = "ENTHUSIA_STAFF_BOT_CHAT_BRIDGE_";
    private static final String HOST = "25319956-7c92-49d1-9afe-ea6e18758016";
    private static final String PORT = "28765";
    private static final String INVALID_CONFIGURATION = "INVALID_LOCAL_CONFIG";
    private static int assertions;

    private StaffBotTlsProbeTest() {
    }

    public static void main(String[] args) throws Exception {
        try (TestWorkspace workspace = new TestWorkspace()) {
            verify(new String[0], 2, "Usage:");
            verify(new String[] {"--help"}, 0, "Usage:");
            verify(new String[] {"missing-file"}, 2, INVALID_CONFIGURATION);

            Path file = workspace.dir.resolve("probe.properties");
            Files.writeString(file, settings("127.0.0.1", PORT, "missing.p12"));
            verify(new String[] {file.toString()}, 2, INVALID_CONFIGURATION);
            Files.writeString(file, settings(HOST, "28766", "missing.p12"));
            verify(new String[] {file.toString()}, 2, INVALID_CONFIGURATION);
            Files.writeString(file, settings(HOST, PORT, "missing.p12"));
            verify(new String[] {file.toString()}, 2, INVALID_CONFIGURATION);

            Path emptyStore = workspace.dir.resolve("empty.p12");
            KeyStore trust = KeyStore.getInstance("PKCS12");
            trust.load(null, "synthetic-test-password".toCharArray());
            try (var stream = Files.newOutputStream(emptyStore)) {
                trust.store(stream, "synthetic-test-password".toCharArray());
            }
            Files.writeString(file, settings(HOST, PORT, emptyStore.toString()));
            verify(new String[] {file.toString()}, 2, INVALID_CONFIGURATION);

            Files.writeString(file, "x".repeat(16_385));
            verify(new String[] {file.toString()}, 2, INVALID_CONFIGURATION);

            Files.writeString(file, settings(HOST, PORT, emptyStore.toString()));
            if (supportsSymlinks(workspace.dir)) {
                Path link = workspace.dir.resolve("probe-link.properties");
                Files.createSymbolicLink(link, file.getFileName());
                verify(new String[] {link.toString()}, 2, INVALID_CONFIGURATION);
            }
        }
        System.out.println("PASS: " + assertions
                + " StaffBot TLS probe negative checks; DNS/TCP NOT attempted");
    }

    private static boolean supportsSymlinks(Path dir) {
        Path target = dir.resolve("symlink-support-test");
        Path link = dir.resolve("symlink-support-link");
        try {
            Files.writeString(target, "test");
            Files.createSymbolicLink(link, target.getFileName());
            return Files.isSymbolicLink(link);
        } catch (Exception exception) {
            return false;
        } finally {
            try {
                Files.deleteIfExists(link);
                Files.deleteIfExists(target);
            } catch (Exception ignored) {
                // Best-effort cleanup in a temporary test-only directory.
            }
        }
    }

    private static String settings(String host, String port, String store) {
        return PREFIX + "HOST=" + host + "\n"
                + PREFIX + "PORT=" + port + "\n"
                + PREFIX + "TRUST_STORE=" + store.replace("\\", "\\\\") + "\n"
                + PREFIX + "TRUST_STORE_PASSWORD=synthetic-test-password\n";
    }

    @SuppressWarnings("PMD.CloseResource") // System.out belongs to the JVM; the capture stream is closed below.
    private static void verify(String[] args, int exit, String expected) {
        PrintStream original = System.out;
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (PrintStream capture = new PrintStream(output, true, StandardCharsets.UTF_8)) {
            System.setOut(capture);
            int actual = StaffBotTlsProbe.run(args);
            assertTrue(actual == exit, "unexpected exit code " + actual);
        } finally {
            System.setOut(original);
        }
        String text = output.toString(StandardCharsets.UTF_8);
        assertTrue(text.contains(expected), "unexpected probe state");
        assertTrue(!text.contains("TLS_VERIFIED"), "invalid configuration must never pass TLS");
    }

    private static void assertTrue(boolean result, String message) {
        assertions++;
        if (!result) {
            throw new AssertionError(message);
        }
    }

    private static final class TestWorkspace implements AutoCloseable {
        final Path dir;

        TestWorkspace() throws Exception {
            dir = Files.createTempDirectory("staffbot-tls-negative-test-");
        }

        @Override
        public void close() throws Exception {
            try (Stream<Path> items = Files.walk(dir)) {
                for (Path item : items.sorted(Comparator.reverseOrder()).toList()) {
                    Files.deleteIfExists(item);
                }
            }
        }
    }
}
