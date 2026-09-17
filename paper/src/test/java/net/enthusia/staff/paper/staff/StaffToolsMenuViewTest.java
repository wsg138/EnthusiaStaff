package net.enthusia.staff.paper.staff;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

final class StaffToolsMenuViewTest {
    private static final UUID VIEWER_ID = UUID.fromString("66000000-0000-0000-0000-000000000001");

    @Test
    void rootViewPreservesOnlyDistinctActionTools() {
        StaffToolsMenuView.Root root = StaffToolsMenuView.root(
                VIEWER_ID,
                List.of(StaffToolDefinition.RANDOM_TELEPORT, StaffToolDefinition.REPORTS)
        );

        assertEquals(StaffToolDefinition.RANDOM_TELEPORT, root.toolAt(0));
        assertEquals(StaffToolDefinition.REPORTS, root.toolAt(1));
        assertNull(root.toolAt(2));
        assertThrows(IllegalArgumentException.class, () -> StaffToolsMenuView.root(
                VIEWER_ID,
                List.of(StaffToolDefinition.STAFF_TOOLS)
        ));
        assertThrows(IllegalArgumentException.class, () -> StaffToolsMenuView.root(
                VIEWER_ID,
                List.of(StaffToolDefinition.REPORTS, StaffToolDefinition.REPORTS)
        ));
    }

    @Test
    void targetPickerExcludesTheViewerAndSortsImmutableTargetSnapshots() {
        UUID alphaId = UUID.fromString("66000000-0000-0000-0000-000000000010");
        UUID betaId = UUID.fromString("66000000-0000-0000-0000-000000000011");
        StaffToolsMenuView.TargetPicker picker = StaffToolsMenuView.TargetPicker.fromCandidates(
                VIEWER_ID,
                StaffToolDefinition.PLAYER_INSPECTOR,
                List.of(
                        new StaffToolsMenuView.TargetEntry(betaId, "Beta"),
                        new StaffToolsMenuView.TargetEntry(VIEWER_ID, "Viewer"),
                        new StaffToolsMenuView.TargetEntry(alphaId, "alpha")
                ),
                0
        );

        assertEquals(List.of("alpha", "Beta"), picker.targets().stream()
                .map(StaffToolsMenuView.TargetEntry::playerName)
                .toList());
        assertEquals(alphaId, picker.targetAtPageIndex(0).playerId());
        assertEquals(betaId, picker.targetAtPageIndex(1).playerId());
        assertNull(picker.targetAtPageIndex(2));
        assertFalse(picker.truncated());
        assertEquals(1, picker.totalPages());
    }

    @Test
    void targetPickerBoundsLargeListsAndClampsToTheLastPage() {
        List<StaffToolsMenuView.TargetEntry> candidates = IntStream.range(0, StaffToolsMenuView.MAX_TARGETS + 10)
                .mapToObj(index -> new StaffToolsMenuView.TargetEntry(
                        new UUID(0L, index + 1L),
                        "Player%03d".formatted(index)
                ))
                .toList();

        StaffToolsMenuView.TargetPicker picker = StaffToolsMenuView.TargetPicker.fromCandidates(
                VIEWER_ID,
                StaffToolDefinition.FREEZE,
                candidates,
                99
        );

        assertEquals(StaffToolsMenuView.MAX_TARGETS, picker.targets().size());
        assertTrue(picker.truncated());
        assertEquals(4, picker.totalPages());
        assertEquals(3, picker.page());
        assertTrue(picker.hasPreviousPage());
        assertFalse(picker.hasNextPage());
        assertEquals("Player135", picker.targetAtPageIndex(0).playerName());
    }

    @Test
    void targetPickerRejectsANonTargetToolAndDuplicateStoredTargets() {
        StaffToolsMenuView.TargetEntry target = new StaffToolsMenuView.TargetEntry(
                UUID.fromString("66000000-0000-0000-0000-000000000020"),
                "Target"
        );

        assertThrows(IllegalArgumentException.class, () -> new StaffToolsMenuView.TargetPicker(
                VIEWER_ID,
                StaffToolDefinition.REPORTS,
                List.of(target),
                0,
                false
        ));
        assertThrows(IllegalArgumentException.class, () -> new StaffToolsMenuView.TargetPicker(
                VIEWER_ID,
                StaffToolDefinition.SPECTATE,
                List.of(target, target),
                0,
                false
        ));
    }

    @Test
    void fixedSlotsNeverRouteLowerInventoryClicksAsStaffMenuActions() {
        assertEquals(0, StaffToolsMenuRenderer.rootToolIndex(10));
        assertEquals(7, StaffToolsMenuRenderer.rootToolIndex(22));
        assertEquals(-1, StaffToolsMenuRenderer.rootToolIndex(36));
        assertEquals(0, StaffToolsMenuRenderer.targetContentIndex(0));
        assertEquals(44, StaffToolsMenuRenderer.targetContentIndex(44));
        assertEquals(-1, StaffToolsMenuRenderer.targetContentIndex(45));
    }
}
