package net.enthusia.staff.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class CommandBridgeRequestAuthenticatorTest {
    private static final String CREDENTIAL = "test-command-bridge-secret";
    private static final String METHOD = "POST";
    private static final String TARGET = "/v1/discord-command";
    private static final String BODY = "{\"requestId\":\"one\"}";
    private static final String NONCE = "0123456789abcdef0123456789abcdef";
    private static final Instant NOW = Instant.parse("2026-09-30T19:00:00Z");

    @Test
    void consumesNonceOnlyOnce() {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        CommandBridgeRequestAuthenticator authenticator = authenticator(clock);
        CommandBridgeHttpSigning.RequestProof proof = proof(BODY, NOW);

        assertEquals(CommandBridgeRequestAuthenticator.Result.ACCEPTED,
                authenticate(authenticator, BODY, proof));
        assertEquals(CommandBridgeRequestAuthenticator.Result.REPLAYED,
                authenticate(authenticator, BODY, proof));
    }

    @Test
    void reportsExpiredTamperedAndBadSignaturesWithoutConsumingNonce() {
        CommandBridgeRequestAuthenticator expired = authenticator(Clock.fixed(NOW.plusSeconds(31), ZoneOffset.UTC));
        CommandBridgeHttpSigning.RequestProof proof = proof(BODY, NOW);
        assertEquals(CommandBridgeRequestAuthenticator.Result.EXPIRED, authenticate(expired, BODY, proof));

        CommandBridgeRequestAuthenticator current = authenticator(Clock.fixed(NOW, ZoneOffset.UTC));
        assertEquals(CommandBridgeRequestAuthenticator.Result.INVALID_SIGNATURE,
                authenticate(current, BODY + "x", proof));
        assertEquals(CommandBridgeRequestAuthenticator.Result.INVALID_SIGNATURE,
                current.authenticate(METHOD, TARGET, BODY, proof.timestamp(), proof.nonce(),
                        "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"));
        assertEquals(CommandBridgeRequestAuthenticator.Result.ACCEPTED,
                authenticate(current, BODY, proof));
    }

    private static CommandBridgeRequestAuthenticator authenticator(Clock clock) {
        return new CommandBridgeRequestAuthenticator(
                CREDENTIAL,
                "smp",
                clock,
                new ReplayGuard(64, Duration.ofMinutes(2))
        );
    }

    private static CommandBridgeHttpSigning.RequestProof proof(String body, Instant now) {
        return CommandBridgeHttpSigning.signRequest(CREDENTIAL, METHOD, TARGET, body, now, NONCE);
    }

    private static CommandBridgeRequestAuthenticator.Result authenticate(
            CommandBridgeRequestAuthenticator authenticator,
            String body,
            CommandBridgeHttpSigning.RequestProof proof
    ) {
        return authenticator.authenticate(
                METHOD, TARGET, body, proof.timestamp(), proof.nonce(), proof.signature());
    }
}
