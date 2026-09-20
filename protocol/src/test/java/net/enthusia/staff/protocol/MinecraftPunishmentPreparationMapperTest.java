package net.enthusia.staff.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import net.enthusia.staff.common.CaseId;
import net.enthusia.staff.common.IdempotencyKey;
import net.enthusia.staff.domain.application.CreatePunishmentRequest;
import net.enthusia.staff.domain.application.PunishmentPlan;
import net.enthusia.staff.domain.auth.Actor;
import net.enthusia.staff.domain.auth.StaffRank;
import net.enthusia.staff.domain.casefile.CaseVisibility;
import net.enthusia.staff.domain.escalation.DecayEligibility;
import net.enthusia.staff.domain.escalation.EscalationDecision;
import net.enthusia.staff.domain.escalation.PunishmentStep;
import net.enthusia.staff.domain.sanction.SanctionLength;
import net.enthusia.staff.domain.sanction.SanctionSpec;
import net.enthusia.staff.domain.sanction.SanctionType;
import org.junit.jupiter.api.Test;

class MinecraftPunishmentPreparationMapperTest {
    private static final CaseId CASE_ID = new CaseId("0123456789ABCDEF");
    private static final UUID ACTOR_ID = UUID.fromString("30000000-0000-0000-0000-000000000088");
    private static final UUID TARGET_ID = UUID.fromString("10000000-0000-0000-0000-000000000088");
    private static final Actor ACTOR = new Actor(ACTOR_ID, "D08Admin", StaffRank.ADMIN);
    private static final SanctionSpec MUTE = new SanctionSpec(
            SanctionType.MUTE, SanctionLength.temporary(Duration.ofDays(30)));
    private static final SanctionSpec BAN = new SanctionSpec(
            SanctionType.NETWORK_BAN, SanctionLength.temporary(Duration.ofDays(14)));

    @Test
    void requestRoundTripPreservesExplicitMultiSanctionOverride() {
        CreatePunishmentRequest request = new CreatePunishmentRequest(
                new IdempotencyKey("d08:multi:request"), TARGET_ID, ACTOR,
                "harassment.sexual", "multi sanction test", CaseVisibility.PUBLIC, List.of(MUTE, BAN)
        );

        String json = MinecraftPunishmentPreparationCodec.encodeRequest(
                MinecraftPunishmentPreparationMapper.request(CASE_ID, request));
        var decoded = MinecraftPunishmentPreparationCodec.decodeRequest(json);
        CreatePunishmentRequest restored = MinecraftPunishmentPreparationMapper.request(decoded, ACTOR);

        assertEquals(List.of(MUTE, BAN), restored.overrideSanctions());
        assertEquals(request.idempotencyKey(), restored.idempotencyKey());
    }

    @Test
    void preparedPlanRoundTripPreservesMultiSanctionExpectation() {
        PunishmentStep step = new PunishmentStep(0, "30 day mute and 14 day ban", List.of(MUTE, BAN));
        PunishmentPlan plan = new PunishmentPlan(
                CASE_ID, new IdempotencyKey("d08:multi:plan"), TARGET_ID, ACTOR,
                "harassment.sexual", "harassment", "Sexual harassment", "multi sanction test",
                "d08-multi-v1", CaseVisibility.PUBLIC, Instant.parse("2026-09-20T04:00:00Z"),
                new EscalationDecision(0, 0, 0, List.of(), DecayEligibility.UNKNOWN, step),
                List.of(MUTE, BAN)
        );

        var wire = MinecraftPunishmentPreparationMapper.plan(plan);
        String json = MinecraftPunishmentPreparationCodec.encodeResponse(
                MinecraftPunishmentPreparationWire.Response.prepared(wire));
        PunishmentPlan restored = MinecraftPunishmentPreparationMapper.plan(
                MinecraftPunishmentPreparationCodec.decodeResponse(json).plan());

        assertEquals(List.of(MUTE, BAN), restored.sanctions());
        assertEquals(step.sanctions(), restored.escalation().selectedStep().sanctions());
    }
}
