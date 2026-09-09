package net.enthusia.staff.discordbot;

import static org.junit.jupiter.api.Assertions.fail;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.junit.jupiter.api.Test;

/** Validation-only diagnostic. Never merge this file into the ES-D16 product branch. */
final class CodacyIssueDiagnosticTest {
    @Test
    void exposePublicPullRequestIssuesForWorkerDiagnosis() throws Exception {
        URI endpoint = URI.create(
                "https://api.codacy.com/api/v3/analysis/organizations/gh/wsg138/repositories/EnthusiaStaff/pull-requests/187/issues");
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        HttpRequest request = HttpRequest.newBuilder(endpoint)
                .timeout(Duration.ofSeconds(30))
                .header("Accept", "application/json")
                .GET()
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        fail("CODACY_PR187_DIAGNOSTIC_HTTP=" + response.statusCode() + "\n" + response.body());
    }
}
