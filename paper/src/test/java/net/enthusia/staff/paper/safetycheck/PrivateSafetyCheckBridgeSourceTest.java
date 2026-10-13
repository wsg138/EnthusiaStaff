package net.enthusia.staff.paper.safetycheck;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class PrivateSafetyCheckBridgeSourceTest {
    private static String source() throws Exception {
        return Files.readString(Path.of(
                "src/main/java/net/enthusia/staff/paper/safetycheck/PrivateSafetyCheckBridge.java"));
    }

    @Test
    void noPunishmentOrPublicChatAuthority() throws Exception {
        String code = source();
        for (String forbidden : new String[]{
                "PunishmentService", "dispatchCommand(", "performCommand(",
                "broadcastMessage(", "strikeRecommendation", "setBanned(",
                "getServer().broadcast(", "/v1/moderate"
        }) {
            assertFalse(code.contains(forbidden), forbidden);
        }
        assertTrue(code.contains("Private safety"));
        assertTrue(code.contains("enabled"));
    }

    @Test
    void onlyScopedAuthenticatedPrivateRoutesUsed() throws Exception {
        String code = source();
        assertTrue(code.contains("/v1/safety-checks/pending?subject_id="));
        assertTrue(code.contains("scope_id="));
        assertTrue(code.contains("/v1/safety-checks/"));
        assertTrue(code.contains("/v1/safety-alerts"));
        assertTrue(code.contains("http.sendAsync("));
        assertTrue(code.contains("X-Client-Id"));
        assertTrue(code.contains("Authorization"));
        assertTrue(code.contains("HttpClient.Redirect.NEVER"));
        assertTrue(code.contains("Private safety roles must be separate"));
    }

    @Test
    void staffAlertNeverAcknowledgedWithoutPrivateStaffSend() throws Exception {
        String code = source();
        int method = code.indexOf("private void deliver(");
        int checkPermission = code.indexOf("staff.hasPermission(PERMISSION)", method);
        int send = code.indexOf("staff.sendMessage(", checkPermission);
        int outcome = code.indexOf("outcome.complete(true)", send);
        int record = code.indexOf("deliveredPendingAck.add(id)", outcome);
        int ack = code.indexOf("ack(id)", record);
        assertTrue(method >= 0 && checkPermission > method);
        assertTrue(send > checkPermission);
        assertTrue(outcome > send);
        assertTrue(record > outcome && ack > record);
        assertTrue(code.contains("deliveries.isEmpty()"));
        assertTrue(code.contains("delivery_receipt_id"));
        assertTrue(code.contains("activeDuty.test(staff.getUniqueId())"));
    }

    @Test
    void delayedHttpCallbacksCannotPermanentlyConsumeUndisplayedPrompts() throws Exception {
        String code = source();
        assertTrue(code.contains("boolean scheduled = player.getScheduler().execute("));
        assertTrue(code.contains("presented.remove(marker)"));
        assertTrue(code.contains("if (!scheduled)"));
        assertTrue(code.contains("if (!active.get() || !player.isOnline())"));
    }

    @Test
    void liveRecipientsAreSelectedOnPaperGlobalScheduler() throws Exception {
        String code = source();
        assertTrue(code.contains("getGlobalRegionScheduler().execute("));
        assertTrue(code.contains("activeDuty.test(staff.getUniqueId())"));
        assertTrue(code.contains("scope.equals(targetScope)"));
    }

    @Test
    void safetyConfigMustMatchActualServerIdentity() throws Exception {
        String code = source();
        assertTrue(code.contains("verifiedScope("));
        assertTrue(code.contains("Safety scope differs from server identity"));
    }

    @Test
    void helpChoiceGivesOptionalNonBlockingResourceScreen() throws Exception {
        String code = source();
        assertTrue(code.contains("private enum Screen { QUESTION, SUPPORT }"));
        assertTrue(code.contains("showSupport(player)"));
        assertTrue(code.contains("holder.screen == Screen.SUPPORT"));
        assertTrue(code.contains("sendSupportDetail(player, event.getSlot())"));
        assertTrue(code.contains("getScheduler().execute(plugin"));
        assertTrue(code.contains("local emergency services"));
        assertTrue(code.contains("call or text 988"));
        assertTrue(code.contains("Reach out to someone you trust"));
    }

    @Test
    void guiIsPrivateNonBlockingAndPreservesPlayerIdentity() throws Exception {
        String code = source();
        assertTrue(code.contains("getUniqueId()"));
        assertTrue(code.contains("holder.player.equals(player.getUniqueId())"));
        assertTrue(code.contains("InventoryClickEvent"));
        assertTrue(code.contains("event.setCancelled(true)"));
        assertTrue(code.contains("case 10 -> \"SAFE\""));
        assertTrue(code.contains("case 12 -> \"NEEDS_HELP\""));
        assertTrue(code.contains("case 14 -> \"UNSURE\""));
        assertTrue(code.contains("case 16 -> \"DECLINED\""));
        assertTrue(code.contains("player.closeInventory()"));
        assertTrue(code.contains("player.getScheduler().execute("));
        assertTrue(code.contains("offerLocalFallback(holder.player)"));
    }
}
