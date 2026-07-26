package net.enthusia.staff.paper.report;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.UUID;
import net.enthusia.staff.domain.report.CreateReportRequest;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

public final class ChatContextBuffer implements Listener {
    private static final Duration WINDOW = Duration.ofMinutes(15);
    private static final int MAX_MESSAGES = 10_000;
    private static final int MAX_BODY = 1_000;

    private final Clock clock;
    private final Deque<CreateReportRequest.ChatContextMessage> messages = new ArrayDeque<>();
    private final Deque<CreateReportRequest.PrivateMessageContextMessage> privateMessages =
            new ArrayDeque<>();

    public ChatContextBuffer(Clock clock) {
        this.clock = clock;
    }

    @SuppressWarnings("deprecation")
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent event) {
        Instant now = clock.instant();
        String body = event.getMessage();
        if (body.length() > MAX_BODY) {
            body = body.substring(0, MAX_BODY);
        }
        synchronized (messages) {
            prunePublic(now);
            messages.addLast(new CreateReportRequest.ChatContextMessage(
                    event.getPlayer().getUniqueId(), event.getPlayer().getName(), body, now
            ));
            while (messages.size() > MAX_MESSAGES) {
                messages.removeFirst();
            }
        }
    }

    public List<CreateReportRequest.ChatContextMessage> snapshot(Instant now) {
        synchronized (messages) {
            prunePublic(now);
            return List.copyOf(new ArrayList<>(messages));
        }
    }

    public void capturePrivate(
            UUID senderId,
            String senderName,
            UUID recipientId,
            String recipientName,
            String body
    ) {
        if (senderId == null || senderName == null || recipientId == null
                || recipientName == null || body == null) {
            throw new IllegalArgumentException("private context message fields are required");
        }
        Instant now = clock.instant();
        String boundedBody = body.length() <= MAX_BODY ? body : body.substring(0, MAX_BODY);
        CreateReportRequest.PrivateMessageContextMessage message =
                new CreateReportRequest.PrivateMessageContextMessage(
                        senderId,
                        senderName,
                        recipientId,
                        recipientName,
                        boundedBody,
                        now
                );
        synchronized (privateMessages) {
            prunePrivate(now);
            privateMessages.addLast(message);
            while (privateMessages.size() > MAX_MESSAGES) {
                privateMessages.removeFirst();
            }
        }
    }

    public List<CreateReportRequest.PrivateMessageContextMessage> privateSnapshot(
            UUID first,
            UUID second,
            Instant now
    ) {
        if (first == null || second == null || now == null) {
            throw new IllegalArgumentException("private context participants and time are required");
        }
        synchronized (privateMessages) {
            prunePrivate(now);
            return privateMessages.stream()
                    .filter(message -> participantsMatch(message, first, second))
                    .toList();
        }
    }

    private void prunePublic(Instant now) {
        Instant cutoff = now.minus(WINDOW);
        while (!messages.isEmpty() && messages.getFirst().sentAt().isBefore(cutoff)) {
            messages.removeFirst();
        }
    }

    private void prunePrivate(Instant now) {
        Instant cutoff = now.minus(WINDOW);
        while (!privateMessages.isEmpty()
                && privateMessages.getFirst().sentAt().isBefore(cutoff)) {
            privateMessages.removeFirst();
        }
    }

    private static boolean participantsMatch(
            CreateReportRequest.PrivateMessageContextMessage message,
            UUID first,
            UUID second
    ) {
        return (message.senderId().equals(first) && message.recipientId().equals(second))
                || (message.senderId().equals(second) && message.recipientId().equals(first));
    }
}
