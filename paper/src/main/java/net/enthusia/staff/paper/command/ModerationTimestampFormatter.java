package net.enthusia.staff.paper.command;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.TextStyle;
import java.time.temporal.ChronoField;
import java.util.Locale;
import java.util.Objects;

final class ModerationTimestampFormatter {
    private static final DateTimeFormatter BASE = new DateTimeFormatterBuilder()
            .append(DateTimeFormatter.ISO_LOCAL_DATE)
            .appendLiteral(' ')
            .appendValue(ChronoField.HOUR_OF_DAY, 2)
            .appendLiteral(':')
            .appendValue(ChronoField.MINUTE_OF_HOUR, 2)
            .appendLiteral(':')
            .appendValue(ChronoField.SECOND_OF_MINUTE, 2)
            .appendLiteral(' ')
            .appendZoneText(TextStyle.SHORT)
            .toFormatter(Locale.ROOT);

    private ModerationTimestampFormatter() {
    }

    static DateTimeFormatter inZone(ZoneId zone) {
        return BASE.withZone(Objects.requireNonNull(zone, "zone"));
    }
}
