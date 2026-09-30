package net.enthusia.staff.velocity;

import net.enthusia.staff.domain.OperationalMode;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

/** Shared Adventure styling for player-facing Velocity administration output. */
final class VelocityMessageStyle {
    private static final String SEPARATOR = " — ";

    private VelocityMessageStyle() {
    }

    enum Tone {
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

    static Component header(String title) {
        return Component.text(title, NamedTextColor.AQUA, TextDecoration.BOLD);
    }

    static Component modeHeader(String title, OperationalMode mode) {
        return header(title)
                .append(Component.text(" • ", NamedTextColor.DARK_GRAY))
                .append(Component.text(displayMode(mode), modeColor(mode), TextDecoration.BOLD));
    }

    static Component section(String title) {
        return Component.text("▸ ", NamedTextColor.GOLD)
                .append(Component.text(title, NamedTextColor.YELLOW, TextDecoration.BOLD));
    }

    static Component statusRow(String label, String status, String detail, Tone tone) {
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

    static Component success(String text) {
        return Component.text(text, NamedTextColor.GREEN);
    }

    static Component warning(String text) {
        return Component.text(text, NamedTextColor.GOLD);
    }

    static Component error(String text) {
        return Component.text(text, NamedTextColor.RED);
    }

    static Component command(String value) {
        return Component.text(value, NamedTextColor.AQUA);
    }

    static Component id(Object value) {
        return Component.text(String.valueOf(value), NamedTextColor.AQUA);
    }

    static Component usage(String usage) {
        int commandStart = usage.indexOf('/');
        if (commandStart < 0) {
            return Component.text(usage, NamedTextColor.GRAY);
        }
        return Component.text(usage.substring(0, commandStart), NamedTextColor.GRAY)
                .append(command(usage.substring(commandStart)));
    }

    static Component style(Component component) {
        if (!(component instanceof TextComponent text)
                || !component.children().isEmpty()
                || !component.style().isEmpty()) {
            return component;
        }
        return style(text.content());
    }

    static Component style(String text) {
        String trimmed = text == null ? "" : text.trim();
        if (trimmed.regionMatches(true, 0, "usage:", 0, "usage:".length())) {
            return usage(text);
        }
        Tone tone = VelocityMessageToneClassifier.toneFor(trimmed);
        if (tone == Tone.MUTED) {
            return neutralLine(text);
        }
        return Component.text(text, tone.color);
    }

    static Tone issueTone(String key, String detail, OperationalMode mode) {
        return VelocityMessageToneClassifier.issueTone(key, detail, mode);
    }

    static NamedTextColor modeColor(OperationalMode mode) {
        return switch (mode) {
            case ACTIVE -> NamedTextColor.GREEN;
            case SHADOW_MIGRATION -> NamedTextColor.GOLD;
            case DEGRADED, READ_ONLY_FAILURE -> NamedTextColor.RED;
            default -> NamedTextColor.GOLD;
        };
    }

    static String displayMode(OperationalMode mode) {
        return mode.name().replace('_', ' ');
    }

    static String label(String value) {
        String normalized = value == null ? "" : value.trim().replace('-', ' ').replace('_', ' ');
        if (normalized.isBlank()) {
            return "Runtime";
        }
        return Character.toUpperCase(normalized.charAt(0)) + normalized.substring(1);
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
