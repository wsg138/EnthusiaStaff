package net.enthusia.staff.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;
import net.enthusia.staff.common.CaseId;
import net.enthusia.staff.common.IdempotencyKey;
import net.enthusia.staff.domain.market.MarketComplianceKind;
import net.enthusia.staff.domain.market.MarketComplianceOperation;
import net.enthusia.staff.domain.market.MarketComplianceRequest;
import net.enthusia.staff.domain.market.MarketComplianceState;
import net.enthusia.staff.domain.market.MarketComplianceUpdate;

/** Encodes the versioned journal payload and reconstructs compliance rows. */
final class MarketCompliancePayloadCodec {
    private static final int PAYLOAD_VERSION = 1;

    private final ObjectMapper json;

    MarketCompliancePayloadCodec(ObjectMapper json) {
        this.json = Objects.requireNonNull(json, "json");
    }

    MarketComplianceOperation read(ResultSet result) throws SQLException {
        JsonNode payload = parsePayload(result.getString("snapshot_json"));
        MarketComplianceRequest request = new MarketComplianceRequest(
                UuidBytes.fromBytes(result.getBytes("compliance_id")),
                new IdempotencyKey(result.getString("idempotency_key")),
                new CaseId(result.getString("case_id")),
                UuidBytes.fromBytes(result.getBytes("target_id")),
                MarketComplianceKind.valueOf(requiredText(payload, "kind")),
                Optional.ofNullable(result.getString("stall_id")),
                UUID.fromString(requiredText(payload, "requestedBy")),
                optionalInstant(payload, "blacklistExpiresAt"),
                optionalLong(payload, "expectedBlacklistRevision"),
                requiredTimestamp(result, "review_due_at"),
                requiredTimestamp(result, "recovery_until"),
                requiredTimestamp(result, "created_at")
        );
        return new MarketComplianceOperation(
                request,
                MarketComplianceState.valueOf(result.getString("state")),
                optionalUuid(payload, "reviewedBy"),
                optionalText(payload, "snapshotChecksum"),
                optionalText(payload, "currentChecksum"),
                payload.path("providerRevision").asLong(0L),
                result.getLong("revision"),
                requiredText(payload, "detail"),
                requiredTimestamp(result, "updated_at"),
                optionalTimestamp(result.getTimestamp("review_alerted_at"))
        );
    }

    String operationPayload(MarketComplianceRequest request, MarketComplianceUpdate update)
            throws SQLException {
        ObjectNode node = json.createObjectNode();
        node.put("version", PAYLOAD_VERSION);
        node.put("kind", request.kind().name());
        node.put("requestedBy", request.requestedBy().toString());
        putInstant(node, "blacklistExpiresAt", request.blacklistExpiresAt());
        if (request.expectedBlacklistRevision().isPresent()) {
            node.put("expectedBlacklistRevision", request.expectedBlacklistRevision().orElseThrow());
        } else {
            node.putNull("expectedBlacklistRevision");
        }
        putUuid(node, "reviewedBy", update.reviewedBy());
        putText(node, "snapshotChecksum", update.snapshotChecksum());
        putText(node, "currentChecksum", update.currentChecksum());
        node.put("providerRevision", update.providerRevision());
        node.put("detail", update.detail());
        return encode(node, "Market compliance payload could not be encoded");
    }

    String alertPayload(MarketComplianceOperation operation) throws SQLException {
        ObjectNode node = json.createObjectNode();
        node.put("operationId", operation.operationId().toString());
        node.put("caseId", operation.request().caseId().value());
        node.put("targetId", operation.request().targetId().toString());
        node.put("stallId", operation.request().stallId().orElse(""));
        node.put("reviewDueAt", operation.request().reviewDueAt().toString());
        return encode(node, "Market review alert payload could not be encoded");
    }

    private String encode(ObjectNode node, String error) throws SQLException {
        try {
            return json.writeValueAsString(node);
        } catch (JsonProcessingException exception) {
            throw new SQLException(error, exception);
        }
    }

    private JsonNode parsePayload(String encoded) throws SQLException {
        try {
            JsonNode payload = json.readTree(encoded);
            if (payload == null || payload.path("version").asInt(-1) != PAYLOAD_VERSION) {
                throw new SQLException("Market compliance payload version is unsupported");
            }
            return payload;
        } catch (JsonProcessingException exception) {
            throw new SQLException("Market compliance payload could not be decoded", exception);
        }
    }

    private static String requiredText(JsonNode payload, String field) throws SQLException {
        String value = payload.path(field).asText("");
        if (value.isBlank()) {
            throw new SQLException("Market compliance payload is missing " + field);
        }
        return value;
    }

    private static Optional<String> optionalText(JsonNode payload, String field) {
        JsonNode value = payload.get(field);
        return value == null || value.isNull() ? Optional.empty() : Optional.of(value.asText());
    }

    private static Optional<UUID> optionalUuid(JsonNode payload, String field) {
        return optionalText(payload, field).map(UUID::fromString);
    }

    private static Optional<Instant> optionalInstant(JsonNode payload, String field) {
        return optionalText(payload, field).map(Instant::parse);
    }

    private static OptionalLong optionalLong(JsonNode payload, String field) {
        JsonNode value = payload.get(field);
        return value == null || value.isNull() ? OptionalLong.empty() : OptionalLong.of(value.asLong());
    }

    private static Instant requiredTimestamp(ResultSet result, String field) throws SQLException {
        Timestamp value = result.getTimestamp(field);
        if (value == null) {
            throw new SQLException("Market compliance row is missing " + field);
        }
        return value.toInstant();
    }

    private static Optional<Instant> optionalTimestamp(Timestamp value) {
        return value == null ? Optional.empty() : Optional.of(value.toInstant());
    }

    private static void putInstant(ObjectNode node, String field, Optional<Instant> value) {
        if (value.isPresent()) {
            node.put(field, value.orElseThrow().toString());
        } else {
            node.putNull(field);
        }
    }

    private static void putUuid(ObjectNode node, String field, Optional<UUID> value) {
        if (value.isPresent()) {
            node.put(field, value.orElseThrow().toString());
        } else {
            node.putNull(field);
        }
    }

    private static void putText(ObjectNode node, String field, Optional<String> value) {
        if (value.isPresent()) {
            node.put(field, value.orElseThrow());
        } else {
            node.putNull(field);
        }
    }
}
