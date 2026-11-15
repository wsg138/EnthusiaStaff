package net.enthusia.staff.domain.modcase;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CaseNoticeTest {
    private static final Instant NOW = Instant.parse("2026-10-14T16:00:00Z");

    @Test
    void evidenceListIsImmutableAndCannotContainRawMessageBodies() {
        List<String> evidence = new ArrayList<>(List.of("evt:1"));
        CaseNotice notice = new CaseNotice(UUID.randomUUID(), CaseNotice.Origin.MINECRAFT,
                "smp/chat", 1, CaseNotice.Priority.HIGH, CaseNotice.State.OPEN,
                evidence, NOW, NOW.plusSeconds(60), null);
        evidence.add("evt:2");
        assertEquals(List.of("evt:1"), notice.evidenceRefs());
        assertThrows(UnsupportedOperationException.class, () -> notice.evidenceRefs().add("evt:3"));
        assertThrows(IllegalArgumentException.class, () -> new CaseNotice(UUID.randomUUID(),
                CaseNotice.Origin.DISCORD, "private room", 1, CaseNotice.Priority.NORMAL,
                CaseNotice.State.OPEN, List.of("raw message"), NOW, NOW.plusSeconds(60), null));
    }

    @Test
    void staffResolutionNeedsClaimantAndAlwaysBumpsRevision() {
        CaseNotice open = new CaseNotice(UUID.randomUUID(), CaseNotice.Origin.MINECRAFT,
                "smp", 3, CaseNotice.Priority.NORMAL, CaseNotice.State.OPEN,
                List.of("evt:1"), NOW, NOW.plusSeconds(90), null);
        CaseNotice claimed = open.apply(CaseNotice.Action.CLAIM, "staff:one");
        assertEquals(4, claimed.revision());
        assertEquals(CaseNotice.State.CLAIMED, claimed.state());
        assertThrows(IllegalStateException.class,
                () -> claimed.apply(CaseNotice.Action.RESOLVE, "staff:other"));
        CaseNotice resolved = claimed.apply(CaseNotice.Action.RESOLVE, "staff:one");
        assertEquals(5, resolved.revision());
        assertEquals(CaseNotice.State.RESOLVED, resolved.state());
    }
}
