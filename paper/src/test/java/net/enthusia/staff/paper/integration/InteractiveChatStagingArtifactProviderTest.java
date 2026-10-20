package net.enthusia.staff.paper.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import net.enthusia.staff.api.chat.RichChatArtifact;
import net.enthusia.staff.api.chat.RichChatArtifactRequest;
import java.util.UUID;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class InteractiveChatStagingArtifactProviderTest {

    private static final Pattern ITEM = Pattern.compile("\\[item\\]");

    @Test
    void addonRenderBoundaryDropsExpiredOrShutdownPreviews() {
        RichChatArtifactRequest request = syntheticRequest();
        assertFalse(InteractiveChatStagingArtifactProvider.shouldDrop(false, 10_999L, request));
        assertTrue(InteractiveChatStagingArtifactProvider.shouldDrop(false, 11_000L, request));
        assertTrue(InteractiveChatStagingArtifactProvider.shouldDrop(true, 10_000L, request));

        RichChatArtifact preview = new RichChatArtifact(
                RichChatArtifact.Kind.ITEM, 0, "icon.png", "image/png", "Item", new byte[]{1});
        CompletableFuture<List<RichChatArtifact>> inTime = new CompletableFuture<>();
        InteractiveChatStagingArtifactProvider.completeIfActive(
                false, 10_999L, request, inTime, List.of(preview));
        assertEquals(List.of(preview), inTime.join());

        CompletableFuture<List<RichChatArtifact>> late = new CompletableFuture<>();
        InteractiveChatStagingArtifactProvider.completeIfActive(
                false, 11_000L, request, late, List.of(preview));
        assertEquals(List.of(), late.join());

        CompletableFuture<List<RichChatArtifact>> shutDown = new CompletableFuture<>();
        InteractiveChatStagingArtifactProvider.completeIfActive(
                true, 10_999L, request, shutDown, List.of(preview));
        assertEquals(List.of(), shutDown.join());
    }

    @Test
    void addonRenderCompletionFreezesReturnedListForWorkerHandoff() {
        RichChatArtifactRequest request = syntheticRequest();
        RichChatArtifact preview = new RichChatArtifact(
                RichChatArtifact.Kind.ITEM, 0, "icon.png", "image/png", "Item", new byte[]{1});
        var produced = new ArrayList<>(List.of(preview));
        CompletableFuture<List<RichChatArtifact>> completed = new CompletableFuture<>();
        InteractiveChatStagingArtifactProvider.completeIfActive(
                false, 10_999L, request, completed, produced);
        produced.clear();
        assertEquals(List.of(preview), completed.join());
        assertThrows(UnsupportedOperationException.class, () -> completed.join().clear());
    }

    @Test
    void workerRejectionOrShutdownFailsToTextOnlyWithoutRendering() {
        CompletableFuture<List<RichChatArtifact>> rejected = new CompletableFuture<>();
        AtomicInteger renders = new AtomicInteger();
        InteractiveChatStagingArtifactProvider.enqueueSnapshot(
                task -> { throw new RejectedExecutionException("queue full"); },
                renders::incrementAndGet, rejected);
        assertEquals(List.of(), rejected.join());
        assertEquals(0, renders.get());

        CompletableFuture<List<RichChatArtifact>> terminated = new CompletableFuture<>();
        InteractiveChatStagingArtifactProvider.enqueueSnapshot(
                task -> { throw new IllegalStateException("worker terminated"); },
                renders::incrementAndGet, terminated);
        assertEquals(List.of(), terminated.join());
        assertEquals(0, renders.get());
    }

    @Test
    void queuedWorkerDoesNotRenderUntilAcceptedActionRuns() {
        java.util.concurrent.atomic.AtomicReference<Runnable> queued =
                new java.util.concurrent.atomic.AtomicReference<>();
        CompletableFuture<List<RichChatArtifact>> pending = new CompletableFuture<>();
        AtomicInteger executions = new AtomicInteger();
        InteractiveChatStagingArtifactProvider.enqueueSnapshot(
                queued::set,
                () -> {
                    executions.incrementAndGet();
                    pending.complete(List.of());
                },
                pending);
        assertFalse(pending.isDone());
        queued.get().run();
        assertEquals(1, executions.get());
        assertEquals(List.of(), pending.join());
    }

    private static RichChatArtifactRequest syntheticRequest() {
        return new RichChatArtifactRequest(
                UUID.fromString("10000000-0000-0000-0000-000000000001"),
                UUID.fromString("20000000-0000-0000-0000-000000000001"),
                "Tester", "global", "[item]", 10_000L, 11_000L);
    }



    @Test
    void tooltipAndIconBundleStayWithinAggregateSizeAndMaximumCount() {
        var first = new net.enthusia.staff.api.chat.RichChatArtifact(
                net.enthusia.staff.api.chat.RichChatArtifact.Kind.ITEM,
                0, "icon.png", "image/png", "Item", new byte[240_000]);
        var tooLarge = new net.enthusia.staff.api.chat.RichChatArtifact(
                net.enthusia.staff.api.chat.RichChatArtifact.Kind.TOOLTIP,
                0, "tooltip.png", "image/png", "Details", new byte[240_000]);
        var small = new net.enthusia.staff.api.chat.RichChatArtifact(
                net.enthusia.staff.api.chat.RichChatArtifact.Kind.TOOLTIP,
                0, "small.png", "image/png", "Details", new byte[10]);
        var result = InteractiveChatStagingArtifactProvider.withinBundleBudget(
                List.of(first, tooLarge, small));
        assertEquals(List.of(first, small), result);
    }

    @Test
    void budgetRejectsOrphanTooltipIfItemIconCouldNotFit() {
        var inventory = new net.enthusia.staff.api.chat.RichChatArtifact(
                net.enthusia.staff.api.chat.RichChatArtifact.Kind.INVENTORY,
                0, "inventory.png", "image/png", "Inventory", new byte[240_000]);
        var skippedIcon = new net.enthusia.staff.api.chat.RichChatArtifact(
                net.enthusia.staff.api.chat.RichChatArtifact.Kind.ITEM,
                8, "icon.png", "image/png", "Item", new byte[240_000]);
        var orphanTooltip = new net.enthusia.staff.api.chat.RichChatArtifact(
                net.enthusia.staff.api.chat.RichChatArtifact.Kind.TOOLTIP,
                8, "tooltip.png", "image/png", "Details", new byte[10_000]);
        var laterPreview = new net.enthusia.staff.api.chat.RichChatArtifact(
                net.enthusia.staff.api.chat.RichChatArtifact.Kind.MAP,
                22, "map.png", "image/png", "Map", new byte[100_000]);

        var kept = InteractiveChatStagingArtifactProvider.withinBundleBudget(
                List.of(inventory, skippedIcon, orphanTooltip, laterPreview));
        assertEquals(List.of(inventory, laterPreview), kept);
    }

    @Test
    void budgetRetainsMatchedItemTooltipAndIgnoresLoneTooltip() {
        var orphan = new net.enthusia.staff.api.chat.RichChatArtifact(
                net.enthusia.staff.api.chat.RichChatArtifact.Kind.TOOLTIP,
                1, "orphan.png", "image/png", "Details", new byte[] {1});
        var icon = new net.enthusia.staff.api.chat.RichChatArtifact(
                net.enthusia.staff.api.chat.RichChatArtifact.Kind.ITEM,
                10, "icon.png", "image/png", "Item", new byte[] {2});
        var tooltip = new net.enthusia.staff.api.chat.RichChatArtifact(
                net.enthusia.staff.api.chat.RichChatArtifact.Kind.TOOLTIP,
                10, "tooltip.png", "image/png", "Details", new byte[] {3});
        assertEquals(List.of(icon, tooltip),
                InteractiveChatStagingArtifactProvider.withinBundleBudget(
                        List.of(orphan, icon, tooltip)));
    }

    @Test
    void itemCardTitleUsesCleanItemName() {
        assertEquals("Diamond Spear",
                InteractiveChatStagingArtifactProvider.plainItemTitle("§bDiamond Spear<reset>"));
        assertEquals("Shared item",
                InteractiveChatStagingArtifactProvider.plainItemTitle("§b<reset>"));
    }

    @Test
    void matcherUsesFirstUnescapedPlaceholder() {
        assertEquals(
                7,
                InteractiveChatStagingArtifactProvider.firstUnescapedMatch(
                        ITEM,
                        "prefix [item] suffix"
                )
        );
    }

    @Test
    void matcherTreatsSingleBackslashAsEscapeButDoubleBackslashAsLiteralSlash() {
        assertEquals(
                -1,
                InteractiveChatStagingArtifactProvider.firstUnescapedMatch(
                        ITEM,
                        "prefix \\[item] suffix"
                )
        );
        assertEquals(
                9,
                InteractiveChatStagingArtifactProvider.firstUnescapedMatch(
                        ITEM,
                        "prefix \\\\[item] suffix"
                )
        );
    }

    @Test
    void matcherSkipsEscapedOccurrenceAndFindsLaterRealPlaceholder() {
        assertEquals(
                19,
                InteractiveChatStagingArtifactProvider.firstUnescapedMatch(
                        ITEM,
                        "first \\[item] then [item]"
                )
        );
    }

    @Test
    void multipleItemMarkersPreservePositionWithBoundedRendering() {
        assertEquals(List.of(2, 14),
                InteractiveChatStagingArtifactProvider.unescapedMatchPositions(
                        ITEM, "a [item] then [item] and [item]", 2));
        assertEquals(List.of(2, 14, 25),
                InteractiveChatStagingArtifactProvider.unescapedMatchPositions(
                        ITEM, "a [item] then [item] and [item]", 3));
    }

    @Test
    void escapeParityHandlesThreeAndFourConsecutiveBackslashes() {
        String odd = "x " + "\\".repeat(3) + "[item]";
        String even = "x " + "\\".repeat(4) + "[item]";
        assertEquals(-1, InteractiveChatStagingArtifactProvider.firstUnescapedMatch(ITEM, odd));
        assertEquals(6, InteractiveChatStagingArtifactProvider.firstUnescapedMatch(ITEM, even));
    }

    @Test
    void tooltipOwnerMustResolveToTheExactSenderNotAnyOnlinePlayer() {
        UUID sender = UUID.fromString("12345678-1234-5678-9234-567812345678");
        UUID unrelated = UUID.fromString("11111111-1234-5678-9234-567812345678");
        org.bukkit.entity.Player ownPlayer = fakePlayer(sender);
        org.bukkit.entity.Player anotherPlayer = fakePlayer(unrelated);
        assertTrue(InteractiveChatStagingArtifactProvider.isMatchingLocalSender(
                sender, ownPlayer));
        assertFalse(InteractiveChatStagingArtifactProvider.isMatchingLocalSender(
                sender, anotherPlayer));
        assertFalse(InteractiveChatStagingArtifactProvider.isMatchingLocalSender(
                sender, null));
        assertFalse(InteractiveChatStagingArtifactProvider.isMatchingLocalSender(
                sender, "not a Bukkit player"));
        assertFalse(InteractiveChatStagingArtifactProvider.isMatchingLocalSender(
                null, ownPlayer));
    }

    private static org.bukkit.entity.Player fakePlayer(UUID id) {
        return (org.bukkit.entity.Player) java.lang.reflect.Proxy.newProxyInstance(
                org.bukkit.entity.Player.class.getClassLoader(),
                new Class<?>[] {org.bukkit.entity.Player.class},
                (proxy, method, arguments) -> {
                    if (method.getName().equals("getUniqueId")) {
                        return id;
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
    }

    @Test
    void detachedRendererUuidIsStableAndCannotResolveAsTheLivePlayerIdentity() {
        UUID playerId = UUID.fromString("12345678-1234-5678-9234-567812345678");

        UUID first = InteractiveChatStagingArtifactProvider.detachedSnapshotUuid(playerId);
        UUID second = InteractiveChatStagingArtifactProvider.detachedSnapshotUuid(playerId);

        assertEquals(first, second);
        assertNotEquals(playerId, first);
    }

    @Test
    void earlierInventoryPlaceholderGetsPriorityOverLaterItemShares() {
        var lateItemA = new InteractiveChatStagingArtifactProvider.RenderJob(
                InteractiveChatStagingArtifactProvider.Kind.ITEM, 20, null, null);
        var lateItemB = new InteractiveChatStagingArtifactProvider.RenderJob(
                InteractiveChatStagingArtifactProvider.Kind.ITEM, 35, null, null);
        var firstInventory = new InteractiveChatStagingArtifactProvider.RenderJob(
                InteractiveChatStagingArtifactProvider.Kind.INVENTORY, 3, null, null);
        var snapshot = new InteractiveChatStagingArtifactProvider.Snapshot(
                new Object(), List.of(lateItemA, lateItemB, firstInventory));

        assertEquals(List.of(firstInventory, lateItemA, lateItemB), snapshot.jobs());
        assertEquals(List.of(3, 20, 35), snapshot.jobs().stream()
                .map(InteractiveChatStagingArtifactProvider.RenderJob::position).toList());
    }

    @Test
    void tiedPlaceholdersHaveStableKindOrderAndSnapshotsAreImmutable() {
        var item = new InteractiveChatStagingArtifactProvider.RenderJob(
                InteractiveChatStagingArtifactProvider.Kind.ITEM, 5, null, null);
        var inventory = new InteractiveChatStagingArtifactProvider.RenderJob(
                InteractiveChatStagingArtifactProvider.Kind.INVENTORY, 5, null, null);
        var ender = new InteractiveChatStagingArtifactProvider.RenderJob(
                InteractiveChatStagingArtifactProvider.Kind.ENDER_CHEST, 5, null, null);
        var source = new java.util.ArrayList<>(List.of(ender, inventory, item));
        var snapshot = new InteractiveChatStagingArtifactProvider.Snapshot(new Object(), source);
        source.clear();

        assertEquals(List.of(item, inventory, ender), snapshot.jobs());
        assertThrows(UnsupportedOperationException.class, () -> snapshot.jobs().clear());
    }

    @Test
    void customTooltipKeyIsCapturedAndCarriedToAsyncRenderJob() throws Exception {
        var factory = FakeNmsAddon.class.getMethod("getInstance");
        var lookup = FakeNmsWrapper.class.getMethod(
                "getCustomTooltipResourceLocation", org.bukkit.inventory.ItemStack.class);

        Object captured = InteractiveChatStagingArtifactProvider.captureTooltipStyle(
                factory, lookup, null);
        assertSame(FakeNmsWrapper.CUSTOM_KEY, captured);
        var job = new InteractiveChatStagingArtifactProvider.RenderJob(
                InteractiveChatStagingArtifactProvider.Kind.ITEM,
                12, null, null, "Custom sword", List.of("tooltip"), captured);
        assertSame(captured, job.tooltipStyleKey());
    }

    @Test
    void detachedImageRendererReceivesCapturedCustomKey() throws Exception {
        Object custom = FakeNmsWrapper.CUSTOM_KEY;
        var renderMethod = FakeTooltipRenderer.class.getMethod(
                "getToolTipImage", List.class, Object.class);
        var components = List.of("custom lore");

        var image = InteractiveChatStagingArtifactProvider.renderDetachedTooltip(
                renderMethod, components, custom);
        assertEquals(16, image.getWidth());
        assertSame(custom, FakeTooltipRenderer.lastStyle);
        assertSame(components, FakeTooltipRenderer.lastComponents);

        InteractiveChatStagingArtifactProvider.renderDetachedTooltip(
                renderMethod, List.of("default lore"), null);
        assertNull(FakeTooltipRenderer.lastStyle);
    }

    public static final class FakeTooltipRenderer {
        static Object lastStyle;
        static Object lastComponents;

        public static java.awt.image.BufferedImage getToolTipImage(
                List<?> components, Object styleKey) {
            lastComponents = components;
            lastStyle = styleKey;
            return new java.awt.image.BufferedImage(16, 8,
                    java.awt.image.BufferedImage.TYPE_INT_ARGB);
        }
    }

    @Test
    void missingOrFailingTooltipStyleApiKeepsNormalBackground() throws Exception {
        var lookup = FakeNmsWrapper.class.getMethod(
                "getCustomTooltipResourceLocation", org.bukkit.inventory.ItemStack.class);
        var unavailable = BrokenNmsAddon.class.getMethod("getInstance");

        assertNull(InteractiveChatStagingArtifactProvider.captureTooltipStyle(
                unavailable, lookup, null));
        assertNull(InteractiveChatStagingArtifactProvider.captureTooltipStyle(
                FakeNmsAddon.class.getMethod("getInstance"),
                FakeNmsWrapper.class.getMethod("missingStyle",
                        org.bukkit.inventory.ItemStack.class), null));
        assertNull(new InteractiveChatStagingArtifactProvider.RenderJob(
                InteractiveChatStagingArtifactProvider.Kind.ITEM,
                0, null, null).tooltipStyleKey());
    }

    public static final class FakeNmsAddon {
        public static FakeNmsWrapper getInstance() {
            return new FakeNmsWrapper();
        }
    }

    public static final class BrokenNmsAddon {
        public static FakeNmsWrapper getInstance() {
            throw new IllegalStateException("addon version not supported");
        }
    }

    public static final class FakeNmsWrapper {
        static final Object CUSTOM_KEY = new Object();

        public Object getCustomTooltipResourceLocation(org.bukkit.inventory.ItemStack item) {
            return CUSTOM_KEY;
        }

        public Object missingStyle(org.bukkit.inventory.ItemStack item) {
            throw new IllegalStateException("missing tooltip style");
        }
    }

    @Test
    void placeholderPermissionsMatchInteractiveChatContracts() {
        assertEquals(
                "interactivechat.module.item",
                InteractiveChatStagingArtifactProvider.Kind.ITEM.permission()
        );
        assertEquals(
                "interactivechat.module.inventory",
                InteractiveChatStagingArtifactProvider.Kind.INVENTORY.permission()
        );
        assertEquals(
                "interactivechat.module.enderchest",
                InteractiveChatStagingArtifactProvider.Kind.ENDER_CHEST.permission()
        );
    }

    @Test
    void renderJobRejectsNegativePositionsAndSnapshotCopiesJobs() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new InteractiveChatStagingArtifactProvider.RenderJob(
                        InteractiveChatStagingArtifactProvider.Kind.ITEM,
                        -1,
                        null,
                        null
                )
        );

        var job = new InteractiveChatStagingArtifactProvider.RenderJob(
                InteractiveChatStagingArtifactProvider.Kind.ITEM,
                3,
                null,
                null
        );
        var source = new java.util.ArrayList<>(List.of(job));
        var snapshot = new InteractiveChatStagingArtifactProvider.Snapshot(
                new Object(),
                source
        );
        source.clear();
        assertEquals(1, snapshot.jobs().size());
    }
}
