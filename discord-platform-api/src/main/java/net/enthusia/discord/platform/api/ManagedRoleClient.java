package net.enthusia.discord.platform.api;

import java.util.concurrent.CompletionStage;

/**
 * Namespace-scoped managed-role client.
 *
 * Implementations must reject keys outside {@link #namespace()} and must resolve Minecraft
 * identities through the platform's canonical account-link authority rather than exposing
 * Discord IDs to consumers.
 */
public interface ManagedRoleClient {
    ManagedRoleNamespace namespace();

    DiscordPlatformAvailability availability();

    CompletionStage<ManagedRoleReconcileResult> reconcile(ManagedRoleClaim claim);

    CompletionStage<ManagedRoleDeleteResult> delete(ManagedRoleKey key);
}
