package net.enthusia.staff.paper.sanction;

import static org.junit.jupiter.api.Assertions.*;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import org.bukkit.inventory.Inventory;
import org.junit.jupiter.api.Test;

/** Prompt origin and async commit completion must not displace newer navigation. */
class SanctionChangePromptNavigationTest {
    @Test void promptsExpirePreciselyAtSixtySeconds() {
        Instant initial=Instant.parse("2026-10-25T14:00:00Z");
        assertFalse(SanctionChangeGuiController.captureExpired(initial, initial));
        assertFalse(SanctionChangeGuiController.captureExpired(initial, initial.plusSeconds(59)));
        assertTrue(SanctionChangeGuiController.captureExpired(initial, initial.plusSeconds(60)));
    }
    @Test void completingOlderConfirmationMayOnlyCloseOriginalInventory() {
        Inventory old=inventory();
        Inventory newer=inventory();
        assertTrue(SanctionChangeGuiController.sameConfirmationInventory(old,old));
        assertFalse(SanctionChangeGuiController.sameConfirmationInventory(old,newer));
        assertFalse(SanctionChangeGuiController.sameConfirmationInventory(old,null));
    }
    @Test void privateCaptureAndDelayedChatMustUseOriginTokensAndNavigationInvalidation() throws Exception {
        String s=source();
        assertTrue(s.contains("onInventoryOpen(InventoryOpenEvent event)"));
        assertTrue(s.contains("onCommandPreprocess(PlayerCommandPreprocessEvent event)"));
        assertTrue(s.contains("pendingChatTokens.remove(viewerId, capture.navigationToken())"));
        assertTrue(s.contains("inputCaptures.remove(viewer.getUniqueId(), capture)"));
        assertTrue(s.contains("captureExpired(capture.startedAt(), clock.instant())"));
        assertTrue(s.contains("runDelayed(plugin, task ->"));
        assertTrue(s.contains("pendingChatTokens.remove(viewerId)"));
    }
    @Test void asyncConfirmedSanctionCannotCloseAnotherInventory() throws Exception {
        String s=source();
        String confirm=s.substring(s.indexOf("private void confirm("),s.indexOf("private void openState("));
        assertTrue(confirm.contains("Inventory confirmationOrigin = viewer.getOpenInventory().getTopInventory()"));
        assertTrue(confirm.contains("finish(viewer,") && confirm.contains("confirmationOrigin"));
        String finish=s.substring(s.indexOf("private void finish("),s.indexOf("private boolean submit("));
        assertTrue(finish.contains("sameConfirmationInventory(confirmationOrigin,"));
        assertTrue(finish.contains("viewer.sendMessage("));
    }
    private static String source() throws Exception {
        return Files.readString(Path.of("src/main/java/net/enthusia/staff/paper/sanction/SanctionChangeGuiController.java"));
    }
    private static Inventory inventory() {
        return (Inventory)Proxy.newProxyInstance(Inventory.class.getClassLoader(),new Class<?>[]{Inventory.class},
                (o,m,a)-> { if(m.getName().equals("toString"))return "inventory"; throw new AssertionError(m.getName()); });
    }
}
