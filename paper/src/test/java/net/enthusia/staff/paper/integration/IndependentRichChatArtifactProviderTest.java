package net.enthusia.staff.paper.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import net.enthusia.staff.api.chat.RichChatArtifactRequest;
import java.util.List;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;
import net.enthusia.staff.api.chat.RichChatArtifact;
import org.junit.jupiter.api.Test;

class IndependentRichChatArtifactProviderTest {
    private static final String STONE_MATERIAL = "STONE";
    private static final String ITEM_PLACEHOLDER = "[item]";

    @Test
    void placeholderUsesConfiguredRegexWithoutAllowingEscapedMatches() {
        Pattern item = Pattern.compile("\\[item\\]", Pattern.CASE_INSENSITIVE);
        assertEquals(2, IndependentRichChatArtifactProvider.firstUnescapedMatch(
                item, "x [ITEM]"));
        assertEquals(-1, IndependentRichChatArtifactProvider.firstUnescapedMatch(
                item, "x \\[item]"));
        assertEquals(4, IndependentRichChatArtifactProvider.firstUnescapedMatch(
                item, "x \\\\[item]"));
        assertEquals(15, IndependentRichChatArtifactProvider.firstUnescapedMatch(
                item, "x \\[item] then [item]"));
    }

    @Test
    void nativeKindsRespectExistingPermissionsAndArtifactTypes() {
        var kinds = IndependentRichChatArtifactProvider.Kind.values();
        assertEquals("interactivechat.module.item", kinds[0].permission);
        assertEquals("interactivechat.module.inventory", kinds[1].permission);
        assertEquals("interactivechat.module.enderchest", kinds[2].permission);
        assertEquals(RichChatArtifact.Kind.ITEM, kinds[0].artifactKind);
        assertEquals(RichChatArtifact.Kind.ENDER_CHEST, kinds[2].artifactKind);
    }

    @Test
    void producesBoundedRealPngCardsWithoutAnyUpstreamAddonClass() throws Exception {
        var slots = new ArrayList<IndependentRichChatArtifactProvider.Slot>();
        for (int i = 0; i < 54; i++) {
            slots.add(new IndependentRichChatArtifactProvider.Slot(
                    "ENCHANTED_DIAMOND_SWORD", 64));
        }
        BufferedImage grid = IndependentRichChatArtifactProvider.renderCard(
                IndependentRichChatArtifactProvider.Kind.INVENTORY, slots);
        assertEquals(788, grid.getWidth());
        assertEquals(445, grid.getHeight());
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        assertTrue(ImageIO.write(grid, "PNG", bytes));
        assertFalse(bytes.size() == 0);
        assertTrue(bytes.size() <= RichChatArtifact.MAX_ARTIFACT_BYTES);

        BufferedImage single = IndependentRichChatArtifactProvider.renderCard(
                IndependentRichChatArtifactProvider.Kind.ITEM,
                List.of(new IndependentRichChatArtifactProvider.Slot("DIAMOND", 5)));
        assertEquals(116, single.getWidth());
        assertEquals(135, single.getHeight());
    }

    @Test
    void rejectsInvalidJobAndSnapshotInputs() {
        assertThrows(IllegalArgumentException.class,
                () -> new IndependentRichChatArtifactProvider.Job(
                        IndependentRichChatArtifactProvider.Kind.ITEM, -1, List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> new IndependentRichChatArtifactProvider.Slot(STONE_MATERIAL, 200));
        assertThrows(IllegalArgumentException.class,
                () -> IndependentRichChatArtifactProvider.renderCard(
                        IndependentRichChatArtifactProvider.Kind.INVENTORY, List.of()));
        List<IndependentRichChatArtifactProvider.Slot> excessive =
                java.util.Collections.nCopies(55,
                        IndependentRichChatArtifactProvider.Slot.empty());
        assertThrows(IllegalArgumentException.class,
                () -> IndependentRichChatArtifactProvider.renderCard(
                        IndependentRichChatArtifactProvider.Kind.INVENTORY, excessive));
    }

    @Test
    void refusesDelayedSnapshotsBeforeRenderingAnyPlayerState() {
        RichChatArtifactRequest request = request(ITEM_PLACEHOLDER);
        assertFalse(IndependentRichChatArtifactProvider.snapshotWindowExpired(request, 11_999L));
        assertTrue(IndependentRichChatArtifactProvider.snapshotWindowExpired(request, 12_000L));
        assertTrue(IndependentRichChatArtifactProvider.snapshotWindowExpired(request, 50_000L));
    }

    @Test
    void neverReadsInventoryForDeniedPermissionsOrQuitPlayer() {
        AtomicInteger reads = new AtomicInteger();
        var rule = itemRule();
        var snapshot = (java.util.function.Supplier<List<IndependentRichChatArtifactProvider.Slot>>) () -> {
            reads.incrementAndGet();
            return List.of(new IndependentRichChatArtifactProvider.Slot(STONE_MATERIAL, 1));
        };

        assertTrue(IndependentRichChatArtifactProvider.captureJob(
                rule, () -> false, () -> true, ITEM_PLACEHOLDER, snapshot).isEmpty());
        assertTrue(IndependentRichChatArtifactProvider.captureJob(
                rule, () -> true, () -> false, ITEM_PLACEHOLDER, snapshot).isEmpty());
        assertTrue(IndependentRichChatArtifactProvider.captureJob(
                rule, () -> true, () -> true, "ordinary chat", snapshot).isEmpty());
        assertEquals(0, reads.get());
    }

    @Test
    void entityOwnedCaptureFreezesSlotSnapshotBeforeWorkerRendering() {
        AtomicInteger reads = new AtomicInteger();
        List<IndependentRichChatArtifactProvider.Slot> hand = new ArrayList<>(
                List.of(new IndependentRichChatArtifactProvider.Slot(STONE_MATERIAL, 3)));
        var captured = IndependentRichChatArtifactProvider.captureJob(
                itemRule(), () -> true, () -> true, ITEM_PLACEHOLDER, () -> {
                    reads.incrementAndGet();
                    return hand;
                }).orElseThrow();
        assertEquals(1, reads.get());
        hand.set(0, new IndependentRichChatArtifactProvider.Slot("DIAMOND", 5));
        assertEquals(List.of(new IndependentRichChatArtifactProvider.Slot(STONE_MATERIAL, 3)),
                captured.slots());
        assertEquals(1, reads.get());
    }

    @Test
    void capturedSnapshotCanRenderAfterFreshnessWindowButBeforeHardExpiry() {
        RichChatArtifactRequest request = request(ITEM_PLACEHOLDER);
        assertTrue(IndependentRichChatArtifactProvider.snapshotWindowExpired(request, 12_001L));
        assertFalse(IndependentRichChatArtifactProvider.shouldDrop(false, request, 12_001L));
        assertTrue(IndependentRichChatArtifactProvider.shouldDrop(true, request, 11_000L));
        assertTrue(IndependentRichChatArtifactProvider.shouldDrop(false, request, 50_000L));
    }

    @Test
    void closedRendererOrRejectedWorkerCannotRenderOptionalArtifact() {
        var request = request(ITEM_PLACEHOLDER);
        assertTrue(IndependentRichChatArtifactProvider.shouldDrop(true, request, 10_100L));
        CompletableFuture<List<RichChatArtifact>> result = new CompletableFuture<>();
        AtomicInteger executed = new AtomicInteger();
        IndependentRichChatArtifactProvider.enqueueSnapshot(
                command -> {
                    throw new RejectedExecutionException("queue full");
                },
                executed::incrementAndGet,
                result);
        assertTrue(result.isDone());
        assertEquals(List.of(), result.join());
        assertEquals(0, executed.get());
    }

    @Test
    void workerStartupFailureCompletesTextOnlyWithoutRendering() {
        CompletableFuture<List<RichChatArtifact>> result = new CompletableFuture<>();
        AtomicInteger executed = new AtomicInteger();
        IndependentRichChatArtifactProvider.enqueueSnapshot(
                command -> {
                    throw new IllegalStateException("worker already stopped");
                },
                executed::incrementAndGet,
                result);
        assertEquals(List.of(), result.join());
        assertEquals(0, executed.get());
    }

    @Test
    void quitBetweenChatAndDelayedCaptureDoesNotReadInventory() {
        AtomicBoolean online = new AtomicBoolean(true);
        AtomicInteger reads = new AtomicInteger();
        var inventory = (java.util.function.Supplier<List<IndependentRichChatArtifactProvider.Slot>>) () -> {
            reads.incrementAndGet();
            return List.of(new IndependentRichChatArtifactProvider.Slot(STONE_MATERIAL, 1));
        };
        online.set(false); // The player quit while an entity-owned callback was queued.
        assertTrue(IndependentRichChatArtifactProvider.captureJob(
                itemRule(), online::get, () -> true, ITEM_PLACEHOLDER, inventory).isEmpty());
        assertEquals(0, reads.get());
    }

    @Test
    void acceptedWorkerKeepsImmutableSnapshotUntilItsSingleExecution() {
        AtomicReference<Runnable> queued = new AtomicReference<>();
        CompletableFuture<List<RichChatArtifact>> result = new CompletableFuture<>();
        AtomicInteger executed = new AtomicInteger();
        IndependentRichChatArtifactProvider.enqueueSnapshot(
                queued::set, () -> {
                    executed.incrementAndGet();
                    result.complete(List.of());
                }, result);
        assertFalse(result.isDone());
        queued.get().run();
        assertEquals(1, executed.get());
        assertEquals(List.of(), result.join());
    }

    private static IndependentRichChatArtifactProvider.MatchRule itemRule() {
        return new IndependentRichChatArtifactProvider.MatchRule(
                IndependentRichChatArtifactProvider.Kind.ITEM,
                Pattern.compile("\\[item\\]"), true);
    }

    private static RichChatArtifactRequest request(String text) {
        return new RichChatArtifactRequest(
                UUID.fromString("10000000-0000-0000-0000-000000000001"),
                UUID.fromString("20000000-0000-0000-0000-000000000001"),
                "Tester", "global", text, 10_000L, 50_000L);
    }


}
