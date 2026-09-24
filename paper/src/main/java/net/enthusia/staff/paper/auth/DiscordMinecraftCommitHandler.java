package net.enthusia.staff.paper.auth;

import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;
import net.enthusia.staff.common.CaseId;
import net.enthusia.staff.domain.OperationalMode;
import net.enthusia.staff.domain.application.CreatePunishmentRequest;
import net.enthusia.staff.domain.application.PunishmentExpectation;
import net.enthusia.staff.domain.application.PunishmentResult;
import net.enthusia.staff.domain.application.PunishmentService;
import net.enthusia.staff.domain.auth.Actor;
import net.enthusia.staff.domain.auth.StaffRank;
import net.enthusia.staff.protocol.MinecraftPunishmentCommitMapper;
import net.enthusia.staff.protocol.MinecraftPunishmentCommitWire;
import net.enthusia.staff.protocol.MinecraftPunishmentPreparationMapper;
import net.enthusia.staff.protocol.MinecraftPunishmentPreparationWire;
import net.enthusia.staff.protocol.MinecraftPunishmentWireCodec;
import net.enthusia.staff.protocol.StaffAuthorityHttpSigning;

/** Authenticated D08 bridge that revalidates and commits one Minecraft punishment. */
final class DiscordMinecraftCommitHandler {
    private static final String POST = "POST";
    private static final int HTTP_OK = 200;

    private final DiscordStaffAuthorityAuthenticator authenticator;
    private final Function<UUID, Optional<StaffRank>> ranks;
    private final Supplier<PunishmentService> punishments;
    private final Supplier<OperationalMode> mode;

    DiscordMinecraftCommitHandler(
            DiscordStaffAuthorityAuthenticator authenticator,
            Function<UUID, Optional<StaffRank>> ranks,
            Supplier<PunishmentService> punishments,
            Supplier<OperationalMode> mode
    ) {
        if (authenticator == null || ranks == null || punishments == null || mode == null) {
            throw new IllegalArgumentException("Minecraft commit handler dependencies must be present");
        }
        this.authenticator = authenticator;
        this.ranks = ranks;
        this.punishments = punishments;
        this.mode = mode;
    }

    void handle(HttpExchange exchange) throws IOException {
        DiscordStaffAuthorityAuthenticator.Result authorization = null;
        try {
            if (!POST.equals(exchange.getRequestMethod())) {
                respond(exchange, 405, "", null);
                return;
            }
            String body = requestBody(exchange);
            if (body == null) {
                respond(exchange, 413, "", null);
                return;
            }
            authorization = authenticate(exchange, body);
            if (!authorization.accepted()) {
                respond(exchange, 401, "", null);
                return;
            }
            String response = MinecraftPunishmentWireCodec.encodeCommitResponse(commit(body));
            respond(exchange, HTTP_OK, response, authorization);
        } catch (IllegalArgumentException exception) {
            respond(exchange, 400, "", authorization);
        } catch (RuntimeException exception) {
            respond(exchange, 503, "", authorization);
        } finally {
            exchange.close();
        }
    }

    private MinecraftPunishmentCommitWire.Response commit(String body) {
        MinecraftPunishmentCommitWire.Request wire = MinecraftPunishmentWireCodec.decodeCommitRequest(body);
        StaffRank rank = ranks.apply(wire.punishment().actorId()).orElse(null);
        if (rank == null) {
            return MinecraftPunishmentCommitWire.Response.rejected(
                    "FORBIDDEN", "Current Minecraft staff authority is required");
        }
        PunishmentService service = punishments.get();
        OperationalMode currentMode = mode.get();
        if (service == null || currentMode == null) {
            throw new IllegalStateException("Minecraft punishment service is unavailable");
        }
        Actor actor = new Actor(wire.punishment().actorId(), wire.punishment().actorName(), rank);
        CreatePunishmentRequest request = MinecraftPunishmentPreparationMapper.request(wire.punishment(), actor);
        PunishmentExpectation expectation = MinecraftPunishmentCommitMapper.expectation(wire.expectation());
        PunishmentResult result = service.createConfirmed(
                request, currentMode, expectation, new CaseId(wire.punishment().caseId()));
        return MinecraftPunishmentCommitMapper.response(result);
    }

    private DiscordStaffAuthorityAuthenticator.Result authenticate(HttpExchange exchange, String body) {
        return authenticator.authenticateBody(
                exchange.getRemoteAddress().getAddress(), exchange.getRequestMethod(),
                exchange.getRequestURI().getRawPath(), body,
                exchange.getRequestHeaders().getFirst("Authorization"),
                exchange.getRequestHeaders().getFirst(StaffAuthorityHttpSigning.TIMESTAMP_HEADER),
                exchange.getRequestHeaders().getFirst(StaffAuthorityHttpSigning.NONCE_HEADER),
                exchange.getRequestHeaders().getFirst(StaffAuthorityHttpSigning.SIGNATURE_HEADER)
        );
    }

    private static String requestBody(HttpExchange exchange) throws IOException {
        byte[] encoded = exchange.getRequestBody().readNBytes(MinecraftPunishmentPreparationWire.MAX_BODY_BYTES + 1);
        return encoded.length > MinecraftPunishmentPreparationWire.MAX_BODY_BYTES
                ? null : new String(encoded, StandardCharsets.UTF_8);
    }

    private void respond(
            HttpExchange exchange,
            int status,
            String body,
            DiscordStaffAuthorityAuthenticator.Result authorization
    ) throws IOException {
        byte[] encoded = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        if (authorization != null) {
            String signature = authenticator.responseSignature(authorization, status, body);
            if (signature != null) {
                exchange.getResponseHeaders().set(StaffAuthorityHttpSigning.RESPONSE_SIGNATURE_HEADER, signature);
            }
        }
        exchange.sendResponseHeaders(status, encoded.length);
        if (encoded.length > 0) {
            exchange.getResponseBody().write(encoded);
        }
    }
}
