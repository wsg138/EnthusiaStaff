package net.enthusia.staff.protocol;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.time.DateTimeException;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.enthusia.staff.domain.commandbridge.CommandBridgeRequest;
import net.enthusia.staff.domain.moderation.ModerationSubjectId;

/** Strict versioned JSON wire codec for signed command requests. */
public final class CommandBridgeWireCodec {
    private static final int VERSION = 1;
    private static final int MAX_BODY_BYTES = 4_096;
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Set<String> REQUEST_FIELDS = Set.of(
            "version",
            "requestId",
            "subjectId",
            "actorPlayerId",
            "targetServer",
            "command",
            "requestedAt"
    );

    public String encodeRequest(CommandBridgeRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("command bridge request is required");
        }
        ObjectNode body = JSON.createObjectNode();
        body.put("version", VERSION);
        body.put("requestId", request.requestId().toString());
        body.put("subjectId", request.subjectId().toString());
        body.put("actorPlayerId", request.actorPlayerId().toString());
        body.put("targetServer", request.targetServer());
        body.put("command", request.command());
        body.put("requestedAt", request.requestedAt().toString());
        try {
            return JSON.writeValueAsString(body);
        } catch (JsonProcessingException failure) {
            throw new IllegalStateException("command request JSON encoding failed", failure);
        }
    }

    public Optional<CommandBridgeRequest> decodeRequest(String body) {
        if (body == null || body.getBytes(StandardCharsets.UTF_8).length > MAX_BODY_BYTES) {
            return Optional.empty();
        }
        try {
            JsonNode root = JSON.readTree(body);
            if (!validRoot(root)) {
                return Optional.empty();
            }
            return Optional.of(new CommandBridgeRequest(
                    UUID.fromString(text(root, "requestId")),
                    new ModerationSubjectId(UUID.fromString(text(root, "subjectId"))),
                    UUID.fromString(text(root, "actorPlayerId")),
                    text(root, "targetServer"),
                    text(root, "command"),
                    Instant.parse(text(root, "requestedAt"))
            ));
        } catch (JsonProcessingException | IllegalArgumentException | DateTimeException failure) {
            return Optional.empty();
        }
    }

    private static boolean validRoot(JsonNode root) {
        if (root == null || !root.isObject() || root.size() != REQUEST_FIELDS.size()) {
            return false;
        }
        if (!root.path("version").isInt() || root.path("version").intValue() != VERSION) {
            return false;
        }
        java.util.Iterator<String> names = root.fieldNames();
        while (names.hasNext()) {
            if (!REQUEST_FIELDS.contains(names.next())) {
                return false;
            }
        }
        return REQUEST_FIELDS.stream().filter(field -> !"version".equals(field))
                .allMatch(field -> root.path(field).isTextual());
    }

    private static String text(JsonNode root, String field) {
        return root.path(field).textValue();
    }
}
