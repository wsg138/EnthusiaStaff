package net.enthusia.staff.discordbot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.RejectedExecutionException;
import org.junit.jupiter.api.Test;

class JdaDiscordRoleReconcilerTest {
    private static final String ROLE_ID = "1001";
    private static final String SECOND_ROLE_ID = "1002";

    @Test
    void mutationPolicyRejectsPublicManagedAndHigherRoles() {
        assertFalse(DiscordRoleMutationPolicy.canMutate(true, false, true));
        assertFalse(DiscordRoleMutationPolicy.canMutate(false, true, true));
        assertFalse(DiscordRoleMutationPolicy.canMutate(false, false, false));
        assertTrue(DiscordRoleMutationPolicy.canMutate(false, false, true));
    }

    @Test
    void asynchronousDiscordFailureBecomesDurableRetrySignal() {
        IllegalStateException discordFailure = new IllegalStateException("rate limited or unavailable");
        DiscordRoleReconciler.RetryableException failure = assertThrows(
                DiscordRoleReconciler.RetryableException.class,
                () -> JdaDiscordRoleReconciler.await(
                        CompletableFuture.failedFuture(discordFailure), "role_add_failed", Set.of(ROLE_ID)));
        assertEquals("role_add_failed", failure.errorCode());
        assertEquals(Set.of(ROLE_ID), failure.observedRoleIds());
        assertSame(discordFailure, failure.getCause());
    }

    @Test
    void synchronousSubmissionFailurePreservesCurrentObservedSnapshot() {
        RejectedExecutionException rejection = new RejectedExecutionException("JDA shutting down");
        DiscordRoleReconciler.RetryableException failure = assertThrows(
                DiscordRoleReconciler.RetryableException.class,
                () -> JdaDiscordRoleReconciler.submitAndAwait(
                        () -> { throw rejection; }, "role_remove_failed", Set.of(ROLE_ID, SECOND_ROLE_ID)));
        assertEquals("role_remove_failed", failure.errorCode());
        assertEquals(Set.of(ROLE_ID, SECOND_ROLE_ID), failure.observedRoleIds());
        assertSame(rejection, failure.getCause());
    }

    @Test
    void partialMutationProgressIsIncludedInLaterFailureSnapshot() {
        JdaDiscordRoleReconciler.MutationProgress progress =
                new JdaDiscordRoleReconciler.MutationProgress(Set.of(ROLE_ID));
        progress.added(SECOND_ROLE_ID);
        IllegalStateException discordFailure = new IllegalStateException("later mutation failed");
        DiscordRoleReconciler.RetryableException failure = assertThrows(
                DiscordRoleReconciler.RetryableException.class,
                () -> JdaDiscordRoleReconciler.await(
                        CompletableFuture.failedFuture(discordFailure),
                        "role_remove_failed", progress.snapshot()));
        assertEquals(Set.of(ROLE_ID, SECOND_ROLE_ID), failure.observedRoleIds());
        progress.removed(ROLE_ID);
        assertEquals(Set.of(SECOND_ROLE_ID), progress.snapshot());
    }
}
