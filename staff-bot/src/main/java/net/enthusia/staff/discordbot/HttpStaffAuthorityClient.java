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

    private final URI endpoint;
    private final String authorizationHeader;
    private final HttpClient client;

    HttpStaffAuthorityClient(URI endpoint, String secret) {
        if (endpoint == null || secret == null || secret.isBlank()) {
            throw new IllegalArgumentException("authority endpoint and secret must be present");
        }
        this.endpoint = endpoint;
        this.authorizationHeader = "Bearer " + secret;
        this.client = HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build();
    }

    @Override
    public Optional<StaffRank> rank(UUID playerId) {
        if (playerId == null) {
            throw new IllegalArgumentException("playerId must be present");
        }
        String query = "player=" + URLEncoder.encode(playerId.toString(), StandardCharsets.UTF_8);
        URI requestUri = URI.create(endpoint + "?" + query);
        HttpRequest request = HttpRequest.newBuilder(requestUri)
                .timeout(REQUEST_TIMEOUT)
                .header("Authorization", authorizationHeader)
                .GET()
                .build();
        final HttpResponse<String> response;
        try {
            response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (IOException exception) {
            throw new UnavailableException("staff authority request failed", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new UnavailableException("staff authority request interrupted", exception);
        }
        if (response.statusCode() == 404) {
            return Optional.empty();
        }
        if (response.statusCode() != 200) {
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
