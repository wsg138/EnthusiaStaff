package net.enthusia.staff.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Map;
import java.util.Set;
import net.enthusia.staff.domain.auth.StaffRank;
import net.enthusia.staff.paper.visibility.DefaultStaffVisibilityService;
import org.junit.jupiter.api.Test;

final class VisibilityMatrixLoaderTest {
    private final VisibilityMatrixLoader loader = new VisibilityMatrixLoader();

    @Test
    void emptyConfigurationUsesSafeDefaults() {
        assertEquals(DefaultStaffVisibilityService.defaultMatrix(), loader.load(path -> List.of()));
    }

    @Test
    void configuredRanksAreCaseInsensitiveAndDeduplicated() {
        Map<StaffRank, Set<StaffRank>> matrix = loader.load(path -> path.endsWith(".MOD")
                ? List.of("admin", "ADMIN", "founder")
                : List.of());

        assertEquals(Set.of(StaffRank.ADMIN, StaffRank.FOUNDER), matrix.get(StaffRank.MOD));
        assertEquals(
                DefaultStaffVisibilityService.defaultMatrix().get(StaffRank.ADMIN),
                matrix.get(StaffRank.ADMIN)
        );
    }

    @Test
    void systemRankCannotBeMadeVisible() {
        assertThrows(IllegalArgumentException.class, () -> loader.load(path -> path.endsWith(".MOD")
                ? List.of("SYSTEM")
                : List.of()));
    }

    @Test
    void unknownRankRejectsTheConfiguration() {
        assertThrows(IllegalArgumentException.class, () -> loader.load(path -> path.endsWith(".MOD")
                ? List.of("unknown")
                : List.of()));
    }
}
