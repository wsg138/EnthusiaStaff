package net.enthusia.staff.paper.sanction;

import static org.junit.jupiter.api.Assertions.*;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.bukkit.inventory.Inventory;
import org.junit.jupiter.api.Test;

class SanctionChangeLookupNavigationTest {
    private static final UUID VIEWER = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final UUID OTHER = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");

    @Test void delayedLookupMayOnlyPresentOnItsOriginalInventory() {
        var fence = new SanctionChangeGuiLookupFence();
        Inventory source = inventory();
        var old = fence.begin(VIEWER, source);
        assertFalse(fence.consume(VIEWER, old, inventory()));
        assertTrue(fence.consume(VIEWER, old, source));
        assertFalse(fence.consume(VIEWER, old, source), "One result consumes the ticket only once");
    }

    @Test void newerLookupAndExternalNavigationSupersedeEarlierResults() {
        var fence = new SanctionChangeGuiLookupFence();
        Inventory source = inventory();
        var old = fence.begin(VIEWER, source);
        var next = fence.begin(VIEWER, source);
        assertFalse(fence.consume(VIEWER, old, source));
        assertTrue(fence.consume(VIEWER, next, source));
        var canceled = fence.begin(VIEWER, source);
        fence.cancel(VIEWER);
        assertFalse(fence.consume(VIEWER, canceled, source));
    }

    @Test void anotherViewerCannotConsumePendingLookup() {
        var fence = new SanctionChangeGuiLookupFence();
        Inventory source = inventory();
        var ticket = fence.begin(VIEWER, source);
        assertFalse(fence.consume(OTHER, ticket, source));
        assertTrue(fence.consume(VIEWER, ticket, source));
    }

    @Test void backgroundLookupPathsMustUseOwnerThreadTicketGatedPresentation() throws Exception {
        String gui = Files.readString(Path.of(
            "src/main/java/net/enthusia/staff/paper/sanction/SanctionChangeGuiController.java"));
        String start = gui.substring(gui.indexOf("public void open(Player viewer"),
            gui.indexOf("@EventHandler(priority = EventPriority.HIGHEST)"));
        assertTrue(start.contains("lookupFence.begin(")
                && start.contains("viewer.getUniqueId(), viewer.getOpenInventory().getTopInventory()"));
        assertTrue(start.contains("resolveAndOpen(viewer, targetQuery, normalizedCommand, ticket)"));
        assertTrue(gui.contains("lookupFence.consume(viewer.getUniqueId(), ticket,"));
        assertTrue(gui.contains("private void openResolvedState("));
        assertTrue(gui.contains("onInventoryClose(InventoryCloseEvent event)"));
        assertTrue(gui.contains("lookupFence.cancel(viewer.getUniqueId())"));
        String worker = gui.substring(gui.indexOf("private void resolveAndOpen("),
            gui.indexOf("private void caseClick("));
        assertFalse(worker.contains("openState(viewer, new SanctionChangeGuiState."),
            "Direct case, player and recent-case lookup must never reopen without original ticket");
        assertTrue(worker.contains("openResolvedState(viewer, new SanctionChangeGuiState.Actions("));
        assertTrue(worker.contains("openResolvedState(viewer, new SanctionChangeGuiState.Cases("));
    }

    private static Inventory inventory() {
        return (Inventory) Proxy.newProxyInstance(Inventory.class.getClassLoader(),
            new Class<?>[]{Inventory.class},
            (self, method, args) -> {
                if ("toString".equals(method.getName())) return "inventory-proxy";
                throw new AssertionError(method.getName());
            });
    }
}
