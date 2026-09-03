package net.enthusia.staff.discordbot;

/** Lifecycle callbacks contain only privacy-safe control-plane facts. */
interface DiscordGatewayObserver {
    void onIdentityResolved(DiscordRuntimeIdentity identity);

    void onDisconnected();

    void onFatal(String reason);

    void onShutdown();
}
