package net.enthusia.staff.velocity;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

final class ExternalReadinessProbe {
    enum State { PASS, WARNING, DISABLED }

    record Result(State state, String detail) {
    }

    private ExternalReadinessProbe() {
    }

    static Result probe(URI endpoint, int timeoutMillis) {
        if (endpoint == null) {
            return new Result(State.DISABLED, "readiness URL is not configured");
        }
        Duration timeout = Duration.ofMillis(timeoutMillis);
        HttpClient client = HttpClient.newBuilder().connectTimeout(timeout).build();
        HttpRequest request = HttpRequest.newBuilder(endpoint).GET().timeout(timeout).build();
        try {
            HttpResponse<Void> response = client.send(request, HttpResponse.BodyHandlers.discarding());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                return new Result(State.PASS, "ready (HTTP " + response.statusCode() + ')');
            }
            return new Result(State.WARNING, "not ready (HTTP " + response.statusCode() + ')');
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return new Result(State.WARNING, "readiness check was interrupted");
        } catch (IOException | RuntimeException exception) {
            return new Result(State.WARNING, "readiness endpoint is unreachable");
        }
    }
}
