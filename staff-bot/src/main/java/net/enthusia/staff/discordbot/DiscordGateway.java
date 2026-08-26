package net.enthusia.staff.discordbot;

import java.time.Duration;

/** Adapter boundary around the maintained Discord Gateway client. */
interface DiscordGateway {
    void start(DiscordGatewayObserver observer);

    void shutdown();

    void shutdownNow();

    boolean awaitShutdown(Duration timeout) throws InterruptedException;
}
