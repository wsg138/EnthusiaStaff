package net.enthusia.staff.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class CommandBridgeHttpSigningTest {
    private static final String CREDENTIAL = "test-command-bridge-secret";
    private static final String METHOD = "POST";
    private static final String TARGET = "/v1/discord-command";
    private static final String BODY = "{\"command\":\"list\"}";
    private static final String NONCE = "0123456789abcdef0123456789abcdef";
    private static final Instant NOW = Instant.parse("2026-09-30T19:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Test
    void bodyIsCryptographicallyBound() {
        CommandBridgeHttpSigning.RequestProof proof = CommandBridgeHttpSigning.signRequest(
                CREDENTIAL, METHOD, TARGET, BODY, NOW, NONCE);

        assertEquals(CommandBridgeHttpSigning.Verification.ACCEPTED, verify(BODY, proof, CLOCK));
        assertEquals(CommandBridgeHttpSigning.Verification.INVALID_SIGNATURE,
                verify("{\"command\":\"stop\"}", proof, CLOCK));
    }

    @Test
    void rejectsExpiredAndBadSignatures() {
        CommandBridgeHttpSigning.RequestProof proof = CommandBridgeHttpSigning.signRequest(
                CREDENTIAL, METHOD, TARGET, BODY, NOW, NONCE);
        Clock expired = Clock.fixed(NOW.plusSeconds(31), ZoneOffset.UTC);

        assertEquals(CommandBridgeHttpSigning.Verification.EXPIRED, verify(BODY, proof, expired));
        assertEquals(CommandBridgeHttpSigning.Verification.INVALID_SIGNATURE,
                CommandBridgeHttpSigning.verifyRequest(
                        CREDENTIAL,
                        METHOD,
                        TARGET,
                        BODY,
                        proof.timestamp(),
                        proof.nonce(),
                        "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA",
                        CLOCK
                ));
    }

    @Test
    void responseSignatureAlsoBindsBody() {
        String signature = CommandBridgeHttpSigning.signResponse(CREDENTIAL, NONCE, 200, "ok");
        assertTrue(CommandBridgeHttpSigning.verifyResponse(CREDENTIAL, NONCE, 200, "ok", signature));
        assertFalse(CommandBridgeHttpSigning.verifyResponse(CREDENTIAL, NONCE, 200, "changed", signature));
    }

    private static CommandBridgeHttpSigning.Verification verify(
            String body,
            CommandBridgeHttpSigning.RequestProof proof,
            Clock clock
    ) {
        return CommandBridgeHttpSigning.verifyRequest(
                CREDENTIAL,
                METHOD,
                TARGET,
                body,
                proof.timestamp(),
                proof.nonce(),
                proof.signature(),
                clock
        );
    }
}
