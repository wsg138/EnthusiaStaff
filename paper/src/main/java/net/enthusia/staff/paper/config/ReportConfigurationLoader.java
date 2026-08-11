package net.enthusia.staff.paper.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import net.enthusia.staff.common.DurationParser;
import net.enthusia.staff.common.ParsedDuration;
import net.enthusia.staff.domain.report.ReportPolicy;
import net.enthusia.staff.paper.report.ReportGuiConfiguration;
import org.bukkit.Material;

public final class ReportConfigurationLoader {
    private static final String VERSION_FIELD = "version";
    private static final String SUBMISSION_PATH_SUFFIX = ".submission";
    private static final Set<String> POLICY_ROOT_FIELDS = Set.of(
            VERSION_FIELD, "submission", "queries", "evidence"
    );
    private static final Set<String> SUBMISSION_FIELDS = Set.of(
            "any-cooldown", "target-cooldown", "duplicate-window", "max-open-reports"
    );
    private static final Set<String> QUERY_FIELDS = Set.of("max-results", "recently-closed-window");
    private static final Set<String> EVIDENCE_FIELDS = Set.of("retention", "purge-batch-limit");
    private static final Set<String> GUI_ROOT_FIELDS = Set.of(
            VERSION_FIELD, "inventory-size", "content-slots", "action-slots",
            "slots", "materials", "titles", "messages"
    );

    private final ObjectMapper yaml = new ObjectMapper(new YAMLFactory());
    private final DurationParser durations = new DurationParser();
    private final Predicate<Material> itemMaterial;

    public ReportConfigurationLoader() {
        this(Material::isItem);
    }

    ReportConfigurationLoader(Predicate<Material> itemMaterial) {
        this.itemMaterial = Objects.requireNonNull(itemMaterial, "itemMaterial");
    }

    public ReportConfigurationSnapshot load(Path policyFile, Path guiFile) {
        try (Reader policy = Files.newBufferedReader(policyFile);
             Reader gui = Files.newBufferedReader(guiFile)) {
            return load(policy, policyFile.toString(), gui, guiFile.toString());
        } catch (IOException exception) {
            throw new ConfigurationValidationException("Unable to read report configuration", exception);
        }
    }

    public ReportConfigurationSnapshot load(
            InputStream policy,
            String policyName,
            InputStream gui,
            String guiName
    ) {
        if (policy == null || gui == null) {
            throw new ConfigurationValidationException("Report configuration resources were not found");
        }
        try (Reader policyReader = new InputStreamReader(policy, StandardCharsets.UTF_8);
             Reader guiReader = new InputStreamReader(gui, StandardCharsets.UTF_8)) {
            return load(policyReader, policyName, guiReader, guiName);
        } catch (IOException exception) {
            throw new ConfigurationValidationException("Unable to read report configuration resources", exception);
        }
    }

    private ReportConfigurationSnapshot load(
            Reader policyReader,
            String policyName,
            Reader guiReader,
            String guiName
    ) {
        try {
            JsonNode policyRoot = yaml.readTree(policyReader);
            JsonNode guiRoot = yaml.readTree(guiReader);
            return new ReportConfigurationSnapshot(
                    text(policyRoot, VERSION_FIELD, policyName),
                    text(guiRoot, VERSION_FIELD, guiName),
                    parsePolicy(policyRoot, policyName),
                    parseGui(guiRoot, guiName)
            );
        } catch (IOException exception) {
            throw new ConfigurationValidationException("Unable to parse report configuration", exception);
        } catch (ConfigurationValidationException exception) {
            throw exception;
        } catch (IllegalArgumentException exception) {
            throw new ConfigurationValidationException("Invalid report configuration: " + exception.getMessage(), exception);
        }
    }

    private ReportPolicy parsePolicy(JsonNode root, String source) {
        requireObject(root, source);
        rejectUnknown(root, POLICY_ROOT_FIELDS, source);
        JsonNode submission = object(root, "submission", source);
        JsonNode queries = object(root, "queries", source);
        JsonNode evidence = object(root, "evidence", source);
        rejectUnknown(submission, SUBMISSION_FIELDS, source + SUBMISSION_PATH_SUFFIX);
        rejectUnknown(queries, QUERY_FIELDS, source + ".queries");
        rejectUnknown(evidence, EVIDENCE_FIELDS, source + ".evidence");
        return new ReportPolicy(
                duration(submission, "any-cooldown", source + SUBMISSION_PATH_SUFFIX),
                duration(submission, "target-cooldown", source + SUBMISSION_PATH_SUFFIX),
                duration(submission, "duplicate-window", source + SUBMISSION_PATH_SUFFIX),
                integer(submission, "max-open-reports", source + SUBMISSION_PATH_SUFFIX, 1, 100),
                integer(queries, "max-results", source + ".queries", 1, 100),
                duration(queries, "recently-closed-window", source + ".queries"),
                duration(evidence, "retention", source + ".evidence"),
                integer(evidence, "purge-batch-limit", source + ".evidence", 1, 1_000)
        );
    }

    private ReportGuiConfiguration parseGui(JsonNode root, String source) {
        requireObject(root, source);
        rejectUnknown(root, GUI_ROOT_FIELDS, source);
        int size = integer(root, "inventory-size", source, 9, 54);
        List<Integer> contentSlots = integerArray(root, "content-slots", source, size);
        List<Integer> actionSlots = integerArray(root, "action-slots", source, size);
        Map<String, Integer> slots = integerMap(
                object(root, "slots", source),
                ReportGuiConfiguration.SLOT_KEYS,
                source + ".slots",
                size
        );
        Map<String, Material> materials = materialMap(
                object(root, "materials", source),
                ReportGuiConfiguration.MATERIAL_KEYS,
                source + ".materials"
        );
        Map<String, String> titles = textMap(
                object(root, "titles", source),
                ReportGuiConfiguration.TITLE_KEYS,
                source + ".titles"
        );
        Map<String, String> messages = textMap(
                object(root, "messages", source),
                ReportGuiConfiguration.MESSAGE_KEYS,
                source + ".messages"
        );
        return new ReportGuiConfiguration(
                size, contentSlots, actionSlots, slots, materials, titles, messages
        );
    }

    private Duration duration(JsonNode parent, String field, String path) {
        String value = text(parent, field, path);
        try {
            ParsedDuration parsed = durations.parse(value);
            if (parsed.isPermanent()) {
                throw invalid(path + "." + field + " must be a finite duration");
            }
            return parsed.temporary().orElseThrow();
        } catch (IllegalArgumentException exception) {
            throw invalid(path + "." + field + " is invalid: " + exception.getMessage());
        }
    }

    private static List<Integer> integerArray(
            JsonNode parent,
            String field,
            String path,
            int inventorySize
    ) {
        JsonNode value = required(parent, field, path);
        if (!value.isArray() || value.isEmpty()) {
            throw invalid(path + "." + field + " must be a non-empty array");
        }
        List<Integer> slots = new ArrayList<>();
        Set<Integer> unique = new HashSet<>();
        for (int index = 0; index < value.size(); index++) {
            JsonNode slot = value.get(index);
            if (!slot.canConvertToInt() || slot.intValue() < 0 || slot.intValue() >= inventorySize) {
                throw invalid(path + "." + field + "[" + index + "] is outside the inventory");
            }
            if (!unique.add(slot.intValue())) {
                throw invalid(path + "." + field + " contains duplicate slot " + slot.intValue());
            }
            slots.add(slot.intValue());
        }
        return List.copyOf(slots);
    }

    private static Map<String, Integer> integerMap(
            JsonNode node,
            Set<String> keys,
            String path,
            int inventorySize
    ) {
        requireExactKeys(node, keys, path);
        Map<String, Integer> values = new HashMap<>();
        for (String key : keys) {
            values.put(key, integer(node, key, path, 0, inventorySize - 1));
        }
        return Map.copyOf(values);
    }

    private Map<String, Material> materialMap(JsonNode node, Set<String> keys, String path) {
        requireExactKeys(node, keys, path);
        Map<String, Material> values = new HashMap<>();
        for (String key : keys) {
            String raw = text(node, key, path);
            Material material = Material.matchMaterial(raw.toUpperCase(Locale.ROOT));
            if (material == null || !itemMaterial.test(material)) {
                throw invalid(path + "." + key + " is not a valid item material");
            }
            values.put(key, material);
        }
        return Map.copyOf(values);
    }

    private static Map<String, String> textMap(JsonNode node, Set<String> keys, String path) {
        requireExactKeys(node, keys, path);
        Map<String, String> values = new HashMap<>();
        for (String key : keys) {
            values.put(key, text(node, key, path));
        }
        return Map.copyOf(values);
    }

    private static void requireExactKeys(JsonNode node, Set<String> keys, String path) {
        requireObject(node, path);
        Set<String> actual = new HashSet<>();
        node.fieldNames().forEachRemaining(actual::add);
        Set<String> missing = new HashSet<>(keys);
        missing.removeAll(actual);
        Set<String> unknown = new HashSet<>(actual);
        unknown.removeAll(keys);
        if (!missing.isEmpty() || !unknown.isEmpty()) {
            throw invalid(path + " has missing keys " + missing + " and unknown keys " + unknown);
        }
    }

    private static JsonNode object(JsonNode parent, String field, String path) {
        JsonNode value = required(parent, field, path);
        requireObject(value, path + "." + field);
        return value;
    }

    private static JsonNode required(JsonNode parent, String field, String path) {
        JsonNode value = parent == null ? null : parent.get(field);
        if (value == null || value.isNull()) {
            throw invalid(path + "." + field + " is required");
        }
        return value;
    }

    private static String text(JsonNode parent, String field, String path) {
        JsonNode value = required(parent, field, path);
        if (!value.isTextual() || value.textValue().isBlank()) {
            throw invalid(path + "." + field + " must be a non-blank string");
        }
        return value.textValue().trim();
    }

    private static int integer(
            JsonNode parent,
            String field,
            String path,
            int minimum,
            int maximum
    ) {
        JsonNode value = required(parent, field, path);
        if (!value.canConvertToInt() || value.intValue() < minimum || value.intValue() > maximum) {
            throw invalid(path + "." + field + " must be between " + minimum + " and " + maximum);
        }
        return value.intValue();
    }

    private static void rejectUnknown(JsonNode node, Set<String> allowed, String path) {
        Iterator<String> fields = node.fieldNames();
        while (fields.hasNext()) {
            String field = fields.next();
            if (!allowed.contains(field)) {
                throw invalid(path + " contains unknown field " + field);
            }
        }
    }

    private static void requireObject(JsonNode node, String path) {
        if (node == null || !node.isObject()) {
            throw invalid(path + " must be an object");
        }
    }

    private static ConfigurationValidationException invalid(String message) {
        return new ConfigurationValidationException(message);
    }
}
