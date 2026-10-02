package net.enthusia.discord.platform.api;

import java.util.Optional;

/**
 * Discoverable entry point for the shared managed-role platform.
 *
 * <p>The EnthusiaStaff Paper runtime registers one implementation through Bukkit's
 * ServicesManager. Consumers request a namespace-scoped client and never receive Discord
 * identities, JDA objects, persistence records, or unrestricted role-mutation access.
 */
public interface ManagedRolePlatform {
    int API_VERSION = 1;

    /** Contract version implemented by this provider. */
    int apiVersion();

    /** Overall runtime availability of the Discord platform. */
    DiscordPlatformAvailability availability();

    /**
     * Returns a client only when this namespace is explicitly allowed for the caller/runtime.
     * Implementations must never manufacture unrestricted clients for unknown namespaces.
     */
    Optional<ManagedRoleClient> clientFor(ManagedRoleNamespace namespace);
}
