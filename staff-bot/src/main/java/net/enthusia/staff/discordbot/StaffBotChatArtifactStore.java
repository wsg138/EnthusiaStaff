package net.enthusia.staff.discordbot;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import net.enthusia.staff.protocol.ChatBridgeArtifact;
import net.enthusia.staff.protocol.ChatBridgeArtifactBundle;

/** Bounded ephemeral cache joining artifact bundles to the later styled chat frame. */
final class StaffBotChatArtifactStore {
    static final int MAXIMUM_EVENTS = 32;
    static final long MAXIMUM_BYTES = 8L * 1_024L * 1_024L;

    private record Entry(ChatBridgeArtifactBundle bundle, long bytes) {
    }

    private final Map<UUID, Entry> entries = new LinkedHashMap<>();
    private long storedBytes;

    synchronized boolean put(ChatBridgeArtifactBundle bundle, long nowEpochMillis) {
        Objects.requireNonNull(bundle, "bundle");
        purgeExpired(nowEpochMillis);
        if (bundle.isExpired(nowEpochMillis)) {
            return false;
        }

        long bytes = bundle.artifacts().stream()
                .mapToLong(artifact -> artifact.data().length)
                .sum();
        // The first admitted artifact bundle owns this event ID until the
        // matching rendered chat frame consumes it. An authenticated retry
        // should be idempotent, not replace the original with later data or
        // delete it merely because a new payload would exceed cache bounds.
        if (entries.containsKey(bundle.eventId())) {
            return true;
        }
        if (entries.size() >= MAXIMUM_EVENTS || storedBytes + bytes > MAXIMUM_BYTES) {
            return false;
        }
        entries.put(bundle.eventId(), new Entry(bundle, bytes));
        storedBytes += bytes;
        return true;
    }

    synchronized List<ChatBridgeArtifact> consume(
            UUID eventId,
            String sourceServerId,
            String logicalChannelId,
            long nowEpochMillis
    ) {
        purgeExpired(nowEpochMillis);
        Entry entry = entries.get(eventId);
        if (entry == null) {
            return List.of();
        }
        ChatBridgeArtifactBundle bundle = entry.bundle();
        // A message from another eligible source/channel must never consume
        // this event's preview. It remains available for the matching frame.
        if (bundle.isExpired(nowEpochMillis)
                || !bundle.sourceServerId().equals(sourceServerId)
                || !bundle.logicalChannelId().equals(logicalChannelId)) {
            return List.of();
        }
        entries.remove(eventId);
        storedBytes -= entry.bytes();
        return bundle.artifacts();
    }

    synchronized int size() {
        return entries.size();
    }

    synchronized long storedBytes() {
        return storedBytes;
    }

    synchronized void clear() {
        entries.clear();
        storedBytes = 0L;
    }

    private void purgeExpired(long nowEpochMillis) {
        var iterator = entries.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, Entry> value = iterator.next();
            if (value.getValue().bundle().isExpired(nowEpochMillis)) {
                storedBytes -= value.getValue().bytes();
                iterator.remove();
            }
        }
    }
}
