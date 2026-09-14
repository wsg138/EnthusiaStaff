package net.enthusia.staff.discordbot;

import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.enthusia.staff.domain.sanction.SanctionLength;

/** Strict, bounded parser for Discord punishment duration presets and custom durations. */
final class DiscordDurationParser {
    private static final Pattern TEMPORARY = Pattern.compile("([1-9][0-9]{0,5})([smhdw])");
    private static final Duration MAXIMUM = Duration.ofDays(3650);
    private static final Map<String, Duration> PRESETS = Map.of(
            "10m", Duration.ofMinutes(10),
            "1h", Duration.ofHours(1),
            "6h", Duration.ofHours(6),
            "1d", Duration.ofDays(1),
            "7d", Duration.ofDays(7),
            "30d", Duration.ofDays(30)
    );

    record Parsed(SanctionLength length, boolean custom) {
        Parsed {
            if (length == null) {
                throw new IllegalArgumentException("parsed duration must be present");
            }
        }
    }

    Parsed parse(String raw, boolean permanentAllowed) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("duration is required");
        }
        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        if (normalized.equals("perm") || normalized.equals("permanent")) {
            if (!permanentAllowed) {
                throw new IllegalArgumentException("permanent duration is not valid for this consequence");
            }
            return new Parsed(SanctionLength.permanent(), false);
        }
        Duration preset = PRESETS.get(normalized);
        if (preset != null) {
            return new Parsed(SanctionLength.temporary(preset), false);
        }
        Matcher matcher = TEMPORARY.matcher(normalized);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("duration must be a preset, custom number plus s/m/h/d/w, or permanent");
        }
        long quantity = Long.parseLong(matcher.group(1));
        Duration duration = switch (matcher.group(2)) {
            case "s" -> Duration.ofSeconds(quantity);
            case "m" -> Duration.ofMinutes(quantity);
            case "h" -> Duration.ofHours(quantity);
            case "d" -> Duration.ofDays(quantity);
            case "w" -> Duration.ofDays(Math.multiplyExact(quantity, 7));
            default -> throw new IllegalArgumentException("unsupported duration unit");
        };
        if (duration.compareTo(MAXIMUM) > 0) {
            throw new IllegalArgumentException("duration exceeds the parser safety maximum");
        }
        return new Parsed(SanctionLength.temporary(duration), true);
    }
}
