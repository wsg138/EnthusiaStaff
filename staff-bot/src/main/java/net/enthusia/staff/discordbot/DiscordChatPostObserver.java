package net.enthusia.staff.discordbot;

/**
 * Optional post-send handoff for authentic Discord message identities.
 *
 * <p>Called only after a successful JDA send; implementations must be
 * non-blocking and enqueue work on an independently bounded executor.
 * Never make network requests or perform message deletion inside this callback.
 * A failed observer must not change the already-completed chat delivery.</p>
 */
@FunctionalInterface
interface DiscordChatPostObserver {
    void published(DiscordChatPublication publication);
}
