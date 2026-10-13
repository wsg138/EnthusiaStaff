package net.enthusia.staff.paper.punishment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Guards noninteractive decoration and the stable clickable slot contract. */
class PunishmentGuiVisualContractTest {
    @Test
    void sixMainCategoriesAreCenteredAndDoNotOverlapControls() {
        int[] expected = {20, 22, 24, 29, 31, 33, 40};
        Set<Integer> controlSlots = Set.of(45, 46, 47, 48, 49, 50, 51, 53, 13);
        Set<Integer> seen = new HashSet<>();
        for (int i = 0; i < expected.length; i++) {
            int slot = PunishmentGuiRenderer.categorySlot(i, expected.length);
            assertEquals(expected[i], slot);
            assertTrue(seen.add(slot));
            assertFalse(controlSlots.contains(slot));
        }
        assertEquals(50, PunishmentGuiRenderer.SEARCH_SLOT);
        assertEquals(49, PunishmentGuiRenderer.CONFIRM_SLOT);
        assertEquals(51, PunishmentGuiRenderer.CLOSE_SLOT);
    }

    @Test
    void reviewActionsRemainDistinctFromLadderAndHistory() {
        Set<Integer> fixed = Set.of(PunishmentGuiRenderer.VISIBILITY_SLOT,
                PunishmentGuiRenderer.NOTE_SLOT, PunishmentGuiRenderer.CONFIRM_SLOT,
                PunishmentGuiRenderer.CLOSE_SLOT, PunishmentGuiRenderer.SEARCH_SLOT);
        for (int slot = 18; slot < 36; slot++) {
            assertFalse(fixed.contains(slot), "Ladder overlaps a clickable action");
        }
        assertEquals(37, PunishmentGuiRenderer.VISIBILITY_SLOT);
        assertEquals(39, PunishmentGuiRenderer.NOTE_SLOT);
    }

    @Test
    void allPunishmentGuiFamiliesDisableDefaultItalicStyling() throws IOException {
        for (String name : new String[]{
                "PunishmentGuiRenderer.java", "PunishmentRequestGuiRenderer.java",
                "policyv2/PolicyV2GuiRenderer.java"
        }) {
            Path path = Path.of("src/main/java/net/enthusia/staff/paper/punishment", name);
            String source = Files.readString(path);
            assertTrue(source.contains("TextDecoration.ITALIC, false"), name);
            assertTrue(source.contains("TextDecoration.BOLD, true"), name);
            assertTrue(source.contains("BLACK_STAINED_GLASS_PANE"), name);
            assertTrue(source.contains("GRAY_STAINED_GLASS_PANE"), name);
        }
    }

    @Test
    void searchResultsPreserveTheSameAuthorizedReasonSelection() throws IOException {
        Path path = Path.of("src/main/java/net/enthusia/staff/paper/punishment/PunishmentGuiController.java");
        String source = Files.readString(path);
        assertTrue(source.contains("prepare(viewer, actor, state, results.get(index))"));
        assertTrue(source.contains("catalog.search(actor, state.commandName(), state.query())"));
    }
}
