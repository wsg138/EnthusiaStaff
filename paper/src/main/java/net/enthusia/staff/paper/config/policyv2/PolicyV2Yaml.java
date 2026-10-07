package net.enthusia.staff.paper.config.policyv2;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Iterator;
import java.util.Locale;
import java.util.Set;

final class PolicyV2Yaml {
    private PolicyV2Yaml() {
    }

    static JsonNode required(JsonNode parent, String field, String path) {
        JsonNode value = parent == null ? null : parent.get(field);
        if (value == null || value.isNull()) {
            throw invalid(path + "." + field + " is required");
        }
        return value;
    }

    static String text(JsonNode parent, String field, String path) {
        JsonNode value = required(parent, field, path);
        if (!value.isTextual() || value.textValue().isBlank()) {
            throw invalid(path + "." + field + " must be a non-blank string");
        }
        return value.textValue().trim();
    }

    static boolean bool(JsonNode parent, String field, String path) {
        JsonNode value = required(parent, field, path);
        if (!value.isBoolean()) {
            throw invalid(path + "." + field + " must be true or false");
        }
        return value.booleanValue();
    }

    static long longValue(JsonNode parent, String field, String path) {
        JsonNode value = required(parent, field, path);
        if (!value.isIntegralNumber() || !value.canConvertToLong()) {
            throw invalid(path + "." + field + " must be an integer");
        }
        return value.longValue();
    }

    static double doubleValue(JsonNode parent, String field, String path) {
        JsonNode value = required(parent, field, path);
        if (!value.isNumber() || !Double.isFinite(value.doubleValue())) {
            throw invalid(path + "." + field + " must be a finite number");
        }
        return value.doubleValue();
    }

    static JsonNode object(JsonNode node, String path) {
        if (node == null || !node.isObject()) {
            throw invalid(path + " must be an object");
        }
        return node;
    }

    static JsonNode array(JsonNode node, String path, boolean nonEmpty) {
        if (node == null || !node.isArray() || (nonEmpty && node.isEmpty())) {
            throw invalid(path + " must be " + (nonEmpty ? "a non-empty array" : "an array"));
        }
        return node;
    }

    static void rejectUnknown(JsonNode node, Set<String> allowed, String path) {
        Iterator<String> names = node.fieldNames();
        while (names.hasNext()) {
            String name = names.next();
            if (!allowed.contains(name)) {
                throw invalid(path + " contains unknown field " + name);
            }
        }
    }

    static <T extends Enum<T>> T enumValue(Class<T> type, String raw, String path) {
        String normalized = raw.trim().replace('-', '_').toUpperCase(Locale.ROOT);
        try {
            return Enum.valueOf(type, normalized);
        } catch (IllegalArgumentException exception) {
            throw invalid(path + " has unsupported value " + raw);
        }
    }

    static PolicyV2ConfigurationException invalid(String message) {
        return new PolicyV2ConfigurationException(message);
    }
}
