package net.enthusia.staff.discordbot;

import java.util.Objects;

/** Two authenticated peers with one existing Discord session; no extra JDA login. */
final class StaffBotDualChatLifecycle implements StaffBotChatLifecycle {
    private final StaffBotChatLifecycle primary;
    private final StaffBotChatLifecycle test;

    StaffBotDualChatLifecycle(StaffBotChatLifecycle primary, StaffBotChatLifecycle test) {
        this.primary = Objects.requireNonNull(primary, "primary");
        this.test = Objects.requireNonNull(test, "test");
        if (primary == test) {
            throw new IllegalArgumentException("primary and TEST must be different transports");
        }
    }

    @Override
    public void start() {
        primary.start();
        try {
            test.start();
        } catch (RuntimeException failure) {
            primary.pause();
            throw failure;
        }
    }

    @Override
    public void resume() {
        primary.resume();
        try {
            test.resume();
        } catch (RuntimeException failure) {
            primary.pause();
            throw failure;
        }
    }

    @Override
    public void pause() {
        try {
            test.pause();
        } finally {
            primary.pause();
        }
    }

    @Override
    public void close() {
        try {
            test.close();
        } finally {
            primary.close();
        }
    }
}
