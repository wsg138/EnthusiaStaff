package net.enthusia.staff.domain.auth;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import net.enthusia.staff.domain.moderation.ModerationPlatform;

/**
 * Explicit authorization input for a Discord moderation interaction.
 *
 * <p>The request carries the final selected platform set. Command origin is intentionally absent:
 * an origin may choose a UI default, but it cannot grant or broaden authority.</p>
 */
public record DiscordAuthorizationRequest(
        DiscordModerationOperation operation,
        Set<ModerationPlatform> platforms,
        List<DiscordConsequenceIntent> consequences
) {
    public DiscordAuthorizationRequest {
        if (operation == null || platforms == null || consequences == null) {
            throw new IllegalArgumentException("operation, platforms and consequences must be present");
        }
        for (ModerationPlatform platform : platforms) {
            if (platform == null) {
                throw new IllegalArgumentException("platform must be present");
            }
        }
        for (DiscordConsequenceIntent consequence : consequences) {
            if (consequence == null) {
                throw new IllegalArgumentException("consequence must be present");
            }
        }
        platforms = Set.copyOf(platforms);
        consequences = List.copyOf(consequences);
        if (platforms.isEmpty()) {
            throw new IllegalArgumentException("at least one explicit platform is required");
        }

        if (operation.consequencesRequired()) {
            if (consequences.isEmpty()) {
                throw new IllegalArgumentException("sanction issuance requires explicit consequences");
            }
            EnumSet<ModerationPlatform> consequencePlatforms = EnumSet.noneOf(ModerationPlatform.class);
            for (DiscordConsequenceIntent consequence : consequences) {
                if (!consequencePlatforms.add(consequence.platform())) {
                    throw new IllegalArgumentException("only one consequence per platform is permitted");
                }
            }
            if (!consequencePlatforms.equals(platforms)) {
                throw new IllegalArgumentException("selected platforms must exactly match consequence platforms");
            }
        } else if (!consequences.isEmpty()) {
            throw new IllegalArgumentException("only sanction issuance may include consequences");
        }
    }
}
