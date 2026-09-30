package net.enthusia.staff.paper.presentation;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.junit.jupiter.api.Test;

class StaffMessageBlockStyleTest {
    @Test
    void caseOutputGetsHeaderAndSectionHierarchyWithoutChangingPrivateContent() {
        List<Component> styled = StaffMessageBlockStyle.style(List.of(
                Component.text("Case ES-42 | subject 00000000-0000-0000-0000-000000000042 | Ban | Active"),
                Component.text("Created 2026-09-29 | public reason: Example"),
                Component.text("Internal explanation: private note"),
                Component.text("Sanctions:"),
                Component.text("- 7 | Ban | Active"),
                Component.text("Timeline:")
        ));

        assertEquals(NamedTextColor.AQUA, styled.getFirst().color());
        assertEquals(Component.text("private note", NamedTextColor.GRAY), styled.get(3).children().getFirst());
        assertEquals(NamedTextColor.GOLD, styled.get(4).color());
        assertEquals(NamedTextColor.GOLD, styled.getLast().color());
    }
}
