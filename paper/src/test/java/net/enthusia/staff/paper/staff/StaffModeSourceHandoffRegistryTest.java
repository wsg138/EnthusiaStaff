package net.enthusia.staff.paper.staff;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class StaffModeSourceHandoffRegistryTest {
    private static final UUID PLAYER = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID TRANSFER = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID OTHER = UUID.fromString("33333333-3333-3333-3333-333333333333");

    @Test
    void conflictingSourceTransferIsRejected() {
        StaffModeSourceHandoffRegistry registry = new StaffModeSourceHandoffRegistry();

        assertTrue(registry.begin(PLAYER, TRANSFER));
        assertFalse(registry.begin(PLAYER, OTHER));
        assertFalse(registry.abort(PLAYER, OTHER));
        assertTrue(registry.abort(PLAYER, TRANSFER));
    }

    @Test
    void abortPreventsLateCommit() {
        StaffModeSourceHandoffRegistry registry = new StaffModeSourceHandoffRegistry();
        AtomicBoolean committed = new AtomicBoolean();
        registry.begin(PLAYER, TRANSFER);

        assertTrue(registry.abort(PLAYER, TRANSFER));
        assertTrue(registry.commitIfActive(PLAYER, TRANSFER, () -> {
            committed.set(true);
            return true;
        }).isEmpty());
        assertFalse(committed.get());
    }

    @Test
    void successfulCommitConsumesTransfer() {
        StaffModeSourceHandoffRegistry registry = new StaffModeSourceHandoffRegistry();
        registry.begin(PLAYER, TRANSFER);

        assertTrue(registry.commitIfActive(PLAYER, TRANSFER, () -> true).orElseThrow());
        assertTrue(registry.commitIfActive(PLAYER, TRANSFER, () -> true).isEmpty());
        assertTrue(registry.abort(PLAYER, TRANSFER));
    }
}
