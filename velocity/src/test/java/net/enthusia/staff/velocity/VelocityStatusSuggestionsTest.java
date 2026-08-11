package net.enthusia.staff.velocity;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class VelocityStatusSuggestionsTest {
    private static final String STATUS = "status";

    @Test
    void topLevelOnlyExposesPrivilegedOperationsWhenPermitted() {
        assertEquals(
                List.of(STATUS, "verify"),
                VelocityStatusSuggestions.suggest(new String[0], ignored -> false)
        );

        Set<String> permissions = Set.of(
                "enthusiastaff.reload",
                "enthusiastaff.migration",
                "enthusiastaff.cutover",
                "enthusiastaff.discord.manage",
                "enthusiastaff.website.manage"
        );
        assertEquals(
                List.of(STATUS, "verify", "reload", "migration", "cutover", "discord", "website"),
                VelocityStatusSuggestions.suggest(new String[]{""}, permissions::contains)
        );
    }

    @Test
    void secondarySuggestionsKeepTheirExistingPermissionBoundaries() {
        assertEquals(
                List.of("inspect", "dry-run", "import", "shadow", "final"),
                VelocityStatusSuggestions.suggest(
                        new String[]{"MIGRATION", ""},
                        "enthusiastaff.migration"::equals
                )
        );
        assertEquals(
                List.of(),
                VelocityStatusSuggestions.suggest(new String[]{"migration", ""}, ignored -> false)
        );
        assertEquals(
                List.of(STATUS, "maintenance", "activate"),
                VelocityStatusSuggestions.suggest(
                        new String[]{"cutover", ""},
                        "enthusiastaff.cutover"::equals
                )
        );
    }

    @Test
    void founderCutoverSuggestionsIncludeEmergencyOperations() {
        Set<String> permissions = Set.of("enthusiastaff.cutover", "enthusiastaff.cutover.founder");

        assertEquals(
                List.of(STATUS, "maintenance", "abort", "freeze", "activate", "override"),
                VelocityStatusSuggestions.suggest(new String[]{"cutover", ""}, permissions::contains)
        );
    }

    @Test
    void tertiarySuggestionsOnlyAppearForSupportedAdministrationRoutes() {
        assertEquals(
                List.of("punishments", "reports", "logs-staffmode", "alerts"),
                VelocityStatusSuggestions.suggest(
                        new String[]{"discord", "retry", ""},
                        "enthusiastaff.discord.manage"::equals
                )
        );
        assertEquals(
                List.of("show", "rotate", "revoke"),
                VelocityStatusSuggestions.suggest(
                        new String[]{"website", "code", ""},
                        "enthusiastaff.website.manage"::equals
                )
        );
        assertEquals(
                List.of(),
                VelocityStatusSuggestions.suggest(new String[]{"discord", STATUS, ""}, ignored -> true)
        );
        assertEquals(
                List.of(),
                VelocityStatusSuggestions.suggest(new String[]{"unknown", "", ""}, ignored -> true)
        );
        assertEquals(
                List.of(),
                VelocityStatusSuggestions.suggest(new String[]{"website", "code", "show", "extra"}, ignored -> true)
        );
    }
}
