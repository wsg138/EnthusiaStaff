package net.enthusia.staff.paper.command;

import java.util.Optional;

/** Parsed vanish behavior requested for a Staff Mode entry. */
enum StaffModeVanishEntryOption {
    REMEMBERED,
    VANISHED,
    VISIBLE;

    private static final int SINGLE_ARGUMENT = 1;

    Optional<Boolean> override() {
        return switch (this) {
            case REMEMBERED -> Optional.empty();
            case VANISHED -> Optional.of(true);
            case VISIBLE -> Optional.of(false);
        };
    }

    boolean explicit() {
        return this != REMEMBERED;
    }

    static Optional<StaffModeVanishEntryOption> parse(String[] arguments) {
        if (arguments.length == 0) {
            return Optional.of(REMEMBERED);
        }
        if (arguments.length != SINGLE_ARGUMENT) {
            return Optional.empty();
        }
        return switch (arguments[0].toLowerCase(java.util.Locale.ROOT)) {
            case "-v", "vanish" -> Optional.of(VANISHED);
            case "-nv", "visible" -> Optional.of(VISIBLE);
            default -> Optional.empty();
        };
    }
}
