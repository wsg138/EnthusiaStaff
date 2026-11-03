package net.enthusia.staff.domain.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.enthusia.staff.common.CaseId;
import net.enthusia.staff.common.SecureIdentifiers;
import net.enthusia.staff.domain.OperationalMode;
import net.enthusia.staff.domain.auth.Actor;
import net.enthusia.staff.domain.auth.DefaultAuthorizationPolicy;
import net.enthusia.staff.domain.auth.StaffRank;
import net.enthusia.staff.domain.casefile.CaseVisibility;
import net.enthusia.staff.domain.escalation.AltInheritanceMode;
import net.enthusia.staff.domain.escalation.EscalationEngine;
import net.enthusia.staff.domain.escalation.PriorOffense;
import net.enthusia.staff.domain.escalation.PunishmentStep;
import net.enthusia.staff.domain.escalation.ReasonPolicy;
import net.enthusia.staff.domain.ports.AtomicReasonPolicyRepository;
import net.enthusia.staff.domain.ports.ModerationStore;
import net.enthusia.staff.domain.ports.PunishmentDraftStore;
import net.enthusia.staff.domain.sanction.SanctionLength;
import net.enthusia.staff.domain.sanction.SanctionSpec;
import net.enthusia.staff.domain.sanction.SanctionType;
import org.junit.jupiter.api.Test;

class PunishmentDraftWorkflowTest {
    private static final Instant NOW = Instant.parse("2026-07-27T12:00:00Z");
    private static final UUID DRAFT_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID ACTOR_ID = UUID.fromString("20000000-0000-0000-0000-000000000002");
    private static final UUID TARGET_ID = UUID.fromString("20000000-0000-0000-0000-000000000003");

    @Test
    void preparedDraftPersistsCompleteExpectationForTwentyFourHours() {
        Fixture fixture = fixture();

        PunishmentDraftEvaluation.Prepared prepared = assertInstanceOf(
                PunishmentDraftEvaluation.Prepared.class,
                fixture.workflow.prepare(request(StaffRank.MOD), OperationalMode.ACTIVE)
        );

        assertEquals(DRAFT_ID, prepared.draft().draftId());
        assertEquals(CaseVisibility.PUBLIC, prepared.draft().visibility());
        assertEquals(NOW.plus(Duration.ofHours(24)), prepared.draft().expiresAt());
        assertEquals(PunishmentExpectation.from(prepared.assessment()), prepared.draft().expectation());
        assertEquals(prepared.draft(), fixture.workflow.resume(ACTOR_ID, TARGET_ID).orElseThrow());
    }

    @Test
    void guidedPreviewReadsAuthoritativePolicyWithoutPersistingADraftOrCase() {
        Fixture fixture = fixture();
        PunishmentEvaluation.Allowed assessed = assertInstanceOf(
                PunishmentEvaluation.Allowed.class,
                fixture.workflow.preview(request(StaffRank.MOD), OperationalMode.ACTIVE)
        );
        assertEquals("v1", assessed.assessment().configurationVersion());
        assertTrue(fixture.workflow.resume(ACTOR_ID, TARGET_ID).isEmpty());
        assertTrue(fixture.moderation.plans.isEmpty());
    }

    @Test
    void guidedPreparationRejectsPolicyReloadBetweenPreviewAndPersistence() {
        Fixture fixture = fixture();
        PunishmentAssessment preview = assertInstanceOf(PunishmentEvaluation.Allowed.class,
                fixture.workflow.preview(request(StaffRank.MOD), OperationalMode.ACTIVE)).assessment();
        fixture.policies.replace("v2", List.of(policy(Duration.ofDays(7))));

        PunishmentDraftEvaluation.Rejected changed = assertInstanceOf(
                PunishmentDraftEvaluation.Rejected.class,
                fixture.workflow.prepare(request(StaffRank.MOD), OperationalMode.ACTIVE, preview));
        assertEquals("RECOMMENDATION_CHANGED", changed.code());
        assertTrue(fixture.workflow.resume(ACTOR_ID, TARGET_ID).isEmpty(),
                "A guided audit computed from an obsolete policy must not be saved");
    }

    @Test
    void guidedPreparationRejectsNewHistoryBetweenPreviewAndPersistence() {
        Fixture fixture = fixture();
        SanctionSpec warning = new SanctionSpec(SanctionType.WARNING, SanctionLength.instant());
        SanctionSpec mute = new SanctionSpec(SanctionType.MUTE,
                SanctionLength.temporary(Duration.ofHours(2)));
        fixture.policies.replace("v1", List.of(new ReasonPolicy("chat.toxicity", "chat",
                "Chat toxicity", 10, true, List.of(
                new PunishmentStep(0, "First", List.of(warning)),
                new PunishmentStep(1, "Escalated", List.of(mute))))));
        PunishmentAssessment preview = assertInstanceOf(PunishmentEvaluation.Allowed.class,
                fixture.workflow.preview(request(StaffRank.MOD), OperationalMode.ACTIVE)).assessment();
        assertEquals(0, preview.escalation().selectedStep().ordinal());
        fixture.moderation.priorOffenses = List.of(new PriorOffense(
                "chat", 10, 0, NOW.minus(Duration.ofHours(1)), true, false));

        PunishmentDraftEvaluation.Rejected changed = assertInstanceOf(
                PunishmentDraftEvaluation.Rejected.class,
                fixture.workflow.prepare(request(StaffRank.MOD), OperationalMode.ACTIVE, preview));
        assertEquals("RECOMMENDATION_CHANGED", changed.code());
        assertTrue(fixture.workflow.resume(ACTOR_ID, TARGET_ID).isEmpty(),
                "History drift must not persist an audit with an obsolete guided outcome");
    }

    @Test
    void guidedPreparationAcceptsStableBaselineWithConfiguredTierOverride() {
        Fixture fixture = fixture();
        SanctionSpec warning = new SanctionSpec(SanctionType.WARNING, SanctionLength.instant());
        SanctionSpec mute = new SanctionSpec(SanctionType.MUTE,
                SanctionLength.temporary(Duration.ofHours(2)));
        fixture.policies.replace("v1", List.of(new ReasonPolicy("chat.toxicity", "chat",
                "Chat toxicity", 10, true, List.of(
                new PunishmentStep(0, "First", List.of(warning)),
                new PunishmentStep(1, "Escalated", List.of(mute))))));
        PunishmentAssessment preview = assertInstanceOf(PunishmentEvaluation.Allowed.class,
                fixture.workflow.preview(request(StaffRank.ADMIN), OperationalMode.ACTIVE)).assessment();
        PreparePunishmentDraftRequest selected = new PreparePunishmentDraftRequest(
                TARGET_ID, actor(StaffRank.ADMIN), "chat.toxicity",
                "Guided incident v1 | severity validated", CaseVisibility.PUBLIC,
                "punish", List.of(mute));

        PunishmentDraftEvaluation.Prepared prepared = assertInstanceOf(
                PunishmentDraftEvaluation.Prepared.class,
                fixture.workflow.prepare(selected, OperationalMode.ACTIVE, preview));
        assertEquals(List.of(mute), prepared.draft().expectation().sanctions());
        assertEquals(0, prepared.draft().expectation().stepOrdinal());
    }

    @Test
    void guidedDraftNoteEditRejectsHistoryDriftWithoutOverwritingSavedDraft() {
        Fixture fixture = fixture();
        SanctionSpec warning = new SanctionSpec(SanctionType.WARNING, SanctionLength.instant());
        SanctionSpec mute = new SanctionSpec(SanctionType.MUTE,
                SanctionLength.temporary(Duration.ofHours(2)));
        fixture.policies.replace("v1", List.of(new ReasonPolicy("chat.toxicity", "chat",
                "Chat toxicity", 10, true, List.of(
                new PunishmentStep(0, "First", List.of(warning)),
                new PunishmentStep(1, "Escalated", List.of(mute))))));
        PreparePunishmentDraftRequest originalRequest = new PreparePunishmentDraftRequest(
                TARGET_ID, actor(StaffRank.MOD), "chat.toxicity",
                "Guided incident v1 | original facts", CaseVisibility.PUBLIC, "punish");
        PunishmentDraftEvaluation.Prepared original = assertInstanceOf(
                PunishmentDraftEvaluation.Prepared.class,
                fixture.workflow.prepare(originalRequest, OperationalMode.ACTIVE));
        fixture.moderation.priorOffenses = List.of(new PriorOffense(
                "chat", 10, 0, NOW.minus(Duration.ofHours(1)), true, false));
        PreparePunishmentDraftRequest edited = new PreparePunishmentDraftRequest(
                TARGET_ID, actor(StaffRank.MOD), "chat.toxicity",
                "Guided incident v1 | original facts | Staff note: clarified",
                CaseVisibility.PRIVATE, "punish");
        PunishmentDraftEvaluation.Rejected changed = assertInstanceOf(
                PunishmentDraftEvaluation.Rejected.class,
                fixture.workflow.prepare(edited, OperationalMode.ACTIVE,
                        null, original.draft().expectation()));
        assertEquals("RECOMMENDATION_CHANGED", changed.code());
        assertEquals(original.draft(), fixture.workflow.resume(ACTOR_ID, TARGET_ID).orElseThrow(),
                "Changing notes or visibility must not silently overwrite the reviewed outcome");
    }

    @Test
    void guidedDraftNoteEditPreservesReviewedOutcomeWhenHistoryStable() {
        Fixture fixture = fixture();
        PreparePunishmentDraftRequest originalRequest = new PreparePunishmentDraftRequest(
                TARGET_ID, actor(StaffRank.MOD), "chat.toxicity",
                "Guided incident v1 | original facts", CaseVisibility.PUBLIC, "punish");
        PunishmentDraftEvaluation.Prepared original = assertInstanceOf(
                PunishmentDraftEvaluation.Prepared.class,
                fixture.workflow.prepare(originalRequest, OperationalMode.ACTIVE));
        PreparePunishmentDraftRequest edited = new PreparePunishmentDraftRequest(
                TARGET_ID, actor(StaffRank.MOD), "chat.toxicity",
                "Guided incident v1 | original facts | Staff note: clarified",
                CaseVisibility.PRIVATE, "punish");
        PunishmentDraftEvaluation.Prepared revised = assertInstanceOf(
                PunishmentDraftEvaluation.Prepared.class,
                fixture.workflow.prepare(edited, OperationalMode.ACTIVE,
                        original.assessment(), original.draft().expectation()));
        assertEquals(original.draft().expectation(), revised.draft().expectation());
        assertEquals(CaseVisibility.PRIVATE, revised.draft().visibility());
        assertTrue(revised.draft().internalExplanation().contains("Staff note: clarified"));
    }

    @Test
    void guidedExactConfiguredHigherStepSurvivesDraftConfirmation() {
        Fixture fixture = fixture();
        SanctionSpec warn = new SanctionSpec(SanctionType.WARNING, SanctionLength.instant());
        SanctionSpec mute = new SanctionSpec(SanctionType.MUTE,
                SanctionLength.temporary(Duration.ofHours(2)));
        ReasonPolicy ladder = new ReasonPolicy("chat.toxicity", "chat",
                "Chat toxicity", 10, true, List.of(
                new PunishmentStep(0, "Initial warning", List.of(warn)),
                new PunishmentStep(1, "Elevated mute", List.of(mute))
        ));
        fixture.policies.replace("v1", List.of(ladder));
        PreparePunishmentDraftRequest selectedTier = new PreparePunishmentDraftRequest(
                TARGET_ID, actor(StaffRank.ADMIN), "chat.toxicity",
                "Guided incident v1 | verified escalation", CaseVisibility.PUBLIC,
                "punish", List.of(mute));
        PunishmentDraftEvaluation.Prepared prepared = assertInstanceOf(
                PunishmentDraftEvaluation.Prepared.class,
                fixture.workflow.prepare(selectedTier, OperationalMode.ACTIVE));
        assertEquals(0, prepared.draft().expectation().stepOrdinal(),
                "History is still first-offense even though guided chose another policy step");
        assertEquals(List.of(mute), prepared.draft().expectation().sanctions());
        assertFalse(prepared.draft().expectation().customDuration(),
                "An exact configured tier does not count as a custom duration");
        PunishmentDraftConfirmation.Rejected demoted = assertInstanceOf(
                PunishmentDraftConfirmation.Rejected.class,
                fixture.workflow.confirmRouted(DRAFT_ID, actor(StaffRank.MOD),
                        OperationalMode.ACTIVE));
        assertEquals("FORBIDDEN_OVERRIDE", demoted.code(),
                "A moderator cannot replay an administrator's elevated tier");
        assertTrue(fixture.workflow.find(DRAFT_ID, ACTOR_ID).isPresent());
        assertTrue(fixture.moderation.plans.isEmpty());
        PunishmentDraftConfirmation.Applied confirmed = assertInstanceOf(
                PunishmentDraftConfirmation.Applied.class,
                fixture.workflow.confirmRouted(DRAFT_ID, actor(StaffRank.ADMIN),
                        OperationalMode.ACTIVE),
                "Confirm must replay the saved configured tier, not silently revert to history");
        assertEquals(new CaseId("TESTCASE00000001"), confirmed.accepted().caseId());
        assertEquals(1, fixture.moderation.plans.size());
        assertEquals(List.of(mute), fixture.moderation.plans.getFirst().sanctions());
    }

    @Test
    void developerCanPrepareRequestDraftWithoutDirectPunishmentAuthority() {
        Fixture fixture = fixture();

        PunishmentDraftEvaluation.Prepared prepared = assertInstanceOf(
                PunishmentDraftEvaluation.Prepared.class,
                fixture.workflow.prepare(request(StaffRank.DEVELOPER), OperationalMode.ACTIVE)
        );

        assertEquals(StaffRank.DEVELOPER, request(StaffRank.DEVELOPER).actor().rank());
        assertEquals(DRAFT_ID, prepared.draft().draftId());
        assertTrue(fixture.workflow.find(DRAFT_ID, ACTOR_ID).isPresent());
        assertTrue(fixture.moderation.plans.isEmpty());
    }

    @Test
    void directConfirmationReauthorizesCurrentActorRankAndRetainsRejectedDraft() {
        Fixture fixture = fixture();
        fixture.workflow.prepare(request(StaffRank.MOD), OperationalMode.ACTIVE);

        PunishmentResult.Rejected rejected = assertInstanceOf(
                PunishmentResult.Rejected.class,
                fixture.workflow.confirm(
                        DRAFT_ID,
                        new Actor(ACTOR_ID, "Developer", StaffRank.DEVELOPER),
                        OperationalMode.ACTIVE
                )
        );

        assertEquals("FORBIDDEN", rejected.code());
        assertTrue(fixture.workflow.find(DRAFT_ID, ACTOR_ID).isPresent());
        assertTrue(fixture.moderation.plans.isEmpty());
    }

    @Test
    void acceptedConfirmationDeletesDraftAndCannotExecuteAgain() {
        Fixture fixture = fixture();
        fixture.workflow.prepare(request(StaffRank.MOD), OperationalMode.ACTIVE);

        PunishmentResult.Accepted accepted = assertInstanceOf(
                PunishmentResult.Accepted.class,
                fixture.workflow.confirm(DRAFT_ID, actor(StaffRank.MOD), OperationalMode.ACTIVE)
        );

        assertEquals(new CaseId("TESTCASE00000001"), accepted.caseId());
        assertFalse(fixture.workflow.find(DRAFT_ID, ACTOR_ID).isPresent());
        assertEquals(1, fixture.moderation.plans.size());
        assertEquals(
                "DRAFT_NOT_FOUND",
                assertInstanceOf(
                        PunishmentResult.Rejected.class,
                        fixture.workflow.confirm(DRAFT_ID, actor(StaffRank.MOD), OperationalMode.ACTIVE)
                ).code()
        );
        assertEquals(1, fixture.moderation.plans.size());
    }

    @Test
    void recommendationChangeRetainsDraftAndCreatesNoCase() {
        Fixture fixture = fixture();
        fixture.workflow.prepare(request(StaffRank.MOD), OperationalMode.ACTIVE);
        fixture.policies.replace("v2", List.of(policy(Duration.ofDays(7))));

        PunishmentResult.Rejected rejected = assertInstanceOf(
                PunishmentResult.Rejected.class,
                fixture.workflow.confirm(DRAFT_ID, actor(StaffRank.MOD), OperationalMode.ACTIVE)
        );

        assertEquals("RECOMMENDATION_CHANGED", rejected.code());
        PunishmentDraftConfirmation.Rejected routed = assertInstanceOf(
                PunishmentDraftConfirmation.Rejected.class,
                fixture.workflow.confirmRouted(DRAFT_ID, actor(StaffRank.MOD),
                        OperationalMode.ACTIVE));
        assertEquals("RECOMMENDATION_CHANGED", routed.code());
        assertTrue(fixture.workflow.find(DRAFT_ID, ACTOR_ID).isPresent());
        assertTrue(fixture.moderation.plans.isEmpty());
    }

    @Test
    void committedResultSurvivesDraftCleanupFailure() {
        Fixture fixture = fixture();
        fixture.workflow.prepare(request(StaffRank.MOD), OperationalMode.ACTIVE);
        fixture.drafts.failDelete = true;

        PunishmentDraftCleanupException failure = assertThrows(
                PunishmentDraftCleanupException.class,
                () -> fixture.workflow.confirm(DRAFT_ID, actor(StaffRank.MOD), OperationalMode.ACTIVE)
        );

        assertEquals(new CaseId("TESTCASE00000001"), failure.accepted().caseId());
        assertEquals(1, fixture.moderation.plans.size());
        assertTrue(fixture.workflow.find(DRAFT_ID, ACTOR_ID).isPresent());
    }

    @Test
    void committedDraftCanRecoverAfterCleanupFailureAndPolicyReloadWithoutAnotherCase() {
        Fixture fixture = fixture();
        fixture.workflow.prepare(request(StaffRank.MOD), OperationalMode.ACTIVE);
        fixture.drafts.failDelete = true;
        assertThrows(PunishmentDraftCleanupException.class,
                () -> fixture.workflow.confirm(DRAFT_ID, actor(StaffRank.MOD),
                        OperationalMode.ACTIVE));
        assertEquals(1, fixture.moderation.plans.size());

        // The first case is durable, but the saved draft was not cleaned up.
        // A policy reload must not hide that committed case or apply it twice.
        fixture.policies.replace("v2", List.of(policy(Duration.ofDays(7))));
        fixture.drafts.failDelete = false;
        PunishmentResult.Accepted replay = assertInstanceOf(PunishmentResult.Accepted.class,
                fixture.workflow.confirm(DRAFT_ID, actor(StaffRank.MOD),
                        OperationalMode.ACTIVE));
        assertTrue(replay.replayed());
        assertEquals(new CaseId("TESTCASE00000001"), replay.caseId());
        assertEquals(1, fixture.moderation.plans.size());
        assertTrue(fixture.workflow.find(DRAFT_ID, ACTOR_ID).isEmpty());
    }

    @Test
    void routedCommittedDraftRecoversAfterCleanupFailureAndPolicyReload() {
        Fixture fixture = fixture();
        fixture.workflow.prepare(request(StaffRank.MOD), OperationalMode.ACTIVE);
        fixture.drafts.failDelete = true;
        assertThrows(PunishmentDraftCleanupException.class,
                () -> fixture.workflow.confirmRouted(DRAFT_ID, actor(StaffRank.MOD),
                        OperationalMode.ACTIVE));
        fixture.policies.replace("v2", List.of(policy(Duration.ofDays(7))));
        fixture.drafts.failDelete = false;
        PunishmentDraftConfirmation.Applied replay = assertInstanceOf(
                PunishmentDraftConfirmation.Applied.class,
                fixture.workflow.confirmRouted(DRAFT_ID, actor(StaffRank.MOD),
                        OperationalMode.ACTIVE));
        assertTrue(replay.accepted().replayed());
        assertEquals(new CaseId("TESTCASE00000001"), replay.accepted().caseId());
        assertEquals(1, fixture.moderation.plans.size());
        assertTrue(fixture.workflow.find(DRAFT_ID, ACTOR_ID).isEmpty());
    }

    private static Fixture fixture() {
        AtomicReasonPolicyRepository policies = new AtomicReasonPolicyRepository(
                "v1", List.of(policy(Duration.ofDays(1)))
        );
        CapturingModerationStore moderation = new CapturingModerationStore();
        InMemoryDraftStore drafts = new InMemoryDraftStore();
        PunishmentService punishments = new PunishmentService(
                Clock.fixed(NOW, ZoneOffset.UTC),
                new SecureIdentifiers(new SecureRandom()),
                new DefaultAuthorizationPolicy(),
                policies,
                moderation,
                new EscalationEngine()
        );
        PunishmentDraftWorkflow workflow = new PunishmentDraftWorkflow(
                Clock.fixed(NOW, ZoneOffset.UTC),
                Duration.ofHours(24),
                () -> DRAFT_ID,
                punishments,
                drafts
        );
        return new Fixture(workflow, policies, drafts, moderation);
    }

    private static PreparePunishmentDraftRequest request(StaffRank rank) {
        return new PreparePunishmentDraftRequest(
                TARGET_ID,
                actor(rank),
                "chat.toxicity",
                "Reviewed through the central punishment workflow",
                CaseVisibility.PUBLIC,
                "punish"
        );
    }

    private static Actor actor(StaffRank rank) {
        return new Actor(ACTOR_ID, rank.name(), rank);
    }

    private static ReasonPolicy policy(Duration duration) {
        return new ReasonPolicy(
                "chat.toxicity",
                "chat",
                "Chat toxicity",
                10,
                true,
                List.of(new PunishmentStep(0, "Mute", List.of(
                        new SanctionSpec(SanctionType.MUTE, SanctionLength.temporary(duration))
                ))),
                List.of("Repeated personal attacks"),
                true,
                true,
                false,
                StaffRank.MOD,
                false,
                AltInheritanceMode.ACTIVE_SANCTIONS
        );
    }

    private record Fixture(
            PunishmentDraftWorkflow workflow,
            AtomicReasonPolicyRepository policies,
            InMemoryDraftStore drafts,
            CapturingModerationStore moderation
    ) {
    }

    private static final class CapturingModerationStore implements ModerationStore {
        private final List<PunishmentPlan> plans = new ArrayList<>();
        private List<PriorOffense> priorOffenses = List.of();

        @Override
        public List<PriorOffense> relatedHistory(UUID targetId, String family) {
            return priorOffenses;
        }

        @Override
        public Optional<CaseId> committedCase(net.enthusia.staff.common.IdempotencyKey key) {
            return plans.stream()
                    .filter(plan -> plan.idempotencyKey().equals(key))
                    .findFirst()
                    .map(ignored -> new CaseId("TESTCASE00000001"));
        }

        @Override
        public PunishmentResult.Accepted createPunishment(PunishmentPlan plan) {
            plans.add(plan);
            return new PunishmentResult.Accepted(new CaseId("TESTCASE00000001"), false);
        }
    }

    private static final class InMemoryDraftStore implements PunishmentDraftStore {
        // This test fake is created and exercised synchronously by one test thread.
        @SuppressWarnings("PMD.DocumentMutableMapFieldConcurrency")
        private final Map<UUID, PunishmentDraft> entries = new HashMap<>();
        private boolean failDelete;

        @Override
        public void save(PunishmentDraft draft) {
            entries.values().removeIf(existing -> existing.actorId().equals(draft.actorId())
                    && existing.targetId().equals(draft.targetId()));
            entries.put(draft.draftId(), draft);
        }

        @Override
        public Optional<PunishmentDraft> find(UUID draftId, UUID actorId, Instant now) {
            return Optional.ofNullable(entries.get(draftId))
                    .filter(draft -> draft.actorId().equals(actorId) && !draft.expiredAt(now));
        }

        @Override
        public Optional<PunishmentDraft> findLatest(UUID actorId, UUID targetId, Instant now) {
            return entries.values().stream()
                    .filter(draft -> draft.actorId().equals(actorId)
                            && draft.targetId().equals(targetId)
                            && !draft.expiredAt(now))
                    .findFirst();
        }

        @Override
        public boolean delete(UUID draftId, UUID actorId) {
            if (failDelete) {
                throw new IllegalStateException("simulated draft cleanup failure");
            }
            PunishmentDraft draft = entries.get(draftId);
            return draft != null && draft.actorId().equals(actorId) && entries.remove(draftId) != null;
        }

        @Override
        public int deleteExpired(Instant now) {
            int before = entries.size();
            entries.values().removeIf(draft -> draft.expiredAt(now));
            return before - entries.size();
        }
    }
}
