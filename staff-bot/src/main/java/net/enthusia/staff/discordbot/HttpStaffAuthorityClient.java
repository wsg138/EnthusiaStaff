package net.enthusia.staff.discordbot;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import net.enthusia.staff.domain.auth.StaffRank;

/** Loopback-only client for Paper's current LuckPerms-backed rank resolver. */
final class HttpStaffAuthorityClient implements StaffAuthorityClient {
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(2);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(3);
    private static final int HTTP_OK = 200;
    private static final int HTTP_NOT_FOUND = 404;

    private final URI endpoint;
    private final String authorizationHeader;
    private final HttpClient client;

    HttpStaffAuthorityClient(URI endpoint, String secret) {
        if (endpoint == null || secret == null || secret.isBlank()) {
            throw new IllegalArgumentException("authority endpoint and secret must be present");
        }
        this.endpoint = endpoint;
        this.authorizationHeader = "Bearer " + secret;
        // nosemgrep -- The client is only used with StaffModerationConfiguration's explicit loopback URI allowlist.
        this.client = HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build();
    }

    @Override
    public Optional<StaffRank> rank(UUID playerId) {
        if (playerId == null) {
            throw new IllegalArgumentException("playerId must be present");
        }
        String encodedPlayer = URLEncoder.encode(playerId.toString(), StandardCharsets.UTF_8);
        String requestText = new StringBuilder(endpoint.toASCIIString())
                .append("?player=")
                .append(encodedPlayer)
                .toString();
        // nosemgrep -- endpoint is startup-validated as an explicit loopback-only URI; the only dynamic value is a UUID.
        URI requestUri = URI.create(requestText);
        // nosemgrep -- Cleartext HTTP is confined to the authenticated loopback bridge and never leaves the host.
        HttpRequest request = HttpRequest.newBuilder(requestUri)
                .timeout(REQUEST_TIMEOUT)
                .header("Authorization", authorizationHeader)
                .GET()
                .build();
        final HttpResponse<String> response;
        try {
            // nosemgrep -- requestUri is constrained to the loopback authority endpoint before this client is constructed.
            response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (IOException exception) {
            throw new UnavailableException("staff authority request failed", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new UnavailableException("staff authority request interrupted", exception);
        }
        int status = response.statusCode();
        if (status == HTTP_NOT_FOUND) {
            return Optional.empty();
        }
        if (status != HTTP_OK) {
            throw new UnavailableException("staff authority request was not successful");
        }
        try {
            StaffRank rank = StaffRank.valueOf(response.body().trim());
            if (rank == StaffRank.SYSTEM) {
                throw new IllegalArgumentException("SYSTEM is not an interactive staff rank");
            }
            return Optional.of(rank);
        } catch (IllegalArgumentException exception) {
            throw new UnavailableException("staff authority response was invalid", exception);
        }
    }
}
