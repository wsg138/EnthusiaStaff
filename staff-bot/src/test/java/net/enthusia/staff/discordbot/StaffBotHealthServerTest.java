package net.enthusia.staff.discordbot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Locale;
import org.junit.jupiter.api.Test;

class StaffBotHealthServerTest {
    @Test
    void distinguishesLivenessFromReadinessWithoutSensitivePayloads() throws Exception {
        StaffBotHealth health = new StaffBotHealth(StaffBotEnvironment.STAGING);
        try (StaffBotHealthServer server = new StaffBotHealthServer(loopbackEphemeralAddress(), health)) {
            server.start();
            HttpClient client = HttpClient.newHttpClient();
            URI base = URI.create("http://" + host(server.boundAddress()) + ":" + server.boundAddress().getPort());

            HttpResponse<String> live = get(client, base.resolve("/health"));
            HttpResponse<String> notReady = get(client, base.resolve("/ready"));
            assertEquals(200, live.statusCode());
            assertEquals(503, notReady.statusCode());
            assertTrue(live.body().contains("\"environment\":\"staging\""));
            assertFalse(live.body().toLowerCase(Locale.ROOT).contains("token"));

            health.transition(StaffBotHealth.Phase.READY, DiscordRuntimeIdentityValidator.READY);
            HttpResponse<String> ready = get(client, base.resolve("/ready"));
            assertEquals(200, ready.statusCode());
            assertTrue(ready.body().contains("\"ready\":true"));
        }
    }

    @Test
    void escapesAllJsonControlCharactersInHealthReasons() throws Exception {
        StaffBotHealth health = new StaffBotHealth(StaffBotEnvironment.STAGING);
        health.transition(
                StaffBotHealth.Phase.CONNECTING,
                "tab\tbackspace\bformfeed\fnewline\nreturn\rcontrol\u0001slash\\quote\"");

        try (StaffBotHealthServer server = new StaffBotHealthServer(loopbackEphemeralAddress(), health)) {
            server.start();
            HttpClient client = HttpClient.newHttpClient();
            URI base = URI.create("http://" + host(server.boundAddress()) + ":" + server.boundAddress().getPort());

            HttpResponse<String> response = get(client, base.resolve("/health"));

            assertEquals(200, response.statusCode());
            assertEquals(
                    "{\"environment\":\"staging\",\"status\":\"CONNECTING\",\"ready\":false,"
                            + "\"reason\":\"tab\\tbackspace\\bformfeed\\fnewline\\nreturn\\rcontrol\\u0001"
                            + "slash\\\\quote\\\"\",\"rejectedWork\":0}\n",
                    response.body());
        }
    }

    private static InetSocketAddress loopbackEphemeralAddress() {
        return new InetSocketAddress("127.0.0.1", 0);
    }

    private static HttpResponse<String> get(HttpClient client, URI uri) throws Exception {
        return client.send(
                HttpRequest.newBuilder(uri).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private static String host(InetSocketAddress address) {
        String host = address.getAddress().getHostAddress();
        return host.contains(":") ? "[" + host + "]" : host;
    }
}
