package net.enthusia.staff.paper.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class InteractiveChatStagingArtifactProviderTest {

    private static final Pattern ITEM = Pattern.compile("\\[item\\]");

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
    void detachedRendererUuidIsStableAndCannotResolveAsTheLivePlayerIdentity() {
        UUID playerId = UUID.fromString("12345678-1234-5678-9234-567812345678");

        UUID first = InteractiveChatStagingArtifactProvider.detachedSnapshotUuid(playerId);
        UUID second = InteractiveChatStagingArtifactProvider.detachedSnapshotUuid(playerId);

        assertEquals(first, second);
        assertNotEquals(playerId, first);
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
