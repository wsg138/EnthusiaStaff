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
import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.enthusia.staff.common.DurationParser;
import net.enthusia.staff.common.ParsedDuration;
import net.enthusia.staff.domain.auth.StaffRank;
import net.enthusia.staff.domain.escalation.AltInheritanceMode;
import net.enthusia.staff.domain.escalation.PunishmentStep;
import net.enthusia.staff.domain.escalation.ReasonPolicy;
import net.enthusia.staff.domain.sanction.SanctionLength;
import net.enthusia.staff.domain.sanction.SanctionSpec;
import net.enthusia.staff.domain.sanction.SanctionType;

public final class ReasonPolicyConfigurationLoader {
    private static final Set<String> ROOT_FIELDS = Set.of("version", "defaults", "reasons");
    private static final Set<String> DEFAULT_FIELDS = Set.of(
            "decay-eligible", "public-default", "reportable", "confiscation-options",
            "required-rank", "automatic-detection-eligible", "alt-inheritance"
    );
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
            return new LoadedPolicies(version, policies);
        } catch (IOException exception) {
            throw new ConfigurationValidationException("Unable to read " + sourceName, exception);
        } catch (IllegalArgumentException exception) {
            if (exception instanceof ConfigurationValidationException validation) {
                throw validation;
            }
            throw new ConfigurationValidationException("Invalid punishment policy: " + exception.getMessage(), exception);
        }
    }

    private Defaults parseDefaults(JsonNode node) {
        String path = "root.defaults";
        requireObject(node, path);
        rejectUnknown(node, DEFAULT_FIELDS, path);
        return new Defaults(
                bool(node, "decay-eligible", path),
                bool(node, "public-default", path),
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
        return new ReasonPolicy(
                id,
                text(node, "family", path),
                text(node, "display-name", path),
                integer(node, "severity", path, 0, 100),
                bool(node, "decay-eligible", defaults.decayEligible(), path),
                steps,
                examples,
                bool(node, "public-default", defaults.publicByDefault(), path),
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

    public record LoadedPolicies(String version, List<ReasonPolicy> policies) {
        public LoadedPolicies {
            policies = List.copyOf(policies);
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
