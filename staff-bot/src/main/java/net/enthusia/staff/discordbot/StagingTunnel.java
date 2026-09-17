package net.enthusia.staff.discordbot;

import java.io.IOException;

/** Lifecycle boundary for the staging-only private ingress connector. */
interface StagingTunnel extends AutoCloseable {
    void start(Runnable unexpectedExit) throws IOException;

    @Override
    void close();
}
