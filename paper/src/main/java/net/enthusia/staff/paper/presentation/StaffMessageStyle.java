package net.enthusia.staff.paper.presentation;

import net.enthusia.staff.domain.OperationalMode;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

/** Shared, restrained Adventure styling for player-facing Staff chat output. */
public final class StaffMessageStyle {
    private static final String SEPARATOR = " — ";

    private StaffMessageStyle() {
    }

    public enum Tone {
        SUCCESS(NamedTextColor.GREEN),
        WARNING(NamedTextColor.GOLD),
        ERROR(NamedTextColor.RED),
        INFO(NamedTextColor.AQUA),
        MUTED(NamedTextColor.GRAY);

        private final NamedTextColor color;

        Tone(NamedTextColor color) {
            this.color = color;
        }
    }

    public static Component header(String title) {
        return Component.text(title, NamedTextColor.AQUA, TextDecoration.BOLD);
    }

    public static Component modeHeader(String title, OperationalMode mode) {
        return header(title)
                .append(Component.text(" • ", NamedTextColor.DARK_GRAY))
                .append(Component.text(displayMode(mode), modeColor(mode), TextDecoration.BOLD));
    }

    public static Component section(String title) {
        return Component.text("▸ ", NamedTextColor.GOLD)
                .append(Component.text(title, NamedTextColor.YELLOW, TextDecoration.BOLD));
    }

    public static Component statusRow(String label, String status, String detail, Tone tone) {
        Component row = Component.text("  ", NamedTextColor.DARK_GRAY)
                .append(Component.text(label, NamedTextColor.GRAY))
                .append(Component.text("  ", NamedTextColor.DARK_GRAY))
                .append(Component.text(status, tone.color));
        if (detail == null || detail.isBlank()) {
            return row;
        }
        return row.append(Component.text(SEPARATOR, NamedTextColor.DARK_GRAY))
                .append(Component.text(detail, NamedTextColor.GRAY));
    }

    public static Component success(String text) {
        return Component.text(text, NamedTextColor.GREEN);
    }

    public static Component warning(String text) {
        return Component.text(text, NamedTextColor.GOLD);
    }

    public static Component error(String text) {
        return Component.text(text, NamedTextColor.RED);
    }

    public static Component info(String text) {
        return Component.text(text, NamedTextColor.GRAY);
    }

    public static Component command(String command) {
        return Component.text(command, NamedTextColor.AQUA);
    }

    public static Component player(String playerName) {
        return Component.text(playerName, NamedTextColor.AQUA);
    }

    public static Component id(Object id) {
        return Component.text(String.valueOf(id), NamedTextColor.AQUA);
    }

    public static Component usage(String usage) {
        int commandStart = usage.indexOf('/');
        if (commandStart < 0) {
            return Component.text(usage, NamedTextColor.GRAY);
        }
        return Component.text(usage.substring(0, commandStart), NamedTextColor.GRAY)
                .append(command(usage.substring(commandStart)));
    }

    /**
     * Adds restrained severity styling to an otherwise plain text component.
     * Deliberately leaves composed or interactive Adventure components untouched.
     */
    public static Component style(Component component) {
        if (!(component instanceof TextComponent text)
                || !component.children().isEmpty()
                || !component.style().isEmpty()) {
            return component;
        }
        return style(text.content());
    }

    public static Component style(String text) {
        String trimmed = text == null ? "" : text.trim();
        if (trimmed.regionMatches(true, 0, "usage:", 0, "usage:".length())) {
            return usage(text);
        }
        Tone tone = StaffMessageToneClassifier.toneFor(trimmed);
        if (tone == Tone.MUTED) {
            return neutralLine(text);
        }
        return Component.text(text, tone.color);
    }

    public static Tone issueTone(String key, String detail, OperationalMode mode) {
        return StaffMessageToneClassifier.issueTone(key, detail, mode);
    }

    public static NamedTextColor modeColor(OperationalMode mode) {
        return switch (mode) {
            case ACTIVE -> NamedTextColor.GREEN;
            case SHADOW_MIGRATION -> NamedTextColor.GOLD;
            case DEGRADED, READ_ONLY_FAILURE -> NamedTextColor.RED;
            default -> NamedTextColor.GOLD;
        };
    }

    public static String displayMode(OperationalMode mode) {
        return mode.name().replace('_', ' ');
    }

    private static Component neutralLine(String text) {
        int separator = text.indexOf(':');
        if (separator <= 0 || separator == text.length() - 1) {
            return Component.text(text, NamedTextColor.GRAY);
        }
        return Component.text(text.substring(0, separator), NamedTextColor.GRAY)
                .append(Component.text(":", NamedTextColor.DARK_GRAY))
                .append(Component.text(text.substring(separator + 1), NamedTextColor.WHITE));
    }
}
