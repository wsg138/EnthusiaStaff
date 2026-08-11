package net.enthusia.staff.velocity;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

@FunctionalInterface
interface DiscordWebhookTransport {
    Delivery send(DiscordWebhookRoute route, String content);

    record Delivery(boolean success, String errorCode) {
        Delivery {
            if (errorCode == null || errorCode.isBlank()) {
                throw new IllegalArgumentException("Discord delivery error code is required");
            }
        }

        static Delivery delivered() {
            return new Delivery(true, "NONE");
        }

        static Delivery failed(String errorCode) {
            return new Delivery(false, errorCode);
        }
    }

    final class Jdk implements DiscordWebhookTransport {
        private final Duration requestTimeout;
        private final ObjectMapper json;
        private final HttpClient http;

        Jdk(Duration requestTimeout) {
            if (requestTimeout == null || requestTimeout.isZero() || requestTimeout.isNegative()) {
                throw new IllegalArgumentException("Discord request timeout must be positive");
            }
            this.requestTimeout = requestTimeout;
            this.json = new ObjectMapper();
            this.http = HttpClient.newBuilder()
                    .connectTimeout(requestTimeout)
                    .followRedirects(HttpClient.Redirect.NEVER)
                    .build();
        }

        @Override
        public Delivery send(DiscordWebhookRoute route, String content) {
            if (route == null || content == null || content.isBlank()) {
                return Delivery.failed("INVALID_DELIVERY_INPUT");
            }
            try {
                ObjectNode body = json.createObjectNode();
                body.put("content", content);
                ObjectNode allowedMentions = body.putObject("allowed_mentions");
                ArrayNode parse = allowedMentions.putArray("parse");
                parse.removeAll();
                HttpRequest request = HttpRequest.newBuilder(route.endpoint())
                        .timeout(requestTimeout)
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)))
                        .build();
                HttpResponse<Void> response = http.send(request, HttpResponse.BodyHandlers.discarding());
                return classify(response.statusCode());
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                return Delivery.failed("INTERRUPTED");
            } catch (IOException | RuntimeException exception) {
                return Delivery.failed("IO_OR_ENCODING_FAILURE");
            }
        }

        static Delivery classify(int statusCode) {
            if (statusCode >= 200 && statusCode < 300) {
                return Delivery.delivered();
            }
            if (statusCode >= 300 && statusCode < 400) {
                return Delivery.failed("HTTP_REDIRECT_REJECTED");
            }
            if (statusCode == 429) {
                return Delivery.failed("HTTP_429");
            }
            if (statusCode >= 500) {
                return Delivery.failed("HTTP_5XX");
            }
            if (statusCode >= 400) {
                return Delivery.failed("HTTP_4XX");
            }
            return Delivery.failed("HTTP_INVALID_STATUS");
        }
    }
}
