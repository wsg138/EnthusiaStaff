package net.enthusia.staff.discordbot;

import java.time.Duration;

/** Adapter boundary around the maintained Discord Gateway client. */
interface DiscordGateway {
    void start(DiscordGatewayObserver observer);

    /** Enables staff interactions only after the runtime identity fence has passed. */
    default void enableInteractions() {
        // Gateways without an interaction surface have nothing to enable.
    }

    void shutdown();

    void shutdownNow();

    boolean awaitShutdown(Duration timeout) throws InterruptedException;
}
