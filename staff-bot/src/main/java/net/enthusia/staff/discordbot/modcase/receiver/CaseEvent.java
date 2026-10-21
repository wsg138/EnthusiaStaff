package net.enthusia.staff.discordbot.modcase.receiver;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.StreamSupport;

/** Strict, content-free envelope from the draft canonical API contract. */
public record CaseEvent(int contractVersion, String operationId, String eventType,
                        String caseId, long revision, String originPlatform, String scopeId,
                        String caseState, String reviewPriority, String createdAt,
                        String expiresAt, List<String> evidenceRefs) {
    private static final ObjectMapper JSON = new ObjectMapper()
            .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
    private static final Set<String> KEYS = Set.of("contract_version", "operation_id",
            "event_type", "case_id", "revision", "origin_platform", "scope_id",
            "case_state", "review_priority", "created_at", "expires_at", "evidence_refs");
    private static final Set<String> TEXT_KEYS = Set.of("operation_id", "event_type", "case_id",
            "origin_platform", "scope_id", "case_state", "review_priority", "created_at", "expires_at");
    private static final Pattern DIGEST = Pattern.compile("[0-9a-f]{64}");
    private static final Pattern REF = Pattern.compile("[A-Za-z0-9_:/.-]{1,160}");

    public CaseEvent {
        if (evidenceRefs == null) {
            throw new IllegalArgumentException("missing case references");
        }
        evidenceRefs = List.copyOf(evidenceRefs);
        if (!validIdentity(operationId, caseId, originPlatform, scopeId)
                || !validLifecycle(contractVersion, eventType, revision, caseState, reviewPriority)
                || !validEvidence(evidenceRefs)) {
            throw new IllegalArgumentException("invalid canonical case event");
        }
        validateTimes(createdAt, expiresAt);
    }

    private static boolean validIdentity(String operation, String id, String origin, String scope) {
        return matches(DIGEST, operation) && canonicalUuid(id)
                && Set.of("minecraft", "discord").contains(origin) && matches(REF, scope);
    }

    private static boolean validLifecycle(int version, String type, long revision,
                                          String state, String priority) {
        if (version != 1 || revision < 0
                || !Set.of("CASE_CREATED", "CASE_REVISION", "CASE_STATE").contains(type)
                || !Set.of("OPEN", "CLAIMED", "CLOSED").contains(state)
                || !Set.of("NONE", "NORMAL", "URGENT").contains(priority)) {
            return false;
        }
        if ("CASE_CREATED".equals(type)) {
            return revision == 0 && "OPEN".equals(state);
        }
        return revision > 0
                && (!"CASE_STATE".equals(type) || !"OPEN".equals(state))
                && (!"CASE_REVISION".equals(type) || !"CLOSED".equals(state));
    }

    private static boolean validEvidence(List<String> refs) {
        return !refs.isEmpty() && refs.size() <= 24
                && refs.stream().allMatch(ref -> matches(REF, ref))
                && new HashSet<>(refs).size() == refs.size();
    }

    private static void validateTimes(String created, String expires) {
        try {
            if (!OffsetDateTime.parse(expires).toInstant()
                    .isAfter(OffsetDateTime.parse(created).toInstant())) {
                throw new IllegalArgumentException("invalid case expiry");
            }
        } catch (java.time.format.DateTimeParseException exception) {
            throw new IllegalArgumentException("invalid case timestamp", exception);
        }
    }

    public static CaseEvent parse(String wire) {
        if (wire == null || wire.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 8192) {
            throw new IllegalArgumentException("bounded event required");
        }
        try {
            JsonNode node = JSON.readTree(wire);
            validateSchema(node);
            return decode(node);
        } catch (IOException exception) {
            throw new IllegalArgumentException("malformed canonical event", exception);
        }
    }

    private static void validateSchema(JsonNode node) {
        if (node == null || !node.isObject() || node.size() != KEYS.size()) {
            throw new IllegalArgumentException("unexpected event schema");
        }
        node.fieldNames().forEachRemaining(field -> {
            if (!KEYS.contains(field)) {
                throw new IllegalArgumentException("unknown event field");
            }
        });
        if (!node.path("contract_version").isIntegralNumber()
                || !node.path("contract_version").canConvertToInt()
                || !node.path("revision").isIntegralNumber()
                || !node.path("revision").canConvertToLong()) {
            throw new IllegalArgumentException("invalid numeric field");
        }
        if (TEXT_KEYS.stream().anyMatch(key -> !node.path(key).isTextual())) {
            throw new IllegalArgumentException("invalid text field");
        }
        JsonNode refs = node.path("evidence_refs");
        if (!refs.isArray() || refs.isEmpty() || refs.size() > 24) {
            throw new IllegalArgumentException("invalid evidence list");
        }
        for (JsonNode ref : refs) {
            if (!ref.isTextual()) {
                throw new IllegalArgumentException("invalid reference field");
            }
        }
    }

    private static CaseEvent decode(JsonNode node) {
        return new CaseEvent(node.path("contract_version").intValue(),
                node.path("operation_id").textValue(), node.path("event_type").textValue(),
                node.path("case_id").textValue(), node.path("revision").longValue(),
                node.path("origin_platform").textValue(), node.path("scope_id").textValue(),
                node.path("case_state").textValue(), node.path("review_priority").textValue(),
                node.path("created_at").textValue(), node.path("expires_at").textValue(),
                StreamSupport.stream(node.path("evidence_refs").spliterator(), false)
                        .map(JsonNode::textValue).toList());
    }

    public String identity() {
        return originPlatform + "/" + scopeId + "/" + caseId;
    }

    private static boolean matches(Pattern pattern, String value) {
        return value != null && pattern.matcher(value).matches();
    }

    private static boolean canonicalUuid(String value) {
        try {
            return value != null && UUID.fromString(value).toString().equals(value);
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }
}
