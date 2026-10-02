package net.enthusia.staff.paper.auth;

import static org.junit.jupiter.api.Assertions.*;

import java.security.SecureRandom;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import net.enthusia.staff.common.CaseId;
import net.enthusia.staff.common.SecureIdentifiers;
import net.enthusia.staff.domain.OperationalMode;
import net.enthusia.staff.domain.application.*;
import net.enthusia.staff.domain.auth.*;
import net.enthusia.staff.domain.escalation.*;
import net.enthusia.staff.domain.player.*;
import net.enthusia.staff.domain.ports.*;
import net.enthusia.staff.domain.sanction.*;
import org.junit.jupiter.api.Test;

final class StaffWebPunishmentServiceTest {
    private static final String CONFIRM_OPERATION = "confirm";

    private static final UUID ACTOR = UUID.fromString("10000000-0000-4000-8000-000000000001");
    private static final UUID TARGET = UUID.fromString("10000000-0000-4000-8000-000000000002");
    private static final String SESSION = "a".repeat(64);

    @Test
    void reviewResponseUsesIsoExpiryWithoutOptionalJacksonTimeModules() throws Exception {
        var prepared = new Fixture().prepare();
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        var response = mapper.readTree(mapper.writeValueAsBytes(prepared));
        assertEquals(prepared.targetId().toString(), response.get("targetId").asText());
        assertEquals(prepared.confirmationId().toString(), response.get("confirmationId").asText());
        assertEquals(Instant.parse(prepared.expiresAt()), Instant.parse(response.get("expiresAt").asText()));
    }

    @Test
    void configuredDraftCreatesOneCaseAndRetryReturnsItsOriginalResult() {
        Fixture fixture = new Fixture();
        var prepared = fixture.prepare();
        assertEquals(TARGET, prepared.targetId());
        assertEquals("1 hour", prepared.consequences().getFirst().duration());
        assertTrue(fixture.plans.isEmpty());
        var request = confirm(prepared.confirmationId(), TARGET, SESSION);
        var applied = assertInstanceOf(StaffWebPunishmentService.Status.class,
                fixture.service.execute(CONFIRM_OPERATION, request));
        assertEquals("APPLIED", applied.state());
        assertEquals("TESTCASE00000001", applied.caseId());
        assertEquals(applied, fixture.service.execute(CONFIRM_OPERATION, request));
        assertEquals(applied, fixture.service.execute("status", request));
        assertEquals(1, fixture.plans.size());
    }

    @Test
    void changedTargetSessionAndIntentCannotConfirmAnExistingDraft() {
        Fixture fixture = new Fixture();
        var prepared = fixture.prepare();
        assertThrows(IllegalArgumentException.class, () -> fixture.service.execute(CONFIRM_OPERATION,
                confirm(prepared.confirmationId(), TARGET, "b".repeat(64))));
        assertThrows(IllegalArgumentException.class, () -> fixture.service.execute(CONFIRM_OPERATION,
                confirm(prepared.confirmationId(), UUID.randomUUID(), SESSION)));
        assertThrows(IllegalArgumentException.class, () -> fixture.service.execute(CONFIRM_OPERATION,
                new StaffWebPunishmentService.Request(ACTOR, TARGET, SESSION, "chat.toxicity", "changed",
                        prepared.confirmationId())));
        assertTrue(fixture.plans.isEmpty());
    }

    @Test
    void leavingStaffModeAfterWebsiteReviewCannotCommitThePunishment() {
        Fixture fixture = new Fixture();
        var prepared = fixture.prepare();
        fixture.activeDuty.set(false);
        assertThrows(SecurityException.class, () -> fixture.service.execute(CONFIRM_OPERATION,
                confirm(prepared.confirmationId(), TARGET, SESSION)));
        assertTrue(fixture.plans.isEmpty());
    }

    @Test
    void currentAuthorityAndTargetProtectionAreCheckedAgainOnConfirmation() {
        Fixture fixture = new Fixture();
        var prepared = fixture.prepare();
        fixture.actor.set(null);
        assertThrows(SecurityException.class, () -> fixture.service.execute(CONFIRM_OPERATION,
                confirm(prepared.confirmationId(), TARGET, SESSION)));
        fixture.actor.set(new Actor(ACTOR, "Moderator", StaffRank.MOD));
        fixture.targetRank.set(Optional.of(StaffRank.MOD));
        assertThrows(SecurityException.class, () -> fixture.service.execute(CONFIRM_OPERATION,
                confirm(prepared.confirmationId(), TARGET, SESSION)));
        assertTrue(fixture.plans.isEmpty());
    }

    @Test
    void expiredAndInactiveConfirmationsCannotCommitPunishments() {
        Fixture fixture = new Fixture();
        var prepared = fixture.prepare();
        fixture.mode.set(OperationalMode.SHADOW_MIGRATION);
        assertThrows(IllegalArgumentException.class, () -> fixture.service.execute(CONFIRM_OPERATION,
                confirm(prepared.confirmationId(), TARGET, SESSION)));
        fixture.mode.set(OperationalMode.ACTIVE);
        fixture.now.set(fixture.now.get().plusSeconds(120));
        assertThrows(IllegalArgumentException.class, () -> fixture.service.execute(CONFIRM_OPERATION,
                confirm(prepared.confirmationId(), TARGET, SESSION)));
        assertTrue(fixture.plans.isEmpty());
    }

    @Test
    void unsupportedAssetConsequencesDoNotEnterTheWebsiteWorkflow() {
        Fixture fixture = new Fixture();
        assertThrows(IllegalArgumentException.class, () -> fixture.service.execute("prepare",
                new StaffWebPunishmentService.Request(ACTOR, TARGET, SESSION, "asset.confiscation", "review", null)));
        assertTrue(fixture.plans.isEmpty());
    }

    private static StaffWebPunishmentService.Request confirm(UUID id, UUID target, String session) {
        return new StaffWebPunishmentService.Request(ACTOR, target, session, null, null, id);
    }

    private static final class Fixture {
        final AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-09-01T12:00:00Z"));
        final AtomicReference<OperationalMode> mode = new AtomicReference<>(OperationalMode.ACTIVE);
        final AtomicReference<Actor> actor = new AtomicReference<>(new Actor(ACTOR, "Moderator", StaffRank.MOD));
        final AtomicReference<Optional<StaffRank>> targetRank = new AtomicReference<>(Optional.empty());
        final java.util.concurrent.atomic.AtomicBoolean activeDuty = new java.util.concurrent.atomic.AtomicBoolean(true);
        final List<PunishmentPlan> plans = new ArrayList<>();
        final StaffWebPunishmentService service;

        Fixture() {
            Clock clock = new Clock() {
                public ZoneId getZone() { return ZoneOffset.UTC; }
                public Clock withZone(ZoneId zone) { return this; }
                public Instant instant() { return now.get(); }
            };
            var policies = new AtomicReasonPolicyRepository("v1", List.of(new ReasonPolicy(
                    "chat.toxicity", "chat", "Chat toxicity", 10, true,
                    List.of(new PunishmentStep(0, "Mute", List.of(new SanctionSpec(SanctionType.MUTE,
                            SanctionLength.temporary(Duration.ofHours(1)))))))));
            var authorization = new ActiveDutyAuthorizationPolicy(new DefaultAuthorizationPolicy(), id -> activeDuty.get());
            ModerationStore moderation = new ModerationStore() {
                public List<PriorOffense> relatedHistory(UUID id, String family) { return List.of(); }
                public PunishmentResult.Accepted createPunishment(PunishmentPlan plan) {
                    plans.add(plan);
                    return new PunishmentResult.Accepted(new CaseId("TESTCASE00000001"), false);
                }
            };
            var punishments = new PunishmentService(clock, new SecureIdentifiers(new SecureRandom()),
                    authorization, policies, moderation, new EscalationEngine());
            var workflow = new PunishmentDraftWorkflow(clock, Duration.ofHours(24), punishments, new Drafts());
            PlayerDirectory players = new PlayerDirectory() {
                public Optional<PlayerIdentity> find(String query) {
                    return Optional.of(new PlayerIdentity(UUID.fromString(query), Optional.of("Player"),
                            PlayerPlatform.JAVA, now.get(), now.get()));
                }
                public List<PlayerIdentity> search(String prefix, int limit) { return List.of(); }
                public Optional<PlayerPresence> presence(UUID id) { return Optional.empty(); }
                public void recordSeen(UUID id, String name, PlayerPlatform platform, String server, Instant time) { }
                public void recordDisconnected(UUID id, String server, Instant time) { }
            };
            service = new StaffWebPunishmentService(new StaffWebPunishmentService.Dependencies(clock, mode::get,
                    () -> workflow, () -> players, policies, authorization), id -> actor.get(), id -> targetRank.get());
        }

        StaffWebPunishmentService.Prepared prepare() {
            return assertInstanceOf(StaffWebPunishmentService.Prepared.class, service.execute("prepare",
                    new StaffWebPunishmentService.Request(ACTOR, TARGET, SESSION, "chat.toxicity", "Reviewed evidence", null)));
        }
    }

    private static final class Drafts implements PunishmentDraftStore {
        final Map<UUID, PunishmentDraft> entries = new java.util.concurrent.ConcurrentHashMap<>();
        public void save(PunishmentDraft draft) { entries.put(draft.draftId(), draft); }
        public Optional<PunishmentDraft> find(UUID id, UUID actor, Instant now) {
            return Optional.ofNullable(entries.get(id)).filter(draft -> draft.actorId().equals(actor) && !draft.expiredAt(now));
        }
        public Optional<PunishmentDraft> findLatest(UUID actor, UUID target, Instant now) {
            return entries.values().stream().filter(draft -> draft.actorId().equals(actor)
                    && draft.targetId().equals(target) && !draft.expiredAt(now)).findFirst();
        }
        public boolean delete(UUID id, UUID actor) { return find(id, actor, Instant.MIN).isPresent() && entries.remove(id) != null; }
        public int deleteExpired(Instant now) {
            int before = entries.size(); entries.values().removeIf(draft -> draft.expiredAt(now)); return before - entries.size();
        }
    }
}
