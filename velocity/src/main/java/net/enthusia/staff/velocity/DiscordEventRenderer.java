package net.enthusia.staff.velocity;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import net.enthusia.staff.domain.discord.DiscordOutboxMessage;

final class DiscordEventRenderer {
    private static final int MAX_PAYLOAD_CHARACTERS = 16_384;
    private static final int MAX_FIELD_CHARACTERS = 180;
    private static final int MAX_ARRAY_VALUES = 8;
    private static final int MAX_CONTENT_CHARACTERS = 1_800;
    private static final char BACKTICK = '`';
    private static final char REPLACEMENT_CHARACTER = '\uFFFD';
    private static final String TARGET_ID = "targetId";
    private static final String STATE = "state";
    private static final Map<String, List<String>> ALLOWED_FIELDS = Map.of(
            "punishments", List.of(
                    "caseId", TARGET_ID, "reasonId", "sanctionId", "sanctionIds",
                    "requestId", "actorId", "action", "sanctionType", "status", STATE, "type",
                    "decision", "outcome"
            ),
            "reports", List.of("reportId", TARGET_ID, "reasonId", "serverId", "status", STATE, "actorId"),
            "logs-staffmode", List.of(
                    "staffId", TARGET_ID, "actorId", "sessionId", "rank", "active", "reason", "serverId", STATE
            ),
            "alerts", List.of(
                    "caseId", TARGET_ID, "sanctionId", "requestId", "reportId", "destination",
                    "errorCode", "status", STATE, "type", "serverId"
            )
    );

    private final ObjectMapper json;

    DiscordEventRenderer() {
        this(new ObjectMapper());
    }

    DiscordEventRenderer(ObjectMapper json) {
        if (json == null) {
            throw new IllegalArgumentException("Discord renderer JSON mapper is required");
        }
        this.json = json;
    }

    String render(DiscordOutboxMessage message) {
        if (message == null) {
            throw new IllegalArgumentException("Discord outbox message is required");
        }
        List<String> allowed = ALLOWED_FIELDS.get(message.destination());
        if (allowed == null) {
            throw new IllegalArgumentException("Discord destination has no rendering policy");
        }
        if (message.payloadJson().length() > MAX_PAYLOAD_CHARACTERS) {
            throw new IllegalArgumentException("Discord event payload exceeds the rendering limit");
        }
        JsonNode payload = parseObject(message.payloadJson());
        StringBuilder rendered = new StringBuilder();
        rendered.append(message.eventType());
        for (String field : allowed) {
            JsonNode value = payload.get(field);
            String safe = safeValue(value);
            if (safe != null) {
                appendField(rendered, field, safe);
            }
        }
        return truncateWithEllipsis(rendered.toString(), MAX_CONTENT_CHARACTERS);
    }

    private JsonNode parseObject(String raw) {
        final JsonNode payload;
        try {
            payload = json.readTree(raw);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Discord event payload is not valid JSON", exception);
        }
        if (payload == null || !payload.isObject()) {
            throw new IllegalArgumentException("Discord event payload must be a JSON object");
        }
        return payload;
    }

    private static String safeValue(JsonNode value) {
        if (value == null || value.isNull() || value.isContainerNode() && !value.isArray()) {
            return null;
        }
        if (value.isArray()) {
            return safeArray(value);
        }
        if (!value.isValueNode()) {
            return null;
        }
        return sanitize(value.isTextual() ? value.textValue() : value.asText());
    }

    private static String safeArray(JsonNode values) {
        StringBuilder safe = new StringBuilder();
        int count = 0;
        for (JsonNode value : values) {
            if (count >= MAX_ARRAY_VALUES || unsupportedArrayValue(value)) {
                break;
            }
            String item = sanitize(value.isTextual() ? value.textValue() : value.asText());
            if (item.isEmpty()) {
                continue;
            }
            if (!safe.isEmpty()) {
                safe.append(", ");
            }
            safe.append(item);
            count++;
        }
        return safe.isEmpty() ? null : safe.toString();
    }

    private static boolean unsupportedArrayValue(JsonNode value) {
        return !value.isValueNode() || value.isNull();
    }

    private static String sanitize(String raw) {
        StringBuilder result = new StringBuilder(raw.length());
        for (int index = 0; index < raw.length();) {
            index += appendSanitizedCodeUnit(raw, index, result);
        }
        String normalized = result.toString().trim().replaceAll("\\s+", " ");
        return truncateWithEllipsis(normalized, MAX_FIELD_CHARACTERS);
    }

    private static int appendSanitizedCodeUnit(String raw, int index, StringBuilder result) {
        char character = raw.charAt(index);
        if (Character.isISOControl(character)) {
            result.append(' ');
            return 1;
        }
        if (character == BACKTICK) {
            result.append('\'');
            return 1;
        }
        if (Character.isHighSurrogate(character)) {
            return appendHighSurrogate(raw, index, result, character);
        }
        if (Character.isLowSurrogate(character)) {
            result.append(REPLACEMENT_CHARACTER);
            return 1;
        }
        result.append(character);
        return 1;
    }

    private static int appendHighSurrogate(String raw, int index, StringBuilder result, char highSurrogate) {
        int lowIndex = index + 1;
        if (lowIndex < raw.length() && Character.isLowSurrogate(raw.charAt(lowIndex))) {
            result.append(highSurrogate).append(raw.charAt(lowIndex));
            return 2;
        }
        result.append(REPLACEMENT_CHARACTER);
        return 1;
    }

    static String truncateWithEllipsis(String value, int maximumCharacters) {
        if (value.length() <= maximumCharacters) {
            return value;
        }
        int boundary = maximumCharacters - 1;
        if (boundary > 0 && Character.isHighSurrogate(value.charAt(boundary - 1))) {
            boundary--;
        }
        return value.substring(0, boundary) + "…";
    }

    private static void appendField(StringBuilder rendered, String field, String value) {
        if (value.isBlank()) {
            return;
        }
        rendered.append('\n').append(field).append('=').append(value);
    }
}
