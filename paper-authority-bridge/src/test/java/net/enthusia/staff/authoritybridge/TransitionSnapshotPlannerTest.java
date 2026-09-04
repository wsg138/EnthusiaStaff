package net.enthusia.staff.authoritybridge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TransitionSnapshotPlannerTest {
    @Test
    void onlyObservedPlayersBecomeImportableDiscordSrvLinks() {
        UUID observed = UUID.randomUUID();
        UUID missing = UUID.randomUUID();
        TransitionSnapshotPlanner.Plan plan = TransitionSnapshotPlanner.plan(
                Map.of("123456789012345678", observed, "223456789012345678", missing),
                List.of(new TransitionSnapshotPlanner.Observation(
                        observed, "ObservedPlayer", Instant.parse("2026-09-04T04:00:00Z"), true)));

        assertEquals(1, plan.observations().size());
        assertEquals(Map.of("123456789012345678", observed), plan.importableLinks());
        assertEquals(1, plan.skippedLinks());
    }

    @Test
    void invalidNamesAreRejectedBeforePersistence() {
        UUID player = UUID.randomUUID();
        TransitionSnapshotPlanner.Plan plan = TransitionSnapshotPlanner.plan(
                Map.of("123456789012345678", player),
                List.of(new TransitionSnapshotPlanner.Observation(
                        player, "invalid name!", Instant.parse("2026-09-04T04:00:00Z"), false)));

        assertTrue(plan.observations().isEmpty());
        assertTrue(plan.importableLinks().isEmpty());
        assertEquals(1, plan.skippedLinks());
    }

    @Test
    void bedrockAliasAndOfflineObservationRemainUsable() {
        UUID player = UUID.randomUUID();
        TransitionSnapshotPlanner.Plan plan = TransitionSnapshotPlanner.plan(
                Map.of("123456789012345678", player),
                List.of(new TransitionSnapshotPlanner.Observation(
                        player, "*BedrockUser", Instant.parse("2026-09-03T23:00:00Z"), false)));

        assertFalse(plan.observations().getFirst().online());
        assertEquals(player, plan.importableLinks().get("123456789012345678"));
    }
}
