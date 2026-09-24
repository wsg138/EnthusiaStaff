package net.enthusia.staff.domain.application;

import java.util.Optional;
import java.util.UUID;
import net.enthusia.staff.common.CaseId;
import net.enthusia.staff.domain.auth.Actor;
import net.enthusia.staff.domain.discord.DiscordPunishmentIntent;
import net.enthusia.staff.domain.moderation.DiscordGuildId;
import net.enthusia.staff.domain.moderation.DiscordUserId;
import net.enthusia.staff.domain.moderation.ModerationSubjectId;

/** Final explicit D08 confirmation for a Both-platform punishment. */
public record CrossPlatformPunishmentRequest(
        CaseId caseId,
        UUID discordPunishmentId,
        String operationKey,
        ModerationSubjectId subjectId,
        DiscordUserId discordUserId,
        DiscordGuildId guildId,
        CreatePunishmentRequest minecraftRequest,
        PunishmentExpectation minecraftExpectation,
        DiscordPunishmentIntent discordIntent,
        Optional<Actor> targetStaff
) {
    private static final int MAX_OPERATION_KEY = 128;

    public CrossPlatformPunishmentRequest {
        if (caseId == null || discordPunishmentId == null || subjectId == null || discordUserId == null
                || guildId == null || minecraftRequest == null || minecraftExpectation == null
                || discordIntent == null || targetStaff == null
                || operationKey == null || operationKey.isBlank() || operationKey.length() > MAX_OPERATION_KEY) {
            throw new IllegalArgumentException("cross-platform punishment request fields are invalid");
        }
    }
}
