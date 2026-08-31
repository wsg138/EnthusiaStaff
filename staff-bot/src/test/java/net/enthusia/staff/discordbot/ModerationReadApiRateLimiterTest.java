package net.enthusia.staff.discordbot;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

class ModerationReadApiRateLimiterTest {
    @Test
    void rejectsPastCapacityAndResetsAfterWindow() {
        MutableClock clock = new MutableClock(Instant.parse("2026-08-31T18:00:00Z"));
        ModerationReadApiRateLimiter limiter = new ModerationReadApiRateLimiter(2, Duration.ofMinutes(1), clock);

        assertTrue(limiter.tryAcquire());
        assertTrue(limiter.tryAcquire());
        assertFalse(limiter.tryAcquire());

        clock.advance(Duration.ofMinutes(1));
        assertTrue(limiter.tryAcquire());
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
            return ZoneId.of("UTC");
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
