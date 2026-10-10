package net.enthusia.staff.domain.alt;

/**
 * Unified moderation inheritance rule. Only verified same-person identities or
 * manual very-high-confidence relationships inherit automatically. Network
 * overlap alone never warrants automatic sanctions.
 */
public final class AltInheritancePolicy {
    public static final double AUTOMATIC_THRESHOLD = 0.85;

    public boolean shouldInherit(AltRelationshipState relationshipState, boolean currentlyVerifiedDiscordLink) {
        if (relationshipState != null && relationshipState.preventsAutomaticInheritance()) {
            return false;
        }
        if (currentlyVerifiedDiscordLink) {
            return true;
        }
        return relationshipState != null && relationshipState.inheritsAutomatically();
    }
}
