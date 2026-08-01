package net.enthusia.staff.velocity;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.Headers;
import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

final class WebsiteApiRequestDecoder {
    private static final String INVALID_FIELD_PREFIX = "INVALID_";
    private static final String INVALID_JSON = "INVALID_JSON";
    private static final String USERNAME_PATTERN = "[A-Za-z0-9_]{3,16}";
    private static final int MAXIMUM_QUERY_PAIRS = 16;

    private final ObjectMapper json = new ObjectMapper();

    ObjectNode jsonBody(Headers headers, byte[] body, Set<String> allowedFields) {
        String contentType = headers.getFirst("content-type");
        if (contentType == null
                || !contentType.toLowerCase(Locale.ROOT).matches(
                        "application/json(?:\\s*;\\s*charset=utf-8)?")) {
            throw new WebsiteApiException(
                    415,
                    "JSON_REQUIRED",
                    "The request must use application/json with UTF-8"
            );
        }
        if (body.length == 0) {
            throw badRequest(INVALID_JSON, "A JSON request body is required");
        }
        JsonNode parsed = parseJson(body);
        if (!(parsed instanceof ObjectNode object)) {
            throw badRequest(INVALID_JSON, "The JSON request body must be an object");
        }
        object.fieldNames().forEachRemaining(field -> {
            if (!allowedFields.contains(field)) {
                throw badRequest("UNKNOWN_FIELD", "The JSON request contains an unsupported field");
            }
        });
        return object;
    }

    Map<String, String> query(String rawQuery) {
        if (rawQuery == null || rawQuery.isEmpty()) {
            return Map.of();
        }
        String[] pairs = rawQuery.split("&", -1);
        if (pairs.length > MAXIMUM_QUERY_PAIRS) {
            throw badRequest("INVALID_QUERY", "The request query is invalid");
        }
        Map<String, String> values = new LinkedHashMap<>();
        for (String pair : pairs) {
            QueryParameter parameter = queryParameter(pair);
            if (values.putIfAbsent(parameter.name(), parameter.value()) != null) {
                throw badRequest("INVALID_QUERY", "The request query is invalid");
            }
        }
        return Map.copyOf(values);
    }

    void requireQueryKeys(Map<String, String> query, Set<String> allowed) {
        if (!allowed.containsAll(query.keySet())) {
            throw badRequest(
                    "UNKNOWN_QUERY_PARAMETER",
                    "The request query contains an unsupported parameter"
            );
        }
    }

    int positiveInteger(String raw, int fallback, int minimum, int maximum) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            int value = Integer.parseInt(raw);
            if (value < minimum || value > maximum) {
                throw new NumberFormatException("Value outside range");
            }
            return value;
        } catch (NumberFormatException exception) {
            throw badRequest("INVALID_NUMBER", "A numeric query value is invalid");
        }
    }

    void requireEmptyBody(byte[] body) {
        if (body.length != 0) {
            throw badRequest("UNEXPECTED_BODY", "This request does not accept a body");
        }
    }

    String text(ObjectNode input, String field, int maximumLength) {
        JsonNode value = input.get(field);
        if (value == null || !value.isTextual() || value.textValue().isBlank()
                || value.textValue().length() > maximumLength) {
            throw badRequest(
                    INVALID_FIELD_PREFIX + safeOutcomeCode(field),
                    "A required request field is invalid"
            );
        }
        return value.textValue();
    }

    String minecraftUsername(ObjectNode input, String field) {
        String value = text(input, field, 16);
        if (!value.matches(USERNAME_PATTERN)) {
            throw badRequest("INVALID_USERNAME", "The Minecraft username is invalid");
        }
        return value;
    }

    UUID uuid(ObjectNode input, String field) {
        String value = text(input, field, 36);
        try {
            UUID parsed = UUID.fromString(value);
            if (!parsed.toString().equalsIgnoreCase(value)) {
                throw new IllegalArgumentException("Non-canonical UUID");
            }
            return parsed;
        } catch (IllegalArgumentException exception) {
            throw badRequest(
                    INVALID_FIELD_PREFIX + safeOutcomeCode(field),
                    "A request identifier is invalid"
            );
        }
    }

    String uuidText(ObjectNode input, String field) {
        return uuid(input, field).toString();
    }

    int integer(ObjectNode input, String field, int minimum, int maximum) {
        JsonNode value = input.get(field);
        if (value == null || !value.canConvertToInt()) {
            throw invalidNumericField(field);
        }
        int converted = value.intValue();
        if (converted < minimum || converted > maximum) {
            throw invalidNumericField(field);
        }
        return converted;
    }

    String singleHeader(Headers headers, String name, int maximumLength) {
        List<String> values = headers.get(name);
        if (values == null || values.size() != 1) {
            throw badRequest("IDEMPOTENCY_KEY_REQUIRED", "A single idempotency key is required");
        }
        String value = values.getFirst();
        if (value == null || value.isBlank() || value.length() > maximumLength
                || !value.chars().allMatch(character -> character >= 0x21 && character <= 0x7e)) {
            throw badRequest("INVALID_IDEMPOTENCY_KEY", "The idempotency key is invalid");
        }
        return value;
    }

    String safeOutcomeCode(String value) {
        StringBuilder safe = new StringBuilder();
        for (int index = 0; index < value.length() && safe.length() < 64; index++) {
            char character = Character.toUpperCase(value.charAt(index));
            safe.append(Character.isLetterOrDigit(character) ? character : '_');
        }
        String result = safe.toString();
        return result.length() >= 3 ? result : "INVALID_VALUE";
    }

    private JsonNode parseJson(byte[] body) {
        try {
            return json.readTree(body);
        } catch (JsonProcessingException exception) {
            throw badRequest(INVALID_JSON, "The JSON request body is invalid");
        } catch (IOException exception) {
            throw badRequest(INVALID_JSON, "The JSON request body could not be read");
        }
    }

    private WebsiteApiException invalidNumericField(String field) {
        return badRequest(
                INVALID_FIELD_PREFIX + safeOutcomeCode(field),
                "A numeric request field is invalid"
        );
    }

    private static QueryParameter queryParameter(String pair) {
        int separator = pair.indexOf('=');
        String name = urlDecode(separator < 0 ? pair : pair.substring(0, separator));
        String value = urlDecode(separator < 0 ? "" : pair.substring(separator + 1));
        if (name.isBlank() || name.length() > 64 || value.length() > 256) {
            throw badRequest("INVALID_QUERY", "The request query is invalid");
        }
        return new QueryParameter(name, value);
    }

    private static String urlDecode(String value) {
        try {
            return URLDecoder.decode(value, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException exception) {
            throw badRequest("INVALID_ENCODING", "The request encoding is invalid");
        }
    }

    private static WebsiteApiException badRequest(String code, String message) {
        return new WebsiteApiException(400, code, message);
    }

    private record QueryParameter(String name, String value) {
    }
}
