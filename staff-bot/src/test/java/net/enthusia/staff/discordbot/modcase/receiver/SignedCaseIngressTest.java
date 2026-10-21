package net.enthusia.staff.discordbot.modcase.receiver;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Independently Python-signed REAL synthetic API envelope; fake transport only. */
class SignedCaseIngressTest {
    private static final long ISSUED = 1792574220L;
    private static final String ORIGIN = "minecraft";
    private static final String SCOPE = "smp";
    private static final String GOLDEN =
            "ebafffda993618d458194bfea1d6ed6070c51d5659b73898ec535f9da0c9f9e8";
    @TempDir Path directory;

    private static byte[] key() {
        byte[] bytes = new byte[32];
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = (byte) i;
        }
        return bytes;
    }

    private String wire() throws Exception {
        try (InputStream source = getClass().getResourceAsStream(
                "/modcase/api-generated-case-events.jsonl")) {
            if (source == null) {
                throw new IllegalStateException("missing synthetic API fixture");
            }
            return new String(source.readAllBytes(), StandardCharsets.UTF_8)
                    .lines().findFirst().orElseThrow();
        }
    }

    private SignedCaseIngress ingress(byte[] secret, long now) throws Exception {
        return new SignedCaseIngress(new ReceiverJournal(directory.resolve("signed.json")),
                ORIGIN, SCOPE, 777L, secret,
                Clock.fixed(Instant.ofEpochSecond(now), ZoneOffset.UTC));
    }

    @Test
    void pythonGoldenSignatureAuthenticatesActualSyntheticApiEvent() throws Exception {
        CaseAck ack = ingress(key(), ISSUED)
                .receive(ORIGIN, SCOPE, ISSUED, GOLDEN, wire()).orElseThrow();
        assertEquals("ACCEPTED", ack.status(), "the signed case is durably accepted");
    }

    @Test
    void exactRetransmissionIsIdempotent() throws Exception {
        SignedCaseIngress service = ingress(key(), ISSUED);
        service.receive(ORIGIN, SCOPE, ISSUED, GOLDEN, wire()).orElseThrow();
        assertEquals("DUPLICATE", service.receive(ORIGIN, SCOPE, ISSUED,
                GOLDEN, wire()).orElseThrow().status(), "one event cannot be created twice");
    }

    @Test
    void modifiedBodyIsRejected() throws Exception {
        String tampered = wire().replace("\"scope_id\":\"smp\"",
                "\"scope_id\":\"foreign\"");
        assertTrue(ingress(key(), ISSUED)
                .receive(ORIGIN, SCOPE, ISSUED, GOLDEN, tampered).isEmpty(),
                "authenticated body bytes are immutable");
    }

    @Test
    void wrongClaimedScopeIsRejected() throws Exception {
        assertTrue(ingress(key(), ISSUED)
                .receive(ORIGIN, "foreign", ISSUED, GOLDEN, wire()).isEmpty(),
                "a foreign scope is not the receiver's binding");
    }

    @Test
    void wrongClaimedOriginIsRejected() throws Exception {
        assertTrue(ingress(key(), ISSUED)
                .receive("discord", SCOPE, ISSUED, GOLDEN, wire()).isEmpty(),
                "a foreign platform cannot impersonate this binding");
    }

    @Test
    void sameKeyCannotAuthenticateAnotherPrivateGuild() throws Exception {
        SignedCaseIngress foreignGuild = new SignedCaseIngress(
                new ReceiverJournal(directory.resolve("other-guild.json")),
                ORIGIN, SCOPE, 888L, key(),
                Clock.fixed(Instant.ofEpochSecond(ISSUED), ZoneOffset.UTC));
        assertTrue(foreignGuild.receive(ORIGIN, SCOPE, ISSUED, GOLDEN, wire()).isEmpty(),
                "the exact private guild is part of the signed claims");
    }

    @Test
    void wrongKeyIsRejected() throws Exception {
        byte[] wrong = key();
        wrong[0] ^= 1;
        assertTrue(ingress(wrong, ISSUED)
                .receive(ORIGIN, SCOPE, ISSUED, GOLDEN, wire()).isEmpty(),
                "a different service cannot authenticate");
    }

    @Test
    void expiredProofIsRejected() throws Exception {
        assertTrue(ingress(key(), ISSUED + 31)
                .receive(ORIGIN, SCOPE, ISSUED, GOLDEN, wire()).isEmpty(),
                "old signatures expire");
    }

    @Test
    void futureProofIsRejected() throws Exception {
        assertTrue(ingress(key(), ISSUED - 31)
                .receive(ORIGIN, SCOPE, ISSUED, GOLDEN, wire()).isEmpty(),
                "future signatures cannot preauthorize an event");
    }

    @Test
    void wrongSignatureIsRejected() throws Exception {
        assertTrue(ingress(key(), ISSUED)
                .receive(ORIGIN, SCOPE, ISSUED, "0".repeat(64), wire()).isEmpty(),
                "a well-shaped invalid MAC is not authentication");
    }

    @Test
    void shortKeyCannotConstructAReceiver() throws Exception {
        assertThrows(IllegalArgumentException.class,
                () -> ingress(new byte[16], ISSUED),
                "caller must provision a strong ingress secret");
    }
}
