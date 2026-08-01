package net.enthusia.staff.paper.alert;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import net.enthusia.staff.common.CaseId;
import net.enthusia.staff.common.IdempotencyKey;
import net.enthusia.staff.domain.application.PunishmentApprovalRequest;
import net.enthusia.staff.domain.application.PunishmentProposal;
import net.enthusia.staff.domain.application.PunishmentRequestAlertAudience;
import net.enthusia.staff.domain.application.PunishmentRequestAlertClaim;
import net.enthusia.staff.domain.application.PunishmentRequestAlertDeliveryId;
import net.enthusia.staff.domain.application.PunishmentRequestAlertIntent;
import net.enthusia.staff.domain.application.PunishmentRequestAlertOccurrence;
import net.enthusia.staff.domain.application.PunishmentRequestLifecycleEventType;
import net.enthusia.staff.domain.application.PunishmentRequestStatus;
import net.enthusia.staff.domain.auth.Actor;
import net.enthusia.staff.domain.auth.StaffRank;
import net.enthusia.staff.domain.casefile.CaseVisibility;
import net.enthusia.staff.domain.escalation.EscalationDecision;
import net.enthusia.staff.domain.escalation.PunishmentStep;
import net.enthusia.staff.domain.sanction.SanctionLength;
import net.enthusia.staff.domain.sanction.SanctionSpec;
import net.enthusia.staff.domain.sanction.SanctionType;

final class PunishmentRequestAlertTestFixtures {
    static final Instant NOW = Instant.parse("2026-07-31T20:00:00Z");
    static final UUID REQUEST_ID = UUID.fromString("66000000-0000-0000-0000-000000000001");
    static final UUID ALERT_ID = UUID.fromString("66000000-0000-0000-0000-000000000002");
    static final UUID REQUESTER_ID = UUID.fromString("66000000-0000-0000-0000-000000000003");
    static final UUID TARGET_ID = UUID.fromString("66000000-0000-0000-0000-000000000004");
    static final UUID REVIEWER_ID = UUID.fromString("66000000-0000-0000-0000-000000000005");
    static final UUID ADMIN_ID = UUID.fromString("66000000-0000-0000-0000-000000000006");
    static final CaseId CASE_ID = new CaseId("0123456789ABCDEF");

    private PunishmentRequestAlertTestFixtures() {
    }

    static PunishmentApprovalRequest request(PunishmentRequestLifecycleEventType eventType) {
        PunishmentProposal proposal = proposal(StaffRank.MOD);
        return switch (eventType) {
            case REQUEST_SUBMITTED, REQUEST_CLAIMED -> PunishmentApprovalRequest.pending(
                    REQUEST_ID,
                    new IdempotencyKey("alert-test-request"),
                    proposal,
                    NOW.minusSeconds(30),
                    NOW.plus(Duration.ofHours(1))
            );
            case REQUEST_APPROVED -> resolved(
                    proposal,
                    PunishmentRequestStatus.APPROVED,
                    REVIEWER_ID,
                    "Approved after review",
                    CASE_ID
            );
            case REQUEST_DENIED -> resolved(
                    proposal,
                    PunishmentRequestStatus.DENIED,
                    REVIEWER_ID,
                    "Denied after review",
                    null
            );
            case REQUEST_EXPIRED -> resolved(
                    proposal,
                    PunishmentRequestStatus.EXPIRED,
                    null,
                    "Punishment request expired without a decision",
                    null
            );
            case REQUEST_EXTERNALLY_FULFILLED -> resolved(
                    proposal,
                    PunishmentRequestStatus.FULFILLED_EXTERNALLY,
                    ADMIN_ID,
                    "Equivalent punishment was created elsewhere",
                    CASE_ID
            );
        };
    }

    static PunishmentApprovalRequest pending(StaffRank requiredRank) {
        return PunishmentApprovalRequest.pending(
                REQUEST_ID,
                new IdempotencyKey("alert-test-request"),
                proposal(requiredRank),
                NOW.minusSeconds(30),
                NOW.plus(Duration.ofHours(1))
        );
    }

    static PunishmentRequestAlertClaim claim(
            PunishmentRequestLifecycleEventType eventType,
            PunishmentRequestAlertAudience audience,
            UUID recipientId,
            StaffRank minimumRank
    ) {
        PunishmentApprovalRequest request = request(eventType);
        PunishmentRequestAlertOccurrence occurrence = switch (eventType) {
            case REQUEST_CLAIMED -> PunishmentRequestAlertOccurrence.forClaim(1, REVIEWER_ID);
            case REQUEST_APPROVED, REQUEST_DENIED ->
                    PunishmentRequestAlertOccurrence.forRevision(request.revision(), REVIEWER_ID);
            case REQUEST_EXTERNALLY_FULFILLED ->
                    PunishmentRequestAlertOccurrence.forRevision(request.revision(), ADMIN_ID);
            case REQUEST_SUBMITTED, REQUEST_EXPIRED ->
                    PunishmentRequestAlertOccurrence.forRevision(request.revision());
        };
        UUID direct = audience == PunishmentRequestAlertAudience.DIRECT_RECIPIENT
                ? REQUESTER_ID : null;
        UUID excluded = audience == PunishmentRequestAlertAudience.ELIGIBLE_REVIEWERS
                ? REQUESTER_ID : null;
        StaffRank required = audience == PunishmentRequestAlertAudience.ELIGIBLE_REVIEWERS
                ? minimumRank : null;
        PunishmentRequestAlertIntent intent = new PunishmentRequestAlertIntent(
                ALERT_ID,
                "alert-test:" + eventType + ":" + audience,
                REQUEST_ID,
                request.revision(),
                eventType,
                occurrence,
                audience,
                direct,
                excluded,
                required,
                CaseVisibility.PUBLIC,
                2,
                NOW.minusSeconds(10),
                NOW.plus(Duration.ofHours(1))
        );
        return new PunishmentRequestAlertClaim(
                new PunishmentRequestAlertDeliveryId(ALERT_ID, recipientId),
                intent,
                1,
                NOW.plusSeconds(45)
        );
    }

    static PunishmentRequestAlertClaim reviewerClaim(StaffRank minimumRank, UUID recipientId) {
        PunishmentApprovalRequest request = pending(minimumRank);
        PunishmentRequestAlertIntent intent = new PunishmentRequestAlertIntent(
                ALERT_ID,
                "alert-test:reviewer:" + minimumRank,
                REQUEST_ID,
                request.revision(),
                PunishmentRequestLifecycleEventType.REQUEST_SUBMITTED,
                PunishmentRequestAlertOccurrence.forRevision(request.revision()),
                PunishmentRequestAlertAudience.ELIGIBLE_REVIEWERS,
                null,
                REQUESTER_ID,
                minimumRank,
                CaseVisibility.PUBLIC,
                2,
                NOW.minusSeconds(10),
                NOW.plus(Duration.ofHours(1))
        );
        return new PunishmentRequestAlertClaim(
                new PunishmentRequestAlertDeliveryId(ALERT_ID, recipientId),
                intent,
                1,
                NOW.plusSeconds(45)
        );
    }

    static PunishmentRequestAlertClaim operationalClaim(UUID recipientId) {
        return claim(
                PunishmentRequestLifecycleEventType.REQUEST_SUBMITTED,
                PunishmentRequestAlertAudience.OPERATIONAL_ADMINISTRATORS,
                recipientId,
                null
        );
    }

    private static PunishmentProposal proposal(StaffRank requiredRank) {
        SanctionSpec sanction = new SanctionSpec(SanctionType.NETWORK_BAN, SanctionLength.permanent());
        PunishmentStep step = new PunishmentStep(0, "Permanent ban", List.of(sanction));
        return new PunishmentProposal(
                TARGET_ID,
                new Actor(REQUESTER_ID, "RequestingHelper", StaffRank.HELPER),
                "alert.test.reason",
                "alert-test",
                "Public alert test reason",
                "Private evidence that must never appear",
                "v1",
                CaseVisibility.PUBLIC,
                requiredRank,
                new EscalationDecision(0, 0, 0, List.of(), step),
                List.of(sanction)
        );
    }

    private static PunishmentApprovalRequest resolved(
            PunishmentProposal proposal,
            PunishmentRequestStatus status,
            UUID resolver,
            String note,
            CaseId caseId
    ) {
        return new PunishmentApprovalRequest(
                REQUEST_ID,
                new IdempotencyKey("alert-test-request"),
                proposal,
                NOW.minus(Duration.ofHours(1)),
                NOW.plus(Duration.ofHours(1)),
                status,
                1,
                resolver,
                note,
                caseId,
                NOW.minusSeconds(10)
        );
    }
}
