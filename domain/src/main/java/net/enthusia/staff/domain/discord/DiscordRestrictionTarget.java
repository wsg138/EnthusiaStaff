package net.enthusia.staff.domain.discord;

import java.math.BigInteger;
import java.util.regex.Pattern;

/** Explicit channel/category restriction target and effect. */
public record DiscordRestrictionTarget(Kind kind, String snowflake, Mode mode) {
    private static final Pattern DECIMAL = Pattern.compile("[0-9]{1,20}");
    private static final BigInteger MAX_UNSIGNED_LONG = new BigInteger("18446744073709551615");

    public enum Kind {
        CHANNEL,
        CATEGORY
    }

    public enum Mode {
        READ_ONLY,
        NO_ACCESS
    }

    public DiscordRestrictionTarget {
        if (kind == null || mode == null) {
            throw new IllegalArgumentException("restriction kind and mode must be present");
        }
        snowflake = normalize(snowflake);
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("restriction snowflake must not be blank");
        }
        String normalized = value.trim();
        if (!DECIMAL.matcher(normalized).matches()) {
            throw new IllegalArgumentException("restriction snowflake must be decimal");
        }
        BigInteger numeric = new BigInteger(normalized);
        if (numeric.signum() <= 0 || numeric.compareTo(MAX_UNSIGNED_LONG) > 0) {
            throw new IllegalArgumentException("restriction snowflake is outside Discord range");
        }
        return numeric.toString();
    }
}
