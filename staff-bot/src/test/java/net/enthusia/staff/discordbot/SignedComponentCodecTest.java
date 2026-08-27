package net.enthusia.staff.discordbot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SignedComponentCodecTest {
    private static final String SECRET = Character.toString('x').repeat(48);
    private static final Instant NOW = Instant.parse("2026-08-27T12:00:00Z");

    @Test
    void signsBindsExpiresAndReplayProtectsComponentIds() {
        SignedComponentCodec codec = codec(Clock.fixed(NOW, ZoneOffset.UTC));
        UUID target = UUID.fromString("11111111-2222-3333-4444-555555555555");
        String encoded = codec.encode(
                SignedComponentCodec.Action.HISTORY,
                SignedComponentCodec.TargetRef.minecraft(target),
                123456789L
        );

        assertTrue(encoded.length() <= 100);
        SignedComponentCodec.Decoded decoded = codec.decodeAndClaim(encoded, 123456789L);
        assertEquals(SignedComponentCodec.Action.HISTORY, decoded.action());
        assertEquals(target, decoded.target().minecraftId());

        SignedComponentCodec.InvalidComponentException replayed = assertThrows(
                SignedComponentCodec.InvalidComponentException.class,
                () -> codec.decodeAndClaim(encoded, 123456789L)
        );
        assertEquals(SignedComponentCodec.Denial.REPLAYED, replayed.denial());
    }

    @Test
    void rejectsWrongActorTamperAndStaleComponents() {
        SignedComponentCodec source = codec(Clock.fixed(NOW, ZoneOffset.UTC));
        String encoded = source.encode(
                SignedComponentCodec.Action.LINKED,
                SignedComponentCodec.TargetRef.discord(987654321L),
                123456789L
        );

        SignedComponentCodec.InvalidComponentException wrongActor = assertThrows(
                SignedComponentCodec.InvalidComponentException.class,
                () -> codec(Clock.fixed(NOW, ZoneOffset.UTC)).decodeAndClaim(encoded, 123456788L)
        );
        assertEquals(SignedComponentCodec.Denial.WRONG_ACTOR, wrongActor.denial());

        String tampered = encoded.substring(0, encoded.length() - 1)
                + (encoded.endsWith("A") ? "B" : "A");
        SignedComponentCodec.InvalidComponentException invalid = assertThrows(
                SignedComponentCodec.InvalidComponentException.class,
                () -> codec(Clock.fixed(NOW, ZoneOffset.UTC)).decodeAndClaim(tampered, 123456789L)
        );
        assertEquals(SignedComponentCodec.Denial.INVALID, invalid.denial());

        SignedComponentCodec.InvalidComponentException stale = assertThrows(
                SignedComponentCodec.InvalidComponentException.class,
                () -> codec(Clock.fixed(NOW.plus(Duration.ofMinutes(6)), ZoneOffset.UTC))
                        .decodeAndClaim(encoded, 123456789L)
        );
        assertEquals(SignedComponentCodec.Denial.STALE, stale.denial());
    }

    private static SignedComponentCodec codec(Clock clock) {
        return new SignedComponentCodec(
                clock,
                Duration.ofMinutes(5),
                SECRET,
                new SecureRandom(),
                new InteractionReplayGuard(64, Duration.ofMinutes(10))
        );
    }
}
