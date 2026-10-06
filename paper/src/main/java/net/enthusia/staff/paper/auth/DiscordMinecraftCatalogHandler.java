package net.enthusia.staff.paper.auth;

import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;
import net.enthusia.staff.domain.application.PunishmentService;
import net.enthusia.staff.domain.auth.Actor;
import net.enthusia.staff.domain.auth.StaffRank;
import net.enthusia.staff.protocol.MinecraftPunishmentCatalogMapper;
import net.enthusia.staff.protocol.MinecraftPunishmentCatalogWire;
import net.enthusia.staff.protocol.MinecraftPunishmentPreparationWire;
import net.enthusia.staff.protocol.MinecraftPunishmentWireCodec;
import net.enthusia.staff.protocol.StaffAuthorityHttpSigning;

/** Authenticated D08 bridge for public-safe configured Minecraft reason metadata. */
final class DiscordMinecraftCatalogHandler {
    private static final String POST = "POST";

    private final DiscordStaffAuthorityAuthenticator authenticator;
    private final Function<UUID, Optional<StaffRank>> ranks;
    private final Supplier<PunishmentService> punishments;

    DiscordMinecraftCatalogHandler(
            DiscordStaffAuthorityAuthenticator authenticator,
            Function<UUID, Optional<StaffRank>> ranks,
            Supplier<PunishmentService> punishments
    ) {
        if (authenticator == null || ranks == null || punishments == null) {
            throw new IllegalArgumentException("Minecraft catalog handler dependencies must be present");
        }
        this.authenticator = authenticator;
        this.ranks = ranks;
        this.punishments = punishments;
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
            String response = MinecraftPunishmentWireCodec.encodeCatalogResponse(catalog(body));
            respond(exchange, 200, response, authorization);
        } catch (IllegalArgumentException exception) {
            respond(exchange, 400, "", authorization);
        } catch (RuntimeException exception) {
            respond(exchange, 503, "", authorization);
        } finally {
            exchange.close();
        }
    }

    private MinecraftPunishmentCatalogWire.Response catalog(String body) {
        MinecraftPunishmentCatalogWire.Request wire = MinecraftPunishmentWireCodec.decodeCatalogRequest(body);
        StaffRank rank = ranks.apply(wire.actorId()).orElse(null);
        if (rank == null) {
            return MinecraftPunishmentCatalogWire.Response.rejected(
                    "FORBIDDEN", "Current Minecraft staff authority is required");
        }
        PunishmentService service = punishments.get();
        if (service == null) {
            throw new IllegalStateException("Minecraft punishment service is unavailable");
        }
        return MinecraftPunishmentCatalogMapper.response(
                service.availableReasons(new Actor(wire.actorId(), wire.actorName(), rank)));
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
