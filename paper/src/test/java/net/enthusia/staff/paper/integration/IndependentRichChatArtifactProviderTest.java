package net.enthusia.staff.paper.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
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
}
