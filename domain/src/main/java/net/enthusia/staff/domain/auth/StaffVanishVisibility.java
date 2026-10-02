package net.enthusia.staff.domain.auth;

import java.util.Map;
import java.util.Set;

/** Viewer ranks allowed to see each vanished staff rank; independent of punishment authority. */
public final class StaffVanishVisibility {
    private static final Map<StaffRank, Set<StaffRank>> MATRIX = Map.of(
                StaffRank.HELPER, Set.of(StaffRank.HELPER, StaffRank.MOD, StaffRank.DEVELOPER),
                StaffRank.MOD, Set.of(StaffRank.HELPER, StaffRank.MOD, StaffRank.DEVELOPER),
                StaffRank.DEVELOPER, Set.of(StaffRank.HELPER, StaffRank.MOD, StaffRank.DEVELOPER, StaffRank.ADMIN),
                StaffRank.ADMIN, Set.of(StaffRank.HELPER, StaffRank.MOD, StaffRank.DEVELOPER, StaffRank.ADMIN),
                StaffRank.FOUNDER, Set.of(StaffRank.HELPER, StaffRank.MOD, StaffRank.DEVELOPER,
                        StaffRank.ADMIN, StaffRank.FOUNDER)
    );

    private StaffVanishVisibility() {
    }

    public static Map<StaffRank, Set<StaffRank>> matrix() {
        return MATRIX;
    }

    public static boolean canSee(StaffRank viewer, StaffRank target) {
        return viewer != null && target != null && matrix().getOrDefault(viewer, Set.of()).contains(target);
    }
}
