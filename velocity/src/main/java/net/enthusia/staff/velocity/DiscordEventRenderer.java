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
    private static final Map<String, List<String>> ALLOWED_FIELDS = Map.of(
            "punishments", List.of(
                    "caseId", "targetId", "reasonId", "sanctionId", "sanctionIds",
                    "requestId", "actorId", "action", "sanctionType", "status", "state", "type",
                    "decision", "outcome"
            ),
            "reports", List.of("reportId", "targetId", "reasonId", "serverId", "status", "state", "actorId"),
            "logs-staffmode", List.of(
                    "staffId", "targetId", "actorId", "sessionId", "rank", "active", "reason", "serverId", "state"
            ),
            "alerts", List.of(
                    "caseId", "targetId", "sanctionId", "requestId", "reportId", "destination",
                    "errorCode", "status", "state", "type", "serverId"
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
        if (rendered.length() > MAX_CONTENT_CHARACTERS) {
            return rendered.substring(0, MAX_CONTENT_CHARACTERS - 1) + "…";
        }
        return rendered.toString();
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
            if (count >= MAX_ARRAY_VALUES || !value.isValueNode() || value.isNull()) {
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

    private static String sanitize(String raw) {
        StringBuilder result = new StringBuilder(Math.min(raw.length(), MAX_FIELD_CHARACTERS));
        for (int index = 0; index < raw.length() && result.length() < MAX_FIELD_CHARACTERS; index++) {
            char character = raw.charAt(index);
            if (Character.isISOControl(character)) {
                result.append(' ');
            } else if (character == '`') {
                result.append('\'');
            } else {
                result.append(character);
            }
        }
        String normalized = result.toString().trim().replaceAll("\\s+", " ");
        if (raw.length() > MAX_FIELD_CHARACTERS && normalized.length() >= MAX_FIELD_CHARACTERS) {
            return normalized.substring(0, MAX_FIELD_CHARACTERS - 1) + "…";
        }
        return normalized;
    }

    private static void appendField(StringBuilder rendered, String field, String value) {
        if (value.isBlank()) {
            return;
        }
        rendered.append('\n').append(field).append('=').append(value);
    }
}
