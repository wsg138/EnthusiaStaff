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
        private final String alertRoleId;

        Jdk(Duration requestTimeout) {
            this(requestTimeout, createClient(requestTimeout));
        }

        private Jdk(Duration requestTimeout, HttpClient client) {
            this(requestTimeout, exchangeFor(client), client::close,
                    System.getProperty("enthusiastaff.discord.alertRoleId", ""));
        }

        Jdk(Duration requestTimeout, HttpExchange exchange) {
            this(requestTimeout, exchange, () -> { }, "");
        }

        Jdk(Duration requestTimeout, HttpExchange exchange, String alertRoleId) {
            this(requestTimeout, exchange, () -> { }, alertRoleId);
        }

        private Jdk(Duration requestTimeout, HttpExchange exchange, Runnable closeAction, String alertRoleId) {
            if (requestTimeout == null || requestTimeout.isZero() || requestTimeout.isNegative()
                    || exchange == null || closeAction == null) {
                throw new IllegalArgumentException("Discord request timeout and HTTP exchange must be valid");
            }
            this.requestTimeout = requestTimeout;
            this.json = new ObjectMapper();
            this.exchange = exchange;
            this.closeAction = closeAction;
            if (alertRoleId == null || (!alertRoleId.isEmpty()
                    && !alertRoleId.matches("[1-9][0-9]{16,19}"))) {
                throw new IllegalArgumentException("Discord alert role ID must be a valid snowflake or disabled");
            }
            if (!alertRoleId.isEmpty()) {
                try {
                    Long.parseUnsignedLong(alertRoleId);
                } catch (NumberFormatException invalid) {
                    throw new IllegalArgumentException("Discord alert role ID is out of range", invalid);
                }
            }
            this.alertRoleId = alertRoleId;
        }

        @Override
        public Delivery send(DiscordWebhookRoute route, String content) {
            if (route == null || content == null || content.isBlank()) {
                return Delivery.failed("INVALID_DELIVERY_INPUT");
            }
            try {
                ObjectNode body = buildEmbedBody(route, content);
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

        /**
         * Existing durable destination routing remains authoritative; presentation alone
         * cannot introduce new destinations or mass mentions; an explicit role ping
         * is allowed only for the private alerts destination and a configured role.
         */
        private ObjectNode buildEmbedBody(DiscordWebhookRoute route, String rendered) {
            String[] lines = rendered.split("\\n");
            ObjectNode body = json.createObjectNode();
            ObjectNode mentions = body.putObject("allowed_mentions");
            mentions.putArray("parse");
            if ("alerts".equals(route.destination()) && !alertRoleId.isEmpty()) {
                body.put("content", "<@&" + alertRoleId + ">");
                mentions.putArray("roles").add(alertRoleId);
            }
            var embeds = body.putArray("embeds");
            ObjectNode embed = embeds.addObject();
            embed.put("title", prettyTitle(lines[0]));
            embed.put("color", color(route.destination()));
            var fields = embed.putArray("fields");
            for (int index = 1; index < lines.length; index++) {
                int separator = lines[index].indexOf('=');
                if (separator < 1 || fields.size() >= 20) {
                    continue;
                }
                String name = lines[index].substring(0, separator);
                String value = lines[index].substring(separator + 1);
                if (!name.matches("[a-zA-Z][a-zA-Z0-9_-]{0,63}") || value.isBlank()) {
                    continue;
                }
                ObjectNode field = fields.addObject();
                field.put("name", prettyTitle(name));
                field.put("value", value.length() > 950 ? value.substring(0, 949) + "…" : value);
                field.put("inline", isCompact(name));
            }
            return body;
        }

        private static String prettyTitle(String raw) {
            String safe = raw.replaceAll("([a-z])([A-Z])", "$1 $2")
                    .replace('_', ' ').replace('-', ' ')
                    .replaceAll("[^a-zA-Z0-9 :./]", "");
            String title = safe.isBlank() ? "Staff notification"
                    : Character.toUpperCase(safe.charAt(0)) + safe.substring(1);
            title = title.replace(" Id", " ID");
            if (title.length() > 130) {
                return title.substring(0, 129) + "…";
            }
            return title;
        }

        private static boolean isCompact(String field) {
            return switch (field) {
                case "staffId", "actorId", "rank", "server", "serverId", "state",
                        "staffMode", "vanished", "active", "sanctionType", "status" -> true;
                default -> false;
            };
        }

        private static int color(String destination) {
            return switch (destination) {
                case "punishments" -> 0xCD4A4A;
                case "logs-staffmode" -> 0x5382B4;
                case "reports" -> 0xD69C42;
                case "alerts" -> 0xC87A39;
                default -> 0x697586;
            };
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
