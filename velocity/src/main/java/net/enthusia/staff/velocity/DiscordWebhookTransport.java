package net.enthusia.staff.velocity;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

@FunctionalInterface
interface DiscordWebhookTransport {
    Delivery send(DiscordWebhookRoute route, String content);

    record Delivery(boolean success, String errorCode) {
        public Delivery {
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

    final class Jdk implements DiscordWebhookTransport, AutoCloseable {
        private static final int HTTP_SUCCESS_MINIMUM = 200;
        private static final int HTTP_REDIRECT_MINIMUM = 300;
        private static final int HTTP_CLIENT_ERROR_MINIMUM = 400;
        private static final int HTTP_TOO_MANY_REQUESTS = 429;
        private static final int HTTP_SERVER_ERROR_MINIMUM = 500;

        @FunctionalInterface
        interface HttpExchange {
            int post(URI endpoint, Duration timeout, String body) throws IOException, InterruptedException;
        }

        private final Duration requestTimeout;
        private final ObjectMapper json;
        private final HttpExchange exchange;
        private final Runnable closeAction;

        Jdk(Duration requestTimeout) {
            this(requestTimeout, createClient(requestTimeout));
        }

        private Jdk(Duration requestTimeout, HttpClient client) {
            this(requestTimeout, exchangeFor(client), client::close);
        }

        Jdk(Duration requestTimeout, HttpExchange exchange) {
            this(requestTimeout, exchange, () -> { });
        }

        private Jdk(Duration requestTimeout, HttpExchange exchange, Runnable closeAction) {
            if (requestTimeout == null || requestTimeout.isZero() || requestTimeout.isNegative()
                    || exchange == null || closeAction == null) {
                throw new IllegalArgumentException("Discord request timeout and HTTP exchange must be valid");
            }
            this.requestTimeout = requestTimeout;
            this.json = new ObjectMapper();
            this.exchange = exchange;
            this.closeAction = closeAction;
        }

        @Override
        public Delivery send(DiscordWebhookRoute route, String content) {
            if (route == null || content == null || content.isBlank()) {
                return Delivery.failed("INVALID_DELIVERY_INPUT");
            }
            try {
                ObjectNode body = json.createObjectNode();
                body.put("content", content);
                body.putObject("allowed_mentions").putArray("parse");
                int statusCode = exchange.post(
                        route.endpoint(),
                        requestTimeout,
                        json.writeValueAsString(body)
                );
                return classify(statusCode);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                return Delivery.failed("INTERRUPTED");
            } catch (IOException | RuntimeException exception) {
                return Delivery.failed("IO_OR_ENCODING_FAILURE");
            }
        }

        static Delivery classify(int statusCode) {
            if (statusCode >= HTTP_SUCCESS_MINIMUM && statusCode < HTTP_REDIRECT_MINIMUM) {
                return Delivery.delivered();
            }
            if (statusCode >= HTTP_REDIRECT_MINIMUM && statusCode < HTTP_CLIENT_ERROR_MINIMUM) {
                return Delivery.failed("HTTP_REDIRECT_REJECTED");
            }
            if (statusCode == HTTP_TOO_MANY_REQUESTS) {
                return Delivery.failed("HTTP_429");
            }
            if (statusCode >= HTTP_SERVER_ERROR_MINIMUM) {
                return Delivery.failed("HTTP_5XX");
            }
            if (statusCode >= HTTP_CLIENT_ERROR_MINIMUM) {
                return Delivery.failed("HTTP_4XX");
            }
            return Delivery.failed("HTTP_INVALID_STATUS");
        }

        @Override
        public void close() {
            closeAction.run();
        }

        private static HttpClient createClient(Duration requestTimeout) {
            if (requestTimeout == null || requestTimeout.isZero() || requestTimeout.isNegative()) {
                throw new IllegalArgumentException("Discord request timeout must be positive");
            }
            return HttpClient.newBuilder()
                    .connectTimeout(requestTimeout)
                    .followRedirects(HttpClient.Redirect.NEVER)
                    .build();
        }

        private static HttpExchange exchangeFor(HttpClient http) {
            return (endpoint, timeout, body) -> {
                HttpRequest request = HttpRequest.newBuilder(endpoint)
                        .timeout(timeout)
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .build();
                HttpResponse<Void> response = http.send(request, HttpResponse.BodyHandlers.discarding());
                return response.statusCode();
            };
        }
    }
}
