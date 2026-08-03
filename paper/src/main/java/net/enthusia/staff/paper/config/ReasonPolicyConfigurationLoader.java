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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.enthusia.staff.common.DurationParser;
import net.enthusia.staff.common.ParsedDuration;
import net.enthusia.staff.domain.auth.StaffRank;
import net.enthusia.staff.domain.escalation.AltInheritanceMode;
import net.enthusia.staff.domain.escalation.PunishmentStep;
import net.enthusia.staff.domain.escalation.ReasonPolicy;
import net.enthusia.staff.domain.escalation.RemovedReason;
import net.enthusia.staff.domain.ports.AtomicReasonPolicyRepository;
import net.enthusia.staff.domain.sanction.SanctionLength;
import net.enthusia.staff.domain.sanction.SanctionSpec;
import net.enthusia.staff.domain.sanction.SanctionType;

public final class ReasonPolicyConfigurationLoader {
    private static final Set<String> ROOT_FIELDS = Set.of(
            "version", "defaults", "aliases", "removed-reasons", "reasons"
    );
    private static final Set<String> DEFAULT_FIELDS = Set.of(
            "decay-eligible", "public-default", "reportable", "confiscation-options",
            "required-rank", "automatic-detection-eligible", "alt-inheritance"
    );
    private static final Set<String> ALIAS_FIELDS = Set.of("id", "target");
    private static final Set<String> REMOVED_REASON_FIELDS = Set.of("id", "family", "display-name");
    private static final Set<String> REASON_FIELDS = Set.of(
            "id", "family", "display-name", "examples", "severity", "decay-eligible",
            "public-default", "reportable", "confiscation-options", "required-rank",
            "automatic-detection-eligible", "alt-inheritance", "ladder"
    );
    private static final Set<String> STEP_FIELDS = Set.of("label", "sanctions");
    private static final Set<String> SANCTION_FIELDS = Set.of("type", "duration");

    private final ObjectMapper yaml = new ObjectMapper(new YAMLFactory());
    private final DurationParser durations = new DurationParser();

    public LoadedPolicies load(Path file) {
        try (Reader reader = Files.newBufferedReader(file)) {
            return load(reader, file.getFileName().toString());
        } catch (IOException exception) {
            throw new ConfigurationValidationException("Unable to read " + file.getFileName(), exception);
        }
    }

    public LoadedPolicies load(InputStream input, String sourceName) {
        if (input == null) {
            throw new ConfigurationValidationException(sourceName + " was not found");
        }
        try (Reader reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
            return load(reader, sourceName);
        } catch (IOException exception) {
            throw new ConfigurationValidationException("Unable to read " + sourceName, exception);
        }
    }

    private LoadedPolicies load(Reader reader, String sourceName) {
        try {
            JsonNode root = yaml.readTree(reader);
            requireObject(root, "root");
            rejectUnknown(root, ROOT_FIELDS, "root");
            String version = text(root, "version", "root");
            Defaults defaults = parseDefaults(required(root, "defaults", "root"));
            JsonNode reasons = required(root, "reasons", "root");
            if (!reasons.isArray() || reasons.isEmpty()) {
                throw invalid("root.reasons must be a non-empty array");
            }
            List<ReasonPolicy> policies = new ArrayList<>();
            Set<String> identifiers = new HashSet<>();
            for (int index = 0; index < reasons.size(); index++) {
                ReasonPolicy policy = parseReason(reasons.get(index), "reasons[" + index + "]", defaults);
                if (!identifiers.add(policy.id())) {
                    throw invalid("duplicate reason policy: " + policy.id());
                }
                policies.add(policy);
            }
            List<RemovedReason> removedReasons = parseRemovedReasons(root.get("removed-reasons"), identifiers);
            Map<String, String> aliases = parseAliases(root.get("aliases"), identifiers, removedReasons);
            new AtomicReasonPolicyRepository(version, policies, aliases, removedReasons);
            return new LoadedPolicies(version, policies, aliases, removedReasons);
        } catch (IOException exception) {
            throw new ConfigurationValidationException("Unable to read " + sourceName, exception);
        } catch (ConfigurationValidationException exception) {
            throw exception;
        } catch (IllegalArgumentException exception) {
            throw new ConfigurationValidationException("Invalid punishment policy: " + exception.getMessage(), exception);
        }
    }

    private List<RemovedReason> parseRemovedReasons(JsonNode node, Set<String> activeIds) {
        if (node == null) {
            return List.of();
        }
        if (!node.isArray()) {
            throw invalid("root.removed-reasons must be an array");
        }
        List<RemovedReason> removed = new ArrayList<>();
        Set<String> identifiers = new HashSet<>();
        for (int index = 0; index < node.size(); index++) {
            String path = "removed-reasons[" + index + "]";
            JsonNode value = node.get(index);
            requireObject(value, path);
            rejectUnknown(value, REMOVED_REASON_FIELDS, path);
            RemovedReason reason = new RemovedReason(
                    text(value, "id", path),
                    text(value, "family", path),
                    text(value, "display-name", path)
            );
            if (activeIds.contains(reason.id())) {
                throw invalid(path + ".id overlaps active reason policy " + reason.id());
            }
            if (!identifiers.add(reason.id())) {
                throw invalid(path + ".id duplicates removed reason " + reason.id());
            }
            removed.add(reason);
        }
        return List.copyOf(removed);
    }

    private Map<String, String> parseAliases(
            JsonNode node,
            Set<String> activeIds,
            List<RemovedReason> removedReasons
    ) {
        if (node == null) {
            return Map.of();
        }
        if (!node.isArray()) {
            throw invalid("root.aliases must be an array");
        }
        Set<String> removedIds = new HashSet<>();
        removedReasons.forEach(reason -> removedIds.add(reason.id()));
        Map<String, String> aliases = new LinkedHashMap<>();
        for (int index = 0; index < node.size(); index++) {
            String path = "aliases[" + index + "]";
            JsonNode value = node.get(index);
            requireObject(value, path);
            rejectUnknown(value, ALIAS_FIELDS, path);
            String id = text(value, "id", path);
            String target = text(value, "target", path);
            if (id.equals(target)) {
                throw invalid(path + " cannot target itself");
            }
            if (activeIds.contains(id)) {
                throw invalid(path + ".id overlaps active reason policy " + id);
            }
            if (removedIds.contains(id)) {
                throw invalid(path + ".id overlaps removed reason " + id);
            }
            if (!activeIds.contains(target)) {
                throw invalid(path + ".target must reference an active reason policy: " + target);
            }
            if (aliases.putIfAbsent(id, target) != null) {
                throw invalid(path + ".id duplicates reason alias " + id);
            }
        }
        return Map.copyOf(aliases);
    }

    private Defaults parseDefaults(JsonNode node) {
        String path = "root.defaults";
        requireObject(node, path);
        rejectUnknown(node, DEFAULT_FIELDS, path);
        boolean publicByDefault = bool(node, "public-default", path);
        requirePublicDefault(publicByDefault, path + ".public-default");
        return new Defaults(
                bool(node, "decay-eligible", path),
                publicByDefault,
                bool(node, "reportable", path),
                bool(node, "confiscation-options", path),
                enumValue(StaffRank.class, text(node, "required-rank", path), path + ".required-rank"),
                bool(node, "automatic-detection-eligible", path),
                enumValue(AltInheritanceMode.class, text(node, "alt-inheritance", path), path + ".alt-inheritance")
        );
    }

    private ReasonPolicy parseReason(JsonNode node, String path, Defaults defaults) {
        requireObject(node, path);
        rejectUnknown(node, REASON_FIELDS, path);
        String id = text(node, "id", path);
        List<String> examples = new ArrayList<>();
        JsonNode exampleNode = node.get("examples");
        if (exampleNode != null) {
            if (!exampleNode.isArray()) {
                throw invalid(path + ".examples must be an array");
            }
            exampleNode.forEach(example -> {
                if (!example.isTextual() || example.textValue().isBlank()) {
                    throw invalid(path + ".examples must contain non-blank strings");
                }
                examples.add(example.textValue().trim());
            });
        }
        JsonNode ladder = required(node, "ladder", path);
        if (!ladder.isArray() || ladder.isEmpty()) {
            throw invalid(path + ".ladder must be a non-empty array");
        }
        List<PunishmentStep> steps = new ArrayList<>();
        for (int index = 0; index < ladder.size(); index++) {
            steps.add(parseStep(ladder.get(index), index, path + ".ladder[" + index + "]"));
        }
        boolean publicByDefault = bool(node, "public-default", defaults.publicByDefault(), path);
        requirePublicDefault(publicByDefault, path + ".public-default");
        return new ReasonPolicy(
                id,
                text(node, "family", path),
                text(node, "display-name", path),
                integer(node, "severity", path, 0, 100),
                bool(node, "decay-eligible", defaults.decayEligible(), path),
                steps,
                examples,
                publicByDefault,
                bool(node, "reportable", defaults.reportable(), path),
                bool(node, "confiscation-options", defaults.confiscationAllowed(), path),
                enumValue(StaffRank.class, optionalText(node, "required-rank", defaults.requiredRank().name()), path + ".required-rank"),
                bool(node, "automatic-detection-eligible", defaults.automaticDetectionAllowed(), path),
                enumValue(AltInheritanceMode.class,
                        optionalText(node, "alt-inheritance", defaults.altInheritance().name()), path + ".alt-inheritance")
        );
    }

    private PunishmentStep parseStep(JsonNode node, int ordinal, String path) {
        requireObject(node, path);
        rejectUnknown(node, STEP_FIELDS, path);
        JsonNode sanctions = required(node, "sanctions", path);
        if (!sanctions.isArray() || sanctions.isEmpty()) {
            throw invalid(path + ".sanctions must be a non-empty array");
        }
        List<SanctionSpec> parsed = new ArrayList<>();
        for (int index = 0; index < sanctions.size(); index++) {
            parsed.add(parseSanction(sanctions.get(index), path + ".sanctions[" + index + "]"));
        }
        return new PunishmentStep(ordinal, text(node, "label", path), parsed);
    }

    private SanctionSpec parseSanction(JsonNode node, String path) {
        requireObject(node, path);
        rejectUnknown(node, SANCTION_FIELDS, path);
        SanctionType type = enumValue(SanctionType.class, text(node, "type", path), path + ".type");
        String duration = text(node, "duration", path);
        SanctionLength length;
        if ("instant".equalsIgnoreCase(duration)) {
            length = SanctionLength.instant();
        } else {
            ParsedDuration parsed = durations.parse(duration);
            length = parsed.isPermanent()
                    ? SanctionLength.permanent()
                    : SanctionLength.temporary(parsed.temporary().orElseThrow());
        }
        return new SanctionSpec(type, length);
    }

    private static JsonNode required(JsonNode parent, String field, String path) {
        JsonNode value = parent.get(field);
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

    private static String optionalText(JsonNode parent, String field, String fallback) {
        JsonNode value = parent.get(field);
        if (value == null) {
            return fallback;
        }
        if (!value.isTextual() || value.textValue().isBlank()) {
            throw invalid(field + " must be a non-blank string");
        }
        return value.textValue().trim();
    }

    private static boolean bool(JsonNode parent, String field, String path) {
        JsonNode value = required(parent, field, path);
        if (!value.isBoolean()) {
            throw invalid(path + "." + field + " must be true or false");
        }
        return value.booleanValue();
    }

    private static boolean bool(JsonNode parent, String field, boolean fallback, String path) {
        JsonNode value = parent.get(field);
        if (value == null) {
            return fallback;
        }
        if (!value.isBoolean()) {
            throw invalid(path + "." + field + " must be true or false");
        }
        return value.booleanValue();
    }

    private static int integer(JsonNode parent, String field, String path, int minimum, int maximum) {
        JsonNode value = required(parent, field, path);
        if (!value.canConvertToInt() || value.intValue() < minimum || value.intValue() > maximum) {
            throw invalid(path + "." + field + " must be between " + minimum + " and " + maximum);
        }
        return value.intValue();
    }

    private static <T extends Enum<T>> T enumValue(Class<T> type, String raw, String path) {
        try {
            return Enum.valueOf(type, raw.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw invalid(path + " has unsupported value " + raw);
        }
    }

    private static void requireObject(JsonNode node, String path) {
        if (node == null || !node.isObject()) {
            throw invalid(path + " must be an object");
        }
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

    private static ConfigurationValidationException invalid(String message) {
        return new ConfigurationValidationException(message);
    }

    private static void requirePublicDefault(boolean publicByDefault, String path) {
        if (!publicByDefault) {
            throw invalid(path + " must be true; staff may explicitly select private during review");
        }
    }

    public record LoadedPolicies(
            String version,
            List<ReasonPolicy> policies,
            Map<String, String> aliases,
            List<RemovedReason> removedReasons
    ) {
        public LoadedPolicies {
            if (version == null || version.isBlank()) {
                throw new IllegalArgumentException("reason policy version must be present");
            }
            version = version.trim();
            policies = List.copyOf(policies);
            aliases = Map.copyOf(aliases);
            removedReasons = List.copyOf(removedReasons);
        }

        public LoadedPolicies(String version, List<ReasonPolicy> policies) {
            this(version, policies, Map.of(), List.of());
        }
    }

    private record Defaults(
            boolean decayEligible,
            boolean publicByDefault,
            boolean reportable,
            boolean confiscationAllowed,
            StaffRank requiredRank,
            boolean automaticDetectionAllowed,
            AltInheritanceMode altInheritance
    ) {
    }
}
