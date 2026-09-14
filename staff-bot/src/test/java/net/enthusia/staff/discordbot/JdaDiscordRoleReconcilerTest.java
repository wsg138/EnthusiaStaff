package net.enthusia.staff.discordbot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;

class JdaDiscordRoleReconcilerTest {
    private static final String ROLE_ID = "1001";

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
        CompletableFuture<Void> future = CompletableFuture.failedFuture(discordFailure);

        DiscordRoleReconciler.RetryableException failure = assertThrows(
                DiscordRoleReconciler.RetryableException.class,
                () -> JdaDiscordRoleReconciler.await(future, "role_add_failed", Set.of(ROLE_ID))
        );

        assertEquals("role_add_failed", failure.errorCode());
        assertEquals(Set.of(ROLE_ID), failure.observedRoleIds());
        assertSame(discordFailure, failure.getCause());
    }
}
