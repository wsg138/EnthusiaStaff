package net.enthusia.staff.paper.command;

import java.util.Optional;

/** Parsed vanish behavior requested for a Staff Mode entry. */
enum StaffModeVanishEntryOption {
    REMEMBERED(null, false),
    VANISHED(Boolean.TRUE, true),
    VISIBLE(Boolean.FALSE, true);

    private final Boolean override;
    private final boolean explicit;

    StaffModeVanishEntryOption(Boolean override, boolean explicit) {
        this.override = override;
        this.explicit = explicit;
    }

    Optional<Boolean> override() {
        return Optional.ofNullable(override);
    }

    boolean explicit() {
        return explicit;
    }

    static Optional<StaffModeVanishEntryOption> parse(String[] arguments) {
        if (arguments.length == 0) {
            return Optional.of(REMEMBERED);
        }
        if (arguments.length != 1) {
            return Optional.empty();
        }
        return switch (arguments[0].toLowerCase(java.util.Locale.ROOT)) {
            case "-v", "vanish" -> Optional.of(VANISHED);
            case "-nv", "visible" -> Optional.of(VISIBLE);
            default -> Optional.empty();
        };
    }
}
