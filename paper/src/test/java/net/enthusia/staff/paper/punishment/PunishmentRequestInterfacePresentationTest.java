package net.enthusia.staff.paper.punishment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;
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
    private static final int PAGE_SIZE = 45;
    private static final int TOTAL_REQUESTS = 47;
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
        List<PunishmentRequestGuiState.RequestView> views = requestViews();

        PunishmentRequestGuiState.Queue first = PunishmentRequestGuiState.Queue.page(views, 0, PAGE_SIZE);
        PunishmentRequestGuiState.Queue second = PunishmentRequestGuiState.Queue.page(views, 1, PAGE_SIZE);

        assertEquals(PAGE_SIZE, first.requests().size());
        assertEquals(TOTAL_REQUESTS - PAGE_SIZE, second.requests().size());
        assertEquals(views.get(0).request().requestId(), first.requests().get(0).request().requestId());
        assertEquals(views.get(PAGE_SIZE).request().requestId(), second.requests().get(0).request().requestId());
        assertTrue(first.hasNext());
        assertFalse(first.hasPrevious());
        assertTrue(second.hasPrevious());
        assertFalse(second.hasNext());
        assertEquals(TOTAL_REQUESTS, second.totalEntries());
    }

    @Test
    void emptyQueueStillProducesOneStablePage() {
        PunishmentRequestGuiState.Queue queue = PunishmentRequestGuiState.Queue.page(List.of(), 0, PAGE_SIZE);

        assertTrue(queue.requests().isEmpty());
        assertEquals(0, queue.page());
        assertEquals(1, queue.totalPages());
        assertEquals(0, queue.totalEntries());
        assertFalse(queue.hasPrevious());
        assertFalse(queue.hasNext());
    }

    private static List<PunishmentRequestGuiState.RequestView> requestViews() {
        return IntStream.range(0, TOTAL_REQUESTS)
                .mapToObj(index -> new PunishmentRequestGuiState.RequestView(
                        request(index + 10, PunishmentRequestStatus.PENDING),
                        "Target" + index
                ))
                .toList();
    }

    private static PunishmentApprovalRequest request(int sequence, PunishmentRequestStatus status) {
        if (status == PunishmentRequestStatus.PENDING) {
            return pendingRequest(sequence);
        }
        return resolvedRequest(sequence, status);
    }

    private static PunishmentApprovalRequest pendingRequest(int sequence) {
        Instant createdAt = NOW.plusSeconds(sequence);
        return PunishmentApprovalRequest.pending(
                requestId(sequence),
                submissionKey(sequence),
                proposal(),
                createdAt,
                createdAt.plusSeconds(86_400)
        );
    }

    private static PunishmentApprovalRequest resolvedRequest(int sequence, PunishmentRequestStatus status) {
        return new PunishmentApprovalRequest(
                requestId(sequence),
                submissionKey(sequence),
                proposal(),
                NOW,
                NOW.plusSeconds(86_400),
                status,
                1,
                resolvedBy(status),
                resolutionNote(status),
                resultingCaseId(status),
                NOW.plusSeconds(60)
        );
    }

    private static PunishmentProposal proposal() {
        SanctionSpec sanction = new SanctionSpec(SanctionType.NETWORK_BAN, SanctionLength.permanent());
        PunishmentStep step = new PunishmentStep(0, "Permanent ban", List.of(sanction));
        return new PunishmentProposal(
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
    }

    private static UUID requestId(int sequence) {
        return new UUID(0x5400000000000000L, sequence);
    }

    private static IdempotencyKey submissionKey(int sequence) {
        return new IdempotencyKey("request-interface:" + sequence);
    }

    private static UUID resolvedBy(PunishmentRequestStatus status) {
        return status == PunishmentRequestStatus.EXPIRED ? null : REVIEWER_ID;
    }

    private static CaseId resultingCaseId(PunishmentRequestStatus status) {
        return switch (status) {
            case APPROVED, FULFILLED_EXTERNALLY -> new CaseId("TESTCASE00000001");
            case DENIED, EXPIRED -> null;
            case PENDING -> throw new IllegalArgumentException("pending requests use the pending factory");
        };
    }

    private static String resolutionNote(PunishmentRequestStatus status) {
        return switch (status) {
            case APPROVED -> "Approved by test reviewer";
            case DENIED -> "Denied by test reviewer";
            case EXPIRED -> "Punishment request expired without a decision";
            case FULFILLED_EXTERNALLY -> "Fulfilled by another authoritative punishment";
            case PENDING -> throw new IllegalArgumentException("pending requests use the pending factory");
        };
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
