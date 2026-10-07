package net.enthusia.staff.domain.policyv2.publicview;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.enthusia.staff.domain.policyv2.BehavioralHistoryEntry;
import net.enthusia.staff.domain.policyv2.IncidentFinding;
import net.enthusia.staff.domain.policyv2.RemedySpec;
import net.enthusia.staff.domain.policyv2.persistence.PolicyV2PublicProjection;
import net.enthusia.staff.domain.policyv2.persistence.PolicyV2Store;
import net.enthusia.staff.domain.sanction.SanctionLength;
import net.enthusia.staff.domain.sanction.SanctionSpec;
import net.enthusia.staff.domain.sanction.SanctionType;
import org.junit.jupiter.api.Test;

final class PolicyV2PublicProjectorTest {
    private static final String CASE_ID = "ABCDEFGHJKMNPQRS";
    private static final UUID ACTOR = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final Instant ISSUED = Instant.parse("2026-10-07T01:00:00Z");
    private static final String PRIVATE_SENTINEL = "PRIVATE 10.0.0.1 anticheat staff-note";
    private static final String APPEAL_REFERENCE = "appeal-private-reference";

    private final PolicyV2PublicProjector projector = new PolicyV2PublicProjector();

    @Test
    void privateLifecycleDetailsNeverReachProjection() {
        PolicyV2PublicProjection projection = projector.project(source(
                BehavioralHistoryEntry.FindingState.RECLASSIFIED,
                Optional.of("chat.evasion"),
                List.of(appeal(PolicyV2Store.AppealEventType.SUBMITTED, ISSUED.plusSeconds(25))),
                ISSUED.plusSeconds(30)
        ));

        assertEquals(PolicyV2PublicProjection.Status.APPEALED, projection.status());
        assertEquals("Mute evasion", projection.publicOffense());
        assertEquals(Optional.of("Repeat related chat violation"), projection.relatedHistorySummary());
        assertTrue(projection.timeline().stream().anyMatch(entry ->
                entry.type() == PolicyV2PublicProjection.RevisionType.RECLASSIFIED));
        assertTrue(projection.timeline().stream().anyMatch(entry ->
                entry.type() == PolicyV2PublicProjection.RevisionType.REDUCED));

        String publicData = projection.toString();
        assertFalse(publicData.contains(PRIVATE_SENTINEL));
        assertFalse(publicData.contains(APPEAL_REFERENCE));
        assertFalse(publicData.contains(ACTOR.toString()));
        assertFalse(publicData.contains("remove-message"));
    }

    @Test
    void overturnRevokesSafeSanctionsAndWinsCaseStatus() {
        PolicyV2PublicProjection projection = projector.project(source(
                BehavioralHistoryEntry.FindingState.OVERTURNED,
                Optional.empty(),
                List.of(appeal(PolicyV2Store.AppealEventType.APPROVED, ISSUED.plusSeconds(25))),
                ISSUED.plusSeconds(30)
        ));

        assertEquals(PolicyV2PublicProjection.Status.OVERTURNED, projection.status());
        assertEquals(
                PolicyV2PublicProjection.PublicSanction.SanctionStatus.REVOKED,
                projection.sanctions().getFirst().status()
        );
    }

    @Test
    void completedActionsProduceExpiredState() {
        PolicyV2PublicProjection projection = projector.project(source(
                BehavioralHistoryEntry.FindingState.RECLASSIFIED,
                Optional.of("chat.evasion"),
                List.of(),
                ISSUED.plus(Duration.ofHours(2))
        ));

        assertEquals(PolicyV2PublicProjection.Status.EXPIRED, projection.status());
    }

    private static PolicyV2PublicProjector.Source source(
            BehavioralHistoryEntry.FindingState findingState,
            Optional<String> effectiveOffenseId,
            List<PolicyV2Store.AppealEvent> appeals,
            Instant now
    ) {
        PolicyV2Store.SanctionRevisionRecord initial = sanctionRevision(
                0, PolicyV2Store.SanctionChangeKind.INITIAL, ISSUED
        );
        PolicyV2Store.SanctionRevisionRecord reduced = sanctionRevision(
                1, PolicyV2Store.SanctionChangeKind.LENIENCY, ISSUED.plusSeconds(20)
        );
        return new PolicyV2PublicProjector.Source(
                CASE_ID,
                findingState,
                "chat.spam",
                effectiveOffenseId,
                ISSUED,
                reduced,
                List.of(new PolicyV2Store.RemedyRecord(
                        CASE_ID,
                        new RemedySpec("remove-message", RemedySpec.Type.REMOVE_CONTENT, PRIVATE_SENTINEL),
                        PolicyV2Store.RemedyStatus.SATISFIED,
                        1,
                        ISSUED.plusSeconds(15)
                )),
                List.of(findingRevision(findingState, effectiveOffenseId)),
                List.of(initial, reduced),
                appeals,
                new PolicyV2PublicProjector.PublicMetadata(
                        Optional.of("PlayerOne"),
                        Optional.of("OldName"),
                        "Chat & Spam",
                        "Repeated disruptive chat",
                        Optional.of("chat"),
                        false
                ),
                Map.of("chat.spam", "Chat spam", "chat.evasion", "Mute evasion"),
                "policy-v2-test",
                now,
                3
        );
    }

    private static PolicyV2Store.FindingRevisionRecord findingRevision(
            BehavioralHistoryEntry.FindingState state,
            Optional<String> effectiveOffenseId
    ) {
        boolean overturned = state == BehavioralHistoryEntry.FindingState.OVERTURNED;
        return new PolicyV2Store.FindingRevisionRecord(
                CASE_ID,
                1,
                overturned
                        ? PolicyV2Store.FindingChangeKind.OVERTURN
                        : PolicyV2Store.FindingChangeKind.RECLASSIFICATION,
                "chat.spam",
                effectiveOffenseId,
                effectiveOffenseId.map(id -> new IncidentFinding(id, Map.of())),
                PRIVATE_SENTINEL,
                ACTOR,
                Optional.of(APPEAL_REFERENCE),
                ISSUED.plusSeconds(10)
        );
    }

    private static PolicyV2Store.SanctionRevisionRecord sanctionRevision(
            long revision,
            PolicyV2Store.SanctionChangeKind kind,
            Instant occurredAt
    ) {
        return new PolicyV2Store.SanctionRevisionRecord(
                CASE_ID,
                revision,
                kind,
                List.of(new SanctionSpec(
                        SanctionType.MUTE,
                        SanctionLength.temporary(Duration.ofMinutes(30))
                )),
                PRIVATE_SENTINEL,
                ACTOR,
                Optional.of(APPEAL_REFERENCE),
                occurredAt
        );
    }

    private static PolicyV2Store.AppealEvent appeal(
            PolicyV2Store.AppealEventType type,
            Instant occurredAt
    ) {
        return new PolicyV2Store.AppealEvent(
                UUID.fromString("22222222-2222-2222-2222-222222222222"),
                CASE_ID,
                APPEAL_REFERENCE,
                type,
                Optional.of(ACTOR),
                PRIVATE_SENTINEL,
                occurredAt
        );
    }
}
