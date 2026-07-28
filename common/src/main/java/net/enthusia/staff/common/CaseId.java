package net.enthusia.staff.common;

import java.util.Locale;
import java.util.regex.Pattern;

public record CaseId(String value) {
    private static final Pattern FORMAT = Pattern.compile("[0-9A-HJKMNP-TV-Z]{16}");

    public CaseId {
        value = Checks.nonBlank(value, "caseId", 16).toUpperCase(Locale.ROOT);
        if (!FORMAT.matcher(value).matches()) {
            throw new IllegalArgumentException("caseId must be a 16-character Crockford identifier");
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
