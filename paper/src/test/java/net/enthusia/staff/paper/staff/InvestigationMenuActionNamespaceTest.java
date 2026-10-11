package net.enthusia.staff.paper.staff;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

class InvestigationMenuActionNamespaceTest {
    @Test
    void inspectorAndInventoryActionsUseOwnedCommandNamespace() {
        assertEquals("enthusiastaff:inspect TestPlayer",
                InvestigationMenuAction.OVERVIEW.command("TestPlayer"));
        assertEquals("enthusiastaff:inspect inventory TestPlayer",
                InvestigationMenuAction.INVENTORY.command("TestPlayer"));
        assertEquals("enthusiastaff:inspect ender TestPlayer",
                InvestigationMenuAction.ENDER_CHEST.command("TestPlayer"));
    }
}
