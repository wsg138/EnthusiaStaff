package net.enthusia.staff.paper.auth;

import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;
import net.enthusia.staff.common.CaseId;
import net.enthusia.staff.domain.OperationalMode;
import net.enthusia.staff.domain.application.CreatePunishmentRequest;
import net.enthusia.staff.domain.application.PunishmentPreparation;
import net.enthusia.staff.domain.application.PunishmentService;
import net.enthusia.staff.domain.auth.Actor;
import net.enthusia.staff.domain.auth.StaffRank;
import net.enthusia.staff.protocol.MinecraftPunishmentWireCodec;
import net.enthusia.staff.protocol.MinecraftPunishmentPreparationMapper;
import net.enthusia.staff.protocol.MinecraftPunishmentPreparationWire;
import net.enthusia.staff.protocol.StaffAuthorityHttpSigning;

/** Bounded, authenticated D08 bridge that prepares but never commits Minecraft punishment state. */
final class DiscordMinecraftPreparationHandler {
    private static final String POST = "POST";
    private static final int HTTP_OK = 200;

    private final DiscordStaffAuthorityAuthenticator authenticator;
    private final Function<UUID, Optional<StaffRank>> ranks;
    private final Supplier<PunishmentService> punishments;
    private final Supplier<OperationalMode> mode;
    private final Clock clock;

    DiscordMinecraftPreparationHandler(
            DiscordStaffAuthorityAuthenticator authenticator,
            Function<UUID, Optional<StaffRank>> ranks,
            Supplier<PunishmentService> punishments,
            Supplier<OperationalMode> mode,
            Clock clock
    ) {
        if (authenticator == null || ranks == null || punishments == null || mode == null || clock == null) {
            throw new IllegalArgumentException("Minecraft preparation handler dependencies must be present");
        }
        this.authenticator = authenticator;
        this.ranks = ranks;
        this.punishments = punishments;
        this.mode = mode;
        this.clock = clock;
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
            MinecraftPunishmentPreparationWire.Response response = prepare(body);
            respond(exchange, HTTP_OK, MinecraftPunishmentWireCodec.encodeResponse(response), authorization);
        } catch (IllegalArgumentException exception) {
            respond(exchange, 400, "", authorization);
        } catch (RuntimeException exception) {
            respond(exchange, 503, "", authorization);
        } finally {
            exchange.close();
        }
    }

    private MinecraftPunishmentPreparationWire.Response prepare(String body) {
        MinecraftPunishmentPreparationWire.Request wire = MinecraftPunishmentWireCodec.decodeRequest(body);
        StaffRank rank = ranks.apply(wire.actorId()).orElse(null);
        if (rank == null) {
            return MinecraftPunishmentPreparationWire.Response.rejected("FORBIDDEN", "Current Minecraft staff authority is required");
        }
        PunishmentService service = punishments.get();
        OperationalMode currentMode = mode.get();
        if (service == null || currentMode == null) {
            throw new IllegalStateException("Minecraft punishment service is unavailable");
        }
        Actor actor = new Actor(wire.actorId(), wire.actorName(), rank);
        CreatePunishmentRequest request = MinecraftPunishmentPreparationMapper.request(wire, actor);
        PunishmentPreparation prepared = service.prepareConfirmed(
                request, currentMode, new CaseId(wire.caseId()), clock.instant()
        );
        if (prepared instanceof PunishmentPreparation.Rejected rejected) {
            return MinecraftPunishmentPreparationWire.Response.rejected(rejected.code(), rejected.message());
        }
        return MinecraftPunishmentPreparationWire.Response.prepared(
                MinecraftPunishmentPreparationMapper.plan(((PunishmentPreparation.Prepared) prepared).plan())
        );
    }

    private DiscordStaffAuthorityAuthenticator.Result authenticate(HttpExchange exchange, String body) {
        String target = exchange.getRequestURI().getRawPath();
        return authenticator.authenticateBody(
                exchange.getRemoteAddress().getAddress(), exchange.getRequestMethod(), target, body,
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
