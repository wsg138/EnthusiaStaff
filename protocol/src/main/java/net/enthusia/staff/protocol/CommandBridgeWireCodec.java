package net.enthusia.staff.protocol;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.time.DateTimeException;
import java.time.Instant;
import java.util.Iterator;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.enthusia.staff.domain.commandbridge.CommandBridgeOutcome;
import net.enthusia.staff.domain.commandbridge.CommandBridgeRequest;
import net.enthusia.staff.domain.commandbridge.CommandBridgeResponse;
import net.enthusia.staff.domain.moderation.DiscordUserId;
import net.enthusia.staff.domain.moderation.ModerationSubjectId;

/** Strict versioned JSON wire codec for signed command requests and safe responses. */
public final class CommandBridgeWireCodec {
    private static final int VERSION = 1;
    private static final int MAX_REQUEST_BYTES = 4_096;
    private static final int MAX_RESPONSE_BYTES = 8_192;
    private static final String VERSION_FIELD = "version";
    private static final String MESSAGE_FIELD = "message";
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Set<String> REQUEST_FIELDS = Set.of(
            VERSION_FIELD, "requestId", "subjectId", "discordUserId", "actorPlayerId",
            "targetServer", "command", "requestedAt"
    );
    private static final Set<String> RESPONSE_FIELDS = Set.of(
            VERSION_FIELD, "outcome", MESSAGE_FIELD, "output", "truncated", "redacted"
    );

    public String encodeRequest(CommandBridgeRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("command bridge request is required");
        }
        ObjectNode body = JSON.createObjectNode();
        body.put(VERSION_FIELD, VERSION);
        body.put("requestId", request.requestId().toString());
        body.put("subjectId", request.subjectId().toString());
        body.put("discordUserId", request.discordUserId().toString());
        body.put("actorPlayerId", request.actorPlayerId().toString());
        body.put("targetServer", request.targetServer());
        body.put("command", request.command());
        body.put("requestedAt", request.requestedAt().toString());
        return encode(body, "command request");
    }

    public Optional<CommandBridgeRequest> decodeRequest(String body) {
        if (tooLarge(body, MAX_REQUEST_BYTES)) {
            return Optional.empty();
        }
        try {
            JsonNode root = JSON.readTree(body);
            if (!validRoot(root, REQUEST_FIELDS) || !requestFieldTypes(root)) {
                return Optional.empty();
            }
            return Optional.of(new CommandBridgeRequest(
                    UUID.fromString(text(root, "requestId")),
                    new ModerationSubjectId(UUID.fromString(text(root, "subjectId"))),
                    new DiscordUserId(text(root, "discordUserId")),
                    UUID.fromString(text(root, "actorPlayerId")),
                    text(root, "targetServer"),
                    text(root, "command"),
                    Instant.parse(text(root, "requestedAt"))
            ));
        } catch (JsonProcessingException | IllegalArgumentException | DateTimeException failure) {
            return Optional.empty();
        }
    }

    public String encodeResponse(CommandBridgeResponse response) {
        if (response == null) {
            throw new IllegalArgumentException("command bridge response is required");
        }
        ObjectNode body = JSON.createObjectNode();
        body.put(VERSION_FIELD, VERSION);
        body.put("outcome", response.outcome().name());
        body.put(MESSAGE_FIELD, response.message());
        body.put("output", response.output());
        body.put("truncated", response.truncated());
        body.put("redacted", response.redacted());
        return encode(body, "command response");
    }

    public Optional<CommandBridgeResponse> decodeResponse(String body) {
        if (tooLarge(body, MAX_RESPONSE_BYTES)) {
            return Optional.empty();
        }
        try {
            JsonNode root = JSON.readTree(body);
            if (!validRoot(root, RESPONSE_FIELDS) || !responseFieldTypes(root)) {
                return Optional.empty();
            }
            return Optional.of(new CommandBridgeResponse(
                    CommandBridgeOutcome.valueOf(text(root, "outcome")),
                    text(root, MESSAGE_FIELD),
                    text(root, "output"),
                    root.path("truncated").booleanValue(),
                    root.path("redacted").booleanValue()
            ));
        } catch (JsonProcessingException | IllegalArgumentException failure) {
            return Optional.empty();
        }
    }

    private static String encode(ObjectNode body, String label) {
        try {
            return JSON.writeValueAsString(body);
        } catch (JsonProcessingException failure) {
            throw new IllegalStateException(label + " JSON encoding failed", failure);
        }
    }

    private static boolean validRoot(JsonNode root, Set<String> fields) {
        if (root == null || !root.isObject() || root.size() != fields.size()) {
            return false;
        }
        if (!root.path(VERSION_FIELD).isInt() || root.path(VERSION_FIELD).intValue() != VERSION) {
            return false;
        }
        Iterator<String> names = root.fieldNames();
        while (names.hasNext()) {
            if (!fields.contains(names.next())) {
                return false;
            }
        }
        return true;
    }

    private static boolean requestFieldTypes(JsonNode root) {
        return REQUEST_FIELDS.stream().filter(field -> !VERSION_FIELD.equals(field))
                .allMatch(field -> root.path(field).isTextual());
    }

    private static boolean responseFieldTypes(JsonNode root) {
        return root.path("outcome").isTextual()
                && root.path(MESSAGE_FIELD).isTextual()
                && root.path("output").isTextual()
                && root.path("truncated").isBoolean()
                && root.path("redacted").isBoolean();
    }

    private static boolean tooLarge(String body, int maximumBytes) {
        return body == null || body.getBytes(StandardCharsets.UTF_8).length > maximumBytes;
    }

    private static String text(JsonNode root, String field) {
        return root.path(field).textValue();
    }
}
