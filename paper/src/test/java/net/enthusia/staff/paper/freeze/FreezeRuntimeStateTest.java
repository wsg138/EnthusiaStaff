package net.enthusia.staff.paper.freeze;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

final class FreezeRuntimeStateTest {
    private static final UUID PLAYER_ID = UUID.fromString("d216b34b-c970-46af-959f-356f839e236b");

    @Test
    void currentActiveVerificationBecomesConfirmedFrozen() {
        FreezeRuntimeState state = new FreezeRuntimeState();
        long token = state.beginVerification(PLAYER_ID);

        assertTrue(state.isRestricted(PLAYER_ID));
        assertFalse(state.isFrozen(PLAYER_ID));
        assertTrue(state.resolveVerification(PLAYER_ID, token, true));
        assertTrue(state.isRestricted(PLAYER_ID));
        assertTrue(state.isFrozen(PLAYER_ID));
        assertFalse(state.isVerificationCurrent(PLAYER_ID, token));
    }

    @Test
    void currentInactiveVerificationClearsTheRestriction() {
        FreezeRuntimeState state = new FreezeRuntimeState();
        long token = state.beginVerification(PLAYER_ID);

        assertTrue(state.resolveVerification(PLAYER_ID, token, false));
        assertFalse(state.isRestricted(PLAYER_ID));
        assertFalse(state.isFrozen(PLAYER_ID));
    }

    @Test
    void reconnectedSessionRejectsTheOldVerificationResult() {
        FreezeRuntimeState state = new FreezeRuntimeState();
        long oldToken = state.beginVerification(PLAYER_ID);
        long currentToken = state.beginVerification(PLAYER_ID);

        assertNotEquals(oldToken, currentToken);
        assertFalse(state.resolveVerification(PLAYER_ID, oldToken, true));
        assertTrue(state.isVerificationCurrent(PLAYER_ID, currentToken));
        assertTrue(state.resolveVerification(PLAYER_ID, currentToken, false));
        assertFalse(state.isRestricted(PLAYER_ID));
    }

    @Test
    void manualReleaseFencesAStaleActiveResult() {
        FreezeRuntimeState state = new FreezeRuntimeState();
        long token = state.beginVerification(PLAYER_ID);

        state.release(PLAYER_ID);

        assertFalse(state.resolveVerification(PLAYER_ID, token, true));
        assertFalse(state.isRestricted(PLAYER_ID));
        assertFalse(state.isFrozen(PLAYER_ID));
    }

    @Test
    void manualApplyFencesAStaleInactiveResult() {
        FreezeRuntimeState state = new FreezeRuntimeState();
        long token = state.beginVerification(PLAYER_ID);

        state.apply(PLAYER_ID);

        assertFalse(state.resolveVerification(PLAYER_ID, token, false));
        assertTrue(state.isRestricted(PLAYER_ID));
        assertTrue(state.isFrozen(PLAYER_ID));
    }

    @Test
    void quitRetiresPendingVerificationWithoutPersistingDisconnect() {
        FreezeRuntimeState state = new FreezeRuntimeState();
        long token = state.beginVerification(PLAYER_ID);

        assertFalse(state.retire(PLAYER_ID));
        assertFalse(state.resolveVerification(PLAYER_ID, token, true));
        assertFalse(state.isRestricted(PLAYER_ID));
    }

    @Test
    void quitIdentifiesAConfirmedFrozenSessionForOfflinePersistence() {
        FreezeRuntimeState state = new FreezeRuntimeState();
        state.apply(PLAYER_ID);

        assertTrue(state.retire(PLAYER_ID));
        assertFalse(state.isRestricted(PLAYER_ID));
        assertFalse(state.retire(PLAYER_ID));
    }

    @Test
    void failedCurrentVerificationRemainsFailClosed() {
        FreezeRuntimeState state = new FreezeRuntimeState();
        long token = state.beginVerification(PLAYER_ID);

        assertTrue(state.isVerificationCurrent(PLAYER_ID, token));
        assertTrue(state.isRestricted(PLAYER_ID));
        assertFalse(state.isFrozen(PLAYER_ID));
    }
}
