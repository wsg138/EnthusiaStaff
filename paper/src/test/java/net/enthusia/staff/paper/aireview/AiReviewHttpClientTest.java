package net.enthusia.staff.paper.aireview;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import net.enthusia.staff.paper.aireview.AiReviewClientException.Category;
import net.enthusia.staff.paper.aireview.AiReviewModels.Containment;
import net.enthusia.staff.paper.aireview.AiReviewModels.CorrectionAuthority;
import net.enthusia.staff.paper.aireview.AiReviewModels.CorrectionDecision;
import net.enthusia.staff.paper.aireview.AiReviewModels.MessageAction;
import net.enthusia.staff.paper.aireview.AiReviewModels.ReviewPriority;
import net.enthusia.staff.paper.aireview.AiReviewModels.StrikeRecommendation;
import net.enthusia.staff.paper.aireview.AiReviewModels.SupportFlow;
import org.junit.jupiter.api.Test;

class AiReviewHttpClientTest {
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void reviewQueueUsesBoundedLimitAndExactAuthenticationHeaders() throws Exception {
        try (MiniServer server = new MiniServer(request -> Response.json(200, queueJson()))) {
            AiReviewHttpClient client = client(server, 64 * 1024, 2_000);
            assertEquals(1, client.listReviews(999).size());

            Request request = server.awaitRequest();
            assertEquals("GET", request.method());
            assertEquals("/v1/review-items?limit=100", request.target());
            assertEquals("Bearer super-secret", request.headers().get("authorization"));
            assertEquals("staff-test", request.headers().get("x-client-id"));
        }
    }

    @Test
    void historyIncludesAllowAndBlockWithoutRawTextAndEncodesCursor() throws Exception {
        String response = """
                {"items":[
                    {"event_id":"abc","occurred_at":"2026-10-09T10:00:00Z",
                     "finalized_at":"2026-10-09T10:00:01Z","platform":"minecraft",
                     "channel_profile":"minecraft_private","ingestion_status":"INGESTED",
                     "message_action":"ALLOW","semantic_label":"SAFE","review_priority":"NONE",
                     "reason_codes":["safe"],"local_model_version":"m1",
                     "policy_version":"v1","degraded":false,"corrected":false},
                    {"event_id":"def","occurred_at":"2026-10-09T10:00:02Z",
                     "finalized_at":"2026-10-09T10:00:03Z","platform":"discord",
                     "channel_profile":"discord_general","ingestion_status":"INGESTED",
                     "message_action":"BLOCK","semantic_label":"SLUR_USE","review_priority":"NORMAL",
                     "reason_codes":["slur"],"local_model_version":"m1",
                     "policy_version":"v1","degraded":false,"corrected":true}
                ],"next_cursor":"def"}
                """;
        try (MiniServer server = new MiniServer(request -> Response.json(200, response))) {
            AiReviewHttpClient client = client(server, 64 * 1024, 2_000);
            var page = client.listDecisions(5, "prior-id");
            assertEquals(2, page.items().size());
            assertEquals("def", page.nextCursor());
            assertEquals(MessageAction.ALLOW, page.items().get(0).messageAction());
            assertEquals(MessageAction.BLOCK, page.items().get(1).messageAction());
            assertTrue(page.items().get(1).corrected());
            assertEquals("minecraft_private", page.items().get(0).channelProfile());
            var request = server.awaitRequest();
            assertEquals("/v1/decisions?limit=5&cursor=prior-id", request.target());
            assertEquals("staff-test", request.headers().get("x-client-id"));
            assertEquals("Bearer super-secret", request.headers().get("authorization"));
        }
    }

    @Test
    void historyFiltersAreServerSideAndNeverSendFreeText() throws Exception {
        try (MiniServer server = new MiniServer(request ->
                Response.json(200, "{\"items\":[],\"next_cursor\":null}"))) {
            AiReviewHttpClient client = client(server, 64 * 1024, 2_000);
            var history = client.listDecisions(10, null, AiReviewHistoryFilter.FAIL_OPEN);
            assertTrue(history.items().isEmpty());
            assertEquals("/v1/decisions?limit=10&filter=fail_open",
                    server.awaitRequest().target());
        }
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.EnumSource(AiReviewHistoryFilter.class)
    void allHistoryFiltersAreEncodedAsFixedServerSideValues(
            AiReviewHistoryFilter filter
    ) throws Exception {
        try (MiniServer server = new MiniServer(request ->
                Response.json(200, "{\"items\":[],\"next_cursor\":null}"))) {
            AiReviewHttpClient client = client(server, 64 * 1024, 2_000);
            assertTrue(client.listDecisions(2, null, filter).items().isEmpty());
            String expected = "/v1/decisions?limit=2"
                    + (filter == AiReviewHistoryFilter.ALL
                            ? "" : "&filter=" + filter.apiValue());
            assertEquals(expected, server.awaitRequest().target());
        }
    }

    @Test
    void historyContractKeepsCorrectedOriginalAndFailOpenUntrusted() throws Exception {
        String response = """
                {"items":[
                  {"event_id":"corrected-1","occurred_at":"2026-10-09T10:00:00Z",
                   "finalized_at":"2026-10-09T10:00:01Z","platform":"minecraft",
                   "channel_profile":"minecraft_public","ingestion_status":"INGESTED",
                   "message_action":"BLOCK","semantic_label":"LOW_LEVEL_HARASSMENT",
                   "review_priority":"NORMAL","reason_codes":["staff_review"],
                   "local_model_version":"synthetic-1","policy_version":"test-policy",
                   "degraded":false,"corrected":true,
                   "text":"synthetic-private-message","sender_id":"synthetic-private-player"},
                  {"event_id":"failopen-1","occurred_at":"2026-10-09T10:00:02Z",
                   "finalized_at":"2026-10-09T10:00:03Z","platform":"discord",
                   "channel_profile":"discord_general","ingestion_status":"FAIL_OPEN",
                   "message_action":"ALLOW","semantic_label":"SAFE",
                   "review_priority":"NONE","reason_codes":["classifier_unavailable"],
                   "local_model_version":"synthetic-1","policy_version":"test-policy",
                   "degraded":true,"corrected":false}
                ],"next_cursor":null}
                """;
        try (MiniServer server = new MiniServer(request -> Response.json(200, response))) {
            var items = client(server, 64 * 1024, 2_000).listDecisions(2, null).items();
            assertEquals(2, items.size());
            assertEquals("LOW_LEVEL_HARASSMENT", items.get(0).semanticLabel());
            assertEquals(MessageAction.BLOCK, items.get(0).messageAction());
            assertTrue(items.get(0).corrected());
            assertTrue(AiReviewHistoryPresentation.summarize(items.get(0))
                    .lore().contains("Staff correction recorded"));
            assertEquals("FAIL_OPEN", items.get(1).ingestionStatus());
            assertTrue(AiReviewHistoryPresentation.summarize(items.get(1))
                    .lore().contains("Fail-open is NOT a verified safe decision."));
            assertFalse(items.toString().contains("synthetic-private-message"));
            assertFalse(items.toString().contains("synthetic-private-player"));
        }
    }

    @Test
    void historyRejectsMalformedOrUnboundedPage() throws Exception {
        try (MiniServer server = new MiniServer(request ->
                Response.json(200, "{\"items\":[{}],\"next_cursor\":null}"))) {
            AiReviewHttpClient client = client(server, 64 * 1024, 2_000);
            assertEquals(Category.MALFORMED,
                    assertThrows(AiReviewClientException.class,
                            () -> client.listDecisions(10, null)).category());
        }
    }

    @Test
    void historyMissingCursorIsInputFailureNotSharedServiceOutage() throws Exception {
        try (MiniServer server = new MiniServer(request -> Response.json(404, "{}"))) {
            AiReviewHttpClient client = client(server, 64 * 1024, 2_000);
            var failure = assertThrows(AiReviewClientException.class,
                    () -> client.listDecisions(10, "missing-cursor"));
            assertEquals(Category.INVALID_CURSOR, failure.category());
            assertEquals("/v1/decisions?limit=10&cursor=missing-cursor",
                    server.awaitRequest().target());
        }
    }

    @Test
    void unrelatedEventNotFoundRemainsOrdinaryServiceError() throws Exception {
        try (MiniServer server = new MiniServer(request -> Response.json(404, "{}"))) {
            AiReviewHttpClient client = client(server, 64 * 1024, 2_000);
            assertEquals(Category.NOT_FOUND,
                    assertThrows(AiReviewClientException.class,
                            () -> client.event("no-such-event")).category());
        }
    }

    @Test
    void eventParsingUsesOnlyTypedAllowlistedFields() throws Exception {
        try (MiniServer server = new MiniServer(request -> Response.json(200, eventJson()))) {
            var details = client(server, 64 * 1024, 2_000).event("event-1");
            assertEquals("event-1", details.eventId());
            assertEquals("hello", details.text());
            assertEquals(MessageAction.ALLOW, details.decision().messageAction());
            assertEquals("incident-1", details.decision().incident().incidentId());
            assertEquals(AiReviewModels.IncidentKind.THREAT, details.decision().incident().kind());
            assertEquals(72, details.decision().incident().severity());
            assertEquals(1, details.contextEvidence().size());
            assertEquals(1, details.corrections().size());
        }
    }

    @Test
    void correctionPostCarriesCompleteDecisionAndStableReviewerId() throws Exception {
        try (MiniServer server = new MiniServer(request -> Response.json(201, correctionJson()))) {
            AiReviewHttpClient client = client(server, 64 * 1024, 2_000);
            client.correct(
                    "event-1",
                    "00000000-0000-0000-0000-000000000001",
                    CorrectionAuthority.STAFF,
                    decision(),
                    "private note"
            );

            Request request = server.awaitRequest();
            assertEquals("POST", request.method());
            assertEquals("/v1/review-corrections", request.target());
            var body = json.readTree(request.body());
            assertEquals("event-1", body.get("event_id").asText());
            assertEquals("STAFF", body.get("authority").asText());
            assertEquals("BLOCK", body.get("corrected").get("message_action").asText());
            assertEquals("EVIDENCE", body.get("corrected").get("strike_recommendation").asText());
            assertEquals(120, body.get("corrected").get("containment_duration_seconds").asInt());
            assertEquals("TARGET_SAFETY_CHECK", body.get("corrected").get("support_flow").asText());
        }
    }

    @Test
    void rejectionUsesExactProposalEndpoint() throws Exception {
        try (MiniServer server = new MiniServer(request -> Response.json(200, rejectedCorrectionJson()))) {
            var correction = client(server, 64 * 1024, 2_000).reject(
                    "proposal-1",
                    "reviewer",
                    CorrectionAuthority.STAFF,
                    null
            );
            assertEquals(AiReviewModels.CorrectionStatus.REJECTED, correction.status());
            assertEquals("/v1/review-corrections/proposal-1/reject", server.awaitRequest().target());
        }
    }

    @Test
    void twoDistinctStaffApprovalsAndDuplicateReviewerRemainCentralSemantics() throws Exception {
        Set<String> reviewers = ConcurrentHashMap.newKeySet();
        try (MiniServer server = new MiniServer(request -> {
            try {
                var body = json.readTree(request.body());
                String reviewer = body.get("reviewer_id").asText();
                String authority = body.get("authority").asText();
                if ("ADMIN".equals(authority)) {
                    return Response.json(201, correctionJson("ACCEPTED", 1, 0));
                }
                reviewers.add(reviewer);
                int approvals = reviewers.size();
                return Response.json(
                        201,
                        correctionJson(
                                approvals >= 2 ? "ACCEPTED" : "PENDING_CONFIRMATION",
                                approvals,
                                0
                        )
                );
            } catch (IOException exception) {
                throw new IllegalStateException(exception);
            }
        })) {
            AiReviewHttpClient client = client(server, 64 * 1024, 2_000);
            var first = client.correct(
                    "event-1", "staff-a", CorrectionAuthority.STAFF, decision(), null
            );
            var duplicate = client.correct(
                    "event-1", "staff-a", CorrectionAuthority.STAFF, decision(), null
            );
            var second = client.correct(
                    "event-1", "staff-b", CorrectionAuthority.STAFF, decision(), null
            );
            assertEquals(AiReviewModels.CorrectionStatus.PENDING_CONFIRMATION, first.status());
            assertEquals(1, first.approvals());
            assertEquals(AiReviewModels.CorrectionStatus.PENDING_CONFIRMATION, duplicate.status());
            assertEquals(1, duplicate.approvals());
            assertEquals(AiReviewModels.CorrectionStatus.ACCEPTED, second.status());
            assertEquals(2, second.approvals());
        }
    }

    @Test
    void adminCorrectionAuthorityIsPropagatedAndCentralResponseMayAcceptImmediately() throws Exception {
        try (MiniServer server = new MiniServer(request -> Response.json(
                201,
                correctionJson("ACCEPTED", 1, 0)
        ))) {
            var result = client(server, 64 * 1024, 2_000).correct(
                    "event-1",
                    "admin-a",
                    CorrectionAuthority.ADMIN,
                    decision(),
                    null
            );
            assertEquals(AiReviewModels.CorrectionStatus.ACCEPTED, result.status());
            var body = json.readTree(server.awaitRequest().body());
            assertEquals("ADMIN", body.get("authority").asText());
        }
    }

    @Test
    void staffRejectVotesRequireDistinctReviewersWhileAdminRejectMayResolveImmediately() throws Exception {
        Set<String> reviewers = ConcurrentHashMap.newKeySet();
        try (MiniServer server = new MiniServer(request -> {
            try {
                var body = json.readTree(request.body());
                if ("ADMIN".equals(body.get("authority").asText())) {
                    return Response.json(200, correctionJson("REJECTED", 0, 1));
                }
                reviewers.add(body.get("reviewer_id").asText());
                int rejections = reviewers.size();
                return Response.json(
                        200,
                        correctionJson(
                                rejections >= 2 ? "REJECTED" : "PENDING_CONFIRMATION",
                                0,
                                rejections
                        )
                );
            } catch (IOException exception) {
                throw new IllegalStateException(exception);
            }
        })) {
            AiReviewHttpClient client = client(server, 64 * 1024, 2_000);
            var first = client.reject(
                    "proposal-1", "staff-a", CorrectionAuthority.STAFF, null
            );
            var duplicate = client.reject(
                    "proposal-1", "staff-a", CorrectionAuthority.STAFF, null
            );
            var second = client.reject(
                    "proposal-1", "staff-b", CorrectionAuthority.STAFF, null
            );
            var admin = client.reject(
                    "proposal-1", "admin-a", CorrectionAuthority.ADMIN, null
            );
            assertEquals(AiReviewModels.CorrectionStatus.PENDING_CONFIRMATION, first.status());
            assertEquals(1, first.rejections());
            assertEquals(1, duplicate.rejections());
            assertEquals(AiReviewModels.CorrectionStatus.REJECTED, second.status());
            assertEquals(2, second.rejections());
            assertEquals(AiReviewModels.CorrectionStatus.REJECTED, admin.status());
        }
    }

    @Test
    void conflictIsReturnedAsBoundedCategoryWithoutResponseBodyOrSecret() throws Exception {
        try (MiniServer server = new MiniServer(request -> Response.json(
                409,
                "{\"detail\":\"attacker body super-secret\"}"
        ))) {
            AiReviewClientException exception = assertThrows(
                    AiReviewClientException.class,
                    () -> client(server, 64 * 1024, 2_000).event("event-1")
            );
            assertEquals(Category.CONFLICT, exception.category());
            assertFalse(exception.getMessage().contains("attacker body"));
            assertFalse(exception.getMessage().contains("super-secret"));
        }
    }

    @Test
    void centralUnavailableFailsAsUnavailableWithoutRetry() throws Exception {
        try (MiniServer server = new MiniServer(request -> Response.json(503, "{}"))) {
            AiReviewClientException exception = assertThrows(
                    AiReviewClientException.class,
                    () -> client(server, 64 * 1024, 2_000).listReviews(20)
            );
            assertEquals(Category.UNAVAILABLE, exception.category());
            assertEquals(1, server.awaitRequestCount(1));
        }
    }

    @Test
    void oversizedResponseIsRejectedBeforeJsonParsing() throws Exception {
        try (MiniServer server = new MiniServer(request -> Response.json(
                200,
                "{\"items\":[]," + "\"padding\":\"" + "x".repeat(20_000) + "\"}"
        ))) {
            AiReviewClientException exception = assertThrows(
                    AiReviewClientException.class,
                    () -> client(server, 16_384, 2_000).listReviews(20)
            );
            assertEquals(Category.OVERSIZED, exception.category());
        }
    }

    @Test
    void malformedSuccessIsRejected() throws Exception {
        try (MiniServer server = new MiniServer(request -> Response.json(200, "{\"unexpected\":true}"))) {
            AiReviewClientException exception = assertThrows(
                    AiReviewClientException.class,
                    () -> client(server, 64 * 1024, 2_000).listReviews(20)
            );
            assertEquals(Category.MALFORMED, exception.category());
        }
    }

    @Test
    void requestTimeoutDoesNotRetryOrBlockIndefinitely() throws Exception {
        try (MiniServer server = new MiniServer(request -> {
            try {
                Thread.sleep(600);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
            return Response.json(200, queueJson());
        })) {
            AiReviewClientException exception = assertThrows(
                    AiReviewClientException.class,
                    () -> client(server, 64 * 1024, 250).listReviews(20)
            );
            assertEquals(Category.TIMEOUT, exception.category());
            assertEquals(1, server.awaitRequestCount(1));
        }
    }

    @Test
    void correctionMayBeAcceptedByServerBeforeItsResponseTimesOut() throws Exception {
        java.util.concurrent.atomic.AtomicBoolean serverAccepted = new java.util.concurrent.atomic.AtomicBoolean();
        try (MiniServer server = new MiniServer(request -> {
            serverAccepted.set(true);
            return new Response(201, correctionJson().getBytes(StandardCharsets.UTF_8), 700);
        })) {
            AiReviewHttpClient client = client(server, 64 * 1024, 250);
            var failure = assertThrows(AiReviewClientException.class, () ->
                    client.correct("event-1", "reviewer-a", CorrectionAuthority.STAFF, decision(), null));
            assertEquals(Category.TIMEOUT, failure.category());
            assertTrue(serverAccepted.get(), "The write reached the server before the reply was lost");
            String issue = "central review timeout; retry backoff 5s";
            assertTrue(AiReviewWriteFeedback.outcomeUncertain(issue));
            assertTrue(AiReviewWriteFeedback.message(issue).contains("status UNKNOWN"));
        }
    }

    private AiReviewHttpClient client(MiniServer server, int maxBytes, long timeoutMillis) {
        AiReviewConfiguration configuration = new AiReviewConfiguration(
                server.uri(),
                "staff-test",
                "super-secret",
                AiReviewPermissions.QUEUE,
                Duration.ofMillis(250),
                Duration.ofMillis(timeoutMillis),
                Duration.ofSeconds(10),
                Duration.ofSeconds(30),
                100,
                1,
                8,
                maxBytes,
                32,
                21,
                800,
                8,
                12,
                10,
                false
        );
        return new AiReviewHttpClient(configuration, json);
    }

    private static CorrectionDecision decision() {
        return new CorrectionDecision(
                "REAL_WORLD_THREAT",
                MessageAction.BLOCK,
                ReviewPriority.URGENT,
                StrikeRecommendation.EVIDENCE,
                Containment.MUTE,
                120,
                SupportFlow.TARGET_SAFETY_CHECK,
                List.of("staff_review")
        );
    }

    private static String queueJson() {
        return """
                {"items":[{
                  "event_id":"event-1",
                  "occurred_at":"2026-10-03T20:00:00Z",
                  "platform":"minecraft",
                  "channel_profile":"minecraft_public",
                  "semantic_label":"SAFE",
                  "message_action":"ALLOW",
                  "review_priority":"NORMAL",
                  "reason_codes":["reason"],
                  "incident_id":null
                }]}
                """;
    }

    private static String eventJson() {
        return """
                {
                  "event_id":"event-1",
                  "client_id":"rosechat",
                  "platform":"minecraft",
                  "channel_profile":"minecraft_public",
                  "scope_id":"SMP",
                  "channel_id":"global",
                  "conversation_id":null,
                  "external_message_id":"mc-1",
                  "canonical_message_id":"canon-1",
                  "sender_id":"player-a",
                  "occurred_at":"2026-10-03T20:00:00Z",
                  "text":"hello",
                  "reply_to_message_id":null,
                  "decision":{
                    "message_action":"ALLOW",
                    "semantic_label":"SAFE",
                    "review_priority":"NORMAL",
                    "strike_recommendation":"NONE",
                    "containment":"NONE",
                    "containment_duration_seconds":null,
                    "support_flow":"NONE",
                    "scores":{"SAFE":0.9},
                    "confidence":0.9,
                    "rule_hits":[],
                    "reason_codes":["reason"],
                    "related_message_ids":[],
                    "related_messages":[],
                    "incident":{
                      "incident_id":"incident-1",
                      "kind":"THREAT",
                      "severity":72,
                      "coordinated":false,
                      "participant_ids":["player-a"],
                      "target_ids":["player-b"]
                    },
                    "local_model_version":"w12",
                    "policy_version":"v1",
                    "advisory_status":"DISABLED",
                    "latency_ms":2,
                    "degraded":false,
                    "fallback_state":null,
                    "idempotent_replay":false
                  },
                  "advisory":null,
                  "corrections":[{
                    "proposal_id":"proposal-1",
                    "event_id":"event-1",
                    "status":"PENDING_CONFIRMATION",
                    "corrected":{
                      "semantic_label":"SAFE",
                      "message_action":"ALLOW",
                      "review_priority":"NORMAL",
                      "strike_recommendation":"NONE",
                      "containment":"NONE",
                      "containment_duration_seconds":null,
                      "support_flow":"NONE",
                      "reason_codes":["reason"]
                    },
                    "approvals":1,
                    "rejections":0,
                    "created_at":"2026-10-03T20:00:01Z",
                    "resolved_at":null
                  }],
                  "accepted_correction":null,
                  "context_evidence":[{
                    "event_id":"context-1",
                    "message":{
                      "platform":"minecraft",
                      "scope_id":"SMP",
                      "channel_id":"global",
                      "external_message_id":"mc-0"
                    },
                    "sender_id":"player-b",
                    "occurred_at":"2026-10-03T19:59:59Z",
                    "text":"context"
                  }],
                  "secret_unknown_field":"must-not-be-exposed"
                }
                """;
    }

    private static String correctionJson() {
        return """
                {
                  "proposal_id":"proposal-1",
                  "event_id":"event-1",
                  "status":"PENDING_CONFIRMATION",
                  "corrected":{
                    "semantic_label":"REAL_WORLD_THREAT",
                    "message_action":"BLOCK",
                    "review_priority":"URGENT",
                    "strike_recommendation":"EVIDENCE",
                    "containment":"MUTE",
                    "containment_duration_seconds":120,
                    "support_flow":"TARGET_SAFETY_CHECK",
                    "reason_codes":["staff_review"]
                  },
                  "approvals":1,
                  "rejections":0,
                  "created_at":"2026-10-03T20:00:01Z",
                  "resolved_at":null
                }
                """;
    }

    private static String rejectedCorrectionJson() {
        return correctionJson()
                .replace("\"PENDING_CONFIRMATION\"", "\"REJECTED\"")
                .replace("\"rejections\":0", "\"rejections\":2")
                .replace("\"resolved_at\":null", "\"resolved_at\":\"2026-10-03T20:00:02Z\"");
    }

    private static String correctionJson(String status, int approvals, int rejections) {
        return correctionJson()
                .replace("\"PENDING_CONFIRMATION\"", "\"" + status + "\"")
                .replace("\"approvals\":1", "\"approvals\":" + approvals)
                .replace("\"rejections\":0", "\"rejections\":" + rejections)
                .replace(
                        "\"resolved_at\":null",
                        "PENDING_CONFIRMATION".equals(status)
                                ? "\"resolved_at\":null"
                                : "\"resolved_at\":\"2026-10-03T20:00:02Z\""
                );
    }

    private record Request(String method, String target, Map<String, String> headers, String body) {
    }

    private record Response(int status, byte[] body, long delayMillis) {
        static Response json(int status, String body) {
            return new Response(status, body.getBytes(StandardCharsets.UTF_8), 0);
        }
    }

    private static final class MiniServer implements AutoCloseable {
        private final ServerSocket server;
        private final Thread thread;
        private final Function<Request, Response> responder;
        private final CopyOnWriteArrayList<Request> requests = new CopyOnWriteArrayList<>();
        private final AtomicReference<Throwable> failure = new AtomicReference<>();
        private volatile boolean closed;

        MiniServer(Function<Request, Response> responder) throws IOException {
            this.responder = responder;
            server = new ServerSocket(0, 20, java.net.InetAddress.getLoopbackAddress());
            thread = new Thread(this::serve, "AiReviewHttpClientTest-server");
            thread.setDaemon(true);
            thread.start();
        }

        URI uri() {
            return URI.create("http://127.0.0.1:" + server.getLocalPort());
        }

        Request awaitRequest() throws Exception {
            for (int attempt = 0; attempt < 100; attempt++) {
                if (!requests.isEmpty()) {
                    return requests.getFirst();
                }
                Throwable problem = failure.get();
                if (problem != null) {
                    throw new AssertionError(problem);
                }
                Thread.sleep(10);
            }
            throw new AssertionError("request did not arrive");
        }

        int awaitRequestCount(int expected) throws Exception {
            for (int attempt = 0; attempt < 100; attempt++) {
                if (requests.size() >= expected) {
                    return requests.size();
                }
                Thread.sleep(10);
            }
            return requests.size();
        }

        private void serve() {
            while (!closed) {
                try (Socket socket = server.accept()) {
                    Request request = read(socket.getInputStream());
                    requests.add(request);
                    Response response = responder.apply(request);
                    if (response.delayMillis() > 0) {
                        Thread.sleep(response.delayMillis());
                    }
                    write(socket.getOutputStream(), response);
                } catch (java.net.SocketException exception) {
                    if (!closed) {
                        failure.compareAndSet(null, exception);
                    }
                } catch (Throwable problem) {
                    if (!closed) {
                        failure.compareAndSet(null, problem);
                    }
                }
            }
        }

        private static Request read(InputStream input) throws IOException {
            ByteArrayOutputStream headers = new ByteArrayOutputStream();
            int matched = 0;
            while (matched < 4) {
                int value = input.read();
                if (value < 0) {
                    throw new IOException("unexpected EOF");
                }
                headers.write(value);
                byte[] marker = {'\r', '\n', '\r', '\n'};
                if (value == marker[matched]) {
                    matched++;
                } else {
                    matched = value == marker[0] ? 1 : 0;
                }
                if (headers.size() > 32_768) {
                    throw new IOException("request headers too large");
                }
            }
            String raw = headers.toString(StandardCharsets.ISO_8859_1);
            String[] lines = raw.split("\\r\\n");
            String[] start = lines[0].split(" ", 3);
            java.util.LinkedHashMap<String, String> parsedHeaders = new java.util.LinkedHashMap<>();
            for (int index = 1; index < lines.length; index++) {
                int colon = lines[index].indexOf(':');
                if (colon > 0) {
                    parsedHeaders.put(
                            lines[index].substring(0, colon).trim().toLowerCase(Locale.ROOT),
                            lines[index].substring(colon + 1).trim()
                    );
                }
            }
            int length = Integer.parseInt(parsedHeaders.getOrDefault("content-length", "0"));
            String body = new String(input.readNBytes(length), StandardCharsets.UTF_8);
            return new Request(start[0], start[1], Map.copyOf(parsedHeaders), body);
        }

        private static void write(OutputStream output, Response response) throws IOException {
            String reason = response.status() >= 200 && response.status() < 300 ? "OK" : "ERROR";
            byte[] prefix = (
                    "HTTP/1.1 " + response.status() + " " + reason + "\r\n"
                            + "Content-Type: application/json\r\n"
                            + "Content-Length: " + response.body().length + "\r\n"
                            + "Connection: close\r\n\r\n"
            ).getBytes(StandardCharsets.ISO_8859_1);
            output.write(prefix);
            output.write(response.body());
            output.flush();
        }

        @Override
        public void close() {
            closed = true;
            try {
                server.close();
            } catch (IOException ignored) {
                // Best-effort test fixture cleanup.
            }
            try {
                thread.join(1_000);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
