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

@SuppressWarnings("PMD.TestClassWithoutTestCases") // Standalone no-framework CLI fixture harness.
public final class DiscordSrvDependencyInventoryTest {
    private static final String BUKKIT_MANIFEST = "plugin.yml";
    private static int assertions;

    public static void main(String[] args) throws Exception {
        withDirectory(dir -> {
            addJar(dir, "legacy.jar", BUKKIT_MANIFEST, "name: DiscordSRV\\nversion: 1\\n");
            addJar(dir, "unrelated.jar", BUKKIT_MANIFEST, "name: Unrelated\\nversion: 1\\n");
            verify(dir, 0, "RESULT=NO_DETECTED_REFERENCES");
        });
        withDirectory(dir -> {
            addJar(dir, "hard.jar", BUKKIT_MANIFEST, "name: Hard\\ndepend: [DiscordSRV]\\n");
            verify(dir, 2, "HARD_DEPENDENCY hard.jar");
        });
        withDirectory(dir -> {
            addJar(dir, "soft.jar", BUKKIT_MANIFEST, "name: Soft\\nsoftdepend:\\n  - DiscordSRV\\n");
            verify(dir, 2, "SOFT_DEPENDENCY soft.jar");
        });
        withDirectory(dir -> {
            addJar(dir, "paper.jar", "paper-plugin.yml",
                    "name: Paper\\ndependencies:\\n  server:\\n    DiscordSRV:\\n      required: true\\n");
            verify(dir, 2, "HARD_DEPENDENCY paper.jar");
        });
        withDirectory(dir -> {
            addJar(dir, "inline-soft.jar", BUKKIT_MANIFEST,
                    "name: LumaGuilds\\nsoftdepend: [Vault, DiscordSRV]\\ndepend: [RoseChat]\\n");
            verify(dir, 2, "SOFT_DEPENDENCY inline-soft.jar");
        });
        withDirectory(dir -> {
            addJar(dir, "paper-optional.jar", "paper-plugin.yml",
                    "name: Optional\\ndependencies:\\n  server:\\n    DiscordSRV:\\n      required: false\\n");
            verify(dir, 2, "SOFT_DEPENDENCY paper-optional.jar");
        });
        withDirectory(dir -> {
            addJar(dir, "unknown-ref.jar", BUKKIT_MANIFEST,
                    "name: Custom\\ncustom-integration: DiscordSRV\\n");
            verify(dir, 2, "REFERENCE unknown-ref.jar");
        });
        withDirectory(dir -> {
            addJar(dir, "comment.jar", BUKKIT_MANIFEST, "name: Clean\\n# softdepend: [DiscordSRV]\\n");
            verify(dir, 0, "RESULT=NO_DETECTED_REFERENCES");
        });
        withDirectory(dir -> {
            addJar(dir, "unknown.jar", "MANIFEST.MF", "Manifest-Version: 1.0\\n");
            verify(dir, 2, "UNVERIFIED unknown.jar");
        });
        withDirectory(dir -> {
            Files.writeString(dir.resolve("broken.jar"), "not a jar");
            verify(dir, 2, "UNVERIFIED broken.jar");
        });
        withDirectory(dir -> {
            addJarWithClass(dir, "bytecode.jar",
                    "name: BytecodeOnly\\nversion: 1\\n",
                    "call github/scarsz/discordsrv/DiscordSRV via reflective code");
            verify(dir, 2, "BYTECODE_REFERENCE bytecode.jar");
        });
        withDirectory(dir -> {
            addJarWithClass(dir, "manifest-plus-class.jar",
                    "name: Optional\nsoftdepend: [DiscordSRV]\n",
                    "invoke com/example/DiscordSrvHook");
            verify(dir, 2, "SOFT_DEPENDENCY manifest-plus-class.jar");
            verify(dir, 2, "BYTECODE_REFERENCE manifest-plus-class.jar");
            verify(dir, 2, "BYTECODE_REFERENCES=1");
            verify(dir, 2, "BYTECODE_ONLY_REFERENCES=0");
            verify(dir, 2, "DEPENDENCY_REFERENCES=1");
        });
        withDirectory(dir -> {
            addJarWithClass(dir, "mixed-case.jar",
                    "name: MixedCase\\nversion: 1\\n",
                    "com/example/DiscordSrvBridge");
            verify(dir, 2, "BYTECODE_REFERENCE mixed-case.jar");
        });
        withDirectory(dir -> {
            addJarWithResource(dir, "resource.jar",
                    "name: ResourceOnly\\nversion: 1\\n",
                    "# Docs mention DiscordSRV but compiled classes have no hooks");
            verify(dir, 0, "BYTECODE_REFERENCES=0");
        });
        withDirectory(dir -> {
            addJarWithClass(dir, "size-limit.jar",
                    "name: LargeClass\\nversion: 1\\n",
                    "x".repeat(1_048_577));
            verify(dir, 2, "UNVERIFIED size-limit.jar");
        });
        withDirectory(dir -> verify(dir, 3, "ERROR: empty plugins folder"));
        System.out.println("PASS: " + assertions + " assertions across 16 isolated JAR inventories");
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

    private static void addJarWithClass(
            Path directory, String filename, String manifest, String bytecode
    ) throws IOException {
        try (JarOutputStream output = new JarOutputStream(
                Files.newOutputStream(directory.resolve(filename)))) {
            addEntry(output, BUKKIT_MANIFEST, manifest.replace("\\n", "\n"));
            addEntry(output, "sample/Example.class", bytecode);
        }
    }

    private static void addJarWithResource(
            Path directory, String filename, String manifest, String resource
    ) throws IOException {
        try (JarOutputStream output = new JarOutputStream(
                Files.newOutputStream(directory.resolve(filename)))) {
            addEntry(output, BUKKIT_MANIFEST, manifest.replace("\\n", "\n"));
            addEntry(output, "readme.txt", resource);
        }
    }

    private static void addEntry(JarOutputStream output, String name, String contents)
            throws IOException {
        output.putNextEntry(new JarEntry(name));
        output.write(contents.getBytes(StandardCharsets.UTF_8));
        output.closeEntry();
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
