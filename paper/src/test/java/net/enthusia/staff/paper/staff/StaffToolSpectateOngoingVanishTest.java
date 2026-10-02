package net.enthusia.staff.paper.staff;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;

class StaffToolSpectateOngoingVanishTest {
    @Test
    void targetEnteringVanishAfterAttachmentDetachesSpectator() {
        StaffToolSpectateFlowHarness harness = new StaffToolSpectateFlowHarness();
        harness.finishSuccessfulFlow();

        assertSame(harness.target.player, harness.actor.lastSpectator);
        harness.vanished.add(harness.targetId);

        harness.actor.runRecurring();

        assertNull(harness.actor.lastSpectator);
        assertEquals(1, harness.actor.attachments);
        assertEquals(1, harness.actor.detachments);
    }

    @Test
    void actorLosingAuthorityAfterAttachmentDetachesSpectator() {
        StaffToolSpectateFlowHarness harness = new StaffToolSpectateFlowHarness();
        harness.finishSuccessfulFlow();

        assertSame(harness.target.player, harness.actor.lastSpectator);
        harness.actorAuthority.set(false);

        harness.actor.runRecurring();

        assertNull(harness.actor.lastSpectator);
        assertEquals(1, harness.actor.attachments);
        assertEquals(1, harness.actor.detachments);
    }
}
