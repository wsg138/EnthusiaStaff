package net.enthusia.staff.paper.tester;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import net.enthusia.staff.domain.tester.CheatTesterJournalRecord;
import net.enthusia.staff.domain.tester.CheatTesterType;
import org.bukkit.Location;

final class CheatTesterSession {
    final UUID sessionId;
    final UUID staffId;
    final UUID targetId;
    final CheatTesterType type;
    final Instant startedAt;
    final AtomicBoolean finishing = new AtomicBoolean();
    final AtomicBoolean startedMutation = new AtomicBoolean();
    final AtomicInteger airborneFallResets = new AtomicInteger();
    final AtomicInteger fakeInteractions = new AtomicInteger();
    final AtomicInteger fakeAttacks = new AtomicInteger();
    final AtomicLong firstInteractionMillis = new AtomicLong(-1L);

    volatile boolean assetLock;
    volatile long revision;
    volatile String snapshot;
    volatile PreparedProbe probe = PreparedProbe.NONE;
    volatile StartPoint startPoint;
    volatile StartPoint fakeLocation;
    volatile FakeEntityAdapter.Handle fakeHandle;
    volatile ScheduledTask timeoutTask;
    volatile ScheduledTask sampleTask;
    volatile float previousFallDistance;
    volatile float maxFallDistance;
    volatile double minimumAimAngleDegrees = 180.0D;

    CheatTesterSession(
            UUID sessionId,
            UUID staffId,
            UUID targetId,
            CheatTesterType type,
            boolean assetLock,
            Instant startedAt
    ) {
        this.sessionId = java.util.Objects.requireNonNull(sessionId, "sessionId");
        this.staffId = java.util.Objects.requireNonNull(staffId, "staffId");
        this.targetId = java.util.Objects.requireNonNull(targetId, "targetId");
        this.type = java.util.Objects.requireNonNull(type, "type");
        this.assetLock = assetLock;
        this.startedAt = java.util.Objects.requireNonNull(startedAt, "startedAt");
    }

    static CheatTesterSession recovered(CheatTesterJournalRecord record) {
        CheatTesterSession session = new CheatTesterSession(
                record.sessionId(),
                record.staffId(),
                record.targetId(),
                record.testerType(),
                false,
                record.startedAt()
        );
        session.revision = record.revision();
        session.snapshot = record.snapshot();
        session.startedMutation.set(record.testerType().mutatesTargetState());
        session.finishing.set(true);
        return session;
    }

    record PreparedProbe(int sourceSlot, int armorSlot, int storageSlot) {
        static final PreparedProbe NONE = new PreparedProbe(-1, -1, -1);
    }

    record StartPoint(UUID worldId, double x, double y, double z) {
        static StartPoint capture(Location location) {
            return new StartPoint(
                    location.getWorld().getUID(),
                    location.getX(),
                    location.getY(),
                    location.getZ()
            );
        }
    }
}
