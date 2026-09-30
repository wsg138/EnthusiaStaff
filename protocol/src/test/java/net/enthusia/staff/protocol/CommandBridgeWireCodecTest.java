package net.enthusia.staff.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.UUID;
import net.enthusia.staff.domain.commandbridge.CommandBridgeOutcome;
import net.enthusia.staff.domain.commandbridge.CommandBridgeRequest;
import net.enthusia.staff.domain.commandbridge.CommandBridgeResponse;
import net.enthusia.staff.domain.moderation.DiscordUserId;
import net.enthusia.staff.domain.moderation.ModerationSubjectId;
import org.junit.jupiter.api.Test;

class CommandBridgeWireCodecTest {
    private static final UUID REQUEST_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID SUBJECT_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final DiscordUserId DISCORD_ID = new DiscordUserId("123456789012345678");
    private static final UUID ACTOR_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final Instant NOW = Instant.parse("2026-09-30T19:00:00Z");
    private final CommandBridgeWireCodec codec = new CommandBridgeWireCodec();

    @Test
    void roundTripsOnlyTheExplicitRequestShape() {
        CommandBridgeRequest request = request();
        String encoded = codec.encodeRequest(request);
        assertTrue(encoded.contains("\"discordUserId\":\"" + DISCORD_ID + "\""));
        assertEquals(request, codec.decodeRequest(encoded).orElseThrow());
    }

    @Test
    void rejectsUnknownRequestFieldsVersionsAndOversizedBodies() {
        String valid = codec.encodeRequest(request());
        assertTrue(codec.decodeRequest(valid.substring(0, valid.length() - 1) + ",\"role\":\"admin\"}").isEmpty());
        assertTrue(codec.decodeRequest(valid.replace("\"version\":1", "\"version\":2")).isEmpty());
        assertTrue(codec.decodeRequest("x".repeat(4_097)).isEmpty());
    }

    @Test
    void responseShapeIsStrictAndBounded() {
        CommandBridgeResponse response = new CommandBridgeResponse(
                CommandBridgeOutcome.SUCCESS,
                "Command executed.",
                "online=2",
                false,
                false
        );
        String encoded = codec.encodeResponse(response);
        assertEquals(response, codec.decodeResponse(encoded).orElseThrow());
        assertTrue(codec.decodeResponse(encoded.substring(0, encoded.length() - 1) + ",\"db\":\"secret\"}").isEmpty());
        assertTrue(codec.decodeResponse("x".repeat(8_193)).isEmpty());
    }

    private static CommandBridgeRequest request() {
        return new CommandBridgeRequest(
                REQUEST_ID,
                new ModerationSubjectId(SUBJECT_ID),
                DISCORD_ID,
                ACTOR_ID,
                "smp",
                "list",
                NOW
        );
    }
}
