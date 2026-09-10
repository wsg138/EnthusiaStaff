package net.enthusia.staff.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class StaffAuthorityHttpSigningTest {
    private static final String CREDENTIAL = "authority-test-credential-value-1234567890";
    private static final String METHOD = "GET";
    private static final String TARGET =
            "/v1/staff-rank?player=0f48cf03-f319-41e8-981f-4d0e765b5b49";
    private static final String NONCE = "abcdefghijklmnopqrstuvwxABCDEFGH";
    private static final Instant NOW = Instant.parse("2026-09-02T20:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Test
    void validRequestAndResponseRoundTrip() {
        StaffAuthorityHttpSigning.RequestProof proof =
                StaffAuthorityHttpSigning.signRequest(CREDENTIAL, METHOD, TARGET, NOW, NONCE);

        assertEquals(StaffAuthorityHttpSigning.Verification.ACCEPTED,
                StaffAuthorityHttpSigning.verifyRequest(
                        CREDENTIAL,
                        METHOD,
                        TARGET,
                        proof.timestamp(),
                        proof.nonce(),
                        proof.signature(),
                        CLOCK));

        String response = "MOD";
        String responseSignature =
                StaffAuthorityHttpSigning.signResponse(CREDENTIAL, NONCE, 200, response);
        assertTrue(StaffAuthorityHttpSigning.verifyResponse(
                CREDENTIAL, NONCE, 200, response, responseSignature));
    }

    @Test
    void requestRejectsTamperingExpiryAndMalformedProofs() {
        StaffAuthorityHttpSigning.RequestProof proof =
                StaffAuthorityHttpSigning.signRequest(CREDENTIAL, METHOD, TARGET, NOW, NONCE);

        assertEquals(StaffAuthorityHttpSigning.Verification.INVALID_SIGNATURE,
                StaffAuthorityHttpSigning.verifyRequest(
                        CREDENTIAL,
                        METHOD,
                        TARGET + "x",
                        proof.timestamp(),
                        proof.nonce(),
                        proof.signature(),
                        CLOCK));
        assertEquals(StaffAuthorityHttpSigning.Verification.EXPIRED,
                StaffAuthorityHttpSigning.verifyRequest(
                        CREDENTIAL,
                        METHOD,
                        TARGET,
                        Long.toString(NOW.minusSeconds(31).getEpochSecond()),
                        proof.nonce(),
                        StaffAuthorityHttpSigning.signRequest(
                                CREDENTIAL, METHOD, TARGET, NOW.minusSeconds(31), NONCE).signature(),
                        CLOCK));
        assertEquals(StaffAuthorityHttpSigning.Verification.MALFORMED,
                StaffAuthorityHttpSigning.verifyRequest(
                        CREDENTIAL, METHOD, TARGET, "bad", NONCE, proof.signature(), CLOCK));
    }

    @Test
    void responseSignatureBindsNonceStatusAndBody() {
        String signature = StaffAuthorityHttpSigning.signResponse(CREDENTIAL, NONCE, 404, "");

        assertFalse(StaffAuthorityHttpSigning.verifyResponse(
                CREDENTIAL, NONCE, 200, "", signature));
        assertFalse(StaffAuthorityHttpSigning.verifyResponse(
                CREDENTIAL, NONCE, 404, "ADMIN", signature));
        assertFalse(StaffAuthorityHttpSigning.verifyResponse(
                CREDENTIAL, "12345678901234567890123456789012", 404, "", signature));
    }
}
