package net.enthusia.staff.paper.config.policyv2;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.enthusia.staff.common.DurationParser;
import net.enthusia.staff.common.ParsedDuration;
import net.enthusia.staff.domain.auth.StaffRank;
import net.enthusia.staff.domain.policyv2.DecayPolicy;
import net.enthusia.staff.domain.policyv2.HistoryPolicy;
import net.enthusia.staff.domain.policyv2.HistoryWindow;
import net.enthusia.staff.domain.policyv2.IncidentAttributeDefinition;
import net.enthusia.staff.domain.policyv2.IncidentAttributeValue;
import net.enthusia.staff.domain.policyv2.OffensePolicy;
import net.enthusia.staff.domain.policyv2.PolicyAction;
import net.enthusia.staff.domain.policyv2.PolicySnapshot;
import net.enthusia.staff.domain.policyv2.PolicyV2RemedyBindingSpec;
import net.enthusia.staff.domain.policyv2.RemedySpec;
import net.enthusia.staff.domain.policyv2.enforcement.PolicyV2RemedyEnforcement.ConditionType;
import net.enthusia.staff.domain.policyv2.enforcement.PolicyV2RemedyEnforcement.Scope;
import net.enthusia.staff.domain.policyv2.ResolutionRule;
import net.enthusia.staff.domain.policyv2.RuleCondition;
import net.enthusia.staff.domain.sanction.SanctionLength;
import net.enthusia.staff.domain.sanction.SanctionSpec;
import net.enthusia.staff.domain.sanction.SanctionType;

final class PolicyV2SnapshotParser {
    private static final String OFFENSE_ID_FIELD = "offense-id";
    private static final String ATTRIBUTE_ID_FIELD = "attribute-id";
    private static final String RULE_ID_FIELD = "rule-id";
    private static final String REMEDY_ID_FIELD = "remedy-id";
    private static final String ATTRIBUTES_FIELD = "attributes";
    private static final String HISTORY_FIELD = "history";
    private static final String ACTION_TYPE_FIELD = "type";
    private static final String SANCTIONS_FIELD = "sanctions";
    private static final String ALLOWED_OPTIONS_FIELD = "allowed-options";
    private static final String MINIMUM_RANK_FIELD = "minimum-rank";
    private static final String REASON_CODE_FIELD = "reason-code";
    private static final String RESOLUTION_RULES_FIELD = "resolution-rules";
    private static final String ENFORCEMENT_FIELD = "enforcement";

    private static final Set<String> SNAPSHOT_FIELDS = Set.of("version", "offenses");
    private static final Set<String> OFFENSE_FIELDS = Set.of(
            OFFENSE_ID_FIELD, "display-name", "navigation-group-id", ATTRIBUTES_FIELD, HISTORY_FIELD, RESOLUTION_RULES_FIELD
    );
    private static final Set<String> ATTRIBUTE_FIELDS = Set.of(
            ATTRIBUTE_ID_FIELD, "kind", "required", "allowed-values", "minimum", "maximum", "maximum-length"
    );
    private static final Set<String> HISTORY_FIELDS = Set.of("relationships", "decay");
    private static final Set<String> DECAY_FIELDS = Set.of(
            "mode", "half-life", "pattern-half-life",
            "repeat-half-life-increase-per-prior", "maximum-half-life-multiplier"
    );
    private static final Set<String> RULE_FIELDS = Set.of(RULE_ID_FIELD, "when", "action", "remedies");
    private static final Set<String> CONDITION_FIELDS = Set.of(ATTRIBUTES_FIELD, HISTORY_FIELD);
    private static final Set<String> WINDOW_FIELDS = Set.of("minimum-inclusive", "maximum-exclusive");
    private static final Set<String> ACTION_FIELDS = Set.of(
            ACTION_TYPE_FIELD, SANCTIONS_FIELD, ALLOWED_OPTIONS_FIELD, MINIMUM_RANK_FIELD, REASON_CODE_FIELD
    );
    private static final Set<String> SANCTION_FIELDS = Set.of(ACTION_TYPE_FIELD, "duration");
    private static final Set<String> REMEDY_FIELDS = Set.of(
            REMEDY_ID_FIELD, ACTION_TYPE_FIELD, "description", ENFORCEMENT_FIELD
    );
    private static final Set<String> REMEDY_ENFORCEMENT_FIELDS = Set.of(
            "scope", "condition-type", "value-attribute-id", "component", "component-attribute-id"
    );

    private final DurationParser durations = new DurationParser();

    PolicySnapshot parse(JsonNode node, String path) {
        PolicyV2Yaml.object(node, path);
        PolicyV2Yaml.rejectUnknown(node, SNAPSHOT_FIELDS, path);
        String version = PolicyV2Yaml.text(node, "version", path);
        JsonNode offenses = PolicyV2Yaml.array(
                PolicyV2Yaml.required(node, "offenses", path),
                path + ".offenses",
                true
        );
        List<OffensePolicy> parsed = new ArrayList<>();
        for (int index = 0; index < offenses.size(); index++) {
            parsed.add(parseOffense(offenses.get(index), path + ".offenses[" + index + "]"));
        }
        return new PolicySnapshot(version, parsed);
    }

    private OffensePolicy parseOffense(JsonNode node, String path) {
        PolicyV2Yaml.object(node, path);
        PolicyV2Yaml.rejectUnknown(node, OFFENSE_FIELDS, path);
        List<IncidentAttributeDefinition> attributes = parseAttributes(
                PolicyV2Yaml.required(node, ATTRIBUTES_FIELD, path),
                path + "." + ATTRIBUTES_FIELD
        );
        return new OffensePolicy(
                PolicyV2Yaml.text(node, OFFENSE_ID_FIELD, path),
                PolicyV2Yaml.text(node, "display-name", path),
                PolicyV2Yaml.text(node, "navigation-group-id", path),
                attributes,
                parseHistory(PolicyV2Yaml.required(node, HISTORY_FIELD, path), path + "." + HISTORY_FIELD),
                parseRules(
                        PolicyV2Yaml.required(node, RESOLUTION_RULES_FIELD, path),
                        path + "." + RESOLUTION_RULES_FIELD,
                        attributes
                )
        );
    }

    private List<IncidentAttributeDefinition> parseAttributes(JsonNode node, String path) {
        PolicyV2Yaml.array(node, path, false);
        List<IncidentAttributeDefinition> parsed = new ArrayList<>();
        for (int index = 0; index < node.size(); index++) {
            parsed.add(parseAttribute(node.get(index), path + "[" + index + "]"));
        }
        return List.copyOf(parsed);
    }

    private IncidentAttributeDefinition parseAttribute(JsonNode node, String path) {
        PolicyV2Yaml.object(node, path);
        PolicyV2Yaml.rejectUnknown(node, ATTRIBUTE_FIELDS, path);
        IncidentAttributeDefinition.Kind kind = PolicyV2Yaml.enumValue(
                IncidentAttributeDefinition.Kind.class,
                PolicyV2Yaml.text(node, "kind", path),
                path + ".kind"
        );
        return new IncidentAttributeDefinition(
                PolicyV2Yaml.text(node, ATTRIBUTE_ID_FIELD, path),
                kind,
                PolicyV2Yaml.bool(node, "required", path),
                stringSet(node.get("allowed-values"), path + ".allowed-values"),
                optionalLong(node.get("minimum"), path + ".minimum"),
                optionalLong(node.get("maximum"), path + ".maximum"),
                optionalInteger(node.get("maximum-length"), path + ".maximum-length")
        );
    }

    private HistoryPolicy parseHistory(JsonNode node, String path) {
        PolicyV2Yaml.object(node, path);
        PolicyV2Yaml.rejectUnknown(node, HISTORY_FIELDS, path);
        JsonNode relationships = PolicyV2Yaml.object(
                PolicyV2Yaml.required(node, "relationships", path),
                path + ".relationships"
        );
        Map<String, Double> weights = new LinkedHashMap<>();
        Iterator<Map.Entry<String, JsonNode>> fields = relationships.properties().iterator();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            if (!field.getValue().isNumber() || !Double.isFinite(field.getValue().doubleValue())) {
                throw PolicyV2Yaml.invalid(path + ".relationships." + field.getKey() + " must be a finite number");
            }
            weights.put(field.getKey(), field.getValue().doubleValue());
        }
        return new HistoryPolicy(
                weights,
                parseDecay(PolicyV2Yaml.required(node, "decay", path), path + ".decay")
        );
    }

    private DecayPolicy parseDecay(JsonNode node, String path) {
        PolicyV2Yaml.object(node, path);
        PolicyV2Yaml.rejectUnknown(node, DECAY_FIELDS, path);
        DecayPolicy.Mode mode = PolicyV2Yaml.enumValue(
                DecayPolicy.Mode.class,
                PolicyV2Yaml.text(node, "mode", path),
                path + ".mode"
        );
        if (mode == DecayPolicy.Mode.NON_DECAYING) {
            rejectPresent(node, path, "half-life", "pattern-half-life",
                    "repeat-half-life-increase-per-prior", "maximum-half-life-multiplier");
            return DecayPolicy.nonDecaying();
        }
        ParsedDuration halfLife = finiteDuration(node, "half-life", path);
        ParsedDuration patternHalfLife = node.has("pattern-half-life")
                ? finiteDuration(node, "pattern-half-life", path)
                : halfLife;
        return DecayPolicy.exponential(
                halfLife.temporary().orElseThrow(),
                patternHalfLife.temporary().orElseThrow(),
                PolicyV2Yaml.doubleValue(node, "repeat-half-life-increase-per-prior", path),
                PolicyV2Yaml.doubleValue(node, "maximum-half-life-multiplier", path)
        );
    }

    private List<ResolutionRule> parseRules(
            JsonNode node,
            String path,
            List<IncidentAttributeDefinition> definitions
    ) {
        PolicyV2Yaml.array(node, path, true);
        Map<String, IncidentAttributeDefinition> attributes = new HashMap<>();
        definitions.forEach(definition -> attributes.put(definition.id(), definition));
        List<ResolutionRule> parsed = new ArrayList<>();
        for (int index = 0; index < node.size(); index++) {
            parsed.add(parseRule(node.get(index), path + "[" + index + "]", attributes));
        }
        return List.copyOf(parsed);
    }

    private ResolutionRule parseRule(
            JsonNode node,
            String path,
            Map<String, IncidentAttributeDefinition> attributes
    ) {
        PolicyV2Yaml.object(node, path);
        PolicyV2Yaml.rejectUnknown(node, RULE_FIELDS, path);
        return new ResolutionRule(
                PolicyV2Yaml.text(node, RULE_ID_FIELD, path),
                parseCondition(PolicyV2Yaml.required(node, "when", path), path + ".when", attributes),
                parseAction(PolicyV2Yaml.required(node, "action", path), path + ".action"),
                parseRemedies(PolicyV2Yaml.required(node, "remedies", path), path + ".remedies")
        );
    }

    private RuleCondition parseCondition(
            JsonNode node,
            String path,
            Map<String, IncidentAttributeDefinition> definitions
    ) {
        PolicyV2Yaml.object(node, path);
        PolicyV2Yaml.rejectUnknown(node, CONDITION_FIELDS, path);
        JsonNode values = PolicyV2Yaml.object(
                PolicyV2Yaml.required(node, ATTRIBUTES_FIELD, path),
                path + "." + ATTRIBUTES_FIELD
        );
        Map<String, Set<IncidentAttributeValue>> accepted = new LinkedHashMap<>();
        Iterator<Map.Entry<String, JsonNode>> fields = values.properties().iterator();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            IncidentAttributeDefinition definition = definitions.get(field.getKey());
            if (definition == null) {
                throw PolicyV2Yaml.invalid(path + ".attributes references undeclared attribute " + field.getKey());
            }
            accepted.put(field.getKey(), parseAcceptedValues(
                    field.getValue(),
                    path + ".attributes." + field.getKey(),
                    definition
            ));
        }
        return new RuleCondition(
                accepted,
                parseWindow(PolicyV2Yaml.required(node, HISTORY_FIELD, path), path + "." + HISTORY_FIELD)
        );
    }

    private Set<IncidentAttributeValue> parseAcceptedValues(
            JsonNode node,
            String path,
            IncidentAttributeDefinition definition
    ) {
        PolicyV2Yaml.array(node, path, true);
        Set<IncidentAttributeValue> values = new HashSet<>();
        for (int index = 0; index < node.size(); index++) {
            IncidentAttributeValue value = parseValue(node.get(index), path + "[" + index + "]", definition);
            if (!values.add(value)) {
                throw PolicyV2Yaml.invalid(path + " contains a duplicate accepted value");
            }
        }
        return Set.copyOf(values);
    }

    private static IncidentAttributeValue parseValue(
            JsonNode node,
            String path,
            IncidentAttributeDefinition definition
    ) {
        return switch (definition.kind()) {
            case BOOLEAN -> booleanValue(node, path);
            case INTEGER -> integerValue(node, path);
            case ENUM -> enumValue(node, path);
            case TEXT -> textValue(node, path);
        };
    }

    private static IncidentAttributeValue booleanValue(JsonNode node, String path) {
        if (!node.isBoolean()) {
            throw PolicyV2Yaml.invalid(path + " must be true or false");
        }
        return new IncidentAttributeValue.BooleanValue(node.booleanValue());
    }

    private static IncidentAttributeValue integerValue(JsonNode node, String path) {
        if (!node.isIntegralNumber() || !node.canConvertToLong()) {
            throw PolicyV2Yaml.invalid(path + " must be an integer");
        }
        return new IncidentAttributeValue.IntegerValue(node.longValue());
    }

    private static IncidentAttributeValue enumValue(JsonNode node, String path) {
        if (!node.isTextual()) {
            throw PolicyV2Yaml.invalid(path + " must be an enum string");
        }
        return new IncidentAttributeValue.EnumValue(node.textValue());
    }

    private static IncidentAttributeValue textValue(JsonNode node, String path) {
        if (!node.isTextual()) {
            throw PolicyV2Yaml.invalid(path + " must be text");
        }
        return new IncidentAttributeValue.TextValue(node.textValue());
    }

    private static HistoryWindow parseWindow(JsonNode node, String path) {
        PolicyV2Yaml.object(node, path);
        PolicyV2Yaml.rejectUnknown(node, WINDOW_FIELDS, path);
        double minimum = PolicyV2Yaml.doubleValue(node, "minimum-inclusive", path);
        JsonNode maximum = node.get("maximum-exclusive");
        return new HistoryWindow(
                minimum,
                maximum == null ? null : finiteNumber(maximum, path + ".maximum-exclusive")
        );
    }

    private PolicyAction parseAction(JsonNode node, String path) {
        PolicyV2Yaml.object(node, path);
        PolicyV2Yaml.rejectUnknown(node, ACTION_FIELDS, path);
        String type = PolicyV2Yaml.text(node, ACTION_TYPE_FIELD, path).toLowerCase(java.util.Locale.ROOT);
        return switch (type) {
            case "exact" -> exactAction(node, path);
            case "exact-with-approval" -> exactWithApprovalAction(node, path);
            case "bounded" -> boundedAction(node, path);
            case "remedy-only" -> remedyOnlyAction(node, path);
            case "requires-review" -> reviewAction(node, path);
            default -> throw PolicyV2Yaml.invalid(path + ".type has unsupported value " + type);
        };
    }

    private PolicyAction exactAction(JsonNode node, String path) {
        requireAbsent(node, path, ALLOWED_OPTIONS_FIELD, MINIMUM_RANK_FIELD, REASON_CODE_FIELD);
        return new PolicyAction.Exact(parseSanctions(
                PolicyV2Yaml.required(node, SANCTIONS_FIELD, path),
                path + ".sanctions"
        ));
    }

    private PolicyAction exactWithApprovalAction(JsonNode node, String path) {
        requireAbsent(node, path, ALLOWED_OPTIONS_FIELD, REASON_CODE_FIELD);
        return new PolicyAction.ExactWithApproval(
                parseSanctions(
                        PolicyV2Yaml.required(node, SANCTIONS_FIELD, path),
                        path + ".sanctions"
                ),
                PolicyV2Yaml.enumValue(
                        StaffRank.class,
                        PolicyV2Yaml.text(node, MINIMUM_RANK_FIELD, path),
                        path + ".minimum-rank"
                )
        );
    }

    private PolicyAction boundedAction(JsonNode node, String path) {
        requireAbsent(node, path, SANCTIONS_FIELD, REASON_CODE_FIELD);
        JsonNode options = PolicyV2Yaml.array(
                PolicyV2Yaml.required(node, ALLOWED_OPTIONS_FIELD, path),
                path + ".allowed-options",
                true
        );
        List<List<SanctionSpec>> parsed = new ArrayList<>();
        for (int index = 0; index < options.size(); index++) {
            parsed.add(parseSanctions(options.get(index), path + ".allowed-options[" + index + "]"));
        }
        return new PolicyAction.Bounded(
                parsed,
                PolicyV2Yaml.enumValue(
                        StaffRank.class,
                        PolicyV2Yaml.text(node, MINIMUM_RANK_FIELD, path),
                        path + ".minimum-rank"
                )
        );
    }

    private static PolicyAction remedyOnlyAction(JsonNode node, String path) {
        requireAbsent(node, path, SANCTIONS_FIELD, ALLOWED_OPTIONS_FIELD, MINIMUM_RANK_FIELD, REASON_CODE_FIELD);
        return new PolicyAction.RemedyOnly();
    }

    private static PolicyAction reviewAction(JsonNode node, String path) {
        requireAbsent(node, path, SANCTIONS_FIELD, ALLOWED_OPTIONS_FIELD, MINIMUM_RANK_FIELD);
        return new PolicyAction.RequiresReview(PolicyV2Yaml.text(node, REASON_CODE_FIELD, path));
    }

    private List<SanctionSpec> parseSanctions(JsonNode node, String path) {
        PolicyV2Yaml.array(node, path, true);
        List<SanctionSpec> parsed = new ArrayList<>();
        for (int index = 0; index < node.size(); index++) {
            parsed.add(parseSanction(node.get(index), path + "[" + index + "]"));
        }
        return List.copyOf(parsed);
    }

    private SanctionSpec parseSanction(JsonNode node, String path) {
        PolicyV2Yaml.object(node, path);
        PolicyV2Yaml.rejectUnknown(node, SANCTION_FIELDS, path);
        SanctionType type = PolicyV2Yaml.enumValue(
                SanctionType.class,
                PolicyV2Yaml.text(node, ACTION_TYPE_FIELD, path),
                path + "." + ACTION_TYPE_FIELD
        );
        String rawDuration = PolicyV2Yaml.text(node, "duration", path);
        return new SanctionSpec(type, sanctionLength(rawDuration, path + ".duration"));
    }

    private SanctionLength sanctionLength(String raw, String path) {
        if ("instant".equalsIgnoreCase(raw)) {
            return SanctionLength.instant();
        }
        ParsedDuration parsed;
        try {
            parsed = durations.parse(raw);
        } catch (IllegalArgumentException exception) {
            throw new PolicyV2ConfigurationException(path + " is invalid: " + exception.getMessage(), exception);
        }
        return parsed.isPermanent()
                ? SanctionLength.permanent()
                : SanctionLength.temporary(parsed.temporary().orElseThrow());
    }

    private static List<RemedySpec> parseRemedies(JsonNode node, String path) {
        PolicyV2Yaml.array(node, path, false);
        List<RemedySpec> parsed = new ArrayList<>();
        for (int index = 0; index < node.size(); index++) {
            String itemPath = path + "[" + index + "]";
            JsonNode item = PolicyV2Yaml.object(node.get(index), itemPath);
            PolicyV2Yaml.rejectUnknown(item, REMEDY_FIELDS, itemPath);
            parsed.add(new RemedySpec(
                    PolicyV2Yaml.text(item, REMEDY_ID_FIELD, itemPath),
                    PolicyV2Yaml.enumValue(
                            RemedySpec.Type.class,
                            PolicyV2Yaml.text(item, "type", itemPath),
                            itemPath + ".type"
                    ),
                    PolicyV2Yaml.text(item, "description", itemPath),
                    parseRemedyBinding(item.get(ENFORCEMENT_FIELD), itemPath + "." + ENFORCEMENT_FIELD)
            ));
        }
        return List.copyOf(parsed);
    }

    private static Optional<PolicyV2RemedyBindingSpec> parseRemedyBinding(JsonNode node, String path) {
        if (node == null || node.isNull()) {
            return Optional.empty();
        }
        PolicyV2Yaml.object(node, path);
        PolicyV2Yaml.rejectUnknown(node, REMEDY_ENFORCEMENT_FIELDS, path);
        return Optional.of(new PolicyV2RemedyBindingSpec(
                PolicyV2Yaml.enumValue(
                        Scope.class,
                        PolicyV2Yaml.text(node, "scope", path),
                        path + ".scope"
                ),
                PolicyV2Yaml.enumValue(
                        ConditionType.class,
                        PolicyV2Yaml.text(node, "condition-type", path),
                        path + ".condition-type"
                ),
                optionalText(node, "value-attribute-id", path),
                optionalText(node, "component", path),
                optionalText(node, "component-attribute-id", path)
        ));
    }

    private static Optional<String> optionalText(JsonNode node, String field, String path) {
        if (!node.has(field)) {
            return Optional.empty();
        }
        return Optional.of(PolicyV2Yaml.text(node, field, path));
    }

    private static Set<String> stringSet(JsonNode node, String path) {
        if (node == null) {
            return Set.of();
        }
        PolicyV2Yaml.array(node, path, true);
        Set<String> values = new HashSet<>();
        for (int index = 0; index < node.size(); index++) {
            JsonNode value = node.get(index);
            if (!value.isTextual() || value.textValue().isBlank() || !values.add(value.textValue().trim())) {
                throw PolicyV2Yaml.invalid(path + " must contain unique non-blank strings");
            }
        }
        return Set.copyOf(values);
    }

    private ParsedDuration finiteDuration(JsonNode node, String field, String path) {
        ParsedDuration parsed = durations.parse(PolicyV2Yaml.text(node, field, path));
        if (parsed.isPermanent()) {
            throw PolicyV2Yaml.invalid(path + "." + field + " must be a finite duration");
        }
        return parsed;
    }

    private static Long optionalLong(JsonNode node, String path) {
        if (node == null) {
            return null;
        }
        if (!node.isIntegralNumber() || !node.canConvertToLong()) {
            throw PolicyV2Yaml.invalid(path + " must be an integer");
        }
        return node.longValue();
    }

    private static Integer optionalInteger(JsonNode node, String path) {
        if (node == null) {
            return null;
        }
        if (!node.isIntegralNumber() || !node.canConvertToInt()) {
            throw PolicyV2Yaml.invalid(path + " must be an integer");
        }
        return node.intValue();
    }

    private static double finiteNumber(JsonNode node, String path) {
        if (!node.isNumber() || !Double.isFinite(node.doubleValue())) {
            throw PolicyV2Yaml.invalid(path + " must be a finite number");
        }
        return node.doubleValue();
    }

    private static void requireAbsent(JsonNode node, String path, String... fields) {
        for (String field : fields) {
            if (node.has(field)) {
                throw PolicyV2Yaml.invalid(path + "." + field + " is not allowed for this action type");
            }
        }
    }

    private static void rejectPresent(JsonNode node, String path, String... fields) {
        for (String field : fields) {
            if (node.has(field)) {
                throw PolicyV2Yaml.invalid(path + "." + field + " is not allowed for non-decaying history");
            }
        }
    }
}
