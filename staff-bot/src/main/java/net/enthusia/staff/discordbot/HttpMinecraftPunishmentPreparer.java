package net.enthusia.staff.discordbot;

import java.util.LinkedHashMap;
import java.util.Map;
import net.enthusia.staff.common.CaseId;
import net.enthusia.staff.domain.application.CreatePunishmentRequest;
import net.enthusia.staff.domain.application.MinecraftPunishmentPreparer;
import net.enthusia.staff.domain.application.PunishmentPlan;
import net.enthusia.staff.domain.application.PunishmentPreparation;
import net.enthusia.staff.protocol.CrossPlatformPunishmentPreparationMapper;
import net.enthusia.staff.protocol.CrossPlatformPunishmentPreparationWire;
import net.enthusia.staff.protocol.CrossPlatformPunishmentWireCodec;

/** Uses the canonical #280 signed authority client for non-committing Both-plan preparation. */
final class HttpMinecraftPunishmentPreparer implements MinecraftPunishmentPreparer {
    private final HttpStaffAuthorityClient authority;

    HttpMinecraftPunishmentPreparer(HttpStaffAuthorityClient authority) {
        if (authority == null) throw new IllegalArgumentException("authority client is required");
        this.authority = authority;
    }

    @Override
    public PunishmentPreparation prepareConfirmed(CreatePunishmentRequest request, CaseId caseId) {
        CrossPlatformPunishmentPreparationWire.Request wire =
                CrossPlatformPunishmentPreparationMapper.request(caseId, request);
        var response = CrossPlatformPunishmentWireCodec.decodeResponse(
                authority.punishment("both-plan", requestMap(wire)).toString());
        if (response.outcome() == CrossPlatformPunishmentPreparationWire.Outcome.REJECTED) {
            return new PunishmentPreparation.Rejected(response.code(), response.message());
        }
        PunishmentPlan plan = CrossPlatformPunishmentPreparationMapper.plan(response.plan());
        requireExpected(request, caseId, plan);
        return new PunishmentPreparation.Prepared(plan);
    }

    private static Map<String, Object> requestMap(CrossPlatformPunishmentPreparationWire.Request request) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("version", request.version());
        values.put("caseId", request.caseId());
        values.put("idempotencyKey", request.idempotencyKey());
        values.put("actorId", request.actorId());
        values.put("actorName", request.actorName());
        values.put("targetId", request.targetId());
        values.put("reasonId", request.reasonId());
        values.put("internalExplanation", request.internalExplanation());
        return Map.copyOf(values);
    }

    private static void requireExpected(CreatePunishmentRequest request, CaseId caseId, PunishmentPlan plan) {
        boolean matches = plan.caseId().equals(caseId)
                && plan.idempotencyKey().equals(request.idempotencyKey())
                && plan.targetId().equals(request.targetId())
                && plan.actor().id().equals(request.actor().id())
                && plan.reasonId().equals(request.reasonId())
                && plan.internalExplanation().equals(request.internalExplanation());
        if (!matches) {
            throw new StaffAuthorityClient.UnavailableException(
                    "cross-platform Minecraft preparation did not match the request");
        }
    }
}
