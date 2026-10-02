package net.enthusia.staff.paper.staff;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class StaffModeHandoffIntentRegistryTest {
    private static final UUID PLAYER = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID FIRST = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID SECOND = UUID.fromString("33333333-3333-3333-3333-333333333333");

    @Test
    void preparedIntentIsOneShotAndSameTransferIsIdempotent() {
        MutableClock clock = new MutableClock();
        StaffModeHandoffIntentRegistry registry = new StaffModeHandoffIntentRegistry(clock);

        assertTrue(registry.prepare(PLAYER, FIRST));
        assertTrue(registry.prepare(PLAYER, FIRST));
        assertEquals(FIRST, registry.consume(PLAYER).orElseThrow());
        assertTrue(registry.consume(PLAYER).isEmpty());
    }

    @Test
    void conflictingLiveTransferIsRejected() {
        StaffModeHandoffIntentRegistry registry = new StaffModeHandoffIntentRegistry(new MutableClock());

        assertTrue(registry.prepare(PLAYER, FIRST));
        assertFalse(registry.prepare(PLAYER, SECOND));
        assertEquals(FIRST, registry.consume(PLAYER).orElseThrow());
    }

    @Test
    void expiredAndCancelledIntentsCannotResume() {
        MutableClock clock = new MutableClock();
        StaffModeHandoffIntentRegistry registry = new StaffModeHandoffIntentRegistry(clock);
        assertTrue(registry.prepare(PLAYER, FIRST));
        clock.advanceSeconds(31);
        assertTrue(registry.consume(PLAYER).isEmpty());

        assertTrue(registry.prepare(PLAYER, SECOND));
        registry.cancel(PLAYER, SECOND);
        assertTrue(registry.consume(PLAYER).isEmpty());
    }

    private static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-10-01T00:00:00Z");

        void advanceSeconds(long seconds) {
            now = now.plusSeconds(seconds);
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
            return now;
        }
    }
}
