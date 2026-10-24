package net.enthusia.staff.paper.punishment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.papermc.paper.threadedregions.scheduler.GlobalRegionScheduler;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import net.enthusia.staff.common.CaseId;
import net.enthusia.staff.domain.application.PunishmentApprovalRequest;
import net.enthusia.staff.domain.application.PunishmentProposal;
import net.enthusia.staff.domain.application.PunishmentRequestResult;
import net.enthusia.staff.domain.application.PunishmentRequestStatus;
import net.enthusia.staff.domain.auth.StaffRank;
import net.enthusia.staff.domain.casefile.CaseVisibility;
import net.enthusia.staff.domain.ports.PlayerDirectory;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Server;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;

class PunishmentApprovalAnnouncementTest {
    @Test
    void onlyFirstPublicApprovalSchedulesAnAnnouncement() {
        AtomicInteger broadcasts = new AtomicInteger();
        AtomicReference<Component> message = new AtomicReference<>();
        GlobalRegionScheduler scheduler = proxy(GlobalRegionScheduler.class, (method, args) -> {
            if (method.getName().equals("execute")) {
                ((Runnable) args[1]).run();
            }
            return null;
        });
        Server server = proxy(Server.class, (method, args) -> {
            if (method.getName().equals("getGlobalRegionScheduler")) {
                return scheduler;
            }
            if (method.getName().equals("broadcast")) {
                broadcasts.incrementAndGet();
                message.set((Component) args[0]);
                return 0;
            }
            return null;
        });
        Plugin plugin = proxy(Plugin.class, (method, args) ->
                method.getName().equals("getServer") ? server : null);

        PunishmentApprovalRequest publicRequest = approved(CaseVisibility.PUBLIC);
        PunishmentRequestResult.Approved first = new PunishmentRequestResult.Approved(
                publicRequest, publicRequest.resultingCaseId(), false);
        PunishmentApprovalAnnouncement.publish(plugin, null, first);
        assertEquals(1, broadcasts.get());
        String plain = PlainTextComponentSerializer.plainText().serialize(message.get());
        assertTrue(plain.contains("PUBLIC PUNISHMENT"));
        assertTrue(plain.contains("Reason: Test reason"));

        PlayerDirectory forbiddenLookup = proxy(PlayerDirectory.class, (method, args) -> {
            throw new AssertionError("private or replayed approvals must not look up players");
        });
        PunishmentApprovalAnnouncement.publish(plugin, forbiddenLookup,
                new PunishmentRequestResult.Approved(publicRequest, first.caseId(), true));
        assertEquals(1, broadcasts.get(), "replay must never broadcast");

        PunishmentApprovalRequest privateRequest = approved(CaseVisibility.PRIVATE);
        PunishmentApprovalAnnouncement.publish(plugin, forbiddenLookup,
                new PunishmentRequestResult.Approved(
                        privateRequest, privateRequest.resultingCaseId(), false));
        assertEquals(1, broadcasts.get(), "private approvals must never broadcast");
    }

    @Test
    void productionUsesOneObserverInsteadOfGuiSpecificBroadcast() throws Exception {
        String plugin = Files.readString(Path.of(
                "src/main/java/net/enthusia/staff/paper/EnthusiaStaffPaperPlugin.java"));
        String gui = Files.readString(Path.of(
                "src/main/java/net/enthusia/staff/paper/punishment/PunishmentRequestGuiController.java"));
        assertTrue(plugin.contains("bindings.punishmentRequestService().addApprovedObserver("));
        assertTrue(plugin.contains("PunishmentApprovalAnnouncement.publish("));
        assertFalse(gui.contains("PunishmentPublicAnnouncement.publish("),
                "GUI approvals should not duplicate the service-level notification");
        String command = Files.readString(Path.of(
                "src/main/java/net/enthusia/staff/paper/command/PunishmentRequestCommandHandler.java"));
        String web = Files.readString(Path.of(
                "src/main/java/net/enthusia/staff/paper/auth/StaffWebReviewService.java"));
        assertTrue(command.contains("service.approve(leased.lease(), actor)"));
        assertTrue(web.contains("service.approve(acquired.lease(), currentActor)"));
    }

    private static PunishmentApprovalRequest approved(CaseVisibility visibility) {
        PunishmentApprovalRequest pending = PunishmentRequestControllerHarness.pending(StaffRank.MOD);
        PunishmentProposal proposal = pending.proposal();
        PunishmentProposal visible = new PunishmentProposal(
                proposal.targetId(), proposal.requester(), proposal.reasonId(),
                proposal.family(), proposal.publicReason(), proposal.internalExplanation(),
                proposal.configurationVersion(), visibility, proposal.requiredRank(),
                proposal.escalation(), proposal.sanctions());
        CaseId caseId = new CaseId("TESTCASE00000001");
        return new PunishmentApprovalRequest(
                pending.requestId(), pending.submissionKey(), visible,
                pending.createdAt(), pending.expiresAt(), PunishmentRequestStatus.APPROVED,
                1, PunishmentRequestControllerHarness.REVIEWER_ID,
                "Approved", caseId, pending.createdAt().plusSeconds(1));
    }

    private interface Invocation {
        Object call(java.lang.reflect.Method method, Object[] args);
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, Invocation invocation) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},
                (proxy, method, args) -> invocation.call(method, args));
    }
}
