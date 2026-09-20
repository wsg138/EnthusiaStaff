package net.enthusia.staff.domain.application;

import net.enthusia.staff.common.CaseId;

/** Authoritative Paper gateway used by Discord-origin Minecraft and Both punishment flows. */
public interface MinecraftPunishmentGateway extends MinecraftPunishmentPreparer {
    PunishmentResult commitConfirmed(
            CreatePunishmentRequest request,
            CaseId caseId,
            PunishmentExpectation expectation
    );
}
