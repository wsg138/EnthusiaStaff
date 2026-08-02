package net.enthusia.staff.paper.command;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class SanctionExpirationParser {
    private static final Pattern COMPACT = Pattern.compile("^([1-9][0-9]{0,8})([smhdw])$");

    private final Clock clock;

    SanctionExpirationParser(Clock clock) {
        if (clock == null) {
            throw new IllegalArgumentException("clock must be present");
        }
        this.clock = clock;
    }

    Optional<Instant> parse(String input) {
        if (input == null || input.isBlank()) {
            return Optional.empty();
        }
        String normalized = input.trim();
        try {
            return Optional.of(Instant.parse(normalized));
        } catch (DateTimeParseException ignored) {
            // Compact duration parsing follows.
        }
        Matcher matcher = COMPACT.matcher(normalized.toLowerCase(Locale.ROOT));
        if (!matcher.matches()) {
            return Optional.empty();
        }
        long amount;
        try {
            amount = Long.parseLong(matcher.group(1));
        } catch (NumberFormatException exception) {
            return Optional.empty();
        }
        Duration duration = switch (matcher.group(2)) {
            case "s" -> Duration.ofSeconds(amount);
            case "m" -> Duration.ofMinutes(amount);
            case "h" -> Duration.ofHours(amount);
            case "d" -> Duration.ofDays(amount);
            case "w" -> Duration.ofDays(Math.multiplyExact(amount, 7));
            default -> throw new IllegalStateException("unreachable duration unit");
        };
        try {
            return Optional.of(clock.instant().plus(duration));
        } catch (ArithmeticException exception) {
            return Optional.empty();
        }
    }
}
