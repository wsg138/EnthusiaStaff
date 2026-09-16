package net.enthusia.staff.discordbot;

import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.enthusia.staff.domain.sanction.SanctionLength;

/** Strict, bounded parser for Discord punishment duration presets and custom durations. */
final class DiscordDurationParser {
    private static final Pattern TEMPORARY = Pattern.compile("([1-9][0-9]{0,8})([smhdw])");
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
        String normalized = normalize(raw);
        if (isPermanent(normalized)) {
            return permanent(permanentAllowed);
        }
        Duration preset = PRESETS.get(normalized);
        return preset == null ? custom(normalized) : temporary(preset, false);
    }

    private static String normalize(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("duration is required");
        }
        return raw.trim().toLowerCase(Locale.ROOT);
    }

    private static boolean isPermanent(String value) {
        return value.equals("perm") || value.equals("permanent");
    }

    private static Parsed permanent(boolean allowed) {
        if (!allowed) {
            throw new IllegalArgumentException("permanent duration is not valid for this consequence");
        }
        return new Parsed(SanctionLength.permanent(), false);
    }

    private static Parsed custom(String value) {
        Matcher matcher = TEMPORARY.matcher(value);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("duration must be a preset, custom number plus s/m/h/d/w, or permanent");
        }
        long quantity = Long.parseLong(matcher.group(1));
        Duration duration = duration(quantity, matcher.group(2));
        if (duration.compareTo(MAXIMUM) > 0) {
            throw new IllegalArgumentException("duration exceeds the parser safety maximum");
        }
        return temporary(duration, true);
    }

    private static Parsed temporary(Duration duration, boolean custom) {
        return new Parsed(SanctionLength.temporary(duration), custom);
    }

    private static Duration duration(long quantity, String unit) {
        return switch (unit) {
            case "s" -> Duration.ofSeconds(quantity);
            case "m" -> Duration.ofMinutes(quantity);
            case "h" -> Duration.ofHours(quantity);
            case "d" -> Duration.ofDays(quantity);
            case "w" -> Duration.ofDays(Math.multiplyExact(quantity, 7));
            default -> throw new IllegalArgumentException("unsupported duration unit");
        };
    }
}
