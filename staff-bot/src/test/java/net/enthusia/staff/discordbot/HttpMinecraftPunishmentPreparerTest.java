package net.enthusia.staff.discordbot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import net.enthusia.staff.common.CaseId;
import net.enthusia.staff.common.IdempotencyKey;
import net.enthusia.staff.domain.application.CreatePunishmentRequest;
import net.enthusia.staff.domain.application.PunishmentPlan;
import net.enthusia.staff.domain.application.PunishmentPreparation;
import net.enthusia.staff.domain.auth.Actor;
import net.enthusia.staff.domain.auth.StaffRank;
import net.enthusia.staff.domain.casefile.CaseVisibility;
import net.enthusia.staff.domain.escalation.DecayEligibility;
import net.enthusia.staff.domain.escalation.EscalationDecision;
import net.enthusia.staff.domain.escalation.PunishmentStep;
import net.enthusia.staff.domain.sanction.SanctionLength;
import net.enthusia.staff.domain.sanction.SanctionSpec;
import net.enthusia.staff.domain.sanction.SanctionType;
import net.enthusia.staff.protocol.MinecraftPunishmentPreparationCodec;
import net.enthusia.staff.protocol.MinecraftPunishmentPreparationMapper;
import net.enthusia.staff.protocol.MinecraftPunishmentPreparationWire;
import net.enthusia.staff.protocol.StaffAuthorityHttpSigning;
import org.junit.jupiter.api.Test;

class HttpMinecraftPunishmentPreparerTest {
    private static final String CREDENTIAL = Character.toString('m').repeat(40);
    private static final Instant NOW = Instant.parse("2026-09-20T03:00:00Z");
    private static final CaseId CASE_ID = new CaseId("0123456789ABCDEF");
    private static final UUID ACTOR_ID = UUID.fromString("30000000-0000-0000-0000-000000000008");
    private static final UUID TARGET_ID = UUID.fromString("10000000-0000-0000-0000-000000000008");
    private static final SanctionSpec MUTE = new SanctionSpec(
            SanctionType.MUTE, SanctionLength.temporary(Duration.ofHours(1)));

    @Test
    void privateSplitBindsBodyAndDecodesFreshPreparedAuthority() throws IOException {
        AtomicReference<StaffAuthorityHttpSigning.Verification> verification = new AtomicReference<>();
        HttpServer server = server(exchange -> {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            String nonce = exchange.getRequestHeaders().getFirst(StaffAuthorityHttpSigning.NONCE_HEADER);
            verification.set(StaffAuthorityHttpSigning.verifyBodyRequest(
                    CREDENTIAL, exchange.getRequestMethod(), MinecraftPunishmentPreparationWire.PATH, body,
                    exchange.getRequestHeaders().getFirst(StaffAuthorityHttpSigning.TIMESTAMP_HEADER), nonce,
                    exchange.getRequestHeaders().getFirst(StaffAuthorityHttpSigning.SIGNATURE_HEADER), Clock.systemUTC()
            ));
            var wire = MinecraftPunishmentPreparationCodec.decodeRequest(body);
            PunishmentPlan plan = preparedPlan(wire);
            String response = MinecraftPunishmentPreparationCodec.encodeResponse(
                    MinecraftPunishmentPreparationWire.Response.prepared(
                            MinecraftPunishmentPreparationMapper.plan(plan)));
            exchange.getResponseHeaders().set(
                    StaffAuthorityHttpSigning.RESPONSE_SIGNATURE_HEADER,
                    StaffAuthorityHttpSigning.signResponse(CREDENTIAL, nonce, 200, response));
            byte[] encoded = response.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, encoded.length);
            exchange.getResponseBody().write(encoded);
        });
        try {
            HttpMinecraftPunishmentPreparer client = client(server, StaffModerationConfiguration.AuthorityTransport.BLOOM_PRIVATE_SPLIT);
            PunishmentPreparation.Prepared prepared = assertInstanceOf(
                    PunishmentPreparation.Prepared.class, client.prepareConfirmed(request(), CASE_ID));

            assertEquals(StaffAuthorityHttpSigning.Verification.ACCEPTED, verification.get());
            assertEquals(StaffRank.ADMIN, prepared.plan().actor().rank());
            assertEquals(CASE_ID, prepared.plan().caseId());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void privateSplitRejectsUnsignedPreparedResponse() throws IOException {
        HttpServer server = server(exchange -> {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            String response = MinecraftPunishmentPreparationCodec.encodeResponse(
                    MinecraftPunishmentPreparationWire.Response.prepared(
                            MinecraftPunishmentPreparationMapper.plan(
                                    preparedPlan(MinecraftPunishmentPreparationCodec.decodeRequest(body))
                            )));
            byte[] encoded = response.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, encoded.length);
            exchange.getResponseBody().write(encoded);
        });
        try {
            HttpMinecraftPunishmentPreparer client = client(server, StaffModerationConfiguration.AuthorityTransport.BLOOM_PRIVATE_SPLIT);
            assertThrows(StaffAuthorityClient.UnavailableException.class,
                    () -> client.prepareConfirmed(request(), CASE_ID));
        } finally {
            server.stop(0);
        }
    }

    private static HttpServer server(com.sun.net.httpserver.HttpHandler handler) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 1);
        server.createContext(MinecraftPunishmentPreparationWire.PATH, exchange -> {
            try {
                handler.handle(exchange);
            } finally {
                exchange.close();
            }
        });
        server.start();
        return server;
    }

    private static HttpMinecraftPunishmentPreparer client(
            HttpServer server,
            StaffModerationConfiguration.AuthorityTransport transport
    ) {
        URI endpoint = URI.create("http://127.0.0.1:%d/v1/staff-rank".formatted(server.getAddress().getPort()));
        return new HttpMinecraftPunishmentPreparer(endpoint, CREDENTIAL, transport);
    }

    private static CreatePunishmentRequest request() {
        return new CreatePunishmentRequest(
                new IdempotencyKey("d08:http:test:0008"), TARGET_ID,
                new Actor(ACTOR_ID, "D08Admin", StaffRank.MOD),
                "chat.toxicity", "Cross-platform HTTP test", CaseVisibility.PUBLIC, List.of(MUTE)
        );
    }

    private static PunishmentPlan preparedPlan(MinecraftPunishmentPreparationWire.Request wire) {
        PunishmentStep step = new PunishmentStep(0, "One hour mute", List.of(MUTE));
        EscalationDecision escalation = new EscalationDecision(
                0, 0, 0, List.of(), DecayEligibility.UNKNOWN, step);
        return new PunishmentPlan(
                new CaseId(wire.caseId()), new IdempotencyKey(wire.idempotencyKey()), wire.targetId(),
                new Actor(wire.actorId(), wire.actorName(), StaffRank.ADMIN),
                "chat.toxicity", "chat", "Chat toxicity", wire.internalExplanation(),
                "d08-http-v1", wire.visibility(), NOW, escalation, List.of(MUTE)
        );
    }
}
