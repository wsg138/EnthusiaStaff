package net.enthusia.staff.discordbot;

import java.util.Objects;
import java.util.Optional;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.enthusia.staff.domain.policyv2.publicview.PolicyV2PublicProjectionReader;

/**
 * Safe Policy v2 Discord read path. It can only read canonical public
 * projections and is not a private-case formatting surface.
 */
final class PolicyV2PublicDiscordView {
    private final PolicyV2PublicProjectionReader projections;

    PolicyV2PublicDiscordView(PolicyV2PublicProjectionReader projections) {
        this.projections = Objects.requireNonNull(projections, "projections");
    }

    Optional<MessageEmbed> find(String caseId, int color, String iconUrl) {
        return projections.publicProjection(caseId)
                .map(projection -> PunishmentNotificationDiscordPresentation.embed(
                        projection,
                        color,
                        iconUrl
                ));
    }
}
