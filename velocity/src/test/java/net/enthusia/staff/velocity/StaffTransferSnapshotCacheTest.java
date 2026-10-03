package net.enthusia.staff.velocity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import net.enthusia.staff.domain.auth.StaffRank;
import net.enthusia.staff.domain.staff.StaffTransferSnapshot;
import org.junit.jupiter.api.Test;

/** Proxy-side transfer snapshot cache semantics (overnight/cross-server). */
class StaffTransferSnapshotCacheTest {
    private static final UUID PLAYER = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID TRANSFER = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID OTHER_TRANSFER = UUID.fromString("44444444-4444-4444-4444-444444444444");

    @Test
    void takeConsumesTheSnapshot() {
        StaffTransferSnapshotCache cache = cacheAt(Instant.parse("2026-10-03T00:00:00Z"));
        cache.put(snapshot());

        assertEquals(snapshot(), cache.take(PLAYER, TRANSFER).orElseThrow());
        assertTrue(cache.take(PLAYER, TRANSFER).isEmpty(), "snapshot must be single-consume");
    }

    @Test
    void takeWithMismatchedTransferIdIsEmpty() {
        StaffTransferSnapshotCache cache = cacheAt(Instant.parse("2026-10-03T00:00:00Z"));
        cache.put(snapshot());

        assertTrue(cache.take(PLAYER, OTHER_TRANSFER).isEmpty());
    }

    @Test
    void expiredSnapshotIsNotReturned() {
        MutableClock clock = new MutableClock(Instant.parse("2026-10-03T00:00:00Z"));
        StaffTransferSnapshotCache cache = new StaffTransferSnapshotCache(clock);
        cache.put(snapshot());

        clock.advance(Duration.ofSeconds(31));

        assertTrue(cache.take(PLAYER, TRANSFER).isEmpty(), "30s TTL must expire the snapshot");
    }

    @Test
    void snapshotWithinTtlIsReturned() {
        MutableClock clock = new MutableClock(Instant.parse("2026-10-03T00:00:00Z"));
        StaffTransferSnapshotCache cache = new StaffTransferSnapshotCache(clock);
        cache.put(snapshot());

        clock.advance(Duration.ofSeconds(29));

        assertTrue(cache.take(PLAYER, TRANSFER).isPresent());
    }

    @Test
    void evictDropsTheSnapshot() {
        StaffTransferSnapshotCache cache = cacheAt(Instant.parse("2026-10-03T00:00:00Z"));
        cache.put(snapshot());

        cache.evict(PLAYER);

        assertTrue(cache.take(PLAYER, TRANSFER).isEmpty());
    }

    @Test
    void takeWithoutUploadIsEmpty() {
        StaffTransferSnapshotCache cache = cacheAt(Instant.parse("2026-10-03T00:00:00Z"));

        assertTrue(cache.take(PLAYER, TRANSFER).isEmpty());
    }

    @Test
    void takeWithNullIdsIsEmpty() {
        StaffTransferSnapshotCache cache = cacheAt(Instant.parse("2026-10-03T00:00:00Z"));
        cache.put(snapshot());

        assertTrue(cache.take(null, TRANSFER).isEmpty());
        assertTrue(cache.take(PLAYER, null).isEmpty());
    }

    @Test
    void putIgnoresNullSnapshot() {
        StaffTransferSnapshotCache cache = cacheAt(Instant.parse("2026-10-03T00:00:00Z"));

        cache.put(null);

        assertTrue(cache.take(PLAYER, TRANSFER).isEmpty());
    }

    private static StaffTransferSnapshot snapshot() {
        return new StaffTransferSnapshot(PLAYER, TRANSFER, "temp", true, true, StaffRank.ADMIN, "SPECTATOR", 1L);
    }

    private static StaffTransferSnapshotCache cacheAt(Instant instant) {
        return new StaffTransferSnapshotCache(Clock.fixed(instant, ZoneId.of("UTC")));
    }

    private static final class MutableClock extends Clock {
        private final AtomicLong millis;

        private MutableClock(Instant start) {
            this.millis = new AtomicLong(start.toEpochMilli());
        }

        void advance(Duration duration) {
            millis.addAndGet(duration.toMillis());
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
            return Instant.ofEpochMilli(millis.get());
        }
    }
}
