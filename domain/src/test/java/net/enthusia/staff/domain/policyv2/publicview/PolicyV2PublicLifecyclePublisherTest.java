package net.enthusia.staff.domain.policyv2.publicview;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.enthusia.staff.domain.policyv2.BehavioralHistoryEntry;
import net.enthusia.staff.domain.policyv2.HistoryAssessment;
import net.enthusia.staff.domain.policyv2.IncidentFinding;
import net.enthusia.staff.domain.policyv2.PolicyResolution;
import net.enthusia.staff.domain.policyv2.RemedySpec;
import net.enthusia.staff.domain.policyv2.persistence.PolicyV2PublicProjection;
import net.enthusia.staff.domain.policyv2.persistence.PolicyV2Store;
import net.enthusia.staff.domain.sanction.SanctionLength;
import net.enthusia.staff.domain.sanction.SanctionSpec;
import net.enthusia.staff.domain.sanction.SanctionType;
import org.junit.jupiter.api.Test;

class PolicyV2PublicLifecyclePublisherTest {
    private static final String CASE_ID = "ABCDEFGHJKMNPQRS";
    private static final String OFFENSE_ID = "profile.bad";
    private static final Instant NOW = Instant.parse("2026-10-07T10:00:00Z");
    private static final UUID ACTOR = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Test
    void lifecyclePublicationAdvancesCanonicalRevisionAndPreservesSafeMetadata() {
        State state = new State();
        PolicyV2PublicLifecyclePublisher publisher = new PolicyV2PublicLifecyclePublisher(state.store());

        PolicyV2PublicProjection initial = publisher.publish(
                CASE_ID, context("BlockedName"), "public-initial", NOW, NOW
        );
        state.remedyStatus = PolicyV2Store.RemedyStatus.SATISFIED;
        PolicyV2PublicProjection repaired = publisher.republishExisting(
                CASE_ID, Optional.of("GoodName"), "public-repair", NOW.plusSeconds(1), NOW.plusSeconds(1)
        ).orElseThrow();

        assertEquals(0L, initial.revision());
        assertEquals(1L, repaired.revision());
        assertEquals(Optional.of("GoodName"), repaired.currentPlayerName());
        assertEquals(
                PolicyV2PublicProjection.PublicRemedy.RemedyStatus.SATISFIED,
                repaired.remedies().getFirst().status()
        );
        assertEquals("Profile compliance required", repaired.publicReason());
    }

    @Test
    void republishWithoutExistingProjectionDoesNothing() {
        State state = new State();
        PolicyV2PublicLifecyclePublisher publisher = new PolicyV2PublicLifecyclePublisher(state.store());

        assertTrue(publisher.republishExisting(
                CASE_ID, Optional.of("GoodName"), "missing", NOW, NOW
        ).isEmpty());
        assertEquals(0, state.publishCount);
    }

    @Test
    void saturatedLifecycleHistoryFailsClosedInsteadOfPublishingTruncatedTimeline() {
        State state = new State();
        state.saturateAppeals = true;
        PolicyV2PublicLifecyclePublisher publisher = new PolicyV2PublicLifecyclePublisher(state.store());

        assertThrows(
                IllegalStateException.class,
                () -> publisher.publish(CASE_ID, context("PlayerOne"), "saturated", NOW, NOW)
        );
        assertEquals(0, state.publishCount);
    }

    @Test
    void persistenceConflictPropagatesInsteadOfServingUncommittedProjection() {
        State state = new State();
        state.failPublish = true;
        PolicyV2PublicLifecyclePublisher publisher = new PolicyV2PublicLifecyclePublisher(state.store());

        assertThrows(
                PolicyV2Store.Conflict.class,
                () -> publisher.publish(CASE_ID, context("PlayerOne"), "conflict", NOW, NOW)
        );
        assertEquals(0, state.publishCount);
    }

    private static PolicyV2PublicLifecyclePublisher.PublicContext context(String player) {
        return new PolicyV2PublicLifecyclePublisher.PublicContext(
                Optional.of(player),
                Optional.of("BlockedName"),
                "Profile",
                "Profile compliance required",
                Optional.empty(),
                Map.of(OFFENSE_ID, "Username compliance"),
                false
        );
    }

    private static final class State {
        private PolicyV2Store.RemedyStatus remedyStatus = PolicyV2Store.RemedyStatus.REQUIRED;
        private PolicyV2PublicProjection projection;
        private int publishCount;
        private boolean failPublish;
        private boolean saturateAppeals;

        private PolicyV2Store store() {
            return (PolicyV2Store) Proxy.newProxyInstance(
                    Thread.currentThread().getContextClassLoader(),
                    new Class<?>[]{PolicyV2Store.class},
                    (proxy, method, arguments) -> invoke(method.getName(), arguments)
            );
        }

        private Object invoke(String method, Object[] arguments) {
            return switch (method) {
                case "findCase" -> Optional.of(caseRecord());
                case "publicProjection" -> Optional.ofNullable(projection);
                case "publishProjection" -> publish((PolicyV2Store.PublishProjectionRequest) arguments[0]);
                case "findingRevisions" -> List.of();
                case "appealHistory" -> saturateAppeals
                        ? java.util.Collections.nCopies(500, appeal())
                        : List.of();
                case "sanctionRevisions" -> List.of(caseRecord().currentSanctions());
                default -> throw new AssertionError("unexpected store method " + method);
            };
        }

        private PolicyV2PublicProjection publish(PolicyV2Store.PublishProjectionRequest request) {
            if (failPublish) {
                throw new PolicyV2Store.Conflict("forced public projection conflict");
            }
            projection = request.projection();
            publishCount++;
            return projection;
        }

        private PolicyV2Store.CaseRecord caseRecord() {
            IncidentFinding finding = new IncidentFinding(OFFENSE_ID, Map.of());
            PolicyResolution resolution = PolicyResolution.requiresReview(
                    "policy-v2-test", OFFENSE_ID, "review-only", HistoryAssessment.empty()
            );
            return new PolicyV2Store.CaseRecord(
                    CASE_ID,
                    UUID.fromString("11111111-1111-1111-1111-111111111111"),
                    UUID.fromString("22222222-2222-2222-2222-222222222222"),
                    UUID.fromString("33333333-3333-3333-3333-333333333333"),
                    finding,
                    Optional.of(finding),
                    BehavioralHistoryEntry.FindingState.CONFIRMED,
                    NOW,
                    0L,
                    0L,
                    resolution,
                    List.of(),
                    List.of(remedy()),
                    sanctions()
            );
        }

        private PolicyV2Store.RemedyRecord remedy() {
            return new PolicyV2Store.RemedyRecord(
                    CASE_ID,
                    new RemedySpec("profile-remedy", RemedySpec.Type.CORRECT_PROFILE, "private"),
                    remedyStatus,
                    remedyStatus == PolicyV2Store.RemedyStatus.REQUIRED ? 0L : 1L,
                    NOW
            );
        }

        private static PolicyV2Store.AppealEvent appeal() {
            return new PolicyV2Store.AppealEvent(
                    UUID.fromString("44444444-4444-4444-4444-444444444444"),
                    CASE_ID,
                    "appeal-test",
                    PolicyV2Store.AppealEventType.SUBMITTED,
                    Optional.of(ACTOR),
                    "private",
                    NOW
            );
        }

        private static PolicyV2Store.SanctionRevisionRecord sanctions() {
            return new PolicyV2Store.SanctionRevisionRecord(
                    CASE_ID,
                    0L,
                    PolicyV2Store.SanctionChangeKind.INITIAL,
                    List.of(new SanctionSpec(SanctionType.MUTE, SanctionLength.permanent())),
                    "private",
                    ACTOR,
                    Optional.empty(),
                    NOW
            );
        }
    }
}
