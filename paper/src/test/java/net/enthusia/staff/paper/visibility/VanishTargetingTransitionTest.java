package net.enthusia.staff.paper.visibility;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import net.enthusia.staff.domain.auth.StaffRank;
import org.junit.jupiter.api.Test;

class VanishTargetingTransitionTest {
    private static final UUID PLAYER_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");

    @Test
    void reconciliationHookRunsOnlyWhenEnteringFullVanish() {
        DefaultStaffVisibilityService visibility = new DefaultStaffVisibilityService(
                DefaultStaffVisibilityService.defaultMatrix()
        );
        AtomicInteger transitions = new AtomicInteger();
        visibility.setVanishEnabledListener(playerId -> {
            assertEquals(PLAYER_ID, playerId);
            transitions.incrementAndGet();
        });

        visibility.setVanished(PLAYER_ID, StaffRank.MOD, true);
        visibility.setVanished(PLAYER_ID, StaffRank.ADMIN, true);
        visibility.setVanished(PLAYER_ID, StaffRank.ADMIN, false);
        visibility.setVanished(PLAYER_ID, StaffRank.ADMIN, true);

        assertEquals(2, transitions.get());
    }
}
