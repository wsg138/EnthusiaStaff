package net.enthusia.staff.paper.aireview;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.enthusia.staff.paper.aireview.AiReviewClientException.Category;
import net.enthusia.staff.paper.aireview.AiReviewModels.Advisory;
import net.enthusia.staff.paper.aireview.AiReviewModels.Containment;
import net.enthusia.staff.paper.aireview.AiReviewModels.ContextEvidence;
import net.enthusia.staff.paper.aireview.AiReviewModels.Correction;
import net.enthusia.staff.paper.aireview.AiReviewModels.CorrectionAuthority;
import net.enthusia.staff.paper.aireview.AiReviewModels.CorrectionDecision;
import net.enthusia.staff.paper.aireview.AiReviewModels.CorrectionStatus;
import net.enthusia.staff.paper.aireview.AiReviewModels.Decision;
import net.enthusia.staff.paper.aireview.AiReviewModels.DecisionHistoryItem;
import net.enthusia.staff.paper.aireview.AiReviewModels.DecisionHistoryPage;
import net.enthusia.staff.paper.aireview.AiReviewModels.EventDetails;
import net.enthusia.staff.paper.aireview.AiReviewModels.MessageAction;
import net.enthusia.staff.paper.aireview.AiReviewModels.MessageReference;
import net.enthusia.staff.paper.aireview.AiReviewModels.ReviewItem;
import net.enthusia.staff.paper.aireview.AiReviewModels.ReviewPriority;
import net.enthusia.staff.paper.aireview.AiReviewModels.StrikeRecommendation;
import net.enthusia.staff.paper.aireview.AiReviewModels.SupportFlow;

final class AiReviewHttpClient implements AiReviewClient {
    private final AiReviewConfiguration configuration;
    private final ObjectMapper json;
    private final HttpClient http;

    AiReviewHttpClient(AiReviewConfiguration configuration, ObjectMapper json) {
        this.configuration = Objects.requireNonNull(configuration, "configuration");
        this.json = Objects.requireNonNull(json, "json");
        this.http = HttpClient.newBuilder()
                .connectTimeout(configuration.connectTimeout())
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    @Override
    public List<ReviewItem> listReviews(int limit) {
        int bounded = Math.max(1, Math.min(limit, configuration.reviewLimit()));
        JsonNode root = request(
                "GET",
                "/v1/review-items?limit=" + bounded,
                null,
                200
        );
        JsonNode items = requiredArray(root, "items");
        List<ReviewItem> parsed = new ArrayList<>();
        for (JsonNode item : items) {
            parsed.add(parseReviewItem(item));
        }
        if (parsed.size() > bounded) {
            throw new AiReviewClientException(Category.MALFORMED);
        }
        return List.copyOf(parsed);
    }

    @Override
    public DecisionHistoryPage listDecisions(int limit, String cursor) {
        return listDecisions(limit, cursor, AiReviewHistoryFilter.ALL);
    }

    @Override
    public DecisionHistoryPage listDecisions(
            int limit, String cursor, AiReviewHistoryFilter filter
    ) {
        int bounded = historyLimit(limit, cursor, filter);
        JsonNode root = historyResponse(historyPath(bounded, cursor, filter), cursor);
        return parseHistoryPage(root, bounded);
    }

    private int historyLimit(int limit, String cursor, AiReviewHistoryFilter filter) {
        if (filter == null || (cursor != null && (cursor.isBlank() || cursor.length() > 64))) {
            throw new AiReviewClientException(Category.MALFORMED);
        }
        return Math.max(1, Math.min(limit, configuration.reviewLimit()));
    }

    private String historyPath(int bounded, String cursor, AiReviewHistoryFilter filter) {
        StringBuilder path = new StringBuilder("/v1/decisions?limit=").append(bounded);
        if (cursor != null) {
            path.append("&cursor=").append(encode(cursor));
        }
        if (filter != AiReviewHistoryFilter.ALL) {
            path.append("&filter=").append(encode(filter.apiValue()));
        }
        return path.toString();
    }

    private JsonNode historyResponse(String path, String cursor) {
        try {
            return request("GET", path, null, 200);
        } catch (AiReviewClientException exception) {
            if (exception.category() == Category.NOT_FOUND && cursor != null) {
                throw new AiReviewClientException(Category.INVALID_CURSOR, exception);
            }
            throw exception;
        }
    }

    private DecisionHistoryPage parseHistoryPage(JsonNode root, int bounded) {
        JsonNode items = requiredArray(root, "items");
        if (items.size() > bounded) {
            throw new AiReviewClientException(Category.MALFORMED);
        }
        List<DecisionHistoryItem> parsed = new ArrayList<>();
        for (JsonNode item : items) {
            parsed.add(parseDecisionHistoryItem(item));
        }
        String next = optionalText(root, "next_cursor");
        if (next != null && (next.isBlank() || next.length() > 64)) {
            throw new AiReviewClientException(Category.MALFORMED);
        }
        return new DecisionHistoryPage(parsed, next);
    }

    @Override
    public EventDetails event(String eventId) {
        return parseEvent(request(
                "GET",
                "/v1/events/" + encode(required(eventId)),
                null,
                200
        ));
    }

    @Override
    public Correction correct(
            String eventId,
            String reviewerId,
            CorrectionAuthority authority,
            CorrectionDecision corrected,
            String note
    ) {
        ObjectNode body = json.createObjectNode();
        body.put("event_id", required(eventId));
        body.put("reviewer_id", required(reviewerId));
        body.put("authority", Objects.requireNonNull(authority, "authority").name());
        body.set("corrected", correctionDecisionNode(corrected));
        putOptionalText(body, "note", note);
        return parseCorrection(request("POST", "/v1/review-corrections", body, 201));
    }

    @Override
    public Correction reject(
            String proposalId,
            String reviewerId,
            CorrectionAuthority authority,
            String note
    ) {
        ObjectNode body = json.createObjectNode();
        body.put("reviewer_id", required(reviewerId));
        body.put("authority", Objects.requireNonNull(authority, "authority").name());
        putOptionalText(body, "note", note);
        return parseCorrection(request(
                "POST",
                "/v1/review-corrections/" + encode(required(proposalId)) + "/reject",
                body,
                200
        ));
    }

    private JsonNode request(String method, String path, JsonNode body, int expectedStatus) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(resolve(path))
                .timeout(configuration.requestTimeout())
                .header("Accept", "application/json")
                .header("X-Client-Id", configuration.clientId())
                .header("Authorization", "Bearer " + configuration.bearerToken());
        if (body == null) {
            builder.method(method, HttpRequest.BodyPublishers.noBody());
        } else {
            byte[] encoded;
            try {
                encoded = json.writeValueAsBytes(body);
            } catch (JsonProcessingException exception) {
                throw new AiReviewClientException(Category.MALFORMED, exception);
            }
            builder.header("Content-Type", "application/json")
                    .method(method, HttpRequest.BodyPublishers.ofByteArray(encoded));
        }
        try {
            HttpResponse<InputStream> response =
                    http.send(builder.build(), HttpResponse.BodyHandlers.ofInputStream());
            try (InputStream stream = response.body()) {
                byte[] bytes = stream.readNBytes(configuration.responseMaxBytes() + 1);
                if (bytes.length > configuration.responseMaxBytes()) {
                    throw new AiReviewClientException(Category.OVERSIZED);
                }
                if (response.statusCode() != expectedStatus) {
                    throw statusException(response.statusCode());
                }
                JsonNode parsed = json.readTree(bytes);
                if (parsed == null || !parsed.isObject()) {
                    throw new AiReviewClientException(Category.MALFORMED);
                }
                return parsed;
            }
        } catch (AiReviewClientException exception) {
            throw exception;
        } catch (HttpTimeoutException exception) {
            throw new AiReviewClientException(Category.TIMEOUT, exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AiReviewClientException(Category.NETWORK, exception);
        } catch (IOException exception) {
            throw new AiReviewClientException(Category.NETWORK, exception);
        } catch (RuntimeException exception) {
            throw new AiReviewClientException(Category.MALFORMED, exception);
        }
    }

    private AiReviewClientException statusException(int status) {
        if (status == 409) {
            return new AiReviewClientException(Category.CONFLICT);
        }
        if (status == 404) {
            return new AiReviewClientException(Category.NOT_FOUND);
        }
        if (status == 401 || status == 403) {
            return new AiReviewClientException(Category.AUTH);
        }
        if (status == 408 || status == 425 || status == 429 || status >= 500) {
            return new AiReviewClientException(Category.UNAVAILABLE);
        }
        return new AiReviewClientException(Category.HTTP);
    }

    private URI resolve(String path) {
        return configuration.baseUri().resolve(path);
    }

    private DecisionHistoryItem parseDecisionHistoryItem(JsonNode node) {
        Boolean degraded = optionalBoolean(node, "degraded");
        Boolean corrected = optionalBoolean(node, "corrected");
        if (degraded == null || corrected == null) {
            throw new AiReviewClientException(Category.MALFORMED);
        }
        return new DecisionHistoryItem(
                requiredText(node, "event_id"),
                instant(node, "occurred_at"),
                instant(node, "finalized_at"),
                requiredText(node, "platform"),
                requiredText(node, "channel_profile"),
                requiredText(node, "ingestion_status"),
                enumValue(MessageAction.class, node, "message_action"),
                requiredText(node, "semantic_label"),
                enumValue(ReviewPriority.class, node, "review_priority"),
                textArray(node, "reason_codes", 32),
                requiredText(node, "local_model_version"),
                requiredText(node, "policy_version"),
                degraded,
                corrected
        );
    }

    private ReviewItem parseReviewItem(JsonNode node) {
        return new ReviewItem(
                requiredText(node, "event_id"),
                instant(node, "occurred_at"),
                requiredText(node, "platform"),
                requiredText(node, "channel_profile"),
                requiredText(node, "semantic_label"),
                enumValue(MessageAction.class, node, "message_action"),
                enumValue(ReviewPriority.class, node, "review_priority"),
                textArray(node, "reason_codes", 32),
                optionalText(node, "incident_id")
        );
    }

    private EventDetails parseEvent(JsonNode node) {
        JsonNode correctionsNode = requiredArray(node, "corrections");
        List<Correction> corrections = new ArrayList<>();
        for (JsonNode correction : correctionsNode) {
            corrections.add(parseCorrection(correction));
        }
        JsonNode contextNode = requiredArray(node, "context_evidence");
        List<ContextEvidence> context = new ArrayList<>();
        for (JsonNode evidence : contextNode) {
            context.add(parseContext(evidence));
        }
        JsonNode accepted = node.get("accepted_correction");
        return new EventDetails(
                requiredText(node, "event_id"),
                requiredText(node, "client_id"),
                requiredText(node, "platform"),
                requiredText(node, "channel_profile"),
                requiredText(node, "scope_id"),
                optionalText(node, "channel_id"),
                optionalText(node, "conversation_id"),
                requiredText(node, "external_message_id"),
                optionalText(node, "canonical_message_id"),
                requiredText(node, "sender_id"),
                instant(node, "occurred_at"),
                requiredTextAllowEmpty(node, "text"),
                optionalText(node, "reply_to_message_id"),
                parseDecision(requiredObject(node, "decision")),
                parseAdvisory(node.get("advisory")),
                corrections,
                accepted == null || accepted.isNull() ? null : parseCorrection(accepted),
                context
        );
    }

    private Decision parseDecision(JsonNode node) {
        return new Decision(
                enumValue(MessageAction.class, node, "message_action"),
                requiredText(node, "semantic_label"),
                enumValue(ReviewPriority.class, node, "review_priority"),
                enumValue(StrikeRecommendation.class, node, "strike_recommendation"),
                enumValue(Containment.class, node, "containment"),
                optionalInteger(node, "containment_duration_seconds"),
                enumValue(SupportFlow.class, node, "support_flow"),
                doubleMap(requiredObject(node, "scores"), 32),
                optionalDouble(node, "confidence"),
                textArray(node, "rule_hits", 32),
                textArray(node, "reason_codes", 32),
                parseIncident(node.get("incident")),
                requiredText(node, "local_model_version"),
                requiredText(node, "policy_version")
        );
    }

    private AiReviewModels.IncidentSummary parseIncident(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (!node.isObject()) {
            throw new AiReviewClientException(Category.MALFORMED);
        }
        int severity = requiredNonNegativeInteger(node, "severity");
        if (severity > 100) {
            throw new AiReviewClientException(Category.MALFORMED);
        }
        JsonNode coordinated = node.get("coordinated");
        if (coordinated == null || !coordinated.isBoolean()) {
            throw new AiReviewClientException(Category.MALFORMED);
        }
        return new AiReviewModels.IncidentSummary(
                requiredText(node, "incident_id"),
                enumValue(AiReviewModels.IncidentKind.class, node, "kind"),
                severity,
                coordinated.booleanValue(),
                textArray(node, "participant_ids", 64),
                textArray(node, "target_ids", 64)
        );
    }

    private Advisory parseAdvisory(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (!node.isObject()) {
            throw new AiReviewClientException(Category.MALFORMED);
        }
        return new Advisory(
                requiredText(node, "status"),
                optionalText(node, "model"),
                optionalBoolean(node, "flagged"),
                doubleMap(requiredObject(node, "scores"), 64),
                booleanMap(requiredObject(node, "categories"), 64),
                optionalText(node, "error_code"),
                optionalInteger(node, "latency_ms"),
                optionalBoolean(node, "disagrees_with_local")
        );
    }

    private ContextEvidence parseContext(JsonNode node) {
        JsonNode message = requiredObject(node, "message");
        return new ContextEvidence(
                requiredText(node, "event_id"),
                new MessageReference(
                        requiredText(message, "platform"),
                        requiredText(message, "scope_id"),
                        optionalText(message, "channel_id"),
                        requiredText(message, "external_message_id")
                ),
                requiredText(node, "sender_id"),
                instant(node, "occurred_at"),
                requiredTextAllowEmpty(node, "text")
        );
    }

    private Correction parseCorrection(JsonNode node) {
        return new Correction(
                requiredText(node, "proposal_id"),
                requiredText(node, "event_id"),
                enumValue(CorrectionStatus.class, node, "status"),
                parseCorrectionDecision(requiredObject(node, "corrected")),
                requiredNonNegativeInteger(node, "approvals"),
                requiredNonNegativeInteger(node, "rejections"),
                instant(node, "created_at"),
                optionalInstant(node, "resolved_at")
        );
    }

    private CorrectionDecision parseCorrectionDecision(JsonNode node) {
        return new CorrectionDecision(
                requiredText(node, "semantic_label"),
                enumValue(MessageAction.class, node, "message_action"),
                enumValue(ReviewPriority.class, node, "review_priority"),
                enumValue(StrikeRecommendation.class, node, "strike_recommendation"),
                enumValue(Containment.class, node, "containment"),
                optionalInteger(node, "containment_duration_seconds"),
                enumValue(SupportFlow.class, node, "support_flow"),
                textArray(node, "reason_codes", 32)
        );
    }

    private ObjectNode correctionDecisionNode(CorrectionDecision decision) {
        Objects.requireNonNull(decision, "corrected");
        ObjectNode node = json.createObjectNode();
        node.put("semantic_label", decision.semanticLabel());
        node.put("message_action", decision.messageAction().name());
        node.put("review_priority", decision.reviewPriority().name());
        node.put("strike_recommendation", decision.strikeRecommendation().name());
        node.put("containment", decision.containment().name());
        if (decision.containmentDurationSeconds() == null) {
            node.putNull("containment_duration_seconds");
        } else {
            node.put("containment_duration_seconds", decision.containmentDurationSeconds());
        }
        node.put("support_flow", decision.supportFlow().name());
        var reasons = node.putArray("reason_codes");
        decision.reasonCodes().stream().limit(32).forEach(reasons::add);
        return node;
    }

    private static JsonNode requiredObject(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isObject()) {
            throw new AiReviewClientException(Category.MALFORMED);
        }
        return value;
    }

    private static JsonNode requiredArray(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isArray()) {
            throw new AiReviewClientException(Category.MALFORMED);
        }
        return value;
    }

    private static String requiredText(JsonNode node, String field) {
        String value = requiredTextAllowEmpty(node, field);
        if (value.isBlank()) {
            throw new AiReviewClientException(Category.MALFORMED);
        }
        return value;
    }

    private static String requiredTextAllowEmpty(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isTextual()) {
            throw new AiReviewClientException(Category.MALFORMED);
        }
        return value.textValue();
    }

    private static String optionalText(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : requiredTextAllowEmpty(node, field);
    }

    private static Instant instant(JsonNode node, String field) {
        try {
            return Instant.parse(requiredText(node, field));
        } catch (RuntimeException exception) {
            throw new AiReviewClientException(Category.MALFORMED, exception);
        }
    }

    private static Instant optionalInstant(JsonNode node, String field) {
        String value = optionalText(node, field);
        if (value == null) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (RuntimeException exception) {
            throw new AiReviewClientException(Category.MALFORMED, exception);
        }
    }

    private static Integer optionalInteger(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        if (!value.canConvertToInt()) {
            throw new AiReviewClientException(Category.MALFORMED);
        }
        return value.intValue();
    }

    private static int requiredNonNegativeInteger(JsonNode node, String field) {
        Integer value = optionalInteger(node, field);
        if (value == null || value < 0) {
            throw new AiReviewClientException(Category.MALFORMED);
        }
        return value;
    }

    private static Double optionalDouble(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        if (!value.isNumber() || !Double.isFinite(value.doubleValue())) {
            throw new AiReviewClientException(Category.MALFORMED);
        }
        return value.doubleValue();
    }

    private static Boolean optionalBoolean(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        if (!value.isBoolean()) {
            throw new AiReviewClientException(Category.MALFORMED);
        }
        return value.booleanValue();
    }

    private static List<String> textArray(JsonNode node, String field, int limit) {
        JsonNode array = requiredArray(node, field);
        if (array.size() > limit) {
            throw new AiReviewClientException(Category.MALFORMED);
        }
        List<String> values = new ArrayList<>();
        for (JsonNode value : array) {
            if (!value.isTextual() || value.textValue().isBlank()) {
                throw new AiReviewClientException(Category.MALFORMED);
            }
            values.add(value.textValue());
        }
        return List.copyOf(values);
    }

    private static Map<String, Double> doubleMap(JsonNode object, int limit) {
        if (object.size() > limit) {
            throw new AiReviewClientException(Category.MALFORMED);
        }
        Map<String, Double> values = new LinkedHashMap<>();
        object.properties().forEach(entry -> {
            JsonNode value = entry.getValue();
            if (!value.isNumber() || !Double.isFinite(value.doubleValue())) {
                throw new AiReviewClientException(Category.MALFORMED);
            }
            values.put(entry.getKey(), value.doubleValue());
        });
        return Map.copyOf(values);
    }

    private static Map<String, Boolean> booleanMap(JsonNode object, int limit) {
        if (object.size() > limit) {
            throw new AiReviewClientException(Category.MALFORMED);
        }
        Map<String, Boolean> values = new LinkedHashMap<>();
        object.properties().forEach(entry -> {
            JsonNode value = entry.getValue();
            if (!value.isBoolean()) {
                throw new AiReviewClientException(Category.MALFORMED);
            }
            values.put(entry.getKey(), value.booleanValue());
        });
        return Map.copyOf(values);
    }

    private static <E extends Enum<E>> E enumValue(Class<E> type, JsonNode node, String field) {
        try {
            return Enum.valueOf(type, requiredText(node, field));
        } catch (IllegalArgumentException exception) {
            throw new AiReviewClientException(Category.MALFORMED, exception);
        }
    }

    private static String required(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("required identifier is blank");
        }
        return normalized;
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static void putOptionalText(ObjectNode node, String field, String value) {
        if (value == null || value.isBlank()) {
            node.putNull(field);
        } else {
            node.put(field, value.trim());
        }
    }
}
