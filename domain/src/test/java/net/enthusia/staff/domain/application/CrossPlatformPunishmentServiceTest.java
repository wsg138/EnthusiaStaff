package net.enthusia.staff.domain.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.enthusia.staff.common.CaseId;
import net.enthusia.staff.common.IdempotencyKey;
import net.enthusia.staff.common.SecureIdentifiers;
import net.enthusia.staff.domain.OperationalMode;
import net.enthusia.staff.domain.auth.Actor;
import net.enthusia.staff.domain.auth.DefaultAuthorizationPolicy;
import net.enthusia.staff.domain.auth.DiscordAuthorizationLimits;
import net.enthusia.staff.domain.auth.DiscordConsequenceIntent;
import net.enthusia.staff.domain.auth.DiscordConsequenceType;
import net.enthusia.staff.domain.auth.DiscordModerationAuthorizationService;
import net.enthusia.staff.domain.auth.StaffRank;
import net.enthusia.staff.domain.casefile.CaseVisibility;
import net.enthusia.staff.domain.discord.DiscordPunishmentIntent;
import net.enthusia.staff.domain.escalation.AltInheritanceMode;
import net.enthusia.staff.domain.escalation.EscalationEngine;
import net.enthusia.staff.domain.escalation.PriorOffense;
import net.enthusia.staff.domain.escalation.PunishmentStep;
import net.enthusia.staff.domain.escalation.ReasonPolicy;
import net.enthusia.staff.domain.moderation.DiscordGuildId;
import net.enthusia.staff.domain.moderation.DiscordUserId;
import net.enthusia.staff.domain.moderation.ModerationPlatform;
import net.enthusia.staff.domain.moderation.ModerationSubjectId;
import net.enthusia.staff.domain.ports.AtomicReasonPolicyRepository;
import net.enthusia.staff.domain.ports.CrossPlatformIdentityLookup;
import net.enthusia.staff.domain.ports.CrossPlatformPunishmentStore;
import net.enthusia.staff.domain.ports.ModerationStore;
import net.enthusia.staff.domain.sanction.SanctionLength;
import net.enthusia.staff.domain.sanction.SanctionSpec;
import net.enthusia.staff.domain.sanction.SanctionType;
import org.junit.jupiter.api.Test;

class CrossPlatformPunishmentServiceTest {
    private static final Instant NOW = Instant.parse("2026-09-19T21:30:00Z");
    private static final UUID TARGET = UUID.fromString("10000000-0000-0000-0000-000000000008");
    private static final ModerationSubjectId SUBJECT = new ModerationSubjectId(
            UUID.fromString("20000000-0000-0000-0000-000000000008"));
    private static final DiscordUserId DISCORD = new DiscordUserId("1410303324745371709");
    private static final DiscordGuildId GUILD = new DiscordGuildId("1497476349244211311");
    private static final CaseId CASE_ID = new CaseId("0123456789ABCDEF");
    private static final SanctionLength ONE_HOUR = SanctionLength.temporary(Duration.ofHours(1));
    private static final SanctionSpec MUTE = new SanctionSpec(SanctionType.MUTE, ONE_HOUR);

    @Test
    void bothCreatesOneAtomicPlanWithoutUsingNormalMinecraftCommit() {
        Fixture fixture = fixture(SUBJECT, SUBJECT, OperationalMode.ACTIVE);

        CrossPlatformPunishmentOutcome.Accepted accepted = assertInstanceOf(
                CrossPlatformPunishmentOutcome.Accepted.class,
                fixture.service.createBoth(request())
        );

        assertEquals(CASE_ID, accepted.result().caseId());
        assertEquals(1, fixture.atomicStore.plans.size());
        assertEquals(0, fixture.minecraftStore.commits);
        CrossPlatformPunishmentPlan plan = fixture.atomicStore.plans.getFirst();
        assertEquals(Optional.of(CASE_ID), plan.discord().caseId());
        assertEquals(TARGET, plan.minecraft().targetId());
        assertTrue(plan.discord().intent().authorizationIntent().platform() == ModerationPlatform.DISCORD);
    }

    @Test
    void identityDriftRejectsBeforePolicyOrPersistence() {
        Fixture fixture = fixture(SUBJECT, new ModerationSubjectId(UUID.randomUUID()), OperationalMode.ACTIVE);

        CrossPlatformPunishmentOutcome.Rejected rejected = assertInstanceOf(
                CrossPlatformPunishmentOutcome.Rejected.class,
                fixture.service.createBoth(request())
        );

        assertEquals("IDENTITY_MISMATCH", rejected.code());
        assertTrue(fixture.atomicStore.plans.isEmpty());
        assertEquals(0, fixture.minecraftStore.historyReads);
    }

    @Test
    void confirmedMinecraftConsequenceMustMatchFreshPolicyResult() {
        Fixture fixture = fixture(SUBJECT, SUBJECT, OperationalMode.ACTIVE);
        CrossPlatformPunishmentRequest request = request();
        PunishmentExpectation stale = new PunishmentExpectation(
                "d08-test-v1",
                0,
                "One day ban",
                List.of(new SanctionSpec(SanctionType.NETWORK_BAN, SanctionLength.temporary(Duration.ofDays(1))))
        );
        CrossPlatformPunishmentRequest changed = new CrossPlatformPunishmentRequest(
                request.caseId(), request.discordPunishmentId(), request.operationKey(), request.subjectId(),
                request.discordUserId(), request.guildId(), request.minecraftRequest(), stale,
                request.discordIntent(), request.targetStaff()
        );

        CrossPlatformPunishmentOutcome.Rejected rejected = assertInstanceOf(
                CrossPlatformPunishmentOutcome.Rejected.class,
                fixture.service.createBoth(changed)
        );

        assertEquals("MINECRAFT_CONSEQUENCE_MISMATCH", rejected.code());
        assertTrue(fixture.atomicStore.plans.isEmpty());
    }

    @Test
    void nonActiveModeRejectsBeforeAtomicCommit() {
        Fixture fixture = fixture(SUBJECT, SUBJECT, OperationalMode.SHADOW_MIGRATION);

        CrossPlatformPunishmentOutcome.Rejected rejected = assertInstanceOf(
                CrossPlatformPunishmentOutcome.Rejected.class,
                fixture.service.createBoth(request())
        );

        assertEquals("MODE_BLOCKED", rejected.code());
        assertTrue(fixture.atomicStore.plans.isEmpty());
    }

    private static Fixture fixture(
            ModerationSubjectId minecraftSubject,
            ModerationSubjectId discordSubject,
            OperationalMode mode
    ) {
        CapturingModerationStore minecraftStore = new CapturingModerationStore();
        PunishmentService punishmentService = new PunishmentService(
                Clock.fixed(NOW, ZoneOffset.UTC),
                new SecureIdentifiers(new SecureRandom(new byte[]{8, 0, 8})),
                new DefaultAuthorizationPolicy(),
                new AtomicReasonPolicyRepository("d08-test-v1", List.of(policy())),
                minecraftStore,
                new EscalationEngine()
        );
        CapturingCrossPlatformStore atomicStore = new CapturingCrossPlatformStore();
        CrossPlatformIdentityLookup identities = new CrossPlatformIdentityLookup() {
            @Override
            public Optional<ModerationSubjectId> subjectForMinecraft(UUID playerId) {
                return TARGET.equals(playerId) ? Optional.of(minecraftSubject) : Optional.empty();
            }

            @Override
            public Optional<ModerationSubjectId> subjectForDiscord(DiscordUserId userId) {
                return DISCORD.equals(userId) ? Optional.of(discordSubject) : Optional.empty();
            }
        };
        DiscordAuthorizationLimits limits = new DiscordAuthorizationLimits(
                Duration.ofHours(1), Duration.ofDays(7), Duration.ofDays(30), Duration.ofDays(7));
        MinecraftPunishmentPreparer preparer = (request, caseId) ->
                punishmentService.prepareConfirmed(request, mode, caseId, NOW);
        CrossPlatformPunishmentService service = new CrossPlatformPunishmentService(
                preparer,
                atomicStore,
                identities,
                new DiscordModerationAuthorizationService(new DefaultAuthorizationPolicy(), limits)
        );
        return new Fixture(service, atomicStore, minecraftStore);
    }

    private static CrossPlatformPunishmentRequest request() {
        Actor actor = new Actor(
                UUID.fromString("30000000-0000-0000-0000-000000000008"), "D08Admin", StaffRank.ADMIN);
        CreatePunishmentRequest minecraft = new CreatePunishmentRequest(
                new IdempotencyKey("d08:test:both:0008"), TARGET, actor, "chat.toxicity", "D08 integration test",
                CaseVisibility.PUBLIC, List.of(MUTE)
        );
        DiscordPunishmentIntent discordIntent = new DiscordPunishmentIntent(
                DiscordConsequenceType.MUTE, ONE_HOUR, false, false, Optional.empty(),
                "Chat toxicity", "D08 integration test", 0, true
        );
        return new CrossPlatformPunishmentRequest(
                CASE_ID,
                UUID.fromString("40000000-0000-0000-0000-000000000008"),
                "d08:test:operation:0008",
                SUBJECT,
                DISCORD,
                GUILD,
                minecraft,
                new PunishmentExpectation("d08-test-v1", 0, "One hour mute", List.of(MUTE)),
                discordIntent,
                Optional.empty()
        );
    }

    private static ReasonPolicy policy() {
        return new ReasonPolicy(
                "chat.toxicity", "chat", "Chat toxicity", 10, true,
                List.of(new PunishmentStep(0, "One hour mute", List.of(
                        MUTE))),
                List.of(), true, true, false, StaffRank.MOD, false, AltInheritanceMode.ACTIVE_SANCTIONS
        );
    }

    private static final class CapturingModerationStore implements ModerationStore {
        private int historyReads;
        private int commits;

        @Override
        public List<PriorOffense> relatedHistory(UUID targetId, String family) {
            historyReads++;
            return List.of();
        }

        @Override
        public PunishmentResult.Accepted createPunishment(PunishmentPlan plan) {
            commits++;
            return new PunishmentResult.Accepted(plan.caseId(), false);
        }
    }

    private static final class CapturingCrossPlatformStore implements CrossPlatformPunishmentStore {
        private final java.util.ArrayList<CrossPlatformPunishmentPlan> plans = new java.util.ArrayList<>();

        @Override
        public CrossPlatformPunishmentResult create(CrossPlatformPunishmentPlan plan) {
            plans.add(plan);
            return new CrossPlatformPunishmentResult(plan.minecraft().caseId(), plan.discord().punishmentId(), false);
        }
    }

    private record Fixture(
            CrossPlatformPunishmentService service,
            CapturingCrossPlatformStore atomicStore,
            CapturingModerationStore minecraftStore
    ) {
    }
}
