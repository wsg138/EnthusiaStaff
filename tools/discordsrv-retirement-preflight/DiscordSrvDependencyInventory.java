import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Stream;

/**
 * Read-only inventory of Paper plugin manifest references to DiscordSRV.
 * No config files, credentials, class files, network operations, or mutations.
 *
 * This is an early removal blocker scan, NOT an authorization to uninstall.
 */
public final class DiscordSrvDependencyInventory {
    private static final int MAX_JARS = 1000;
    private static final int MAX_MANIFEST_BYTES = 65_536;
    private static final String[] MANIFESTS = {"plugin.yml", "paper-plugin.yml"};

    private DiscordSrvDependencyInventory() {
    }

    public static void main(String[] args) {
        System.exit(run(args, System.out));
    }

    static int run(String[] args, PrintStream output) {
        if (args.length != 2 || !"--plugins-dir".equals(args[0])) {
            output.println("USAGE: java DiscordSrvDependencyInventory --plugins-dir <Paper plugins folder>");
            return 3;
        }
        try {
            Path dir = Path.of(args[1]);
            if (Files.isSymbolicLink(dir) || !Files.isDirectory(dir, LinkOption.NOFOLLOW_LINKS)) {
                output.println("ERROR: plugins directory missing or is a symlink");
                return 3;
            }
            return scan(dir, output);
        } catch (IOException | RuntimeException failure) {
            // Never include exception messages: paths can contain sensitive server details.
            output.println("ERROR: scan failed (" + failure.getClass().getSimpleName() + ")");
            return 3;
        }
    }

    private enum JarStatus {
        CLEAR, LEGACY, REFERENCE, UNVERIFIED
    }

    private static int scan(Path directory, PrintStream output) throws IOException {
        List<Path> jars;
        try (Stream<Path> entries = Files.list(directory)) {
            jars = entries.filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".jar"))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .limit(MAX_JARS + 1L)
                    .toList();
        }
        if (jars.isEmpty() || jars.size() > MAX_JARS) {
            output.println("ERROR: empty plugins folder or too many JARs to scan");
            return 3;
        }

        int references = 0;
        int unknown = 0;
        int legacyJar = 0;
        for (Path jar : jars) {
            JarStatus status = inspectJar(jar, output);
            if (status == JarStatus.REFERENCE) {
                references++;
            } else if (status == JarStatus.UNVERIFIED) {
                unknown++;
            } else if (status == JarStatus.LEGACY) {
                legacyJar++;
            }
        }
        output.println("TOTAL_JARS=" + jars.size());
        output.println("LEGACY_JARS=" + legacyJar);
        output.println("DEPENDENCY_REFERENCES=" + references);
        output.println("UNVERIFIED_JARS=" + unknown);
        output.println("SCOPED_TO_MANIFESTS_ONLY=true");
        output.println("REMOVAL_AUTHORIZED=false");
        output.println(references > 0 || unknown > 0 ? "RESULT=BLOCKED" : "RESULT=NO_MANIFEST_REFERENCES");
        return references > 0 || unknown > 0 ? 2 : 0;
    }

    private static JarStatus inspectJar(Path jar, PrintStream output) {
        String label = safeLabel(jar.getFileName().toString());
        if (Files.isSymbolicLink(jar) || !Files.isRegularFile(jar, LinkOption.NOFOLLOW_LINKS)) {
            output.println("UNVERIFIED " + label + " (not a regular JAR)");
            return JarStatus.UNVERIFIED;
        }
        try (JarFile archive = new JarFile(jar.toFile(), false)) {
            List<String> contents = readManifests(archive);
            if (contents.isEmpty()) {
                output.println("UNVERIFIED " + label + " (no Paper/Bukkit manifest)");
                return JarStatus.UNVERIFIED;
            }
            if (isDiscordSrvPlugin(contents)) {
                output.println("LEGACY_PLUGIN " + label + " (DiscordSRV installed)");
                return JarStatus.LEGACY;
            }
            if (mentionsDiscordSrv(contents)) {
                output.println("BLOCKER " + label + " (DiscordSRV referenced in plugin manifest)");
                return JarStatus.REFERENCE;
            }
            return JarStatus.CLEAR;
        } catch (IOException | IllegalArgumentException invalidJar) {
            output.println("UNVERIFIED " + label + " (unreadable or oversized plugin manifest)");
            return JarStatus.UNVERIFIED;
        }
    }

    private static boolean isDiscordSrvPlugin(List<String> manifests) {
        return manifests.stream().anyMatch(text -> text.lines().anyMatch(line ->
                line.trim().matches("(?i)^name:\\s*['\\\"]?DiscordSRV['\\\"]?\\s*(?:#.*)?$")));
    }

    private static boolean mentionsDiscordSrv(List<String> manifests) {
        // Conservatively include optional dependencies and custom compatibility hooks.
        return manifests.stream().flatMap(String::lines)
                .map(String::strip)
                .filter(line -> !line.startsWith("#"))
                .anyMatch(line -> line.toLowerCase(Locale.ROOT).contains("discordsrv"));
    }

    private static List<String> readManifests(JarFile jar) throws IOException {
        List<String> result = new ArrayList<>();
        for (String name : MANIFESTS) {
            JarEntry entry = jar.getJarEntry(name);
            if (entry == null) {
                continue;
            }
            if (entry.getSize() > MAX_MANIFEST_BYTES) {
                throw new IOException("Oversized manifest");
            }
            try (InputStream in = jar.getInputStream(entry)) {
                byte[] data = in.readNBytes(MAX_MANIFEST_BYTES + 1);
                if (data.length > MAX_MANIFEST_BYTES) {
                    throw new IOException("Oversized manifest");
                }
                result.add(new String(data, StandardCharsets.UTF_8));
            }
        }
        return result;
    }

    private static String safeLabel(String label) {
        return label.replaceAll("[^A-Za-z0-9._-]", "_");
    }
}
