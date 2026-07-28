package net.enthusia.staff.persistence;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.regex.Pattern;

final class JdbcOutboxSupport {
    private static final Pattern ERROR_CODE_PATTERN = Pattern.compile("[A-Z0-9_]{1,64}");

    private JdbcOutboxSupport() {
    }

    static int bindDueWindow(
            PreparedStatement statement,
            Instant now,
            int timestampCount
    ) throws SQLException {
        Timestamp timestamp = Timestamp.from(now);
        for (int index = 1; index <= timestampCount; index++) {
            statement.setTimestamp(index, timestamp);
        }
        return timestampCount + 1;
    }

    static void addLeaseBatchEntry(
            PreparedStatement statement,
            String owner,
            Timestamp leaseUntil,
            UUID messageId
    ) throws SQLException {
        statement.setString(1, owner);
        statement.setTimestamp(2, leaseUntil);
        statement.setBytes(3, UuidBytes.toBytes(messageId));
        statement.addBatch();
    }

    static boolean validIdentifier(String value, int maximumLength) {
        return value != null && !value.isBlank() && value.length() <= maximumLength;
    }

    static boolean positive(Duration duration) {
        return duration != null && !duration.isZero() && !duration.isNegative();
    }

    static String safeError(String errorCode) {
        if (errorCode == null || !ERROR_CODE_PATTERN.matcher(errorCode).matches()) {
            throw new IllegalArgumentException("errorCode must be a stable sanitized identifier");
        }
        return errorCode;
    }
}
