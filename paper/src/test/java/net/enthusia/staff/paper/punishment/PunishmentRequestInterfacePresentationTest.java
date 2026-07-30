package net.enthusia.staff.paper.punishment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.enthusia.staff.common.CaseId;
import net.enthusia.staff.common.IdempotencyKey;
import net.enthusia.staff.domain.application.PunishmentApprovalRequest;
import net.enthusia.staff.domain.application.PunishmentProposal;
import net.enthusia.staff.domain.application.PunishmentRequestStatus;
import net.enthusia.staff.domain.auth.Actor;
import net.enthusia.staff.domain.auth.StaffRank;
import net.enthusia.staff.domain.casefile.CaseVisibility;
import net.enthusia.staff.domain.escalation.EscalationDecision;
import net.enthusia.staff.domain.escalation.PunishmentStep;
import net.enthusia.staff.domain.player.PlayerIdentity;
import net.enthusia.staff.domain.player.PlayerPlatform;
import net.enthusia.staff.domain.player.PlayerPresence;
import net.enthusia.staff.domain.ports.PlayerDirectory;
import net.enthusia.staff.domain.sanction.SanctionLength;
import net.enthusia.staff.domain.sanction.SanctionSpec;
import net.enthusia.staff.domain.sanction.SanctionType;
import org.junit.jupiter.api.Test;

final class PunishmentRequestInterfacePresentationTest {
    private static final Instant NOW = Instant.parse("2026-07-30T20:00:00Z");
    private static final UUID TARGET_ID = UUID.fromString("54000000-0000-0000-0000-000000000001");
    private static final Actor REQUESTER = new Actor(
            UUID.fromString("54000000-0000-0000-0000-000000000002"),
            "HelperOne",
            StaffRank.HELPER
    );
    private static final UUID REVIEWER_ID = UUID.fromString("54000000-0000-0000-0000-000000000003");

    @Test
    void statusPresentationCoversEveryDurableState() {
        assertEquals("pending", PunishmentRequestPresentation.status(PunishmentRequestStatus.PENDING));
        assertEquals("approved", PunishmentRequestPresentation.status(PunishmentRequestStatus.APPROVED));
        assertEquals("denied", PunishmentRequestPresentation.status(PunishmentRequestStatus.DENIED));
        assertEquals("expired", PunishmentRequestPresentation.status(PunishmentRequestStatus.EXPIRED));
        assertEquals(
                "externally fulfilled",
                PunishmentRequestPresentation.status(PunishmentRequestStatus.FULFILLED_EXTERNALLY)
        );
    }

    @Test
    void resolvedPresentationExplainsApprovedDeniedExpiredAndExternalFulfillment() {
        assertEquals(
                "Approved as case TESTCASE00000001",
                PunishmentRequestPresentation.resolution(request(1, PunishmentRequestStatus.APPROVED))
        );
        assertEquals(
                "Denied by test reviewer",
                PunishmentRequestPresentation.resolution(request(2, PunishmentRequestStatus.DENIED))
        );
        assertEquals(
                "Punishment request expired without a decision",
                PunishmentRequestPresentation.resolution(request(3, PunishmentRequestStatus.EXPIRED))
        );
        assertEquals(
                "Fulfilled by case TESTCASE00000001",
                PunishmentRequestPresentation.resolution(request(4, PunishmentRequestStatus.FULFILLED_EXTERNALLY))
        );
    }

    @Test
    void offlineTargetPresentationNeverLeaksTheTargetUuid() {
        String presented = PunishmentRequestPresentation.targetName(directory(Optional.empty()), TARGET_ID);

        assertEquals("Offline player (name unavailable)", presented);
        assertFalse(presented.contains(TARGET_ID.toString()));
    }

    @Test
    void authoritativeOfflineTargetNameIsDisplayedWhenKnown() {
        PlayerIdentity identity = new PlayerIdentity(
                TARGET_ID,
                Optional.of("KnownOfflineTarget"),
                PlayerPlatform.JAVA,
                NOW.minusSeconds(3_600),
                NOW.minusSeconds(60)
        );

        assertEquals(
                "KnownOfflineTarget",
                PunishmentRequestPresentation.targetName(directory(Optional.of(identity)), TARGET_ID)
        );
    }

    @Test
    void requestQueueKeepsCreationOrderAcrossPages() {
        List<PunishmentRequestGuiState.RequestView> views = new ArrayList<>();
        for (int index = 0; index < 47; index++) {
            PunishmentApprovalRequest request = request(index + 10, PunishmentRequestStatus.PENDING);
            views.add(new PunishmentRequestGuiState.RequestView(request, "Target" + index));
        }

        PunishmentRequestGuiState.Queue first = PunishmentRequestGuiState.Queue.page(views, 0, 45);
        PunishmentRequestGuiState.Queue second = PunishmentRequestGuiState.Queue.page(views, 1, 45);

        assertEquals(45, first.requests().size());
        assertEquals(2, second.requests().size());
        assertEquals(views.get(0).request().requestId(), first.requests().get(0).request().requestId());
        assertEquals(views.get(45).request().requestId(), second.requests().get(0).request().requestId());
        assertTrue(first.hasNext());
        assertFalse(first.hasPrevious());
        assertTrue(second.hasPrevious());
        assertFalse(second.hasNext());
        assertEquals(47, second.totalEntries());
    }

    @Test
    void emptyQueueStillProducesOneStablePage() {
        PunishmentRequestGuiState.Queue queue = PunishmentRequestGuiState.Queue.page(List.of(), 0, 45);

        assertTrue(queue.requests().isEmpty());
        assertEquals(0, queue.page());
        assertEquals(1, queue.totalPages());
        assertEquals(0, queue.totalEntries());
        assertFalse(queue.hasPrevious());
        assertFalse(queue.hasNext());
    }

    private static PunishmentApprovalRequest request(int sequence, PunishmentRequestStatus status) {
        SanctionSpec sanction = new SanctionSpec(SanctionType.NETWORK_BAN, SanctionLength.permanent());
        PunishmentStep step = new PunishmentStep(0, "Permanent ban", List.of(sanction));
        PunishmentProposal proposal = new PunishmentProposal(
                TARGET_ID,
                REQUESTER,
                "chat.request-test",
                "chat",
                "Request test",
                "Evidence-backed request",
                "v1",
                CaseVisibility.PUBLIC,
                StaffRank.MOD,
                new EscalationDecision(0, 0, 0, List.of(), step),
                List.of(sanction)
        );
        UUID requestId = new UUID(0x5400000000000000L, sequence);
        if (status == PunishmentRequestStatus.PENDING) {
            return PunishmentApprovalRequest.pending(
                    requestId,
                    new IdempotencyKey("request-interface:" + sequence),
                    proposal,
                    NOW.plusSeconds(sequence),
                    NOW.plusSeconds(sequence).plusSeconds(86_400)
            );
        }
        UUID resolvedBy = status == PunishmentRequestStatus.EXPIRED ? null : REVIEWER_ID;
        CaseId caseId = status == PunishmentRequestStatus.APPROVED
                || status == PunishmentRequestStatus.FULFILLED_EXTERNALLY
                ? new CaseId("TESTCASE00000001")
                : null;
        String note = switch (status) {
            case APPROVED -> "Approved by test reviewer";
            case DENIED -> "Denied by test reviewer";
            case EXPIRED -> "Punishment request expired without a decision";
            case FULFILLED_EXTERNALLY -> "Fulfilled by another authoritative punishment";
            case PENDING -> throw new IllegalStateException("pending handled above");
        };
        return new PunishmentApprovalRequest(
                requestId,
                new IdempotencyKey("request-interface:" + sequence),
                proposal,
                NOW,
                NOW.plusSeconds(86_400),
                status,
                1,
                resolvedBy,
                note,
                caseId,
                NOW.plusSeconds(60)
        );
    }

    private static PlayerDirectory directory(Optional<PlayerIdentity> identity) {
        return new PlayerDirectory() {
            @Override
            public Optional<PlayerIdentity> find(String uuidOrUsername) {
                return identity;
            }

            @Override
            public List<PlayerIdentity> search(String prefix, int limit) {
                return identity.stream().toList();
            }

            @Override
            public Optional<PlayerPresence> presence(UUID playerId) {
                return Optional.empty();
            }

            @Override
            public void recordSeen(
                    UUID playerId,
                    String username,
                    PlayerPlatform platform,
                    String serverId,
                    Instant seenAt
            ) {
                throw new UnsupportedOperationException("read-only test directory");
            }

            @Override
            public void recordDisconnected(UUID playerId, String serverId, Instant disconnectedAt) {
                throw new UnsupportedOperationException("read-only test directory");
            }
        };
    }
}
