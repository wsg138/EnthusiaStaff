package net.enthusia.staff.domain.application;

import net.enthusia.staff.domain.discord.DiscordPunishment;

/** One authoritative case plus its independently enforceable Discord intent. */
public record CrossPlatformPunishmentPlan(
        PunishmentPlan minecraft,
        DiscordPunishment discord,
        String operationKey
) {
    private static final int MAX_OPERATION_KEY = 128;

    public CrossPlatformPunishmentPlan {
        if (minecraft == null || discord == null || operationKey == null || operationKey.isBlank()
                || operationKey.length() > MAX_OPERATION_KEY) {
            throw new IllegalArgumentException("cross-platform punishment plan fields are invalid");
        }
        if (discord.caseId().isEmpty() || !discord.caseId().orElseThrow().equals(minecraft.caseId())) {
            throw new IllegalArgumentException("Discord intent must reference the authoritative Minecraft case");
        }
        if (!discord.issuer().equals(minecraft.actor()) || !discord.issuedAt().equals(minecraft.issuedAt())) {
            throw new IllegalArgumentException("cross-platform punishment actor and issue time must match");
        }
    }
}
