import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.stream.Stream;

public final class DiscordSrvDependencyInventoryTest {
    private static int assertions;

    public static void main(String[] args) throws Exception {
        withDirectory(dir -> {
            addJar(dir, "legacy.jar", "plugin.yml", "name: DiscordSRV\\nversion: 1\\n");
            addJar(dir, "unrelated.jar", "plugin.yml", "name: Unrelated\\nversion: 1\\n");
            verify(dir, 0, "RESULT=NO_MANIFEST_REFERENCES");
        });
        withDirectory(dir -> {
            addJar(dir, "hard.jar", "plugin.yml", "name: Hard\\ndepend: [DiscordSRV]\\n");
            verify(dir, 2, "BLOCKER hard.jar");
        });
        withDirectory(dir -> {
            addJar(dir, "soft.jar", "plugin.yml", "name: Soft\\nsoftdepend:\\n  - DiscordSRV\\n");
            verify(dir, 2, "BLOCKER soft.jar");
        });
        withDirectory(dir -> {
            addJar(dir, "paper.jar", "paper-plugin.yml",
                    "name: Paper\\ndependencies:\\n  server:\\n    DiscordSRV:\\n      required: true\\n");
            verify(dir, 2, "BLOCKER paper.jar");
        });
        withDirectory(dir -> {
            addJar(dir, "comment.jar", "plugin.yml", "name: Clean\\n# softdepend: [DiscordSRV]\\n");
            verify(dir, 0, "RESULT=NO_MANIFEST_REFERENCES");
        });
        withDirectory(dir -> {
            addJar(dir, "unknown.jar", "MANIFEST.MF", "Manifest-Version: 1.0\\n");
            verify(dir, 2, "UNVERIFIED unknown.jar");
        });
        withDirectory(dir -> {
            Files.writeString(dir.resolve("broken.jar"), "not a jar");
            verify(dir, 2, "UNVERIFIED broken.jar");
        });
        withDirectory(dir -> verify(dir, 3, "ERROR: empty plugins folder"));
        System.out.println("PASS: " + assertions + " assertions across 8 isolated JAR inventories");
    }

    private static void verify(Path dir, int expectedCode, String expectedText) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        int code = DiscordSrvDependencyInventory.run(
                new String[] {"--plugins-dir", dir.toString()},
                new PrintStream(output, true, StandardCharsets.UTF_8));
        String text = output.toString(StandardCharsets.UTF_8);
        assertTrue(code == expectedCode, "Expected " + expectedCode + " got " + code + " in " + text);
        assertTrue(text.contains(expectedText), "Missing " + expectedText + " in " + text);
        assertTrue(!text.contains("REMOVAL_AUTHORIZED=true"), "Safety boundary violated");
    }

    private static void addJar(Path dir, String filename, String entry, String contents) throws IOException {
        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(dir.resolve(filename)))) {
            output.putNextEntry(new JarEntry(entry));
            output.write(contents.replace("\\n", "\n").getBytes(StandardCharsets.UTF_8));
            output.closeEntry();
        }
    }

    private static void withDirectory(CheckedConsumer<Path> test) throws Exception {
        Path dir = Files.createTempDirectory("discordsrv-manifest-test-");
        try {
            test.accept(dir);
        } finally {
            try (Stream<Path> entries = Files.walk(dir)) {
                for (Path path : entries.sorted(Comparator.reverseOrder()).toList()) {
                    Files.deleteIfExists(path);
                }
            }
        }
    }

    private static void assertTrue(boolean valid, String error) {
        assertions++;
        if (!valid) {
            throw new AssertionError(error);
        }
    }

    @FunctionalInterface
    private interface CheckedConsumer<T> {
        void accept(T value) throws Exception;
    }
}
