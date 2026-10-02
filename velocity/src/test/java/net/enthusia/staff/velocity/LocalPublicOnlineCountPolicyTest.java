package net.enthusia.staff.velocity;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class LocalPublicOnlineCountPolicyTest {
    private static final String SMP = "SMP";
    private static final String HUB = "HUB";

    private final UUID ordinarySmp = UUID.randomUUID();
    private final UUID visibleStaffSmp = UUID.randomUUID();
    private final UUID vanishedSmp = UUID.randomUUID();
    private final UUID ordinaryHub = UUID.randomUUID();
    private final UUID vanishedHub = UUID.randomUUID();

    @Test
    void localCountsIncludeVisiblePlayersAndExcludeVanishedStaffPerBackend() {
        Map<UUID, String> backends = Map.of(
                ordinarySmp, SMP,
                visibleStaffSmp, SMP,
                vanishedSmp, SMP,
                ordinaryHub, HUB,
                vanishedHub, HUB
        );
        Set<UUID> vanished = Set.of(vanishedSmp, vanishedHub);

        assertEquals(2, LocalPublicOnlineCountPolicy.count(true, SMP, backends, vanished));
        assertEquals(1, LocalPublicOnlineCountPolicy.count(true, HUB, backends, vanished));
    }

    @Test
    void visibleTransferMovesCountFromSourceToDestination() {
        Map<UUID, String> before = Map.of(ordinarySmp, SMP, ordinaryHub, HUB);
        Map<UUID, String> after = Map.of(ordinarySmp, HUB, ordinaryHub, HUB);

        assertEquals(1, LocalPublicOnlineCountPolicy.count(true, SMP, before, Set.of()));
        assertEquals(1, LocalPublicOnlineCountPolicy.count(true, HUB, before, Set.of()));
        assertEquals(0, LocalPublicOnlineCountPolicy.count(true, SMP, after, Set.of()));
        assertEquals(2, LocalPublicOnlineCountPolicy.count(true, HUB, after, Set.of()));
    }

    @Test
    void vanishedTransferContributesToNeitherBackend() {
        Map<UUID, String> before = Map.of(vanishedSmp, SMP, ordinaryHub, HUB);
        Map<UUID, String> after = Map.of(vanishedSmp, HUB, ordinaryHub, HUB);
        Set<UUID> vanished = Set.of(vanishedSmp);

        assertEquals(0, LocalPublicOnlineCountPolicy.count(true, SMP, before, vanished));
        assertEquals(1, LocalPublicOnlineCountPolicy.count(true, HUB, before, vanished));
        assertEquals(0, LocalPublicOnlineCountPolicy.count(true, SMP, after, vanished));
        assertEquals(1, LocalPublicOnlineCountPolicy.count(true, HUB, after, vanished));
    }

    @Test
    void staleOrMissingViewerServerContextFailsClosed() {
        Map<UUID, String> backends = Map.of(ordinarySmp, SMP);

        assertEquals(0, LocalPublicOnlineCountPolicy.count(false, SMP, backends, Set.of()));
        assertEquals(0, LocalPublicOnlineCountPolicy.count(true, null, backends, Set.of()));
        assertEquals(0, LocalPublicOnlineCountPolicy.count(true, "", backends, Set.of()));
    }
}
