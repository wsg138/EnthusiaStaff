package net.enthusia.staff.paper.auth;

import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import net.enthusia.staff.domain.OperationalMode;
import net.enthusia.staff.domain.auth.Actor;
import net.enthusia.staff.domain.auth.StaffRank;
import org.junit.jupiter.api.Test;

final class StaffWebReviewServiceTest {
    private static final UUID ACTOR_ID = UUID.randomUUID();
    private static final UUID REQUEST_ID = UUID.randomUUID();

    @Test
    void disabledAuthorityCannotApproveOrDenyEvenWithRank() {
        var deps = new StaffWebReviewService.Dependencies(
                Clock.fixed(Instant.parse("2026-10-10T17:00:00Z"), ZoneOffset.UTC),
                () -> OperationalMode.SHADOW_MIGRATION,
                () -> { throw new AssertionError("disabled mode must not inspect requests"); },
                () -> { throw new AssertionError("disabled mode must not inspect duty"); }
        );
        var web = new StaffWebReviewService(
                deps, id -> new Actor(id, "Moderator", StaffRank.ADMIN), id -> java.util.Optional.empty());
        assertThrows(SecurityException.class,
                () -> web.execute("approve", new StaffWebReviewService.Request(ACTOR_ID, REQUEST_ID, "")));
        assertThrows(SecurityException.class,
                () -> web.execute("deny", new StaffWebReviewService.Request(ACTOR_ID, REQUEST_ID, "test")));
    }

    @Test
    void onlyKnownReviewOperationsAreAccepted() {
        var deps = new StaffWebReviewService.Dependencies(
                Clock.systemUTC(), () -> OperationalMode.ACTIVE, () -> null, () -> null);
        var web = new StaffWebReviewService(deps, id -> new Actor(id, "Admin", StaffRank.ADMIN), id -> java.util.Optional.empty());
        assertThrows(IllegalArgumentException.class,
                () -> web.execute("delete", new StaffWebReviewService.Request(ACTOR_ID, REQUEST_ID, "")));
    }

    @Test
    void developersCannotApproveEvenThoughTheyHaveToolingRank() {
        var deps = new StaffWebReviewService.Dependencies(
                Clock.systemUTC(), () -> OperationalMode.ACTIVE, () -> null,
                () -> { throw new AssertionError("developer rank must be rejected first"); });
        var web = new StaffWebReviewService(deps, id -> new Actor(id, "Developer", StaffRank.DEVELOPER), id -> java.util.Optional.empty());
        assertThrows(SecurityException.class,
                () -> web.execute("approve", new StaffWebReviewService.Request(ACTOR_ID, REQUEST_ID, "")));
    }
}
