package net.enthusia.staff.paper.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import net.enthusia.staff.api.chat.RichChatArtifactRequest;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import java.util.List;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;
import net.enthusia.staff.api.chat.RichChatArtifact;
import org.junit.jupiter.api.Test;

class IndependentRichChatArtifactProviderTest {
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
                () -> new IndependentRichChatArtifactProvider.Slot("STONE", 200));
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
        RichChatArtifactRequest request = request("[item]");
        assertFalse(IndependentRichChatArtifactProvider.snapshotWindowExpired(request, 11_999L));
        assertTrue(IndependentRichChatArtifactProvider.snapshotWindowExpired(request, 12_000L));
        assertTrue(IndependentRichChatArtifactProvider.snapshotWindowExpired(request, 50_000L));
    }

    @Test
    void neverReadsInventoryForDeniedPermissionsOrQuitPlayer() {
        AtomicInteger reads = new AtomicInteger();
        AtomicReference<ItemStack> hand = new AtomicReference<>(new ItemStack(Material.STONE, 1));
        var rule = itemRule();

        assertTrue(IndependentRichChatArtifactProvider.captureJob(
                rule, fakePlayer(false, true, hand, reads), request("[item]")).isEmpty());
        assertTrue(IndependentRichChatArtifactProvider.captureJob(
                rule, fakePlayer(true, false, hand, reads), request("[item]")).isEmpty());
        assertTrue(IndependentRichChatArtifactProvider.captureJob(
                rule, fakePlayer(true, true, hand, reads), request("ordinary chat")).isEmpty());
        assertEquals(0, reads.get());
    }

    @Test
    void entityOwnedCaptureFreezesSlotSnapshotBeforeWorkerRendering() {
        AtomicInteger reads = new AtomicInteger();
        AtomicReference<ItemStack> hand = new AtomicReference<>(new ItemStack(Material.STONE, 3));
        var player = fakePlayer(true, true, hand, reads);
        var captured = IndependentRichChatArtifactProvider.captureJob(
                itemRule(), player, request("[item]")).orElseThrow();
        assertEquals(1, reads.get());
        hand.set(new ItemStack(Material.DIAMOND, 5));
        assertEquals(List.of(new IndependentRichChatArtifactProvider.Slot("STONE", 3)),
                captured.slots());
        assertEquals(1, reads.get());
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

    private static Player fakePlayer(
            boolean allowed, boolean online, AtomicReference<ItemStack> hand, AtomicInteger reads
    ) {
        PlayerInventory inventory = (PlayerInventory) Proxy.newProxyInstance(
                PlayerInventory.class.getClassLoader(), new Class<?>[]{PlayerInventory.class},
                (proxy, method, arguments) -> {
                    if (method.getName().equals("getItemInMainHand")) {
                        reads.incrementAndGet();
                        return hand.get();
                    }
                    throw unexpected(method);
                });
        return (Player) Proxy.newProxyInstance(
                Player.class.getClassLoader(), new Class<?>[]{Player.class},
                (proxy, method, arguments) -> switch (method.getName()) {
                    case "isOnline" -> online;
                    case "hasPermission" -> allowed;
                    case "getInventory" -> inventory;
                    default -> throw unexpected(method);
                });
    }

    private static AssertionError unexpected(Method method) {
        return new AssertionError("Unexpected method: " + method.getName());
    }
}
