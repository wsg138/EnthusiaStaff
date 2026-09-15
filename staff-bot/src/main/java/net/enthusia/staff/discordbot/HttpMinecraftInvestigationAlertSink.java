package net.enthusia.staff.discordbot;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import java.util.UUID;
import net.enthusia.staff.domain.investigation.EvasionAlert;
import net.enthusia.staff.protocol.StaffAuthorityHttpSigning;

/** Sends only a generic alert identifier across the authenticated private Paper bridge. */
final class HttpMinecraftInvestigationAlertSink implements DiscordInvestigationAlertSink {
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(2);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(3);
    private static final String PATH = "/v1/staff-alert";
    private static final int HTTP_ACCEPTED = 202;
    private static final int NONCE_BYTES = 24;

    private final URI authorityEndpoint;
    private final String credential;
    private final StaffModerationConfiguration.AuthorityTransport transport;
    private final PrivateSplitAuthorityEndpointResolver privateResolver;
    private final Clock clock;
    private final SecureRandom random;
    private final HttpClient client;

    HttpMinecraftInvestigationAlertSink(
            URI authorityEndpoint,
            String credential,
            StaffModerationConfiguration.AuthorityTransport transport
    ) {
        this(authorityEndpoint, credential, transport, new PrivateSplitAuthorityEndpointResolver(),
                Clock.systemUTC(), new SecureRandom());
    }

    HttpMinecraftInvestigationAlertSink(
            URI authorityEndpoint,
            String credential,
            StaffModerationConfiguration.AuthorityTransport transport,
            PrivateSplitAuthorityEndpointResolver privateResolver,
            Clock clock,
            SecureRandom random
    ) {
        if (authorityEndpoint == null || credential == null || credential.isBlank() || transport == null
                || privateResolver == null || clock == null || random == null) {
            throw new IllegalArgumentException("Minecraft investigation alert client configuration is invalid");
        }
        this.authorityEndpoint = authorityEndpoint;
        this.credential = credential;
        this.transport = transport;
        this.privateResolver = privateResolver;
        this.clock = clock;
        this.random = random;
        this.client = HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build();
    }

    @Override
    public Delivery deliver(EvasionAlert alert) {
        if (alert == null) {
            throw new IllegalArgumentException("alert must be present");
        }
        RequestCall call = request(alert.alertId());
        try {
            HttpResponse<String> response = client.send(
                    call.request(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (!validResponse(call, response)) {
                return Delivery.retry("MINECRAFT_ALERT_AUTH_FAILED");
            }
            return response.statusCode() == HTTP_ACCEPTED
                    ? Delivery.success() : Delivery.retry("MINECRAFT_ALERT_REJECTED");
        } catch (IOException exception) {
            return Delivery.retry("MINECRAFT_ALERT_UNAVAILABLE");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return Delivery.retry("MINECRAFT_ALERT_INTERRUPTED");
        }
    }

    private RequestCall request(UUID alertId) {
        String target = PATH + "?alert=" + URLEncoder.encode(alertId.toString(), StandardCharsets.UTF_8);
        URI endpoint = transport == StaffModerationConfiguration.AuthorityTransport.BLOOM_PRIVATE_SPLIT
                ? privateResolver.resolve(authorityEndpoint) : authorityEndpoint;
        HttpRequest.Builder builder = HttpRequest.newBuilder(endpoint.resolve(target))
                .timeout(REQUEST_TIMEOUT)
                .POST(HttpRequest.BodyPublishers.noBody());
        if (transport == StaffModerationConfiguration.AuthorityTransport.LOOPBACK) {
            return new RequestCall(builder.header("Authorization", "Bearer " + credential).build(), null);
        }
        String nonce = nonce();
        StaffAuthorityHttpSigning.RequestProof proof = StaffAuthorityHttpSigning.signRequest(
                credential, "POST", target, clock.instant(), nonce);
        return new RequestCall(builder.header(StaffAuthorityHttpSigning.TIMESTAMP_HEADER, proof.timestamp())
                .header(StaffAuthorityHttpSigning.NONCE_HEADER, proof.nonce())
                .header(StaffAuthorityHttpSigning.SIGNATURE_HEADER, proof.signature())
                .build(), nonce);
    }

    private boolean validResponse(RequestCall call, HttpResponse<String> response) {
        if (call.nonce() == null) {
            return true;
        }
        String signature = response.headers()
                .firstValue(StaffAuthorityHttpSigning.RESPONSE_SIGNATURE_HEADER)
                .orElse(null);
        return StaffAuthorityHttpSigning.verifyResponse(
                credential, call.nonce(), response.statusCode(), response.body(), signature);
    }

    private String nonce() {
        byte[] bytes = new byte[NONCE_BYTES];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private record RequestCall(HttpRequest request, String nonce) {
    }
}
