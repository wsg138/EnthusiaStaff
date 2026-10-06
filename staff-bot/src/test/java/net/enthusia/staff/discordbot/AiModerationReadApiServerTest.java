package net.enthusia.staff.discordbot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class AiModerationReadApiServerTest {
    private static final String TOKEN = "service-token-" + "x".repeat(32);

    @Test
    void authenticatesAndReturnsOnlyBoundedModerationState() throws Exception {
        try (AiModerationReadApiServer server = server()) {
            server.start();
            HttpResponse<String> response = request(
                    server.port(), "POST", TOKEN, "{\"minecraftUsername\":\"Bad_Player\"}");

            assertEquals(200, response.statusCode());
            assertTrue(response.body().contains("\"resolution\":\"RESOLVED\""));
            assertTrue(response.body().contains("\"exactReasonId\":\"exploit.major-abuse\""));
            assertTrue(response.body().contains("\"sanctionFamily\":\"exploit\""));
            assertFalse(response.body().contains("discordId"));
            assertFalse(response.body().contains("note"));
            assertFalse(response.body().contains("internalExplanation"));
        }
    }

    @Test
    void rejectsMissingOrWrongBearerBeforeReadingState() throws Exception {
        int[] reads = {0};
        try (AiModerationReadApiServer server = new AiModerationReadApiServer(
                "127.0.0.1", 0, TOKEN, request -> {
                    reads[0]++;
                    return response(request.minecraftUsername());
                })) {
            server.start();

            assertEquals(401, request(
                    server.port(), "POST", "wrong-" + TOKEN,
                    "{\"minecraftUsername\":\"Bad_Player\"}").statusCode());
            assertEquals(401, requestWithoutBearer(
                    server.port(), "{\"minecraftUsername\":\"Bad_Player\"}").statusCode());
            assertEquals(0, reads[0]);
        }
    }

    @Test
    void rejectsInvalidMethodAndInvalidMinecraftTarget() throws Exception {
        try (AiModerationReadApiServer server = server()) {
            server.start();

            assertEquals(405, request(
                    server.port(), "GET", TOKEN, "").statusCode());
            assertEquals(400, request(
                    server.port(), "POST", TOKEN,
                    "{\"minecraftUsername\":\"../bad\"}").statusCode());
        }
    }

    private static AiModerationReadApiServer server() throws Exception {
        return new AiModerationReadApiServer(
                "127.0.0.1", 0, TOKEN, request -> response(request.minecraftUsername()));
    }

    private static AiModerationReadApiModel.SubjectStateResponse response(String username) {
        return new AiModerationReadApiModel.SubjectStateResponse(
                "RESOLVED",
                username,
                Optional.of("11111111-1111-1111-1111-111111111111"),
                List.of(new AiModerationReadApiModel.ActiveSanctionDto(
                        "sanction-1",
                        "0123456789ABCDEF",
                        "NETWORK_BAN",
                        "Major exploit abuse",
                        Optional.of("exploit.major-abuse"),
                        Optional.of("exploit"),
                        Instant.parse("2026-10-06T18:00:00Z"),
                        Optional.empty()
                )),
                List.of(new AiModerationReadApiModel.CaseDto(
                        "0123456789ABCDEF",
                        "exploit.major-abuse",
                        "exploit",
                        "OPEN",
                        Instant.parse("2026-10-06T18:00:00Z")
                )),
                Instant.parse("2026-10-06T19:00:00Z")
        );
    }

    private static HttpResponse<String> request(
            int port,
            String method,
            String bearer,
            String body
    ) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri(port))
                .header("Authorization", "Bearer " + bearer);
        if ("GET".equals(method)) {
            builder.GET();
        } else {
            builder.POST(HttpRequest.BodyPublishers.ofString(body));
        }
        return HttpClient.newHttpClient().send(
                builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static HttpResponse<String> requestWithoutBearer(int port, String body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(uri(port))
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        return HttpClient.newHttpClient().send(
                request, HttpResponse.BodyHandlers.ofString());
    }

    private static URI uri(int port) {
        return URI.create("http://127.0.0.1:" + port + AiModerationReadApiServer.SUBJECT_STATE_PATH);
    }
}
