package net.enthusia.staff.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Exhaustive guard for the user-visible Paper feature surface.
 *
 * <p>This deliberately fails when a command/permission/feature is added without updating the
 * reviewed contract and without a concrete regression suite somewhere in the repository.</p>
 */
final class AllFeatureSurfaceContractTest {
    private static final String RANK_HELPER = "enthusiastaff.rank.helper";
    private static final String RANK_MOD = "enthusiastaff.rank.mod";
    private static final String RANK_ADMIN = "enthusiastaff.rank.admin";
    private static final String RANK_FOUNDER = "enthusiastaff.rank.founder";

    private static final Set<String> EXPECTED_COMMANDS = Set.of(
            "estaff",
            "history",
            "punish",
            "ban",
            "mute",
            "warn",
            "kick",
            "ipban",
            "removepunishment",
            "unban",
            "unmute",
            "removewarning",
            "unwarn",
            "report",
            "reports",
            "freeze",
            "unfreeze",
            "staff",
            "stafftools",
            "cheattester",
            "fakebase",
            "vanish",
            "staffchat",
            "staffwho",
            "client",
            "invsee",
            "endersee",
            "inspect",
            "case",
            "link",
            "unlink"
    );

    private static final Set<String> COMMANDS_WITHOUT_OUTER_PERMISSION = Set.of(
            "estaff",
            "report",
            "case",
            "link",
            "unlink",
            "staffchat"
    );

    private static final Map<String, List<String>> FEATURE_TEST_MARKERS = featureTestMarkers();

    @Test
    void manifestContainsExactlyTheReviewedCommandSurface() throws IOException {
        JsonNode commands = pluginMetadata().path("commands");
        Set<String> actual = new TreeSet<>();
        commands.fieldNames().forEachRemaining(actual::add);

        assertEquals(new TreeSet<>(EXPECTED_COMMANDS), actual);
    }

    @Test
    void everyManifestCommandIsBoundByTheRuntime() throws IOException {
        Path moduleRoot = moduleRoot();
        String registrar = Files.readString(moduleRoot.resolve(
                "src/main/java/net/enthusia/staff/paper/PaperCommandRegistrar.java"
        ));
        String runtime = Files.readString(moduleRoot.resolve(
                "src/main/java/net/enthusia/staff/paper/PaperRuntimeComponents.java"
        ));
        String wiring = registrar + "\n" + runtime;

        for (String command : EXPECTED_COMMANDS) {
            assertTrue(
                    wiring.contains("\"" + command + "\""),
                    () -> command + " is declared in plugin.yml but has no reviewed runtime binding"
            );
        }
    }

    @Test
    void commandPermissionBoundariesAreCompleteAndIntentional() throws IOException {
        JsonNode metadata = pluginMetadata();
        JsonNode commands = metadata.path("commands");
        JsonNode permissions = metadata.path("permissions");
        Set<String> actualUnpermissioned = new TreeSet<>();

        commands.properties().forEach(entry -> {
            JsonNode permission = entry.getValue().path("permission");
            if (permission.isMissingNode() || permission.asText().isBlank()) {
                actualUnpermissioned.add(entry.getKey());
                return;
            }
            assertTrue(
                    permissions.has(permission.asText()),
                    () -> entry.getKey() + " references undeclared permission " + permission.asText()
            );
        });

        assertEquals(new TreeSet<>(COMMANDS_WITHOUT_OUTER_PERMISSION), actualUnpermissioned);
    }

    @Test
    void allStaffPermissionsFailClosedAndEveryChildPermissionExists() throws IOException {
        JsonNode permissions = pluginMetadata().path("permissions");
        assertTrue(permissions.isObject() && !permissions.isEmpty(),
                "plugin.yml must declare Staff permissions");

        permissions.properties().forEach(entry -> {
            String permission = entry.getKey();
            JsonNode definition = entry.getValue();
            assertTrue(permission.startsWith("enthusiastaff."), permission);
            assertTrue(definition.has("default"), permission + " has no explicit default");
            JsonNode defaultValue = definition.get("default");
            assertTrue(defaultValue.isBoolean(), permission + " default must be literal boolean false");
            assertFalse(defaultValue.booleanValue(), permission + " must default to false");

            JsonNode children = definition.path("children");
            if (!children.isObject()) {
                return;
            }
            children.properties().forEach(child -> {
                assertTrue(permissions.has(child.getKey()),
                        () -> permission + " references undeclared child " + child.getKey());
                assertTrue(child.getValue().isBoolean(),
                        () -> permission + " must declare a boolean grant for child " + child.getKey());
                assertTrue(child.getValue().booleanValue(),
                        () -> permission + " must grant child " + child.getKey());
            });
        });
    }

    @Test
    void staffRankInheritanceRetainsTheReviewedEscalationChain() throws IOException {
        JsonNode permissions = pluginMetadata().path("permissions");
        assertGranted(permissions, RANK_MOD, RANK_HELPER);
        assertGranted(permissions, RANK_ADMIN, RANK_MOD);
        assertGranted(permissions, RANK_FOUNDER, RANK_ADMIN);

        assertGranted(permissions, RANK_HELPER, "enthusiastaff.staffmode");
        assertGranted(permissions, RANK_HELPER, "enthusiastaff.stafftools.teleport");
        assertGranted(permissions, RANK_HELPER, "enthusiastaff.stafftools.spectate");
        assertGranted(permissions, RANK_HELPER, "enthusiastaff.vanish");
        assertGranted(permissions, RANK_HELPER, "enthusiastaff.staffwho");
        assertGranted(permissions, RANK_MOD, "enthusiastaff.remove");
        assertGranted(permissions, RANK_MOD, "enthusiastaff.inventory.edit");
        assertGranted(permissions, RANK_ADMIN, "enthusiastaff.cheattester.cancel-any");
        assertGranted(permissions, RANK_FOUNDER, "enthusiastaff.owner.recovery");
    }

    @Test
    void everyMajorFeatureFamilyHasConcreteRegressionCoverage() throws IOException {
        Path root = repositoryRoot();
        Set<String> testPaths;
        try (Stream<Path> paths = Files.walk(root)) {
            testPaths = paths
                    .filter(Files::isRegularFile)
                    .map(root::relativize)
                    .map(Path::toString)
                    .map(value -> value.replace('\\', '/'))
                    .filter(value -> value.contains("/src/test/") || value.startsWith("src/test/"))
                    .filter(value -> value.endsWith("Test.java"))
                    .map(value -> value.toLowerCase(Locale.ROOT))
                    .collect(Collectors.toSet());
        }

        Map<String, List<String>> missing = new LinkedHashMap<>();
        FEATURE_TEST_MARKERS.forEach((feature, markers) -> {
            boolean covered = markers.stream()
                    .map(marker -> marker.toLowerCase(Locale.ROOT))
                    .anyMatch(marker -> testPaths.stream().anyMatch(path -> path.contains(marker)));
            if (!covered) {
                missing.put(feature, markers);
            }
        });

        if (!missing.isEmpty()) {
            fail("Major Staff feature families without a concrete regression-test source: " + missing);
        }
    }

    private static Map<String, List<String>> featureTestMarkers() {
        Map<String, List<String>> markers = new LinkedHashMap<>();
        markers.put("runtime/reload", List.of("estaff", "runtimelifecycle"));
        markers.put("punishment workflow", List.of("punishment"));
        markers.put("sanction changes", List.of("sanction"));
        markers.put("history", List.of("history"));
        markers.put("reports/evidence", List.of("report"));
        markers.put("freeze", List.of("freeze"));
        markers.put("staff mode", List.of("staffmode"));
        markers.put("staff tools", List.of("stafftool"));
        markers.put("cheat tester", List.of("cheattester"));
        markers.put("fake base", List.of("fakebase"));
        markers.put("vanish/visibility", List.of("vanish", "visibility"));
        markers.put("staff chat", List.of("staffchat", "rosechat"));
        markers.put("client evidence", List.of("client", "evidence"));
        markers.put("inventory/ender chest", List.of("inventory"));
        markers.put("inspection/confiscation", List.of("inspect", "confiscation"));
        markers.put("case/recovery", List.of("case", "recovery"));
        markers.put("account linking", List.of("accountlink", "linking"));
        markers.put("Discord moderation", List.of("discord"));
        markers.put("network identity/alts", List.of("alt", "identity"));
        markers.put("persistence/migrations", List.of("jdbc", "migration", "mariadb"));
        markers.put("Velocity runtime", List.of("velocity"));
        return Map.copyOf(markers);
    }

    private static void assertGranted(JsonNode permissions, String parent, String child) {
        assertTrue(permissions.has(parent), parent);
        assertTrue(permissions.has(child), child);
        assertTrue(permissions.path(parent).path("children").path(child).asBoolean(),
                parent + " must grant " + child);
    }

    private static JsonNode pluginMetadata() throws IOException {
        try (InputStream input = Thread.currentThread()
                .getContextClassLoader()
                .getResourceAsStream("plugin.yml")) {
            if (input == null) {
                throw new IOException("plugin.yml is absent from the test classpath");
            }
            return new ObjectMapper(new YAMLFactory()).readTree(input);
        }
    }

    private static Path moduleRoot() {
        Path current = Path.of("").toAbsolutePath().normalize();
        if (Files.exists(current.resolve("src/main/java/net/enthusia/staff/paper/PaperCommandRegistrar.java"))) {
            return current;
        }
        Path paper = current.resolve("paper");
        if (Files.exists(paper.resolve("src/main/java/net/enthusia/staff/paper/PaperCommandRegistrar.java"))) {
            return paper;
        }
        throw new IllegalStateException("Could not locate the Paper module from " + current);
    }

    private static Path repositoryRoot() {
        Path module = moduleRoot();
        Path parent = module.getParent();
        if (parent != null && Files.exists(parent.resolve("settings.gradle.kts"))) {
            return parent;
        }
        if (Files.exists(module.resolve("settings.gradle.kts"))) {
            return module;
        }
        throw new IllegalStateException("Could not locate repository root from " + module);
    }
}
