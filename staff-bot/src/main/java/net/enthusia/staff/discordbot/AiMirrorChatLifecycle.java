package net.enthusia.staff.discordbot;

import java.util.Objects;

/** Keep the optional mirror publisher fenced by the existing chat lifecycle. */
final class AiMirrorChatLifecycle implements StaffBotChatLifecycle {
    private final StaffBotChatLifecycle chat;
    private final AiMirrorRegistrationPublisher publisher;

    AiMirrorChatLifecycle(StaffBotChatLifecycle chat, AiMirrorRegistrationPublisher publisher) {
        this.chat = Objects.requireNonNull(chat, "chat");
        this.publisher = Objects.requireNonNull(publisher, "publisher");
    }

    @Override
    public void start() {
        chat.start();
    }

    @Override
    public void resume() {
        publisher.resume();
        chat.resume();
    }

    @Override
    public void pause() {
        publisher.pause();
        chat.pause();
    }

    @Override
    public void close() {
        publisher.close();
        chat.close();
    }
}
