package net.enthusia.staff.paper;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import net.enthusia.staff.domain.auth.StaffRank;
import net.enthusia.staff.paper.visibility.DefaultStaffVisibilityService;

final class VisibilityMatrixLoader {
    private static final List<StaffRank> CONFIGURABLE_VIEWERS = List.of(
            StaffRank.MOD,
            StaffRank.DEVELOPER,
            StaffRank.ADMIN,
            StaffRank.FOUNDER
    );

    Map<StaffRank, Set<StaffRank>> load(Function<String, List<String>> configuredValues) {
        Map<StaffRank, Set<StaffRank>> defaults = DefaultStaffVisibilityService.defaultMatrix();
        Map<StaffRank, Set<StaffRank>> configured = new EnumMap<>(StaffRank.class);
        configured.put(StaffRank.HELPER, defaults.get(StaffRank.HELPER));
        for (StaffRank viewer : CONFIGURABLE_VIEWERS) {
            configured.put(viewer, targetsFor(viewer, configuredValues.apply(path(viewer)), defaults));
        }
        return Map.copyOf(configured);
    }

    private Set<StaffRank> targetsFor(
            StaffRank viewer,
            List<String> configuredValues,
            Map<StaffRank, Set<StaffRank>> defaults
    ) {
        if (configuredValues.isEmpty()) {
            return defaults.get(viewer);
        }
        Set<StaffRank> targets = EnumSet.noneOf(StaffRank.class);
        for (String value : configuredValues) {
            StaffRank target = StaffRank.valueOf(value.toUpperCase(Locale.ROOT));
            if (target == StaffRank.SYSTEM) {
                throw new IllegalArgumentException("SYSTEM is not a visible staff rank");
            }
            targets.add(target);
        }
        return Set.copyOf(targets);
    }

    private String path(StaffRank viewer) {
        return "visibility.matrix." + viewer.name();
    }
}
