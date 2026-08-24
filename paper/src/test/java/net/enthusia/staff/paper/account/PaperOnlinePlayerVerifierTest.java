package net.enthusia.staff.paper.account;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PaperOnlinePlayerVerifierTest {
    @Test
    void asyncReadersObserveOnlyTheEventMaintainedUuidSnapshot() {
        UUID initiallyOnline = UUID.randomUUID();
        UUID joinsLater = UUID.randomUUID();
        PaperOnlinePlayerVerifier verifier = new PaperOnlinePlayerVerifier(Set.of(initiallyOnline));

        assertTrue(verifier.isOnline(initiallyOnline));
        assertFalse(verifier.isOnline(joinsLater));
        assertFalse(verifier.isOnline(null));

        verifier.markOnline(joinsLater);
        assertTrue(verifier.isOnline(joinsLater));

        verifier.markOffline(initiallyOnline);
        assertFalse(verifier.isOnline(initiallyOnline));
    }
}
