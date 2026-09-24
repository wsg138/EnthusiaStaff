package net.enthusia.staff.domain.application;

import java.util.List;
import net.enthusia.staff.common.CaseId;
import net.enthusia.staff.domain.auth.Actor;

/** Authoritative Paper gateway used by Discord-origin Minecraft and Both punishment flows. */
public interface MinecraftPunishmentGateway extends MinecraftPunishmentPreparer {
    List<PunishmentReasonOption> availableReasons(Actor actor);

    PunishmentResult commitConfirmed(
            CreatePunishmentRequest request,
            CaseId caseId,
            PunishmentExpectation expectation
    );
}
