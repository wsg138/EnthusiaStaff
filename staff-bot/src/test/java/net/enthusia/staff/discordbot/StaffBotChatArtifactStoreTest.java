package net.enthusia.staff.discordbot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;
import net.enthusia.staff.protocol.ChatBridgeArtifact;
import net.enthusia.staff.protocol.ChatBridgeArtifactBundle;
import org.junit.jupiter.api.Test;

class StaffBotChatArtifactStoreTest {
    private static final long NOW = 1_800_000_000_000L;

    @Test
    void consumesOnlyMatchingEventSourceAndChannel() {
        StaffBotChatArtifactStore store = new StaffBotChatArtifactStore();
        ChatBridgeArtifactBundle bundle = bundle("SMP", "global", NOW + 30_000);

        assertTrue(store.put(bundle, NOW));
        assertEquals(1, store.size());
        assertTrue(store.consume(bundle.eventId(), "HUB", "global", NOW).isEmpty());
        assertEquals(1, store.size()); // wrong route must not steal this preview

        assertEquals(1, store.consume(bundle.eventId(), "SMP", "global", NOW).size());
        assertEquals(0, store.size());
        assertEquals(0L, store.storedBytes());
    }

    @Test
    void mismatchDoesNotStealBundleBeforeCorrectSourceAndChannelConsume() {
        StaffBotChatArtifactStore store = new StaffBotChatArtifactStore();
        ChatBridgeArtifactBundle original = bundle("TEST", "global", NOW + 30_000);
        assertTrue(store.put(original, NOW));
        long originalBytes = store.storedBytes();

        assertTrue(store.consume(original.eventId(), "SMP", "global", NOW).isEmpty());
        assertTrue(store.consume(original.eventId(), "TEST", "staff", NOW).isEmpty());
        assertEquals(1, store.size());
        assertEquals(originalBytes, store.storedBytes());

        assertEquals(original.artifacts(),
                store.consume(original.eventId(), "TEST", "global", NOW));
        assertEquals(0, store.size());
        assertEquals(0L, store.storedBytes());
    }

    @Test
    void duplicateBundleCannotReplaceFirstValidatedSourceOrEraseItsBytes() {
        StaffBotChatArtifactStore store = new StaffBotChatArtifactStore();
        ChatBridgeArtifactBundle original = bundle("SMP", "global", NOW + 30_000);
        ChatBridgeArtifactBundle conflicting = new ChatBridgeArtifactBundle(
                original.eventId(), original.createdAtEpochMillis(), original.expiresAtEpochMillis(),
                "TEST", "global", List.of(new ChatBridgeArtifact(
                        ChatBridgeArtifact.Kind.ITEM, 7, "Different.png", "image/png",
                        "Later image", new byte[] {8, 9, 10, 11, 12, 13})));

        assertTrue(store.put(original, NOW));
        long originalBytes = store.storedBytes();
        // A replayed/conflicting frame is accepted as an idempotent no-op,
        // keeping the earliest admitted attachment and its original byte budget.
        assertTrue(store.put(conflicting, NOW));
        assertEquals(1, store.size());
        assertEquals(originalBytes, store.storedBytes());
        assertTrue(store.consume(original.eventId(), "TEST", "global", NOW).isEmpty());
        assertEquals(original.artifacts(),
                store.consume(original.eventId(), "SMP", "global", NOW));
        assertEquals(0, store.size());
    }

    @Test
    void fullCacheStillAcknowledgesRetryOfExistingEventWithoutDroppingIt() {
        StaffBotChatArtifactStore store = new StaffBotChatArtifactStore();
        ChatBridgeArtifactBundle first = bundle("SMP", "global", NOW + 30_000);
        assertTrue(store.put(first, NOW));
        for (int index = 1; index < StaffBotChatArtifactStore.MAXIMUM_EVENTS; index++) {
            assertTrue(store.put(bundle("SMP", "global", NOW + 30_000), NOW));
        }
        long bytes = store.storedBytes();
        assertTrue(store.put(first, NOW));
        assertEquals(StaffBotChatArtifactStore.MAXIMUM_EVENTS, store.size());
        assertEquals(bytes, store.storedBytes());
        assertEquals(first.artifacts(), store.consume(first.eventId(), "SMP", "global", NOW));
    }

    @Test
    void expiryAndClearRemoveEphemeralBytes() {
        StaffBotChatArtifactStore store = new StaffBotChatArtifactStore();
        ChatBridgeArtifactBundle bundle = bundle("SMP", "global", NOW + 30_000);
        assertTrue(store.put(bundle, NOW));
        assertTrue(store.storedBytes() > 0);

        assertTrue(store.consume(bundle.eventId(), "SMP", "global", NOW + 30_001).isEmpty());
        assertEquals(0L, store.storedBytes());

        assertTrue(store.put(bundle, NOW));
        store.clear();
        assertEquals(0, store.size());
        assertEquals(0L, store.storedBytes());
    }

    private static ChatBridgeArtifactBundle bundle(
            String sourceServer,
            String logicalChannel,
            long expiresAt
    ) {
        return new ChatBridgeArtifactBundle(
                UUID.randomUUID(),
                NOW,
                expiresAt,
                sourceServer,
                logicalChannel,
                List.of(new ChatBridgeArtifact(
                        ChatBridgeArtifact.Kind.ITEM,
                        3,
                        "Item.png",
                        "image/png",
                        "Shared item",
                        new byte[] {1, 2, 3, 4}
                ))
        );
    }
}
