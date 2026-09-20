package net.enthusia.staff.domain.application;

import net.enthusia.staff.common.CaseId;

/** Prepares a Minecraft punishment against the current authoritative policy without committing it. */
public interface MinecraftPunishmentPreparer {
    PunishmentPreparation prepareConfirmed(CreatePunishmentRequest request, CaseId caseId);
}
