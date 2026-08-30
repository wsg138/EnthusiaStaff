package net.enthusia.staff.discordbot;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class ModerationPreviewWebRuntimeTest {
    private static final Pattern CSRF_PATTERN = Pattern.compile("\\\"csrfToken\\\":\\\"([^\\\"]+)\\\"");

    @Test
    void launchExchangeProtectsWorkspaceRejectsReplayAndSimulatesWithoutSideEffects() throws Exception {
        var config = loopbackConfig();
        var tickets = new ModerationPreviewLaunchTicketService(8, Duration.ofMinutes(2));
        var sessions = new ModerationPreviewWebSessionStore(8, Duration.ofMinutes(15));

        try (var runtime = new ModerationPreviewWebRuntime(config, tickets, sessions)) {
            runtime.start();
            URI origin = origin(runtime.boundAddress());
            HttpClient client = HttpClient.newBuilder()
                    .followRedirects(HttpClient.Redirect.NEVER)
                    .build();

            assertEquals(401, get(client, origin.resolve("/moderation"), null).statusCode());
            assertEquals(401, get(client, origin.resolve("/launch?t=%25"), null).statusCode());

            String token = tickets.issue(1234L, 5678L, "sample-river-ash");
            URI launch = origin.resolve("/launch?t=" + URLEncoder.encode(token, StandardCharsets.UTF_8));
            HttpResponse<String> launched = get(client, launch, null);
            assertEquals(303, launched.statusCode());
            String cookie = cookie(launched);

            assertEquals(401, get(client, launch, null).statusCode());

            HttpResponse<String> page = get(client, origin.resolve("/moderation"), cookie);
            assertEquals(200, page.statusCode());
            assertTrue(page.body().contains("STAGING PREVIEW"));

            HttpResponse<String> session = get(client, origin.resolve("/api/session"), cookie);
            assertEquals(200, session.statusCode());
            assertTrue(session.body().contains("\"actorId\":\"1234\""));
            assertTrue(session.body().contains("\"guildId\":\"5678\""));
            assertTrue(session.body().contains("\"targetKey\":\"sample-river-ash\""));
            String csrf = csrf(session.body());

            assertEquals(403, postSimulation(client, origin, cookie, null, "{}").statusCode());
            String destructiveLooking = "{\"action\":\"Ban\",\"delete\":[\"19002\"],\"duration\":\"Permanent\"}";
            HttpResponse<String> simulated = postSimulation(client, origin, cookie, csrf, destructiveLooking);
            assertEquals(200, simulated.statusCode());
            assertTrue(simulated.body().contains("Simulation complete"));
            assertTrue(simulated.body().contains("No live moderation action was performed."));
            assertFalse(simulated.body().contains("Ban"));
            assertFalse(simulated.body().contains("19002"));
        }
    }

    @Test
    void runtimeCannotStartAfterClose() {
        var runtime = runtime();

        runtime.close();

        assertThrows(IllegalStateException.class, runtime::start);
    }

    @Test
    void concurrentStartAndCloseCannotLeaveRuntimeReachable() throws Exception {
        var runtime = runtime();
        var start = new CountDownLatch(1);
        var startFailure = new AtomicReference<RuntimeException>();
        Thread starter = Thread.ofPlatform().start(() -> runStart(runtime, start, startFailure));
        Thread closer = Thread.ofPlatform().start(() -> runClose(runtime, start));

        start.countDown();
        starter.join();
        closer.join();

        RuntimeException failure = startFailure.get();
        assertTrue(failure == null || failure instanceof IllegalStateException);
        assertThrows(IllegalStateException.class, runtime::boundAddress);
    }

    @Test
    void runtimeConstructorAcceptsOnlyPreviewInfrastructureDependencies() {
        var constructors = ModerationPreviewWebRuntime.class.getDeclaredConstructors();

        assertEquals(1, constructors.length);
        assertArrayEquals(new Class<?>[] {
                ModerationPreviewWebConfig.class,
                ModerationPreviewLaunchTicketService.class,
                ModerationPreviewWebSessionStore.class
        }, constructors[0].getParameterTypes());
    }

    private static ModerationPreviewWebRuntime runtime() {
        return new ModerationPreviewWebRuntime(
                loopbackConfig(),
                new ModerationPreviewLaunchTicketService(2, Duration.ofMinutes(2)),
                new ModerationPreviewWebSessionStore(2, Duration.ofMinutes(15)));
    }

    private static ModerationPreviewWebConfig loopbackConfig() {
        return new ModerationPreviewWebConfig(new InetSocketAddress("127.0.0.1", 0), Optional.empty());
    }

    private static void runStart(
            ModerationPreviewWebRuntime runtime,
            CountDownLatch start,
            AtomicReference<RuntimeException> failure
    ) {
        await(start);
        try {
            runtime.start();
        } catch (RuntimeException exception) {
            failure.set(exception);
        }
    }

    private static void runClose(ModerationPreviewWebRuntime runtime, CountDownLatch start) {
        await(start);
        runtime.close();
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }

    private static HttpResponse<String> get(HttpClient client, URI uri, String cookie) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(uri).GET();
        if (cookie != null) {
            request.header("Cookie", cookie);
        }
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static HttpResponse<String> postSimulation(
            HttpClient client,
            URI origin,
            String cookie,
            String csrf,
            String body
    ) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(origin.resolve("/api/simulate"))
                .header("Cookie", cookie)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body));
        if (csrf != null) {
            request.header("X-Preview-Csrf", csrf);
        }
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static URI origin(InetSocketAddress address) {
        return URI.create("http://127.0.0.1:" + address.getPort());
    }

    private static String cookie(HttpResponse<String> response) {
        String setCookie = response.headers().firstValue("Set-Cookie").orElseThrow();
        return setCookie.substring(0, setCookie.indexOf(';'));
    }

    private static String csrf(String json) {
        Matcher matcher = CSRF_PATTERN.matcher(json);
        assertTrue(matcher.find());
        return matcher.group(1);
    }
}
