package net.enthusia.staff.discordbot.modcase.receiver;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/** Offline synthetic API event ACK, signed with an independent response-only test key. */
class SignedJournalAckTest {
    private static final String OPERATION =
            "8a21ecddea845cfb3de9b1887293e2d1157e8cad18ae13512a95931cb09b9ad2";
    private static final String CASE_ID = "aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee";
    private static final String ORIGIN = "minecraft";
    private static final String SCOPE = "smp";
    private static final long ISSUED = 1792574220L;
    private static final String GOLDEN_HMAC =
            "8c66d75945c300a9ecb978807c8b22c6283bf52f1b7804ad4f89ec149c5f1706";
    private static final String GOLDEN_BODY =
            "{\"case_id\":\"aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee\","
            + "\"event_type\":\"CASE_CREATED\","
            + "\"operation_id\":\"" + OPERATION + "\","
            + "\"origin_platform\":\"minecraft\",\"revision\":0,"
            + "\"scope_id\":\"smp\",\"status\":\"ACCEPTED\"}";

    private byte[] responseKey() {
        byte[] key = new byte[32];
        for (int i = 0; i < key.length; i++) {
            key[i] = (byte) (i + 32);
        }
        return key;
    }

    private CaseAck journalAck() {
        return new CaseAck("ACCEPTED", OPERATION, "CASE_CREATED", CASE_ID, 0, ORIGIN, SCOPE);
    }

    @Test
    void responseMatchesIndependentPythonGoldenBodyAndSignature() {
        SignedJournalAck.Proof signed = SignedJournalAck.sign(
                journalAck(), ORIGIN, SCOPE, 777L, ISSUED, responseKey());
        assertEquals(GOLDEN_BODY, new String(signed.body(), StandardCharsets.UTF_8));
        assertEquals(GOLDEN_HMAC, signed.signatureHex());
        assertEquals(ISSUED, signed.issuedAtSeconds());
    }

    @Test
    void privateGuildIsPartOfResponseMac() {
        SignedJournalAck.Proof one = SignedJournalAck.sign(
                journalAck(), ORIGIN, SCOPE, 777L, ISSUED, responseKey());
        SignedJournalAck.Proof other = SignedJournalAck.sign(
                journalAck(), ORIGIN, SCOPE, 888L, ISSUED, responseKey());
        assertNotEquals(one.signatureHex(), other.signatureHex());
    }

    @Test
    void timestampAndStatusAreBoundIntoSignature() {
        SignedJournalAck.Proof now = SignedJournalAck.sign(
                journalAck(), ORIGIN, SCOPE, 777L, ISSUED, responseKey());
        SignedJournalAck.Proof later = SignedJournalAck.sign(
                journalAck(), ORIGIN, SCOPE, 777L, ISSUED + 1, responseKey());
        CaseAck rejected = new CaseAck("REJECTED", OPERATION,
                "CASE_CREATED", CASE_ID, 0, ORIGIN, SCOPE);
        SignedJournalAck.Proof changed = SignedJournalAck.sign(
                rejected, ORIGIN, SCOPE, 777L, ISSUED, responseKey());
        assertNotEquals(now.signatureHex(), later.signatureHex());
        assertNotEquals(now.signatureHex(), changed.signatureHex());
    }

    @Test
    void proofNeverExposesMutableSignedBytes() {
        SignedJournalAck.Proof signed = SignedJournalAck.sign(
                journalAck(), ORIGIN, SCOPE, 777L, ISSUED, responseKey());
        signed.body()[0] = 0;
        assertArrayEquals(GOLDEN_BODY.getBytes(StandardCharsets.UTF_8), signed.body());
    }

    @Test
    void foreignCaseScopeFailsClosed() {
        assertThrows(IllegalArgumentException.class,
                () -> SignedJournalAck.sign(journalAck(), ORIGIN,
                        "other", 777L, ISSUED, responseKey()));
    }

    @Test
    void weakKeyCannotSignJournalAcks() {
        assertThrows(IllegalArgumentException.class,
                () -> SignedJournalAck.sign(journalAck(), ORIGIN,
                        SCOPE, 777L, ISSUED, new byte[12]));
    }

    @Test
    void nonJournalStatusCannotBeSigned() {
        assertThrows(IllegalArgumentException.class,
                () -> SignedJournalAck.sign(new CaseAck("PUBLISHED", OPERATION,
                        "CASE_CREATED", CASE_ID, 0, ORIGIN, SCOPE), ORIGIN,
                        SCOPE, 777L, ISSUED, responseKey()));
    }
}
