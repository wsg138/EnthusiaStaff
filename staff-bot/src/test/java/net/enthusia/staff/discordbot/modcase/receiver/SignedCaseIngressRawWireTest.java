package net.enthusia.staff.discordbot.modcase.receiver;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Synthetic raw-wire ingress tests; no HTTP listener or real service key. */
class SignedCaseIngressRawWireTest {
    private static final long ISSUED = 1792574220L;
    private static final String ORIGIN = "minecraft";
    private static final String SCOPE = "smp";
    private static final String PATH = "/v1/ai-case-events";
    private static final String POST = "POST";
    private static final String SIGNATURE =
            "ebafffda993618d458194bfea1d6ed6070c51d5659b73898ec535f9da0c9f9e8";
    @TempDir Path directory;

    private byte[] apiWire() throws Exception {
        try (InputStream stream = getClass().getResourceAsStream(
                "/modcase/api-generated-case-events.jsonl")) {
            if (stream == null) {
                throw new IllegalStateException("missing synthetic API payload");
            }
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8)
                    .lines().findFirst().orElseThrow()
                    .getBytes(StandardCharsets.UTF_8);
        }
    }

    private SignedCaseIngress verifier() throws Exception {
        byte[] secret = new byte[32];
        for (int i = 0; i < secret.length; i++) {
            secret[i] = (byte) i;
        }
        return new SignedCaseIngress(new ReceiverJournal(directory.resolve("raw-wire.json")),
                ORIGIN, SCOPE, 777L, secret,
                Clock.fixed(Instant.ofEpochSecond(ISSUED), ZoneOffset.UTC));
    }

    @Test
    void exactSignedBytesAreAccepted() throws Exception {
        CaseAck ack = verifier().receiveRaw(POST, PATH, new SignedCaseIngress.Proof(ORIGIN, SCOPE, ISSUED, SIGNATURE), apiWire()).orElseThrow();
        assertEquals("ACCEPTED", ack.status(), "exact bytes authorize one durable case");
    }

    @Test
    void wrongMethodIsRejected() throws Exception {
        assertTrue(verifier().receiveRaw("GET", PATH, new SignedCaseIngress.Proof(ORIGIN, SCOPE, ISSUED, SIGNATURE), apiWire()).isEmpty(), "signed method is POST");
    }

    @Test
    void wrongRouteIsRejected() throws Exception {
        assertTrue(verifier().receiveRaw(POST, "/v1/other", new SignedCaseIngress.Proof(ORIGIN, SCOPE, ISSUED, SIGNATURE), apiWire()).isEmpty(), "signed route is exact");
    }

    @Test
    void malformedUtf8IsRejectedBeforeProcessing() throws Exception {
        byte[] broken = {(byte) 0xc3, (byte) 0x28};
        assertTrue(verifier().receiveRaw(POST, PATH, new SignedCaseIngress.Proof(ORIGIN, SCOPE, ISSUED, SIGNATURE), broken).isEmpty(), "no replacement-character decoding");
    }

    @Test
    void oversizedRawBodyIsRejectedBeforeProcessing() throws Exception {
        byte[] large = new byte[8193];
        assertTrue(verifier().receiveRaw(POST, PATH, new SignedCaseIngress.Proof(ORIGIN, SCOPE, ISSUED, SIGNATURE), large).isEmpty(), "raw body byte cap is strict");
    }

    @Test
    void nullProofCannotBypassAuthentication() throws Exception {
        assertTrue(verifier().receiveRaw(POST, PATH, null, apiWire()).isEmpty(),
                "an omitted proof must never create an authenticated case");
    }

    @Test
    void nullRawBodyIsRejected() throws Exception {
        assertTrue(verifier().receiveRaw(POST, PATH, new SignedCaseIngress.Proof(ORIGIN, SCOPE, ISSUED, SIGNATURE), null).isEmpty(), "missing request has no ACK");
    }
}
