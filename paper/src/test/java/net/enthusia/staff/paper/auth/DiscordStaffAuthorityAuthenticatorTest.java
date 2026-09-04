package net.enthusia.staff.paper.auth;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.InetAddress;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import net.enthusia.staff.protocol.ReplayGuard;
import net.enthusia.staff.protocol.StaffAuthorityHttpSigning;
import org.junit.jupiter.api.Test;

class DiscordStaffAuthorityAuthenticatorTest {
    private static final String CREDENTIAL = "authority-test-credential-value-1234567890";
    private static final String GET_METHOD = "GET";
    private static final String TARGET =
            "/v1/staff-rank?player=0f48cf03-f319-41e8-981f-4d0e765b5b49";
    private static final String NONCE = "abcdefghijklmnopqrstuvwxABCDEFGH";
    private static final Instant NOW = Instant.parse("2026-09-02T20:00:00Z");

    @Test
    void loopbackModeRequiresLoopbackPeerAndBearer() throws Exception {
        DiscordStaffAuthorityAuthenticator authenticator =
                new DiscordStaffAuthorityAuthenticator(CREDENTIAL, false);

        assertTrue(authenticator.authenticate(
                InetAddress.getByName("127.0.0.1"),
                GET_METHOD,
                TARGET,
                "Bearer " + CREDENTIAL,
                null,
                null,
                null
        ).accepted());
        assertFalse(authenticator.authenticate(
                InetAddress.getByName("10.0.0.2"),
                GET_METHOD,
                TARGET,
                "Bearer " + CREDENTIAL,
                null,
                null,
                null
        ).accepted());
    }

    @Test
    void privateSplitModeAcceptsSignedPrivatePeerAndSignsResponse() throws Exception {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        DiscordStaffAuthorityAuthenticator authenticator = new DiscordStaffAuthorityAuthenticator(
                CREDENTIAL, true, clock, new ReplayGuard(8, Duration.ofMinutes(2)));
        StaffAuthorityHttpSigning.RequestProof proof =
                StaffAuthorityHttpSigning.signRequest(CREDENTIAL, GET_METHOD, TARGET, NOW, NONCE);

        DiscordStaffAuthorityAuthenticator.Result result = authenticator.authenticate(
                InetAddress.getByName("172.18.0.2"),
                GET_METHOD,
                TARGET,
                null,
                proof.timestamp(),
                proof.nonce(),
                proof.signature()
        );

        assertTrue(result.accepted());
        String signature = authenticator.responseSignature(result, 200, "MOD");
        assertNotNull(signature);
        assertTrue(StaffAuthorityHttpSigning.verifyResponse(
                CREDENTIAL, NONCE, 200, "MOD", signature));
    }

    @Test
    void privateSplitModeRejectsPublicPeerTamperingAndReplay() throws Exception {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        DiscordStaffAuthorityAuthenticator authenticator = new DiscordStaffAuthorityAuthenticator(
                CREDENTIAL, true, clock, new ReplayGuard(8, Duration.ofMinutes(2)));
        StaffAuthorityHttpSigning.RequestProof proof =
                StaffAuthorityHttpSigning.signRequest(CREDENTIAL, GET_METHOD, TARGET, NOW, NONCE);

        assertFalse(authenticator.authenticate(
                InetAddress.getByName("8.8.8.8"),
                GET_METHOD,
                TARGET,
                null,
                proof.timestamp(),
                proof.nonce(),
                proof.signature()
        ).accepted());

        DiscordStaffAuthorityAuthenticator.Result accepted = authenticator.authenticate(
                InetAddress.getByName("10.0.0.2"),
                GET_METHOD,
                TARGET,
                null,
                proof.timestamp(),
                proof.nonce(),
                proof.signature()
        );
        assertTrue(accepted.accepted());

        DiscordStaffAuthorityAuthenticator.Result replayed = authenticator.authenticate(
                InetAddress.getByName("10.0.0.2"),
                GET_METHOD,
                TARGET,
                null,
                proof.timestamp(),
                proof.nonce(),
                proof.signature()
        );
        assertFalse(replayed.accepted());
        assertNull(authenticator.responseSignature(replayed, 401, ""));
    }
}
