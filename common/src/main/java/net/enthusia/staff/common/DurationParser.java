package net.enthusia.staff.common;

import java.time.Duration;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class DurationParser {
    private static final Pattern TOKEN = Pattern.compile("(\\d+)([smhdw])", Pattern.CASE_INSENSITIVE);
    private static final long MAX_SECONDS = Duration.ofDays(365L * 100L).getSeconds();

    public ParsedDuration parse(String input) {
        String normalized = Checks.nonBlank(input, "duration", 64).toLowerCase(Locale.ROOT);
        if (normalized.equals("permanent") || normalized.equals("perm")) {
            return ParsedDuration.permanent();
        }

        Matcher matcher = TOKEN.matcher(normalized);
        long totalSeconds = 0;
        int consumed = 0;
        while (matcher.find()) {
            if (matcher.start() != consumed) {
                throw invalid(input);
            }
            long amount;
            try {
                amount = Long.parseLong(matcher.group(1));
            } catch (NumberFormatException exception) {
                throw invalid(input);
            }
            long multiplier = switch (matcher.group(2).charAt(0)) {
                case 's' -> 1L;
                case 'm' -> 60L;
                case 'h' -> 3_600L;
                case 'd' -> 86_400L;
                case 'w' -> 604_800L;
                default -> throw invalid(input);
            };
            try {
                totalSeconds = Math.addExact(totalSeconds, Math.multiplyExact(amount, multiplier));
            } catch (ArithmeticException exception) {
                throw invalid(input);
            }
            if (totalSeconds > MAX_SECONDS) {
                throw new IllegalArgumentException("duration exceeds the 100-year safety limit");
            }
            consumed = matcher.end();
        }
        if (consumed != normalized.length() || totalSeconds <= 0) {
            throw invalid(input);
        }
        return ParsedDuration.temporary(Duration.ofSeconds(totalSeconds));
    }

    private static IllegalArgumentException invalid(String input) {
        return new IllegalArgumentException("invalid duration: " + input);
    }
}
