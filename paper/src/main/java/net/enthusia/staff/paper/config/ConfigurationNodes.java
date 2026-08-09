package net.enthusia.staff.paper.config;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

final class ConfigurationNodes {
    private ConfigurationNodes() {
    }

    static JsonNode requiredMapping(JsonNode parent, String field, String path, List<String> errors) {
        JsonNode value = parent == null ? null : parent.get(field);
        if (value == null || value.isNull() || !value.isObject()) {
            errors.add(path + " must be a mapping section");
            return null;
        }
        return value;
    }

    static JsonNode optionalMapping(JsonNode parent, String field, String path, List<String> errors) {
        if (parent == null) {
            return null;
        }
        JsonNode value = parent.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        if (!value.isObject()) {
            errors.add(path + " must be a mapping section");
            return null;
        }
        return value;
    }

    static void rejectUnknown(JsonNode node, Set<String> allowed, String path, List<String> errors) {
        if (node == null || !node.isObject()) {
            return;
        }
        Iterator<String> fields = node.fieldNames();
        while (fields.hasNext()) {
            String field = fields.next();
            if (!allowed.contains(field)) {
                errors.add(path + " contains unknown key " + field);
            }
        }
    }

    static boolean bool(
            JsonNode parent,
            String field,
            String path,
            boolean fallback,
            List<String> errors
    ) {
        JsonNode value = parent == null ? null : parent.get(field);
        if (value == null || value.isNull()) {
            return fallback;
        }
        if (!value.isBoolean()) {
            errors.add(path + " must be true or false");
            return fallback;
        }
        return value.booleanValue();
    }

    static int integer(
            JsonNode parent,
            String field,
            String path,
            int fallback,
            List<String> errors
    ) {
        JsonNode value = parent == null ? null : parent.get(field);
        if (value == null || value.isNull()) {
            return fallback;
        }
        if (!value.isIntegralNumber() || !value.canConvertToInt()) {
            errors.add(path + " must be an integer");
            return fallback;
        }
        return value.intValue();
    }

    static int boundedInteger(
            JsonNode parent,
            String field,
            String path,
            int fallback,
            int minimum,
            int maximum,
            List<String> errors
    ) {
        JsonNode value = parent == null ? null : parent.get(field);
        if (value == null || value.isNull()) {
            return fallback;
        }
        if (!value.isIntegralNumber() || !value.canConvertToInt()) {
            errors.add(path + " must be an integer");
            return fallback;
        }
        int parsed = value.intValue();
        if (parsed < minimum || parsed > maximum) {
            errors.add(path + " must be between " + minimum + " and " + maximum);
            return fallback;
        }
        return parsed;
    }

    static long boundedLong(
            JsonNode parent,
            String field,
            String path,
            long fallback,
            long minimum,
            long maximum,
            List<String> errors
    ) {
        JsonNode value = parent == null ? null : parent.get(field);
        if (value == null || value.isNull()) {
            return fallback;
        }
        if (!value.isIntegralNumber() || !value.canConvertToLong()) {
            errors.add(path + " must be an integer");
            return fallback;
        }
        long parsed = value.longValue();
        if (parsed < minimum || parsed > maximum) {
            errors.add(path + " must be between " + minimum + " and " + maximum);
            return fallback;
        }
        return parsed;
    }

    static double boundedDouble(
            JsonNode parent,
            String field,
            String path,
            double fallback,
            double minimum,
            double maximum,
            List<String> errors
    ) {
        JsonNode value = parent == null ? null : parent.get(field);
        if (value == null || value.isNull()) {
            return fallback;
        }
        if (!value.isNumber()) {
            errors.add(path + " must be a number");
            return fallback;
        }
        double parsed = value.doubleValue();
        if (!Double.isFinite(parsed) || parsed < minimum || parsed > maximum) {
            errors.add(path + " must be between " + minimum + " and " + maximum);
            return fallback;
        }
        return parsed;
    }

    static String text(
            JsonNode parent,
            String field,
            String path,
            String fallback,
            List<String> errors
    ) {
        JsonNode value = parent == null ? null : parent.get(field);
        if (value == null || value.isNull()) {
            return fallback;
        }
        if (!value.isTextual() || value.textValue().isBlank()) {
            errors.add(path + " must be a non-blank string");
            return fallback;
        }
        return value.textValue().trim();
    }
}
