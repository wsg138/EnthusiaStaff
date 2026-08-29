package net.enthusia.staff.discordbot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class ModerationPreviewWebSessionStoreTest {
    @Test
    void browserSessionPreservesBoundClaimsAndExpires() {
        var clock = new MutableClock(Instant.parse("2026-08-29T22:00:00Z"));
        var store = new ModerationPreviewWebSessionStore(4, Duration.ofMinutes(15), clock, new SecureRandom());
        var claims = new ModerationPreviewLaunchTicketService.Claims(
                11L,
                22L,
                "sample-target",
                clock.instant(),
                clock.instant().plusSeconds(120));

        var session = store.create(claims);
        assertEquals(11L, store.find(session.id()).orElseThrow().claims().actorId());
        assertNotEquals(session.id(), session.csrfToken());

        clock.advance(Duration.ofMinutes(16));
        assertTrue(store.find(session.id()).isEmpty());
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
