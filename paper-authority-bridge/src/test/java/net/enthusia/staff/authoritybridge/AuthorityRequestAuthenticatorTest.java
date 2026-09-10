package net.enthusia.staff.authoritybridge;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.InetAddress;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import net.enthusia.staff.protocol.ReplayGuard;
import net.enthusia.staff.protocol.StaffAuthorityHttpSigning;
import org.junit.jupiter.api.Test;

class AuthorityRequestAuthenticatorTest {
    private static final String GET_METHOD = "GET";
    private static final Instant NOW = Instant.parse("2026-09-03T03:45:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final String TARGET =
            "/v1/staff-rank?player=0f48cf03-f319-41e8-981f-4d0e765b5b49";
    private static final String NONCE = "abcdefghijklmnopqrstuvwxABCDEFGH";

    @Test
    void signedPrivateRequestIsAcceptedAndResponseIsSigned() throws Exception {
        String keyMaterial = testKeyMaterial();
        AuthorityRequestAuthenticator authenticator = new AuthorityRequestAuthenticator(
                keyMaterial,
                CLOCK,
                new ReplayGuard(8, Duration.ofMinutes(2))
        );
        StaffAuthorityHttpSigning.RequestProof proof = StaffAuthorityHttpSigning.signRequest(
                keyMaterial,
                GET_METHOD,
                TARGET,
                NOW,
                NONCE
        );

        AuthorityRequestAuthenticator.Result result = authenticator.authenticate(
                InetAddress.getByName("172.18.0.2"),
                GET_METHOD,
                TARGET,
                proof.timestamp(),
                proof.nonce(),
                proof.signature()
        );

        assertTrue(result.accepted());
        String responseSignature = authenticator.signResponse(result, 200, "MOD");
        assertNotNull(responseSignature);
        assertTrue(StaffAuthorityHttpSigning.verifyResponse(
                keyMaterial,
                NONCE,
                200,
                "MOD",
                responseSignature
        ));
    }

    @Test
    void publicPeerAndReplayAreRejected() throws Exception {
        String keyMaterial = testKeyMaterial();
        AuthorityRequestAuthenticator authenticator = new AuthorityRequestAuthenticator(
                keyMaterial,
                CLOCK,
                new ReplayGuard(8, Duration.ofMinutes(2))
        );
        StaffAuthorityHttpSigning.RequestProof proof = StaffAuthorityHttpSigning.signRequest(
                keyMaterial,
                GET_METHOD,
                TARGET,
                NOW,
                NONCE
        );

        assertFalse(authenticator.authenticate(
                InetAddress.getByName("8.8.8.8"),
                GET_METHOD,
                TARGET,
                proof.timestamp(),
                proof.nonce(),
                proof.signature()
        ).accepted());

        assertTrue(authenticator.authenticate(
                InetAddress.getByName("10.0.0.2"),
                GET_METHOD,
                TARGET,
                proof.timestamp(),
                proof.nonce(),
                proof.signature()
        ).accepted());

        assertFalse(authenticator.authenticate(
                InetAddress.getByName("10.0.0.2"),
                GET_METHOD,
                TARGET,
                proof.timestamp(),
                proof.nonce(),
                proof.signature()
        ).accepted());
    }

    private static String testKeyMaterial() {
        return UUID.randomUUID().toString() + UUID.randomUUID();
    }
}
