package net.enthusia.staff.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class StaffAuthorityHttpSigningTest {
    private static final String POST = "POST";

    @Test
    void punishmentProofBindsExactBodyAndOperation() {
        byte[] original = "{\"target\":\"player-one\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] changed = "{\"target\":\"player-two\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        String path = "/v1/staff-punishments/prepare";
        String originalTarget = StaffAuthorityHttpSigning.punishmentRequestTarget(path, original);
        var proof = StaffAuthorityHttpSigning.signRequest(CREDENTIAL, POST, originalTarget, NOW, NONCE);
        assertEquals(StaffAuthorityHttpSigning.Verification.ACCEPTED,
                StaffAuthorityHttpSigning.verifyRequest(CREDENTIAL, POST, originalTarget,
                        proof.timestamp(), proof.nonce(), proof.signature(), CLOCK));
        assertEquals(StaffAuthorityHttpSigning.Verification.INVALID_SIGNATURE,
                StaffAuthorityHttpSigning.verifyRequest(CREDENTIAL, POST,
                        StaffAuthorityHttpSigning.punishmentRequestTarget(path, changed),
                        proof.timestamp(), proof.nonce(), proof.signature(), CLOCK));
        assertEquals(StaffAuthorityHttpSigning.Verification.INVALID_SIGNATURE,
                StaffAuthorityHttpSigning.verifyRequest(CREDENTIAL, POST,
                        StaffAuthorityHttpSigning.punishmentRequestTarget("/v1/staff-punishments/confirm", original),
                        proof.timestamp(), proof.nonce(), proof.signature(), CLOCK));
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> StaffAuthorityHttpSigning.punishmentRequestTarget(path, new byte[8193]));
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> StaffAuthorityHttpSigning.punishmentRequestTarget("/v1/staff-punishments/prepare/../confirm", original));
        String bothTarget = StaffAuthorityHttpSigning.punishmentRequestTarget(
                "/v1/staff-punishments/both-plan", original);
        var bothProof = StaffAuthorityHttpSigning.signRequest(CREDENTIAL, POST, bothTarget, NOW, NONCE);
        assertEquals(StaffAuthorityHttpSigning.Verification.ACCEPTED,
                StaffAuthorityHttpSigning.verifyRequest(CREDENTIAL, POST, bothTarget,
                        bothProof.timestamp(), bothProof.nonce(), bothProof.signature(), CLOCK));
    }
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
