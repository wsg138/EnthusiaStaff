package net.enthusia.staff.velocity;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PublicOnlineCountPolicyTest {
    private final UUID ordinary = UUID.randomUUID();
    private final UUID visibleStaff = UUID.randomUUID();
    private final UUID hiddenOne = UUID.randomUUID();
    private final UUID hiddenTwo = UUID.randomUUID();

    @Test
    void verifiedNetworkCountExcludesEveryVanishedStaffMember() {
        Set<UUID> online = Set.of(ordinary, visibleStaff, hiddenOne, hiddenTwo);
        assertEquals(2, PublicOnlineCountPolicy.count(true, online, Set.of(hiddenOne, hiddenTwo)));
    }

    @Test
    void joinsQuitsTransfersAndReconnectsUseTheVerifiedNetworkPopulation() {
        Set<UUID> hidden = Set.of(hiddenOne);
        assertEquals(1, PublicOnlineCountPolicy.count(true, Set.of(ordinary, hiddenOne), hidden));
        assertEquals(2, PublicOnlineCountPolicy.count(true, Set.of(ordinary, visibleStaff, hiddenOne), hidden));
        assertEquals(2, PublicOnlineCountPolicy.count(true, Set.of(ordinary, visibleStaff, hiddenOne), hidden),
                "a backend transfer must not change the network-wide count");
        assertEquals(1, PublicOnlineCountPolicy.count(true, Set.of(visibleStaff, hiddenOne), hidden));
        assertEquals(2, PublicOnlineCountPolicy.count(true, Set.of(ordinary, visibleStaff, hiddenOne), hidden));
    }

    @Test
    void unavailableOrStaleAuthorityNeverFallsBackToRawProxyCount() {
        Set<UUID> online = Set.of(ordinary, visibleStaff, hiddenOne);
        assertEquals(0, PublicOnlineCountPolicy.count(false, online, Set.of()));
        assertEquals(0, PublicOnlineCountPolicy.count(false, online, Set.of(hiddenOne)));
    }
}
