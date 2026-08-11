package net.enthusia.staff.velocity;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Predicate;

final class VelocityStatusSuggestions {
    private static final String STATUS = "status";
    private static final List<String> MIGRATION_OPERATIONS =
            List.of("inspect", "dry-run", "import", "shadow", "final");
    private static final List<String> CUTOVER_OPERATIONS =
            List.of(STATUS, "maintenance", "activate");
    private static final List<String> FOUNDER_CUTOVER_OPERATIONS =
            List.of(STATUS, "maintenance", "abort", "freeze", "activate", "override");
    private static final List<String> DISCORD_OPERATIONS = List.of(STATUS, "retry");
    private static final List<String> DISCORD_DESTINATIONS =
            List.of("punishments", "reports", "logs-staffmode", "alerts");
    private static final List<String> WEBSITE_OPERATIONS = List.of(STATUS, "code");
    private static final List<String> WEBSITE_CODE_OPERATIONS = List.of("show", "rotate", "revoke");

    private VelocityStatusSuggestions() {
    }

    static List<String> suggest(String[] arguments, Predicate<String> hasPermission) {
        Objects.requireNonNull(arguments, "arguments");
        Objects.requireNonNull(hasPermission, "hasPermission");
        return switch (arguments.length) {
            case 0, 1 -> topLevel(hasPermission);
            case 2 -> secondLevel(arguments[0], hasPermission);
            case 3 -> thirdLevel(arguments[0], arguments[1], hasPermission);
            default -> List.of();
        };
    }

    private static List<String> topLevel(Predicate<String> hasPermission) {
        List<String> suggestions = new ArrayList<>(List.of(STATUS, "verify"));
        addWhenPermitted(suggestions, "reload", "enthusiastaff.reload", hasPermission);
        addWhenPermitted(suggestions, "migration", "enthusiastaff.migration", hasPermission);
        addWhenPermitted(suggestions, "cutover", "enthusiastaff.cutover", hasPermission);
        addWhenPermitted(suggestions, "discord", "enthusiastaff.discord.manage", hasPermission);
        addWhenPermitted(suggestions, "website", "enthusiastaff.website.manage", hasPermission);
        return List.copyOf(suggestions);
    }

    private static List<String> secondLevel(String first, Predicate<String> hasPermission) {
        return switch (normalize(first)) {
            case "migration" -> permitted(MIGRATION_OPERATIONS, "enthusiastaff.migration", hasPermission);
            case "cutover" -> cutoverOperations(hasPermission);
            case "discord" -> permitted(DISCORD_OPERATIONS, "enthusiastaff.discord.manage", hasPermission);
            case "website" -> permitted(WEBSITE_OPERATIONS, "enthusiastaff.website.manage", hasPermission);
            default -> List.of();
        };
    }

    private static List<String> thirdLevel(
            String first,
            String second,
            Predicate<String> hasPermission
    ) {
        return switch (normalize(first)) {
            case "discord" -> normalize(second).equals("retry")
                    ? permitted(DISCORD_DESTINATIONS, "enthusiastaff.discord.manage", hasPermission)
                    : List.of();
            case "website" -> normalize(second).equals("code")
                    ? permitted(WEBSITE_CODE_OPERATIONS, "enthusiastaff.website.manage", hasPermission)
                    : List.of();
            default -> List.of();
        };
    }

    private static List<String> cutoverOperations(Predicate<String> hasPermission) {
        if (!hasPermission.test("enthusiastaff.cutover")) {
            return List.of();
        }
        return hasPermission.test("enthusiastaff.cutover.founder")
                ? FOUNDER_CUTOVER_OPERATIONS
                : CUTOVER_OPERATIONS;
    }

    private static List<String> permitted(
            List<String> suggestions,
            String permission,
            Predicate<String> hasPermission
    ) {
        return hasPermission.test(permission) ? suggestions : List.of();
    }

    private static void addWhenPermitted(
            List<String> suggestions,
            String suggestion,
            String permission,
            Predicate<String> hasPermission
    ) {
        if (hasPermission.test(permission)) {
            suggestions.add(suggestion);
        }
    }

    private static String normalize(String value) {
        return value.toLowerCase(Locale.ROOT);
    }
}
