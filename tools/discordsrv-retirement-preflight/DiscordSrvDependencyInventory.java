import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Stream;

/**
 * Read-only inventory of Paper plugin manifests and class-file references to DiscordSRV.
 * No plugin data/config, credentials, network operations, or mutations.
 *
 * This is an early removal blocker scan, NOT an authorization to uninstall.
 */
public final class DiscordSrvDependencyInventory {
    private static final int MAX_JARS = 1000;
    private static final int MAX_MANIFEST_BYTES = 65_536;
    private static final int MAX_CLASS_BYTES = 1_048_576;
    private static final long MAX_TOTAL_CLASS_BYTES = 268_435_456L;
    private static final int MAX_CLASS_ENTRIES = 100_000;
    private static final byte[] BYTECODE_MARKER =
            "discordsrv".getBytes(StandardCharsets.US_ASCII);
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
        CLEAR, LEGACY, HARD_DEPENDENCY, SOFT_DEPENDENCY, REFERENCE, BYTECODE_REFERENCE, UNVERIFIED
    }

    private record JarInspection(JarStatus status, boolean bytecodeReference) {
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
        int bytecodeHits = 0;
        for (Path jar : jars) {
            JarInspection result = inspectJar(jar, output);
            counts[result.status().ordinal()]++;
            if (result.bytecodeReference()) {
                bytecodeHits++;
            }
        }
        return summarizeCounts(jars.size(), counts, bytecodeHits, output);
    }

    private static int summarizeCounts(
            int totalJars, int[] counts, int bytecodeHits, PrintStream output
    ) {
        int hard = counts[JarStatus.HARD_DEPENDENCY.ordinal()];
        int soft = counts[JarStatus.SOFT_DEPENDENCY.ordinal()];
        int generic = counts[JarStatus.REFERENCE.ordinal()];
        int bytecodeOnly = counts[JarStatus.BYTECODE_REFERENCE.ordinal()];
        int references = hard + soft + generic + bytecodeOnly;
        int unknown = counts[JarStatus.UNVERIFIED.ordinal()];
        output.println("TOTAL_JARS=" + totalJars);
        output.println("LEGACY_JARS=" + counts[JarStatus.LEGACY.ordinal()]);
        output.println("HARD_DEPENDENCIES=" + hard);
        output.println("SOFT_DEPENDENCIES=" + soft);
        output.println("OTHER_MANIFEST_REFERENCES=" + generic);
        output.println("BYTECODE_REFERENCES=" + bytecodeHits);
        output.println("BYTECODE_ONLY_REFERENCES=" + bytecodeOnly);
        output.println("DEPENDENCY_REFERENCES=" + references);
        output.println("UNVERIFIED_JARS=" + unknown);
        output.println("SCOPED_TO_MANIFESTS_ONLY=false");
        output.println("CLASS_UTF8_SCANNING_ENABLED=true");
        output.println("REMOVAL_AUTHORIZED=false");
        output.println(references > 0 || unknown > 0 ? "RESULT=BLOCKED" : "RESULT=NO_DETECTED_REFERENCES");
        return references > 0 || unknown > 0 ? 2 : 0;
    }

    private static JarInspection inspectJar(Path jar, PrintStream output) {
        String label = safeLabel(jar.getFileName().toString());
        if (Files.isSymbolicLink(jar) || !Files.isRegularFile(jar, LinkOption.NOFOLLOW_LINKS)) {
            output.println("UNVERIFIED " + label + " (not a regular JAR)");
            return new JarInspection(JarStatus.UNVERIFIED, false);
        }
        try (JarFile archive = new JarFile(jar.toFile(), false)) {
            List<String> contents = readManifests(archive);
            if (contents.isEmpty()) {
                output.println("UNVERIFIED " + label + " (no Paper/Bukkit manifest)");
                return new JarInspection(JarStatus.UNVERIFIED, false);
            }
            if (isDiscordSrvPlugin(contents)) {
                output.println("LEGACY_PLUGIN " + label + " (DiscordSRV installed)");
                return new JarInspection(JarStatus.LEGACY, false);
            }
            JarStatus manifest = classifyManifests(contents);
            boolean bytecode = hasBytecodeReference(archive);
            if (manifest != JarStatus.CLEAR) {
                output.println(manifest + " " + label
                        + " (DiscordSRV in plugin manifest; runtime behavior not verified)");
            }
            if (bytecode) {
                output.println("BYTECODE_REFERENCE " + label
                        + " (class-file symbol or literal; confirm runtime dependency)");
            }
            JarStatus result = manifest == JarStatus.CLEAR && bytecode
                    ? JarStatus.BYTECODE_REFERENCE : manifest;
            return new JarInspection(result, bytecode);
        } catch (IOException | IllegalArgumentException invalidJar) {
            output.println("UNVERIFIED " + label + " (unreadable or oversized manifest/class files)");
            return new JarInspection(JarStatus.UNVERIFIED, false);
        }
    }

    /**
     * Scan only compiled classes. Source archives, resource documents and unrelated
     * configuration are intentionally outside this low-noise reference check.
     * Class-file UTF8 constants include direct JVM symbols and reflective literals.
     */
    private static boolean hasBytecodeReference(JarFile jar) throws IOException {
        long inspectedBytes = 0;
        int inspectedClasses = 0;
        Enumeration<JarEntry> entries = jar.entries();
        while (entries.hasMoreElements()) {
            JarEntry entry = entries.nextElement();
            if (entry.isDirectory() || !entry.getName().endsWith(".class")) {
                continue;
            }
            inspectedClasses++;
            if (inspectedClasses > MAX_CLASS_ENTRIES || entry.getSize() > MAX_CLASS_BYTES) {
                throw new IOException("class scan size limit");
            }
            byte[] contents;
            try (InputStream input = jar.getInputStream(entry)) {
                contents = input.readNBytes(MAX_CLASS_BYTES + 1);
            }
            inspectedBytes += contents.length;
            if (contents.length > MAX_CLASS_BYTES || inspectedBytes > MAX_TOTAL_CLASS_BYTES) {
                throw new IOException("expanded class scan size limit");
            }
            if (containsAsciiIgnoreCase(contents, BYTECODE_MARKER)) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsAsciiIgnoreCase(byte[] haystack, byte[] needle) {
        for (int start = 0; start <= haystack.length - needle.length; start++) {
            int matched = 0;
            while (matched < needle.length
                    && asciiLower(haystack[start + matched]) == needle[matched]) {
                matched++;
            }
            if (matched == needle.length) {
                return true;
            }
        }
        return false;
    }

    private static int asciiLower(byte raw) {
        int letter = raw & 0xff;
        return letter >= 'A' && letter <= 'Z' ? letter + ('a' - 'A') : letter;
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
