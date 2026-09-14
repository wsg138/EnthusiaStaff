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
import java.util.Optional;
import java.util.UUID;
import net.enthusia.staff.domain.auth.StaffRank;
import net.enthusia.staff.protocol.StaffAuthorityHttpSigning;

/** Authenticated client for Paper's current LuckPerms-backed staff-rank resolver. */
final class HttpStaffAuthorityClient implements StaffAuthorityClient {
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(2);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(3);
    private static final int HTTP_OK = 200;
    private static final int HTTP_NOT_FOUND = 404;
    private static final int NONCE_BYTES = 24;

    private final URI endpoint;
    private final String credential;
    private final StaffModerationConfiguration.AuthorityTransport transport;
    private final PrivateSplitAuthorityEndpointResolver privateResolver;
    private final Clock clock;
    private final SecureRandom random;
    private final HttpClient client;

    HttpStaffAuthorityClient(
            URI endpoint,
            String credential,
            StaffModerationConfiguration.AuthorityTransport transport
    ) {
        this(
                endpoint,
                credential,
                transport,
                new PrivateSplitAuthorityEndpointResolver(),
                Clock.systemUTC(),
                new SecureRandom()
        );
    }

    HttpStaffAuthorityClient(
            URI endpoint,
            String credential,
            StaffModerationConfiguration.AuthorityTransport transport,
            PrivateSplitAuthorityEndpointResolver privateResolver,
            Clock clock,
            SecureRandom random
    ) {
        if (endpoint == null || credential == null || credential.isBlank()
                || transport == null || privateResolver == null || clock == null || random == null) {
            throw new IllegalArgumentException("authority client configuration must be present");
        }
        this.endpoint = endpoint;
        this.credential = credential;
        this.transport = transport;
        this.privateResolver = privateResolver;
        this.clock = clock;
        this.random = random;
        this.client = HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build();
    }

    @Override
    public Optional<StaffRank> rank(UUID playerId) {
        if (playerId == null) {
            throw new IllegalArgumentException("playerId must be present");
        }
        RequestCall call = request(playerId);
        HttpResponse<String> response = send(call.request());
        verifySignedResponse(call, response);
        return decode(response);
    }

    private RequestCall request(UUID playerId) {
        String encodedPlayer = URLEncoder.encode(playerId.toString(), StandardCharsets.UTF_8);
        String target = endpoint.getRawPath() + "?player=" + encodedPlayer;
        URI requestEndpoint = transport == StaffModerationConfiguration.AuthorityTransport.BLOOM_PRIVATE_SPLIT
                ? privateResolver.resolve(endpoint)
                : endpoint;
        URI requestUri = requestEndpoint.resolve(target);
        HttpRequest.Builder builder = HttpRequest.newBuilder(requestUri)
                .timeout(REQUEST_TIMEOUT)
                .GET();
        if (transport == StaffModerationConfiguration.AuthorityTransport.LOOPBACK) {
            return new RequestCall(
                    builder.header("Authorization", "Bearer " + credential).build(),
                    null
            );
        }
        String nonce = nonce();
        StaffAuthorityHttpSigning.RequestProof proof =
                StaffAuthorityHttpSigning.signRequest(credential, "GET", target, clock.instant(), nonce);
        return new RequestCall(
                builder.header(StaffAuthorityHttpSigning.TIMESTAMP_HEADER, proof.timestamp())
                        .header(StaffAuthorityHttpSigning.NONCE_HEADER, proof.nonce())
                        .header(StaffAuthorityHttpSigning.SIGNATURE_HEADER, proof.signature())
                        .build(),
                nonce
        );
    }

    private HttpResponse<String> send(HttpRequest request) {
        try {
            return client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (IOException exception) {
            throw new UnavailableException("staff authority request failed", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new UnavailableException("staff authority request interrupted", exception);
        }
    }

    private void verifySignedResponse(RequestCall call, HttpResponse<String> response) {
        if (call.nonce() == null) {
            return;
        }
        String signature = response.headers()
                .firstValue(StaffAuthorityHttpSigning.RESPONSE_SIGNATURE_HEADER)
                .orElse(null);
        if (!StaffAuthorityHttpSigning.verifyResponse(
                credential,
                call.nonce(),
                response.statusCode(),
                response.body(),
                signature
        )) {
            throw new UnavailableException("staff authority response authentication failed");
        }
    }

    private static Optional<StaffRank> decode(HttpResponse<String> response) {
        if (response.statusCode() == HTTP_NOT_FOUND) {
            return Optional.empty();
        }
        if (response.statusCode() != HTTP_OK) {
            throw new UnavailableException("staff authority request was not successful");
        }
        return Optional.of(parseRank(response.body()));
    }

    private static StaffRank parseRank(String body) {
        try {
            StaffRank rank = StaffRank.valueOf(body.trim());
            if (rank == StaffRank.SYSTEM) {
                throw new IllegalArgumentException("SYSTEM is not an interactive staff rank");
            }
            return rank;
        } catch (IllegalArgumentException exception) {
            throw new UnavailableException("staff authority response was invalid", exception);
        }
    }

    private String nonce() {
        byte[] bytes = new byte[NONCE_BYTES];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private record RequestCall(HttpRequest request, String nonce) {
    }
}
