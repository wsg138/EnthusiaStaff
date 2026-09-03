package net.enthusia.staff.discordbot;

import java.io.IOException;

/** Local process health endpoint boundary used by lifecycle tests. */
interface HealthEndpoint extends AutoCloseable {
    void start() throws IOException;

    @Override
    void close();
}
