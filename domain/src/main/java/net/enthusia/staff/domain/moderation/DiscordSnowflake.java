package net.enthusia.staff.domain.moderation;

import java.math.BigInteger;
import java.util.regex.Pattern;

final class DiscordSnowflake {
    private static final Pattern DECIMAL = Pattern.compile("[0-9]{1,20}");
    private static final BigInteger MAX_UNSIGNED_LONG = new BigInteger("18446744073709551615");

    private DiscordSnowflake() {
    }

    static String normalize(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        String normalized = value.trim();
        if (!DECIMAL.matcher(normalized).matches()) {
            throw new IllegalArgumentException(field + " must be a decimal Discord snowflake");
        }
        BigInteger numeric = new BigInteger(normalized);
        if (numeric.signum() <= 0 || numeric.compareTo(MAX_UNSIGNED_LONG) > 0) {
            throw new IllegalArgumentException(field + " is outside the Discord snowflake range");
        }
        return numeric.toString();
    }
}
