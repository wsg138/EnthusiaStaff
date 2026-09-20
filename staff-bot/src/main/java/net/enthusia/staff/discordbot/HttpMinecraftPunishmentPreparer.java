package net.enthusia.staff.discordbot;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import net.enthusia.staff.common.CaseId;
import net.enthusia.staff.domain.application.CreatePunishmentRequest;
import net.enthusia.staff.domain.application.MinecraftPunishmentPreparer;
import net.enthusia.staff.domain.application.PunishmentPlan;
import net.enthusia.staff.domain.application.PunishmentPreparation;
import net.enthusia.staff.protocol.MinecraftPunishmentPreparationCodec;
import net.enthusia.staff.protocol.MinecraftPunishmentPreparationMapper;
import net.enthusia.staff.protocol.MinecraftPunishmentPreparationWire;
import net.enthusia.staff.protocol.StaffAuthorityHttpSigning;

/** Signed D08 client for Paper's non-committing Minecraft punishment preparation endpoint. */
final class HttpMinecraftPunishmentPreparer implements MinecraftPunishmentPreparer {
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(2);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(4);
    private static final int NONCE_BYTES = 24;

    private final URI endpoint;
    private final String credential;
    private final StaffModerationConfiguration.AuthorityTransport transport;
    private final PrivateSplitAuthorityEndpointResolver privateResolver;
    private final Clock clock;
    private final SecureRandom random;
    private final HttpClient client;

    HttpMinecraftPunishmentPreparer(
            URI authorityEndpoint,
            String credential,
            StaffModerationConfiguration.AuthorityTransport transport
    ) {
        this(authorityEndpoint, credential, transport, new PrivateSplitAuthorityEndpointResolver(),
                Clock.systemUTC(), new SecureRandom());
    }

    HttpMinecraftPunishmentPreparer(
            URI authorityEndpoint,
            String credential,
            StaffModerationConfiguration.AuthorityTransport transport,
            PrivateSplitAuthorityEndpointResolver privateResolver,
            Clock clock,
            SecureRandom random
    ) {
        if (authorityEndpoint == null || credential == null || credential.isBlank() || transport == null
                || privateResolver == null || clock == null || random == null) {
            throw new IllegalArgumentException("Minecraft preparation client configuration must be present");
        }
        this.endpoint = authorityEndpoint.resolve(MinecraftPunishmentPreparationWire.PATH);
        this.credential = credential;
        this.transport = transport;
        this.privateResolver = privateResolver;
        this.clock = clock;
        this.random = random;
        this.client = HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build();
    }

    @Override
    public PunishmentPreparation prepareConfirmed(CreatePunishmentRequest request, CaseId caseId) {
        String body = MinecraftPunishmentPreparationCodec.encodeRequest(
                MinecraftPunishmentPreparationMapper.request(caseId, request)
        );
        RequestCall call = request(body);
        HttpResponse<String> response = send(call.request());
        verifySignedResponse(call, response);
        if (response.statusCode() != 200) {
            throw new StaffAuthorityClient.UnavailableException("Minecraft preparation request was not successful");
        }
        return decode(request, caseId, response.body());
    }

    private RequestCall request(String body) {
        URI resolved = transport == StaffModerationConfiguration.AuthorityTransport.BLOOM_PRIVATE_SPLIT
                ? privateResolver.resolve(endpoint) : endpoint;
        HttpRequest.Builder builder = HttpRequest.newBuilder(resolved)
                .timeout(REQUEST_TIMEOUT)
                .header("Content-Type", "application/json; charset=utf-8")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
        if (transport == StaffModerationConfiguration.AuthorityTransport.LOOPBACK) {
            return new RequestCall(builder.header("Authorization", "Bearer " + credential).build(), null);
        }
        String nonce = nonce();
        StaffAuthorityHttpSigning.RequestProof proof = StaffAuthorityHttpSigning.signBodyRequest(
                credential, "POST", MinecraftPunishmentPreparationWire.PATH, body, clock.instant(), nonce
        );
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
            throw new StaffAuthorityClient.UnavailableException("Minecraft preparation request failed", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new StaffAuthorityClient.UnavailableException("Minecraft preparation request interrupted", exception);
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
                credential, call.nonce(), response.statusCode(), response.body(), signature)) {
            throw new StaffAuthorityClient.UnavailableException("Minecraft preparation response authentication failed");
        }
    }

    private static PunishmentPreparation decode(
            CreatePunishmentRequest request,
            CaseId caseId,
            String body
    ) {
        MinecraftPunishmentPreparationWire.Response response = MinecraftPunishmentPreparationCodec.decodeResponse(body);
        if (response.outcome() == MinecraftPunishmentPreparationWire.Outcome.REJECTED) {
            return new PunishmentPreparation.Rejected(response.code(), response.message());
        }
        PunishmentPlan plan = MinecraftPunishmentPreparationMapper.plan(response.plan());
        requireExpected(request, caseId, plan);
        return new PunishmentPreparation.Prepared(plan);
    }

    private static void requireExpected(CreatePunishmentRequest request, CaseId caseId, PunishmentPlan plan) {
        boolean matches = plan.caseId().equals(caseId)
                && plan.idempotencyKey().equals(request.idempotencyKey())
                && plan.targetId().equals(request.targetId())
                && plan.actor().id().equals(request.actor().id())
                && plan.internalExplanation().equals(request.internalExplanation())
                && plan.visibility() == request.visibility();
        if (!matches) {
            throw new StaffAuthorityClient.UnavailableException("Minecraft preparation response did not match the request");
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
