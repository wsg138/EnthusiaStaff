package net.enthusia.staff.domain.auth;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/**
 * Protects staff targets from moderation actions initiated by equal or lower authority.
 * Technical developer authority is intentionally incomparable with the moderation ladder.
 */
public final class StaffTargetHierarchyPolicy {
    private static final Map<StaffRank, Integer> MODERATION_LEVELS = moderationLevels();

    public Decision decide(StaffRank actorRank, StaffRank targetRank) {
        Objects.requireNonNull(actorRank, "actorRank");
        if (targetRank == null || actorRank == StaffRank.SYSTEM) {
            return Decision.ALLOW;
        }
        return protectedTarget(actorRank, targetRank) ? Decision.PROTECTED : Decision.ALLOW;
    }

    public boolean permits(StaffRank actorRank, StaffRank targetRank) {
        return decide(actorRank, targetRank) == Decision.ALLOW;
    }

    private static boolean protectedTarget(StaffRank actorRank, StaffRank targetRank) {
        if (targetRank == StaffRank.SYSTEM) {
            return true;
        }
        if (actorRank == StaffRank.FOUNDER) {
            return targetRank == StaffRank.FOUNDER;
        }
        Integer actorLevel = MODERATION_LEVELS.get(actorRank);
        Integer targetLevel = MODERATION_LEVELS.get(targetRank);
        return actorLevel == null || targetLevel == null || actorLevel <= targetLevel;
    }

    private static Map<StaffRank, Integer> moderationLevels() {
        Map<StaffRank, Integer> levels = new EnumMap<>(StaffRank.class);
        levels.put(StaffRank.HELPER, 0);
        levels.put(StaffRank.MOD, 1);
        levels.put(StaffRank.ADMIN, 2);
        levels.put(StaffRank.FOUNDER, 3);
        return Map.copyOf(levels);
    }

    public enum Decision {
        ALLOW,
        PROTECTED
    }
}
