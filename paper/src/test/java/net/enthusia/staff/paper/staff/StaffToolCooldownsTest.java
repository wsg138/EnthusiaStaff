package net.enthusia.staff.paper.staff;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class StaffToolCooldownsTest {
    private static final UUID PLAYER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Test
    void repeatedUseIsBlockedUntilCooldownExpires() {
        MutableClock clock = new MutableClock(Instant.parse("2026-08-07T10:00:00Z"));
        StaffToolCooldowns cooldowns = new StaffToolCooldowns(clock);

        assertTrue(cooldowns.acquire(
                PLAYER_ID,
                StaffToolDefinition.RANDOM_TELEPORT,
                Duration.ofSeconds(2)
        ).allowed());
        StaffToolCooldowns.Result blocked = cooldowns.acquire(
                PLAYER_ID,
                StaffToolDefinition.RANDOM_TELEPORT,
                Duration.ofSeconds(2)
        );
        assertFalse(blocked.allowed());
        assertTrue(blocked.remainingMillis() > 0L);

        clock.advance(Duration.ofSeconds(2));
        assertTrue(cooldowns.acquire(
                PLAYER_ID,
                StaffToolDefinition.RANDOM_TELEPORT,
                Duration.ofSeconds(2)
        ).allowed());
    }

    @Test
    void toolsHaveIndependentCooldownsAndQuitClearRemovesLedger() {
        MutableClock clock = new MutableClock(Instant.parse("2026-08-07T10:00:00Z"));
        StaffToolCooldowns cooldowns = new StaffToolCooldowns(clock);

        assertTrue(cooldowns.acquire(PLAYER_ID, StaffToolDefinition.VANISH, Duration.ofSeconds(5)).allowed());
        assertTrue(cooldowns.acquire(PLAYER_ID, StaffToolDefinition.REPORTS, Duration.ofSeconds(5)).allowed());
        assertFalse(cooldowns.acquire(PLAYER_ID, StaffToolDefinition.VANISH, Duration.ofSeconds(5)).allowed());

        cooldowns.clear(PLAYER_ID);
        assertTrue(cooldowns.acquire(PLAYER_ID, StaffToolDefinition.VANISH, Duration.ofSeconds(5)).allowed());
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
