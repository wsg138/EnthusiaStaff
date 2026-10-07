package net.enthusia.staff.domain.policyv2.publicview;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.enthusia.staff.domain.policyv2.BehavioralHistoryEntry;
import net.enthusia.staff.domain.policyv2.IncidentFinding;
import net.enthusia.staff.domain.policyv2.persistence.PolicyV2PublicProjection;
import net.enthusia.staff.domain.policyv2.persistence.PolicyV2Store;
import net.enthusia.staff.domain.sanction.SanctionLength;
import net.enthusia.staff.domain.sanction.SanctionSpec;
import net.enthusia.staff.domain.sanction.SanctionType;
import org.junit.jupiter.api.Test;

class PolicyV2PublicStateMatrixTest {
    private static final String CASE_ID = "ABCDEFGHJKMNPQRS";
    private static final String SPAM = "chat.spam";
    private static final String EVASION = "chat.evasion";
    private static final Instant ISSUED = Instant.parse("2026-10-07T10:00:00Z");
    private static final UUID ACTOR = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private final PolicyV2PublicProjector projector = new PolicyV2PublicProjector();

    @Test
    void canonicalProjectionCoversAllRequiredPublicStates() {
        assertEquals(PolicyV2PublicProjection.Status.ACTIVE, projector.project(active()).status());
        assertEquals(PolicyV2PublicProjection.Status.EXPIRED, projector.project(expired()).status());
        assertEquals(PolicyV2PublicProjection.Status.REDUCED, projector.project(reduced()).status());
        assertEquals(PolicyV2PublicProjection.Status.APPEALED, projector.project(appealed()).status());
        assertEquals(PolicyV2PublicProjection.Status.RECLASSIFIED, projector.project(reclassified()).status());
        assertEquals(PolicyV2PublicProjection.Status.OVERTURNED, projector.project(overturned()).status());
    }

    private static PolicyV2PublicProjector.Source active() {
        return source(
                BehavioralHistoryEntry.FindingState.CONFIRMED,
                Optional.of(SPAM),
                initial(0, Duration.ofHours(2), ISSUED),
                List.of(),
                List.of(),
                List.of(),
                ISSUED.plusSeconds(30)
        );
    }

    private static PolicyV2PublicProjector.Source expired() {
        return source(
                BehavioralHistoryEntry.FindingState.CONFIRMED,
                Optional.of(SPAM),
                initial(0, Duration.ofMinutes(5), ISSUED),
                List.of(),
                List.of(),
                List.of(),
                ISSUED.plus(Duration.ofHours(1))
        );
    }

    private static PolicyV2PublicProjector.Source reduced() {
        PolicyV2Store.SanctionRevisionRecord reduced =
                initial(1, Duration.ofHours(1), ISSUED.plusSeconds(20), PolicyV2Store.SanctionChangeKind.LENIENCY);
        return source(
                BehavioralHistoryEntry.FindingState.CONFIRMED,
                Optional.of(SPAM),
                reduced,
                List.of(),
                List.of(initial(0, Duration.ofHours(2), ISSUED), reduced),
                List.of(),
                ISSUED.plusSeconds(30)
        );
    }

    private static PolicyV2PublicProjector.Source appealed() {
        return source(
                BehavioralHistoryEntry.FindingState.CONFIRMED,
                Optional.of(SPAM),
                initial(0, Duration.ofHours(2), ISSUED),
                List.of(),
                List.of(),
                List.of(appeal(PolicyV2Store.AppealEventType.SUBMITTED, ISSUED.plusSeconds(10))),
                ISSUED.plusSeconds(30)
        );
    }

    private static PolicyV2PublicProjector.Source reclassified() {
        PolicyV2Store.FindingRevisionRecord revision = new PolicyV2Store.FindingRevisionRecord(
                CASE_ID,
                1,
                PolicyV2Store.FindingChangeKind.RECLASSIFICATION,
                SPAM,
                Optional.of(EVASION),
                Optional.of(new IncidentFinding(EVASION, Map.of())),
                "finding reclassified",
                ACTOR,
                Optional.empty(),
                ISSUED.plusSeconds(20)
        );
        return source(
                BehavioralHistoryEntry.FindingState.RECLASSIFIED,
                Optional.of(EVASION),
                initial(0, Duration.ofHours(2), ISSUED),
                List.of(revision),
                List.of(),
                List.of(),
                ISSUED.plusSeconds(30)
        );
    }

    private static PolicyV2PublicProjector.Source overturned() {
        PolicyV2Store.FindingRevisionRecord revision = new PolicyV2Store.FindingRevisionRecord(
                CASE_ID,
                1,
                PolicyV2Store.FindingChangeKind.OVERTURN,
                SPAM,
                Optional.empty(),
                Optional.empty(),
                "finding overturned",
                ACTOR,
                Optional.empty(),
                ISSUED.plusSeconds(20)
        );
        return source(
                BehavioralHistoryEntry.FindingState.OVERTURNED,
                Optional.empty(),
                initial(0, Duration.ofHours(2), ISSUED),
                List.of(revision),
                List.of(),
                List.of(appeal(PolicyV2Store.AppealEventType.APPROVED, ISSUED.plusSeconds(25))),
                ISSUED.plusSeconds(30)
        );
    }

    private static PolicyV2PublicProjector.Source source(
            BehavioralHistoryEntry.FindingState findingState,
            Optional<String> effectiveOffense,
            PolicyV2Store.SanctionRevisionRecord current,
            List<PolicyV2Store.FindingRevisionRecord> findingRevisions,
            List<PolicyV2Store.SanctionRevisionRecord> sanctionRevisions,
            List<PolicyV2Store.AppealEvent> appeals,
            Instant now
    ) {
        return new PolicyV2PublicProjector.Source(
                CASE_ID,
                findingState,
                SPAM,
                effectiveOffense,
                ISSUED,
                current,
                List.of(),
                findingRevisions,
                sanctionRevisions,
                appeals,
                new PolicyV2PublicProjector.PublicMetadata(
                        Optional.of("PlayerOne"),
                        Optional.of("OldName"),
                        "Chat & Spam",
                        "Repeated disruptive chat",
                        Optional.empty(),
                        true
                ),
                Map.of(SPAM, "Chat spam", EVASION, "Mute evasion"),
                "policy-v2-test",
                now,
                1
        );
    }

    private static PolicyV2Store.SanctionRevisionRecord initial(
            long revision,
            Duration duration,
            Instant occurredAt
    ) {
        return initial(revision, duration, occurredAt, PolicyV2Store.SanctionChangeKind.INITIAL);
    }

    private static PolicyV2Store.SanctionRevisionRecord initial(
            long revision,
            Duration duration,
            Instant occurredAt,
            PolicyV2Store.SanctionChangeKind kind
    ) {
        return new PolicyV2Store.SanctionRevisionRecord(
                CASE_ID,
                revision,
                kind,
                List.of(new SanctionSpec(SanctionType.MUTE, SanctionLength.temporary(duration))),
                kind == PolicyV2Store.SanctionChangeKind.LENIENCY ? "punishment reduced on appeal" : "initial",
                ACTOR,
                Optional.empty(),
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
                "appeal-safe-test-ref",
                type,
                Optional.of(ACTOR),
                "safe private note",
                occurredAt
        );
    }
}
