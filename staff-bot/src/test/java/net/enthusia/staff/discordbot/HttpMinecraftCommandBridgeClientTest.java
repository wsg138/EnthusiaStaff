package net.enthusia.staff.discordbot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import net.enthusia.staff.domain.commandbridge.CommandBridgeOutcome;
import net.enthusia.staff.domain.commandbridge.CommandBridgeRequest;
import net.enthusia.staff.domain.commandbridge.CommandBridgeResponse;
import net.enthusia.staff.domain.moderation.DiscordUserId;
import net.enthusia.staff.domain.moderation.ModerationSubjectId;
import net.enthusia.staff.protocol.CommandBridgeHttpSigning;
import net.enthusia.staff.protocol.CommandBridgeWireCodec;
import org.junit.jupiter.api.Test;

class HttpMinecraftCommandBridgeClientTest {
    private static final String CREDENTIAL = "command-bridge-test-secret";
    private static final String NONCE = "0123456789abcdef0123456789abcdef";
    private static final Instant NOW = Instant.parse("2026-09-30T19:00:00Z");
    private static final DiscordUserId DISCORD_ID = new DiscordUserId("123456789012345678");
    private static final String TARGET_SERVER = "smp";
    private static final URI ENDPOINT = URI.create("http://127.0.0.1:8772/v1/discord-command");
    private static final CommandBridgeWireCodec CODEC = new CommandBridgeWireCodec();

    @Test
    void acceptsOnlyExplicitServerAndSignedResponse() {
        AtomicInteger sends = new AtomicInteger();
        HttpMinecraftCommandBridgeClient client = client(request -> {
            sends.incrementAndGet();
            String nonce = header(request, CommandBridgeHttpSigning.NONCE_HEADER);
            String body = CODEC.encodeResponse(CommandBridgeResponse.withoutOutput(
                    CommandBridgeOutcome.SUCCESS, "Command executed."));
            String signature = CommandBridgeHttpSigning.signResponse(CREDENTIAL, nonce, 200, body);
            return new HttpMinecraftCommandBridgeClient.RawResponse(200, body, signature);
        });

        HttpMinecraftCommandBridgeClient.Result result = client.dispatch(request(TARGET_SERVER));
        assertEquals(HttpMinecraftCommandBridgeClient.Status.RECEIVED, result.status());
        assertEquals(CommandBridgeOutcome.SUCCESS, result.response().orElseThrow().outcome());
        assertEquals(1, sends.get());

        assertEquals(HttpMinecraftCommandBridgeClient.Status.INVALID_SERVER,
                client.dispatch(request("events")).status());
        assertEquals(1, sends.get());
    }

    @Test
    void endpointUnavailableIsSingleAttempt() {
        AtomicInteger sends = new AtomicInteger();
        HttpMinecraftCommandBridgeClient client = client(request -> {
            sends.incrementAndGet();
            throw new ConnectException("not listening");
        });

        assertEquals(HttpMinecraftCommandBridgeClient.Status.ENDPOINT_UNAVAILABLE,
                client.dispatch(request(TARGET_SERVER)).status());
        assertEquals(1, sends.get());
    }

    @Test
    void ambiguousPostDispatchFailureIsNeverBlindlyRetried() {
        AtomicInteger sends = new AtomicInteger();
        HttpMinecraftCommandBridgeClient client = client(request -> {
            sends.incrementAndGet();
            throw new IOException("connection closed after request body may have been sent");
        });

        assertEquals(HttpMinecraftCommandBridgeClient.Status.AMBIGUOUS_FAILURE,
                client.dispatch(request(TARGET_SERVER)).status());
        assertEquals(1, sends.get());
    }

    @Test
    void badOrTamperedResponseSignatureIsRejected() {
        HttpMinecraftCommandBridgeClient client = client(request -> {
            String body = CODEC.encodeResponse(CommandBridgeResponse.withoutOutput(
                    CommandBridgeOutcome.SUCCESS, "Command executed."));
            return new HttpMinecraftCommandBridgeClient.RawResponse(
                    200,
                    body + " ",
                    CommandBridgeHttpSigning.signResponse(CREDENTIAL, NONCE, 200, body)
            );
        });

        assertEquals(HttpMinecraftCommandBridgeClient.Status.INVALID_RESPONSE,
                client.dispatch(request(TARGET_SERVER)).status());
    }

    @Test
    void requestCarriesBodyBoundSignatureWithoutAuthorizationHeader() {
        HttpMinecraftCommandBridgeClient client = client(request -> {
            assertTrue(request.headers().firstValue("Authorization").isEmpty());
            assertTrue(request.headers().firstValue(CommandBridgeHttpSigning.SIGNATURE_HEADER).isPresent());
            throw new ConnectException("test complete");
        });
        client.dispatch(request(TARGET_SERVER));
    }

    private static HttpMinecraftCommandBridgeClient client(
            HttpMinecraftCommandBridgeClient.RawHttpSender sender
    ) {
        return new HttpMinecraftCommandBridgeClient(
                Map.of(TARGET_SERVER, ENDPOINT),
                CREDENTIAL,
                Duration.ofSeconds(2),
                Clock.fixed(NOW, ZoneOffset.UTC),
                () -> NONCE,
                sender
        );
    }

    private static CommandBridgeRequest request(String server) {
        return new CommandBridgeRequest(
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                new ModerationSubjectId(UUID.fromString("22222222-2222-2222-2222-222222222222")),
                DISCORD_ID,
                UUID.fromString("33333333-3333-3333-3333-333333333333"),
                server,
                "list",
                NOW
        );
    }

    private static String header(HttpRequest request, String name) {
        return request.headers().firstValue(name).orElseThrow();
    }
}
