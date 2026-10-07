package net.enthusia.staff.velocity;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.enthusia.staff.domain.policyv2.BehavioralHistoryEntry;
import net.enthusia.staff.domain.policyv2.IncidentAttributeValue;
import net.enthusia.staff.domain.policyv2.IncidentFinding;
import net.enthusia.staff.domain.policyv2.RemedySpec;
import net.enthusia.staff.domain.policyv2.persistence.PolicyV2PublicProjection;
import net.enthusia.staff.domain.policyv2.persistence.PolicyV2Store;
import net.enthusia.staff.domain.policyv2.publicview.PolicyV2PublicDiscordAdapter;
import net.enthusia.staff.domain.policyv2.publicview.PolicyV2PublicProjector;
import net.enthusia.staff.domain.sanction.SanctionLength;
import net.enthusia.staff.domain.sanction.SanctionSpec;
import net.enthusia.staff.domain.sanction.SanctionType;
import org.junit.jupiter.api.Test;

class PolicyV2PublicPrivacyBoundaryTest {
    private static final String CASE_ID = "ABCDEFGHJKMNPQRS";
    private static final String EVASION = "chat.evasion";
    private static final Instant ISSUED = Instant.parse("2026-10-07T10:00:00Z");
    private static final UUID ACTOR = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final List<String> PRIVATE_SENTINELS = List.of(
            "PRIVATE-IP-192.0.2.55",
            "PRIVATE-ALT-LINK-TEST",
            "PRIVATE-EVIDENCE-TEXT",
            "PRIVATE-INTERNAL-NOTE",
            "PRIVATE-DETECTION-SIGNAL",
            "PRIVATE-APPEAL-NOTE"
    );

    @Test
    void websiteDtoAndSerializedJsonContainOnlyCanonicalProjectionFields() throws Exception {
        PolicyV2PublicProjection projection = new PolicyV2PublicProjector().project(source());
        PolicyV2PublicWebsiteView view = new PolicyV2PublicWebsiteView(caseId -> Optional.of(projection));
        Map<String, Object> response = view.find(CASE_ID).orElseThrow();
        String json = new ObjectMapper().writeValueAsString(response);
        PolicyV2PublicDiscordAdapter.Message discord = PolicyV2PublicDiscordAdapter.toMessage(projection);

        assertTrue(response.keySet().containsAll(List.of(
                "caseId", "player", "category", "offense", "reason", "sanctions", "remedies", "timeline"
        )));
        assertPrivateSentinelsAbsent(projection.toString());
        assertPrivateSentinelsAbsent(response.toString());
        assertPrivateSentinelsAbsent(json);
        assertPrivateSentinelsAbsent(discord.toString());
        assertPrivateSentinelsAbsent(projection.publicReason());
        assertPrivateSentinelsAbsent(projection.timeline().toString());
    }

    private static PolicyV2PublicProjector.Source source() {
        PolicyV2Store.SanctionRevisionRecord initial = sanction(0, PolicyV2Store.SanctionChangeKind.INITIAL);
        PolicyV2Store.SanctionRevisionRecord reduced = sanction(1, PolicyV2Store.SanctionChangeKind.LENIENCY);
        return new PolicyV2PublicProjector.Source(
                CASE_ID,
                BehavioralHistoryEntry.FindingState.RECLASSIFIED,
                "chat.spam",
                Optional.of(EVASION),
                ISSUED,
                reduced,
                List.of(new PolicyV2Store.RemedyRecord(
                        CASE_ID,
                        new RemedySpec("remove-message", RemedySpec.Type.REMOVE_CONTENT, PRIVATE_SENTINELS.get(2)),
                        PolicyV2Store.RemedyStatus.SATISFIED,
                        1,
                        ISSUED.plusSeconds(15)
                )),
                List.of(findingRevision()),
                List.of(initial, reduced),
                List.of(appeal()),
                new PolicyV2PublicProjector.PublicMetadata(
                        Optional.of("PlayerOne"),
                        Optional.of("OldName"),
                        "Chat & Spam",
                        "Repeated disruptive chat",
                        Optional.of("repeat related chat violation"),
                        false
                ),
                Map.of("chat.spam", "Chat spam", EVASION, "Mute evasion"),
                "policy-v2-test",
                ISSUED.plusSeconds(30),
                3
        );
    }

    private static PolicyV2Store.FindingRevisionRecord findingRevision() {
        IncidentFinding resulting = new IncidentFinding(
                EVASION,
                Map.of("private-network", new IncidentAttributeValue.TextValue(PRIVATE_SENTINELS.getFirst()))
        );
        return new PolicyV2Store.FindingRevisionRecord(
                CASE_ID,
                1,
                PolicyV2Store.FindingChangeKind.RECLASSIFICATION,
                "chat.spam",
                Optional.of(EVASION),
                Optional.of(resulting),
                PRIVATE_SENTINELS.get(3),
                ACTOR,
                Optional.of(PRIVATE_SENTINELS.get(1)),
                ISSUED.plusSeconds(10)
        );
    }

    private static PolicyV2Store.SanctionRevisionRecord sanction(
            long revision,
            PolicyV2Store.SanctionChangeKind kind
    ) {
        return new PolicyV2Store.SanctionRevisionRecord(
                CASE_ID,
                revision,
                kind,
                List.of(new SanctionSpec(
                        SanctionType.MUTE,
                        SanctionLength.temporary(Duration.ofMinutes(revision == 0 ? 60 : 30))
                )),
                PRIVATE_SENTINELS.get(4),
                ACTOR,
                Optional.of(PRIVATE_SENTINELS.get(1)),
                ISSUED.plusSeconds(revision * 20)
        );
    }

    private static PolicyV2Store.AppealEvent appeal() {
        return new PolicyV2Store.AppealEvent(
                UUID.fromString("22222222-2222-2222-2222-222222222222"),
                CASE_ID,
                PRIVATE_SENTINELS.get(1),
                PolicyV2Store.AppealEventType.SUBMITTED,
                Optional.of(ACTOR),
                PRIVATE_SENTINELS.get(5),
                ISSUED.plusSeconds(25)
        );
    }

    private static void assertPrivateSentinelsAbsent(String output) {
        PRIVATE_SENTINELS.forEach(sentinel -> assertFalse(output.contains(sentinel), sentinel));
    }
}
