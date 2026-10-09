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
        CLEAR, LEGACY, HARD_DEPENDENCY, SOFT_DEPENDENCY, REFERENCE, UNVERIFIED
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

        int[] counts = new int[JarStatus.values().length];
        for (Path jar : jars) {
            counts[inspectJar(jar, output).ordinal()]++;
        }
        int hard = counts[JarStatus.HARD_DEPENDENCY.ordinal()];
        int soft = counts[JarStatus.SOFT_DEPENDENCY.ordinal()];
        int generic = counts[JarStatus.REFERENCE.ordinal()];
        int references = hard + soft + generic;
        int unknown = counts[JarStatus.UNVERIFIED.ordinal()];
        output.println("TOTAL_JARS=" + jars.size());
        output.println("LEGACY_JARS=" + counts[JarStatus.LEGACY.ordinal()]);
        output.println("HARD_DEPENDENCIES=" + hard);
        output.println("SOFT_DEPENDENCIES=" + soft);
        output.println("OTHER_MANIFEST_REFERENCES=" + generic);
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
            JarStatus relationship = classifyManifests(contents);
            if (relationship != JarStatus.CLEAR) {
                output.println(relationship + " " + label
                        + " (DiscordSRV in plugin manifest; runtime behavior not verified)");
                return relationship;
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

    private static JarStatus classifyManifests(List<String> manifests) {
        JarStatus strongest = JarStatus.CLEAR;
        for (String manifest : manifests) {
            JarStatus status = classifyManifest(manifest);
            if (status == JarStatus.HARD_DEPENDENCY) {
                return status;
            }
            if (status == JarStatus.REFERENCE || (status == JarStatus.SOFT_DEPENDENCY
                    && strongest == JarStatus.CLEAR)) {
                strongest = status;
            }
        }
        return strongest;
    }

    private static JarStatus classifyManifest(String manifest) {
        String section = "";
        JarStatus strongest = JarStatus.CLEAR;
        List<String> lines = manifest.lines().toList();
        for (int i = 0; i < lines.size(); i++) {
            String line = withoutComment(lines.get(i));
            if (line.isBlank()) {
                continue;
            }
            String trimmed = line.strip();
            int indent = line.length() - line.stripLeading().length();
            if (indent == 0 && trimmed.contains(":")) {
                section = trimmed.substring(0, trimmed.indexOf(':')).toLowerCase(Locale.ROOT);
            }
            if (!trimmed.toLowerCase(Locale.ROOT).contains("discordsrv")) {
                continue;
            }
            JarStatus candidate = classifyReference(section, trimmed, lines, i, indent);
            if (candidate == JarStatus.HARD_DEPENDENCY) {
                return candidate;
            }
            strongest = preferConservative(strongest, candidate);
        }
        return strongest;
    }

    private static JarStatus classifyReference(
            String section, String entry, List<String> lines, int index, int indent
    ) {
        return switch (section) {
            case "depend" -> JarStatus.HARD_DEPENDENCY;
            case "softdepend", "loadbefore" -> JarStatus.SOFT_DEPENDENCY;
            case "dependencies" -> entry.matches("(?i)^['\\\"]?DiscordSRV['\\\"]?:\\s*$")
                    ? paperRequiredStatus(lines, index, indent) : JarStatus.REFERENCE;
            default -> JarStatus.REFERENCE;
        };
    }

    private static JarStatus preferConservative(JarStatus current, JarStatus candidate) {
        if (current == JarStatus.REFERENCE || candidate == JarStatus.REFERENCE) {
            return JarStatus.REFERENCE;
        }
        return candidate == JarStatus.SOFT_DEPENDENCY ? candidate : current;
    }

    private static JarStatus paperRequiredStatus(List<String> lines, int index, int parentIndent) {
        for (int i = index + 1; i < lines.size(); i++) {
            String line = withoutComment(lines.get(i));
            if (line.isBlank()) {
                continue;
            }
            int indent = line.length() - line.stripLeading().length();
            if (indent <= parentIndent) {
                break;
            }
            String setting = line.strip().toLowerCase(Locale.ROOT);
            if (setting.matches("required:\\s*true")) {
                return JarStatus.HARD_DEPENDENCY;
            }
            if (setting.matches("required:\\s*false")) {
                return JarStatus.SOFT_DEPENDENCY;
            }
        }
        return JarStatus.REFERENCE;
    }

    private static String withoutComment(String line) {
        int comment = line.indexOf('#');
        return comment < 0 ? line : line.substring(0, comment);
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
