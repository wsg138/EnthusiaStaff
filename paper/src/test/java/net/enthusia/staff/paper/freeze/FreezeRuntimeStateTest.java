package net.enthusia.staff.paper.freeze;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

final class FreezeRuntimeStateTest {
    private static final UUID PLAYER_ID = UUID.fromString("d216b34b-c970-46af-959f-356f839e236b");

    @Test
    void unknownPlayerIsUnrestrictedAndNotRetirable() {
        FreezeRuntimeState state = new FreezeRuntimeState();

        assertFalse(state.isRestricted(PLAYER_ID));
        assertFalse(state.isFrozen(PLAYER_ID));
        assertFalse(state.retire(PLAYER_ID));
        assertFalse(state.resolveVerification(PLAYER_ID, 1L, true));
        assertFalse(state.retireIfCurrent(PLAYER_ID, 1L));
    }

    @Test
    void currentActiveVerificationBecomesConfirmedFrozen() {
        FreezeRuntimeState state = new FreezeRuntimeState();
        long token = state.beginVerification(PLAYER_ID);

        assertTrue(state.isRestricted(PLAYER_ID));
        assertFalse(state.isFrozen(PLAYER_ID));
        assertTrue(state.resolveVerification(PLAYER_ID, token, true));
        assertTrue(state.isRestricted(PLAYER_ID));
        assertTrue(state.isFrozen(PLAYER_ID));
        assertTrue(state.isCurrentFrozen(PLAYER_ID, token));
        assertFalse(state.isVerificationCurrent(PLAYER_ID, token));
    }

    @Test
    void currentInactiveVerificationClearsTheRestriction() {
        FreezeRuntimeState state = new FreezeRuntimeState();
        long token = state.beginVerification(PLAYER_ID);

        assertTrue(state.resolveVerification(PLAYER_ID, token, false));
        assertFalse(state.isRestricted(PLAYER_ID));
        assertFalse(state.isFrozen(PLAYER_ID));
        assertTrue(state.isCurrentRelease(PLAYER_ID, token));
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

        long releaseGeneration = state.release(PLAYER_ID);

        assertFalse(state.resolveVerification(PLAYER_ID, token, true));
        assertFalse(state.isRestricted(PLAYER_ID));
        assertFalse(state.isFrozen(PLAYER_ID));
        assertTrue(state.isCurrentRelease(PLAYER_ID, releaseGeneration));
    }

    @Test
    void manualApplyFencesAStaleInactiveResult() {
        FreezeRuntimeState state = new FreezeRuntimeState();
        long token = state.beginVerification(PLAYER_ID);

        long generation = state.apply(PLAYER_ID);

        assertFalse(state.resolveVerification(PLAYER_ID, token, false));
        assertTrue(state.isRestricted(PLAYER_ID));
        assertTrue(state.isFrozen(PLAYER_ID));
        assertTrue(state.isCurrentFrozen(PLAYER_ID, generation));
    }

    @Test
    void laterStateChangeFencesDelayedFrozenSideEffects() {
        FreezeRuntimeState state = new FreezeRuntimeState();
        long recoveredGeneration = state.beginVerification(PLAYER_ID);
        assertTrue(state.resolveVerification(PLAYER_ID, recoveredGeneration, true));

        state.release(PLAYER_ID);
        long appliedGeneration = state.apply(PLAYER_ID);

        assertFalse(state.isCurrentFrozen(PLAYER_ID, recoveredGeneration));
        assertTrue(state.isCurrentFrozen(PLAYER_ID, appliedGeneration));
    }

    @Test
    void reFreezeFencesDelayedReleaseSideEffects() {
        FreezeRuntimeState state = new FreezeRuntimeState();
        long releaseGeneration = state.release(PLAYER_ID);

        long frozenGeneration = state.apply(PLAYER_ID);

        assertFalse(state.isCurrentRelease(PLAYER_ID, releaseGeneration));
        assertTrue(state.isCurrentFrozen(PLAYER_ID, frozenGeneration));
        assertTrue(state.isRestricted(PLAYER_ID));
    }

    @Test
    void offlineCleanupOnlyRetiresItsOwnGeneration() {
        FreezeRuntimeState state = new FreezeRuntimeState();
        long staleGeneration = state.apply(PLAYER_ID);
        long currentGeneration = state.release(PLAYER_ID);

        assertFalse(state.retireIfCurrent(PLAYER_ID, staleGeneration));
        assertTrue(state.isCurrentRelease(PLAYER_ID, currentGeneration));
        assertTrue(state.retireIfCurrent(PLAYER_ID, currentGeneration));
        assertFalse(state.isRestricted(PLAYER_ID));
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
    void unresolvedCurrentVerificationRemainsFailClosed() {
        FreezeRuntimeState state = new FreezeRuntimeState();
        long token = state.beginVerification(PLAYER_ID);

        assertTrue(state.isVerificationCurrent(PLAYER_ID, token));
        assertTrue(state.isRestricted(PLAYER_ID));
        assertFalse(state.isFrozen(PLAYER_ID));
    }
}
