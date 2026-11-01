package net.enthusia.staff.velocity;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import net.enthusia.staff.domain.sanction.ActiveSanction;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;

/** Compact, player-facing Velocity login denial for active bans. */
final class VelocityPunishmentDisconnectRenderer {
    private static final TextColor ORANGE = TextColor.color(0xFF8C00);
    private static final ZoneId EASTERN = ZoneId.of("America/New_York");
    private static final DateTimeFormatter END_TIME = DateTimeFormatter
            .ofPattern("MMM d, uuuu 'at' h:mm:ss a z", Locale.US)
            .withZone(EASTERN);

    private VelocityPunishmentDisconnectRenderer() {
    }

    static Component render(
            ActiveSanction sanction,
            Instant now,
            String appealsUrl,
            Optional<String> appealCode
    ) {
        Objects.requireNonNull(sanction, "sanction");
        Objects.requireNonNull(now, "now");
        Objects.requireNonNull(appealsUrl, "appealsUrl");
        Objects.requireNonNull(appealCode, "appealCode");

        Component message = Component.empty()
                .append(Component.text("ENTHUSIA | CONNECTION BLOCKED",
                        NamedTextColor.RED, TextDecoration.BOLD))
                .append(Component.text("\n"))
                .append(Component.text("You cannot join while this ban is active.", NamedTextColor.GRAY))
                .append(Component.text("\n\n"))
                .append(labelValue("Reason: ", sanction.publicReason(), NamedTextColor.WHITE))
                .append(Component.text("\n"));

        if (sanction.expiresAt().isPresent()) {
            Instant end = sanction.expiresAt().orElseThrow();
            message = message
                    .append(labelValue("Time left: ", remaining(now, end), NamedTextColor.YELLOW))
                    .append(Component.text("\n"))
                    .append(Component.text("(Ends " + END_TIME.format(end) + ")", NamedTextColor.GRAY));
        } else {
            message = message.append(labelValue(
                    "Duration: ", "Permanent ban", NamedTextColor.RED));
        }

        message = message
                .append(Component.text("\n\n"))
                .append(labelValue("Appeal: ", appealsUrl, NamedTextColor.YELLOW));

        if (appealCode.isPresent() && !appealCode.orElseThrow().isBlank()) {
            message = message
                    .append(Component.text("\n"))
                    .append(labelValue("Appeal code: ", appealCode.orElseThrow(), NamedTextColor.WHITE));
        }
        return message;
    }

    /** Deliberately omits seconds: the disconnect screen does not update after it appears. */
    static String remaining(Instant now, Instant end) {
        long seconds = Duration.between(now, end).getSeconds();
        if (seconds <= 0) {
            return "Ending now";
        }
        if (seconds < 60) {
            return "Less than 1 minute";
        }
        long minutes = seconds / 60;
        long days = minutes / (24 * 60);
        long hours = (minutes / 60) % 24;
        long remainderMinutes = minutes % 60;
        if (days > 0) {
            return days + "d " + hours + "h " + remainderMinutes + "m";
        }
        if (minutes >= 60) {
            return hours + "h " + remainderMinutes + "m";
        }
        return minutes + "m";
    }

    private static Component labelValue(String label, String value, TextColor valueColor) {
        return Component.text(label, ORANGE)
                .append(Component.text(value, valueColor));
    }
}
