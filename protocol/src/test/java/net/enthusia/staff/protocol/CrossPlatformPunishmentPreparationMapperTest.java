package net.enthusia.staff.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import net.enthusia.staff.common.CaseId;
import net.enthusia.staff.common.IdempotencyKey;
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

class CrossPlatformPunishmentPreparationMapperTest {
    private static final CaseId CASE_ID = new CaseId("0123456789ABCDEF");
    private static final UUID TARGET = UUID.fromString("10000000-0000-0000-0000-000000000088");
    private static final Actor ACTOR = new Actor(
            UUID.fromString("30000000-0000-0000-0000-000000000088"), "D08Admin", StaffRank.ADMIN);

    @Test
    void preparedPlanRoundTripPreservesMultiSanctionExpectation() {
        SanctionSpec mute = new SanctionSpec(SanctionType.MUTE, SanctionLength.temporary(Duration.ofDays(30)));
        SanctionSpec ban = new SanctionSpec(SanctionType.NETWORK_BAN, SanctionLength.temporary(Duration.ofDays(14)));
        PunishmentStep step = new PunishmentStep(0, "combined", List.of(mute, ban));
        PunishmentPlan plan = new PunishmentPlan(
                CASE_ID, new IdempotencyKey("d08:multi:plan"), TARGET, ACTOR, "harassment.sexual",
                "harassment", "Sexual harassment", "multi sanction test", "d08-multi-v1",
                CaseVisibility.PUBLIC, Instant.parse("2026-09-20T04:00:00Z"),
                new EscalationDecision(0, 0, 0, List.of(), DecayEligibility.UNKNOWN, step), List.of(mute, ban));

        String json = CrossPlatformPunishmentWireCodec.encodeResponse(
                CrossPlatformPunishmentPreparationWire.Response.prepared(
                        CrossPlatformPunishmentPreparationMapper.plan(plan)));
        PunishmentPlan restored = CrossPlatformPunishmentPreparationMapper.plan(
                CrossPlatformPunishmentWireCodec.decodeResponse(json).plan());

        assertEquals(plan, restored);
    }

    @Test
    void strictCodecRejectsUnknownRequestFields() {
        String json = """
                {"version":1,"caseId":"0123456789ABCDEF","idempotencyKey":"d08:test",
                 "actorId":"30000000-0000-0000-0000-000000000088","actorName":"Admin",
                 "targetId":"10000000-0000-0000-0000-000000000088","reasonId":"chat.toxicity",
                 "internalExplanation":"test","unexpected":true}
                """;

        assertThrows(IllegalArgumentException.class, () -> CrossPlatformPunishmentWireCodec.decodeRequest(json));
    }
}
